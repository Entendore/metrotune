package com.example.metrotune.audio

/**
 * One setlist part: feel + length. bars = 0 means "play to end" — it absorbs
 * every remaining bar, and a list containing one has no fixed total.
 * pattern = null inherits the app's user pattern; a non-null list overrides
 * it for the duration of the part.
 */
data class SetlistItem(
    val name: String,
    val bpm: Int,
    val beatsPerBar: Int = 4,
    val subdivision: Int = 1,
    val bars: Int = 0,
    val pattern: List<MetronomeEngine.BeatAccent>? = null
)

/** Pure setlist scheduling — no Android dependencies, unit-tested. */
object SetlistMath {

    /** Total bars across the list; −1 when it contains an infinite (bars = 0) item. */
    fun totalBars(items: List<SetlistItem>): Int {
        if (items.any { it.bars <= 0 }) return -1
        return items.sumOf { it.bars }
    }

    /**
     * Absolute bar (0-based, spanning the whole setlist) → (itemIndex, barWithinItem).
     * Returns (−1, −1) when past the end and not looping. Loop wraps only
     * fully-finite lists; an infinite item never wraps (it already never ends).
     */
    fun locate(absBar: Int, items: List<SetlistItem>, loop: Boolean): Pair<Int, Int> {
        if (items.isEmpty()) return -1 to -1
        var b = absBar
        if (loop) {
            val total = totalBars(items)
            if (total > 0) b %= total
        }
        var remaining = b
        items.forEachIndexed { i, item ->
            if (item.bars <= 0) return i to remaining
            if (remaining < item.bars) return i to remaining
            remaining -= item.bars
        }
        return -1 to -1
    }
}
