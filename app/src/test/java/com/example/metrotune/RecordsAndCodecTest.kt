package com.example.metrotune

import com.example.metrotune.audio.DrillRecords
import com.example.metrotune.audio.MetronomeEngine.BeatAccent
import com.example.metrotune.audio.SetlistCodec
import com.example.metrotune.audio.SetlistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordsAndCodecTest {

    // ---- drill record merge ----

    @Test fun firstDrillIsAlwaysAPb() {
        val (e, pb) = DrillRecords.merged(null, 4_000, 5_000, 2, 1_000)
        assertTrue(pb)
        assertEquals(4_000, e.bestMs)
        assertEquals(5_000, e.inTuneMs)
        assertEquals(2, e.attempts)
    }

    @Test fun worseDrillAccumulatesWithoutPb() {
        val prev = DrillRecords.Entry(69, 9_000, 20_000, 5, 0)
        val (e, pb) = DrillRecords.merged(prev, 3_000, 4_000, 1, 999)
        assertFalse(pb)
        assertEquals(9_000, e.bestMs)
        assertEquals(24_000, e.inTuneMs)
        assertEquals(6, e.attempts)
    }

    @Test fun betterDrillFlagsPb() {
        val prev = DrillRecords.Entry(69, 9_000, 20_000, 5, 0)
        val (e, pb) = DrillRecords.merged(prev, 12_000, 2_000, 1, 999)
        assertTrue(pb)
        assertEquals(12_000, e.bestMs)
    }

    @Test fun zeroHoldNeverFlagsPb() {
        val (e, pb) = DrillRecords.merged(null, 0, 0, 0, 1)
        assertFalse(pb)
        assertEquals(0, e.bestMs)
    }

    @Test fun grades() {
        assertEquals(0, DrillRecords.grade(4_999))
        assertEquals(1, DrillRecords.grade(5_000))
        assertEquals(2, DrillRecords.grade(15_000))
        assertEquals(3, DrillRecords.grade(30_000))
    }

    // ---- setlist codec ----

    @Test fun roundTripWithPatterns() {
        val items = listOf(
            SetlistItem("Verse", 100, 4, 1, 8),
            SetlistItem("Chorus", 120, 4, 2, 8,
                listOf(BeatAccent.ACCENT, BeatAccent.NORMAL, BeatAccent.SOFT, BeatAccent.MUTE)))
        val decoded = SetlistCodec.decode(SetlistCodec.encode(items))
        assertEquals(items, decoded)
    }

    @Test fun legacyFiveFieldRowHasNullPattern() {
        val decoded = SetlistCodec.decode("Intro|90|3|1|4")
        assertEquals(1, decoded.size)
        assertNull(decoded[0].pattern)
        assertEquals(90, decoded[0].bpm)
    }

    @Test fun invalidAccentNamesAreDropped() {
        val decoded = SetlistCodec.decode("X|100|4|1|4|ACCENT,BOGUS,NORMAL")
        assertEquals(listOf(BeatAccent.ACCENT, BeatAccent.NORMAL), decoded[0].pattern)
    }

    @Test fun separatorsInNamesAreSanitized() {
        val encoded = SetlistCodec.encode(listOf(SetlistItem("A|B;C", 100, 4, 1, 2)))
        assertEquals(1, SetlistCodec.decode(encoded).size)
        assertEquals("A B C", SetlistCodec.decode(encoded)[0].name)
    }

    @Test fun malformedRowsSkipped() {
        assertTrue(SetlistCodec.decode("garbage;;x|1|2").isEmpty())
    }
}
