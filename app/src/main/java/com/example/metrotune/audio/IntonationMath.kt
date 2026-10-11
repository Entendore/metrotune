package com.example.metrotune.audio

import kotlin.math.pow

/**
 * 12th-fret / 1st-fret intonation math.
 *
 * Measurement is differential: deviation = fretted vs. one octave above the
 * OPEN string of the same measurement — so the open string doesn't need to
 * be perfectly at pitch, eliminating the classic double-error of absolute
 * methods.
 *
 * Saddle offset: the 12th fret is fixed at half the (design) scale; if the
 * fretted note is D cents sharp, the fretted vibrating length must grow by
 * 2^(D/1200):  offset = (scale/2) · (2^(D/1200) − 1).
 * Positive = move saddle AWAY from the nut (toward the bridge).
 */
object IntonationMath {

    fun deviationCents(openHz: Double, frettedHz: Double): Double =
        1200.0 * kotlin.math.ln(frettedHz / (2.0 * openHz)) / kotlin.math.ln(2.0)

    fun offsetMm(scaleMm: Double, deviationCents: Double): Double =
        scaleMm / 2.0 * (2.0.pow(deviationCents / 1200.0) - 1.0)

    /** 1st-fret differential: positive = fretted note sharp = nut slots too high. */
    fun semitoneDeviationCents(openHz: Double, fret1Hz: Double): Double =
        1200.0 * kotlin.math.ln((fret1Hz / openHz) / 2.0.pow(1.0 / 12.0)) / kotlin.math.ln(2.0)
}
