package com.example.metrotune.audio

/** Pure polyrhythm scheduling: merged hit events for one cycle. */
object PolyMath {

    data class Event(val time: Double, val voice: Int, val index: Int)

    /**
     * Hit times for voice A (polyVoice hits, index = hit number) and voice B
     * across one cycle of length cycleLen. Sorted by time; A sorts before B
     * at identical times so the accented downbeat always sounds first.
     * b = 0 yields an A-only cycle (used for poly count-in).
     */
    fun events(a: Int, b: Int, cycleLen: Double): List<Event> {
        val ev = ArrayList<Event>(a + b)
        for (j in 0 until a) ev.add(Event(j * cycleLen / a, 0, j))
        for (k in 0 until b) ev.add(Event(k * cycleLen / b, 1, k))
        return ev.sortedWith(compareBy({ it.time }, { it.voice }))
    }
}
