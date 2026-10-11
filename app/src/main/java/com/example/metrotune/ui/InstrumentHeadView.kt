package com.example.metrotune.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Instrument headstock diagram: one row per string with a tuning peg, the
 * string, and its note + live cents deviation. Green = within tolerance,
 * amber = out, gray = no reading. Tap a row to lock/unlock; long-press opens
 * the personal-offset editor (via onStringLongPress).
 */
class InstrumentHeadView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val d = resources.displayMetrics.density
    private val rowH = 40f * d

    private var names: List<String> = emptyList()
    private var deviations: List<Double?> = emptyList()
    private var active = -1
    private var locked = -1
    private var tolerance = 5.0

    var onStringTap: ((Int) -> Unit)? = null
    var onStringLongPress: ((Int) -> Unit)? = null

    private val activeBg = Paint().apply { color = 0x227C4DFF.toInt() }
    private val lockRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7C4DFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 2.5f * d
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * d
        textAlign = Paint.Align.RIGHT
    }

    private fun linePaint(color: Int, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        strokeWidth = (if (bold) 3.5f else 2f) * d
    }

    fun update(
        names: List<String>,
        deviations: List<Double?>,
        active: Int,
        locked: Int,
        tolerance: Double
    ) {
        this.names = names
        this.deviations = deviations
        this.active = active
        this.locked = locked
        this.tolerance = tolerance
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY)
            MeasureSpec.getSize(widthMeasureSpec) else (400 * d).toInt()
        val padV = (6 * d).toInt()
        setMeasuredDimension(w, (names.size * rowH).toInt() + padV * 2)
    }

    override fun onDraw(canvas: Canvas) {
        if (names.isEmpty()) return
        label.color = ThemePalette.label
        val w = width.toFloat()
        val top = 6f * d
        val labelX = w - 10f * d
        val stringEndX = labelX - 64f * d

        val idle = linePaint(ThemePalette.idle, false)
        val ok = linePaint(0xFF4CAF50.toInt(), true)
        val off = linePaint(0xFFFFB74D.toInt(), true)

        names.forEachIndexed { i, n ->
            val rowTop = top + i * rowH
            val cy = rowTop + rowH / 2f

            if (i == active) canvas.drawRect(0f, rowTop, w, rowTop + rowH, activeBg)

            val dev = deviations.getOrNull(i)
            val paint = when {
                dev == null -> idle
                abs(dev) <= tolerance -> ok
                else -> off
            }

            // Pegs: staggered two columns for 5+ strings, single column otherwise
            val pegX = if (names.size > 4) 16f * d + (i % 2) * 15f * d else 26f * d
            canvas.drawCircle(pegX, cy, 8f * d, paint)
            canvas.drawLine(pegX + 9f * d, cy, stringEndX, cy, paint)

            label.color = when {
                dev == null -> ThemePalette.label
                i == active || i == locked -> ThemePalette.needle
                abs(dev) <= tolerance -> 0xFF4CAF50.toInt()
                else -> 0xFFFFB74D.toInt()
            }
            val text = if (dev == null) n else "%s %+,.0f¢".format(n, dev)
            canvas.drawText(text, labelX, cy + 5f * d, label)

            if (i == locked) canvas.drawCircle(pegX, cy, 12f * d, lockRing)
        }
    }

    // ---------- touch: tap = lock/unlock, long-press = offset editor ----------

    private var touchIndex = -1
    private var longFired = false
    private val handler = Handler(Looper.getMainLooper())
    private val longRun = Runnable {
        if (touchIndex >= 0) { longFired = true; onStringLongPress?.invoke(touchIndex) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val i = ((event.y - 6f * d) / rowH).toInt()
                touchIndex = if (i in names.indices) i else -1
                longFired = false
                if (touchIndex >= 0) {
                    handler.postDelayed(longRun, ViewConfiguration.getLongPressTimeout().toLong())
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longRun)
                if (event.action == MotionEvent.ACTION_UP && touchIndex >= 0 && !longFired) {
                    performClick()
                    onStringTap?.invoke(touchIndex)
                }
                touchIndex = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()
}
