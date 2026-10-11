package com.example.metrotune

import android.app.Application
import android.os.StrictMode
import com.example.metrotune.audio.PracticeSessionManager
import com.example.metrotune.audio.Settings
import com.example.metrotune.data.PrefsImporter
import com.example.metrotune.ui.ThemePalette
import com.google.android.material.color.DynamicColors
import javax.inject.Inject

@HiltAndroidApp
class MetroTuneApp : Application() {

    @Inject lateinit var importer: PrefsImporter

    override fun onCreate() {
        super.onCreate()   // Hilt injection happens before this returns
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().penaltyLog().build())
        }
        Settings.init(this)
        ThemePalette.init(this)
        PracticeSessionManager.init(this)   // static facade — bridges to Hilt via @EntryPoint
        DynamicColors.applyToActivitiesIfAvailable(this)
        importer.runIfNeeded()              // one-time prefs → Room migration
    }
}
