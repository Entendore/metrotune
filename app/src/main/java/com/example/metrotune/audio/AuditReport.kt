package com.example.metrotune.audio

import java.util.Locale
import kotlin.math.abs

/** Shareable CSV summaries: single audit, multi-instrument session, setup wizard. */
object AuditReport {

    data class AuditRow(val stringIndex: Int, val cents: Double, val stabilityCents: Double)
    data class SessionRow(val instrument: String, val stringNo: Int, val cents: Double, val stabilityCents: Double)
    data class SetupRow(val stringName: String, val deviationCents: Double, val offsetMm: Double)

    fun csv(instrumentName: String, a4: Double, rows: List<AuditRow>): String = buildString {
        appendLine("instrument,$instrumentName")
        appendLine("a4_hz,$a4")
        appendLine()
        appendLine("string,final_cents,stability_cents")
        rows.forEach {
            appendLine("${it.stringIndex + 1},${fmt(it.cents)},${fmt2(it.stabilityCents)}")
        }
        appendLine()
        appendLine("strings_over_5c,${rows.count { abs(it.cents) > 5.0 }}")
    }

    fun sessionCsv(rows: List<SessionRow>): String = buildString {
        appendLine("instrument,string,final_cents,stability_cents")
        rows.forEach {
            appendLine("${it.instrument.replace(',', ' ')},${it.stringNo},${fmt(it.cents)},${fmt2(it.stabilityCents)}")
        }
    }

    fun setupCsv(instrumentName: String, rows: List<SetupRow>): String = buildString {
        appendLine("instrument,$instrumentName")
        appendLine()
        appendLine("string,deviation_cents,saddle_offset_mm")
        rows.forEach {
            appendLine("${it.stringName.replace(',', ' ')},${fmt(it.deviationCents)},${fmt2(it.offsetMm)}")
        }
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%+.1f", v)
    private fun fmt2(v: Double) = String.format(Locale.US, "%.2f", v)
}
