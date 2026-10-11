package com.example.metrotune

import com.example.metrotune.audio.AuditReport
import com.example.metrotune.audio.InstrumentLibrary
import com.example.metrotune.audio.PolyMath
import com.example.metrotune.audio.SpectrumAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PolyAndCustomTest {

    // ---- polyrhythm scheduling ----

    @Test fun threeAgainstTwo() {
        val ev = PolyMath.events(3, 2, 1.0)
        assertEquals(5, ev.size)
        assertEquals(0.0, ev.first().time, 1e-12)
        assertEquals(0, ev.first().voice)                       // accented A first
        assertEquals(3, ev.count { it.voice == 0 })
        assertEquals(2, ev.count { it.voice == 1 })
        assertTrue(ev.zipWithNext().all { (x, y) -> y.time >= x.time })
        assertEquals(listOf(0.0, 1.0 / 3, 2.0 / 3),
            ev.filter { it.voice == 0 }.map { it.time })
        assertEquals(listOf(0.0, 0.5),
            ev.filter { it.voice == 1 }.map { it.time })
    }

    @Test fun countInIsVoiceAOnly() {
        assertTrue(PolyMath.events(3, 0, 1.0).all { it.voice == 0 })
    }

    // ---- custom instrument parsing ----

    @Test fun parsesMixedSeparators() {
        assertEquals(listOf(40, 45, 50), InstrumentLibrary.parseStrings("E2, A2 D3"))
        assertEquals(listOf(64, 64), InstrumentLibrary.parseStrings("E4;E4"))
        assertEquals(listOf(40), InstrumentLibrary.parseStrings("E2,,"))
    }

    @Test fun rejectsBadInput() {
        assertNull(InstrumentLibrary.parseStrings("E2, H3"))
        assertNull(InstrumentLibrary.parseStrings("E9"))     // above C8
        assertNull(InstrumentLibrary.parseStrings(""))
        assertNull(InstrumentLibrary.parseStrings("A0, B0, x2"))
    }

    // ---- mallet partial probing (pure DSP) ----

    @Test fun partialSeriesProbesFindMalletPartials() {
        val sr = 44100; val n = 8192; val f0 = 220.0
        val buf = FloatArray(n) { i ->
            val t = i.toDouble() / sr
            (0.6 * sin(2 * PI * f0 * t) + 0.4 * sin(2 * PI * 4 * f0 * t)
                + 0.25 * sin(2 * PI * 10 * f0 * t)).toFloat()
        }
        val sa = SpectrumAnalyzer(sr).apply { prepare(n) }
        sa.run(buf)
        val noise = sa.noiseFloor()
        listOf(1.0, 4.0, 10.0).forEach { r ->
            assertTrue("partial $r", sa.probe(f0 * r).magnitude > noise * 20)
        }
        // 3×f0 is NOT part of a marimba bar's series — must stay near noise
        assertTrue(sa.probe(f0 * 3).magnitude < noise * 8)
    }

    // ---- audit report ----

    @Test fun auditCsvFormat() {
        val csv = AuditReport.csv("Harp · 34", 440.0, listOf(
            AuditReport.AuditRow(0, -2.3, 0.4),
            AuditReport.AuditRow(1, 4.7, 0.8)))
        assertTrue(csv.contains("string,final_cents,stability_cents"))
        assertTrue(csv.contains("1,-2.3,0.40"))
        assertTrue(csv.contains("2,+4.7,0.80"))
        assertTrue(csv.contains("strings_over_5c,0"))
    }
}
