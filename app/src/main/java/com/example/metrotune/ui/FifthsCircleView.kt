package com.example.metrotune.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Circle of fifths: adjacent spokes colored by deviation from the pure 3:2. */
class FifthsCircleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    companion object {
        // circle-of-fifths placement order (pitch classes): C G D A E B F♯ C♯ G♯ D♯ A♯ F
        val ORDER = intArrayOf(0, 7, 2, 9, 4, 11, 6, 1, 3, 8, 10, 5)
        val NAMES = listOf("C", "G", "D", "A", "E", "B", "F♯", "C♯", "G♯", "D♯", "A♯", "F")
    }

    private val d = resources.displayMetrics.density
    private var devs: DoubleArray = DoubleArray(12)

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * d
    }
    private val spoke = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * d
        textAlign = Paint.Align.CENTER
    }
    private val center = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f * d
        textAlign = Paint.Align.CENTER
    }

    fun setDeviations(v: DoubleArray) {
        if (v.size == 12) { devs = v; invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        ring.color = ThemePalette.grid
        label.color = ThemePalette.label
        center.color = ThemePalette.label
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 18f * d

        canvas.drawCircle(cx, cy, r, ring)

        fun pos(i: Int): Pair<Float, Float> {
            val a = Math.toRadians(-90.0 + i * 30.0)
            return (cx + r * cos(a)).toFloat() to (cy + r * sin(a)).toFloat()
        }

        ORDER.forEachIndexed { i, pc ->
            val (x, y) = pos(i)
            val (lx, ly) = pos(i)
            canvas.drawText(
                NAMES[pc],
                lx * 1.16f - cx * 0.16f,
                ly * 1.16f - cy * 0.16f + 4f * d,
                label
            )

            val dev = devs[(pc + 7) % 12]   // fifth built on pc lands on (pc+7)
            val ax = abs(dev)
            spoke.color = when {
                ax < 2.0 -> 0xFF4CAF50.toInt()
                ax < 10.0 -> 0xFFFFB74D.toInt()
                else -> 0xFFE53935.toInt()
            }
            spoke.strokeWidth = (2f + ax.coerceAtMost(20f) / 20f * 3f) * d
            val (nx, ny) = pos((i + 1) % 12)
            canvas.drawLine(x, y, nx, ny, spoke)
        }

        val maxDev = devs.maxOfOrNull { abs(it) } ?: 0.0
        canvas.drawText("max %+.1f¢".format(maxDev), cx, cy + 4f * d, center)
    }
}
