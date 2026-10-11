package com.example.metrotune.audio

import kotlin.math.abs

/**
 * Beginner-friendly tuning guidance: direction + magnitude from a cents
 * deviation. The arrow direction mirrors the needle convention (left = flat,
 * right = sharp, matching the −50¢/+50¢ labels); magnitude grows with the
 * distance beyond tolerance in 10¢ steps.
 */
object TuneGuidance {

    enum class State { IN_TUNE, FLAT, SHARP }

    data class Guidance(val state: State, val level: Int)   // level 1–5, 0 when in tune

    fun of(cents: Double, tolerance: Double): Guidance {
        val a = abs(cents)
        if (a <= tolerance) return Guidance(State.IN_TUNE, 0)
        val level = (((a - tolerance) / 10.0).toInt() + 1).coerceIn(1, 5)
        return Guidance(if (cents < 0) State.FLAT else State.SHARP, level)
    }
}
