package com.example.metrotune

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.metrotune.audio.MetronomeController
import com.example.metrotune.audio.MetronomeEngine
import com.example.metrotune.audio.PracticeSessionManager
import com.example.metrotune.audio.Settings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MetronomeWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var controller: MetronomeController

    companion object {
        const val ACTION_TOGGLE = "com.example.metrotune.widget.TOGGLE"
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        Settings.init(context)
        // WidgetBridge updates every instance of this provider in one call.
        WidgetBridge.update(context, MetronomeEngine.isRunning, MetronomeEngine.liveBpm)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE) return
        Settings.init(context)
        PracticeSessionManager.init(context)   // engine stop credits practice time
        if (MetronomeEngine.isRunning) controller.stop()
        else if (!controller.start()) {
            // Some OEMs block service starts from background contexts.
            Toast.makeText(context, R.string.widget_start_failed, Toast.LENGTH_SHORT).show()
        }
        // State refresh arrives via the service's engine listener → WidgetBridge.
    }
}
