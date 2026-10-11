package com.example.metrotune

import com.example.metrotune.audio.InstrumentLibrary
import com.example.metrotune.audio.Overpull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorsAndOverpullTest {

    @Test fun harpMapsHaveCorrectCountsAndBases() {
        assertEquals(34, InstrumentLibrary.byId("harp34").strings.size)
        assertEquals(48, InstrumentLibrary.byId("harp34").strings.first())   // C3
        assertEquals(47, InstrumentLibrary.byId("harp47").strings.size)
        assertEquals(23, InstrumentLibrary.byId("harp47").strings.first())   // B0 = C♭1
    }

    @Test fun diatonicStepsNeverRepeatOrSkip() {
        val s = InstrumentLibrary.byId("harp34").strings
        s.zipWithNext().forEach { (a, b) ->
            assertTrue("bad step $a→$b", b - a in 1..3)
        }
    }

    @Test fun guzhengSpansD3ToD6Pentatonic() {
        val g = InstrumentLibrary.byId("guzheng")
        assertEquals(21, g.strings.size)
        assertEquals(50, g.strings.first())
        assertEquals(98, g.strings.last())   // D6
    }

    @Test fun kalimbaPresetExists() {
        val k = InstrumentLibrary.byId("kalimba")
        assertEquals(17, k.strings.size)
        assertEquals(60, k.strings.first())
        assertEquals(88, k.strings.last())   // E6
    }

    @Test fun overpullMath() {
        assertEquals(10.0, Overpull.apply(-40.0, 25.0), 1e-9)
        assertEquals(0.0, Overpull.apply(-40.0, 0.0), 1e-9)
        assertEquals(0.0, Overpull.apply(-10.0, 25.0), 1e-9)   // below entry threshold
        assertEquals(0.0, Overpull.apply(30.0, 25.0), 1e-9)    // sharp: never
        assertEquals(Overpull.CAP_CENTS, Overpull.apply(-300.0, 50.0), 1e-9)
    }

    @Test fun writtenPitchSignsAreCorrect() {
        assertEquals(-12, InstrumentLibrary.byId("piccolo").writtenOffset)
        assertEquals(0, InstrumentLibrary.byId("recorder_s").writtenOffset)   // parts at pitch
        assertEquals(0, InstrumentLibrary.byId("recorder_a").writtenOffset)
        assertEquals(12, InstrumentLibrary.byId("doublebass").writtenOffset)
        assertEquals(-24, InstrumentLibrary.byId("glockenspiel").writtenOffset)
        assertEquals(-12, InstrumentLibrary.byId("celesta").writtenOffset)
        assertEquals(12, InstrumentLibrary.byId("contrabassoon").writtenOffset)
        assertEquals(26, InstrumentLibrary.byId("cl_contra_bb").writtenOffset)
        assertEquals(-3, InstrumentLibrary.byId("sax_sopranino").writtenOffset)
        assertEquals(475.0, InstrumentLibrary.byId("bagpipes").suggestedA4!!, 1e-9)
    }

    @Test fun generatorBackedInstrumentsHaveRightRanges() {
        assertEquals(53, InstrumentLibrary.byId("accordion").strings.first())    // F3
        assertEquals(93, InstrumentLibrary.byId("accordion").strings.last())     // A6
        assertEquals(41, InstrumentLibrary.byId("accordion").strings.size)
        assertEquals(62, InstrumentLibrary.byId("shakuhachi").strings.first())   // D4
        assertEquals(11, InstrumentLibrary.byId("shakuhachi").strings.size)
        assertEquals(79, InstrumentLibrary.byId("glockenspiel").strings.first()) // G5 sounding
        assertEquals(108, InstrumentLibrary.byId("glockenspiel").strings.last()) // C8
        assertEquals(47, InstrumentLibrary.byId("pedalsteel_e9").strings.first())// B2
        assertEquals(10, InstrumentLibrary.byId("pedalsteel_e9").strings.size)
    }
}
