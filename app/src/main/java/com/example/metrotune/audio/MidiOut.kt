package com.example.metrotune.audio

import android.content.Context

/** Single shared BLE-MIDI server instance (one GATT server per app process). */
object MidiOut {
    @Volatile private var server: BleMidiServer? = null

    fun get(context: Context): BleMidiServer =
        server ?: synchronized(this) {
            server ?: BleMidiServer(context.applicationContext).also { server = it }
        }
}
