package com.example.metrotune.audio

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.metrotune.MetronomeService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Single start/stop path shared by the fragment VM and the home-screen widget. */
@Singleton
class MetronomeController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun start(): Boolean = runCatching {
        ContextCompat.startForegroundService(
            context, Intent(context, MetronomeService::class.java))
        true
    }.getOrDefault(false)

    fun stop(): Boolean = runCatching {
        context.startService(
            Intent(context, MetronomeService::class.java)
                .setAction(MetronomeService.ACTION_STOP))
        true
    }.getOrDefault(false)
}
