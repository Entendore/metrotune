package com.example.metrotune

import com.example.metrotune.audio.MetronomeEngine.TrainerConfig
import com.example.metrotune.audio.MetronomeEngine.TrainerMode
import com.example.metrotune.audio.PolyphonicDetector
import com.example.metrotune.audio.TrainerMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class EngineAndPolyTest {

    // ---------- trainer state machine ----------

    @Test fun rampAdvancesEveryNBars() {
        val cfg = TrainerConfig(TrainerMode.RAMP, rampStepBpm = 2, rampEveryBars = 4)
        assertEquals(120 to false, TrainerMath.stateFor(0, 120, cfg))
        assertEquals(120 to false, TrainerMath.stateFor(3, 120, cfg))
        assertEquals(122 to false, TrainerMath.stateFor(4, 120, cfg))
        assertEquals(126 to false, TrainerMath.stateFor(8, 120, cfg))
    }

    @Test fun rampCapsAtMaxBpm() {
        val cfg = TrainerConfig(TrainerMode.RAMP, rampStepBpm = 2, rampEveryBars = 1)
        assertEquals(250, TrainerMath.stateFor(10, 245, cfg).first)
        assertEquals(250, TrainerMath.stateFor(500, 120, cfg).first)
    }

    @Test fun gapAlternatesPlayAndMute() {
        val cfg = TrainerConfig(TrainerMode.GAP, gapPlayBars = 2, gapMuteBars = 2)
        assertFalse(TrainerMath.stateFor(0, 120, cfg).second)
        assertFalse(TrainerMath.stateFor(1, 120, cfg).second)
        assertTrue(TrainerMath.stateFor(2, 120, cfg).second)
        assertTrue(TrainerMath.stateFor(3, 120, cfg).second)
        assertFalse(TrainerMath.stateFor(4, 120, cfg).second)
        assertEquals(120, TrainerMath.stateFor(7, 120, cfg).first)  // bpm unchanged while muted
    }

    @Test fun offNeverMutes() {
        val cfg = TrainerConfig(TrainerMode.OFF)
        repeat(10) { bar -> assertEquals(120 to false, TrainerMath.stateFor(bar, 120, cfg)) }
    }

    // ---------- polyphonic detection ----------

    private fun detector() = PolyphonicDetector(44100).apply { prepare(8192) }

    private fun twoSines(f1: Double, a1: Double, f2: Double, a2: Double, n: Int = 8192) =
        FloatArray(n) { i ->
            val t = i.toDouble() / 44100.0
            (a1 * sin(2 * PI * f1 * t) + a2 * sin(2 * PI * f2 * t)).toFloat()
        }

    @Test fun polyDetectsTwoSimultaneousNotes() {
        val e2 = 82.4069   // guitar low E
        val a2 = 110.0     // open A
        val r = detector().detect(twoSines(e2, 0.5, a2, 0.45), listOf(e2, a2), 0.5f)
        assertEquals(2, r.size)
        assertTrue(abs(r.first { it.stringIndex == 0 }.cents) < 25.0)
        assertTrue(abs(r.first { it.stringIndex == 1 }.cents) < 25.0)
    }

    @Test fun polyIgnoresSilentStrings() {
        // Silent target chosen far from the played note's leakage skirt
        // (110 Hz sits only ~5 bins from E2 at this resolution).
        val e2 = 82.4069
        val a4 = 440.0
        val r = detector().detect(twoSines(e2, 0.5, e2, 0.0), listOf(e2, a4), 0.5f)
        assertEquals(listOf(0), r.map { it.stringIndex })
    }
}
