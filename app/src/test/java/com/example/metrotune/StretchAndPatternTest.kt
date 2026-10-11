package com.example.metrotune

import com.example.metrotune.audio.InstrumentLibrary
import com.example.metrotune.audio.StretchCurves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StretchAndPatternTest {

    @Test fun railsbackMatchesRailsbackAtExtremes() {
        assertTrue(abs(StretchCurves.railsbackCents(21) + 28.0) < 3.0)   // A0 ≈ −28¢
        assertTrue(abs(StretchCurves.railsbackCents(108) - 30.0) < 3.0)  // C8 ≈ +30¢
        assertTrue(abs(StretchCurves.railsbackCents(60)) < 4.0)          // C4 ≈ 0
    }

    @Test fun trebleStretchIsMonotonicallyRising() {
        var prev = StretchCurves.railsbackCents(80)
        for (m in 81..108) {
            val v = StretchCurves.railsbackCents(m)
            assertTrue(v >= prev - 0.01); prev = v
        }
    }

    @Test fun sweetenedOffsetsMatchStringCount() {
        InstrumentLibrary.ALL.forEach { p ->
            if (p.sweetened.isNotEmpty())
                assertEquals("preset ${p.id}", p.strings.size, p.sweetened.size)
        }
    }

    @Test fun pianoHas88KeysSpanningA0ToC8() {
        val piano = InstrumentLibrary.byId("piano88")
        assertEquals(88, piano.strings.size)
        assertEquals(21, piano.strings.first())
        assertEquals(108, piano.strings.last())
    }
}
