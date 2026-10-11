package com.example.metrotune.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow

/**
 * Windowed FFT analysis feeding the visualizers and polyphonic detection.
 * Call run() once per frame; bandSpectrum(), probe(), noiseFloor() and
 * framePeak() then read the cached magnitudes — the poly detector can
 * interrogate the same frame without recomputing the FFT.
 */
class SpectrumAnalyzer(private val sampleRate: Int) {

    data class Peak(val magnitude: Float, val frequency: Double)

    companion object {
        const val BANDS = 128
        const val F_MIN = 27.5
        const val F_MAX = 8000.0
        const val DB_FLOOR = -80.0
    }

    private var frameSize = 0
    private lateinit var hann: FloatArray
    private lateinit var re: FloatArray
    private lateinit var im: FloatArray
    private lateinit var mag: FloatArray
    private var hasFrame = false

    fun prepare(size: Int) {
        frameSize = size
        hann = FloatArray(size) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / (size - 1))).toFloat() }
        re = FloatArray(size)
        im = FloatArray(size)
        mag = FloatArray(size / 2)
        hasFrame = false
    }

    fun run(samples: FloatArray) {
        val n = frameSize
        if (n == 0) return
        for (i in 0 until n) { re[i] = samples[i] * hann[i]; im[i] = 0f }
        Fft.fft(re, im)
        for (k in mag.indices) mag[k] = hypot(re[k].toDouble(), im[k].toDouble()).toFloat()
        hasFrame = true
    }

    fun binCount(): Int = if (hasFrame) mag.size else 0

    fun framePeak(): Float {
        if (!hasFrame) return 0f
        var m = 0f
        for (v in mag) if (v > m) m = v
        return m
    }

    /** Median magnitude (skips DC) — the noise-floor reference for detectors. */
    fun noiseFloor(): Float {
        if (!hasFrame) return 0f
        val sorted = mag.copyOf().also { it.sort(1, it.size) }
        return sorted[sorted.size / 2].coerceAtLeast(1e-9f)
    }

    /** BANDS log-frequency dB values relative to the frame peak. */
    fun bandSpectrum(): FloatArray {
        val out = FloatArray(BANDS)
        if (!hasFrame) return out
        val peak = framePeak().coerceAtLeast(1e-9f)
        val binHz = sampleRate.toDouble() / frameSize
        val ratio = F_MAX / F_MIN
        val bins = mag.size
        for (b in 0 until BANDS) {
            val f0 = F_MIN * ratio.pow(b.toDouble() / BANDS)
            val f1 = F_MIN * ratio.pow((b + 1).toDouble() / BANDS)
            var k = (f0 / binHz).toInt().coerceIn(1, bins - 1)
            val kEnd = (f1 / binHz).toInt().coerceIn(k, bins - 1)
            var p = 0f
            while (k <= kEnd) { if (mag[k] > p) p = mag[k]; k++ }
            out[b] = (20.0 * log10((p / peak).toDouble()))
                .toFloat().coerceAtLeast(DB_FLOOR.toFloat())
        }
        return out
    }

    /** Interpolated (magnitude, frequency) of the strongest bin near freq (±2 bins). */
    fun probe(freq: Double): Peak {
        if (!hasFrame || frameSize == 0) return Peak(0f, freq)
        val binHz = sampleRate.toDouble() / frameSize
        val center = (freq / binHz).toInt()
        if (center < 2 || center >= mag.size - 2) return Peak(0f, freq)
        var best = center
        for (k in center - 2..center + 2) if (mag[k] > mag[best]) best = k
        val y0 = mag[best - 1]; val y1 = mag[best]; val y2 = mag[best + 1]
        val d = y0 - 2f * y1 + y2
        val delta = if (abs(d) > 1e-9f) (0.5f * (y0 - y2) / d).coerceIn(-1f, 1f) else 0f
        return Peak(y1, (best + delta) * binHz)
    }
}
