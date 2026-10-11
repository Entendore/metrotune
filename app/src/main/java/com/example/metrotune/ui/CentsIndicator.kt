package com.example.metrotune.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs

/** ±50-cent scale: tolerance-sized green zone, needle, peak-hold marker. */
class CentsIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var cents = 0f
        set(value) {
            field = value.coerceIn(-50f, 50f)
            val now = System.currentTimeMillis()
            val a = abs(field)
            if (a >= peak || now - peakAt > PEAK_HOLD_MS) {
                peak = a
                peakSign = if (field < 0) -1 else 1
                peakAt = now
            }
            invalidate()
        }

    /** Width of the green zone in cents — driven by the user's tolerance. */
    var zoneCents = 5f

    private var peak = 0f
    private var peakSign = 1
    private var peakAt = 0L

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E7D32.toInt() }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }
    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        tickPaint.color = ThemePalette.grid
        needlePaint.color = ThemePalette.needle
        peakPaint.color = ThemePalette.peak

        val cy = height / 2f
        val centerX = width / 2f
        val usable = width - 32f * resources.displayMetrics.density
        if (usable <= 0) return

        val zoneHalf = usable * (zoneCents / 50f)
        canvas.drawRoundRect(
            centerX - zoneHalf, cy - 14f, centerX + zoneHalf, cy + 14f, 10f, 10f, zonePaint)

        for (c in -50..50 step 10) {
            val x = centerX + usable * (c / 50f)
            canvas.drawLine(x, cy - 12f, x, cy + 12f, tickPaint)
        }

        if (peak > 2f) {
            val px = centerX + peakSign * usable * (peak / 50f)
            canvas.drawLine(px, cy - 26f, px, cy + 26f, peakPaint)
        }

        val x = centerX + usable * (cents / 50f)
        canvas.drawLine(x, cy - 34f, x, cy + 34f, needlePaint)
    }

    companion object { private const val PEAK_HOLD_MS = 1500L }
}
