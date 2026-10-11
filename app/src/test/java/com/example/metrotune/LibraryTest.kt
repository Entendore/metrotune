package com.example.metrotune

import com.example.metrotune.audio.InstrumentLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {

    @Test fun allStringPresetsParseAndIdsAreUnique() {
        val ids = InstrumentLibrary.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        InstrumentLibrary.ALL.forEach { p ->
            p.strings.forEach { midi ->
                assertTrue("preset ${p.id} bad midi $midi", midi in 21..108)
            }
        }
    }

    @Test fun sweetenedMatchesStringCount() {
        InstrumentLibrary.ALL.forEach { p ->
            if (p.sweetened.isNotEmpty())
                assertEquals("preset ${p.id}", p.strings.size, p.sweetened.size)
        }
    }

    @Test fun windOffsetsAreTransposable() {
        val valid = setOf(-24, -12, -7, -3, 0, 2, 5, 7, 9, 12, 14, 21, 26)
        InstrumentLibrary.ALL.forEach { p ->
            if (p.writtenOffset != null)
                assertTrue("preset ${p.id}", p.writtenOffset in valid)
        }
    }

    @Test fun spotCheckKeyPitches() {
        val g = InstrumentLibrary.byId("guitar_std")
        assertEquals(listOf(40, 45, 50, 55, 59, 64), g.strings)   // E2…E4
        assertEquals(23, InstrumentLibrary.byId("bass5").strings.first())  // B0
        assertEquals(88, InstrumentLibrary.byId("piano88").strings.size)
        assertEquals(2, InstrumentLibrary.byId("trumpet_bb").writtenOffset)
    }

    @Test fun everyPresetHasAFamily() {
        InstrumentLibrary.ALL.forEach { assertTrue(it.family.isNotBlank()) }
    }
}
