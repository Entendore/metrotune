package com.example.metrotune.audio

/**
 * Text encoding for setlists in prefs/backups:
 * "name|bpm|beats|sub|bars[|PATTERN];…" — the optional 6th field is a
 * comma-joined BeatAccent list. 5-field rows (pre-v6.0) decode with a null
 * pattern; unknown accent names are dropped, not fatal.
 */
object SetlistCodec {

    fun encode(items: List<SetlistItem>): String = items.joinToString(";") { item ->
        var s = "${sanitize(item.name)}|${item.bpm}|${item.beatsPerBar}|${item.subdivision}|${item.bars}"
        item.pattern?.takeIf { it.isNotEmpty() }?.let { p ->
            s += "|" + p.joinToString(",") { it.name }
        }
        s
    }

    fun decode(raw: String): List<SetlistItem> = raw.split(";").mapNotNull { part ->
        val f = part.split("|")
        if (f.size < 5) return@mapNotNull null
        val bpm = f[1].toIntOrNull() ?: return@mapNotNull null
        val beats = f[2].toIntOrNull() ?: return@mapNotNull null
        val sub = f[3].toIntOrNull() ?: return@mapNotNull null
        val bars = f[4].toIntOrNull() ?: return@mapNotNull null
        val pattern = f.getOrNull(5)?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.mapNotNull { a -> runCatching { MetronomeEngine.BeatAccent.valueOf(a.trim()) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
        SetlistItem(f[0], bpm, beats, sub, bars, pattern)
    }

    private fun sanitize(s: String) =
        s.replace('|', ' ').replace(';', ' ').trim()
}
