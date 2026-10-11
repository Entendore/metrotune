package com.example.metrotune

import com.example.metrotune.audio.TuneGuidance
import org.junit.Assert.assertEquals
import org.junit.Test

class TuneGuidanceTest {

    @Test fun withinToleranceIsInTune() {
        assertEquals(TuneGuidance.State.IN_TUNE, TuneGuidance.of(4.9, 5.0).state)
        assertEquals(0, TuneGuidance.of(0.0, 5.0).level)
        assertEquals(TuneGuidance.State.IN_TUNE, TuneGuidance.of(-5.0, 5.0).state)
    }

    @Test fun justBeyondToleranceIsLevel1() {
        val g = TuneGuidance.of(-5.1, 5.0)
        assertEquals(TuneGuidance.State.FLAT, g.state)
        assertEquals(1, g.level)
    }

    @Test fun magnitudeGrowsInTenCentSteps() {
        // floor(past/10) + 1: 30¢ past → 4th step, 15¢ past → 2nd step
        assertEquals(4, TuneGuidance.of(-35.0, 5.0).level)
        assertEquals(2, TuneGuidance.of(20.0, 5.0).level)
    }

    @Test fun magnitudeCapsAtFive() {
        assertEquals(5, TuneGuidance.of(-49.9, 5.0).level)
    }

    @Test fun tightToleranceEscalatesFaster() {
        assertEquals(1, TuneGuidance.of(-3.0, 1.0).level)    // 2¢ past at ±1¢
    }

    @Test fun signFollowsDirection() {
        assertEquals(TuneGuidance.State.SHARP, TuneGuidance.of(12.0, 5.0).state)
        assertEquals(TuneGuidance.State.FLAT, TuneGuidance.of(-12.0, 5.0).state)
    }
}
