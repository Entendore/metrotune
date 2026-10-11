package com.example.metrotune.data

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthRepository @Inject constructor(private val db: AppDatabase) {

    /** Running-mean update; cheap enough to call at every confirmed string. */
    suspend fun record(instrumentId: String, midi: Int, cents: Double) {
        val prev = db.healthDao().byKey(instrumentId, midi)
        val count = (prev?.count ?: 0) + 1
        val avg = ((prev?.avgCents ?: 0.0) * (count - 1) + cents) / count
        db.healthDao().upsert(StringHealthEntity(
            instrumentId, midi, count, avg, cents, System.currentTimeMillis()))
    }

    /**
     * Flag: chronically off (avg beyond ±5¢) or deviating from its own
     * average this visit (>4¢) — "this piano runs sharp" and "this string
     * didn't settle like it usually does".
     */
    fun flagged(row: StringHealthEntity): Boolean =
        kotlin.math.abs(row.avgCents) > 5.0 ||
            kotlin.math.abs(row.lastCents - row.avgCents) > 4.0

    suspend fun forInstrument(id: String): List<StringHealthEntity> =
        db.healthDao().forInstrument(id)

    suspend fun all(): List<StringHealthEntity> = db.healthDao().all()
}
