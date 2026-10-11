package com.example.metrotune

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.metrotune.audio.AudioFocusHelper
import com.example.metrotune.audio.BecomingNoisyWatcher
import com.example.metrotune.audio.MetronomeEngine
import com.example.metrotune.audio.MetronomeEngine.ClockSink
import com.example.metrotune.audio.MidiOut
import com.example.metrotune.audio.PracticeSessionManager
import com.example.metrotune.audio.Settings
import com.example.metrotune.audio.SetlistItem

/**
 * Foreground wrapper so the metronome survives screen-off and app-swipe.
 * Owns audio focus + becoming-noisy handling for playback, and the MIDI
 * clock sink when enabled.
 *
 * Deliberately NOT Hilt-injected: after a sticky-service restart in a fresh
 * process, this must run before any DI graph is guaranteed warm — it uses
 * the static facades (Settings / PracticeSessionManager / MetronomeEngine),
 * all of which are idempotent to init.
 */
class MetronomeService : Service(), MetronomeEngine.Listener {

    companion object {
        private const val CHANNEL_ID = "metronome_playback"
        private const val NOTIF_ID = 41
        const val ACTION_STOP = "com.example.metrotune.action.METRONOME_STOP"

        fun start(context: Context) {
            ensureChannel(context)
            ContextCompat.startForegroundService(
                context, Intent(context, MetronomeService::class.java))
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, MetronomeService::class.java).setAction(ACTION_STOP))
            }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(NotificationManager::class.java) ?: return
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID,
                        context.getString(R.string.notif_channel),
                        NotificationManager.IMPORTANCE_LOW))
            }
        }
    }

    private lateinit var focus: AudioFocusHelper
    private lateinit var noisy: BecomingNoisyWatcher
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        PracticeSessionManager.init(this)
        focus = AudioFocusHelper(this) { stopPlayback() }
        noisy = BecomingNoisyWatcher(applicationContext) { stopPlayback() }
        MetronomeEngine.addListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPlayback()
            return START_NOT_STICKY
        }
        if (intent == null) {
            // START_STICKY restart in a fresh process — restore what the
            // user was playing instead of snapping back to 120 BPM.
            MetronomeEngine.bpm = Settings.metroBpm
            MetronomeEngine.beatsPerBar = Settings.metroBeats
            MetronomeEngine.subdivision = Settings.metroSub
        }
        updateClockSink()
        if (!MetronomeEngine.isRunning) {
            if (!focus.request()) { stopSelf(); return START_NOT_STICKY }
            if (!MetronomeEngine.start()) {
                focus.abandon(); stopSelf(); return START_NOT_STICKY
            }
            noisy.register()
            acquireWakeLock()
        }
        startForegroundCompat()
        return START_STICKY
    }

    private fun updateClockSink() {
        if (Settings.metroMidiClock) {
            val server = MidiOut.get(applicationContext)
            runCatching { server.start() }   // fails gracefully without BT adapter/permission
            MetronomeEngine.clockSink = object : ClockSink {
                override fun tick() = server.sendClock()
                override fun start() = server.sendStart()
                override fun stop() = server.sendStop()
            }
        } else {
            MetronomeEngine.clockSink = null
        }
    }

    private fun notification(bpmNow: Int, detail: String? = null): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text, bpmNow) +
                (detail?.let { " · $it" } ?: ""))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .addAction(0, getString(R.string.action_stop),
                PendingIntent.getService(this, 1,
                    Intent(this, MetronomeService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE))
            .build()

    private fun startForegroundCompat() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notification(MetronomeEngine.liveBpm), type)
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MetroTune:metronome")
        }
        wakeLock?.acquire(4 * 60 * 60 * 1000L)   // 4 h safety cap
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    private fun stopPlayback() {
        releaseWakeLock()
        MetronomeEngine.stop()          // fires onStateChanged(false) → also cleans UI
        if (this::noisy.isInitialized) noisy.unregister()
        if (this::focus.isInitialized) focus.abandon()
        MetronomeEngine.clockSink = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---- engine observation (main thread) ----

    override fun onBeat(beat: Int, countIn: Boolean) = Unit

    override fun onStateChanged(running: Boolean) {
        WidgetBridge.update(this, running, MetronomeEngine.liveBpm)
        if (running) {
            getSystemService(NotificationManager::class.java)
                ?.notify(NOTIF_ID, notification(MetronomeEngine.liveBpm))
        } else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onTrainerTick(liveBpm: Int, muted: Boolean) {
        WidgetBridge.update(this, true, liveBpm)
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIF_ID, notification(liveBpm))
    }

    override fun onSetlistAdvance(index: Int, item: SetlistItem?, ended: Boolean) {
        WidgetBridge.update(this, !ended, item?.bpm ?: MetronomeEngine.liveBpm)
        if (item != null) {
            getSystemService(NotificationManager::class.java)
                ?.notify(NOTIF_ID, notification(MetronomeEngine.liveBpm, item.name))
        }
    }

    override fun onDestroy() {
        MetronomeEngine.removeListener(this)
        if (MetronomeEngine.isRunning) MetronomeEngine.stop()
        releaseWakeLock()
        if (this::noisy.isInitialized) noisy.unregister()
        if (this::focus.isInitialized) focus.abandon()
        MetronomeEngine.clockSink = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopPlayback()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
