package com.example.metrotune.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/** Continuous phase-accumulator tone (no wrap clicks), fades in/out. */
class ReferenceToneGenerator {

    companion object {
        const val SINE = 0
        const val TRIANGLE = 1
        const val SQUARE = 2
        private const val SR = 44100
    }

    @Volatile private var running = false
    @Volatile private var stopping = false
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    val isPlaying get() = running

    fun start(frequencies: List<Double>, volumeDb: Float, wave: Int) {
        stop()
        if (frequencies.isEmpty()) return
        val minBuf = AudioTrack.getMinBufferSize(
            SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(SR).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuf, SR))
            .build()
        track = t
        t.play()
        running = true
        stopping = false
        thread = Thread { writeLoop(t, frequencies, volumeDb, wave) }.apply { start() }
    }

    private fun writeLoop(t: AudioTrack, freqs: List<Double>, volumeDb: Float, wave: Int) {
        val amp = 10.0.pow(volumeDb / 20.0).coerceIn(0.0, 1.0)
        val phases = DoubleArray(freqs.size)
        val gains = DoubleArray(freqs.size) { if (it == 0) 1.0 else 0.45 }
        val buf = ShortArray(2048)
        var fade = 0.0
        val fadeIn = 1.0 / (SR * 0.02)
        val fadeOut = 1.0 / (SR * 0.05)

        while (running) {
            for (i in buf.indices) {
                var s = 0.0
                for (c in freqs.indices) {
                    val norm = phases[c] % 1.0
                    s += gains[c] * when (wave) {
                        TRIANGLE -> 4.0 * abs(norm - 0.5) - 1.0
                        SQUARE -> if (sin(2 * PI * phases[c]) >= 0) 0.8 else -0.8
                        else -> sin(2.0 * PI * phases[c])
                    }
                    phases[c] += freqs[c] / SR
                }
                s *= amp * fade
                if (stopping) {
                    fade -= fadeOut
                    if (fade <= 0.0) { running = false; break }
                } else if (fade < 1.0) {
                    fade = (fade + fadeIn).coerceAtMost(1.0)
                }
                buf[i] = (s * 32767.0 / freqs.size).toInt().toShort()
            }
            if (!running || t.write(buf, 0, buf.size) <= 0) break
        }
    }

    fun stop() {
        if (!running) { cleanup(); return }
        stopping = true
        thread?.join(800)
        thread = null
        cleanup()
    }

    private fun cleanup() {
        track?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        track = null
    }
}
