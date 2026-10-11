package com.example.metrotune

import com.example.metrotune.audio.TuningSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TuningSessionTest {

    private val counts = listOf(6, 4, 2)   // guitar, bass, erhu

    @Test fun flattensInOrder() {
        val steps = TuningSession.steps(counts)
        assertEquals(12, steps.size)
        assertEquals(TuningSession.Step(0, 0), steps.first())
        assertEquals(TuningSession.Step(0, 5), steps[5])
        assertEquals(TuningSession.Step(1, 0), steps[6])
        assertEquals(TuningSession.Step(2, 1), steps.last())
    }

    @Test fun locateBoundaries() {
        assertEquals(TuningSession.Step(0, 0), TuningSession.locate(0, counts))
        assertEquals(TuningSession.Step(0, 5), TuningSession.locate(5, counts))
        assertEquals(TuningSession.Step(1, 0), TuningSession.locate(6, counts))
        assertEquals(TuningSession.Step(2, 1), TuningSession.locate(11, counts))
        assertNull(TuningSession.locate(12, counts))   // session complete
        assertNull(TuningSession.locate(0, emptyList()))
    }

    @Test fun singleInstrumentEqualsPlainAudit() {
        assertEquals(6, TuningSession.totalStrings(listOf(6)))
        assertEquals(TuningSession.Step(0, 3), TuningSession.locate(3, listOf(6)))
    }
}
