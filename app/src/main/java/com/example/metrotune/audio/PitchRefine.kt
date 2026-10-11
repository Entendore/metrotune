package com.example.metrotune.audio

import kotlin.math.roundToInt

/**
 * Pure post-detection refinement.
 *
 * Octave errors are the classic pitch-detector failure: a harmonic tone with
 * period T also "has" period 2T (YIN may report f/2), and a weak fundamental
 * makes detectors jump to 2f. The sieve exploits that a REAL fundamental
 * explains odd harmonics too: scoring 5 harmonics of each candidate (f/2, f,
 * 2f) discriminates cleanly, including the leakage-skirt trap that defeats
 * simple energy-ratio checks.
 */
object PitchRefine {

    /** A probe counts as a harmonic hit only above noise × K. */
    const val HARMONIC_K = 6.0f

    /** Frames below this confidence are rejected (UI holds the last reading). */
    const val MIN_CONFIDENCE = 30

    /** Weighted above-noise harmonic energy of candidate f (lower harmonics weigh more). */
    fun harmonicScore(probe: (Double) -> Float, noise: Float, f: Double): Double {
        var score = 0.0
        for (n in 1..5) {
            val excess = (probe(f * n) - noise * HARMONIC_K).coerceAtLeast(0f).toDouble()
            score += excess / (1.0 + 0.25 * (n - 1))
        }
        return score
    }

    /**
     * Best-supported fundamental among f/2, f, 2f. Alternatives carry a
     * ×0.92 trust bias: only a *clearly* better harmonic explanation
     * overrules YIN, so ambiguous frames never octave-flip.
     */
    fun refineOctave(
        probe: (Double) -> Float,
        noise: Float,
        fYin: Double,
        minFreq: Double
    ): Double {
        var best = fYin
        var bestScore = harmonicScore(probe, noise, fYin)
        for (c in doubleArrayOf(fYin / 2, fYin * 2)) {
            if (c < minFreq) continue
            val s = harmonicScore(probe, noise, c) * 0.92
            if (s > bestScore) { bestScore = s; best = c }
        }
        return best
    }

    /**
     * 0–100 confidence. SNR term is scale-invariant (fundamental peak vs the
     * same frame's noise floor), so playing quietly is not punished.
     * supportRatio = fraction of harmonics 2..5 present above noise.
     */
    fun confidence(clarity: Double, snrDb: Double, supportRatio: Double): Int {
        val snrScore = (snrDb / 30.0).coerceIn(0.0, 1.0)          // 30 dB SNR = full marks
        val supportScore = (supportRatio / 0.6).coerceIn(0.0, 1.0)
        val c = 0.45 * clarity.coerceIn(0.0, 1.0) + 0.35 * snrScore + 0.20 * supportScore
        return (c * 100).roundToInt()
    }
}
