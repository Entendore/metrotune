package com.example.metrotune

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.example.metrotune.ui.MetronomeFragment
import com.example.metrotune.ui.OnboardingDialog
import com.example.metrotune.ui.PracticeTimerFragment
import com.example.metrotune.ui.SettingsFragment
import com.example.metrotune.ui.TunerFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TAB = "tab"
        private const val KEY_TAB = "selectedTab"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.updatePadding(top = bars.top)
            bottomNav.updatePadding(bottom = bars.bottom)
            insets
        }

        bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }

        // Tab ownership is explicit: restored state > shortcut > default.
        val startId = savedInstanceState?.getInt(KEY_TAB)
            ?: tabIdFor(intent?.getStringExtra(EXTRA_TAB)).takeIf { it != 0 }
            ?: R.id.nav_metronome
        bottomNav.selectedItemId = startId
        showTab(startId)

        if (savedInstanceState == null) {
            OnboardingDialog.showIfFirstRun(supportFragmentManager)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Warm-start shortcut (launchMode="singleTop")
        tabIdFor(intent?.getStringExtra(EXTRA_TAB)).takeIf { it != 0 }
            ?.let { findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId)
    }

    private fun tabIdFor(name: String?): Int = when (name) {
        "tuner" -> R.id.nav_tuner
        "metronome" -> R.id.nav_metronome
        else -> 0
    }

    private fun showTab(id: Int) {
        val fragment: Fragment = when (id) {
            R.id.nav_tuner -> TunerFragment()
            R.id.nav_practice -> PracticeTimerFragment()
            R.id.nav_settings -> SettingsFragment()
            else -> MetronomeFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }
}
