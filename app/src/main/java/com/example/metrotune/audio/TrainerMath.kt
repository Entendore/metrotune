package com.example.metrotune.audio

/**
 * Pure tempo-trainer state machine — no Android dependencies, so it runs in
 * plain JVM unit tests. (Kept out of MetronomeEngine because touching the
 * singleton would initialize its Handler on a JVM without Looper.)
 */
object TrainerMath {

    /** Returns (liveBpm, muted) for the given bar index. */
    fun stateFor(bar: Int, baseBpm: Int, cfg: MetronomeEngine.TrainerConfig): Pair<Int, Boolean> =
        when (cfg.mode) {
            MetronomeEngine.TrainerMode.RAMP -> {
                val steps = bar / cfg.rampEveryBars.coerceAtLeast(1)
                (baseBpm + steps * cfg.rampStepBpm).coerceAtMost(MetronomeEngine.MAX_BPM) to false
            }
            MetronomeEngine.TrainerMode.GAP -> {
                val cycle = (cfg.gapPlayBars + cfg.gapMuteBars).coerceAtLeast(1)
                baseBpm to (bar % cycle >= cfg.gapPlayBars)
            }
            MetronomeEngine.TrainerMode.OFF -> baseBpm to false
        }
}
