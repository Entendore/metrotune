package com.example.metrotune.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PracticeDao {

    @Upsert
    suspend fun upsertDay(day: PracticeDayEntity)

    @Query("SELECT * FROM practice_days WHERE dateKey = :key")
    suspend fun byKey(key: String): PracticeDayEntity?

    @Query("SELECT * FROM practice_days WHERE dateKey >= :fromKey ORDER BY dateKey")
    suspend fun daysSince(fromKey: String): List<PracticeDayEntity>

    @Query("SELECT * FROM practice_days ORDER BY dateKey")
    suspend fun allDays(): List<PracticeDayEntity>

    @Insert
    suspend fun insertSession(session: PracticeSessionEntity): Long

    @Query("SELECT * FROM practice_sessions ORDER BY dateMs DESC LIMIT :limit")
    suspend fun recentSessions(limit: Int): List<PracticeSessionEntity>

    @Query("SELECT * FROM practice_sessions ORDER BY dateMs")
    suspend fun allSessions(): List<PracticeSessionEntity>
}

@Dao
interface DrillDao {

    @Upsert
    suspend fun upsert(record: DrillRecordEntity)

    @Query("SELECT * FROM drill_records WHERE midi = :midi")
    suspend fun byMidi(midi: Int): DrillRecordEntity?

    @Query("SELECT * FROM drill_records ORDER BY midi")
    suspend fun all(): List<DrillRecordEntity>
}

@Dao
interface HealthDao {
    @Upsert
    suspend fun upsert(row: StringHealthEntity)

    @Query("SELECT * FROM string_health WHERE instrumentId = :id AND midi = :midi")
    suspend fun byKey(id: String, midi: Int): StringHealthEntity?

    @Query("SELECT * FROM string_health WHERE instrumentId = :id ORDER BY midi")
    suspend fun forInstrument(id: String): List<StringHealthEntity>

    @Query("SELECT * FROM string_health")
    suspend fun all(): List<StringHealthEntity>
}
