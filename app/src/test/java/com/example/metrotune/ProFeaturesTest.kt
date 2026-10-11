package com.example.metrotune

import com.example.metrotune.audio.CustomTemperaments
import com.example.metrotune.audio.IntonationMath
import com.example.metrotune.audio.IntonationWizard
import com.example.metrotune.audio.StringStability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ProFeaturesTest {

    @Test fun perfectOctaveIsZeroDeviation() {
        assertEquals(0.0, IntonationMath.deviationCents(440.0, 880.0), 1e-9)
        assertEquals(0.0, IntonationMath.deviationCents(82.41, 164.82), 0.01)
    }

    @Test fun tenCentsSharpAtGuitarScale() {
        val dev = 10.0
        val fretted = 440.0 * 2.0 * 2.0.pow(dev / 1200.0)
        assertEquals(dev, IntonationMath.deviationCents(440.0, fretted), 1e-9)
        val offset = IntonationMath.offsetMm(648.0, dev)
        assertEquals(324.0 * (2.0.pow(dev / 1200.0) - 1.0), offset, 1e-9)
        assertTrue(offset > 1.5 && offset < 2.2)   // real-world: ~1.9 mm
    }

    @Test fun flatDeviationMovesTowardNut() {
        assertTrue(IntonationMath.offsetMm(648.0, -10.0) < 0)
    }

    @Test fun wizardSequencesOpenThenFretted() {
        val w = IntonationWizard(648.0, samplesNeeded = 3)
        repeat(3) { w.feed(110.0) }
        assertEquals(IntonationWizard.Phase.FRETTED, w.state.phase)
        repeat(2) { w.feed(221.0) }
        assertEquals(IntonationWizard.Phase.FRETTED, w.state.phase)
        w.feed(221.0)
        assertEquals(IntonationWizard.Phase.DONE, w.state.phase)
        val r = w.lastResult!!
        assertEquals(0, r.stringIndex)
        assertEquals(IntonationMath.deviationCents(110.0, 221.0), r.deviationCents, 1e-9)
        w.nextString()
        assertEquals(1, w.state.stringIndex)
        assertEquals(IntonationWizard.Phase.OPEN, w.state.phase)
        assertEquals(0, w.state.samples)
    }

    @Test fun stabilityConstantIsSettled() {
        val s = StringStability(windowMs = 10_000)
        repeat(10) { s.add(3.0, it * 100L) }
        val snap = s.snapshot()
        assertTrue(snap.settled)
        assertEquals(0.0, snap.stddevCents, 1e-9)
        assertEquals(0.0, snap.driftCents, 1e-9)
    }

    @Test fun stabilityCapturesDriftAndSpread() {
        val s = StringStability(windowMs = 10_000, minSamples = 4)
        s.add(0.0, 0); s.add(1.5, 100); s.add(3.0, 200)
        s.add(4.5, 400); s.add(6.0, 800)
        val snap = s.snapshot()
        assertEquals(6.0, snap.driftCents, 1e-9)
        assertFalse(snap.settled)                  // sd of 0…6 step 1.5 ≈ 2.12 > 1.5
    }

    @Test fun temperamentCodecRoundTrip() {
        val o = List(12) { it * 1.5 - 6.0 }
        assertEquals(o, CustomTemperaments.decode(CustomTemperaments.encode(o)))
    }

    @Test fun temperamentCodecRejectsBadInput() {
        assertEquals(null, CustomTemperaments.decode("0,1,2"))                       // wrong size
        assertEquals(null, CustomTemperaments.decode(List(11) { "0.0" }.joinToString(",")))
        assertEquals(null, CustomTemperaments.decode(List(12) { "abc" }.joinToString(",")))
        assertEquals(null, CustomTemperaments.decode(List(12) { "500.0" }.joinToString(",")))
        assertEquals(100.0, CustomTemperaments.sanitize(List(12) { 500.0 })[0], 1e-9)
    }
}
