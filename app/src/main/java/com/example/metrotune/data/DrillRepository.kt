package com.example.metrotune.data

import com.example.metrotune.audio.DrillRecords
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DrillRepository @Inject constructor(private val db: AppDatabase) {

    /** @return true when this drill set a new personal best. */
    suspend fun record(midi: Int, bestMs: Long, inTuneMs: Long, attempts: Int): Boolean {
        if (midi !in DrillRecords.MIN_MIDI..DrillRecords.MAX_MIDI || bestMs <= 0L) return false
        val prev = db.drillDao().byMidi(midi)?.toEntry()
        val (merged, newPb) = DrillRecords.merged(
            prev, bestMs, inTuneMs, attempts, System.currentTimeMillis())
        db.drillDao().upsert(merged.toEntity())
        return newPb
    }

    suspend fun best(midi: Int): Long = db.drillDao().byMidi(midi)?.bestMs ?: 0L

    suspend fun all(): List<DrillRecords.Entry> = db.drillDao().all().map { it.toEntry() }
}

private fun DrillRecordEntity.toEntry() =
    DrillRecords.Entry(midi, bestMs, inTuneMs, attempts, lastAtMs)

private fun DrillRecords.Entry.toEntity() =
    DrillRecordEntity(midi, bestMs, inTuneMs, attempts, lastAtMs)
