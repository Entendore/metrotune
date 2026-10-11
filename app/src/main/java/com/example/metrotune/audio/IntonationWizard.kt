package com.example.metrotune.audio

/**
 * Per-string setup sequencing: collect N confident frequency medians open,
 * then N fretted, then emit a Result. The VM feeds every confident tuner
 * frame; the UI renders state. Auto-advances across strings via nextString().
 * semitone = false → 12th-fret saddle measurement (mm offset);
 * semitone = true  → 1st-fret nut measurement (cents verdict only).
 */
class IntonationWizard(
    val scaleMm: Double,
    val samplesNeeded: Int = 12,
    private val semitone: Boolean = false
) {
    enum class Phase { OPEN, FRETTED, DONE }

    data class Result(val stringIndex: Int, val deviationCents: Double, val offsetMm: Double)
    data class State(val stringIndex: Int, val phase: Phase, val samples: Int, val last: Result?)

    private var stringIndex = 0
    private var phase = Phase.OPEN
    private val open = ArrayList<Double>(samplesNeeded)
    private val fret = ArrayList<Double>(samplesNeeded)
    var lastResult: Result? = null; private set

    val state: State
        get() = State(stringIndex, phase, if (phase == Phase.FRETTED) fret.size else open.size, lastResult)

    fun feed(hz: Double): State {
        if (phase == Phase.DONE) return state
        if (phase == Phase.OPEN) {
            open.add(hz)
            if (open.size >= samplesNeeded) { fret.clear(); phase = Phase.FRETTED }
        } else {
            fret.add(hz)
            if (fret.size >= samplesNeeded) {
                val dev = if (semitone)
                    IntonationMath.semitoneDeviationCents(median(open), median(fret))
                else
                    IntonationMath.deviationCents(median(open), median(fret))
                lastResult = Result(stringIndex, dev,
                    if (semitone) 0.0 else IntonationMath.offsetMm(scaleMm, dev))
                phase = Phase.DONE
            }
        }
        return state
    }

    fun nextString(): State {
        stringIndex++
        open.clear(); fret.clear()
        phase = Phase.OPEN
        return state
    }

    private fun median(xs: List<Double>): Double {
        val s = xs.sorted(); return s[s.size / 2]
    }
}
