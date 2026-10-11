package com.example.metrotune.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Bars for the last 7 days (oldest → today), with a dashed daily-goal line. */
class PracticeWeekView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var data: LongArray = LongArray(7)
    private var letters: List<String> = List(7) { "" }
    private var goalSeconds = 0L

    private val gray = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7C4DFF.toInt() }
    private val done = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4CAF50.toInt() }
    private val goalLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88FFB74D.toInt(); strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(10f); textAlign = Paint.Align.CENTER
    }

    fun setData(seconds: LongArray, dayLabels: List<String>, goalSec: Long) {
        data = seconds; letters = dayLabels; goalSeconds = goalSec; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        gray.color = ThemePalette.idle
        label.color = ThemePalette.label
        val w = width.toFloat(); val h = height.toFloat()
        val labelH = label.textSize + dp(6f)
        val chartH = (h - labelH).coerceAtLeast(1f)
        val slot = w / 7f
        val barW = slot * 0.5f
        val maxVal = maxOf(goalSeconds.toFloat(), (data.maxOrNull() ?: 0L).toFloat(), 60f)

        if (goalSeconds > 0) {
            val y = chartH * (1f - goalSeconds / maxVal)
            canvas.drawLine(0f, y, w, y, goalLine)
        }

        for (i in 0 until 7) {
            val cx = slot * i + slot / 2f
            val bh = (data[i] / maxVal).coerceIn(0f, 1f) * chartH
            val paint = when {
                goalSeconds > 0 && data[i] >= goalSeconds -> done
                i == 6 -> accent
                else -> gray
            }
            if (bh > 2f) {
                canvas.drawRoundRect(cx - barW / 2, chartH - bh, cx + barW / 2, chartH,
                    dp(3f), dp(3f), paint)
            }
            canvas.drawText(letters.getOrElse(i) { "" }, cx, h - dp(3f), label)
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
