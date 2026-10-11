package com.example.metrotune.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.metrotune.R
import com.example.metrotune.audio.PracticeSessionManager
import com.example.metrotune.audio.Settings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class PracticeTimerFragment : Fragment(R.layout.fragment_practice) {

    private val vm: PracticeViewModel by viewModels()

    private lateinit var clockText: TextView
    private lateinit var todayText: TextView
    private lateinit var goalBar: ProgressBar
    private lateinit var goalText: TextView
    private lateinit var weekText: TextView
    private lateinit var weekChart: PracticeWeekView
    private lateinit var historyList: LinearLayout
    private lateinit var noteInput: EditText
    private lateinit var startPause: Button
    private lateinit var resetButton: Button
    private lateinit var goalSpin: Spinner
    private lateinit var autoCheck: CheckBox
    private lateinit var keepAwakeCheck: CheckBox

    private val goals = intArrayOf(5, 10, 15, 20, 30, 45, 60, 90, 120)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ThemePalette.init(requireContext())

        clockText = view.findViewById(R.id.sessionClock)
        todayText = view.findViewById(R.id.todayText)
        goalBar = view.findViewById(R.id.goalBar)
        goalText = view.findViewById(R.id.goalText)
        weekText = view.findViewById(R.id.weekText)
        weekChart = view.findViewById(R.id.weekChart)
        historyList = view.findViewById(R.id.historyList)
        noteInput = view.findViewById(R.id.noteInput)
        startPause = view.findViewById(R.id.startPause)
        resetButton = view.findViewById(R.id.resetButton)
        goalSpin = view.findViewById(R.id.spinGoal)
        autoCheck = view.findViewById(R.id.checkAuto)
        keepAwakeCheck = view.findViewById(R.id.checkKeepAwake)

        goalSpin.setup(goals.map { "$it min" }) { pos -> vm.setGoal(goals[pos]) }
        autoCheck.setOnCheckedChangeListener { _, c -> vm.setAutoCount(c) }
        keepAwakeCheck.isChecked = Settings.keepScreenOn
        keepAwakeCheck.setOnCheckedChangeListener { _, c -> Settings.keepScreenOn = c }

        startPause.setOnClickListener { vm.toggle() }
        resetButton.setOnClickListener { vm.reset() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.ui.collect { render(it) } }
                launch {
                    vm.note.collect {
                        if (noteInput.text.toString().trim() != it) noteInput.setText(it)
                    }
                }
            }
        }
    }

    private fun render(s: PracticeViewModel.UiState) {
        clockText.text = hms(s.sessionSeconds)
        todayText.text = getString(R.string.practice_today, hms(s.todaySeconds))
        goalBar.max = s.goalSeconds.toInt()
        goalBar.progress = s.todaySeconds.toInt().coerceAtMost(s.goalSeconds.toInt())
        goalText.text = getString(R.string.practice_goal_progress,
            hms(s.todaySeconds), hms(s.goalSeconds))
        weekText.text = getString(R.string.practice_week, hms(s.weekTotal), s.weekDays)
        weekChart.setData(s.weekBars.toLongArray(), dayLetters(), s.goalSeconds)

        val idx = goals.indexOf(s.goalMinutes).coerceAtLeast(0)
        if (goalSpin.selectedItemPosition != idx) goalSpin.setSelection(idx, false)
        if (autoCheck.isChecked != s.autoCount) autoCheck.isChecked = s.autoCount
        startPause.setText(if (s.running) R.string.pause else R.string.resume_start)
        renderHistory(s.sessions)
    }

    private fun dayLetters(): List<String> {
        val out = ArrayList<String>(7)
        val fmt = SimpleDateFormat("EEE", Locale.getDefault())
        for (back in 6 downTo 0) {
            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -back) }
            out.add(fmt.format(c.time))
        }
        return out
    }

    private fun renderHistory(sessions: List<PracticeSessionManager.SessionEntry>) {
        historyList.removeAllViews()
        if (sessions.isEmpty()) {
            historyList.addView(TextView(requireContext()).apply {
                text = getString(R.string.history_empty); alpha = 0.5f; textSize = 13f
            })
            return
        }
        val fmt = SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault())
        val d = resources.displayMetrics.density
        sessions.forEach { s ->
            historyList.addView(TextView(requireContext()).apply {
                text = "${fmt.format(Date(s.dateMs))} · ${hms(s.seconds)} · ${s.source}"
                textSize = 14f
                setPadding(0, (6 * d).toInt(), 0, 0)
            })
        }
    }

    private fun hms(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    override fun onPause() {
        super.onPause()
        vm.saveNote(noteInput.text.toString())
    }
}
