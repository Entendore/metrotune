package com.example.metrotune

import com.example.metrotune.audio.SetlistItem
import com.example.metrotune.audio.SetlistMath
import org.junit.Assert.assertEquals
import org.junit.Test

class SetlistMathTest {

    private val finite = listOf(
        SetlistItem("A", 100, bars = 4),
        SetlistItem("B", 120, bars = 8))

    private val withInfinite = finite + SetlistItem("C", 90, 3, 1, 0)

    @Test fun totalBars() {
        assertEquals(12, SetlistMath.totalBars(finite))
        assertEquals(-1, SetlistMath.totalBars(withInfinite))
        assertEquals(0, SetlistMath.totalBars(emptyList()))
    }

    @Test fun locateWalksItems() {
        assertEquals(0 to 0, SetlistMath.locate(0, withInfinite, false))
        assertEquals(0 to 3, SetlistMath.locate(3, withInfinite, false))
        assertEquals(1 to 0, SetlistMath.locate(4, withInfinite, false))
        assertEquals(1 to 7, SetlistMath.locate(11, withInfinite, false))
        assertEquals(2 to 0, SetlistMath.locate(12, withInfinite, false))
        assertEquals(2 to 38, SetlistMath.locate(50, withInfinite, false))
    }

    @Test fun loopWrapsFiniteLists() {
        assertEquals(0 to 0, SetlistMath.locate(12, finite, true))
        assertEquals(0 to 2, SetlistMath.locate(14, finite, true))
        assertEquals(1 to 7, SetlistMath.locate(11, finite, true))
    }

    @Test fun pastEndWithoutLoopIsSentinel() {
        assertEquals(-1 to -1, SetlistMath.locate(12, finite, false))
        assertEquals(-1 to -1, SetlistMath.locate(0, emptyList(), true))
    }

    @Test fun infiniteItemAbsorbsRemainingBarsEvenWhenLooping() {
        assertEquals(2 to 500, SetlistMath.locate(512, withInfinite, true))
    }
}
