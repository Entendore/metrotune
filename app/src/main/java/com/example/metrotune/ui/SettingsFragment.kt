package com.example.metrotune.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.metrotune.BuildConfig
import com.example.metrotune.DiagnosticsReport
import com.example.metrotune.R
import com.example.metrotune.SettingsSnapshot
import com.example.metrotune.audio.PracticeSessionManager
import com.example.metrotune.audio.Settings
import com.example.metrotune.data.AppDatabase
import com.google.android.material.materialswitch.MaterialSwitch
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    @Inject lateinit var db: AppDatabase

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@registerForActivityResult
            viewLifecycleOwner.lifecycleScope.launch {
                val ok = runCatching {
                    requireContext().contentResolver.openOutputStream(uri)
                        ?.bufferedWriter()?.use {
                            it.write(SettingsSnapshot.export(requireContext(), db))
                        }
                }.getOrDefault(null) != null
                Toast.makeText(requireContext(),
                    if (ok) R.string.export_done else R.string.export_failed,
                    Toast.LENGTH_SHORT).show()
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            viewLifecycleOwner.lifecycleScope.launch {
                val result = runCatching {
                    val text = requireContext().contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() } ?: error("empty")
                    SettingsSnapshot.import(requireContext(), text, db)
                }
                result.onSuccess { n ->
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.import_done)
                        .setMessage(getString(R.string.import_summary, n))
                        .setPositiveButton(R.string.ok, null)
                        .show()
                }.onFailure {
                    Toast.makeText(requireContext(), R.string.import_failed,
                        Toast.LENGTH_LONG).show()
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Settings.init(requireContext())
        PracticeSessionManager.init(requireContext())

        view.findViewById<MaterialSwitch>(R.id.switchKeepAwake).apply {
            isChecked = Settings.keepScreenOn
            setOnCheckedChangeListener { _, c -> Settings.keepScreenOn = c }
        }
        view.findViewById<MaterialSwitch>(R.id.switchHaptics).apply {
            isChecked = Settings.metroHaptics
            setOnCheckedChangeListener { _, c -> Settings.metroHaptics = c }
        }
        view.findViewById<MaterialSwitch>(R.id.switchAutoLog).apply {
            isChecked = PracticeSessionManager.autoCountMetronome
            setOnCheckedChangeListener { _, c -> PracticeSessionManager.setAutoCount(c) }
        }

        view.findViewById<View>(R.id.rowResetMic).setOnClickListener {
            Settings.micRationaleShown = false
            Toast.makeText(requireContext(), R.string.mic_rationale_reset,
                Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.rowOnboarding).setOnClickListener {
            Settings.onboardingDone = false
            OnboardingDialog().show(parentFragmentManager, "onboarding")
        }

        view.findViewById<View>(R.id.rowExport).setOnClickListener {
            exportLauncher.launch("metrotune-backup.json")
        }
        view.findViewById<View>(R.id.rowImport).setOnClickListener {
            importLauncher.launch(arrayOf("*/*"))
        }
        view.findViewById<View>(R.id.rowAbout).setOnClickListener { showAbout() }
    }

    private fun showAbout() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.about) + " · v" + BuildConfig.VERSION_NAME)
            .setMessage(DiagnosticsReport.build(requireContext()))
            .setPositiveButton(R.string.ok, null)
            .show()
    }
}
