package com.example.metrotune

import com.example.metrotune.audio.PitchTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchTrackerTest {

    private val a4 = 440.0
    private val f = { midi: Int, cents: Double ->
        440.0 * Math.pow(2.0, (midi - 69 + cents / 100.0) / 12.0)
    }

    @Test fun stableThroughVibrato() {
        val t = PitchTracker()
        var changed = 0
        repeat(60) { i ->
            if (t.update(f(69, 30.0 * Math.sin(i / 4.0)), 0.9, a4).noteChanged) changed++
        }
        assertEquals(1, changed)                       // initial lock only
        assertEquals(69, t.update(f(69, 0.0), 0.9, a4).midi)
    }

    @Test fun sustainedDriftSwitchesDecisively() {
        val t = PitchTracker()
        repeat(8) { t.update(f(69, 0.0), 0.9, a4) }    // lock A4
        repeat(10) { t.update(f(70, 20.0), 0.9, a4) }  // drift past boundary
        assertEquals(70, t.update(f(70, 20.0), 0.9, a4).midi)
    }

    @Test fun singleFrameExcursionsNeverSwitch() {
        val t = PitchTracker()
        repeat(8) { t.update(f(69, 0.0), 0.9, a4) }
        repeat(20) {
            t.update(f(75, 0.0), 0.9, a4)              // blip: a wrong note
            assertEquals(69, t.update(f(69, 0.0), 0.9, a4).midi)
        }
    }

    @Test fun octaveAlternationDoesNotFlipFlop() {
        val t = PitchTracker()
        repeat(8) { t.update(f(69, 0.0), 0.9, a4) }
        repeat(30) {                                    // detector oscillating f / 2f
            assertEquals(69, t.update(f(81, 0.0), 0.9, a4).midi)
            assertEquals(69, t.update(f(69, 0.0), 0.9, a4).midi)
        }
    }

    @Test fun realNoteChangeSwitchesAfterSustain() {
        val t = PitchTracker()
        repeat(8) { t.update(f(69, 0.0), 0.9, a4) }
        // The challenger needs 4 frames to clear the switch margin against a
        // saturated leader (score math: 22.6, 47, 71.5, 95.9 vs decaying 100).
        assertFalse(t.update(f(76, 0.0), 0.9, a4).noteChanged)
        assertFalse(t.update(f(76, 0.0), 0.9, a4).noteChanged)
        assertFalse(t.update(f(76, 0.0), 0.9, a4).noteChanged)
        val d = t.update(f(76, 0.0), 0.9, a4)
        assertTrue(d.noteChanged)
        assertEquals(76, d.midi)
    }

    @Test fun freqEmaAnchorsOnSwitch() {
        val t = PitchTracker()
        repeat(10) { t.update(f(64, 0.0), 0.9, a4) }
        repeat(6) { t.update(f(70, 0.0), 0.9, a4) }     // switch to B4
        val d = t.update(f(70, 0.0), 0.9, a4)
        assertTrue(d.frequency > 460.0 && d.frequency < 500.0)  // not dragged toward E4
    }
}
