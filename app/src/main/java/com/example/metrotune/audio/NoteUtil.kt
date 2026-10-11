package com.example.metrotune.audio

import kotlin.math.ln
import kotlin.math.pow

object NoteUtil {
    private val NAMES = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

    /** MIDI 69 = A4. */
    fun noteName(midi: Int): String {
        val pitchClass = ((midi % 12) + 12) % 12
        val octave = midi / 12 - 1
        return NAMES[pitchClass] + octave
    }

    fun frequency(midi: Double, a4: Double): Double = a4 * 2.0.pow((midi - 69.0) / 12.0)

    fun centsFrom(frequency: Double, target: Double): Double =
        1200.0 * (ln(frequency / target) / ln(2.0))
}
