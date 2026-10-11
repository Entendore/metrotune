package com.example.metrotune

import android.content.Context
import com.example.metrotune.data.AppDatabase
import com.example.metrotune.data.PracticeDayEntity
import com.example.metrotune.data.PracticeSessionEntity
import com.example.metrotune.data.StringHealthEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Human-readable JSON backup of everything user-generated:
 * settings, practice data (Room), custom instruments, custom temperaments,
 * tuning curves, setlists, tempo presets. Values are type-tagged
 * ("s:", "i:", "l:", "b:", "f:", "ss:") because JSON cannot distinguish
 * Int/Long/Float — SharedPreferences restore needs the exact type.
 */
object SettingsSnapshot {

    private const val VERSION = 2
    private val FILES = listOf(
        "metrotune_settings", "practice", "custom_instruments",
        "custom_temps", "tuning_curves")

    suspend fun export(context: Context, db: AppDatabase): String {
        val root = JSONObject().put("app", "MetroTune").put("version", VERSION)
        val files = JSONObject()
        for (name in FILES) {
            val o = JSONObject()
            for ((k, v) in context.getSharedPreferences(name, Context.MODE_PRIVATE).all) {
                val enc = when (v) {
                    is String -> "s:" + v
                    is Int -> "i:$v"
                    is Long -> "l:$v"
                    is Boolean -> "b:$v"
                    is Float -> "f:$v"
                    is Set<*> -> "ss:" + v.joinToString("\u0001") { it.toString() }
                    else -> null
                }
                if (enc != null) o.put(k, enc)
            }
            files.put(name, o)
        }
        root.put("prefs", files)

        val data = JSONObject()
        val days = JSONArray()
        db.practiceDao().allDays().forEach {
            days.put(JSONObject().put("k", it.dateKey).put("s", it.seconds).put("n", it.note))
        }
        val sessions = JSONArray()
        db.practiceDao().allSessions().forEach {
            sessions.put(JSONObject().put("t", it.dateMs).put("s", it.seconds).put("src", it.source))
        }
        val drills = JSONArray()
        db.drillDao().all().forEach {
            drills.put(JSONObject().put("m", it.midi).put("b", it.bestMs)
                .put("t", it.inTuneMs).put("a", it.attempts).put("l", it.lastAtMs))
        }
        val healthArr = JSONArray()
        db.healthDao().all().forEach {
            healthArr.put(JSONObject().put("i", it.instrumentId).put("m", it.midi)
                .put("c", it.count).put("a", it.avgCents)
                .put("l", it.lastCents).put("t", it.lastAtMs))
        }
        root.put("data", data
            .put("days", days).put("sessions", sessions)
            .put("drills", drills).put("health", healthArr))
        return root.toString(2)
    }

    /** @return number of keys/rows restored (best-effort; unknown entries are skipped). */
    suspend fun import(context: Context, json: String, db: AppDatabase): Int {
        val root = JSONObject(json)
        if (root.optString("app") != "MetroTune")
            throw IllegalArgumentException("Not a MetroTune backup")
        var count = 0
        val files = root.getJSONObject("prefs")
        for (name in FILES) {
            if (!files.has(name)) continue
            val o = files.getJSONObject(name)
            val ed = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            for (k in o.keys()) {
                val raw = o.optString(k)
                val idx = raw.indexOf(':')
                if (idx <= 0) continue
                val body = raw.substring(idx + 1)
                when (raw.substring(0, idx)) {
                    "s" -> ed.putString(k, body)
                    "i" -> body.toIntOrNull()?.let { ed.putInt(k, it) }
                    "l" -> body.toLongOrNull()?.let { ed.putLong(k, it) }
                    "b" -> ed.putBoolean(k, body == "true")
                    "f" -> body.toFloatOrNull()?.let { ed.putFloat(k, it) }
                    "ss" -> ed.putStringSet(
                        k, body.split('\u0001').filter { it.isNotEmpty() }.toSet())
                }
                count++
            }
            ed.apply()
        }

        val data = root.optJSONObject("data")
        if (data != null) {
            data.optJSONArray("days")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.practiceDao().upsertDay(PracticeDayEntity(
                        o.getString("k"), o.getLong("s"), o.optString("n")))
                    count++
                }
            }
            data.optJSONArray("sessions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.practiceDao().insertSession(PracticeSessionEntity(
                        dateMs = o.getLong("t"), seconds = o.getLong("s"),
                        source = o.optString("src")))
                    count++
                }
            }
            data.optJSONArray("drills")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.drillDao().upsert(com.example.metrotune.data.DrillRecordEntity(
                        o.getInt("m"), o.getLong("b"), o.getLong("t"),
                        o.getInt("a"), o.getLong("l")))
                    count++
                }
            }
            data.optJSONArray("health")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.healthDao().upsert(StringHealthEntity(
                        o.getString("i"), o.getInt("m"), o.getInt("c"),
                        o.getDouble("a"), o.getDouble("l"), o.getLong("t")))
                    count++
                }
            }
        }
        return count
    }
}
