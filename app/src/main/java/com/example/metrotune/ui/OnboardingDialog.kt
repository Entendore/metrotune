package com.example.metrotune.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.example.metrotune.R
import com.example.metrotune.audio.Settings

/** Three-card first-run tour. Flag flips on Skip OR on finishing the last card. */
class OnboardingDialog : DialogFragment() {

    private var page = 0
    private var positiveBtn: Button? = null
    private lateinit var emoji: TextView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var dots: LinearLayout

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        page = savedInstanceState?.getInt("page") ?: 0
        val d = requireContext().resources.displayMetrics.density
        emoji = TextView(requireContext()).apply { textSize = 44f; gravity = Gravity.CENTER }
        title = TextView(requireContext()).apply {
            textSize = 20f; gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        body = TextView(requireContext()).apply {
            textSize = 15f; gravity = Gravity.CENTER; alpha = 0.8f
            setPadding(0, (12 * d).toInt(), 0, 0)
        }
        dots = LinearLayout(requireContext()).apply {
            gravity = Gravity.CENTER
            setPadding(0, (20 * d).toInt(), 0, 0)
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((28 * d).toInt(), (24 * d).toInt(), (28 * d).toInt(), (8 * d).toInt())
            addView(emoji); addView(title); addView(body); addView(dots)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(content)
            .setPositiveButton(R.string.onboarding_next, null)
            .setNegativeButton(R.string.onboarding_skip, null)
            .create()
        dialog.setOnShowListener {
            positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                setOnClickListener {
                    if (page < 2) { page++; renderPage() } else finish()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { finish() }
        }
        renderPage()
        return dialog
    }

    private fun renderPage() {
        val (icon, t, b) = when (page) {
            0 -> Triple("⏱", R.string.ob1_title, R.string.ob1_body)
            1 -> Triple("🎯", R.string.ob2_title, R.string.ob2_body)
            else -> Triple("📈", R.string.ob3_title, R.string.ob3_body)
        }
        emoji.text = icon
        title.setText(t)
        body.setText(b)
        positiveBtn?.setText(
            if (page == 2) R.string.onboarding_start else R.string.onboarding_next)
        val d = resources.displayMetrics.density
        dots.removeAllViews()
        repeat(3) { i ->
            dots.addView(View(requireContext()).apply {
                setBackgroundResource(
                    if (i == page) R.drawable.chip_bg_selected else R.drawable.chip_bg)
            }, LinearLayout.LayoutParams((10 * d).toInt(), (10 * d).toInt()).apply {
                marginEnd = (8 * d).toInt()
            })
        }
    }

    private fun finish() {
        Settings.onboardingDone = true
        dismiss()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("page", page)
    }

    companion object {
        fun showIfFirstRun(fm: FragmentManager) {
            if (!Settings.onboardingDone) OnboardingDialog().show(fm, "onboarding")
        }
    }
}
