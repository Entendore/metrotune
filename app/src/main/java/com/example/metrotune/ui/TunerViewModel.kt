package com.example.metrotune.ui

import android.app.Application
import android.media.AudioDeviceInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.metrotune.audio.AuditReport
import com.example.metrotune.audio.AudioConfig
import com.example.metrotune.audio.AudioFocusHelper
import com.example.metrotune.audio.BecomingNoisyWatcher
import com.example.metrotune.audio.BleMidiServer
import com.example.metrotune.audio.CustomInstruments
import com.example.metrotune.audio.CustomTemperaments
import com.example.metrotune.audio.DrillRecords
import com.example.metrotune.audio.EarTraining
import com.example.metrotune.audio.InstrumentLibrary
import com.example.metrotune.audio.InstrumentPreset
import com.example.metrotune.audio.IntonationDrill
import com.example.metrotune.audio.IntonationWizard
import com.example.metrotune.audio.MicRepository
import com.example.metrotune.audio.MidiOut
import com.example.metrotune.audio.NoteUtil
import com.example.metrotune.audio.PracticeSessionManager
import com.example.metrotune.audio.ReferenceToneGenerator
import com.example.metrotune.audio.Settings
import com.example.metrotune.audio.StretchCurves
import com.example.metrotune.audio.StringStability
import com.example.metrotune.audio.Temperament
import com.example.metrotune.audio.Temperaments
import com.example.metrotune.audio.TunerEngine
import com.example.metrotune.audio.TuningCurves
import com.example.metrotune.audio.TuningDetect
import com.example.metrotune.audio.TuningSession
import com.example.metrotune.data.DrillRepository
import com.example.metrotune.data.HealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

