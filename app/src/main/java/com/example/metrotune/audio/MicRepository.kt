package com.example.metrotune.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

object MicRepository {

    fun list(context: Context): List<AudioDeviceInfo> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.isSource && it.type != AudioDeviceInfo.TYPE_TELEPHONY }
    }

    fun label(d: AudioDeviceInfo): String {
        val type = when (d.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in mic"
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth mic"
            else -> "Input ${d.type}"
        }
        val name = d.productName?.toString()?.takeIf { it.isNotBlank() } ?: "—"
        return "$type · $name"
    }
}
