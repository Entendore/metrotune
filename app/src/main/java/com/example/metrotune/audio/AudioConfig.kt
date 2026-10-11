package com.example.metrotune.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord

/** Device audio capability probing. */
object AudioConfig {

    const val FALLBACK_RATE = 44100

    /**
     * Best capture sample rate: the device's preferred rate if the HAL
     * accepts it, otherwise 48 kHz / 44.1 kHz validated via getMinBufferSize.
     */
    fun bestSampleRate(context: Context): Int {
        val preferred = (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
            ?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull() ?: FALLBACK_RATE
        for (rate in listOf(preferred, 48000, 44100)) {
            if (rate < 8000) continue
            val buf = AudioRecord.getMinBufferSize(
                rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (buf > 0) return rate
        }
        return FALLBACK_RATE
    }
}
