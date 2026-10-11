package com.example.metrotune.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.metrotune.audio.PracticeSessionManager
import com.example.metrotune.data.DateKeys
import com.example.metrotune.data.PracticeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single source of truth for the Practice tab: the manager's StateFlow is
 * folded into immutable UI state; week/chart/history reads (Room) run inside
 * collectLatest so a fresh emission supersedes an in-flight refresh.
 */
@HiltViewModel
class PracticeViewModel @Inject constructor(
    private val practice: PracticeRepository
) : ViewModel() {

    data class UiState(
        val running: Boolean = false,
        val sessionSeconds: Long = 0,
        val todaySeconds: Long = 0,
        val goalSeconds: Long = 1200,
        val weekTotal: Long = 0,
        val weekDays: Int = 0,
        val weekBars: List<Long> = List(7) { 0 },
        val sessions: List<PracticeSessionManager.SessionEntry> = emptyList(),
        val goalMinutes: Int = 20,
        val autoCount: Boolean = true
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

    init {
        viewModelScope.launch { _note.value = practice.note(DateKeys.today()) }
        viewModelScope.launch {
            PracticeSessionManager.state.collectLatest { s ->
                _ui.update {
                    it.copy(
                        running = s.running,
                        sessionSeconds = s.sessionSeconds,
                        todaySeconds = s.todaySeconds,
                        goalSeconds = s.goalSeconds,
                        goalMinutes = PracticeSessionManager.goalMinutes,
                        autoCount = PracticeSessionManager.autoCountMetronome
                    )
                }
                val week = PracticeSessionManager.weekSummary()
                val bars = PracticeSessionManager.last7Days()
                val sessions = PracticeSessionManager.recentSessions().take(7)
                _ui.update {
                    it.copy(weekTotal = week.first, weekDays = week.second,
                        weekBars = bars.toList(), sessions = sessions)
                }
            }
        }
    }

    fun toggle() = if (PracticeSessionManager.isRunning) PracticeSessionManager.pause()
                   else PracticeSessionManager.start()
    fun reset() = PracticeSessionManager.reset()
    fun setGoal(minutes: Int) = PracticeSessionManager.setGoal(minutes)
    fun setAutoCount(enabled: Boolean) = PracticeSessionManager.setAutoCount(enabled)

    fun saveNote(text: String) {
        viewModelScope.launch { practice.setNote(DateKeys.today(), text) }
        _note.value = text.trim()
    }
}
