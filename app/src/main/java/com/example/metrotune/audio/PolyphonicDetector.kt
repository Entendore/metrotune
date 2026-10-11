package com.example.metrotune.audio

import kotlin.math.abs
import kotlin.math.ln

/**
 * Matched-filter polyphonic detection for instruments with known string
 * frequencies. For each target string the cached FFT spectrum is probed at
 * f0 / 2f0 / 3f0; a string "rings" when its fundamental clears the median
 * noise floor AND has harmonic support — or is so strong that support is
 * unnecessary (pure-sine sources: flute, reference tones).
 *
 * Two strings an octave apart can legitimately both fire (a strummed D chord
 * contains D2 and D3) — results are reported per string.
 */
class PolyphonicDetector(private val sampleRate: Int) {

    class StringReading(val stringIndex: Int, val cents: Double, val strength: Float)

    private val analyzer = SpectrumAnalyzer(sampleRate)

    fun prepare(frameSize: Int) = analyzer.prepare(frameSize)

    /** @param sensitivity 0 = strict … 1 = loose */
    fun detect(
        samples: FloatArray,
        targets: List<Double>,
        sensitivity: Float
    ): List<StringReading> {
        if (targets.isEmpty()) return emptyList()
        analyzer.run(samples)
        if (analyzer.binCount() == 0) return emptyList()

        val noise = analyzer.noiseFloor()
        val peak = analyzer.framePeak()
        // 12 (strict) … 4 (loose) × noise floor for the fundamental
        val factor = 12f - 8f * sensitivity.coerceIn(0f, 1f)

        val out = ArrayList<StringReading>(targets.size)
        for ((index, f) in targets.withIndex()) {
            if (f <= 0.0) continue
            val p1 = analyzer.probe(f)
            if (p1.magnitude <= 0f) continue
            val p2 = analyzer.probe(f * 2)
            val p3 = analyzer.probe(f * 3)

            val snr = p1.magnitude / noise
            val harmonicSupport =
                p2.magnitude > noise * factor * 0.5f || p3.magnitude > noise * factor * 0.4f
            val accepted = snr > factor && (harmonicSupport || snr > factor * 4f)
            if (!accepted) continue

            val cents = 1200.0 * ln(p1.frequency / f) / LN2
            if (abs(cents) >= 60.0) continue
            out.add(StringReading(index, cents, (p1.magnitude / peak).coerceIn(0f, 1f)))
        }
        return out
    }

    companion object { private val LN2 = kotlin.math.ln(2.0) }
}
