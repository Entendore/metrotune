package com.example.metrotune.audio

import android.content.Context
import java.util.Locale
import kotlin.math.abs

/** Captured piano tuning curves: sparse note→cents maps, persisted. */
object TuningCurves {

    data class Curve(val id: String, val name: String, val points: Map<Int, Double>)

    // ---- pure codec (tested) ----

    fun encode(points: Map<Int, Double>): String =
        points.entries.sortedBy { it.key }
            .joinToString(",") { "${it.key}:${String.format(Locale.US, "%.2f", it.value)}" }

    fun decode(raw: String): Map<Int, Double>? {
        if (raw.isBlank()) return null
        val out = LinkedHashMap<Int, Double>()
        for (part in raw.split(",")) {
            val kv = part.split(":")
            if (kv.size != 2) return null
            val midi = kv[0].trim().toIntOrNull() ?: return null
            val cents = kv[1].trim().toDoubleOrNull() ?: return null
            if (midi !in 21..108 || !cents.isFinite() || abs(cents) > 100.0) return null
            out[midi] = cents
        }
        return out.ifEmpty { null }
    }

    // ---- store ----

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("tuning_curves", Context.MODE_PRIVATE)

    fun all(context: Context): List<Curve> =
        prefs(context).all.entries.asSequence()
            .filter { it.key.startsWith("tc.") }
            .mapNotNull { e ->
                val raw = (e.value as? String) ?: return@mapNotNull null
                val sep = raw.indexOf('|')
                if (sep <= 0) return@mapNotNull null
                val pts = decode(raw.substring(sep + 1)) ?: return@mapNotNull null
                Curve(e.key.removePrefix("tc."), raw.substring(0, sep), pts)
            }
            .sortedBy { it.name.lowercase() }
            .toList()

    fun byId(context: Context, id: String): Curve? = all(context).firstOrNull { it.id == id }

    fun save(context: Context, name: String, points: Map<Int, Double>): Curve? {
        val n = name.trim().replace('|', ' ')
        if (n.isEmpty() || points.isEmpty()) return null
        val c = Curve("k_${System.currentTimeMillis()}", n, points)
        prefs(context).edit().putString("tc.${c.id}", "${c.name}|${encode(c.points)}").apply()
        return c
    }

    fun delete(context: Context, id: String) { prefs(context).edit().remove("tc.$id").apply() }
}
