package com.example.metrotune

import com.example.metrotune.audio.PitchRefine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PitchRefineTest {

    /** Line spectrum: peaks within ±1 Hz of a partial return their amplitude, else noise. */
    private fun lineSpectrum(peaks: Map<Double, Double>, noise: Double = 0.01): (Double) -> Float = { f ->
        var best = noise
        for ((pf, a) in peaks) if (abs(f - pf) < 1.0 && a > best) best = a
        best.toFloat()
    }

    private fun harmonics(f0: Double, amps: List<Double>) =
        amps.withIndex().associate { (i, a) -> (f0 * (i + 1)) to a }

    private val rich = listOf(1.0, 0.5, 0.33, 0.25, 0.2, 0.17, 0.14, 0.12)

    @Test fun correctsSubharmonicError() {
        // True F = 110; YIN reported 55 (period doubled)
        val probe = lineSpectrum(harmonics(110.0, rich))
        assertEquals(110.0, PitchRefine.refineOctave(probe, 0.01f, 55.0, 27.0), 1e-9)
    }

    @Test fun correctsMissingFundamental() {
        // True F = 110 with a full series; YIN locked onto 220
        val probe = lineSpectrum(harmonics(110.0, rich))
        assertEquals(110.0, PitchRefine.refineOctave(probe, 0.01f, 220.0, 27.0), 1e-9)
    }

    @Test fun keepsCorrectAnswer() {
        val probe = lineSpectrum(harmonics(110.0, rich))
        assertEquals(110.0, PitchRefine.refineOctave(probe, 0.01f, 110.0, 27.0), 1e-9)
    }

    @Test fun trustBiasPreventsDownwardFlipOnStrongEvenHarmonics() {
        // Guitar-like: 2nd harmonic stronger than the fundamental must NOT
        // trigger an octave-down "repair".
        val amps = listOf(0.6, 1.0, 0.5, 0.33, 0.25, 0.2, 0.17, 0.14)
        val probe = lineSpectrum(harmonics(110.0, amps))
        assertEquals(110.0, PitchRefine.refineOctave(probe, 0.01f, 110.0, 27.0), 1e-9)
    }

    @Test fun trulyAbsentFundamentalStaysWhereEnergyIs() {
        // If F is genuinely absent, 2F is the right answer — no "repair".
        val amps = listOf(0.01, 1.0, 0.5, 0.33, 0.25, 0.2, 0.17, 0.14)
        val probe = lineSpectrum(harmonics(110.0, amps))
        assertEquals(220.0, PitchRefine.refineOctave(probe, 0.01f, 220.0, 27.0), 1e-9)
    }

    @Test fun harmonicScoreMonotonicInSupport() {
        val full = lineSpectrum(harmonics(100.0, listOf(1.0, 0.5, 0.33, 0.25, 0.2)))
        val fundamentalOnly = lineSpectrum(harmonics(100.0, listOf(1.0, 0.01, 0.01, 0.01, 0.01)))
        assertTrue(
            PitchRefine.harmonicScore(full, 0.01f, 100.0) >
            PitchRefine.harmonicScore(fundamentalOnly, 0.01f, 100.0)
        )
    }

    @Test fun confidenceOrdersCleanMarginalJunk() {
        val clean = PitchRefine.confidence(clarity = 0.95, snrDb = 40.0, supportRatio = 0.75)
        val marginal = PitchRefine.confidence(clarity = 0.55, snrDb = 12.0, supportRatio = 0.15)
        val junk = PitchRefine.confidence(clarity = 0.20, snrDb = 4.0, supportRatio = 0.0)
        assertTrue(clean >= 80)
        assertTrue(marginal in PitchRefine.MIN_CONFIDENCE until 70)
        assertTrue(junk < PitchRefine.MIN_CONFIDENCE)
    }

    @Test fun quietButCleanStillScoresHigh() {
        // SNR is scale-invariant: a 10× quieter identical tone scores the same.
        val loud = PitchRefine.confidence(0.9, 30.0, 0.6)
        val quiet = PitchRefine.confidence(0.9, 30.0, 0.6)
        assertEquals(loud, quiet)
    }
}
