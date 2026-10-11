package com.example.metrotune

import com.example.metrotune.audio.InstrumentLibrary
import com.example.metrotune.audio.IntonationMath
import com.example.metrotune.audio.IntonationWizard
import com.example.metrotune.audio.IntervalCheck
import com.example.metrotune.audio.Temperaments
import com.example.metrotune.audio.TuningDetect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class MoreProTest {

    // ---- tuning detection ----

    private val guitar = InstrumentLibrary.byId("guitar_std")
    private val dropD = InstrumentLibrary.byId("guitar_dropd")
    private val violin = InstrumentLibrary.byId("violin")

    @Test fun standardRecognized() {
        val top = TuningDetect.detect(guitar.strings, listOf(guitar, dropD, violin)).first()
        assertEquals("guitar_std", top.presetId)
        assertEquals(1.0, top.score, 1e-9)
    }

    @Test fun dropDRecognizedOverStandard() {
        val played = listOf(38, 45, 50, 55, 59, 64)   // D2 A2 D3 G3 B3 E4
        val matches = TuningDetect.detect(played, listOf(guitar, dropD))
        assertEquals("guitar_dropd", matches.first().presetId)
        assertEquals(1.0, matches.first().score, 1e-9)
        assertEquals(5.0 / 6.0, matches.first { it.presetId == "guitar_std" }.score, 1e-9)
    }

    @Test fun scalesNeverMatch() {
        val scale = listOf(40, 41, 43, 45, 47, 48, 50)   // chromatic-ish run
        assertTrue(TuningDetect.detect(scale, listOf(guitar, dropD, violin)).isEmpty())
    }

    @Test fun tooFewNotesYieldsNothing() {
        assertTrue(TuningDetect.detect(listOf(40, 45), listOf(guitar)).isEmpty())
    }

    // ---- interval check ----

    @Test fun perfectFifthJustAndEt() {
        val info = IntervalCheck.info(440.0, 660.0)!!
        assertEquals("Perfect 5th", info.name)
        assertEquals(701.96, info.measuredCents, 0.01)
        assertEquals(0.0, info.justDeviation!!, 0.01)      // 3:2 is pure
        assertEquals(1.96, info.etDeviation, 0.01)         // ET fifth is narrow
    }

    @Test fun orderIndependentAndMajorThird() {
        // 550.0 Hz is the TRUE 5:4 just third above A4 (554.37 would be the
        // ET third, +13.69¢ vs just).
        val up = IntervalCheck.info(440.0, 550.0)!!
        val down = IntervalCheck.info(550.0, 440.0)!!
        assertEquals(up.measuredCents, down.measuredCents, 1e-9)
        assertEquals("Major 3rd", up.name)
        assertEquals(0.0, up.justDeviation!!, 0.01)
        assertEquals(-13.69, up.etDeviation, 0.01)
    }

    @Test fun minorSecondHasNoJustValue() {
        val info = IntervalCheck.info(440.0, 466.16)!!     // A → A♯
        assertEquals("Minor 2nd", info.name)
        assertNull(info.justCents)
    }

    // ---- nut diagnosis ----

    @Test fun perfectFretIsZero() {
        val open = 110.0
        val f1 = open * 2.0.pow(1.0 / 12.0)
        assertEquals(0.0, IntonationMath.semitoneDeviationCents(open, f1), 1e-9)
    }

    @Test fun highNutReadsSharp() {
        val open = 110.0
        val f1 = open * 2.0.pow(1.0 / 12.0) * 2.0.pow(8.0 / 1200.0)   // +8¢ stretch
        val dev = IntonationMath.semitoneDeviationCents(open, f1)
        assertEquals(8.0, dev, 0.01)
        assertTrue(dev > 3.0)   // above the "consider filing" bar
    }

    @Test fun nutWizardModeProducesCentsOnly() {
        val open = 110.0
        val f1 = open * 2.0.pow(1.0 / 12.0) * 2.0.pow(6.0 / 1200.0)
        val w = IntonationWizard(648.0, samplesNeeded = 3, semitone = true)
        repeat(3) { w.feed(open) }
        repeat(3) { w.feed(f1) }
        assertEquals(IntonationWizard.Phase.DONE, w.state.phase)
        assertEquals(6.0, w.lastResult!!.deviationCents, 0.01)
        assertEquals(0.0, w.lastResult!!.offsetMm, 1e-9)   // no mm for nut work
    }

    // ---- fifths deviations ----

    @Test fun equalTemperamentFifthsAllNarrow() {
        val fifths = Temperaments.fifthDeviations(Temperaments.EQUAL, 0)
        for (v in fifths) assertEquals(-1.96, v, 0.01)
    }

    @Test fun justHasWolfOnDToA() {
        val fifths = Temperaments.fifthDeviations(Temperaments.JUST, 0)
        assertEquals(-21.51, fifths[2], 0.05)   // D→A wolf
        assertEquals(0.0, fifths[0], 0.01)      // C→G pure
        assertEquals(0.0, fifths[7], 0.02)      // G→D pure
    }
}
