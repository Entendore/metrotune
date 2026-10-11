package com.example.metrotune.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/** Scrolling strobe band. Scrolls right when sharp, left when flat, static when locked. */
class StrobeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var cents = 0f            // ±50
    var active = false        // signal present
    var locked = false        // |cents| < 1 and confidence high

    private var offset = 0f
    private var lastFrame = 0L

    private val bg = Paint()
    private val stripe = Paint(Paint.ANTI_ALIAS_FLAG)

    private val stripeW = 14f * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        bg.color = ThemePalette.surface
        val now = System.nanoTime()
        if (lastFrame != 0L) {
            val dt = ((now - lastFrame) / 1e9f).coerceIn(0f, 0.1f)
            offset += (cents * 60f).coerceIn(-1200f, 1200f) * dt   // px/s
        }
        lastFrame = now

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
        stripe.color = if (locked) 0xFF43A047.toInt() else ThemePalette.stripe
        stripe.alpha = if (active) 255 else 60

        val period = stripeW * 2
        var x = offset % period
        if (x > 0) x -= period
        while (x < width) {
            canvas.drawRect(x, 0f, x + stripeW, height.toFloat(), stripe)
            x += period
        }
        if (active) postInvalidateOnAnimation() else lastFrame = 0L
    }
}

/** Horizontal dBFS meter with a noise-gate tick. */
class LevelMeterView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var levelDb = -100f
    var gateDb = -46f

    private val track = Paint()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gate = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF8A65.toInt(); strokeWidth = 3f
    }

    fun setLevel(db: Float) { levelDb = db.coerceIn(-60f, 0f); invalidate() }

    override fun onDraw(canvas: Canvas) {
        track.color = ThemePalette.track
        val h = height.toFloat()
        val r = h / 2f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), h, r, r, track)

        val aboveGate = levelDb > gateDb
        fill.color = if (aboveGate) 0xFF7C4DFF.toInt() else ThemePalette.idle
        val w = width * ((levelDb + 60f) / 60f).coerceIn(0f, 1f)
        if (w > r) canvas.drawRoundRect(0f, 0f, w, h, r, r, fill)

        val gx = width * ((gateDb + 60f) / 60f).coerceIn(0f, 1f)
        canvas.drawLine(gx, 0f, gx, h, gate)
    }
}

/** Cents-over-time graph (±50¢), last 8 seconds, newest at the right edge. */
class PitchGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val WINDOW_MS = 8000L
    private val points = ArrayDeque<Pair<Long, Float>>()

    private val band = Paint().apply { color = 0x334CAF50 }
    private val zero = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7C4DFF.toInt(); strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
    }
    private val path = Path()

    fun add(cents: Float) {
        val now = System.currentTimeMillis()
        points.addLast(now to cents)
        while (points.isNotEmpty() && now - points.first().first > WINDOW_MS) points.removeFirst()
        invalidate()
    }

    fun clear() { points.clear(); invalidate() }

    override fun onDraw(canvas: Canvas) {
        zero.color = ThemePalette.grid
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 6f
        val now = System.currentTimeMillis()

        val bandHalf = (h / 2 - pad) * (5f / 50f)
        canvas.drawRect(0f, h / 2 - bandHalf, w, h / 2 + bandHalf, band)
        canvas.drawLine(0f, h / 2, w, h / 2, zero)

        if (points.size < 2) return
        path.reset()
        var first = true
        for ((t, c) in points) {
            val x = w - (now - t) / WINDOW_MS.toFloat() * w
            val y = h / 2 - (c / 50f) * (h / 2 - pad)
            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
        }
        canvas.drawPath(path, line)
    }
}
