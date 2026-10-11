package com.example.metrotune.audio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** User-created instruments, persisted as JSON in their own prefs file. */
object CustomInstruments {

    data class Custom(val id: String, val name: String, val strings: List<Int>)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("custom_instruments", Context.MODE_PRIVATE)

    fun all(context: Context): List<Custom> =
        prefs(context).all.entries.asSequence()
            .filter { it.key.startsWith("ci.") }
            .mapNotNull { e ->
                runCatching {
                    val o = JSONObject(e.value as? String ?: return@mapNotNull null)
                    val arr = o.getJSONArray("strings")
                    Custom(e.key.removePrefix("ci."), o.getString("name"),
                        (0 until arr.length()).map { arr.getInt(it) })
                }.getOrNull()
            }
            .sortedBy { it.name.lowercase() }
            .toList()

    /** Returns null if the name is blank or any note in the spec is invalid. */
    fun save(context: Context, name: String, stringSpec: String): Custom? {
        val n = name.trim()
        if (n.isEmpty()) return null
        val strings = InstrumentLibrary.parseStrings(stringSpec) ?: return null
        val id = "c_${System.currentTimeMillis()}"
        val o = JSONObject().put("name", n).put("strings", JSONArray(strings))
        prefs(context).edit().putString("ci.$id", o.toString()).apply()
        return Custom(id, n, strings)
    }

    fun delete(context: Context, id: String) {
        prefs(context).edit().remove("ci.$id").apply()
    }

    fun toPreset(c: Custom) =
        InstrumentPreset(c.id, c.name, c.strings, family = "My instruments")
}
