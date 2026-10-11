package com.example.metrotune.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** ISO date keys for the practice-days table. New formatter per call (thread-safe). */
object DateKeys {
    private const val PATTERN = "yyyy-MM-dd"

    fun format(cal: Calendar): String =
        SimpleDateFormat(PATTERN, Locale.US).format(cal.time)

    fun today(): String = format(Calendar.getInstance())

    fun daysAgo(n: Int): String =
        format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -n) })
}
