package com.example.metrotune.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** Scrollable 88-key piano (A0–C8). Detects keys, marks targets, tap-to-play. */
class OctaveKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    companion object { const val LOW = 21; const val HIGH = 108 }

    var onKeyTap: ((Int) -> Unit)? = null

    private var activeMidi = -1
    private var targets: Set<Int> = emptySet()
    private val keyW = dp(context, 26f)
    private val blackW = keyW * 0.62f

    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val whiteActive = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7C4DFF.toInt() }
    private val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF10141B.toInt() }
    private val blackActive = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF512DA8.toInt() }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF455A64.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4CAF50.toInt() }
    private val keyLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF37474F.toInt(); textSize = dp(context, 9f); textAlign = Paint.Align.CENTER
    }

    private val whiteCount = (LOW..HIGH).count { !isBlack(it) }

    fun setActiveMidi(midi: Int) { if (midi != activeMidi) { activeMidi = midi; invalidate() } }
    fun setTargets(list: List<Int>) { targets = list.toSet(); invalidate() }

    private fun isBlack(m: Int) = when (m % 12) { 1, 3, 6, 8, 10 -> true; else -> false }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY)
            MeasureSpec.getSize(heightMeasureSpec) else dp(context, 64f).toInt()
        setMeasuredDimension((whiteCount * keyW).toInt(), h)
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()

        var wx = 0f
        for (m in LOW..HIGH) {
            if (isBlack(m)) continue
            canvas.drawRect(wx, 0f, wx + keyW - 1f, h, if (m == activeMidi) whiteActive else white)
            canvas.drawRect(wx, 0f, wx + keyW - 1f, h, border)
            if (targets.contains(m)) {
                canvas.drawCircle(wx + keyW / 2f, h - dp(context, 7f), dp(context, 3.5f), dot)
            }
            if (m % 12 == 0) {
                canvas.drawText("C${m / 12 - 1}", wx + keyW / 2f, h - dp(context, 16f), keyLabel)
            }
            wx += keyW
        }

        wx = 0f
        for (m in LOW..HIGH) {
            if (!isBlack(m)) { wx += keyW; continue }
            val left = wx - blackW / 2f
            val bh = h * 0.62f
            canvas.drawRect(left, 0f, left + blackW - 1f, bh,
                if (m == activeMidi) blackActive else black)
            canvas.drawRect(left, 0f, left + blackW - 1f, bh, border)
            if (targets.contains(m)) {
                canvas.drawCircle(left + blackW / 2f, bh - dp(context, 7f), dp(context, 3.5f), dot)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val midi = keyAt(event.x, event.y)
            if (midi >= 0) {
                performClick()
                onKeyTap?.invoke(midi)
                flash(midi)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    /** Black keys first (they overlay the top 62% of whites), then whites. */
    private fun keyAt(x: Float, y: Float): Int {
        val h = height.toFloat()
        if (y <= h * 0.62f) {
            var wx = 0f
            for (m in LOW..HIGH) {
                if (!isBlack(m)) { wx += keyW; continue }
                val left = wx - blackW / 2f
                if (x >= left && x <= left + blackW) return m
            }
        }
        val idx = (x / keyW).toInt()
        if (idx in 0 until whiteCount) {
            var c = 0
            for (m in LOW..HIGH) if (!isBlack(m)) { if (c++ == idx) return m }
        }
        return -1
    }

    private fun flash(midi: Int) {
        activeMidi = midi
        invalidate()
        postDelayed({
            if (activeMidi == midi) { activeMidi = -1; invalidate() }
        }, 350)
    }

    private fun dp(context: Context, v: Float) = v * context.resources.displayMetrics.density
}
