package com.example.metrotune.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Audio focus for the app's playback engines (metronome, reference tone).
 * Any focus loss stops playback — the correct behaviour for utility audio.
 */
class AudioFocusHelper(context: Context, private val onLost: () -> Unit) {

    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var request: AudioFocusRequest? = null

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onLost()
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setOnAudioFocusChangeListener(listener)
                .build()
        }
    }

    fun request(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            request?.let { am.requestAudioFocus(it) } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        else
            @Suppress("DEPRECATION")
            am.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandon() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            request?.let { am.abandonAudioFocusRequest(it) }
        else
            @Suppress("DEPRECATION") am.abandonAudioFocus(listener)
    }
}

/** Stops playback when headphones are unplugged or BT audio disconnects. */
class BecomingNoisyWatcher(private val context: Context, private val onStop: () -> Unit) {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = onStop()
    }
    private var registered = false

    fun register() {
        if (registered) return
        ContextCompat.registerReceiver(
            context, receiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
    }

    fun unregister() {
        if (!registered) return
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        registered = false
    }
}