@HiltViewModel
class TunerViewModel @Inject constructor(
    app: Application,
    private val drills: DrillRepository,
    private val health: HealthRepository
) : AndroidViewModel(app) {

    companion object {
        val A4_VALUES = listOf(415.0, 430.0, 432.0, 435.0, 438.0, 440.0,
            441.0, 442.0, 443.0, 444.0, 445.0, 475.0)
        val TRANSPOSE_NAMES = listOf(
            "Concert pitch", "B♭ +2", "E♭ +9", "F +7", "B♭ (tenor) +14", "E♭ (bari) +21",
            "B♭ (contra) +26", "G +5", "8va down +12", "8va up −12", "5th up −7", "m3 up −3")
        val TRANSPOSE_VALUES = intArrayOf(0, 2, 9, 7, 14, 21, 26, 5, 12, -12, -7, -3)
        val TOLERANCE_VALUES = intArrayOf(1, 3, 5, 10)
        private const val ADV_FRAMES = 10
        private const val CAL_FRAMES = 12
        private const val CAL_TIMEOUT_MS = 10_000L
    }

    data class DrillUi(
        val holding: Boolean, val currentMs: Long,
        val bestMs: Long, val inTuneMs: Long, val attempts: Int
    )

    data class WizardUi(
        val kind: Int,              // 0 = saddle (12th fret), 1 = nut (1st fret)
        val stringIndex: Int, val phase: Int, val samples: Int, val needed: Int,
        val stringName: String, val results: List<ResultLine>
    )
    data class ResultLine(val stringName: String, val devCents: Double, val offsetMm: Double)

    data class SessionState(
        val instrumentIds: List<String>,
        val instrumentIndex: Int,
        val flatDone: Int
    )

    data class EarUi(
        val level: Int = 0, val active: Boolean = false, val listening: Boolean = false,
        val awaiting: Boolean = false, val round: Int = 0, val correct: Int = 0,
        val streak: Int = 0, val best: Int = 0
    )

    data class HealthRow(val name: String, val avg: Double, val last: Double,
                         val count: Int, val flagged: Boolean)

    data class UiState(
        val instrument: InstrumentPreset = InstrumentLibrary.CHROMATIC,
        val mode: TunerEngine.Mode = TunerEngine.Mode.CHROMATIC,
        val lockedString: Int = -1,
        val a4: Double = 440.0,
        val a4Index: Int = 5,
        val temperament: Temperament = Temperaments.EQUAL,
        val temperamentIndex: Int = 0,
        val tonicIndex: Int = 0,
        val calibCents: Double = 0.0,
        val toleranceIndex: Int = 2,
        val toleranceCents: Double = 5.0,
        val transposeIndex: Int = 0,
        val gateProgress: Int = 14,
        val autoOctave: Boolean = true,
        val adaptiveGate: Boolean = true,
        val feiten: Boolean = false,
        val sweetened: Boolean = false,
        val stretch: Boolean = false,
        val overpullPct: Int = 0,
        val headDevs: List<Double?> = emptyList(),
        val headActive: Int = -1,
        val poly: List<TunerEngine.PolyString> = emptyList(),
        val reading: TunerEngine.TunerResult.Mono? = null,
        val heldReading: Boolean = false,
        val lowNoteMode: Boolean = false,
        val octaveCorrected: Boolean = false,
        val captureRunning: Boolean = false,
        val tonePlaying: Boolean = false,
        val micGranted: Boolean = false,
        val bleActive: Boolean = false,
        val bleStatus: String = "",
        val droneSemi: Int = 0,
        val calibrating: Boolean = false,
        val drill: DrillUi? = null,
        val drillPb: Pair<Int, Long>? = null,
        val auditActive: Boolean = false,
        val auditProgress: Pair<Int, Int>? = null,
        val simpleMode: Boolean = true,
        val session: SessionState? = null,
        val wizard: WizardUi? = null,
        val activeCurve: Map<Int, Double>? = null,
        val activeCurveName: String? = null,
        val curveCapture: Boolean = false,
        val curveCount: Int = 0,
        val frozenTarget: Double? = null,
        val ear: EarUi = EarUi(),
        val interval: com.example.metrotune.audio.IntervalCheck.Info? = null
    )

    sealed class Msg {
        data class StringInTune(val index: Int, val name: String) : Msg()
        object AllTuned : Msg()
        data class Calibrated(val a4: Double) : Msg()
        object CalibrateTimeout : Msg()
        object MicBusy : Msg()
        object MicDisconnected : Msg()
        data class NewRecord(val note: String, val ms: Long) : Msg()
        object DrillNoTarget : Msg()
        data class ShareAudit(val subject: String, val csv: String) : Msg()
        object CustomInvalid : Msg()
        object FocusDenied : Msg()
        data class SessionStarted(val first: String, val count: Int) : Msg()
        data class SessionAdvance(val name: String, val index: Int, val count: Int) : Msg()
        object SessionDone : Msg()
        object SessionCancelled : Msg()
        data class RecallPitch(val a4: Double) : Msg()
        object WizardDone : Msg()
        data class SuggestTuning(val name: String, val presetId: String) : Msg()
        data class EarFeedback(val correct: Boolean, val actualCents: Double, val streak: Int) : Msg()
    }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _msgs = MutableSharedFlow<Msg>(extraBufferCapacity = 16)
    val msgs: SharedFlow<Msg> = _msgs.asSharedFlow()

    private val context = app.applicationContext
    private var engine: TunerEngine? = null
    private val tone = ReferenceToneGenerator()
    private val focus = AudioFocusHelper(app) { stopTone(restart = true) }
    private val noisy = BecomingNoisyWatcher(context) { stopTone(restart = true) }

    // BLE
    private var ble: BleMidiServer? = null
    private var lastMidiSent = -1

    // drill
    private var drill: IntonationDrill? = null
    private var drillTarget = -1
    private var drillStartMs = 0L

    // calibration
    private var calibSamples = mutableListOf<Double>()
    private var calibDeadline = 0L

    // auto-advance / audit / session
    private var advStreak = 0
    private var advString = -1
    private val auditResults = mutableListOf<AuditReport.AuditRow>()
    private val sessionResults = mutableListOf<AuditReport.SessionRow>()

    // setup wizard
    private var wizard: IntonationWizard? = null
    private var wizardKind = 0
    private val wizardResults = mutableListOf<IntonationWizard.Result>()

    // stability (string diagnostics)
    private var stability = StringStability()

    // tone memory (for parameter refresh while playing)
    private var lastToneMidi = 69
    private var lastToneWave = 0
    private var lastToneVol = 70

    // curves
    private val curveSamples = mutableMapOf<Int, MutableList<Double>>()
    private val workingCurve = mutableMapOf<Int, Double>()
    private var curveRestoreStretch = false

    // ear training
    private var earJob: Job? = null
    private var earRound: EarTraining.Round? = null

    // detection
    private val recentNotes = ArrayDeque<Int>()
    private val dismissedDetect = mutableSetOf<String>()
    private var lastSuggestAt = 0L
    private var prevStable: Pair<Int, Double>? = null

    init {
        Settings.init(context)
        val a4Idx = Settings.a4Index.coerceIn(0, A4_VALUES.size - 1)
        val calibrated = Settings.calibratedA4
        val a4 = if (calibrated > 0) calibrated.toDouble() else A4_VALUES[a4Idx]
        val preset = findPreset(Settings.instrumentId)
        val prof = Settings.profile(preset.id)
        val mode = prof.mode.takeIf { it.isNotEmpty() }
            ?.let { runCatching { TunerEngine.Mode.valueOf(it) }.getOrNull() }
            ?: TunerEngine.Mode.CHROMATIC
        val temps = temperaments()
        val tempIdx = temps.indexOfFirst { it.id == Settings.temperamentId }.coerceAtLeast(0)
        val tolIdx = TOLERANCE_VALUES.indexOf(Settings.toleranceCents).coerceAtLeast(0)
        _ui.value = UiState(
            instrument = preset,
            mode = if (preset.supportsStringModes) mode else TunerEngine.Mode.CHROMATIC,
            lockedString = if (prof.lockedString in preset.strings.indices) prof.lockedString else -1,
            a4 = a4, a4Index = a4Idx,
            temperament = temps[tempIdx], temperamentIndex = tempIdx,
            tonicIndex = Settings.tonicIndex,
            calibCents = Settings.calibrationCents.toDouble(),
            toleranceIndex = tolIdx, toleranceCents = TOLERANCE_VALUES[tolIdx].toDouble(),
            transposeIndex = effectiveTransposeIndex(preset, prof.transposeIndex),
            gateProgress = Settings.gateDb,
            autoOctave = Settings.autoOctave,
            adaptiveGate = Settings.adaptiveGate,
            feiten = prof.useFeiten,
            sweetened = if (preset.sweetened.isNotEmpty()) Settings.sweetened else false,
            stretch = if (preset.id == "piano88") Settings.stretch else false,
            overpullPct = if (preset.id == "piano88") Settings.overpullPct else 0,
            simpleMode = Settings.simpleMode,
            headDevs = MutableList(preset.strings.size) { null }
        )
        val curveId = Settings.activeCurveId
        if (curveId.isNotEmpty()) {
            TuningCurves.byId(context, curveId)?.let { c ->
                _ui.update { it.copy(activeCurve = c.points, activeCurveName = c.name) }
            }
        }
        if (_ui.value.simpleMode) applySimpleDefaults()
        viewModelScope.launch { refreshPb() }
    }

    // ---------------- instrument resolution / custom instruments ----------------

    private fun findPreset(id: String): InstrumentPreset =
        CustomInstruments.all(context).map { CustomInstruments.toPreset(it) }
            .firstOrNull { it.id == id }
            ?: InstrumentLibrary.byId(id)

    fun presets(): List<InstrumentPreset> =
        CustomInstruments.all(context).map { CustomInstruments.toPreset(it) } +
            InstrumentLibrary.ALL

    fun saveCustom(name: String, spec: String): Boolean {
        val c = CustomInstruments.save(context, name, spec) ?: return false
        selectInstrument(CustomInstruments.toPreset(c))
        return true
    }

    fun deleteCustom(id: String) = CustomInstruments.delete(context, id)

    private fun effectiveTransposeIndex(p: InstrumentPreset, saved: Int): Int {
        val off = p.writtenOffset ?: return saved.coerceIn(0, TRANSPOSE_VALUES.size - 1)
        return TRANSPOSE_VALUES.indexOf(off).coerceAtLeast(0)
    }

    // ---------------- instrument / mode ----------------

    fun selectInstrument(p: InstrumentPreset) {
        val prof = Settings.profile(p.id)
        val locked = if (prof.lockedString in p.strings.indices) prof.lockedString else -1
        val trIdx = effectiveTransposeIndex(p, prof.transposeIndex)
        Settings.instrumentId = p.id
        Settings.pushRecentInstrument(p.id)
        _ui.update {
            it.copy(
                instrument = p,
                lockedString = locked,
                transposeIndex = trIdx,
                feiten = if (p.id == "guitar_std") it.feiten else false,
                sweetened = if (p.sweetened.isNotEmpty()) it.sweetened else false,
                stretch = if (p.id == "piano88") it.stretch else false,
                overpullPct = if (p.id == "piano88") it.overpullPct else 0,
                mode = if (p.supportsStringModes) it.mode else TunerEngine.Mode.CHROMATIC,
                headDevs = MutableList(p.strings.size) { null },
                headActive = -1,
                poly = emptyList(),
                reading = null, heldReading = false,
                auditActive = false, auditProgress = null,
                interval = null, frozenTarget = null
            )
        }
        prevStable = null
        if (prof.a4 > 0 && abs(prof.a4 - _ui.value.a4) > 0.05)
            _msgs.tryEmit(Msg.RecallPitch(prof.a4.toDouble()))
        persistProfile()
        engine?.let { e ->
            applyConfig(e)
            e.onPresetChanged()
        }
        viewModelScope.launch { refreshPb() }
        if (_ui.value.simpleMode) applySimpleDefaults()
    }

    fun setMode(m: TunerEngine.Mode) {
        _ui.update {
            it.copy(mode = m, reading = null, heldReading = false, poly = emptyList(),
                headActive = -1, interval = null, frozenTarget = null)
        }
        prevStable = null
        engine?.let { e -> applyConfig(e); e.onPresetChanged() }
        persistProfile()
    }

    fun setSimpleMode(b: Boolean) {
        Settings.simpleMode = b
        if (b) applySimpleDefaults()
        _ui.update { it.copy(simpleMode = b) }
    }

    /**
     * Simple mode makes the decisions a beginner shouldn't have to:
     * stringed instruments open in Strings mode with auto-advance on, so the
     * flow is pick → pluck → follow the arrow. Pro restores full control.
     */
    private fun applySimpleDefaults() {
        val s = _ui.value
        if (s.instrument.supportsStringModes) {
            if (s.mode != TunerEngine.Mode.STRING) setMode(TunerEngine.Mode.STRING)
            Settings.autoAdvance = true
        }
    }

    fun setLockedString(i: Int) {
        _ui.update { it.copy(lockedString = i, headActive = if (i >= 0) it.headActive else -1) }
        engine?.lockedString = i
        stability.reset()
        persistProfile()
        viewModelScope.launch { refreshPb() }
    }

    // ---------------- config setters ----------------

    fun setA4Index(idx: Int) {
        val a4 = A4_VALUES[idx]
        Settings.calibratedA4 = -1f          // explicit choice clears the calibration override
        _ui.update { it.copy(a4 = a4, a4Index = idx) }
        engine?.a4 = a4
    }

    fun applyRecalledA4(a4: Double) {
        engine?.a4 = a4
        _ui.update { it.copy(a4 = a4) }
        persistProfile()
    }

    fun temperaments(): List<Temperament> =
        Temperaments.ALL +
            CustomTemperaments.all(context).map { CustomTemperaments.toTemperament(it) }

    fun temperamentNames(): List<String> = temperaments().map { it.name }

    fun setTemperamentIndex(idx: Int) {
        val t = temperaments().getOrNull(idx) ?: return
        _ui.update { it.copy(temperament = t, temperamentIndex = idx) }
        Settings.temperamentId = t.id
        engine?.temperament = t
        refreshToneIfPlaying()
    }

    fun saveCustomTemperament(name: String, offsets: List<Double>): Boolean {
        val c = CustomTemperaments.save(context, name, offsets) ?: return false
        val t = CustomTemperaments.toTemperament(c)
        val idx = temperaments().indexOfFirst { it.id == t.id }
        _ui.update { it.copy(temperament = t, temperamentIndex = idx) }
        engine?.temperament = t
        refreshToneIfPlaying()
        return true
    }

    fun deleteCustomTemperament(id: String) {
        CustomTemperaments.delete(context, id)
        if (_ui.value.temperament.id == id) setTemperamentIndex(0)
    }

    fun setTonicIndex(idx: Int) {
        _ui.update { it.copy(tonicIndex = idx) }
        Settings.tonicIndex = idx
        engine?.temperamentRoot = idx
        refreshToneIfPlaying()
    }

    fun setCalibration(cents: Double) {
        _ui.update { it.copy(calibCents = cents) }
        Settings.calibrationCents = cents.toFloat()
        engine?.calibrationCents = cents
        refreshToneIfPlaying()
    }

    fun setToleranceIndex(idx: Int) {
        _ui.update {
            it.copy(toleranceIndex = idx, toleranceCents = TOLERANCE_VALUES[idx].toDouble())
        }
        Settings.toleranceCents = TOLERANCE_VALUES[idx]
    }

    fun setTransposeIndex(idx: Int) {
        _ui.update { it.copy(transposeIndex = idx) }
        engine?.transposeSemitones = TRANSPOSE_VALUES[idx]
        persistProfile()
    }

    fun setGateProgress(p: Int) {
        _ui.update { it.copy(gateProgress = p) }
        Settings.gateDb = p
        engine?.noiseGate = 10.0.pow((-60 + p) / 20.0)
    }

    fun setAutoOctave(b: Boolean) {
        _ui.update { it.copy(autoOctave = b) }; Settings.autoOctave = b; engine?.autoOctave = b
    }
    fun setAdaptiveGate(b: Boolean) {
        _ui.update { it.copy(adaptiveGate = b) }; Settings.adaptiveGate = b; engine?.adaptiveGate = b
    }
    fun setFeiten(b: Boolean) { _ui.update { it.copy(feiten = b) }; engine?.useFeiten = b; persistProfile() }
    fun setSweetened(b: Boolean) {
        _ui.update { it.copy(sweetened = b) }; Settings.sweetened = b; engine?.useSweetened = b
    }
    fun setStretch(b: Boolean) { _ui.update { it.copy(stretch = b) }; Settings.stretch = b; engine?.useStretch = b }
    fun setOverpull(pct: Int) {
        _ui.update { it.copy(overpullPct = pct) }; Settings.overpullPct = pct
        engine?.overpullPct = pct.toDouble()
    }
    fun setDroneSemi(semi: Int) { _ui.update { it.copy(droneSemi = semi) }; refreshToneIfPlaying() }

    fun setAudit(active: Boolean) {
        val strings = _ui.value.instrument.strings
        val on = active && strings.size >= 6
        auditResults.clear(); advStreak = 0; advString = 0
        _ui.update {
            it.copy(auditActive = on, auditProgress = if (on) 1 to strings.size else null)
        }
        if (on) setLockedString(0)
    }

    // ---------------- personal offsets ----------------

    fun userOffset(idx: Int): Double =
        Settings.userOffsets(_ui.value.instrument.id)[idx] ?: 0.0

    fun adjustUserOffset(idx: Int, cents: Double) {
        Settings.setUserOffset(_ui.value.instrument.id, idx, cents)
        engine?.userOffsets = Settings.userOffsets(_ui.value.instrument.id)
    }

    // ---------------- mic routing ----------------

    fun micDevices(): List<AudioDeviceInfo> = MicRepository.list(context)

    fun setPreferredMic(d: AudioDeviceInfo?) {
        if (d == null) { Settings.micType = -1; Settings.micName = "" }
        else { Settings.micType = d.type; Settings.micName = MicRepository.label(d) }
        engine?.preferredMic = d
    }

    /** Hot-plug: clears a vanished preferred device; returns true if fallback happened. */
    fun handleMicPlugChange(): Boolean {
        val t = Settings.micType
        if (t < 0) return false
        val name = Settings.micName
        val stillThere = MicRepository.list(context)
            .any { it.type == t && MicRepository.label(it) == name }
        if (stillThere) return false
        setPreferredMic(null)
        restartCapture()
        _msgs.tryEmit(Msg.MicDisconnected)
        return true
    }

    // ---------------- capture lifecycle ----------------

    fun setMicGranted(b: Boolean) { _ui.update { it.copy(micGranted = b) } }

    fun startCapture() {
        val s = _ui.value
        if (s.captureRunning || tone.isPlaying || !s.micGranted) return
        if (engine == null)
            engine = TunerEngine(context, AudioConfig.bestSampleRate(context), ::onEngineResult)
        engine?.let { e ->
            applyConfig(e)
            e.preferredMic = currentPreferredMic()
        }
        val ok = runCatching { engine?.start() == true }.getOrDefault(false)
        _ui.update { it.copy(captureRunning = ok) }
        if (!ok) _msgs.tryEmit(Msg.MicBusy)
    }

    fun stopCapture() {
        finishDrillIfActive()
        engine?.stop()
        _ui.update { it.copy(captureRunning = false, reading = null, heldReading = false,
            lowNoteMode = false, octaveCorrected = false) }
    }

    fun restartCapture() {
        if (!_ui.value.captureRunning) return
        stopCapture()
        startCapture()
    }

    private fun currentPreferredMic(): AudioDeviceInfo? {
        val t = Settings.micType
        if (t < 0) return null
        val n = Settings.micName
        return MicRepository.list(context)
            .firstOrNull { it.type == t && MicRepository.label(it) == n }
    }

    private fun applyConfig(e: TunerEngine) {
        val s = _ui.value
        e.a4 = s.a4
        e.mode = s.mode
        e.preset = s.instrument
        e.lockedString = s.lockedString
        e.temperament = s.temperament
        e.temperamentRoot = s.tonicIndex
        e.calibrationCents = s.calibCents
        e.noiseGate = 10.0.pow((-60 + s.gateProgress) / 20.0)
        e.transposeSemitones = TRANSPOSE_VALUES[s.transposeIndex]
        e.useFeiten = s.feiten
        e.useSweetened = s.sweetened
        e.useStretch = s.stretch
        e.customStretch = s.activeCurve
        e.autoOctave = s.autoOctave
        e.adaptiveGate = s.adaptiveGate
        e.overpullPct = s.overpullPct.toDouble()
        e.userOffsets = Settings.userOffsets(s.instrument.id)
        e.precisionSmoothing = true
    }

    // ---------------- reference tone ----------------

    private fun toneFreq(midi: Int): Double {
        val s = _ui.value
        val total = s.temperament.centsFor(((midi % 12) + 12) % 12, s.tonicIndex) +
            s.calibCents +
            if (s.stretch) StretchCurves.interpolate(s.activeCurve ?: emptyMap(), midi) else 0.0
        return NoteUtil.frequency(midi.toDouble(), s.a4) * 2.0.pow(total / 1200.0)
    }

    fun playTone(midi: Int, wavePos: Int, volProgress: Int) {
        if (!focus.request()) { _msgs.tryEmit(Msg.FocusDenied); return }
        if (tone.isPlaying) tone.stop()
        stopCapture()
        lastToneMidi = midi; lastToneWave = wavePos; lastToneVol = volProgress
        Settings.toneWave = wavePos
        Settings.toneVol = volProgress
        val semi = _ui.value.droneSemi
        val freqs = mutableListOf(toneFreq(midi))
        if (semi > 0) freqs += toneFreq(midi + semi)
        val db = (-30.0 + 30.0 * volProgress / 100.0).toFloat()
        tone.start(freqs, db, wavePos)
        noisy.register()
        _ui.update { it.copy(tonePlaying = true) }
    }

    /**
     * Simple-mode one-tap reference: the locked string in Strings mode,
     * otherwise the currently detected note. Returns false when there is
     * no target yet.
     */
    fun playTargetTone(): Boolean {
        val s = _ui.value
        val midi = s.lockedString.takeIf { it in s.instrument.strings.indices }
            ?.let { s.instrument.strings[it] }
            ?: (s.reading?.midi?.takeIf { it > 0 })
            ?: return false
        playTone(midi, lastToneWave, lastToneVol)
        return true
    }

    fun stopTone(restart: Boolean = true) {
        if (!tone.isPlaying && !_ui.value.tonePlaying) return
        tone.stop()
        noisy.unregister()
        focus.abandon()
        _ui.update { it.copy(tonePlaying = false) }
        if (restart && _ui.value.micGranted) startCapture()
    }

    fun refreshToneIfPlaying() {
        if (tone.isPlaying) playTone(lastToneMidi, lastToneWave, lastToneVol)
    }

    fun pauseForBackground() {
        if (tone.isPlaying) stopTone(restart = false)
        if (!_ui.value.bleActive) stopCapture()
    }

    // ---------------- BLE-MIDI out ----------------

    fun setBleEnabled(b: Boolean) {
        if (b) {
            val server = MidiOut.get(context)
            server.channel = Settings.midiChannel
            server.statusListener = { st -> _ui.update { it.copy(bleStatus = st) } }
            val ok = server.start()
            ble = if (ok) server else null
            _ui.update { it.copy(bleActive = ok) }
        } else {
            ble?.let {
                if (lastMidiSent >= 0) { it.sendNoteOff(lastMidiSent); lastMidiSent = -1 }
                it.stop()
            }
            ble = null
            _ui.update { it.copy(bleActive = false, bleStatus = "") }
        }
    }

    fun setBleChannel(ch: Int) { Settings.midiChannel = ch; ble?.channel = ch }
    fun setBlePitchBend(b: Boolean) { Settings.midiPitchBend = b }

    // ---------------- calibration ----------------

    fun toggleCalibration() {
        if (_ui.value.calibrating) {
            _ui.update { it.copy(calibrating = false) }
            return
        }
        calibSamples.clear()
        calibDeadline = System.currentTimeMillis() + CAL_TIMEOUT_MS
        _ui.update { it.copy(calibrating = true) }
    }

    private fun feedCalibration(r: TunerEngine.TunerResult.Mono) {
        if (System.currentTimeMillis() > calibDeadline) {
            _ui.update { it.copy(calibrating = false) }
            _msgs.tryEmit(Msg.CalibrateTimeout)
            return
        }
        if (r.held || r.frequency == null || r.confidence < 60) return
        // Sounding pitch (self-consistent regardless of transposition/string mode)
        val soundingMidi = (69.0 + 12.0 * log2(r.frequency / _ui.value.a4)).roundToInt()
        if (((soundingMidi % 12) + 12) % 12 != 9) return
        calibSamples.add(r.frequency)
        if (calibSamples.size >= CAL_FRAMES) {
            val median = calibSamples.sorted()[calibSamples.size / 2]
            val a4 = (median / 2.0.pow((soundingMidi - 69) / 12.0) * 10).roundToInt() / 10.0
            _ui.update { it.copy(calibrating = false) }
            if (a4 in 415.0..465.0) {
                Settings.calibratedA4 = a4.toFloat()
                engine?.a4 = a4
                _ui.update { it.copy(a4 = a4) }
                _msgs.tryEmit(Msg.Calibrated(a4))
            } else _msgs.tryEmit(Msg.CalibrateTimeout)
        }
    }

    // ---------------- drill + records ----------------

    fun toggleDrill() {
        val d = drill
        if (d != null) {
            finishDrill(System.currentTimeMillis())
            return
        }
        val target = pbTarget()
        if (target == null || target <= 0) { _msgs.tryEmit(Msg.DrillNoTarget); return }
        drill = IntonationDrill(toleranceCents = _ui.value.toleranceCents)
        drillTarget = target
        drillStartMs = System.currentTimeMillis()
        drill!!.start(target, drillStartMs)
        _ui.update { it.copy(drill = DrillUi(false, 0, 0, 0, 0)) }
    }

    private fun finishDrillIfActive() { if (drill != null) finishDrill(System.currentTimeMillis()) }

    private fun finishDrill(now: Long) {
        val d = drill ?: return
        val st = d.stop(now)
        drill = null
        val target = drillTarget
        if (target in DrillRecords.MIN_MIDI..DrillRecords.MAX_MIDI && st.bestHoldMs > 0) {
            viewModelScope.launch(Dispatchers.IO) {
                val prevBest = drills.best(target)
                val newPb = drills.record(target, st.bestHoldMs, st.inTuneMs, st.attempts)
                if (newPb && st.bestHoldMs > prevBest)
                    _msgs.tryEmit(Msg.NewRecord(NoteUtil.noteName(target), st.bestHoldMs))
                _ui.update { it.copy(drillPb = target to drills.best(target)) }
            }
        }
        val elapsed = (now - drillStartMs) / 1000
        if (elapsed >= 30) PracticeSessionManager.recordSession(elapsed, "Intonation drill")
        _ui.update { it.copy(drill = null) }
        viewModelScope.launch { refreshPb() }
    }

    private fun pbTarget(): Int? {
        val s = _ui.value
        val strings = s.instrument.strings
        return when {
            s.lockedString in strings.indices -> strings[s.lockedString]
            (s.reading?.midi ?: -1) > 0 -> s.reading?.midi
            else -> null
        }
    }

    private suspend fun refreshPb() {
        val t = pbTarget()
        _ui.update { it.copy(drillPb = t?.let { m -> m to drills.best(m) }) }
    }

    suspend fun drillRecords(): List<DrillRecords.Entry> = drills.all()

    // ---------------- multi-instrument session ----------------

    fun startSession(ids: List<String>) {
        val list = ids.map { findPreset(it) }.filter { it.supportsStringModes }
        if (list.isEmpty()) return
        if (_ui.value.mode != TunerEngine.Mode.STRING) setMode(TunerEngine.Mode.STRING)
        Settings.autoAdvance = true
        _ui.update {
            it.copy(session = SessionState(list.map { p -> p.id }, 0, 0),
                auditActive = false, auditProgress = null)
        }
        sessionResults.clear()
        selectInstrument(list[0])
        setLockedString(0)
        _msgs.tryEmit(Msg.SessionStarted(list[0].name, list.size))
    }

    fun cancelSession() {
        if (_ui.value.session == null) return
        _ui.update { it.copy(session = null) }
        setLockedString(-1)
        Settings.autoAdvance = false
        _msgs.tryEmit(Msg.SessionCancelled)
    }

    private fun finishSession() {
        val csv = AuditReport.sessionCsv(sessionResults.toList())
        sessionResults.clear()
        _ui.update { it.copy(session = null) }
        setLockedString(-1)
        Settings.autoAdvance = false
        _msgs.tryEmit(Msg.SessionDone)
        _msgs.tryEmit(Msg.ShareAudit("MetroTune session report", csv))
    }

    // ---------------- setup wizard ----------------

    fun startWizard(scaleMm: Double, kind: Int) {
        wizardKind = kind
        wizard = IntonationWizard(scaleMm, semitone = kind == 1)
        wizardResults.clear()
        stability.reset()
        emitWizard()
    }

    fun cancelWizard() { wizard = null; _ui.update { it.copy(wizard = null) } }

    fun setupReport(): Pair<String, String>? {
        if (wizardResults.isEmpty()) return null
        val s = _ui.value
        val rows = wizardResults.map {
            AuditReport.SetupRow(
                NoteUtil.noteName(s.instrument.strings.getOrElse(it.stringIndex) { 69 }),
                it.deviationCents, it.offsetMm)
        }
        return "MetroTune setup — ${s.instrument.name}" to
            AuditReport.setupCsv(s.instrument.name, rows)
    }

    private fun emitWizard() {
        val w = wizard
        if (w == null) { _ui.update { it.copy(wizard = null) }; return }
        val s = _ui.value
        val strings = s.instrument.strings
        _ui.update {
            it.copy(wizard = WizardUi(
                kind = wizardKind,
                stringIndex = w.state.stringIndex,
                phase = w.state.phase.ordinal,
                samples = w.state.samples,
                needed = w.samplesNeeded,
                stringName = strings.getOrNull(w.state.stringIndex)
                    ?.let { m -> NoteUtil.noteName(m) } ?: "—",
                results = wizardResults.map { r -> ResultLine(
                    NoteUtil.noteName(strings.getOrElse(r.stringIndex) { 69 }),
                    r.deviationCents, r.offsetMm) }
            ))
        }
    }

    // ---------------- tuning curves ----------------

    fun startCurveCapture() {
        val s = _ui.value
        curveSamples.clear(); workingCurve.clear()
        curveRestoreStretch = s.stretch
        engine?.useStretch = false               // measure raw tempered deviation
        _ui.update { it.copy(curveCapture = true, curveCount = 0) }
    }

    fun cancelCurveCapture() {
        engine?.useStretch = curveRestoreStretch
        curveSamples.clear(); workingCurve.clear()
        _ui.update { it.copy(curveCapture = false, curveCount = 0) }
    }

    fun finishCurveCapture(name: String) {
        val points = workingCurve.toMap()
        engine?.useStretch = curveRestoreStretch
        curveSamples.clear(); workingCurve.clear()
        _ui.update { it.copy(curveCapture = false, curveCount = 0) }
        val c = TuningCurves.save(context, name, points) ?: return
        applyCurve(c)
    }

    private fun applyCurve(c: TuningCurves.Curve) {
        Settings.activeCurveId = c.id
        Settings.stretch = true
        engine?.customStretch = c.points
        engine?.useStretch = true
        _ui.update { it.copy(activeCurve = c.points, activeCurveName = c.name, stretch = true) }
        refreshToneIfPlaying()
    }

    fun useRailsbackCurve() {
        Settings.activeCurveId = ""
        engine?.customStretch = null
        _ui.update { it.copy(activeCurve = null, activeCurveName = null) }
        refreshToneIfPlaying()
    }

    fun selectCurve(id: String) { TuningCurves.byId(context, id)?.let { applyCurve(it) } }

    fun curves(): List<TuningCurves.Curve> = TuningCurves.all(context)

    fun deleteCurve(id: String) {
        TuningCurves.delete(context, id)
        if (Settings.activeCurveId == id) useRailsbackCurve()
    }

    // ---------------- freeze + beat ----------------

    fun toggleFreeze() {
        val s = _ui.value
        if (s.frozenTarget != null) _ui.update { it.copy(frozenTarget = null) }
        else s.reading?.targetFrequency?.let { t -> _ui.update { it.copy(frozenTarget = t) } }
    }

    // ---------------- ear training ----------------

    fun setEarLevel(i: Int) { _ui.update { it.copy(ear = it.ear.copy(level = i)) } }

    fun toggleEar() {
        if (_ui.value.ear.active) {
            earJob?.cancel()
            runCatching { tone.stop() }
            noisy.unregister(); focus.abandon()
            earRound = null
            _ui.update {
                it.copy(ear = it.ear.copy(active = false, listening = false, awaiting = false))
            }
            if (_ui.value.micGranted) startCapture()
            return
        }
        if (!focus.request()) { _msgs.tryEmit(Msg.FocusDenied); return }
        stopCapture()
        noisy.register()
        _ui.update { it.copy(ear = it.ear.copy(active = true)) }
        nextEarRound()
    }

    private fun nextEarRound() {
        val lvl = EarTraining.Level.values()[_ui.value.ear.level]
        val target = pbTarget()
        if (target == null) { _msgs.tryEmit(Msg.DrillNoTarget); return }
        val round = EarTraining.next(lvl)
        earRound = round
        _ui.update { it.copy(ear = it.ear.copy(
            listening = true, awaiting = false, round = it.ear.round + 1)) }
        val base = toneFreq(target)
        val test = base * 2.0.pow(round.comparisonCents / 1200.0)
        earJob = viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                tone.start(listOf(base), -12f, lastToneWave); delay(800); tone.stop()
                delay(350)
                tone.start(listOf(test), -12f, lastToneWave); delay(800); tone.stop()
            }
            _ui.update { it.copy(ear = it.ear.copy(listening = false, awaiting = true)) }
        }
    }

    fun answerEar(saidSharp: Boolean) {
        val round = earRound ?: return
        if (!_ui.value.ear.awaiting) return
        earRound = null
        val correct = EarTraining.answer(round, saidSharp)
        val s0 = _ui.value.ear
        var score = EarTraining.apply(
            EarTraining.Score(s0.round, s0.correct, s0.streak, s0.best), correct)
        if (score.streak > score.best) {
            score = score.copy(best = score.streak)
            Settings.setEarBest(s0.level, score.streak)
        }
        _ui.update { it.copy(ear = it.ear.copy(
            correct = score.correct, streak = score.streak, best = score.best)) }
        _msgs.tryEmit(Msg.EarFeedback(correct, round.comparisonCents, score.streak))
    }

    // ---------------- health ----------------

    suspend fun healthRows(): List<HealthRow> {
        val s = _ui.value
        return health.forInstrument(s.instrument.id).map { e ->
            HealthRow(NoteUtil.noteName(e.midi), e.avgCents, e.lastCents,
                e.count, health.flagged(e))
        }
    }

    private fun recordHealth(instrumentId: String, midi: Int, cents: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { health.record(instrumentId, midi, cents) }
        }
    }

    // ---------------- tuning detection ----------------

    fun detectTop(): TuningDetect.Match? =
        TuningDetect.detect(recentNotes.toList(), presets()).firstOrNull()

    private fun maybeSuggest() {
        if (_ui.value.simpleMode) return
        if (System.currentTimeMillis() - lastSuggestAt < 60_000) return
        val top = detectTop() ?: return
        if (top.presetId == _ui.value.instrument.id || top.presetId in dismissedDetect) return
        lastSuggestAt = System.currentTimeMillis()
        _msgs.tryEmit(Msg.SuggestTuning(top.name, top.presetId))
    }

    // ---------------- engine results ----------------

    private fun onEngineResult(r: TunerEngine.TunerResult) {
        when (r) {
            is TunerEngine.TunerResult.Mono -> onMono(r)
            is TunerEngine.TunerResult.Poly -> _ui.update { it.copy(poly = r.strings) }
        }
    }

    private fun onMono(r: TunerEngine.TunerResult.Mono) {
        if (_ui.value.calibrating) feedCalibration(r)
        val live = !r.held && r.noteName != null && r.cents != null
        if (!live) {
            _ui.update {
                it.copy(
                    heldReading = r.held,
                    reading = if (r.held) it.reading else null,
                    lowNoteMode = false, octaveCorrected = false
                )
            }
            ble?.let { srv ->
                if (lastMidiSent >= 0) { srv.sendNoteOff(lastMidiSent); lastMidiSent = -1 }
            }
            return
        }

        var devs = _ui.value.headDevs
        var active = _ui.value.headActive
        if (r.stringIndex >= 0 && r.stringIndex < devs.size) {
            devs = devs.toMutableList().also { it[r.stringIndex] = r.cents }
            active = r.stringIndex
        }
        _ui.update {
            it.copy(reading = r, heldReading = false, headDevs = devs, headActive = active,
                lowNoteMode = r.lowNoteMode, octaveCorrected = r.octaveCorrected)
        }

        drill?.let { d ->
            if (r.midi > 0 && r.cents != null) {
                val st = d.onFrame(r.midi, r.cents, r.confidence, System.currentTimeMillis())
                _ui.update {
                    it.copy(drill = DrillUi(st.holding, st.currentHoldMs,
                        st.bestHoldMs, st.inTuneTotalMs, st.attempts))
                }
            }
        }

        if (r.noteChanged && r.midi > 0) {
            recentNotes.addLast(r.midi)
            while (recentNotes.size > 14) recentNotes.removeFirst()
            // double-stop interval: previous stable note vs this one
            val info = prevStable?.let { p ->
                com.example.metrotune.audio.IntervalCheck.info(p.second, r.frequency ?: 0.0)
            }
            prevStable = r.midi to (r.frequency ?: 0.0)
            _ui.update { it.copy(interval = info) }
            maybeSuggest()
        }

        if (r.stringIndex >= 0 && r.cents != null) {
            stability.add(r.cents, System.currentTimeMillis())
        }

        // curve capture
        if (_ui.value.curveCapture && r.confidence >= 70 && r.cents != null && r.midi > 0) {
            val list = curveSamples.getOrPut(r.midi) { mutableListOf() }
            list.add(r.cents)
            if (list.size >= 12) {
                workingCurve[r.midi] = list.sorted()[list.size / 2]
                curveSamples.remove(r.midi)
                _ui.update { it.copy(curveCount = workingCurve.size) }
            }
        }

        // setup wizard
        wizard?.let { w ->
            if (r.confidence >= 60 && r.frequency != null) {
                val st = w.feed(r.frequency)
                if (st.phase == IntonationWizard.Phase.DONE) {
                    st.last?.let { wizardResults.add(it) }
                    if (st.stringIndex + 1 < _ui.value.instrument.strings.size) w.nextString()
                    else { wizard = null; _msgs.tryEmit(Msg.WizardDone) }
                }
                emitWizard()
            }
        }

        ble?.let { srv ->
            if (r.noteChanged && r.midi > 0) {
                if (lastMidiSent >= 0) srv.sendNoteOff(lastMidiSent)
                srv.sendNoteOn(r.midi, 64 + r.confidence / 2)
                lastMidiSent = r.midi
            }
            if (Settings.midiPitchBend) srv.sendPitchBend(r.cents)
        }

        if (_ui.value.mode == TunerEngine.Mode.STRING &&
            _ui.value.instrument.strings.isNotEmpty() && Settings.autoAdvance
        ) advanceLogic(r)
    }

    private fun advanceLogic(r: TunerEngine.TunerResult.Mono) {
        val s = _ui.value
        val ok = r.confidence >= 70 && abs(r.cents) <= s.toleranceCents
        if (ok && r.stringIndex == advString) advStreak++
        else { advString = r.stringIndex; advStreak = if (ok) 1 else 0 }
        if (advStreak < ADV_FRAMES) return
        advStreak = 0

        val snap = stability.snapshot()
        val midiNow = s.instrument.strings.getOrNull(advString) ?: 69
        recordHealth(s.instrument.id, midiNow, r.cents)

        // ---- multi-instrument session takes precedence ----
        val session = _ui.value.session
        if (session != null) {
            val counts = session.instrumentIds.map { findPreset(it).strings.size }
            val nextFlat = session.flatDone + 1
            sessionResults.add(AuditReport.SessionRow(
                s.instrument.name, advString + 1, r.cents, snap.stddevCents))
            val next = TuningSession.locate(nextFlat, counts)
            if (next == null) {
                finishSession()
            } else {
                if (next.instrumentIndex != session.instrumentIndex) {
                    val p = findPreset(session.instrumentIds[next.instrumentIndex])
                    selectInstrument(p)
                    _msgs.tryEmit(Msg.SessionAdvance(
                        p.name, next.instrumentIndex + 1, session.instrumentIds.size))
                }
                setLockedString(next.stringIndex)
                _ui.update {
                    it.copy(session = session.copy(
                        instrumentIndex = next.instrumentIndex, flatDone = nextFlat))
                }
            }
            return
        }

        // ---- single-instrument audit / plain auto-advance ----
        val strings = s.instrument.strings
        if (_ui.value.auditActive) {
            auditResults.add(AuditReport.AuditRow(advString, r.cents, snap.stddevCents))
        }
        when {
            s.lockedString == -1 -> setLockedString(advString)
            s.lockedString == advString -> {
                val next = advString + 1
                if (next < strings.size) {
                    setLockedString(next)
                    _msgs.tryEmit(Msg.StringInTune(next, NoteUtil.noteName(strings[next])))
                    if (_ui.value.auditActive)
                        _ui.update { it.copy(auditProgress = (next + 1) to strings.size) }
                } else {
                    val audit = _ui.value.auditActive
                    setLockedString(-1)
                    if (audit) {
                        _ui.update { it.copy(auditActive = false, auditProgress = null) }
                        val csv = AuditReport.csv(s.instrument.name, s.a4, auditResults.toList())
                        auditResults.clear()
                        _msgs.tryEmit(Msg.ShareAudit(
                            "MetroTune audit — ${s.instrument.name}", csv))
                    } else _msgs.tryEmit(Msg.AllTuned)
                    // Simple mode: stay auto — "all tuned" shouldn't leave the
                    // flow broken for the next pass. Pro users own the toggle.
                    if (!_ui.value.simpleMode) Settings.autoAdvance = false
                }
            }
        }
    }

    // ---------------- export ----------------

    fun csvSnapshot(): Pair<String, String>? {
        val r = _ui.value.reading ?: return null
        val sb = StringBuilder()
        sb.append("metric,value\n")
        sb.append("instrument,${_ui.value.instrument.name}\n")
        sb.append("note,${r.noteName}\n")
        sb.append("detected_hz,${String.format(Locale.US, "%.2f", r.frequency)}\n")
        sb.append("target_hz,${String.format(Locale.US, "%.2f", r.targetFrequency)}\n")
        sb.append("cents,${String.format(Locale.US, "%+.1f", r.cents)}\n")
        sb.append("clarity,${String.format(Locale.US, "%.2f", r.clarity)}\n")
        sb.append("confidence,${r.confidence}\n")
        sb.append("snr_db,${String.format(Locale.US, "%.1f", r.snrDb)}\n")
        sb.append("inharmonicity_B,${String.format(Locale.US, "%.6g", r.inharmonicity)}\n")
        sb.append("\nharmonic,ratio,amplitude_db,cents_deviation\n")
        r.harmonics.forEach { h ->
            val dev = if (h.centsDeviation.isNaN()) ""
                else String.format(Locale.US, "%+.2f", h.centsDeviation)
            sb.append("${h.n},${String.format(Locale.US, "%.2f", h.ratio)},")
                .append("${String.format(Locale.US, "%.1f", h.amplitudeDb)},$dev\n")
        }
        return "MetroTune analysis — ${r.noteName}" to sb.toString()
    }

    fun persistOnPause() = persistProfile()

    private fun persistProfile() {
        val s = _ui.value
        Settings.saveProfile(s.instrument.id, Settings.Profile(
            useFeiten = s.feiten,
            transposeIndex = s.transposeIndex,
            mode = s.mode.name,
            lockedString = s.lockedString,
            a4 = s.a4.toFloat()))
    }

    override fun onCleared() {
        earJob?.cancel()
        stopTone(restart = false)
        ble?.stop()
        engine?.stop()
        super.onCleared()
    }
}
