package com.example.metrotune.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

object MetronomeEngine {

    const val SAMPLE_RATE = 44100
    const val MIN_BPM = 30
    const val MAX_BPM = 250

    enum class TrainerMode { OFF, RAMP, GAP }
    enum class BeatAccent { ACCENT, NORMAL, SOFT, MUTE }

    data class TrainerConfig(
        val mode: TrainerMode = TrainerMode.OFF,
        val rampStepBpm: Int = 2,
        val rampEveryBars: Int = 4,
        val gapPlayBars: Int = 2,
        val gapMuteBars: Int = 2
    )

    interface Listener {
        fun onBeat(beat: Int, countIn: Boolean = false) {}
        fun onStateChanged(running: Boolean) {}
        fun onTrainerTick(liveBpm: Int, muted: Boolean) {}
        fun onSetlistAdvance(index: Int, item: SetlistItem?, ended: Boolean) {}
    }

    /** MIDI-clock sink — called on the main thread, sample-aligned via the audio clock. */
    interface ClockSink {
        fun tick()      // one of 24 per quarter note
        fun start()     // transport start (FA)
        fun stop()      // transport stop (FC)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val handler = Handler(Looper.getMainLooper())

    @Volatile var bpm = 120
    @Volatile var beatsPerBar = 4
    @Volatile var subdivision = 1
    @Volatile var trainer = TrainerConfig()
    @Volatile var pattern: List<BeatAccent> =
        listOf(BeatAccent.ACCENT, BeatAccent.NORMAL, BeatAccent.NORMAL, BeatAccent.NORMAL)
    @Volatile var countInBars = 0
    @Volatile var polyVoice = 0
    @Volatile var polyAgainst = 0

    /** Non-empty setlist drives tempo/feel at bar boundaries (overrides the trainer). */
    @Volatile var setlist: List<SetlistItem> = emptyList()
    @Volatile var setlistLoop = false

    @Volatile var clockSink: ClockSink? = null

    @Volatile var liveBpm = 120; private set
    @Volatile var muted = false; private set
    var isRunning = false; private set

    private var audioTrack: AudioTrack? = null
    private var thread: Thread? = null
    private var sessionStartStamp = 0L
    @Volatile private var generation = 0

    @Volatile private var userPattern: List<BeatAccent> = pattern

    // Click samples — replaced wholesale by setVolumeDb(). Reference assignment
    // is atomic, so the audio thread always sees a complete array.
    private var accentClick = makeClick(1700.0, 60.0, 1.0)
    private var normalClick = makeClick(1100.0, 50.0, 0.75)
    private var softClick = makeClick(950.0, 38.0, 0.40)
    private var subClick = makeClick(800.0, 35.0, 0.45)
    private var polyClick = makeClick(600.0, 45.0, 0.55)

    /** Click volume in dB (−24…0). Regenerates the cached click samples. */
    fun setVolumeDb(db: Int) {
        val g = 10.0.pow(db / 20.0).coerceIn(0.06, 1.0)
        accentClick = makeClick(1700.0, 60.0, g)
        normalClick = makeClick(1100.0, 50.0, 0.75 * g)
        softClick = makeClick(950.0, 38.0, 0.40 * g)
        subClick = makeClick(800.0, 35.0, 0.45 * g)
        polyClick = makeClick(600.0, 45.0, 0.55 * g)
    }

    /** UI edits go through here so setlist parts without a pattern inherit them. */
    fun setPattern(p: List<BeatAccent>) { pattern = p; userPattern = p }

    fun addListener(l: Listener) { listeners.add(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    fun start(): Boolean {
        if (isRunning) return true
        val minBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBytes, SAMPLE_RATE / 2))
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }
        val track = try { builder.build() } catch (_: Exception) { null } ?: return false
        audioTrack = track
        return try {
            track.play()
            sessionStartStamp = SystemClock.elapsedRealtime()
            liveBpm = bpm
            muted = false
            userPattern = pattern
            val gen = ++generation
            isRunning = true
            thread = Thread({ runLoop(gen) }, "MetronomeThread").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
            val sink0 = clockSink
            handler.post { sink0?.start() }
            handler.post { listeners.forEach { it.onStateChanged(true) } }
            true
        } catch (_: Exception) {
            isRunning = false
            track.release(); audioTrack = null
            false
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        generation++
        val practicedSec = (SystemClock.elapsedRealtime() - sessionStartStamp) / 1000
        handler.removeCallbacksAndMessages(null)

        val track = audioTrack; audioTrack = null
        val t = thread; thread = null
        Thread {
            try { t?.join(1000) } catch (_: InterruptedException) {}
            track?.let {
                try { it.stop() } catch (_: IllegalStateException) {}
                it.release()
            }
        }.apply { isDaemon = true; start() }

        muted = false
        liveBpm = bpm
        val sink = clockSink
        handler.post { sink?.stop() }
        if (PracticeSessionManager.autoCountMetronome &&
            !PracticeSessionManager.isRunning && practicedSec > 0
        ) {
            PracticeSessionManager.addSeconds(practicedSec)
        }
        // Session history — recordSession filters < 30 s itself.
        PracticeSessionManager.recordSession(practicedSec, "Metronome")
        handler.post { listeners.forEach { it.onStateChanged(false) } }
    }

