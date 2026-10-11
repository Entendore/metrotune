package com.example.metrotune.ui

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.example.metrotune.audio.MetronomeController
import com.example.metrotune.audio.MetronomeEngine
import com.example.metrotune.audio.MetronomeEngine.BeatAccent
import com.example.metrotune.audio.MetronomeEngine.TrainerConfig
import com.example.metrotune.audio.MetronomeEngine.TrainerMode
import com.example.metrotune.audio.Settings
import com.example.metrotune.audio.SetlistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class MetronomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: android.content.Context,
    private val controller: MetronomeController
) : AndroidViewModel(appContext as Application) {

    data class BeatPulse(val beat: Int, val countIn: Boolean)

    sealed class Msg {
        object NeedNotificationPermission : Msg()
        object PlaybackFailed : Msg()
        data class ShareCsv(val subject: String, val csv: String) : Msg()
    }

    data class UiState(
        val bpm: Int = 120,
        val beatsPerBar: Int = 4,
        val subdivision: Int = 1,
        val pattern: List<BeatAccent> =
            List(4) { if (it == 0) BeatAccent.ACCENT else BeatAccent.NORMAL },
        val running: Boolean = false,
        val liveBpm: Int = 120,
        val muted: Boolean = false,
        val countInBars: Int = 0,
        val polyIndex: Int = 0,
        val trainerMode: Int = 0,
        val trainerA: Int = 1,
        val trainerB: Int = 3,
        val volumeProgress: Int = 24,
        val flash: Boolean = false,
        val haptics: Boolean = false,
        val setlistNames: List<String> = emptyList(),
        val activeSetlist: String? = null,
        val setlistLoop: Boolean = false,
        val workingSetlist: List<SetlistItem> = emptyList(),
        val nowPart: String? = null,
        val nextPart: String? = null,
        val presets: List<Settings.TempoPreset> = emptyList()
    )

    companion object {
        val POLY_OPTIONS = listOf("Off", "3 : 2", "4 : 3", "2 : 3", "5 : 2", "5 : 3")
        val POLY_PAIRS = listOf(0 to 0, 3 to 2, 4 to 3, 2 to 3, 5 to 2, 5 to 3)
        val COUNT_IN_VALUES = intArrayOf(0, 1, 2, 4)
    }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _beats = MutableSharedFlow<BeatPulse>(extraBufferCapacity = 16)
    val beats: SharedFlow<BeatPulse> = _beats.asSharedFlow()

    private val _msgs = MutableSharedFlow<Msg>(extraBufferCapacity = 8)
    val msgs: SharedFlow<Msg> = _msgs.asSharedFlow()

    private val taps = mutableListOf<Long>()

    private val listener = object : MetronomeEngine.Listener {
        override fun onBeat(beat: Int, countIn: Boolean) {
            _beats.tryEmit(BeatPulse(beat, countIn))
        }
        override fun onStateChanged(running: Boolean) {
            _ui.update {
                it.copy(running = running, muted = false,
                    liveBpm = if (running) it.liveBpm else it.bpm)
            }
        }
        override fun onTrainerTick(liveBpm: Int, muted: Boolean) {
            _ui.update { it.copy(liveBpm = liveBpm, muted = muted) }
        }
        override fun onSetlistAdvance(index: Int, item: SetlistItem?, ended: Boolean) {
            if (ended) {
                _ui.update { it.copy(nowPart = null, nextPart = null) }
                return
            }
            item ?: return
            val next = _ui.value.workingSetlist.getOrNull(index + 1)?.name
            _ui.update { s -> s.copy(
                bpm = item.bpm, beatsPerBar = item.beatsPerBar, subdivision = item.subdivision,
                liveBpm = item.bpm, muted = false,
                pattern = resizePattern(s.pattern, item.beatsPerBar),
                nowPart = item.name, nextPart = next) }
        }
    }

    init {
        Settings.init(appContext)
        val running = MetronomeEngine.isRunning
        val savedPattern = parsePattern(Settings.metroPattern, Settings.metroBeats)
        _ui.value = UiState(
            bpm = if (running) MetronomeEngine.bpm else Settings.metroBpm,
            beatsPerBar = if (running) MetronomeEngine.beatsPerBar else Settings.metroBeats,
            subdivision = if (running) MetronomeEngine.subdivision else Settings.metroSub,
            pattern = savedPattern,
            running = running,
            liveBpm = MetronomeEngine.liveBpm,
            countInBars = Settings.metroCountIn,
            polyIndex = POLY_PAIRS.indexOfFirst {
                "${it.first}:${it.second}" == Settings.metroPoly }.coerceAtLeast(0),
            volumeProgress = (Settings.clickVolumeDb + 24).coerceIn(0, 24),
            flash = Settings.metroFlash,
            haptics = Settings.metroHaptics,
            presets = Settings.tempoPresets()
        )
        val active = Settings.activeSetlist
        _ui.value = _ui.value.copy(
            activeSetlist = active.ifEmpty { null },
            workingSetlist = Settings.setlists().firstOrNull { it.name == active }?.items
                ?: emptyList(),
            setlistNames = Settings.setlists().map { it.name })
        if (!running) publishConfig()   // when running, engine values win
        MetronomeEngine.setVolumeDb(Settings.clickVolumeDb)
        MetronomeEngine.addListener(listener)
    }

    // ---------------- playback ----------------

    fun togglePlay() {
        if (MetronomeEngine.isRunning) { controller.stop(); return }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                appContext, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            _msgs.tryEmit(Msg.NeedNotificationPermission)
            return
        }
        playConfirmed()
    }

    fun playConfirmed() {
        if (!controller.start()) _msgs.tryEmit(Msg.PlaybackFailed)
    }

    fun restartForConfigChange() {
        if (!MetronomeEngine.isRunning) return
        controller.stop()
        controller.start()
    }

    // ---------------- feel / tempo ----------------

    fun setBpm(value: Int) {
        val v = value.coerceIn(MetronomeEngine.MIN_BPM, MetronomeEngine.MAX_BPM)
        MetronomeEngine.bpm = v
        _ui.update { it.copy(bpm = v, liveBpm = v) }
        Settings.metroBpm = v
    }

    fun tapTempo() {
        val now = SystemClock.elapsedRealtime()
        if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear()
        taps.add(now)
        if (taps.size > 5) taps.removeAt(0)
        if (taps.size >= 2) {
            val avg = taps.zipWithNext { a, b -> (b - a).toDouble() }.average()
            setBpm((60000.0 / avg).roundToInt())
        }
    }

    fun setBeats(pos: Int) {
        val beats = pos + 1
        MetronomeEngine.beatsPerBar = beats
        _ui.update { it.copy(beatsPerBar = beats, pattern = resizePattern(it.pattern, beats)) }
        Settings.metroBeats = beats
        publishPattern()
    }

    fun setSub(pos: Int) {
        val sub = pos + 1
        MetronomeEngine.subdivision = sub
        _ui.update { it.copy(subdivision = sub) }
        Settings.metroSub = sub
    }

    fun cyclePatternAt(index: Int) {
        val p = _ui.value.pattern.toMutableList()
        p[index] = when (p[index]) {
            BeatAccent.ACCENT -> BeatAccent.NORMAL
            BeatAccent.NORMAL -> BeatAccent.SOFT
            BeatAccent.SOFT -> BeatAccent.MUTE
            BeatAccent.MUTE -> BeatAccent.ACCENT
        }
        _ui.update { it.copy(pattern = p) }
        Settings.metroPattern = p.joinToString(",") { it.name }
        publishPattern()
    }

    fun setCountIn(pos: Int) {
        val bars = COUNT_IN_VALUES[pos]
        MetronomeEngine.countInBars = bars
        _ui.update { it.copy(countInBars = bars) }
        Settings.metroCountIn = bars
    }

    fun setPoly(pos: Int) {
        val pair = POLY_PAIRS[pos]
        MetronomeEngine.polyVoice = pair.first
        MetronomeEngine.polyAgainst = pair.second
        _ui.update { it.copy(polyIndex = pos) }
        Settings.metroPoly = if (pair.first == 0) "off" else "${pair.first}:${pair.second}"
        restartForConfigChange()   // loop variant is chosen at start
    }

    fun setTrainerMode(pos: Int) { _ui.update { it.copy(trainerMode = pos) }; publishTrainer() }
    fun setTrainerA(a: Int) { _ui.update { it.copy(trainerA = a) }; publishTrainer() }
    fun setTrainerB(b: Int) { _ui.update { it.copy(trainerB = b) }; publishTrainer() }

    fun setMidiClock(enabled: Boolean) {
        Settings.metroMidiClock = enabled
        restartForConfigChange()
    }

    fun setVolumeProgress(progress: Int) {
        val db = progress - 24
        Settings.clickVolumeDb = db
        MetronomeEngine.setVolumeDb(db)
        _ui.update { it.copy(volumeProgress = progress) }
    }

    fun setFlash(b: Boolean) { Settings.metroFlash = b; _ui.update { it.copy(flash = b) } }
    fun setHaptics(b: Boolean) { Settings.metroHaptics = b; _ui.update { it.copy(haptics = b) } }

    // ---------------- tempo presets ----------------

    fun loadPreset(p: Settings.TempoPreset) {
        MetronomeEngine.beatsPerBar = p.beats
        MetronomeEngine.subdivision = p.sub
        setBpm(p.bpm)
        _ui.update {
            it.copy(beatsPerBar = p.beats, subdivision = p.sub,
                pattern = resizePattern(it.pattern, p.beats))
        }
        publishPattern()
    }

    fun saveTempoPreset(name: String) {
        val s = _ui.value
        Settings.saveTempoPreset(Settings.TempoPreset(name, s.bpm, s.beatsPerBar, s.subdivision))
        _ui.update { it.copy(presets = Settings.tempoPresets()) }
    }

    fun deleteTempoPreset(name: String) {
        Settings.deleteTempoPreset(name)
        _ui.update { it.copy(presets = Settings.tempoPresets()) }
    }

    // ---------------- setlists ----------------

    fun selectSetlist(name: String?) {
        val items = name?.let { n ->
            Settings.setlists().firstOrNull { it.name == n }?.items
        } ?: emptyList()
        _ui.update { it.copy(activeSetlist = name, workingSetlist = items,
            nowPart = null, nextPart = null) }
        Settings.activeSetlist = name ?: ""
        publishSetlist()
    }

    fun setSetlistLoop(b: Boolean) {
        MetronomeEngine.setlistLoop = b
        _ui.update { it.copy(setlistLoop = b) }
    }

    fun addSetlistItem(name: String, bars: Int) {
        val s = _ui.value
        val item = SetlistItem(
            name.ifEmpty { "Part ${s.workingSetlist.size + 1}" },
            s.bpm, s.beatsPerBar, s.subdivision, bars.coerceIn(0, 999))
        _ui.update { it.copy(workingSetlist = it.workingSetlist + item,
            nowPart = it.nowPart ?: item.name) }
        publishSetlist()
    }

    fun updateSetlistItem(index: Int, name: String, bars: Int, pattern: List<BeatAccent>?) {
        val list = _ui.value.workingSetlist.toMutableList()
        val old = list.getOrNull(index) ?: return
        list[index] = old.copy(
            name = name.ifEmpty { old.name },
            bars = bars.coerceIn(0, 999),
            pattern = pattern)
        _ui.update { it.copy(workingSetlist = list) }
        publishSetlist()
    }

    fun moveSetlistItem(index: Int, delta: Int) {
        val list = _ui.value.workingSetlist.toMutableList()
        val to = index + delta
        if (to !in list.indices) return
        java.util.Collections.swap(list, index, to)
        _ui.update { it.copy(workingSetlist = list) }
        publishSetlist()
    }

    fun removeSetlistItem(index: Int) {
        _ui.update {
            it.copy(workingSetlist = it.workingSetlist.filterIndexed { i, _ -> i != index })
        }
        publishSetlist()
    }

    fun saveSetlist(name: String) {
        val n = name.trim()
        if (n.isEmpty() || _ui.value.workingSetlist.isEmpty()) return
        Settings.saveSetlist(n, _ui.value.workingSetlist)
        Settings.activeSetlist = n
        _ui.update { it.copy(activeSetlist = n,
            setlistNames = Settings.setlists().map { e -> e.name }) }
    }

    fun shareSetlistCsv() {
        val items = _ui.value.workingSetlist
        if (items.isEmpty()) return
        fun glyph(a: BeatAccent) = when (a) {
            BeatAccent.ACCENT -> "A"; BeatAccent.NORMAL -> "N"
            BeatAccent.SOFT -> "S"; BeatAccent.MUTE -> "x"
        }
        val sb = StringBuilder("name,bpm,beats,subdivision,bars,pattern\n")
        items.forEach {
            sb.append("${it.name.replace(',', ' ')},${it.bpm},${it.beatsPerBar},")
                .append("${it.subdivision},${it.bars},")
                .append(it.pattern?.joinToString("-") { a -> glyph(a) } ?: "")
                .append('\n')
        }
        _msgs.tryEmit(Msg.ShareCsv(
            "MetroTune setlist — ${_ui.value.activeSetlist ?: "Setlist"}", sb.toString()))
    }

    // ---------------- publishing (state → engine) ----------------

    private fun publishConfig() {
        val s = _ui.value
        MetronomeEngine.bpm = s.bpm
        MetronomeEngine.beatsPerBar = s.beatsPerBar
        MetronomeEngine.subdivision = s.subdivision
        MetronomeEngine.countInBars = s.countInBars
        val pair = POLY_PAIRS[s.polyIndex]
        MetronomeEngine.polyVoice = pair.first
        MetronomeEngine.polyAgainst = pair.second
        publishTrainer()
        publishPattern()
        publishSetlist()
    }

    private fun publishTrainer() {
        val s = _ui.value
        MetronomeEngine.trainer = TrainerConfig(
            mode = TrainerMode.values()[s.trainerMode],
            rampStepBpm = s.trainerA, rampEveryBars = s.trainerB,
            gapPlayBars = s.trainerA, gapMuteBars = s.trainerB)
    }

    private fun publishPattern() {
        val p = _ui.value.pattern
        MetronomeEngine.setPattern(p)
        Settings.metroPattern = p.joinToString(",") { it.name }
    }

    private fun publishSetlist() {
        MetronomeEngine.setlist = _ui.value.workingSetlist
        MetronomeEngine.setlistLoop = _ui.value.setlistLoop
    }

    // ---------------- helpers ----------------

    private fun parsePattern(s: String, beats: Int): List<BeatAccent> =
        resizePattern(
            s.split(",").mapNotNull { part ->
                runCatching { BeatAccent.valueOf(part.trim()) }.getOrNull()
            }, beats)

    private fun resizePattern(src: List<BeatAccent>, beats: Int): List<BeatAccent> =
        List(beats) { i -> src.getOrNull(i) ?: if (i == 0) BeatAccent.ACCENT else BeatAccent.NORMAL }

    override fun onCleared() {
        MetronomeEngine.removeListener(listener)
        val s = _ui.value
        // Persist user-intent values — not setlist-mutated ones.
        Settings.metroBpm = s.bpm
        Settings.metroBeats = s.beatsPerBar
        Settings.metroSub = s.subdivision
        Settings.metroPattern = s.pattern.joinToString(",") { it.name }
        super.onCleared()
    }
}
