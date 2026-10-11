package com.example.metrotune

import com.example.metrotune.audio.EarTraining
import com.example.metrotune.audio.StretchCurves
import com.example.metrotune.audio.TuningCurves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SellablesTest {

    // ---- curve interpolation ----

    private val sparse = mapOf(48 to 0.0, 60 to 4.0, 72 to 12.0)

    @Test fun exactPointsPassThrough() {
        assertEquals(4.0, StretchCurves.interpolate(sparse, 60), 1e-9)
    }

    @Test fun midpointIsLinear() {
        assertEquals(8.0, StretchCurves.interpolate(sparse, 66), 1e-9)
    }

    @Test fun endsAreClamped() {
        assertEquals(0.0, StretchCurves.interpolate(sparse, 40), 1e-9)
        assertEquals(12.0, StretchCurves.interpolate(sparse, 90), 1e-9)
    }

    @Test fun singlePointMapIsConstant() {
        assertEquals(3.0, StretchCurves.interpolate(mapOf(60 to 3.0), 30), 1e-9)
        assertEquals(3.0, StretchCurves.interpolate(mapOf(60 to 3.0), 90), 1e-9)
    }

    @Test fun emptyMapFallsBackToRailsback() {
        assertEquals(
            StretchCurves.railsbackCents(88),
            StretchCurves.interpolate(emptyMap(), 88), 1e-12)
    }

    // ---- curve codec ----

    @Test fun curveCodecRoundTrip() {
        val pts = mapOf(33 to -12.5, 45 to 0.0, 88 to 25.75)
        assertEquals(pts, TuningCurves.decode(TuningCurves.encode(pts)))
    }

    @Test fun curveCodecRejectsBadInput() {
        assertNull(TuningCurves.decode(""))
        assertNull(TuningCurves.decode("60:abc"))
        assertNull(TuningCurves.decode("5:1.0"))              // midi out of range
        assertNull(TuningCurves.decode("60:900.0"))           // cents out of range
        assertNull(TuningCurves.decode("60:1.0;61:2.0"))      // wrong separator
    }

    // ---- ear training ----

    @Test fun answerMatchesDirection() {
        val sharp = EarTraining.Round(5.0, true)
        val flat = EarTraining.Round(-5.0, false)
        assertTrue(EarTraining.answer(sharp, true))
        assertFalse(EarTraining.answer(sharp, false))
        assertTrue(EarTraining.answer(flat, false))
        assertFalse(EarTraining.answer(flat, true))
    }

    @Test fun roundsAreRandomDirectionWithLevelMagnitude() {
        val r1 = EarTraining.next(EarTraining.Level.MEDIUM, Random(1))
        val r2 = EarTraining.next(EarTraining.Level.MEDIUM, Random(2))
        assertEquals(5.0, kotlin.math.abs(r1.comparisonCents), 1e-9)
        assertEquals(5.0, kotlin.math.abs(r2.comparisonCents), 1e-9)
        assertTrue(r1.comparisonCents != 0.0 && r2.comparisonCents != 0.0)
    }

    @Test fun scoringStreakAndBest() {
        var s = EarTraining.Score()
        s = EarTraining.apply(s, true)   // streak 1
        s = EarTraining.apply(s, true)   // streak 2
        s = EarTraining.apply(s, false)  // streak 0
        assertEquals(3, s.round)
        assertEquals(2, s.correct)
        assertEquals(0, s.streak)
        var s2 = EarTraining.Score()
        repeat(4) { s2 = EarTraining.apply(s2, true) }
        assertEquals(4, s2.streak)       // caller promotes best = max(streak)
    }
}