    // ---------------- audio loop ----------------

    private fun runLoop(gen: Int) {
        if (polyVoice > 0 && polyAgainst > 0) { runPolyLoop(gen); return }
        val track = audioTrack ?: return
        val silence = ShortArray(1024)
        var framesWritten = 0L
        var carry = 0.0
        var beat = 0
        var sub = 0
        var phaseBar = 0
        var inCountIn = countInBars > 0
        var currentBpm = bpm
        var mutedNow = false
        var curBeats = beatsPerBar
        var curSub = subdivision
        var setlistIndex = -1

        // A loaded setlist starts at its first item's feel — count-in included.
        if (setlist.isNotEmpty()) {
            val first = setlist[0]
            currentBpm = first.bpm.coerceIn(MIN_BPM, MAX_BPM)
            curBeats = first.beatsPerBar.coerceIn(1, 12)
            curSub = first.subdivision.coerceIn(1, 4)
        }

        while (isRunning && gen == generation) {
            if (sub == 0 && beat == 0) {
                if (inCountIn && phaseBar >= countInBars) {
                    inCountIn = false
                    phaseBar = 0
                    if (setlist.isEmpty()) {
                        currentBpm = bpm
                        curBeats = beatsPerBar
                        curSub = subdivision
                    }
                    mutedNow = false
                }
                if (!inCountIn) {
                    if (setlist.isNotEmpty()) {
                        val idx = SetlistMath.locate(phaseBar, setlist, setlistLoop).first
                        if (idx < 0) {
                            handler.post {
                                listeners.forEach { it.onSetlistAdvance(-1, null, true) }
                            }
                            handler.post { stop() }
                            return
                        }
                        if (idx != setlistIndex) {
                            setlistIndex = idx
                            val item = setlist[idx]
                            currentBpm = item.bpm.coerceIn(MIN_BPM, MAX_BPM)
                            curBeats = item.beatsPerBar.coerceIn(1, 12)
                            curSub = item.subdivision.coerceIn(1, 4)
                            pattern = item.pattern ?: userPattern
                            mutedNow = false
                            bpm = currentBpm
                            beatsPerBar = curBeats
                            subdivision = curSub
                            handler.post {
                                listeners.forEach { it.onSetlistAdvance(idx, item, false) }
                            }
                        }
                    } else {
                        curBeats = beatsPerBar
                        curSub = subdivision
                        val (nb, nm) = TrainerMath.stateFor(phaseBar, bpm, trainer)
                        if (nb != currentBpm || nm != mutedNow) {
                            currentBpm = nb
                            mutedNow = nm
                            postTrainer(currentBpm, mutedNow)
                        }
                    }
                }
            }

            val samplesPerSub = SAMPLE_RATE * 60.0 / (currentBpm * curSub)
            var slot = floor(samplesPerSub).toInt()
            carry += samplesPerSub - slot
            if (carry >= 1.0) { slot++; carry -= 1.0 }

            val mainClick: ShortArray? = if (inCountIn) {
                if (beat == 0) accentClick else normalClick
            } else {
                val acc = pattern.getOrNull(beat)
                    ?: (if (beat == 0) BeatAccent.ACCENT else BeatAccent.NORMAL)
                when (acc) {
                    BeatAccent.ACCENT -> accentClick
                    BeatAccent.NORMAL -> normalClick
                    BeatAccent.SOFT -> softClick
                    BeatAccent.MUTE -> null
                }
            }

            val slotStart = framesWritten
            var writtenThisSub = 0
            if (!mutedNow) {
                val click = if (sub == 0) mainClick else subClick
                if (click != null) {
                    val clickLen = min(click.size, slot).coerceAtLeast(1)
                    val written = track.write(click, 0, clickLen)
                    if (written <= 0) break
                    framesWritten += written
                    writtenThisSub = written
                }
                if (sub == 0) scheduleVisual(beat, framesWritten, inCountIn)
            } else if (sub == 0) {
                scheduleVisual(beat, framesWritten, inCountIn)
            }

            var remaining = slot - writtenThisSub
            while (remaining > 0 && isRunning) {
                val n = min(remaining, silence.size)
                val w = track.write(silence, 0, n)
                if (w <= 0) return
                framesWritten += w
                remaining -= w
            }

            // MIDI clock: 24 PPQ, quarter note = the beat (not the subdivision)
            clockSink?.let { sink ->
                val clocks = 24 / curSub
                for (k in 0 until clocks) {
                    scheduleClock(sink, slotStart + slot.toLong() * k / clocks)
                }
            }

            sub++
            if (sub >= curSub) {
                sub = 0
                beat = (beat + 1) % curBeats
                if (beat == 0) phaseBar++
            }
        }
    }

