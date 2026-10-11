package com.example.metrotune.data

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PracticeRepository @Inject constructor(private val db: AppDatabase) {

    private val dao get() = db.practiceDao()

    suspend fun day(key: String): PracticeDayEntity? = dao.byKey(key)

    suspend fun daySeconds(key: String): Long = dao.byKey(key)?.seconds ?: 0L

    suspend fun daysSince(fromKey: String): List<PracticeDayEntity> = dao.daysSince(fromKey)

    /** Atomic increment-or-create (transaction — safe against concurrent flushes). */
    suspend fun addSeconds(key: String, seconds: Long) = db.withTransaction {
        val row = dao.byKey(key)
        dao.upsertDay(
            (row ?: PracticeDayEntity(key, 0)).copy(seconds = (row?.seconds ?: 0) + seconds)
        )
    }

    suspend fun setNote(key: String, text: String) {
        val row = dao.byKey(key) ?: PracticeDayEntity(key, 0)
        dao.upsertDay(row.copy(note = text))
    }

    suspend fun note(key: String): String = dao.byKey(key)?.note.orEmpty()

    suspend fun insertSession(dateMs: Long, seconds: Long, source: String) {
        dao.insertSession(PracticeSessionEntity(dateMs = dateMs, seconds = seconds, source = source))
    }

    suspend fun recentSessions(limit: Int = 60): List<PracticeSessionEntity> = dao.recentSessions(limit)
}
