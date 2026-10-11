package com.example.metrotune.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per calendar day — practice seconds AND the user's day note. */
@Entity(tableName = "practice_days")
data class PracticeDayEntity(
    @PrimaryKey val dateKey: String,   // "yyyy-MM-dd"
    val seconds: Long,
    val note: String = ""
)

@Entity(tableName = "practice_sessions")
data class PracticeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMs: Long,
    val seconds: Long,
    val source: String
)

@Entity(tableName = "drill_records")
data class DrillRecordEntity(
    @PrimaryKey val midi: Int,
    val bestMs: Long,
    val inTuneMs: Long,
    val attempts: Int,
    val lastAtMs: Long
)

/** Per-instrument, per-pitch tuning history ("string health"). */
@Entity(tableName = "string_health", primaryKeys = ["instrumentId", "midi"])
data class StringHealthEntity(
    val instrumentId: String,
    val midi: Int,
    val count: Int,
    val avgCents: Double,
    val lastCents: Double,
    val lastAtMs: Long
)
