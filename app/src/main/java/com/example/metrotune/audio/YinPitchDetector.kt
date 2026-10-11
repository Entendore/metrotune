package com.example.metrotune.audio

import kotlin.math.abs

class PitchResult(val frequency: Double, val clarity: Double)

/**
 * YIN pitch detection — FFT-accelerated difference function.
 *
 * d(τ) = E0 + E(τ) − 2·r(τ), where r(τ) = Σ_{i<W} x[i]·x[i+τ] over a fixed
 * window W. r is obtained through the cross-correlation theorem
 * (2 forward FFTs + 1 inverse FFT, zero-padded to 2N) instead of the direct
 * O(N·τmax) sum — roughly 10× faster at 4096 samples, identical results.
 */
class YinPitchDetector(
    private val sampleRate: Int,
    private val minFrequency: Double = 27.5,
    private val maxFrequency: Double = 2100.0
) {
    private var tauMin = 2
    private var tauMax = 1
    private var windowSize = 0
    private lateinit var diff: FloatArray

    private lateinit var reX: FloatArray; private lateinit var imX: FloatArray  // full buffer
    private lateinit var reA: FloatArray; private lateinit var imA: FloatArray  // window [0, W)
    private lateinit var reC: FloatArray; private lateinit var imC: FloatArray  // correlation
    private lateinit var energy: FloatArray                                     // sliding E(τ)
    private var e0 = 0.0

    fun prepare(frameSize: Int) {
        tauMax = minOf(frameSize - 64, (sampleRate / minFrequency).toInt())
        tauMin = maxOf(2, (sampleRate / maxFrequency).toInt())
        windowSize = frameSize - tauMax - 1
        diff = FloatArray(tauMax + 2)
        energy = FloatArray(tauMax + 2)

        var l = 1
        while (l < frameSize + windowSize) l = l shl 1     // pad so circular corr. ≡ linear
        reX = FloatArray(l); imX = FloatArray(l)
        reA = FloatArray(l); imA = FloatArray(l)
        reC = FloatArray(l); imC = FloatArray(l)
    }

    fun detect(buffer: FloatArray, threshold: Float = 0.15f): PitchResult? {
        val w = windowSize
        val tLimit = tauMax + 1
        val n = buffer.size
        if (n < w + tLimit) return null

        // ---- difference function via FFT cross-correlation ----
        reX.fill(0f); imX.fill(0f); reA.fill(0f); imA.fill(0f)
        for (i in 0 until n) reX[i] = buffer[i]
        for (i in 0 until w) reA[i] = buffer[i]
        Fft.fft(reX, imX)
        Fft.fft(reA, imA)
        for (k in reC.indices) {
            // X · conj(A)  →  IFFT gives r(τ) for τ ≤ N − W
            reC[k] = reX[k] * reA[k] + imX[k] * imA[k]
            imC[k] = imX[k] * reA[k] - reX[k] * imA[k]
        }
        Fft.ifft(reC, imC)

        // ---- window energies: E0 constant, E(τ) as a running sum ----
        var e = 0.0
        for (i in 0 until w) e += buffer[i].toDouble() * buffer[i]
        e0 = e

        var prev = e
        for (tau in 1..tLimit) {
            val add = buffer[tau + w - 1].toDouble()
            val sub = buffer[tau - 1].toDouble()
            prev += add * add - sub * sub
            energy[tau] = prev.toFloat()
        }

        for (tau in 1..tLimit) {
            diff[tau] = (e0 + energy[tau] - 2.0 * reC[tau]).toFloat()
        }

        // ---- cumulative mean normalisation ----
        var running = 0f
        diff[0] = 1f
        for (tau in 1..tLimit) {
            running += diff[tau]
            diff[tau] = if (running > 0f) diff[tau] * tau / running else 1f
        }

        // ---- absolute threshold: first local minimum ----
        var tauEstimate = -1
        var tau = tauMin
        while (tau <= tauMax) {
            if (diff[tau] < threshold) {
                while (tau + 1 <= tauMax && diff[tau + 1] < diff[tau]) tau++
                tauEstimate = tau
                break
            }
            tau++
        }
        if (tauEstimate < 0) return null

        // ---- parabolic interpolation ----
        val x0 = diff[tauEstimate - 1]
        val x1 = diff[tauEstimate]
        val x2 = diff[tauEstimate + 1]
        val denom = 2f * (2f * x1 - x0 - x2)
        val betterTau = if (abs(denom) > 1e-9f)
            tauEstimate + (x2 - x0) / denom
        else
            tauEstimate.toFloat()

        val clarity = (1.0 - x1).coerceIn(0.0, 1.0)
        val freq = sampleRate / betterTau
        return if (freq in minFrequency..maxFrequency) PitchResult(freq, clarity) else null
    }
}
