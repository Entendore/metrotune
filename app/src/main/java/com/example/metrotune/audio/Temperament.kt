package com.example.metrotune.audio

/**
 * A temperament is a 12-entry table of cents DEVIATIONS from equal
 * temperament, index 0 = the temperament's tonic. `centsFor(pc, root)`
 * rotates the table so the tonic lands on the user-selected pitch class.
 *
 * NOTE (representation fix): earlier revisions stored absolute just/historic
 * intervals but applied them as per-note deviations — detuning non-tonic
 * notes by hundreds of cents. These tables are the corrected standard form
 * (as used by professional strobe tuners): 0 for the tonic's octaves,
 * small ±values elsewhere, and the meantone "wolf" is visible as one large
 * entry rather than a corrupted scale.
 */
class Temperament(val id: String, val name: String, private val offsets: DoubleArray) {
    fun centsFor(pitchClass: Int, rootPitchClass: Int): Double {
        val idx = ((pitchClass - rootPitchClass) % 12 + 12) % 12
        return offsets[idx]
    }
}

object Temperaments {

    private fun t(id: String, name: String, vararg o: Double) =
        Temperament(id, name, doubleArrayOf(*o))

    val EQUAL = t("equal", "Equal (12-TET)",
        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

    // 5-limit just: absolute 0/111.73/203.91/315.64/386.31/498.04/590.22/701.96/
    // 813.69/884.36/1017.60/1088.27 minus 100·n
    val JUST = t("just", "Just intonation (5-limit)",
        0.0, 11.73, 3.91, 15.64, -13.69, -1.96, -9.78, 1.96, 13.69, -15.64, 17.60, -11.73)

    // Pythagorean: 0/90.22/203.91/294.13/407.82/498.04/611.73/701.96/
    // 792.18/905.87/996.09/1109.78 minus 100·n
    val PYTHAGOREAN = t("pythagorean", "Pythagorean",
        0.0, -9.78, 3.91, -5.87, 7.82, -1.96, 11.73, 1.96, -7.82, 5.87, -3.91, 9.78)

    // Quarter-comma meantone: 0/76.05/193.16/269.21/386.31/503.42/579.47/696.58/
    // 772.64/889.74/1006.84/1082.90 minus 100·n (wolf fifth on D♯ = +35.7¢)
    val MEANTONE = t("meantone", "Quarter-comma meantone",
        0.0, -23.95, -6.84, -30.79, -13.69, 3.42, -20.53, -3.42, -27.36, -10.26, 6.84, -17.10)

    // Werckmeister III (C–G, G–D, D–A, B–F♯ narrowed ¼ Pythagorean comma, rest pure)
    val WERCKMEISTER = t("werckmeister", "Werckmeister III",
        0.0, -9.78, -7.82, -5.87, -9.78, -1.96, -11.73, -3.91, -7.82, -11.73, -3.91, -7.82)

    // Vallotti (F–C–G–D–A–E–B narrowed ⅙ comma, rest pure)
    val VALLOTTI = t("vallotti", "Vallotti",
        0.0, -5.87, -3.91, -1.96, -7.82, 1.96, -7.82, -1.96, -3.91, -5.87, 0.0, -9.78)

    val ALL = listOf(EQUAL, JUST, PYTHAGOREAN, MEANTONE, WERCKMEISTER, VALLOTTI)
    fun byId(id: String) = ALL.firstOrNull { it.id == id } ?: EQUAL

    /**
     * Deviation of each fifth (built on pitch class p) from the pure 3:2
     * (701.96¢) under the temperament, rooted at root. Index = lower note's
     * pitch class. Deviations are per-pitch-class, so no octave term is
     * needed; the meantone/just "wolf" shows up as one large entry.
     */
    fun fifthDeviations(t: Temperament, root: Int): DoubleArray = DoubleArray(12) { p ->
        t.centsFor((p + 7) % 12, root) - t.centsFor(p, root) - 1.96
    }
}
