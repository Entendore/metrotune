package com.example.metrotune.audio

import kotlin.math.sqrt

/**
 * Rolling pitch-stability for one string: stddev + drift over a window.
 * stddev < 1.5¢ with enough samples = "settled". A string that keeps
 * drifting flat after being tuned up is the classic nut-friction signature —
 * surfaced in audit/session reports.
 */
class StringStability(
    private val windowMs: Long = 3000,
    private val minSamples: Int = 8
) {
    data class Snapshot(val samples: Int, val stddevCents: Double, val driftCents: Double, val settled: Boolean)

    private data class P(val t: Long, val c: Double)
    private val pts = ArrayDeque<P>()

    fun add(cents: Double, now: Long) {
        pts.addLast(P(now, cents))
        while (pts.isNotEmpty() && now - pts.first().t > windowMs) pts.removeFirst()
    }

    fun reset() = pts.clear()

    fun snapshot(): Snapshot {
        val n = pts.size
        if (n < 2) return Snapshot(n, 0.0, 0.0, false)
        val mean = pts.sumOf { it.c } / n
        val sd = sqrt(pts.sumOf { (it.c - mean) * (it.c - mean) } / n)
        val drift = pts.last().c - pts.first().c
        return Snapshot(n, sd, drift, n >= minSamples && sd < 1.5)
    }
}
