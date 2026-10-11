package com.example.metrotune

import com.example.metrotune.audio.IntonationDrill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntonationDrillTest {

    private fun drill() = IntonationDrill(toleranceCents = 10.0)

    private fun hold(d: IntonationDrill, fromMs: Long, frames: Int, step: Long = 100) {
        for (i in 1..frames) d.onFrame(69, 0.0, 90, fromMs + i * step)
    }

    @Test fun holdAccumulates() {
        val d = drill(); d.start(69, 0)
        hold(d, 0, 10)
        val s = d.onFrame(69, 0.0, 90, 1000)
        assertTrue(s.holding)
        assertEquals(900, s.currentHoldMs)     // holdStart = first good frame (100)
        assertEquals(1000, s.inTuneTotalMs)    // 10 × 100 ms credited
        assertEquals(1, s.attempts)
    }

    @Test fun bestHoldSurvivesBreaks() {
        val d = drill(); d.start(69, 0)
        hold(d, 0, 20)                          // 1.9 s hold
        d.onFrame(70, 0.0, 90, 2100)            // break
        hold(d, 2100, 10)                       // 0.9 s hold → attempt 2
        val s = d.onFrame(70, 0.0, 90, 3200)
        assertEquals(1900, s.bestHoldMs)
        assertEquals(2, s.attempts)
    }

    @Test fun silenceGapNotCredited() {
        val d = drill(); d.start(69, 0)
        d.onFrame(69, 0.0, 90, 100)
        val s = d.onFrame(69, 0.0, 90, 2000)    // 1.9 s gap → clamped
        assertEquals(350, s.inTuneTotalMs)      // 100 + 250 (clamp)
    }

    @Test fun wrongNoteAndLowConfidenceBreakHold() {
        val d = drill(); d.start(69, 0)
        hold(d, 0, 5)
        assertFalse(d.onFrame(71, 0.0, 90, 600).holding)
        assertTrue(d.onFrame(69, 0.0, 90, 700).holding)
        assertFalse(d.onFrame(69, 0.0, 30, 800).holding)   // low confidence
        assertEquals(3, d.onFrame(69, 0.0, 90, 900).attempts)
    }

    @Test fun stopMeasuresToLastGoodFrame() {
        val d = drill(); d.start(69, 0)
        hold(d, 0, 15)
        val s = d.stop(1600)                    // stopped "late" — must not inflate
        assertEquals(1400, s.bestHoldMs)
        assertFalse(s.active)
    }

    @Test fun toleranceBoundary() {
        val d = drill(); d.start(69, 0)
        assertTrue(d.onFrame(69, 9.9, 90, 100).holding)
        assertFalse(d.onFrame(69, 10.1, 90, 200).holding)
    }
}
