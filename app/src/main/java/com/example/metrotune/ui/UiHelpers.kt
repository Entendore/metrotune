package com.example.metrotune.ui

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner

/**
 * Builds the adapter and attaches the listener. Some Spinner versions fire
 * one attach-time onItemSelected after layout; we absorb it — but only if it
 * reports the initial position, so the user's first real choice is never lost.
 */
fun Spinner.setup(
    items: List<String>,
    initial: Int = 0,
    onSelected: (Int) -> Unit = {}
) {
    adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, items).apply {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }
    val initPos = initial.coerceIn(0, items.size - 1)
    setSelection(initPos, false)
    var echoHandled = false
    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
            if (!echoHandled) {
                echoHandled = true
                if (pos == initPos) return
            }
            onSelected(pos)
        }
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }
}

/**
 * Hardware-style auto-repeat on press-and-hold: fires immediately, then
 * repeats with an accelerating interval (400 ms → 60 ms). Replaces a plain
 * click listener — the tap itself is handled on ACTION_DOWN.
 */
fun View.setFastRepeat(onStep: () -> Unit) {
    val handler = Handler(Looper.getMainLooper())
    var runnable: Runnable? = null
    var interval = 300L
    setOnTouchListener { v, e ->
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                onStep()
                interval = 300L
                val r = object : Runnable {
                    override fun run() {
                        onStep()
                        interval = (interval * 0.8f).toLong().coerceAtLeast(60L)
                        handler.postDelayed(this, interval)
                    }
                }
                runnable = r
                handler.postDelayed(r, 400)
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                runnable?.let { handler.removeCallbacks(it) }
                v.performClick()
                true
            }
            else -> false
        }
    }
}
