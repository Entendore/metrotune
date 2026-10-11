package com.example.metrotune.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.metrotune.R
import com.example.metrotune.audio.MetronomeEngine
import com.example.metrotune.audio.MetronomeEngine.BeatAccent
import com.example.metrotune.audio.Settings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MetronomeFragment : Fragment(R.layout.fragment_metronome) {

    private val vm: MetronomeViewModel by viewModels()

    private val beatDots = mutableListOf<View>()

    private lateinit var bpmText: TextView
    private lateinit var countInText: TextView
    private lateinit var bpmSeek: SeekBar
    private lateinit var playButton: Button
    private lateinit var beatRow: LinearLayout
    private lateinit var patternRow: LinearLayout
    private lateinit var presetRow: LinearLayout
    private lateinit var pendulum: PendulumView
    private lateinit var flashOverlay: View
    private lateinit var spinBeats: Spinner
    private lateinit var spinSub: Spinner
    private lateinit var spinCountIn: Spinner
    private lateinit var spinPoly: Spinner
    private lateinit var spinTrainer: Spinner
    private lateinit var seekTrainerA: SeekBar
    private lateinit var seekTrainerB: SeekBar
    private lateinit var labelTrainerA: TextView
    private lateinit var labelTrainerB: TextView
    private lateinit var seekClickVol: SeekBar
    private lateinit var checkFlash: CheckBox
    private lateinit var checkHaptics: CheckBox
    private lateinit var checkMidiClock: CheckBox
    private lateinit var spinSetlist: Spinner
    private lateinit var checkSetlistLoop: CheckBox
    private lateinit var setlistChipRow: LinearLayout
    private lateinit var textNowNext: TextView

    // echo-suppression diff keys
    private var patternKey = ""
    private var setlistKey = ""
    private var presetsKey = ""
    private var setlistAdapterKey = ""
    private var lastDots = -1

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            vm.playConfirmed()
        }

    private val blePermsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.all { it }) vm.setMidiClock(true)
            else {
                checkMidiClock.isChecked = false
                Toast.makeText(requireContext(),
                    R.string.midi_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ThemePalette.init(requireContext())

        bpmText = view.findViewById(R.id.bpmText)
        countInText = view.findViewById(R.id.countInText)
        bpmSeek = view.findViewById(R.id.bpmSeek)
        playButton = view.findViewById(R.id.playButton)
        beatRow = view.findViewById(R.id.beatRow)
        patternRow = view.findViewById(R.id.patternRow)
        presetRow = view.findViewById(R.id.presetRow)
        pendulum = view.findViewById(R.id.pendulum)
        flashOverlay = view.findViewById(R.id.flashOverlay)
        spinBeats = view.findViewById(R.id.spinBeats)
        spinSub = view.findViewById(R.id.spinSub)
        spinCountIn = view.findViewById(R.id.spinCountIn)
        spinPoly = view.findViewById(R.id.spinPoly)
        spinTrainer = view.findViewById(R.id.spinTrainer)
        seekTrainerA = view.findViewById(R.id.seekTrainerA)
        seekTrainerB = view.findViewById(R.id.seekTrainerB)
        labelTrainerA = view.findViewById(R.id.labelTrainerA)
        labelTrainerB = view.findViewById(R.id.labelTrainerB)
        seekClickVol = view.findViewById(R.id.seekClickVol)
        checkFlash = view.findViewById(R.id.checkFlash)
        checkHaptics = view.findViewById(R.id.checkHaptics)
        checkMidiClock = view.findViewById(R.id.checkMidiClock)
        spinSetlist = view.findViewById(R.id.spinSetlist)
        checkSetlistLoop = view.findViewById(R.id.checkSetlistLoop)
        setlistChipRow = view.findViewById(R.id.setlistChipRow)
        textNowNext = view.findViewById(R.id.textNowNext)

        flashOverlay.background = android.graphics.ColorDrawable(
            if (ThemePalette.isLight) 0xFF263238.toInt() else 0xFFFFFFFF.toInt())

        bindControls()
        buildBeatDots(4)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.ui.collect { render(it) } }
                launch {
                    vm.beats.collect { p ->
                        pendulum.onBeat()
                        highlightBeat(p.beat)
                        val s = vm.ui.value
                        if (p.beat == 0 && s.haptics) vibrate()
                        if (p.beat == 0 && !p.countIn && s.flash) flashScreen()
                        countInText.visibility = if (p.countIn) View.VISIBLE else View.GONE
                    }
                }
                launch { vm.msgs.collect { handle(it) } }
            }
        }
    }

    private fun bindControls() {
        bpmSeek.max = MetronomeEngine.MAX_BPM - MetronomeEngine.MIN_BPM
        bpmSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) vm.setBpm(progress + MetronomeEngine.MIN_BPM)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        })
        requireView().findViewById<Button>(R.id.btnMinus)
            .setFastRepeat { vm.setBpm(vm.ui.value.bpm - 1) }
        requireView().findViewById<Button>(R.id.btnPlus)
            .setFastRepeat { vm.setBpm(vm.ui.value.bpm + 1) }
        requireView().findViewById<Button>(R.id.btnTap).setOnClickListener { vm.tapTempo() }
        playButton.setOnClickListener { vm.togglePlay() }

        spinBeats.setup((1..12).map { it.toString() }) { vm.setBeats(it) }
        spinSub.setup(resources.getStringArray(R.array.subdivisions).toList()) { vm.setSub(it) }
        spinCountIn.setup(
            listOf(getString(R.string.countin_off), "1 bar", "2 bars", "4 bars")
        ) { vm.setCountIn(it) }
        spinPoly.setup(MetronomeViewModel.POLY_OPTIONS) { vm.setPoly(it) }
        spinTrainer.setup(listOf(
            getString(R.string.trainer_off),
            getString(R.string.trainer_ramp),
            getString(R.string.trainer_gap))) { vm.setTrainerMode(it) }

        val seekWatcher = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                if (sb?.id == R.id.seekTrainerA) vm.setTrainerA(p + 1) else vm.setTrainerB(p + 1)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        }
        seekTrainerA.max = 9; seekTrainerA.progress = 1
        seekTrainerB.max = 7; seekTrainerB.progress = 3
        seekTrainerA.setOnSeekBarChangeListener(seekWatcher)
        seekTrainerB.setOnSeekBarChangeListener(seekWatcher)

        seekClickVol.max = 24
        seekClickVol.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) vm.setVolumeProgress(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        })

        checkHaptics.setOnCheckedChangeListener { _, c -> vm.setHaptics(c) }
        checkFlash.setOnCheckedChangeListener { _, c -> vm.setFlash(c) }
        checkMidiClock.setOnCheckedChangeListener { _, c ->
            if (c && Build.VERSION.SDK_INT >= 31) {
                val perms = arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT)
                if (perms.any {
                        ContextCompat.checkSelfPermission(requireContext(), it) !=
                            PackageManager.PERMISSION_GRANTED
                    }) {
                    checkMidiClock.isChecked = false
                    blePermsLauncher.launch(perms)
                    return@setOnCheckedChangeListener
                }
            }
            vm.setMidiClock(c)
        }

        checkSetlistLoop.setOnCheckedChangeListener { _, c -> vm.setSetlistLoop(c) }
        requireView().findViewById<Button>(R.id.btnAddSetlistItem)
            .setOnClickListener { showAddSetlistItemDialog() }
        requireView().findViewById<Button>(R.id.btnSaveSetlist)
            .setOnClickListener { showSaveSetlistDialog() }
        requireView().findViewById<Button>(R.id.btnShareSetlist)
            .setOnClickListener { vm.shareSetlistCsv() }

        spinSetlist.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long
                ) {
                    val names = vm.ui.value.setlistNames
                    vm.selectSetlist(if (pos == 0) null else names.getOrNull(pos - 1))
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
    }

    // ---------- render ----------

    private fun render(s: MetronomeViewModel.UiState) {
        playButton.setText(if (s.running) R.string.stop else R.string.start)
        bpmText.text = (if (s.running) s.liveBpm else s.bpm).toString()
        pendulum.setPeriod(if (s.running) s.liveBpm else s.bpm)
        bpmSeek.progress = s.bpm - MetronomeEngine.MIN_BPM
        beatRow.alpha = if (s.muted) 0.35f else 1f

        // spinners — programmatic selection must not echo
        if (spinBeats.selectedItemPosition != s.beatsPerBar - 1)
            spinBeats.setSelection(s.beatsPerBar - 1, false)
        if (spinSub.selectedItemPosition != s.subdivision - 1)
            spinSub.setSelection(s.subdivision - 1, false)
        val ciIdx = MetronomeViewModel.COUNT_IN_VALUES.indexOf(s.countInBars).coerceAtLeast(0)
        if (spinCountIn.selectedItemPosition != ciIdx) spinCountIn.setSelection(ciIdx, false)
        if (spinPoly.selectedItemPosition != s.polyIndex) spinPoly.setSelection(s.polyIndex, false)
        if (spinTrainer.selectedItemPosition != s.trainerMode)
            spinTrainer.setSelection(s.trainerMode, false)
        if (seekTrainerA.progress != s.trainerA - 1) seekTrainerA.progress = s.trainerA - 1
        if (seekTrainerB.progress != s.trainerB - 1) seekTrainerB.progress = s.trainerB - 1
        if (seekClickVol.progress != s.volumeProgress) seekClickVol.progress = s.volumeProgress

        val gapMode = s.trainerMode == 2
        if (gapMode) {
            labelTrainerA.text = getString(R.string.gap_play, s.trainerA)
            labelTrainerB.text = getString(R.string.gap_mute, s.trainerB)
        } else {
            labelTrainerA.text = getString(R.string.ramp_step, s.trainerA)
            labelTrainerB.text = getString(R.string.ramp_every, s.trainerB)
        }

        if (checkHaptics.isChecked != s.haptics) checkHaptics.isChecked = s.haptics
        if (checkFlash.isChecked != s.flash) checkFlash.isChecked = s.flash

        // Simple/Pro panels
        val pro = if (Settings.simpleMode) View.GONE else View.VISIBLE
        requireView().findViewById<View>(R.id.trainerPanel).visibility = pro
        requireView().findViewById<View>(R.id.polyPanel).visibility = pro
        requireView().findViewById<View>(R.id.setlistPanel).visibility = pro
        requireView().findViewById<View>(R.id.feelRow).visibility = pro
        requireView().findViewById<View>(R.id.patternPanel).visibility = pro
        requireView().findViewById<View>(R.id.metroExtras).visibility = pro

        buildBeatDots(
            if (MetronomeEngine.polyVoice > 0) MetronomeEngine.polyVoice else s.beatsPerBar)

        renderPatternChips(s.pattern)
        renderPresetRow(s.presets)
        renderSetlist(s)
    }

    private fun renderPatternChips(pattern: List<BeatAccent>) {
        val key = pattern.joinToString(",") { it.name }
        if (key == patternKey) return
        patternKey = key
        patternRow.removeAllViews()
        val d = resources.displayMetrics.density
        pattern.forEachIndexed { i, acc ->
            patternRow.addView(TextView(requireContext()).apply {
                text = accentGlyph(acc)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), (8 * d).toInt())
                setBackgroundResource(
                    if (acc == BeatAccent.ACCENT) R.drawable.chip_bg_selected
                    else R.drawable.chip_bg)
                alpha = when (acc) {
                    BeatAccent.SOFT -> 0.65f; BeatAccent.MUTE -> 0.45f; else -> 1f
                }
                setOnClickListener { vm.cyclePatternAt(i) }
            }, rowParams(d))
        }
    }

    private fun renderPresetRow(presets: List<Settings.TempoPreset>) {
        val key = presets.joinToString("|") { it.name }
        if (key == presetsKey) return
        presetsKey = key
        presetRow.removeAllViews()
        val d = resources.displayMetrics.density
        fun chip(text: String) = TextView(requireContext()).apply {
            this.text = text; textSize = 14f; gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
            setBackgroundResource(R.drawable.chip_bg)
        }
        presetRow.addView(chip(getString(R.string.save_preset)).apply {
            setOnClickListener { showSavePresetDialog() }
        }, rowParams(d))
        presets.forEach { p ->
            presetRow.addView(chip("${p.name} · ${p.bpm}").apply {
                setOnClickListener { vm.loadPreset(p) }
                setOnLongClickListener {
                    AlertDialog.Builder(requireContext())
                        .setMessage(getString(R.string.delete_preset_confirm, p.name))
                        .setPositiveButton(R.string.delete) { _, _ ->
                            vm.deleteTempoPreset(p.name)
                        }
                        .setNegativeButton(R.string.cancel, null).show()
                    true
                }
            }, rowParams(d))
        }
    }

    private fun renderSetlist(s: MetronomeViewModel.UiState) {
        val namesKey = s.setlistNames.joinToString("|") + "#" + (s.activeSetlist ?: "")
        if (namesKey != setlistAdapterKey) {
            setlistAdapterKey = namesKey
            val options = listOf(getString(R.string.setlist_off)) + s.setlistNames
            spinSetlist.adapter = android.widget.ArrayAdapter(
                requireContext(), android.R.layout.simple_spinner_item, options).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            val sel = s.activeSetlist?.let { s.setlistNames.indexOf(it) + 1 } ?: 0
            spinSetlist.setSelection(sel.coerceIn(0, options.size - 1), false)
        }
        if (checkSetlistLoop.isChecked != s.setlistLoop) checkSetlistLoop.isChecked = s.setlistLoop

        val chipsKey = s.workingSetlist.joinToString("|") { "${it.name}:${it.bars}:${it.bpm}" }
        if (chipsKey != setlistKey) {
            setlistKey = chipsKey
            setlistChipRow.removeAllViews()
            val d = resources.displayMetrics.density
            s.workingSetlist.forEachIndexed { i, item ->
                setlistChipRow.addView(TextView(requireContext()).apply {
                    text = "${item.name} · ${item.bpm}" +
                        if (item.bars > 0) " · ${item.bars} bars" else " · ∞"
                    textSize = 13f; gravity = Gravity.CENTER
                    setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
                    setBackgroundResource(R.drawable.chip_bg)
                    setOnClickListener { showEditSetlistItemDialog(i) }
                }, rowParams(d))
            }
        }

        textNowNext.text = when {
            s.workingSetlist.isEmpty() -> ""
            s.nowPart == null -> getString(R.string.setlist_ready, s.workingSetlist[0].name)
            s.nextPart != null -> getString(R.string.setlist_now_next, s.nowPart, s.nextPart)
            else -> getString(R.string.setlist_now, s.nowPart)
        }
    }

    private fun rowParams(d: Float) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { marginEnd = (8 * d).toInt() }

    // ---------- dialogs ----------

    private fun showSavePresetDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.preset_name_hint); setSingleLine()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.save_preset)
            .setView(wrapInput(input))
            .setPositiveButton(R.string.save) { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) vm.saveTempoPreset(n)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddSetlistItemDialog() {
        val name = EditText(requireContext()).apply {
            hint = getString(R.string.setlist_item_name); setSingleLine()
            setText(getString(R.string.setlist_part_n, vm.ui.value.workingSetlist.size + 1))
        }
        val bars = EditText(requireContext()).apply {
            hint = getString(R.string.setlist_bars_hint); setSingleLine()
            inputType = InputType.TYPE_CLASS_NUMBER; setText("4")
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.setlist_add_item)
            .setView(wrapInput(name, bars))
            .setPositiveButton(R.string.save) { _, _ ->
                vm.addSetlistItem(name.text.toString().trim(),
                    bars.text.toString().toIntOrNull() ?: 0)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditSetlistItemDialog(index: Int) {
        val item = vm.ui.value.workingSetlist.getOrNull(index) ?: return
        val name = EditText(requireContext()).apply { setText(item.name); setSingleLine() }
        val bars = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(item.bars.toString()); setSingleLine()
        }
        var editedPattern = item.pattern
        val btnPattern = Button(requireContext()).apply {
            text = getString(R.string.setlist_pattern) + " · " + patternLabel(editedPattern)
            setOnClickListener {
                showPatternEditor(item.beatsPerBar, editedPattern) { p ->
                    editedPattern = p
                    text = getString(R.string.setlist_pattern) + " · " + patternLabel(p)
                }
            }
        }
        val col = wrapInput(name, bars)
        col.addView(btnPattern)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.setlist_edit_item)
            .setView(col)
            .setPositiveButton(R.string.save) { _, _ ->
                vm.updateSetlistItem(index, name.text.toString().trim(),
                    bars.text.toString().toIntOrNull() ?: item.bars, editedPattern)
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.delete) { _, _ -> vm.removeSetlistItem(index) }
            .show()
    }

    private fun showSaveSetlistDialog() {
        if (vm.ui.value.workingSetlist.isEmpty()) {
            Toast.makeText(requireContext(), R.string.setlist_empty,
                Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.setlist_name_hint); setSingleLine()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.setlist_save)
            .setView(wrapInput(input))
            .setPositiveButton(R.string.save) { _, _ ->
                vm.saveSetlist(input.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showPatternEditor(
        beats: Int,
        initial: List<BeatAccent>?,
        onDone: (List<BeatAccent>?) -> Unit
    ) {
        val d = resources.displayMetrics.density
        val inherit = CheckBox(requireContext()).apply {
            text = getString(R.string.setlist_pattern_inherit)
            isChecked = initial == null
        }
        val working = (initial ?: List(beats) {
            if (it == 0) BeatAccent.ACCENT else BeatAccent.NORMAL }).toMutableList()
        val row = LinearLayout(requireContext()).apply {
            gravity = Gravity.CENTER
            setPadding(0, (12 * d).toInt(), 0, 0)
        }
        fun rebuild() {
            row.removeAllViews()
            if (inherit.isChecked) return
            working.forEachIndexed { i, acc ->
                row.addView(TextView(requireContext()).apply {
                    text = accentGlyph(acc)
                    textSize = 14f; gravity = Gravity.CENTER
                    setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
                    setBackgroundResource(
                        if (acc == BeatAccent.ACCENT) R.drawable.chip_bg_selected
                        else R.drawable.chip_bg)
                    setOnClickListener { working[i] = cycleAccent(acc); rebuild() }
                }, rowParams(d))
            }
        }
        inherit.setOnCheckedChangeListener { _, _ -> rebuild() }
        rebuild()
        val col = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * d).toInt(), (8 * d).toInt(), (24 * d).toInt(), 0)
            addView(inherit); addView(row)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.pattern_editor_title)
            .setView(col)
            .setPositiveButton(R.string.save) { _, _ ->
                onDone(if (inherit.isChecked) null else working.toList())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun wrapInput(vararg views: EditText): LinearLayout {
        val d = resources.displayMetrics.density
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * d).toInt(), (8 * d).toInt(), (24 * d).toInt(), 0)
            views.forEach { addView(it) }
        }
    }

    // ---------- helpers ----------

    private fun accentGlyph(a: BeatAccent) = when (a) {
        BeatAccent.ACCENT -> "A"; BeatAccent.NORMAL -> "N"
        BeatAccent.SOFT -> "S"; BeatAccent.MUTE -> "✕"
    }

    private fun cycleAccent(a: BeatAccent) = when (a) {
        BeatAccent.ACCENT -> BeatAccent.NORMAL
        BeatAccent.NORMAL -> BeatAccent.SOFT
        BeatAccent.SOFT -> BeatAccent.MUTE
        BeatAccent.MUTE -> BeatAccent.ACCENT
    }

    private fun patternLabel(p: List<BeatAccent>?) =
        p?.joinToString("·") { accentGlyph(it) } ?: getString(R.string.setlist_pattern_inherit)

    private fun buildBeatDots(count: Int) {
        if (count == lastDots) return
        lastDots = count
        beatRow.removeAllViews()
        beatDots.clear()
        val d = resources.displayMetrics.density
        repeat(count) { i ->
            val dot = View(requireContext()).apply {
                setBackgroundResource(
                    if (i == 0) R.drawable.beat_dot_downbeat else R.drawable.beat_dot)
            }
            beatRow.addView(dot, LinearLayout.LayoutParams(
                (22 * d).toInt(), (22 * d).toInt()
            ).apply { marginEnd = (10 * d).toInt() })
            beatDots.add(dot)
        }
        highlightBeat(-1)
    }

    private fun highlightBeat(beat: Int) {
        beatDots.forEachIndexed { i, dot -> dot.isSelected = i == beat }
    }

    private fun flashScreen() {
        flashOverlay.alpha = 0.18f
        flashOverlay.visibility = View.VISIBLE
        flashOverlay.animate().alpha(0f).setDuration(200)
            .withEndAction { flashOverlay.visibility = View.GONE }.start()
    }

    private fun vibrate() {
        val ctx = context ?: return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE)
                as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                vibrator.vibrate(VibrationEffect.createOneShot(
                    40, VibrationEffect.DEFAULT_AMPLITUDE))
            else -> @Suppress("DEPRECATION") vibrator.vibrate(40)
        }
    }

    private fun handle(m: MetronomeViewModel.Msg) {
        if (!isAdded) return
        when (m) {
            MetronomeViewModel.Msg.NeedNotificationPermission ->
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            MetronomeViewModel.Msg.PlaybackFailed ->
                Toast.makeText(requireContext(), R.string.widget_start_failed,
                    Toast.LENGTH_SHORT).show()
            is MetronomeViewModel.Msg.ShareCsv -> {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, m.subject)
                    putExtra(Intent.EXTRA_TEXT, m.csv)
                }
                runCatching {
                    startActivity(Intent.createChooser(send, getString(R.string.setlist_share)))
                }
            }
        }
    }
}