    /**
     * Polyrhythm loop. Deliberately ignores the setlist — poly and setlists
     * are different drills. Clock sink is not driven here (no single grid).
     */
    private fun runPolyLoop(gen: Int) {
        val track = audioTrack ?: return
        val silence = ShortArray(1024)
        var framesWritten = 0L
        var cycleIndex = 0
        var inCountIn = countInBars > 0
        var currentBpm = bpm
        var mutedNow = false

        fun writeSilence(frames: Int): Boolean {
            var remaining = frames
            while (remaining > 0) {
                if (!isRunning || gen != generation) return false
                val n = min(remaining, silence.size)
                val w = track.write(silence, 0, n)
                if (w <= 0) return false
                framesWritten += w
                remaining -= w
            }
            return true
        }
        fun writeClick(click: ShortArray): Boolean {
            if (!isRunning || gen != generation) return false
            if (mutedNow && !inCountIn) return true   // gap trainer: skip audio, keep visuals
            val w = track.write(click, 0, click.size)
            if (w <= 0) return false
            framesWritten += w
            return true
        }

        while (isRunning && gen == generation) {
            if (inCountIn && cycleIndex >= countInBars) { inCountIn = false; cycleIndex = 0 }
            if (!inCountIn) {
                val (nb, nm) = TrainerMath.stateFor(cycleIndex, bpm, trainer)
                if (nb != currentBpm || nm != mutedNow) {
                    currentBpm = nb; mutedNow = nm
                    postTrainer(currentBpm, mutedNow)
                }
            }

            val cycleStart = framesWritten
            val len = SAMPLE_RATE * 60.0 / currentBpm
            val a = polyVoice.coerceAtLeast(1)
            val b = if (inCountIn) 0 else polyAgainst.coerceAtLeast(1)

            for (e in PolyMath.events(a, b, len)) {
                val target = cycleStart + Math.round(e.time)
                if (target > framesWritten && !writeSilence((target - framesWritten).toInt())) return
                if (e.voice == 0) {
                    if (!writeClick(if (inCountIn || e.index == 0) accentClick else normalClick)) return
                    scheduleVisual(e.index, framesWritten, inCountIn)
                } else {
                    if (!writeClick(polyClick)) return
                }
            }
            val end = cycleStart + Math.round(len)
            if (end > framesWritten && !writeSilence((end - framesWritten).toInt())) return
            cycleIndex++
        }
    }

    private fun postTrainer(liveBpmNow: Int, mutedNow: Boolean) {
        handler.post {
            liveBpm = liveBpmNow
            muted = mutedNow
            listeners.forEach { it.onTrainerTick(liveBpm, muted) }
        }
    }

    private fun scheduleVisual(beat: Int, framesWritten: Long, countIn: Boolean) {
        val track = audioTrack ?: return
        val played = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        val aheadMs = (framesWritten - played) * 1000.0 / SAMPLE_RATE
        val delay = aheadMs.toLong().coerceIn(0, 400)
        handler.postDelayed({
            listeners.forEach { it.onBeat(beat, countIn) }
        }, delay)
    }

    private fun scheduleClock(sink: ClockSink, atFrame: Long) {
        val track = audioTrack ?: return
        val played = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        val delay = ((atFrame - played) * 1000.0 / SAMPLE_RATE).toLong().coerceIn(0, 400)
        handler.postDelayed({ sink.tick() }, delay)
    }

    private fun makeClick(freqHz: Double, lengthMs: Double, amplitude: Double): ShortArray {
        val length = (SAMPLE_RATE * lengthMs / 1000.0).toInt()
        val decay = lengthMs / 1000.0 / 5.0
        return ShortArray(length) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val attack = 1.0 - exp(-i.toDouble() / (SAMPLE_RATE * 0.0008))
            val envelope = exp(-t / decay) * attack
            (amplitude * envelope * sin(2.0 * PI * freqHz * t) * 32767.0).toInt().toShort()
        }
    }
}
