package com.example.metrotune.audio

/**
 * Tuning recognition from recently detected notes: a preset matches when its
 * string set contains the played notes. Requires ≥3 distinct notes and a
 * 75% match — scales/chromatic wandering never reach that bar.
 */
object TuningDetect {

    data class Match(val presetId: String, val name: String, val score: Double, val matched: Int)

    fun detect(notes: List<Int>, presets: List<InstrumentPreset>): List<Match> {
        val distinct = notes.distinct()
        if (distinct.size < 3) return emptyList()
        return presets.asSequence()
            .filter { it.strings.size in 3..12 }
            .map { p ->
                val m = distinct.count { it in p.strings }
                Match(p.id, p.name, m.toDouble() / distinct.size, m)
            }
            .filter { it.score >= 0.75 }
            .sortedWith(compareByDescending<Match> { it.score }.thenByDescending { it.matched })
            .take(3)
            .toList()
    }
}
