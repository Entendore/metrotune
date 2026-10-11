package com.example.metrotune.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.ToneGenerator
import com.example.metrotune.data.DateKeys
import com.example.metrotune.data.PracticeRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Practice tracker facade. In-session accounting is in-memory; durable data
 * (day totals, sessions) lives in Room via PracticeRepository.
 * MetronomeEngine and MetronomeService reach this statically, so the repo is
 * obtained through a Hilt EntryPoint at init. Reads are synchronous against
 * a cached today-total; disk writes are batched every FLUSH_EVERY seconds.
 */
object PracticeSessionManager {

    data class State(
        val running: Boolean = false,
        val sessionSeconds: Long = 0,
        val todaySeconds: Long = 0,
        val goalSeconds: Long = 20 * 60
    )

    data class SessionEntry(val dateMs: Long, val seconds: Long, val source: String)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RepoEntryPoint {
        fun practiceRepository(): PracticeRepository
    }

    private const val FLUSH_EVERY = 15L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var repo: PracticeRepository? = null
    private var appContext: Context? = null
    @Volatile private var startedAtMs = 0L
    @Volatile private var elapsedBase = 0L
    @Volatile private var goalAnnounced = false
    @Volatile private var pendingSeconds = 0L
    @Volatile private var todayCache = 0L      // Room-side today total (excl. pending)
    @Volatile private var todayCacheKey = ""
    private var ticker: Job? = null

    var goalMinutes: Int = 20; private set
    var autoCountMetronome: Boolean = true; private set

    val isRunning: Boolean get() = startedAtMs != 0L
    val sessionSeconds: Long
        get() = elapsedBase + if (isRunning) (System.currentTimeMillis() - startedAtMs) / 1000 else 0L

    fun init(context: Context) {
        if (repo != null) return
        val app = context.applicationContext
        appContext = app
        val p = prefs()
        goalMinutes = p.getInt("goal_min", 20)
        autoCountMetronome = p.getBoolean("auto_meta", true)
        repo = EntryPointAccessors.fromApplication(app, RepoEntryPoint::class.java).practiceRepository()
        scope.launch {
            val key = DateKeys.today()
            todayCache = repo?.daySeconds(key) ?: 0L
            todayCacheKey = key
            emit()
        }
        emit()
    }

    private fun prefs(): SharedPreferences =
        (appContext ?: error("PracticeSessionManager not initialized"))
            .getSharedPreferences("practice", Context.MODE_PRIVATE)

    fun start() {
        if (repo == null || isRunning) return
        startedAtMs = System.currentTimeMillis()
        goalAnnounced = sessionSeconds >= goalMinutes * 60L
        emit()
        ticker = scope.launch { tickerLoop() }
    }

    fun pause() {
        if (repo == null || !isRunning) return
        val gained = (System.currentTimeMillis() - startedAtMs) / 1000
        startedAtMs = 0
        elapsedBase += gained
        addSeconds(gained)
        recordSession(gained, "Practice timer")
        ticker?.cancel(); ticker = null
        emit()
    }

    fun reset() {
        if (repo == null) return
        startedAtMs = 0
        elapsedBase = 0
        goalAnnounced = false
        ticker?.cancel(); ticker = null
        emit()
    }

    fun setGoal(minutes: Int) {
        goalMinutes = minutes
        goalAnnounced = sessionSeconds >= minutes * 60L
        prefs().edit().putInt("goal_min", minutes).apply()
        emit()
    }

    fun setAutoCount(enabled: Boolean) {
        autoCountMetronome = enabled
        prefs().edit().putBoolean("auto_meta", enabled).apply()
    }

    fun addSeconds(seconds: Long) {
        if (seconds <= 0) return
        pendingSeconds += seconds
        if (pendingSeconds >= FLUSH_EVERY) flush()
        emit()
    }

    fun secondsToday(): Long = todayCache + pendingSeconds

    private fun flush() {
        val s = pendingSeconds
        if (s <= 0L) return
        val r = repo ?: return
        pendingSeconds = 0
        val key = DateKeys.today()
        if (key == todayCacheKey) {
            todayCache += s
            scope.launch { r.addSeconds(key, s) }
        } else {
            // Midnight rollover mid-session: reload today's row from Room.
            scope.launch {
                r.addSeconds(key, s)
                todayCache = r.daySeconds(key)
                todayCacheKey = key
                emit()
            }
        }
    }

    suspend fun weekSummary(): Pair<Long, Int> {
        val r = repo ?: return 0L to 0
        val rows = r.daysSince(DateKeys.daysAgo(6))
        var total = rows.sumOf { it.seconds }
        var days = rows.count { it.seconds > 0 }
        val pending = pendingSeconds
        if (pending > 0) {
            total += pending
            val todayRow = rows.firstOrNull { it.dateKey == DateKeys.today() }
            if (todayRow == null || todayRow.seconds == 0L) days++
        }
        return total to days
    }

    suspend fun last7Days(): LongArray {
        val r = repo ?: return LongArray(7)
        val byKey = r.daysSince(DateKeys.daysAgo(6)).associateBy { it.dateKey }
        val out = LongArray(7)
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
        repeat(7) { i ->
            out[i] = byKey[DateKeys.format(cal)]?.seconds ?: 0L
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        out[6] += pendingSeconds
        return out
    }

    fun recordSession(seconds: Long, source: String) {
        if (seconds < 30) return
        val r = repo ?: return
        scope.launch { r.insertSession(System.currentTimeMillis(), seconds, source) }
    }

    suspend fun recentSessions(limit: Int = 60): List<SessionEntry> =
        repo?.recentSessions(limit)?.map { SessionEntry(it.dateMs, it.seconds, it.source) }
            ?: emptyList()

    private suspend fun tickerLoop() {
        while (isRunning) {
            delay(1000)
            if (!isRunning) return
            pendingSeconds += 1
            if (pendingSeconds >= FLUSH_EVERY) flush()
            if (!goalAnnounced && sessionSeconds >= goalMinutes * 60L) {
                goalAnnounced = true
                chime()
            }
            emit()
        }
    }

    private fun emit() {
        _state.value = State(
            running = isRunning,
            sessionSeconds = sessionSeconds,
            todaySeconds = secondsToday(),
            goalSeconds = goalMinutes * 60L
        )
    }

    private fun chime() {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tg.startTone(ToneGenerator.TONE_CDMA_CONFIRM, 400)
            scope.launch { delay(600); runCatching { tg.release() } }
        }
    }
}
