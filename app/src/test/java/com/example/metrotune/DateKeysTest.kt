package com.example.metrotune

import com.example.metrotune.data.DateKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DateKeysTest {

    @Test fun todayEqualsDaysAgoZero() {
        assertEquals(DateKeys.today(), DateKeys.daysAgo(0))
    }

    @Test fun isoKeysSortLexicographically() {
        // ISO yyyy-MM-dd — lexicographic order == chronological order (the
        // property the weekly aggregates rely on).
        for (back in 6 downTo 1) {
            assertTrue(DateKeys.daysAgo(back) < DateKeys.daysAgo(back - 1))
        }
    }

    @Test fun fixedDateFormatsCorrectly() {
        val cal = Calendar.getInstance().apply {
            clear()
            set(2024, Calendar.JANUARY, 31)
        }
        assertEquals("2024-01-31", DateKeys.format(cal))
    }
}
