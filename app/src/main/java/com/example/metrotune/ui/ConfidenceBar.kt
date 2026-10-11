package com.example.metrotune.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Thin 0–100 detection-confidence indicator (gray → amber → green). */
class ConfidenceBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var confidence = 0
        set(value) { field = value.coerceIn(0, 100); invalidate() }

    private val track = Paint()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        track.color = ThemePalette.track
        val h = height.toFloat()
        val r = h / 2f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), h, r, r, track)
        val w = width * confidence / 100f
        if (w > r) {
            fill.color = when {
                confidence >= 75 -> 0xFF4CAF50.toInt()
                confidence >= 45 -> 0xFFFFB74D.toInt()
                else -> 0xFF78909C.toInt()
            }
            canvas.drawRoundRect(0f, 0f, w, h, r, r, fill)
        }
    }
}
