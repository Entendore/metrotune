package com.example.metrotune

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Pushes metronome state to the home-screen widget. */
object WidgetBridge {

    fun update(context: Context, running: Boolean, bpm: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_metronome).apply {
            setTextViewText(R.id.widgetBpm, bpm.toString())
            setTextViewText(
                R.id.widgetToggle,
                context.getString(if (running) R.string.stop else R.string.start))
            setOnClickPendingIntent(R.id.widgetToggle, togglePending(context))
            setOnClickPendingIntent(R.id.widgetRoot, openPending(context))
        }
        AppWidgetManager.getInstance(context).updateAppWidget(
            ComponentName(context, MetronomeWidgetProvider::class.java), views)
    }

    private fun togglePending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, MetronomeWidgetProvider::class.java)
            .setAction(MetronomeWidgetProvider.ACTION_TOGGLE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun openPending(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
