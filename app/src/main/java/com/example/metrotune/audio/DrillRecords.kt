package com.example.metrotune.audio

/**
 * Pure long-tone record logic: merge + grading. Storage lives in Room
 * (DrillRepository).
 */
object DrillRecords {

    const val MIN_MIDI = 21
    const val MAX_MIDI = 108

    data class Entry(
        val midi: Int,
        val bestMs: Long,
        val inTuneMs: Long,
        val attempts: Int,
        val lastAtMs: Long
    )

    /** Merges a finished drill into the previous record; flag = new personal best. */
    fun merged(prev: Entry?, bestHoldMs: Long, inTuneMs: Long, attempts: Int, now: Long): Pair<Entry, Boolean> {
        val newPb = bestHoldMs > (prev?.bestMs ?: 0L) && bestHoldMs > 0
        val e = Entry(
            midi = prev?.midi ?: Int.MIN_VALUE,
            bestMs = maxOf(bestHoldMs, prev?.bestMs ?: 0L),
            inTuneMs = (prev?.inTuneMs ?: 0L) + inTuneMs,
            attempts = (prev?.attempts ?: 0) + attempts,
            lastAtMs = now
        )
        return e to newPb
    }

    /** ★0 < 5 s · ★1 5–15 s · ★2 15–30 s · ★3 ≥ 30 s sustained. */
    fun grade(bestMs: Long): Int = when {
        bestMs >= 30_000 -> 3
        bestMs >= 15_000 -> 2
        bestMs >= 5_000 -> 1
        else -> 0
    }
}
