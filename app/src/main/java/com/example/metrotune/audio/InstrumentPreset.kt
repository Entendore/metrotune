package com.example.metrotune.audio

/**
 * @param family         picker-dialog group
 * @param writtenOffset  semitones added to sounding pitch to display *written*
 *                       pitch. Positive = instrument sounds below written
 *                       (B♭ trumpet). Negative = sounds above (piccolo).
 * @param suggestedA4    non-standard pitch centers (Highland pipes ≈ 475 Hz).
 * @param partials       non-harmonic partial ratios (mallet bars: 1:4:10 …).
 * @param sweetened      per-string cents offsets for "sweetened" mode.
 */
data class InstrumentPreset(
    val id: String,
    val name: String,
    val strings: List<Int>,
    val sweetened: List<Double> = emptyList(),
    val family: String = "All notes",
    val writtenOffset: Int? = null,
    val suggestedA4: Double? = null,
    val partials: List<Double> = emptyList()
) {
    val supportsStringModes: Boolean get() = strings.isNotEmpty()
}

object InstrumentLibrary {

    private val OFFSETS =
        mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)
    private val MAJOR = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val PENTA = intArrayOf(0, 2, 4, 7, 9)
    private val PENTA_MINOR = intArrayOf(0, 2, 3, 7, 9)

    fun toMidi(note: String): Int {
        val s = note.trim()
        var pc = OFFSETS[s[0].uppercaseChar()] ?: throw IllegalArgumentException("Bad note: $note")
        var i = 1
        if (i < s.length && (s[i] == '#' || s[i] == 'b')) {
            pc += if (s[i] == '#') 1 else -1
            i++
        }
        return (s.substring(i).toInt() + 1) * 12 + pc
    }

    /** "E2, A2 D3" → MIDI list; null if any note is invalid or outside A0–C8. */
    fun parseStrings(spec: String): List<Int>? {
        val parts = spec.split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val out = ArrayList<Int>(parts.size)
        for (str in parts) {
            val m = try { toMidi(str) } catch (_: Exception) { return null }
            if (m !in 21..108) return null
            out.add(m)
        }
        return out
    }

    /** Diatonic scale from startMidi as tonic (C D E F G A B C …). */
    private fun diatonic(startMidi: Int, count: Int): List<Int> =
        (0 until count).map { startMidi + 12 * (it / 7) + MAJOR[it % 7] }

    /** Major pentatonic from startMidi (D E F♯ A B …). */
    private fun penta(startMidi: Int, count: Int): List<Int> =
        (0 until count).map { startMidi + 12 * (it / 5) + PENTA[it % 5] }

    /** Minor-flavor pentatonic (1‑♭3‑4‑5‑♭7): D F G A C for shakuhachi etc. */
    private fun pentaMinor(startMidi: Int, count: Int): List<Int> =
        (0 until count).map { startMidi + 12 * (it / 5) + PENTA_MINOR[it % 5] }

    /** Chromatic scale startMidi … +count−1 semitones (keyboard-type instruments). */
    private fun chromatic(startMidi: Int, count: Int): List<Int> =
        (0 until count).map { startMidi + it }

    private fun p(id: String, family: String, name: String, vararg notes: String) =
        InstrumentPreset(id, name, notes.map { toMidi(it) }, family = family)

    private fun ps(id: String, family: String, name: String, sweet: List<Double>, vararg notes: String) =
        InstrumentPreset(id, name, notes.map { toMidi(it) }, sweet, family)

    private fun w(id: String, family: String, name: String, written: Int) =
        InstrumentPreset(id, name, emptyList(), family = family, writtenOffset = written)

    val CHROMATIC =
        InstrumentPreset("chromatic", "Chromatic (any note)", emptyList(), family = "All notes")

    private val GUITAR_SWEET = listOf(-2.0, 0.0, 2.0, 3.0, -2.0, 0.0)

    val ALL: List<InstrumentPreset> = listOf(
        CHROMATIC,

        // ---- Guitar ----
        ps("guitar_std",    "Guitar", "Guitar · Standard (E A D G B E)", GUITAR_SWEET,
            "E2","A2","D3","G3","B3","E4"),
        ps("guitar_dropd",  "Guitar", "Guitar · Drop D", GUITAR_SWEET,
            "D2","A2","D3","G3","B3","E4"),
        p("guitar_eb",      "Guitar", "Guitar · Half-step down (E♭)",
            "D#2","G#2","C#3","F#3","A#3","D#4"),
        ps("guitar_dadgad", "Guitar", "Guitar · DADGAD", listOf(-2.0, 0.0, -2.0, -4.0, 0.0, -2.0),
            "D2","A2","D3","G3","A3","D4"),
        ps("guitar_openg",  "Guitar", "Guitar · Open G", listOf(-2.0, -4.0, -2.0, -4.0, 2.0, -2.0),
            "D2","G2","D3","G3","B3","D4"),
        p("guitar_opend",   "Guitar", "Guitar · Open D", "D2","A2","D3","F#3","A3","D4"),
        p("guitar7",        "Guitar", "Guitar · 7-string (B E A D G B E)",
            "B1","E2","A2","D3","G3","B3","E4"),
        p("guitar12",       "Guitar", "Guitar · 12-string (courses low→high)",
            "E2","E3","A2","A3","D3","D4","G3","G4","B3","B3","E4","E4"),
        p("guitar_baritone","Guitar", "Guitar · Baritone B standard (B E A D F♯ B)",
            "B1","E2","A2","D3","F#3","B3"),
        p("guitar_tenor",   "Guitar", "Tenor guitar (C G D A)", "C3","G3","D4","A4"),
        p("guitar_cbg3",    "Guitar", "Cigar box guitar · 3-string (G D G)", "G2","D3","G3"),

        // ---- Bass ----
        p("bass4",          "Bass", "Bass · 4-string (E A D G)",  "E1","A1","D2","G2"),
        p("bass5",          "Bass", "Bass · 5-string (B E A D G)","B0","E1","A1","D2","G2"),
        p("bass6",          "Bass", "Bass · 6-string (B E A D G C)", "B0","E1","A1","D2","G2","C3"),

        // ---- Ukulele ----
        ps("ukulele",       "Ukulele", "Ukulele · Standard (G C E A)", listOf(2.0, 0.0, 4.0, 2.0),
            "G4","C4","E4","A4"),
        p("ukulele_lowg",   "Ukulele", "Ukulele · Low G (G C E A)", "G3","C4","E4","A4"),
        p("ukulele_d",      "Ukulele", "Ukulele · D tuning (A D F♯ B)", "A4","D4","F#4","B4"),
        p("ukulele_bar",    "Ukulele", "Ukulele · Baritone (D G B E)", "D3","G3","B3","E4"),

        // ---- Mandolin family ----
        ps("mandolin",      "Mandolin family", "Mandolin (G D A E)", listOf(0.0, 2.0, 4.0, 6.0),
            "G3","D4","A4","E5"),
        p("mandola",        "Mandolin family", "Mandola (C G D A)", "C3","G3","D4","A4"),
        p("octavemandolin", "Mandolin family", "Octave mandolin (G D A E)", "G2","D3","A3","E4"),
        p("bouzouki",       "Mandolin family", "Irish bouzouki · GDAD", "G2","D3","A3","D4"),
        p("balalaika",      "Mandolin family", "Balalaika · prima (E E A)", "E4","E4","A4"),
        p("mandocello",     "Mandolin family", "Mandocello (C G D A)", "C2","G2","D3","A3"),
        p("bouzouki_gr",    "Mandolin family", "Greek bouzouki · tetrachordo (C F A D)",
            "C3","F3","A3","D4"),

        // ---- Banjo ----
        p("banjo",          "Banjo", "Banjo · Open G (g D G B D)", "G4","D3","G3","B3","D4"),
        p("banjo_doublec",  "Banjo", "Banjo · Double C (g C G C D)", "G4","C3","G3","C4","D4"),
        p("banjo_opend",    "Banjo", "Banjo · Open D (a D A D F♯ A)", "D3","A3","D4","F#4","A4"),
        p("banjo_tenor",    "Banjo", "Tenor banjo (C G D A)", "C3","G3","D4","A4"),
        p("banjo_plectrum", "Banjo", "Plectrum banjo (C G B D)", "C3","G3","B3","D4"),
        p("banjolele",      "Banjo", "Banjo ukulele (G C E A)", "G4","C4","E4","A4"),

        // ---- Steel & resonator ----
        p("dobro",          "Steel & resonator", "Dobro · G B D G B D", "G2","B2","D3","G3","B3","D4"),
        p("lapsteel_c6",    "Steel & resonator", "Lap steel · C6", "C3","E3","G3","A3","C4","E4"),
        p("pedalsteel_e9",  "Steel & resonator", "Pedal steel · E9 (10-string)",
            "B2","D3","E3","F#3","G#3","B3","D4","E4","F#4","G#4"),

        // ---- World strings ----
        p("charango",       "World strings", "Charango (G C E A E)", "G4","C5","E5","A4","E5"),
        p("cuatro",         "World strings", "Cuatro · Venezuelan (A D F♯ B)", "A3","D4","F#4","B4"),
        p("cavaquinho",     "World strings", "Cavaquinho (D G B D)", "D4","G4","B4","D5"),
        p("oud",            "World strings", "Oud · Arabic (D G A D G C)", "D2","G2","A2","D3","G3","C4"),
        p("erhu",           "World strings", "Erhu (inner D, outer A)", "D4","A4"),
        p("saz",            "World strings", "Bağlama / saz · long-neck (A D G)", "A2","D3","G3"),
        p("domra",          "World strings", "Domra · 3-string (E A D)", "E3","A3","D4"),
        p("shamisen",       "World strings", "Shamisen · niagari tuning (D G D)", "D3","G3","D4"),
        InstrumentPreset("guzheng", "Guzheng · 21 strings (D pentatonic)",
            penta(50, 21), family = "World strings"),
        p("koto",           "World strings", "Koto · Hirajōshi (13)",
            "D4","G4","A4","A#4","D5","D#5","G5","A5","A#5","D6","D#6","G6","A6"),
        p("pipa",           "World strings", "Pipa (A E D A)", "A2","E3","D3","A3"),
        p("sitar",          "World strings", "Sitar · default (raga-variable)",
            "F#3","A3","C#4","D4","F#4","A3","D4"),
        p("tanpura",        "World strings", "Tanpura · Pa sa sa Sa", "G2","C3","C3","C3"),

        // ---- Harps & plucked grids ----
        InstrumentPreset("harp34", "Harp · Lever, 34 strings (C3–A6)",
            diatonic(48, 34), family = "Harps"),
        InstrumentPreset("harp47", "Harp · Pedal, 47 strings (C♭1 up)",
            diatonic(23, 47), family = "Harps"),
        InstrumentPreset("kalimba", "Kalimba · 17-key (C4–E6)",
            diatonic(60, 17), family = "Harps"),

        // ---- Folk & early ----
        p("dulcimer",       "Folk & early", "Appalachian dulcimer · DAd", "D3","A3","D4"),
        p("kantele",        "Folk & early", "Kantele · 5-string", "D4","E4","F#4","A4","B4"),
        p("handpan",        "Folk & early", "Handpan · D Kurd (9 notes)",
            "D3","A3","A#3","C4","D4","E4","F4","A4"),
        p("harmonicaC",     "Folk & early", "Harmonica · Diatonic C (Richter, all reeds)",
            "C4","D4","E4","G4","B4","C5","D5","E5","F5","G5","A5","B5","C6"),
        InstrumentPreset("accordion", "Accordion · treble, 41 keys (F3–A6)",
            chromatic(53, 41), family = "Folk & early"),
        InstrumentPreset("melodica", "Melodica · 37 keys (F3–F6)",
            chromatic(53, 37), family = "Folk & early"),
        InstrumentPreset("tinwhistle", "Tin whistle · D (D4–D6)",
            chromatic(62, 25), family = "Folk & early"),
        InstrumentPreset("shakuhachi", "Shakuhachi · 1.8 (D pentatonic)",
            pentaMinor(62, 11), family = "Folk & early"),

        // ---- Orchestral strings ----
        p("violin",         "Orchestral strings", "Violin (G D A E)",   "G3","D4","A4","E5"),
        p("viola",          "Orchestral strings", "Viola (C G D A)",    "C3","G3","D4","A4"),
        p("cello",          "Orchestral strings", "Cello (C G D A)",    "C2","G2","D3","A3"),
        InstrumentPreset("doublebass", "Double bass (E A D G) — written 8va up",
            listOf(toMidi("E1"), toMidi("A1"), toMidi("D2"), toMidi("G2")),
            family = "Orchestral strings", writtenOffset = 12),

        // ---- Keys ----
        InstrumentPreset("piano88", "Piano · 88 keys (stretch assist)",
            (21..108).toList(), family = "Keys"),
        InstrumentPreset("celesta", "Celesta — sounds 8va above written",
            chromatic(72, 37), family = "Keys", writtenOffset = -12),

        // ---- Percussion ----
        p("timpani",        "Percussion", "Timpani · 4 drums (typical centers)",
            "D2","F2","A2","D3"),
        InstrumentPreset("marimba43", "Marimba · 4.3 oct (A2–C7)",
            chromatic(45, 52), family = "Percussion", partials = listOf(1.0, 4.0, 10.0)),
        InstrumentPreset("vibraphone", "Vibraphone · 3 oct (F3–F6)",
            chromatic(53, 37), family = "Percussion", partials = listOf(1.0, 4.0, 10.0)),
        InstrumentPreset("xylophone", "Xylophone — sounds 8va above written (F45–C8)",
            chromatic(65, 44), family = "Percussion", writtenOffset = -12,
            partials = listOf(1.0, 3.0, 6.0)),
        InstrumentPreset("glockenspiel", "Glockenspiel — sounds 2×8va above written (G5–C8)",
            chromatic(79, 30), family = "Percussion", writtenOffset = -24),
        InstrumentPreset("steelpan", "Steelpan · tenor/lead (D4–F♯6)",
            chromatic(62, 29), family = "Percussion"),

        // ---- Woodwinds (written pitch auto-configured) ----
        w("flute",        "Woodwinds", "Flute (C)", 0),
        InstrumentPreset("piccolo", "Piccolo (C) — sounds 8va up",
            emptyList(), family = "Woodwinds", writtenOffset = -12),
        w("oboe",         "Woodwinds", "Oboe (C)", 0),
        w("bassoon",      "Woodwinds", "Bassoon (C)", 0),
        w("clarinet_bb",  "Woodwinds", "Clarinet in B♭", 2),
        w("basscl_bb",    "Woodwinds", "Bass clarinet in B♭", 14),
        w("sax_soprano",  "Woodwinds", "Soprano sax in B♭", 2),
        w("sax_alto",     "Woodwinds", "Alto sax in E♭", 9),
        w("sax_tenor",    "Woodwinds", "Tenor sax in B♭", 14),
        w("sax_bari",     "Woodwinds", "Baritone sax in E♭", 21),
        InstrumentPreset("recorder_s", "Recorder · Soprano (C) — parts at sounding pitch",
            emptyList(), family = "Woodwinds", writtenOffset = 0),
        InstrumentPreset("recorder_a", "Recorder · Alto (F) — parts at sounding pitch",
            emptyList(), family = "Woodwinds", writtenOffset = 0),
        w("englishhorn",  "Woodwinds", "English horn (cor anglais) in F", 7),
        w("altflute_g",   "Woodwinds", "Alto flute in G", 5),
        w("contrabassoon","Woodwinds", "Contrabassoon — sounds 8va below written", 12),
        InstrumentPreset("sax_sopranino", "Sopranino sax in E♭ — sounds m3 above written",
            emptyList(), family = "Woodwinds", writtenOffset = -3),
        w("cl_alto_eb",   "Woodwinds", "Alto clarinet in E♭", 9),
        w("cl_contra_bb", "Woodwinds", "Contrabass clarinet in B♭", 26),
        w("sax_bass",     "Woodwinds", "Bass sax in B♭", 26),

        // ---- Brass ----
        w("trumpet_bb",   "Brass", "Trumpet in B♭", 2),
        w("cornet_bb",    "Brass", "Cornet in B♭", 2),
        w("flugel_bb",    "Brass", "Flugelhorn in B♭", 2),
        w("horn_f",       "Brass", "French horn in F", 7),
        w("trombone_c",   "Brass", "Trombone (C, concert)", 0),
        w("tuba_c",       "Brass", "Tuba (C, concert)", 0),
        w("horn_eb_tenor","Brass", "Tenor horn in E♭", 9),
        w("baritone_bb",  "Brass", "Baritone horn in B♭ (treble)", 14),
        w("euphonium_bb", "Brass", "Euphonium in B♭ (treble)", 14),
        w("mellophone_f", "Brass", "Mellophone in F", 7),
        w("cornet_eb",    "Brass", "Soprano cornet in E♭", 9),

        // ---- Bagpipes ----
        InstrumentPreset("bagpipes", "Bagpipes · Highland chanter",
            listOf(67, 69, 71, 73, 74, 76, 78, 79, 81),
            family = "Winds", suggestedA4 = 475.0)
    )

    fun byId(id: String): InstrumentPreset = ALL.firstOrNull { it.id == id } ?: CHROMATIC

    /** Typical nut→saddle scale lengths (mm) for the setup wizard; editable in the UI. */
    private val SCALE_MM = mapOf(
        "guitar_std" to 648.0, "guitar_dropd" to 648.0, "guitar_opend" to 648.0,
        "guitar_openg" to 648.0, "guitar_dadgad" to 648.0, "guitar_eb" to 648.0,
        "guitar7" to 648.0, "guitar12" to 648.0, "guitar_baritone" to 680.0,
        "guitar_tenor" to 575.0, "guitar_cbg3" to 610.0,
        "bass4" to 864.0, "bass5" to 864.0, "bass6" to 864.0,
        "ukulele" to 350.0, "ukulele_lowg" to 380.0, "ukulele_d" to 350.0, "ukulele_bar" to 480.0,
        "violin" to 328.0, "viola" to 360.0, "cello" to 695.0, "doublebass" to 1060.0,
        "mandolin" to 350.0, "mandola" to 420.0, "octavemandolin" to 510.0,
        "bouzouki" to 640.0, "mandocello" to 610.0, "bouzouki_gr" to 670.0,
        "banjo" to 660.0, "banjo_doublec" to 660.0, "banjo_opend" to 660.0, "banjo_tenor" to 560.0,
        "dobro" to 570.0, "lapsteel_c6" to 570.0
    )

    fun scaleMm(id: String): Double? = SCALE_MM[id]
}
