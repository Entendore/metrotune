package com.example.metrotune.ui

import android.content.Context
import android.content.res.Configuration

/**
 * Palette for the custom Canvas views. Data colors (accent purple, green/amber
 * states) are fixed mid-tones that read on both themes; surfaces, grids,
 * labels, needles and track fills switch. Views re-read these at the top of
 * onDraw, so a runtime dark-mode toggle (activity recreation) is picked up
 * with no lifecycle coupling — paint construction order is irrelevant.
 */
object ThemePalette {
    @Volatile var isLight = false; private set

    fun init(context: Context) {
        val mask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        isLight = mask == Configuration.UI_MODE_NIGHT_NOT
    }

    val surface: Int get() = if (isLight) 0xFFE9EDF2.toInt() else 0xFF10141B.toInt()
    val track:   Int get() = if (isLight) 0xFFC9D1DA.toInt() else 0xFF232A36.toInt()
    val grid:    Int get() = if (isLight) 0x40000000.toInt() else 0x30FFFFFF.toInt()
    val label:   Int get() = if (isLight) 0x8A000000.toInt() else 0x88FFFFFF.toInt()
    val needle:  Int get() = if (isLight) 0xFF263238.toInt() else 0xFFFFFFFF.toInt()
    val peak:    Int get() = if (isLight) 0x59000000.toInt() else 0x73FFFFFF.toInt()
    val stripe:  Int get() = if (isLight) 0xFF546E7A.toInt() else 0xFFB0BEC5.toInt()
    val rod:     Int get() = if (isLight) 0xFF546E7A.toInt() else 0xFFB0BEC5.toInt()
    val idle:    Int get() = 0xFF78909C.toInt()
}
