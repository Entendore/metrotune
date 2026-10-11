package com.example.metrotune.audio

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * Multi-hypothesis note tracker. Each frame's detected frequency "votes":
 * the nearest hypothesis within tolerance is boosted (more for closer
 * matches and clearer frames), all others decay. A challenger must beat the
 * leader by [switchMargin] AND lead for [sustainFrames] consecutive frames
 * before it takes over — that kills boundary flicker while switching
 * decisively on real note changes. Frequency smoothing (EMA) lives inside
 * each hypothesis, so glides re-anchor instead of dragging the old note.
 */
class PitchTracker(
    private val toleranceCents: Double = 60.0,
    private val switchMargin: Double = 18.0,
    private val sustainFrames: Int = 3,
    private val matchBoost: Double = 26.0,
    private val decayPerFrame: Double = 0.88
) {
    data class Decision(val midi: Int, val frequency: Double, val noteChanged: Boolean)

    private class Hyp(val midi: Int) {
        var score = 0.0
        var leadFrames = 0
        var freqEma = -1.0
    }

    private val hyps = LinkedHashMap<Int, Hyp>()
    private var reportedMidi = Int.MIN_VALUE

    fun reset() {
        hyps.clear()
        reportedMidi = Int.MIN_VALUE
    }

    fun update(freq: Double, clarity: Double, a4: Double): Decision {
        if (freq <= 0.0) {
            for (h in hyps.values) { h.score *= decayPerFrame; h.leadFrames = 0 }
            return decisionFor(reportedMidi, freq)
        }

        var best: Hyp? = null
        var bestCents = Double.MAX_VALUE
        for (h in hyps.values) {
            val c = abs(NoteUtil.centsFrom(freq, midiFreq(h.midi, a4)))
            if (c < bestCents) { bestCents = c; best = h }
        }

        if (best == null || bestCents > toleranceCents) {
            val spawnMidi = midiOf(freq, a4)
            best = hyps.getOrPut(spawnMidi) { Hyp(spawnMidi) }
            best.score += 10.0 + 14.0 * clarity.coerceIn(0.0, 1.0)
        } else {
            best.score += matchBoost *
                (1.0 - bestCents / toleranceCents) *
                (0.4 + 0.6 * clarity.coerceIn(0.0, 1.0))
        }
        best.score = best.score.coerceIn(0.0, 100.0)
        best.freqEma = if (best.freqEma < 0) freq else best.freqEma * 0.6 + freq * 0.4

        for (h in hyps.values) if (h !== best) { h.score *= decayPerFrame; h.leadFrames = 0 }

        if (best.midi != reportedMidi) best.leadFrames++ else best.leadFrames = 0

        var changed = false
        if (reportedMidi == Int.MIN_VALUE) {
            reportedMidi = best.midi
            changed = true
        } else if (best.midi != reportedMidi) {
            val cur = hyps[reportedMidi]
            if (cur == null || (best.leadFrames >= sustainFrames &&
                    best.score > cur.score + switchMargin)
            ) {
                reportedMidi = best.midi
                changed = true
            }
        }

        prune()
        return decisionFor(reportedMidi, freq, changed)
    }

    private fun decisionFor(midi: Int, fallback: Double, changed: Boolean = false): Decision {
        val ema = hyps[midi]?.freqEma?.takeIf { it > 0 }
        return Decision(midi, ema ?: fallback, changed)
    }

    private fun prune() {
        hyps.values.removeAll { it.midi != reportedMidi && it.score < 8.0 }
        while (hyps.size > 6) {
            val victim = hyps.entries
                .filter { it.key != reportedMidi }
                .minByOrNull { it.value.score }?.key ?: break
            hyps.remove(victim)
        }
    }

    private fun midiOf(f: Double, a4: Double): Int =
        (69.0 + 12.0 * log2(f / a4)).roundToInt()

    private fun midiFreq(m: Int, a4: Double): Double =
        NoteUtil.frequency(m.toDouble(), a4)
}
