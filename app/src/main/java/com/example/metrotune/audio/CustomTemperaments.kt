package com.example.metrotune.audio

import android.content.Context
import java.util.Locale
import kotlin.math.abs

/** User-built temperaments: 12 cent offsets (deviations from ET), persisted. */
object CustomTemperaments {

    const val MAX_CENTS = 100.0

    data class Custom(val id: String, val name: String, val offsets: List<Double>)

    // ---- pure codec (tested) ----

    fun encode(offsets: List<Double>): String =
        sanitize(offsets).joinToString(",") { String.format(Locale.US, "%.1f", it) }

    fun decode(raw: String): List<Double>? {
        val parts = raw.split(",")
        if (parts.size != 12) return null
        val out = ArrayList<Double>(12)
        for (p in parts) {
            val v = p.trim().toDoubleOrNull() ?: return null
            if (!v.isFinite() || abs(v) > MAX_CENTS) return null
            out.add(v)
        }
        return out
    }

    fun sanitize(offsets: List<Double>): List<Double> =
        List(12) { i -> offsets.getOrElse(i) { 0.0 }.coerceIn(-MAX_CENTS, MAX_CENTS) }

    // ---- store ----

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("custom_temps", Context.MODE_PRIVATE)

    fun all(context: Context): List<Custom> =
        prefs(context).all.entries.asSequence()
            .filter { it.key.startsWith("ct.") }
            .mapNotNull { e ->
                val raw = (e.value as? String) ?: return@mapNotNull null
                val sep = raw.indexOf('|')
                if (sep <= 0) return@mapNotNull null
                val offsets = decode(raw.substring(sep + 1)) ?: return@mapNotNull null
                Custom(e.key.removePrefix("ct."), raw.substring(0, sep), offsets)
            }
            .sortedBy { it.name.lowercase() }
            .toList()

    /** @return null for a blank name. */
    fun save(context: Context, name: String, offsets: List<Double>): Custom? {
        val n = name.trim().replace('|', ' ')
        if (n.isEmpty()) return null
        val c = Custom("u_${System.currentTimeMillis()}", n, sanitize(offsets))
        prefs(context).edit().putString("ct.${c.id}", "${c.name}|${encode(c.offsets)}").apply()
        return c
    }

    fun delete(context: Context, id: String) { prefs(context).edit().remove("ct.$id").apply() }

    fun toTemperament(c: Custom) = Temperament(c.id, c.name, c.offsets.toDoubleArray())
}
