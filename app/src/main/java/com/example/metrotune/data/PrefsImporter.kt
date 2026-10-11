package com.example.metrotune.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time import of pre-Room data (v≤6.0 SharedPreferences) into Room.
 * Guarded by a flag in a dedicated "meta" prefs file; source keys are kept
 * (not deleted) so a rollback to an older build still sees its data.
 * Handles three shapes:
 *  - "practice" file:  day_<key> (Long), note_<key> (String), sessions (JSON)
 *  - "drill_records":  n.<midi> = {"b":…,"t":…,"a":…,"l":…}
 */
@Singleton
class PrefsImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    @ApplicationScope private val scope: CoroutineScope
) {
    fun runIfNeeded() {
        scope.launch {
            val meta = context.getSharedPreferences("meta", Context.MODE_PRIVATE)
            if (meta.getBoolean("room_imported_v1", false)) return@launch
            runCatching {
                val practice = context.getSharedPreferences("practice", Context.MODE_PRIVATE)
                val days = HashMap<String, PracticeDayEntity>()
                for ((k, v) in practice.all) when {
                    k.startsWith("day_") && v is Long ->
                        days[k.removePrefix("day_")] =
                            PracticeDayEntity(k.removePrefix("day_"), v)
                    k.startsWith("note_") && v is String -> {
                        val key = k.removePrefix("note_")
                        days[key] = (days[key] ?: PracticeDayEntity(key, 0)).copy(note = v)
                    }
                }
                days.values.forEach { db.practiceDao().upsertDay(it) }

                val sessions = runCatching {
                    JSONArray(practice.getString("sessions", "[]"))
                }.getOrDefault(JSONArray())
                for (i in 0 until sessions.length()) {
                    val o = sessions.getJSONObject(i)
                    db.practiceDao().insertSession(
                        PracticeSessionEntity(
                            dateMs = o.getLong("t"),
                            seconds = o.getLong("s"),
                            source = o.optString("src")))
                }

                val drills = context.getSharedPreferences("drill_records", Context.MODE_PRIVATE)
                for ((k, v) in drills.all) {
                    val midi = k.removePrefix("n.").toIntOrNull() ?: continue
                    val raw = v as? String ?: continue
                    val o = runCatching { JSONObject(raw) }.getOrNull() ?: continue
                    db.drillDao().upsert(DrillRecordEntity(
                        midi, o.optLong("b"), o.optLong("t"), o.optInt("a"), o.optLong("l")))
                }
            }
            meta.edit().putBoolean("room_imported_v1", true).apply()
        }
    }
}
