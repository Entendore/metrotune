package com.example.metrotune.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.metrotune.audio.SpectrumAnalyzer
import com.example.metrotune.audio.TunerEngine
import kotlin.math.abs
import kotlin.math.ln

private fun dp(context: Context, v: Float) = v * context.resources.displayMetrics.density

/** Log-frequency spectrum with octave gridlines and a fundamental marker. */
class SpectrumAnalyzerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var data: FloatArray? = null
    private var markerFreq = -1.0

    private val bar = Paint().apply { color = 0xFF7C4DFF.toInt() }
    private val grid = Paint().apply { strokeWidth = 1f }
    private val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF5252.toInt(); strokeWidth = 2f
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(context, 9f) }

    private val gridFreqs = listOf(
        "C1" to 32.70, "C2" to 65.41, "C3" to 130.81, "C4" to 261.63,
        "C5" to 523.25, "C6" to 1046.5, "C7" to 2093.0, "A4" to 440.0)

    fun update(spectrum: FloatArray?, f0: Double) {
        data = spectrum; markerFreq = f0; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        grid.color = ThemePalette.grid
        label.color = ThemePalette.label
        val w = width.toFloat(); val h = height.toFloat()
        val fSpan = ln(SpectrumAnalyzer.F_MAX / SpectrumAnalyzer.F_MIN).toFloat()

        fun fx(f: Double) = w * (ln(f / SpectrumAnalyzer.F_MIN).toFloat() / fSpan)

        for ((name, f) in gridFreqs) {
            val x = fx(f)
            canvas.drawLine(x, 0f, x, h - label.textSize - 4f, grid)
            canvas.drawText(name, x + 3f, h - 3f, label)
        }

        val d = data ?: return
        val n = d.size
        val barW = w / n
        for (i in 0 until n) {
            val frac = ((d[i] + SpectrumAnalyzer.DB_FLOOR.toFloat()) /
                -SpectrumAnalyzer.DB_FLOOR.toFloat()).coerceIn(0f, 1f)
            if (frac <= 0.01f) continue
            val bh = frac * (h - label.textSize - 6f)
            canvas.drawRect(i * barW, h - label.textSize - 4f - bh,
                (i + 1) * barW - 1f, h - label.textSize - 4f, bar)
        }

        if (markerFreq > 0) {
            val x = fx(markerFreq)
            canvas.drawLine(x, 0f, x, h, marker)
            canvas.drawCircle(x, 4f, 4f, marker)
        }
    }
}

/** Harmonics ×1…×8 (or instrument partials): cents dots (±40¢) + level bars. */
class HarmonicProfileView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var harmonics: List<TunerEngine.Harmonic> = emptyList()
    private val count = 8

    private val inTune = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4CAF50.toInt() }
    private val offTune = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFB74D.toInt() }
    private val weak = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x5578909C }
    private val midline = Paint().apply { strokeWidth = 1.5f }
    private val tick = Paint().apply { strokeWidth = 1f }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(context, 10f); textAlign = Paint.Align.CENTER
    }

    fun update(list: List<TunerEngine.Harmonic>) { harmonics = list; invalidate() }

    override fun onDraw(canvas: Canvas) {
        midline.color = ThemePalette.grid
        tick.color = ThemePalette.grid
        label.color = ThemePalette.label
        val w = width.toFloat(); val h = height.toFloat()
        val centsH = h * 0.40f
        val ampTop = centsH + dp(context, 8f)
        val ampBottom = h - label.textSize - 4f
        val ampH = (ampBottom - ampTop).coerceAtLeast(1f)
        val barW = (w / count) * 0.55f

        canvas.drawLine(0f, centsH / 2f, w, centsH / 2f, midline)
        canvas.drawLine(0f, dp(context, 4f), w, dp(context, 4f), tick)
        canvas.drawLine(0f, centsH - dp(context, 4f), w, centsH - dp(context, 4f), tick)

        for (i in 0 until count) {
            val cx = (i + 0.5f) * w / count
            val hm = harmonics.getOrNull(i)
            val lbl = when {
                hm == null -> "×${i + 1}"
                hm.ratio == kotlin.math.round(hm.ratio) -> "×${hm.ratio.toInt()}"
                else -> "×%.1f".format(hm.ratio)
            }
            canvas.drawText(lbl, cx, h - 2f, label)
            if (hm == null) continue

            val present = hm.amplitudeDb > -58.0
            if (present) {
                val frac = ((hm.amplitudeDb + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
                val paint = when {
                    hm.centsDeviation.isNaN() -> weak
                    abs(hm.centsDeviation) <= 3.0 -> inTune
                    else -> offTune
                }
                val bh = frac * ampH
                canvas.drawRect(cx - barW / 2, ampBottom - bh, cx + barW / 2, ampBottom, paint)

                if (!hm.centsDeviation.isNaN()) {
                    val y = (centsH / 2f - (hm.centsDeviation / 40.0).toFloat()
                        .coerceIn(-1f, 1f) * (centsH / 2f - dp(context, 5f)))
                    canvas.drawCircle(cx, y, dp(context, 4.5f), paint)
                }
            } else {
                canvas.drawRect(cx - barW / 2, ampBottom - 2f, cx + barW / 2, ampBottom, weak)
            }
        }
    }
}
