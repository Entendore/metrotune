package com.example.metrotune.audio

/**
 * Piano pitch-raise overpull: when a string is far flat, tune it PAST the
 * target by a percentage of its flatness — the plate/soundboard settles and
 * pulls it back down. Applied only beyond a flatness threshold, capped.
 * As the string approaches pitch the overpull shrinks, so the target
 * converges to true pitch exactly at zero — the needle-to-zero workflow
 * piano techs expect.
 */
object Overpull {
    const val ENTER_CENTS = 20.0     // no overpull for small deviations
    const val CAP_CENTS = 60.0       // never overshoot more than this

    /** @param flatCents the measured deviation (negative = flat). Returns overpull cents ≥ 0. */
    fun apply(flatCents: Double, pct: Double,
              enter: Double = ENTER_CENTS, cap: Double = CAP_CENTS): Double {
        if (pct <= 0.0 || flatCents >= -enter) return 0.0
        return minOf(cap, -flatCents * pct / 100.0)
    }
}
