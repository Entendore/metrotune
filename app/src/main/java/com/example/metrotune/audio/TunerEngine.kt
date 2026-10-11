package com.example.metrotune.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class TunerEngine(
    context: Context,
    private val sampleRate: Int = 44100,   // device-native rate (see AudioConfig)
    private val onResult: (TunerResult) -> Unit
) {

    enum class Mode { CHROMATIC, STRING, POLYPHONIC }

    data class Harmonic(
        val n: Int,                   // display order, 1-based
        val ratio: Double,            // multiple of f0 (non-integer for mallet bars)
        val amplitudeDb: Double,      // vs. fundamental, 0…−60
        val centsDeviation: Double    // vs. exact ratio·f0 (NaN = too weak)
    )

    sealed class TunerResult {
        @Suppress("ArrayInDataClass")   // spectrum is an opaque event payload, never compared
        data class Mono(
            val frequency: Double?,
            val noteName: String?,          // written pitch (after transposition)
            val targetFrequency: Double?,   // tempered + calibrated target
            val cents: Double?,             // smoothed deviation vs tempered target
            val rawCents: Double?,          // instant deviation vs ET grid (strobe/graph)
            val stringIndex: Int,           // -1 in chromatic mode
            val clarity: Double,
            val rms: Double,
            val held: Boolean,              // true = display holding last reading
            val midi: Int = -1,
            val spectrum: FloatArray? = null,
            val harmonics: List<Harmonic> = emptyList(),
            val octaveCorrected: Boolean = false,
            val inharmonicity: Double = 0.0,
            val confidence: Int = 0,
            val snrDb: Double = 0.0,
            val lowNoteMode: Boolean = false,
            val noteChanged: Boolean = false,
            val overpullApplied: Double = 0.0
        ) : TunerResult()

        data class Poly(val strings: List<PolyString>, val rms: Double) : TunerResult()
    }

    data class PolyString(
        val stringIndex: Int, val noteName: String, val cents: Double, val strength: Float
    )

    companion object {
        private const val MONO_FRAME = 4096
        private const val POLY_FRAME = 8192
        private const val POLY_HOLD_MS = 350L
        private const val HOLD_MS = 700L
        private const val LOW_NOTE_HZ = 110.0     // escalate to 8192 below this
        private const val OCTAVE_CHECK_RATIO = 0.9f

        /** Buzz Feiten nut offsets (cents), standard 6-string, low E → high e. */
        val FEITEN = doubleArrayOf(-3.0, -2.0, -2.0, -2.0, 0.0, 2.0)
    }

    // ---- configuration (set from UI at any time) ----
    @Volatile var a4 = 440.0
    @Volatile var mode = Mode.CHROMATIC
    @Volatile var preset: InstrumentPreset = InstrumentLibrary.CHROMATIC
    @Volatile var lockedString = -1
    @Volatile var sensitivity = 0.5f
    @Volatile var temperament = Temperaments.EQUAL
    @Volatile var temperamentRoot = 0
    @Volatile var transposeSemitones = 0
    @Volatile var useFeiten = false
    @Volatile var useSweetened = false
    @Volatile var useStretch = false
    @Volatile var customStretch: Map<Int, Double>? = null   // captured curve wins over Railsback
    @Volatile var calibrationCents = 0.0
    @Volatile var noiseGate = 0.005
    @Volatile var precisionSmoothing = true
    @Volatile var autoOctave = true
    @Volatile var adaptiveGate = true
    @Volatile var overpullPct = 0.0
    /** Player's per-string habits; win over sweetened and Feiten. */
    @Volatile var userOffsets: Map<Int, Double> = emptyMap()
    /** Input routing — applied on each start(). Null = system default. */
    @Volatile var preferredMic: AudioDeviceInfo? = null

    private val appContext = context.applicationContext
    private val yin4096 = YinPitchDetector(sampleRate)     // fast path
    private val yin8192 = YinPitchDetector(sampleRate)     // low-note escalation
    private val polyDetector = PolyphonicDetector(sampleRate)
    private val an4096 = SpectrumAnalyzer(sampleRate)      // display + normal probes
    private val an8192 = SpectrumAnalyzer(sampleRate)      // escalated probes
    private val tracker = PitchTracker()

    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var generation = 0

    private var agc: AutomaticGainControl? = null
    private var ns: NoiseSuppressor? = null
    private var aec: AcousticEchoCanceler? = null

    private var lastMidi = Int.MIN_VALUE
    private var smoothedFreq = -1.0
    private var smoothedCents = 0.0
    private var lastTargetMidi = Int.MIN_VALUE
    private val medBuf = DoubleArray(5)
    private var medIdx = 0
    private var medCount = 0
    private var holdUntil = 0L
    private var lastEmit: TunerResult.Mono? = null
    private val polyHold = HashMap<Int, Pair<Long, PolyString>>()
    private var noiseRmsEma = 0.004

    fun onPresetChanged() = resetState()

    private fun resetState() {
        lastMidi = Int.MIN_VALUE
        smoothedFreq = -1.0
        smoothedCents = 0.0
        lastTargetMidi = Int.MIN_VALUE
        medIdx = 0; medCount = 0
        holdUntil = 0
        lastEmit = null
        polyHold.clear()
        tracker.reset()
    }

    @SuppressLint("MissingPermission") // caller checks permission first
    fun start(): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return false
        val ok = runCatching {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, POLY_FRAME * 4))
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) return@runCatching false
            preferredMic?.let { pref -> runCatching { audioRecord?.preferredDevice = pref } }
            monoPrepare()
            audioRecord?.startRecording()
            detachCleanupEffects()
            true
        }.getOrDefault(false)
        if (!ok) { release(); return false }

        val gen = ++generation
        running = true
        resetState()
        thread = Thread({ loop(gen) }, "TunerThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        return true
    }

    private fun monoPrepare() {
        yin4096.prepare(MONO_FRAME)
        yin8192.prepare(POLY_FRAME)
        polyDetector.prepare(POLY_FRAME)
        an4096.prepare(MONO_FRAME)
        an8192.prepare(POLY_FRAME)
        noiseRmsEma = 0.004
    }

    private fun loop(gen: Int) {
        val record = audioRecord ?: return
        val pcm = ShortArray(POLY_FRAME)
        val samples = FloatArray(POLY_FRAME)

        while (running && gen == generation) {
            val frameSize = if (mode == Mode.POLYPHONIC) POLY_FRAME else MONO_FRAME
            val n = record.read(pcm, 0, frameSize)
            // A negative return is an error (device loss/invalidation) — exit.
            if (n < 0) break
            if (n < frameSize) continue

            var energy = 0.0
            for (i in 0 until frameSize) {
                val s = pcm[i] / 32768f
                samples[i] = s
                energy += (s * s).toDouble()
            }
            val rms = sqrt(energy / frameSize)

            if (mode == Mode.POLYPHONIC) {
                if (rms < noiseGate) expirePoly(rms) else emitPoly(samples, rms)
            } else {
                emitMono(record, pcm, rms)
            }
        }
    }

    // ---------------- mono path ----------------

    private fun emitMono(record: AudioRecord, pcm: ShortArray, rms: Double) {
        val now = System.currentTimeMillis()

        // ---- adaptive gate: slow minimum-statistics noise tracking ----
        if (rms < noiseRmsEma) noiseRmsEma = 0.8 * noiseRmsEma + 0.2 * rms
        else noiseRmsEma += 0.001 * (rms - noiseRmsEma)
        val effectiveGate = if (adaptiveGate) maxOf(noiseGate, noiseRmsEma * 2.2) else noiseGate
        if (rms < effectiveGate) { holdOrSilence(now, rms); return }

        // ---- stage 1: fast YIN on 4096, DC/rumble removed ----
        val fast = FloatArray(MONO_FRAME) { i -> pcm[i] / 32768f }
        var mean = 0.0
        for (v in fast) mean += v
        mean /= MONO_FRAME
        for (i in fast.indices) fast[i] -= mean.toFloat()

        var pitch = yin4096.detect(fast)
        var escalated = false
        var full: FloatArray? = null

        // ---- stage 2: escalate low/failed frames to an 8192 window ----
        if (pitch == null || pitch.frequency < LOW_NOTE_HZ) {
            val n2 = record.read(pcm, MONO_FRAME, MONO_FRAME)
            if (n2 == MONO_FRAME) {
                val f8 = FloatArray(POLY_FRAME) { i -> pcm[i] / 32768f }
                var m2 = 0.0
                for (v in f8) m2 += v
                m2 /= POLY_FRAME
                for (i in f8.indices) f8[i] -= m2.toFloat()
                yin8192.detect(f8)?.let { pitch = it; escalated = true; full = f8 }
            }
        }
        val p = pitch ?: run { holdOrSilence(now, rms); return }

        // ---- spectra: 4096 always drives the display; 8192 drives probes ----
        an4096.run(fast)
        val active = if (escalated) { an8192.run(full!!); an8192 } else an4096
        val noise = active.noiseFloor()

        // ---- stage 3: octave repair via harmonic sieve ----
        var trackFreq = p.frequency
        if (autoOctave) {
            trackFreq = PitchRefine.refineOctave(
                { fr -> active.probe(fr).magnitude }, noise, trackFreq, 27.0)
        }

        // ---- stage 4: confidence gate (rejects junk frames) ----
        val fundMag = active.probe(trackFreq).magnitude.toDouble()
        val snrDb = if (fundMag > 0.0 && noise > 0f)
            (20.0 * log10(fundMag / noise)).coerceIn(0.0, 60.0) else 0.0
        var support = 0
        for (n in 2..5) if (active.probe(trackFreq * n).magnitude > noise * PitchRefine.HARMONIC_K) support++
        val confidence = PitchRefine.confidence(p.clarity, snrDb, support / 4.0)
        if (confidence < PitchRefine.MIN_CONFIDENCE) { holdOrSilence(now, rms); return }

        // ---- note identity via multi-hypothesis tracker ----
        val decision = tracker.update(trackFreq, p.clarity, a4)
        val stableMidi = decision.midi
        val shownFreq = decision.frequency
        smoothedFreq = shownFreq
        lastMidi = stableMidi

        val harmonics = analyzeHarmonics(active, shownFreq)
        val inharmonicity = estimateInharmonicity(harmonics)

        val etTarget = NoteUtil.frequency(stableMidi.toDouble(), a4)
        val rawCents = NoteUtil.centsFrom(shownFreq, etTarget)

        val target = resolveTarget(stableMidi)
        if (target.midi != lastTargetMidi) { medIdx = 0; medCount = 0; smoothedCents = target.cents }
        lastTargetMidi = target.midi

        val smoothCents: Double = if (precisionSmoothing) {
            medBuf[medIdx % 5] = target.cents
            medIdx++; medCount = minOf(medCount + 1, 5)
            val median = medBuf.copyOf(medCount).sorted()[medCount / 2]
            smoothedCents = 0.5 * smoothedCents + 0.5 * median
            smoothedCents
        } else {
            smoothedCents = target.cents
            target.cents
        }

        holdUntil = now + HOLD_MS
        val result = TunerResult.Mono(
            frequency = shownFreq,
            noteName = target.name,
            targetFrequency = target.freq,
            cents = smoothCents,
            rawCents = rawCents,
            stringIndex = target.stringIndex,
            clarity = p.clarity,
            rms = rms,
            held = false,
            midi = target.midi,
            spectrum = an4096.bandSpectrum(),
            harmonics = harmonics,
            octaveCorrected = abs(1200.0 * log2(trackFreq / p.frequency)) > 600.0,
            inharmonicity = inharmonicity,
            confidence = confidence,
            snrDb = snrDb,
            lowNoteMode = escalated,
            noteChanged = decision.noteChanged,
            overpullApplied = target.overpull
        )
        lastEmit = result
        onResult(result)
    }

    /** Relative level + cents deviation of the partial series via FFT peak probing. */
    private fun analyzeHarmonics(sa: SpectrumAnalyzer, f0: Double): List<TunerResult.Harmonic> {
        val nyquist = sampleRate / 2.0
        val h1 = sa.probe(f0).magnitude.coerceAtLeast(1e-9f)
        val ratios = if (preset.partials.isNotEmpty())
            preset.partials.take(8) else (1..8).map { it.toDouble() }
        val out = ArrayList<TunerResult.Harmonic>(ratios.size)
        ratios.forEachIndexed { idx, r ->
            val f = f0 * r
            if (f > nyquist * 0.95) return@forEachIndexed
            val pr = sa.probe(f)
            val ampDb = (20.0 * log10((pr.magnitude / h1).toDouble())).coerceIn(-60.0, 0.0)
            val cents = if (pr.magnitude > h1 * 0.02f)
                1200.0 * log2(pr.frequency / f) else Double.NaN
            out.add(TunerResult.Harmonic(idx + 1, r, ampDb, cents))
        }
        return out
    }

    /** Stiffness coefficient B from f_n = n·f₀·√(1+B·n²), n = 2…6, integer ratios only. */
    private fun estimateInharmonicity(harmonics: List<TunerResult.Harmonic>): Double {
        var sum = 0.0; var cnt = 0
        for (h in harmonics) {
            val n = h.ratio
            if (n in 2.0..6.0 && n == kotlin.math.round(n) && !h.centsDeviation.isNaN()) {
                val r2 = 2.0.pow(h.centsDeviation / 600.0)
                val b = (r2 - 1.0) / (n * n)
                if (b > 0.0) { sum += b; cnt++ }
            }
        }
        return if (cnt > 0) sum / cnt else 0.0
    }

    private class TargetInfo(
        val midi: Int, val name: String, val freq: Double,
        val cents: Double, val stringIndex: Int, val overpull: Double = 0.0
    )

    private fun resolveTarget(nearestMidi: Int): TargetInfo {
        val base = resolveBase(nearestMidi)
        val over = Overpull.apply(base.cents, overpullPct)
        if (over <= 0.0) return base
        val f = base.freq * 2.0.pow(over / 1200.0)
        return TargetInfo(base.midi, base.name, f,
            NoteUtil.centsFrom(smoothedFreq, f), base.stringIndex, over)
    }

    private fun resolveBase(nearestMidi: Int): TargetInfo {
        if (mode == Mode.STRING && preset.strings.isNotEmpty()) {
            val idx = if (lockedString in preset.strings.indices) lockedString
                      else nearestStringIndex(smoothedFreq)
            val midi = preset.strings[idx]
            val f = temperedFreq(midi, stringOffset(idx))
            return TargetInfo(midi, NoteUtil.noteName(midi), f,
                NoteUtil.centsFrom(smoothedFreq, f), idx)
        }
        // Chromatic — transposing winds display written pitch
        val written = nearestMidi + transposeSemitones
        val f = temperedFreq(written, 0.0)
        return TargetInfo(written, NoteUtil.noteName(written), f,
            NoteUtil.centsFrom(smoothedFreq, f), -1)
    }

    /** Personal offsets take precedence; sweetened is the fallback; Feiten last. */
    private fun stringOffset(idx: Int): Double =
        userOffsets[idx]
            ?: if (useSweetened && idx < preset.sweetened.size) preset.sweetened[idx]
            else feitenFor(idx)

    private fun feitenFor(stringIndex: Int): Double =
        if (useFeiten && preset.id == "guitar_std" && stringIndex in FEITEN.indices)
            FEITEN[stringIndex] else 0.0

    private fun temperedFreq(midi: Int, extraCents: Double): Double {
        val pc = ((midi % 12) + 12) % 12
        val stretch = if (useStretch)
            customStretch?.let { StretchCurves.interpolate(it, midi) }
                ?: StretchCurves.railsbackCents(midi)
        else 0.0
        val total = temperament.centsFor(pc, temperamentRoot) + calibrationCents + extraCents + stretch
        return NoteUtil.frequency(midi.toDouble(), a4) * 2.0.pow(total / 1200.0)
    }

    private fun nearestStringIndex(freq: Double): Int {
        var best = 0
        var bestCents = Double.MAX_VALUE
        preset.strings.forEachIndexed { i, midi ->
            val c = kotlin.math.abs(NoteUtil.centsFrom(freq, NoteUtil.frequency(midi.toDouble(), a4)))
            if (c < bestCents) { bestCents = c; best = i }
        }
        return best
    }

    /** Keep the last reading on screen (dimmed) for HOLD_MS, then idle. */
    private fun holdOrSilence(now: Long, rms: Double) {
        if (now < holdUntil) return
        val e = lastEmit
        val out = if (e == null)
            TunerResult.Mono(null, null, null, null, null, -1, 0.0, rms, false)
        else
            e.copy(held = true, rms = rms)
        lastEmit = out
        onResult(out)
    }

    // ---------------- poly path ----------------

    private fun emitPoly(samples: FloatArray, rms: Double) {
        // Poly targets respect temperament + calibration + per-string offsets,
        // so poly cents match the string-mode readout.
        val targets = preset.strings.mapIndexed { i, midi -> temperedFreq(midi, stringOffset(i)) }
        val readings = polyDetector.detect(samples, targets, sensitivity)
        val now = System.currentTimeMillis()

        for (r in readings) {
            val prev = polyHold[r.stringIndex]
            val cents = if (prev != null) prev.second.cents * 0.6 + r.cents * 0.4 else r.cents
            polyHold[r.stringIndex] = now to PolyString(
                r.stringIndex, NoteUtil.noteName(preset.strings[r.stringIndex]), cents, r.strength)
        }
        polyHold.entries.removeAll { now - it.value.first > POLY_HOLD_MS }
        onResult(TunerResult.Poly(polyHold.values.map { it.second }.sortedBy { it.stringIndex }, rms))
    }

    private fun expirePoly(rms: Double) {
        val now = System.currentTimeMillis()
        val before = polyHold.size
        polyHold.entries.removeAll { now - it.value.first > POLY_HOLD_MS }
        // Only emit on an actual change — no empty-result spam while silent.
        if (polyHold.size != before) {
            onResult(TunerResult.Poly(polyHold.values.map { it.second }.sortedBy { it.stringIndex }, rms))
        }
    }

    /** AGC/NS/AEC "helpfully" level out and filter the mic — poison for pitch detection. */
    private fun detachCleanupEffects() {
        val session = audioRecord?.audioSessionId ?: return
        runCatching {
            if (AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(session).also { it.enabled = false }
            }
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(session).also { it.enabled = false }
            }
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(session).also { it.enabled = false }
            }
        }
    }

    fun stop() {
        if (!running && audioRecord == null) return
        running = false
        generation++
        val record = audioRecord; audioRecord = null
        val t = thread; thread = null
        val a = agc; val n = ns; val e = aec
        agc = null; ns = null; aec = null
        Thread {
            try { record?.stop() } catch (_: Exception) {}
            try { t?.join(1000) } catch (_: InterruptedException) {}
            record?.release()
            runCatching { a?.release(); n?.release(); e?.release() }
        }.apply { isDaemon = true; start() }
    }

    private fun release() {
        audioRecord?.release()
        audioRecord = null
    }
}
