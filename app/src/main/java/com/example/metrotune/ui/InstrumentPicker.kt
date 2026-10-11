package com.example.metrotune.ui

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.metrotune.R
import com.example.metrotune.audio.InstrumentPreset

object InstrumentPicker {

    fun show(
        context: Context,
        presets: List<InstrumentPreset>,
        currentId: String,
        recents: List<String> = emptyList(),
        onCreateCustom: (() -> Unit)? = null,
        onDeleteCustom: ((InstrumentPreset) -> Unit)? = null,
        onDetect: (() -> Unit)? = null,
        onPick: (InstrumentPreset) -> Unit
    ) {
        lateinit var dialog: AlertDialog
        val d = context.resources.displayMetrics.density

        val scroll = ScrollView(context)
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
        }
        scroll.addView(col)

        fun header(text: String) = col.addView(TextView(context).apply {
            this.text = text
            textSize = 12f
            isAllCaps = true
            alpha = 0.55f
            setPadding(0, (14 * d).toInt(), 0, (4 * d).toInt())
        })

        fun row(p: InstrumentPreset, allowDelete: Boolean = false) =
            col.addView(TextView(context).apply {
                text = p.name
                textSize = 16f
                if (p.id == currentId) { setTextColor(0xFF7C4DFF.toInt()); text = "✓ $text" }
                gravity = Gravity.CENTER_VERTICAL
                minHeight = (44 * d).toInt()
                setBackgroundResource(android.R.attr.selectableItemBackground)
                setOnClickListener { dialog.dismiss(); onPick(p) }
                if (allowDelete && onDeleteCustom != null) {
                    setOnLongClickListener {
                        AlertDialog.Builder(context)
                            .setMessage(context.getString(R.string.ci_delete_confirm, p.name))
                            .setPositiveButton(R.string.delete) { _, _ ->
                                dialog.dismiss(); onDeleteCustom(p)
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                        true
                    }
                }
            })

        if (onDetect != null) {
            col.addView(TextView(context).apply {
                text = context.getString(R.string.detect_tuning)
                textSize = 16f
                setTextColor(0xFF7C4DFF.toInt())
                gravity = Gravity.CENTER_VERTICAL
                minHeight = (44 * d).toInt()
                setBackgroundResource(android.R.attr.selectableItemBackground)
                setOnClickListener { dialog.dismiss(); onDetect() }
            })
        }

        if (onCreateCustom != null) {
            col.addView(TextView(context).apply {
                text = context.getString(R.string.picker_create)
                textSize = 16f
                setTextColor(0xFF7C4DFF.toInt())
                gravity = Gravity.CENTER_VERTICAL
                minHeight = (44 * d).toInt()
                setBackgroundResource(android.R.attr.selectableItemBackground)
                setOnClickListener { dialog.dismiss(); onCreateCustom() }
            })
        }

        if (recents.isNotEmpty()) {
            header(context.getString(R.string.picker_recent))
            recents.mapNotNull { com.example.metrotune.audio.InstrumentLibrary.byId(it) }
                .filter { it.id != currentId }
                .forEach { row(it) }
        }

        var lastFamily: String? = null
        presets.forEach { p ->
            if (p.family != lastFamily) { header(p.family); lastFamily = p.family }
            row(p, allowDelete = p.family == "My instruments")
        }

        dialog = AlertDialog.Builder(context)
            .setTitle(R.string.pick_instrument)
            .setView(scroll)
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
