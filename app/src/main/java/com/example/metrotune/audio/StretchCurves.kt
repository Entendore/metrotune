package com.example.metrotune.audio

import kotlin.math.exp

/**
 * Railsback-style piano stretch approximation (cents relative to 12-TET).
 * Real pianos are tuned with stretched octaves: bass pulled flat, treble
 * pushed sharp, driven by string inharmonicity. This analytic curve matches
 * the classic Railsback measurements to within a few cents at the extremes.
 */
object StretchCurves {
    fun railsbackCents(midi: Int): Double {
        val x = midi.toDouble()
        val bass = -28.0 * exp(-(x - 21.0) / 14.0)     // A0 region → ≈ −28¢
        val treble = 30.0 * exp(-(108.0 - x) / 10.0)   // C8 region → ≈ +30¢
        return bass + treble
    }

    /**
     * Cents offset at midi from a sparse captured curve (note → cents).
     * Linear between neighboring captured points; clamped at the ends;
     * falls back to Railsback for an empty map — so captured curves and the
     * analytic default are interchangeable everywhere.
     */
    fun interpolate(points: Map<Int, Double>, midi: Int): Double {
        if (points.isEmpty()) return railsbackCents(midi)
        points[midi]?.let { return it }
        val lower = points.keys.filter { it < midi }.maxOrNull()
        val upper = points.keys.filter { it > midi }.minOrNull()
        return when {
            lower != null && upper != null -> {
                val t = (midi - lower).toDouble() / (upper - lower)
                points[lower]!! * (1 - t) + points[upper]!! * t
            }
            lower != null -> points[lower]!!
            upper != null -> points[upper]!!
            else -> points.values.first()
        }
    }
}
