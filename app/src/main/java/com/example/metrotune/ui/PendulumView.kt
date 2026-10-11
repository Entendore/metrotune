package com.example.metrotune.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos

/**
 * Swinging pendulum, phase-locked to the metronome: one beat = one half-swing.
 * The fragment resyncs the anchor at each onBeat (audio-clock moments), so the
 * bob is at an extreme exactly when the click sounds — and it follows
 * ramp-tempo changes because the fragment updates the period live.
 */
class PendulumView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var running = false
    private var periodMs = 500.0
    private var anchorMs = 0L
    private var flip = false          // alternates the starting side each beat

    private val rod = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 4f }
    private val bob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7C4DFF.toInt() }
    private val pivot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF78909C.toInt() }
    private val stand = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3f }

    fun setRunning(r: Boolean) {
        running = r
        if (r) { anchorMs = SystemClock.elapsedRealtime(); postInvalidateOnAnimation() }
        else invalidate()
    }

    fun setPeriod(beatsPerMinute: Int) {
        periodMs = 60000.0 / beatsPerMinute.coerceAtLeast(1)
    }

    /** Called at each audio-clock beat moment: resync + alternate side. */
    fun onBeat() {
        anchorMs = SystemClock.elapsedRealtime()
        flip = !flip
    }

    override fun onDraw(canvas: Canvas) {
        rod.color = ThemePalette.rod
        stand.color = ThemePalette.grid
        val w = width.toFloat()
        val h = height.toFloat()
        val pivotY = h * 0.12f
        val rodLen = h * 0.74f

        canvas.drawLine(w / 2f - 14f, pivotY, w / 2f + 14f, pivotY, stand)
        canvas.drawCircle(w / 2f, pivotY, 6f, pivot)

        val maxDeg = 28f
        val angle = if (running) {
            val phase = (SystemClock.elapsedRealtime() - anchorMs) / periodMs
            maxDeg * cos(Math.PI * phase).toFloat() * (if (flip) -1f else 1f)
        } else 0f

        canvas.save()
        canvas.rotate(angle, w / 2f, pivotY)
        canvas.drawLine(w / 2f, pivotY, w / 2f, pivotY + rodLen, rod)
        canvas.drawCircle(w / 2f, pivotY + rodLen, 16f, bob)
        canvas.restore()

        if (running) postInvalidateOnAnimation()
    }
}
