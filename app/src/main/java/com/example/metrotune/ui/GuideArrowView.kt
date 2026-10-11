package com.example.metrotune.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.example.metrotune.audio.TuneGuidance
import kotlin.math.min

/**
 * Big directional guidance: chevrons pointing toward the current deviation
 * (left = flat, right = sharp — same convention as the cents bar labels),
 * count = magnitude 1–5; a green check when in tune. Null guidance = idle.
 */
class GuideArrowView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val d = resources.displayMetrics.density
    private var guidance: TuneGuidance.Guidance? = null

    private val chevron = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 7f * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }
    private val check = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4CAF50.toInt()
        strokeWidth = 9f * d
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }

    fun set(g: TuneGuidance.Guidance?) {
        if (g != guidance) { guidance = g; invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        val g = guidance ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        when (g.state) {
            TuneGuidance.State.IN_TUNE -> {
                val cx = w / 2f
                val cy = h / 2f
                val r = min(w, h) * 0.30f
                val p = Path().apply {
                    moveTo(cx - r * 0.9f, cy)
                    lineTo(cx - r * 0.15f, cy + r * 0.7f)
                    lineTo(cx + r, cy - r * 0.7f)
                }
                canvas.drawPath(p, check)
            }
            TuneGuidance.State.FLAT, TuneGuidance.State.SHARP -> {
                chevron.color = 0xFFFFB74D.toInt()
                val n = g.level.coerceIn(1, 5)
                val size = min(w, h)
                val cw = size * 0.20f          // chevron width
                val ch = size * 0.52f          // chevron height
                val gap = cw * 0.55f
                val dir = if (g.state == TuneGuidance.State.FLAT) -1f else 1f
                val total = (n - 1) * gap + cw
                var x = w / 2f - dir * total / 2f
                repeat(n) {
                    val path = Path().apply {
                        moveTo(x, h / 2f - ch / 2f)
                        lineTo(x + dir * cw, h / 2f)
                        lineTo(x, h / 2f + ch / 2f)
                    }
                    canvas.drawPath(path, chevron)
                    x += dir * gap
                }
            }
        }
    }
}
