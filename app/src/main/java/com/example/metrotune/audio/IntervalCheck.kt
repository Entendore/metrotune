package com.example.metrotune.audio

import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Measured interval between two sounding notes, compared against BOTH tuning
 * systems players actually use: just intonation (double stops, ensemble
 * blending) and 12-TET (piano reference). 5-limit just values; 5-limit has
 * no clean minor 2nd or tritone — those report equal-temperament only.
 */
object IntervalCheck {

    val NAMES = listOf("Unison", "Minor 2nd", "Major 2nd", "Minor 3rd", "Major 3rd",
        "Perfect 4th", "Tritone", "Perfect 5th", "Minor 6th", "Major 6th",
        "Minor 7th", "Major 7th", "Octave")

    val JUST5 = mapOf(
        0 to 0.0, 2 to 203.91, 3 to 315.64, 4 to 386.31, 5 to 498.04,
        7 to 701.96, 8 to 813.69, 9 to 884.36, 10 to 1017.60, 11 to 1088.27, 12 to 1200.0)

    data class Info(
        val name: String,
        val measuredCents: Double,   // always 0..~1200 (low→high)
        val justCents: Double?,      // null = no 5-limit value
        val justDeviation: Double?,  // measured − just
        val etDeviation: Double      // measured − 100·semitones
    )

    fun info(f1: Double, f2: Double): Info? {
        if (f1 <= 0.0 || f2 <= 0.0) return null
        val lo = minOf(f1, f2)
        val hi = maxOf(f1, f2)
        val cents = 1200.0 * ln(hi / lo) / ln(2.0)
        val semi = (cents / 100.0).roundToInt().coerceIn(0, 12)
        val just = JUST5[semi]
        return Info(NAMES[semi], cents, just,
            just?.let { cents - it }, cents - 100.0 * semi)
    }
}
