package com.example.metrotune

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.os.Build
import com.example.metrotune.audio.AudioConfig

object DiagnosticsReport {

    fun version(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }

    fun build(context: Context): String = buildString {
        appendLine("MetroTune ${version(context)}")
        appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
        val rate = AudioConfig.bestSampleRate(context)
        val inBuf = AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val outBuf = AudioTrack.getMinBufferSize(
            44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        appendLine("capture rate: $rate Hz (min buffer $inBuf frames)")
        appendLine("output min buffer: $outBuf frames")
    }
}
