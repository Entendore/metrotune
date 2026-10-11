package com.example.metrotune.audio

import android.content.Context
import android.content.SharedPreferences

/** Central, idempotent settings store. Cheap reads, write-on-change. */
object Settings {

    private const val FILE = "metrotune_settings"
    private lateinit var p: SharedPreferences

    fun init(context: Context) {
        if (!::p.isInitialized) {
            p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    private fun i(k: String, d: Int) = p.getInt(k, d)
    private fun b(k: String, d: Boolean) = p.getBoolean(k, d)
    private fun s(k: String, d: String) = p.getString(k, d) ?: d
    private fun f(k: String, d: Float) = p.getFloat(k, d)
    private fun put(edit: SharedPreferences.Editor.() -> Unit) = p.edit().apply(edit).apply()

    // ---------- ui ----------
    var keepScreenOn: Boolean   get() = b("ui.keepAwake", true);  set(v) = put { putBoolean("ui.keepAwake", v) }
    var onboardingDone: Boolean get() = b("ui.onboarded", false); set(v) = put { putBoolean("ui.onboarded", v) }
    var micRationaleShown: Boolean get() = b("ui.micRationale", false); set(v) = put { putBoolean("ui.micRationale", v) }
    var simpleMode: Boolean     get() = b("ui.simple", true);     set(v) = put { putBoolean("ui.simple", v) }

    // ---------- metronome ----------
    var metroBpm: Int          get() = i("metro.bpm", 120);  set(v) = put { putInt("metro.bpm", v) }
    var metroBeats: Int        get() = i("metro.beats", 4);  set(v) = put { putInt("metro.beats", v) }
    var metroSub: Int          get() = i("metro.sub", 1);    set(v) = put { putInt("metro.sub", v) }
    var metroHaptics: Boolean  get() = b("metro.haptics", false); set(v) = put { putBoolean("metro.haptics", v) }
    var metroPattern: String   get() = s("metro.pattern", "ACCENT,NORMAL,NORMAL,NORMAL"); set(v) = put { putString("metro.pattern", v) }
    var metroCountIn: Int      get() = i("metro.countIn", 0); set(v) = put { putInt("metro.countIn", v) }
    var metroPoly: String      get() = s("metro.poly", "off"); set(v) = put { putString("metro.poly", v) }
    var clickVolumeDb: Int     get() = i("metro.vol", 0);    set(v) = put { putInt("metro.vol", v) }
    var metroFlash: Boolean    get() = b("metro.flash", false); set(v) = put { putBoolean("metro.flash", v) }
    var metroMidiClock: Boolean get() = b("metro.midiClock", false); set(v) = put { putBoolean("metro.midiClock", v) }
    var activeSetlist: String  get() = s("metro.setlist", ""); set(v) = put { putString("metro.setlist", v) }

    // ---------- tempo presets ----------
    data class TempoPreset(val name: String, val bpm: Int, val beats: Int, val sub: Int)

    fun tempoPresets(): List<TempoPreset> =
        p.all.entries.asSequence()
            .filter { it.key.startsWith("preset.") }
            .mapNotNull { e ->
                val name = e.key.removePrefix("preset.")
                val parts = (e.value as? String)?.split("|") ?: return@mapNotNull null
                val bpm = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val beats = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                val sub = parts.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
                TempoPreset(name, bpm, beats, sub)
            }
            .sortedBy { it.name.lowercase() }
            .toList()

    fun saveTempoPreset(preset: TempoPreset) =
        put { putString("preset.${preset.name}", "${preset.bpm}|${preset.beats}|${preset.sub}") }

    fun deleteTempoPreset(name: String) { p.edit().remove("preset.$name").apply() }

    // ---------- setlists (encoded via SetlistCodec) ----------
    data class SetlistEntry(val name: String, val items: List<SetlistItem>)

    fun setlists(): List<SetlistEntry> =
        p.all.entries.asSequence()
            .filter { it.key.startsWith("setlist.") }
            .mapNotNull { e ->
                val items = (e.value as? String)?.let { SetlistCodec.decode(it) }.orEmpty()
                if (items.isEmpty()) null else SetlistEntry(e.key.removePrefix("setlist."), items)
            }
            .sortedBy { it.name.lowercase() }
            .toList()

    fun saveSetlist(name: String, items: List<SetlistItem>) {
        val n = name.trim()
        if (n.isEmpty() || items.isEmpty()) return
        put { putString("setlist.$n", SetlistCodec.encode(items)) }
    }

    // ---------- tuner: global ----------
    var instrumentId: String    get() = s("t.inst", "chromatic"); set(v) = put { putString("t.inst", v) }
    var a4Index: Int            get() = i("t.a4", 5);             set(v) = put { putInt("t.a4", v) }
    var temperamentId: String   get() = s("t.temp", "equal");     set(v) = put { putString("t.temp", v) }
    var tonicIndex: Int         get() = i("t.tonic", 0);          set(v) = put { putInt("t.tonic", v) }
    var calibrationCents: Float get() = f("t.cal", 0f);           set(v) = put { putFloat("t.cal", v) }
    var gateDb: Int             get() = i("t.gate", 14);          set(v) = put { putInt("t.gate", v) }
    var autoOctave: Boolean     get() = b("t.oct", true);         set(v) = put { putBoolean("t.oct", v) }
    var adaptiveGate: Boolean   get() = b("t.adaptiveGate", true); set(v) = put { putBoolean("t.adaptiveGate", v) }
    var strobeOn: Boolean       get() = b("t.strobe", false);     set(v) = put { putBoolean("t.strobe", v) }
    var vizOn: Boolean          get() = b("t.viz", true);         set(v) = put { putBoolean("t.viz", v) }
    var toleranceCents: Int     get() = i("t.tol", 5);            set(v) = put { putInt("t.tol", v) }
    var autoAdvance: Boolean    get() = b("t.autoAdv", false);    set(v) = put { putBoolean("t.autoAdv", v) }
    var sweetened: Boolean      get() = b("t.sweet", false);      set(v) = put { putBoolean("t.sweet", v) }
    var stretch: Boolean        get() = b("t.stretch", false);    set(v) = put { putBoolean("t.stretch", v) }
    var overpullPct: Int        get() = i("t.overpull", 0);       set(v) = put { putInt("t.overpull", v) }
    var calibratedA4: Float     get() = f("t.a4cal", -1f);        set(v) = put { putFloat("t.a4cal", v) }
    var activeCurveId: String   get() = s("t.curve", "");         set(v) = put { putString("t.curve", v) }
    var micType: Int            get() = i("t.micType", -1);       set(v) = put { putInt("t.micType", v) }
    var micName: String         get() = s("t.micName", "");       set(v) = put { putString("t.micName", v) }
    var sessionInstruments: String get() = s("t.session", "");    set(v) = put { putString("t.session", v) }

    // ---------- tuner: reference-tone memory ----------
    var toneWave: Int get() = i("tone.wave", 0);  set(v) = put { putInt("tone.wave", v) }
    var toneVol: Int  get() = i("tone.vol", 70);  set(v) = put { putInt("tone.vol", v) }

    // ---------- ear training ----------
    fun earBest(level: Int): Int = i("ear.best.$level", 0)
    fun setEarBest(level: Int, streak: Int) = put { putInt("ear.best.$level", streak) }

    // ---------- recent instruments ----------
    fun recentInstruments(): List<String> =
        s("t.recents", "").split("|").filter { it.isNotBlank() }

    fun pushRecentInstrument(id: String) {
        val cur = recentInstruments().toMutableList() - id
        put { putString("t.recents", (listOf(id) + cur).take(5).joinToString("|")) }
    }

    // ---------- per-instrument profile ----------
    data class Profile(
        val useFeiten: Boolean,
        val transposeIndex: Int,
        val mode: String,          // TunerEngine.Mode name, "" = default
        val lockedString: Int,     // -1 = none
        val a4: Float = -1f        // exact A4 last used with this instrument
    )

    fun profile(id: String): Profile = Profile(
        useFeiten = b("p_$id.feiten", false),
        transposeIndex = i("p_$id.tr", 0),
        mode = s("p_$id.mode", ""),
        lockedString = i("p_$id.lock", -1),
        a4 = f("p_$id.a4", -1f)
    )

    fun saveProfile(id: String, pr: Profile) = put {
        putBoolean("p_$id.feiten", pr.useFeiten)
        putInt("p_$id.tr", pr.transposeIndex)
        putString("p_$id.mode", pr.mode)
        putInt("p_$id.lock", pr.lockedString)
        putFloat("p_$id.a4", pr.a4)
    }

    // ---------- personal per-string offsets ("p_<id>.off" = "idx:cents;…") ----------
    fun userOffsets(id: String): Map<Int, Double> =
        s("p_$id.off", "").split(";").mapNotNull { part ->
            val kv = part.split(":")
            val k = kv.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val v = kv.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            k to v
        }.toMap()

    fun setUserOffset(id: String, idx: Int, cents: Double) {
        val m = userOffsets(id).toMutableMap()
        if (kotlin.math.abs(cents) < 0.05) m.remove(idx) else m[idx] = cents
        val enc = m.entries.sortedBy { it.key }
            .joinToString(";") {
                "${it.key}:${String.format(java.util.Locale.US, "%.1f", it.value)}"
            }
        put { putString("p_$id.off", enc) }
    }

    // ---------- BLE-MIDI ----------
    var midiChannel: Int       get() = i("midi.ch", 0);      set(v) = put { putInt("midi.ch", v) }
    var midiPitchBend: Boolean get() = b("midi.pb", true);   set(v) = put { putBoolean("midi.pb", v) }
}
