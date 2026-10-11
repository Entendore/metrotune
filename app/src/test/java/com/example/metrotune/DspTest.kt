package com.example.metrotune

import com.example.metrotune.audio.InstrumentLibrary
import com.example.metrotune.audio.NoteUtil
import com.example.metrotune.audio.Temperaments
import com.example.metrotune.audio.YinPitchDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class DspTest {

    @Test
    fun noteParsing() {
        assertEquals(69, InstrumentLibrary.toMidi("A4"))
        assertEquals(22, InstrumentLibrary.toMidi("Bb0"))
        assertEquals("A4", NoteUtil.noteName(69))
        assertEquals("A♯0", NoteUtil.noteName(22))
    }

    @Test
    fun a4Is440() {
        assertEquals(440.0, NoteUtil.frequency(69.0, 440.0), 1e-9)
        val target = NoteUtil.frequency(64.0, 440.0)
        assertEquals(0.0, NoteUtil.centsFrom(target, target), 1e-9)
    }

    @Test
    fun justMajorThirdFromC() {
        // Deviation-from-ET representation: 5-limit just third = 386.31¢
        // absolute = −13.69¢ vs the 400¢ ET third.
        assertEquals(-13.69, Temperaments.JUST.centsFor(4, 0), 0.01)
        // Tonic rotation: the root itself is always 0¢.
        assertEquals(0.0, Temperaments.JUST.centsFor(4, 4), 1e-9)
    }

    @Test
    fun yinDetectsPureSines() {
        val sr = 44100
        val n = 4096
        val det = YinPitchDetector(sr)
        det.prepare(n)
        for (f in listOf(110.0, 220.0, 440.0, 880.0)) {
            val buf = FloatArray(n) { i -> (0.8 * sin(2.0 * PI * f * i / sr)).toFloat() }
            val p = det.detect(buf)!!
            assertTrue(abs(p.frequency - f) < 0.6, "f=$f got ${p.frequency}")
            assertTrue(p.clarity > 0.8, "clarity ${p.clarity} at f=$f")
        }
    }

    @Test
    fun octaveRelation() {
        val a4 = NoteUtil.frequency(69.0, 440.0)
        assertEquals(2.0, NoteUtil.frequency(81.0, 440.0) / a4, 1e-9)
        assertEquals(a4 * Math.pow(2.0, 7.0 / 12.0), NoteUtil.frequency(76.0, 440.0), 1e-9)
    }
}
