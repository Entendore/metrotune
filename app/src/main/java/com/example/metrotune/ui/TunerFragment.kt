package com.example.metrotune.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
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
import com.example.metrotune.audio.DrillRecords
import com.example.metrotune.audio.InstrumentLibrary
import com.example.metrotune.audio.InstrumentPreset
import com.example.metrotune.audio.MicRepository
import com.example.metrotune.audio.NoteUtil
import com.example.metrotune.audio.Settings
import com.example.metrotune.audio.Temperaments
import com.example.metrotune.audio.TunerEngine
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt

@AndroidEntryPoint
class TunerFragment : Fragment(R.layout.fragment_tuner) {

    private val vm: TunerViewModel by viewModels()

    // View-only toggles (display prefs — deliberately not in VM state)
    private var strobeOn = false
    private var vizOn = true

    private lateinit var statusText: TextView
    private lateinit var octaveBadge: TextView
    private lateinit var lowBadge: TextView
    private lateinit var overpullBadge: TextView
    private lateinit var noteText: TextView
    private lateinit var centsText: TextView
    private lateinit var freqText: TextView
    private lateinit var targetText: TextView
    private lateinit var centsBar: CentsIndicator
    private lateinit var confidenceBar: ConfidenceBar
    private lateinit var guideArrow: GuideArrowView
    private lateinit var strobeView: StrobeView
    private lateinit var graphView: PitchGraphView
    private lateinit var levelMeter: LevelMeterView
    private lateinit var monoSection: View
    private lateinit var headView: InstrumentHeadView
    private lateinit var auditProgress: TextView
    private lateinit var polySection: View
    private lateinit var polyList: LinearLayout
    private lateinit var grantButton: Button
    private lateinit var btnInstrument: Button
    private lateinit var btnSession: Button
    private lateinit var btnSetup: Button
    private lateinit var btnProMode: Button
    private lateinit var btnStrobe: Button
    private lateinit var btnViz: Button
    private lateinit var btnTone: Button
    private lateinit var btnQuickTone: Button
    private lateinit var btnFreeze: Button
    private lateinit var beatText: TextView
    private lateinit var intervalText: TextView
    private lateinit var btnShare: Button
    private lateinit var vizPanel: View
    private lateinit var spectrumView: SpectrumAnalyzerView
    private lateinit var harmonicView: HarmonicProfileView
    private lateinit var keyboardView: OctaveKeyboardView
    private lateinit var inharmonicityText: TextView
    private lateinit var checkFeiten: CheckBox
    private lateinit var checkOctave: CheckBox
    private lateinit var checkAdaptiveGate: CheckBox
    private lateinit var checkSweetened: CheckBox
    private lateinit var checkStretch: CheckBox
    private lateinit var checkAutoAdvance: CheckBox
    private lateinit var checkOverpull: CheckBox
    private lateinit var checkAudit: CheckBox
    private lateinit var seekOverpull: SeekBar
    private lateinit var labelOverpull: TextView
    private lateinit var seekGate: SeekBar
    private lateinit var gateLabel: TextView
    private lateinit var seekCalib: SeekBar
    private lateinit var calibLabel: TextView
    private lateinit var seekToneVol: SeekBar
    private lateinit var spinWave: Spinner
    private lateinit var spinToneNote: Spinner
    private lateinit var spinInterval: Spinner
    private lateinit var spinA4: Spinner
    private lateinit var spinTemperament: Spinner
    private lateinit var spinTonic: Spinner
    private lateinit var spinTranspose: Spinner
    private lateinit var spinTolerance: Spinner
    private lateinit var spinMic: Spinner
    private lateinit var switchBle: MaterialSwitch
    private lateinit var midiStatus: TextView
    private lateinit var spinChannel: Spinner
    private lateinit var checkPitchBend: CheckBox
    private lateinit var a4Effective: TextView
    private lateinit var btnCalibA4: Button
    private lateinit var btnCurve: Button
    private lateinit var curveBar: View
    private lateinit var curveText: TextView
    private lateinit var btnCurveFinish: Button
    private lateinit var btnCurveCancel: Button
    private lateinit var btnHealth: Button
    private lateinit var btnEar: Button
    private lateinit var earLevel: Spinner
    private lateinit var earSharp: Button
    private lateinit var earFlat: Button
    private lateinit var earFeedback: TextView
    private lateinit var earScore: TextView
    private lateinit var btnDrill: Button
    private lateinit var btnRecords: Button
    private lateinit var drillHold: TextView
    private lateinit var drillStats: TextView
    private lateinit var drillPb: TextView
    private lateinit var wizardPanel: View
    private lateinit var wizardInstr: TextView
    private lateinit var wizardProgress: TextView
    private lateinit var wizardLast: TextView
    private lateinit var btnSetupShare: Button
    private lateinit var btnTempEdit: Button
    private lateinit var practiceBar: View
    private lateinit var practiceBpm: TextView
    private lateinit var practiceDots: LinearLayout
    private lateinit var practiceStop: Button
    private lateinit var studioPanel: View
    private lateinit var midiPanel: View
    private lateinit var tonePanel: View
    private lateinit var drillPanel: View
    private lateinit var earPanel: View
    private lateinit var graphCard: View
    private lateinit var a4Row: View
    private lateinit var modeToggle: MaterialButtonToggleGroup

    private val practiceBarDots = mutableListOf<View>()
    private val polyViews = mutableListOf<TextView>()
    private var polyCount = -1
    private var keyboardKey = ""
    private var temperamentKey = ""
    private val intervalSemi = intArrayOf(0, 12, 7, 5, 4, 3, 9)
    private val overpullSteps = intArrayOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50)
    private val toneNotes = (21..108).toList()
    private val NOTE_NAMES =
        listOf("C","C♯","D","D♯","E","F","F♯","G","G♯","A","A♯","B")

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            vm.setMicGranted(ok)
            if (ok) vm.startCapture() else grantButton.visibility = View.VISIBLE
        }

    private val blePermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.all { it }) vm.setBleEnabled(true)
            else {
                switchBle.isChecked = false
                midiStatus.setText(R.string.midi_permission_denied)
            }
        }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            view?.post { buildMicSpinner() }
        }
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            view?.post { if (vm.handleMicPlugChange()) buildMicSpinner() }
        }
    }

    // The practice bar mirrors the other tab's playback — direct engine
    // listener is the right tool (passive display, no shared state needed).
    private val metroListener = object : com.example.metrotune.audio.MetronomeEngine.Listener {
        override fun onBeat(beat: Int, countIn: Boolean) {
            view?.post { flashPracticeBeat(beat) }
        }
        override fun onStateChanged(running: Boolean) {
            view?.post { updatePracticeBar(running) }
        }
        override fun onTrainerTick(liveBpm: Int, muted: Boolean) {
            view?.post { practiceBpm.text = liveBpm.toString() }
        }
        override fun onSetlistAdvance(
            index: Int, item: com.example.metrotune.audio.SetlistItem?, ended: Boolean
        ) = Unit
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ThemePalette.init(requireContext())
        bindViews(view)

        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        vm.setMicGranted(granted)
        grantButton.visibility = if (granted) View.GONE else View.VISIBLE
        grantButton.setOnClickListener { requestMicWithRationale() }

        bindControls()

        keyboardView.onKeyTap = { midi ->
            spinToneNote.setSelection(toneNotes.indexOf(midi), false)
            vm.playTone(midi, spinWave.selectedItemPosition, seekToneVol.progress)
        }
        headView.onStringTap = {
            vm.setLockedString(if (vm.ui.value.lockedString == it) -1 else it)
        }
        headView.onStringLongPress = { showOffsetEditor(it) }
        btnShare.setOnClickListener { shareCsv() }
        btnRecords.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch { showRecords(vm.drillRecords()) }
        }
        btnCalibA4.setOnClickListener { vm.toggleCalibration() }
        btnDrill.setOnClickListener { vm.toggleDrill() }
        btnQuickTone.setOnClickListener {
            if (vm.ui.value.tonePlaying) vm.stopTone()
            else if (!vm.playTargetTone())
                Toast.makeText(requireContext(), R.string.no_analysis, Toast.LENGTH_SHORT).show()
        }
        btnFreeze.setOnClickListener { vm.toggleFreeze() }
        btnCurve.setOnClickListener {
            if (vm.ui.value.curveCapture) vm.cancelCurveCapture()
            else vm.startCurveCapture()
        }
        btnCurve.setOnLongClickListener { showCurvePicker(); true }
        btnCurveCancel.setOnClickListener { vm.cancelCurveCapture() }
        btnCurveFinish.setOnClickListener { showCurveSaveDialog() }
        btnHealth.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch { showHealth(vm.healthRows()) }
        }
        btnEar.setOnClickListener { vm.toggleEar() }
        earLevel.setup(listOf("±10¢ · easy", "±5¢ · medium", "±3¢ · hard")) { vm.setEarLevel(it) }
        earSharp.setOnClickListener { vm.answerEar(true) }
        earFlat.setOnClickListener { vm.answerEar(false) }
        btnSetup.setOnClickListener { onSetupClicked() }
        btnSetupShare.setOnClickListener {
            vm.setupReport()?.let { (subject, csv) -> shareText(subject, csv) }
        }
        btnTempEdit.setOnClickListener { showTemperamentEditor() }
        practiceStop.setOnClickListener {
            com.example.metrotune.MetronomeService.stop(requireContext())
        }

        (requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .registerAudioDeviceCallback(deviceCallback, null)
        com.example.metrotune.audio.MetronomeEngine.addListener(metroListener)
        updatePracticeBar(com.example.metrotune.audio.MetronomeEngine.isRunning)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.ui.collect { render(it) } }
                launch { vm.msgs.collect { handle(it) } }
            }
        }
    }

    private fun bindViews(view: View) {
        statusText = view.findViewById(R.id.statusText)
        octaveBadge = view.findViewById(R.id.octaveBadge)
        lowBadge = view.findViewById(R.id.lowBadge)
        overpullBadge = view.findViewById(R.id.overpullBadge)
        noteText = view.findViewById(R.id.noteText)
        centsText = view.findViewById(R.id.centsText)
        freqText = view.findViewById(R.id.freqText)
        targetText = view.findViewById(R.id.targetText)
        centsBar = view.findViewById(R.id.centsBar)
        confidenceBar = view.findViewById(R.id.confidenceBar)
        guideArrow = view.findViewById(R.id.guideArrow)
        strobeView = view.findViewById(R.id.strobeView)
        graphView = view.findViewById(R.id.graphView)
        levelMeter = view.findViewById(R.id.levelMeter)
        monoSection = view.findViewById(R.id.monoSection)
        headView = view.findViewById(R.id.headView)
        auditProgress = view.findViewById(R.id.auditProgress)
        polySection = view.findViewById(R.id.polySection)
        polyList = view.findViewById(R.id.polyList)
        grantButton = view.findViewById(R.id.grantButton)
        btnInstrument = view.findViewById(R.id.btnInstrument)
        btnSession = view.findViewById(R.id.btnSession)
        btnSetup = view.findViewById(R.id.btnSetup)
        btnProMode = view.findViewById(R.id.btnProMode)
        btnStrobe = view.findViewById(R.id.btnStrobe)
        btnViz = view.findViewById(R.id.btnViz)
        btnTone = view.findViewById(R.id.btnTone)
        btnQuickTone = view.findViewById(R.id.btnQuickTone)
        btnFreeze = view.findViewById(R.id.btnFreeze)
        beatText = view.findViewById(R.id.beatText)
        intervalText = view.findViewById(R.id.intervalText)
        btnShare = view.findViewById(R.id.btnShare)
        vizPanel = view.findViewById(R.id.vizPanel)
        spectrumView = view.findViewById(R.id.spectrumView)
        harmonicView = view.findViewById(R.id.harmonicView)
        keyboardView = view.findViewById(R.id.keyboardView)
        inharmonicityText = view.findViewById(R.id.inharmonicityText)
        checkFeiten = view.findViewById(R.id.checkFeiten)
        checkOctave = view.findViewById(R.id.checkOctave)
        checkAdaptiveGate = view.findViewById(R.id.checkAdaptiveGate)
        checkSweetened = view.findViewById(R.id.checkSweetened)
        checkStretch = view.findViewById(R.id.checkStretch)
        checkAutoAdvance = view.findViewById(R.id.checkAutoAdvance)
        checkOverpull = view.findViewById(R.id.checkOverpull)
        checkAudit = view.findViewById(R.id.checkAudit)
        seekOverpull = view.findViewById(R.id.seekOverpull)
        labelOverpull = view.findViewById(R.id.labelOverpull)
        seekGate = view.findViewById(R.id.seekGate)
        gateLabel = view.findViewById(R.id.gateLabel)
        seekCalib = view.findViewById(R.id.seekCalib)
        calibLabel = view.findViewById(R.id.calibLabel)
        seekToneVol = view.findViewById(R.id.seekToneVol)
        spinWave = view.findViewById(R.id.spinWave)
        spinToneNote = view.findViewById(R.id.spinToneNote)
        spinInterval = view.findViewById(R.id.spinInterval)
        spinA4 = view.findViewById(R.id.spinA4)
        spinTemperament = view.findViewById(R.id.spinTemperament)
        spinTonic = view.findViewById(R.id.spinTonic)
        spinTranspose = view.findViewById(R.id.spinTranspose)
        spinTolerance = view.findViewById(R.id.spinTolerance)
        spinMic = view.findViewById(R.id.spinMic)
        switchBle = view.findViewById(R.id.switchBle)
        midiStatus = view.findViewById(R.id.midiStatus)
        spinChannel = view.findViewById(R.id.spinChannel)
        checkPitchBend = view.findViewById(R.id.checkPitchBend)
        a4Effective = view.findViewById(R.id.a4Effective)
        btnCalibA4 = view.findViewById(R.id.btnCalibA4)
        btnCurve = view.findViewById(R.id.btnCurve)
        curveBar = view.findViewById(R.id.curveBar)
        curveText = view.findViewById(R.id.curveText)
        btnCurveFinish = view.findViewById(R.id.btnCurveFinish)
        btnCurveCancel = view.findViewById(R.id.btnCurveCancel)
        btnHealth = view.findViewById(R.id.btnHealth)
        btnEar = view.findViewById(R.id.btnEar)
        earLevel = view.findViewById(R.id.earLevel)
        earSharp = view.findViewById(R.id.earSharp)
        earFlat = view.findViewById(R.id.earFlat)
        earFeedback = view.findViewById(R.id.earFeedback)
        earScore = view.findViewById(R.id.earScore)
        btnDrill = view.findViewById(R.id.btnDrill)
        btnRecords = view.findViewById(R.id.btnRecords)
        drillHold = view.findViewById(R.id.drillHold)
        drillStats = view.findViewById(R.id.drillStats)
        drillPb = view.findViewById(R.id.drillPb)
        wizardPanel = view.findViewById(R.id.wizardPanel)
        wizardInstr = view.findViewById(R.id.wizardInstr)
        wizardProgress = view.findViewById(R.id.wizardProgress)
        wizardLast = view.findViewById(R.id.wizardLast)
        btnSetupShare = view.findViewById(R.id.btnSetupShare)
        btnTempEdit = view.findViewById(R.id.btnTempEdit)
        practiceBar = view.findViewById(R.id.practiceBar)
        practiceBpm = view.findViewById(R.id.practiceBpm)
        practiceDots = view.findViewById(R.id.practiceDots)
        practiceStop = view.findViewById(R.id.practiceStop)
        studioPanel = view.findViewById(R.id.studioPanel)
        midiPanel = view.findViewById(R.id.midiPanel)
        tonePanel = view.findViewById(R.id.tonePanel)
        drillPanel = view.findViewById(R.id.drillPanel)
        earPanel = view.findViewById(R.id.earPanel)
        graphCard = view.findViewById(R.id.graphCard)
        a4Row = view.findViewById(R.id.a4Row)
        modeToggle = view.findViewById(R.id.modeToggle)
    }

    // ---------- control bindings ----------

    private fun bindControls() {
        val s = vm.ui.value

        btnInstrument.setOnClickListener {
            InstrumentPicker.show(
                requireContext(), vm.presets(), s.instrument.id,
                Settings.recentInstruments(),
                onCreateCustom = { showCustomEditor() },
                onDeleteCustom = { confirmDeleteCustom(it) },
                onDetect = { showDetectResult() }
            ) { vm.selectInstrument(it) }
        }

        btnProMode.setOnClickListener { vm.setSimpleMode(!vm.ui.value.simpleMode) }
        btnSession.setOnClickListener { showSessionDialog() }

        val a4Labels = TunerViewModel.A4_VALUES.map {
            when (it) {
                415.0 -> "$it Hz (Baroque)"
                475.0 -> "$it Hz (Pipes)"
                else -> "$it Hz"
            }
        }
        spinA4.setup(a4Labels, s.a4Index) { vm.setA4Index(it) }

        spinTemperament.setup(vm.temperamentNames(), s.temperamentIndex) {
            vm.setTemperamentIndex(it)
        }
        spinTonic.setup(
            listOf("C","C♯","D","D♯","E","F","F♯","G","G♯","A","A♯","B"), s.tonicIndex) {
            vm.setTonicIndex(it)
        }
        spinTranspose.setup(TunerViewModel.TRANSPOSE_NAMES, s.transposeIndex) {
            vm.setTransposeIndex(it)
        }
        spinTolerance.setup(
            listOf("±1¢ · studio", "±3¢ · tight", "±5¢ · standard", "±10¢ · quick"),
            s.toleranceIndex) { vm.setToleranceIndex(it) }

        seekGate.max = 40
        seekGate.progress = s.gateProgress
        applyGateLabel(s.gateProgress)
        seekGate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) { vm.setGateProgress(p); applyGateLabel(p) }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        })

        seekCalib.max = 100
        seekCalib.progress = (s.calibCents + 50).toInt().coerceIn(0, 100)
        calibLabel.text = getString(R.string.cents_format, s.calibCents)
        seekCalib.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val c = (p - 50).toDouble()
                calibLabel.text = getString(R.string.cents_format, c)
                if (fromUser) vm.setCalibration(c)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        })

        checkOctave.isChecked = s.autoOctave
        checkOctave.setOnCheckedChangeListener { _, c -> vm.setAutoOctave(c) }
        checkAdaptiveGate.isChecked = s.adaptiveGate
        checkAdaptiveGate.setOnCheckedChangeListener { _, c -> vm.setAdaptiveGate(c) }
        checkFeiten.setOnCheckedChangeListener { _, c -> vm.setFeiten(c) }
        checkSweetened.isChecked = s.sweetened
        checkSweetened.setOnCheckedChangeListener { _, c ->
            if (c) checkFeiten.isChecked = false
            vm.setSweetened(c)
        }
        checkStretch.isChecked = s.stretch
        checkStretch.setOnCheckedChangeListener { _, c -> vm.setStretch(c) }
        checkAutoAdvance.isChecked = Settings.autoAdvance
        checkAutoAdvance.setOnCheckedChangeListener { _, c -> Settings.autoAdvance = c }
        checkAudit.setOnCheckedChangeListener { _, c -> vm.setAudit(c) }

        seekOverpull.max = overpullSteps.size - 1
        seekOverpull.progress = overpullSteps.indexOf(s.overpullPct).coerceAtLeast(0)
        updateOverpullLabel()
        checkOverpull.isChecked = s.overpullPct > 0
        checkOverpull.setOnCheckedChangeListener { _, c ->
            if (c && seekOverpull.progress == 0) seekOverpull.progress = 1
            if (!c) seekOverpull.progress = 0
            updateOverpullLabel()
        }
        seekOverpull.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                checkOverpull.isChecked = p > 0
                updateOverpullLabel()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        })

        spinWave.setup(listOf("Sine", "Triangle", "Square")) { vm.refreshToneIfPlaying() }
        spinToneNote.setup(toneNotes.map { NoteUtil.noteName(it) }, toneNotes.indexOf(69))
        spinInterval.setup(listOf(
            getString(R.string.interval_none),
            "Octave (+12)", "Fifth (+7)", "Fourth (+5)",
            "Major 3rd (+4)", "Minor 3rd (+3)", "Major 6th (+9)"
        )) { vm.setDroneSemi(intervalSemi[it]) }
        seekToneVol.max = 100
        seekToneVol.progress = 70
        btnTone.setOnClickListener {
            if (vm.ui.value.tonePlaying) vm.stopTone()
            else vm.playTone(toneNotes[spinToneNote.selectedItemPosition],
                spinWave.selectedItemPosition, seekToneVol.progress)
        }

        switchBle.setOnCheckedChangeListener { _, checked ->
            if (checked) maybeEnableBle() else vm.setBleEnabled(false)
        }
        spinChannel.setup((0..15).map { "Ch ${it + 1}" }, Settings.midiChannel) {
            vm.setBleChannel(it)
        }
        checkPitchBend.isChecked = Settings.midiPitchBend
        checkPitchBend.setOnCheckedChangeListener { _, c -> vm.setBlePitchBend(c) }

        strobeOn = Settings.strobeOn
        updateStrobeUi()
        btnStrobe.setOnClickListener {
            strobeOn = !strobeOn
            Settings.strobeOn = strobeOn
            updateStrobeUi()
        }
        vizOn = Settings.vizOn
        updateVizUi()
        btnViz.setOnClickListener {
            vizOn = !vizOn
            Settings.vizOn = vizOn
            updateVizUi()
            if (!vizOn) clearViz()
        }

        modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val m = when (checkedId) {
                R.id.modeString -> TunerEngine.Mode.STRING
                R.id.modePoly -> TunerEngine.Mode.POLYPHONIC
                else -> TunerEngine.Mode.CHROMATIC
            }
            if (m != vm.ui.value.mode) vm.setMode(m)
        }
    }

    private fun applyGateLabel(progress: Int) {
        val db = (-60 + progress).toFloat()
        gateLabel.text = getString(R.string.gate_value, db)
        levelMeter.gateDb = db
    }

    private fun updateStrobeUi() {
        btnStrobe.text = getString(R.string.strobe) + if (strobeOn) " · ON" else " · OFF"
        strobeView.visibility = if (strobeOn) View.VISIBLE else View.GONE
        centsBar.visibility = if (strobeOn) View.GONE else View.VISIBLE
    }

    private fun updateVizUi() {
        btnViz.text = getString(R.string.visualizers) + if (vizOn) " · ON" else " · OFF"
        vizPanel.visibility = if (vizOn) View.VISIBLE else View.GONE
    }

    private fun updateOverpullLabel() {
        val pct = overpullSteps[seekOverpull.progress]
        labelOverpull.text =
            if (pct == 0) getString(R.string.overpull_off)
            else getString(R.string.overpull_value, pct)
        vm.setOverpull(pct)
    }

    private fun buildMicSpinner() {
        val devices = vm.micDevices()
        val resolved = listOf<AudioDeviceInfo?>(null) + devices
        val labels = mutableListOf(getString(R.string.mic_auto))
        devices.forEach { labels += MicRepository.label(it) }
        var sel = 0
        if (Settings.micType >= 0) {
            sel = devices.indexOfFirst {
                it.type == Settings.micType && MicRepository.label(it) == Settings.micName
            }.let { if (it >= 0) it + 1 else 0 }
        }
        spinMic.setup(labels, sel) { pos -> vm.setPreferredMic(resolved[pos]) }
        vm.setPreferredMic(resolved[sel])
    }

    // ---------- rendering ----------

    private fun render(s: TunerViewModel.UiState) {
        // ----- instrument-dependent visibility + labels -----
        btnInstrument.text = s.instrument.name
        checkFeiten.visibility = if (s.instrument.id == "guitar_std") View.VISIBLE else View.GONE
        checkSweetened.visibility =
            if (s.instrument.sweetened.isNotEmpty()) View.VISIBLE else View.GONE
        val piano = s.instrument.id == "piano88"
        checkStretch.visibility = if (piano) View.VISIBLE else View.GONE
        checkOverpull.visibility = if (piano) View.VISIBLE else View.GONE
        seekOverpull.visibility = if (piano) View.VISIBLE else View.GONE
        labelOverpull.visibility = if (piano) View.VISIBLE else View.GONE
        checkAudit.visibility =
            if (s.instrument.strings.size >= 6 && s.mode == TunerEngine.Mode.STRING)
                View.VISIBLE else View.GONE

        // ----- Simple/Pro -----
        val pro = if (s.simpleMode) View.GONE else View.VISIBLE
        btnProMode.text = getString(R.string.pro_mode) + if (s.simpleMode) " · OFF" else " · ON"
        btnStrobe.visibility = pro
        btnViz.visibility = pro
        studioPanel.visibility = pro
        midiPanel.visibility = pro
        graphCard.visibility = pro
        drillPanel.visibility = pro
        earPanel.visibility = pro
        tonePanel.visibility = pro
        btnSession.visibility = pro
        a4Row.visibility = pro
        a4Effective.visibility = pro
        btnCalibA4.visibility = pro
        modeToggle.visibility = pro
        vizPanel.visibility = if (vizOn && !s.simpleMode) View.VISIBLE else View.GONE
        val strobeVisible = strobeOn && !s.simpleMode
        centsBar.visibility = if (strobeVisible) View.GONE else View.VISIBLE
        strobeView.visibility = if (strobeVisible) View.VISIBLE else View.GONE

        // ----- mode toggle -----
        modeToggle.findViewById<MaterialButton>(R.id.modeString)
            .isEnabled = s.instrument.supportsStringModes
        modeToggle.findViewById<MaterialButton>(R.id.modePoly)
            .isEnabled = s.instrument.supportsStringModes
        val targetId = when (s.mode) {
            TunerEngine.Mode.STRING -> R.id.modeString
            TunerEngine.Mode.POLYPHONIC -> R.id.modePoly
            else -> R.id.modeChromatic
        }
        if (modeToggle.checkedButtonId != targetId) modeToggle.check(targetId)

        val stringMode = s.mode == TunerEngine.Mode.STRING
        headView.visibility =
            if (stringMode && s.instrument.supportsStringModes) View.VISIBLE else View.GONE
        polySection.visibility =
            if (s.mode == TunerEngine.Mode.POLYPHONIC) View.VISIBLE else View.GONE
        monoSection.visibility =
            if (s.mode == TunerEngine.Mode.POLYPHONIC) View.GONE else View.VISIBLE

        // ----- keyboard targets (diffed) -----
        val key = s.instrument.id + ":" + s.mode
        if (key != keyboardKey) {
            keyboardKey = key
            keyboardView.setTargets(if (stringMode) s.instrument.strings else emptyList())
        }

        // ----- temperament spinner (custom temp changes) -----
        val tNames = vm.temperamentNames()
        val tKey = tNames.joinToString("|")
        if (tKey != temperamentKey) {
            temperamentKey = tKey
            spinTemperament.setup(tNames, s.temperamentIndex) { vm.setTemperamentIndex(it) }
        }

        a4Effective.text = getString(R.string.a4_effective, s.a4)
        btnCalibA4.setText(if (s.calibrating) R.string.calibrating else R.string.calibrate_a4)

        // ----- poly rows (rebuilt only when string count changes) -----
        if (s.instrument.strings.size != polyCount) rebuildPolyRows(s)

        // ----- readout -----
        val r = s.reading
        if (r != null && !s.heldReading && r.noteName != null && r.cents != null) {
            noteText.text = r.noteName
            noteText.alpha = 1f
            centsText.text = getString(R.string.cents_format, r.cents)
            centsText.alpha = 1f
            freqText.text = getString(R.string.detected_freq, r.frequency ?: 0.0)
            targetText.text = getString(R.string.target_freq, r.targetFrequency ?: 0.0)
            centsBar.cents = r.cents.toFloat()
            centsBar.zoneCents = s.toleranceCents.toFloat()
            confidenceBar.confidence = r.confidence
            lowBadge.visibility = if (s.lowNoteMode) View.VISIBLE else View.GONE
            octaveBadge.visibility = if (s.octaveCorrected) View.VISIBLE else View.GONE
            if (r.overpullApplied > 0.0) {
                overpullBadge.visibility = View.VISIBLE
                overpullBadge.text = getString(R.string.overpull_fmt, r.overpullApplied)
            } else overpullBadge.visibility = View.GONE

            guideArrow.set(
                com.example.metrotune.audio.TuneGuidance.of(r.cents, s.toleranceCents))
            btnQuickTone.setText(
                if (s.tonePlaying) R.string.stop_tone else R.string.quick_tone)

            val inTune = abs(r.cents) <= s.toleranceCents
            statusText.setText(when {
                inTune -> R.string.in_tune
                r.cents < 0 -> R.string.too_flat
                else -> R.string.too_sharp
            })
            statusText.setTextColor(ContextCompat.getColor(requireContext(),
                if (inTune) R.color.in_tune else R.color.off_tune))

            // freeze + beat meter
            btnFreeze.setText(
                if (s.frozenTarget != null) R.string.unfreeze else R.string.freeze)
            val frozen = s.frozenTarget
            when {
                frozen != null -> {
                    beatText.text = getString(R.string.beat_frozen,
                        String.format(java.util.Locale.US, "%.1f", frozen),
                        abs((r.frequency ?: 0.0) - frozen))
                    beatText.visibility = View.VISIBLE
                }
                abs(r.cents) <= 25.0 && r.targetFrequency != null -> {
                    beatText.text = getString(R.string.beat_rate,
                        abs((r.frequency ?: 0.0) - r.targetFrequency))
                    beatText.visibility = View.VISIBLE
                }
                else -> beatText.visibility = View.GONE
            }

            // double-stop interval
            val iv = s.interval
            if (iv != null) {
                intervalText.visibility = View.VISIBLE
                intervalText.text = if (iv.justCents != null)
                    getString(R.string.interval_fmt, iv.name, iv.justDeviation, iv.etDeviation)
                else getString(R.string.interval_nojust, iv.name, iv.etDeviation)
            } else intervalText.visibility = View.GONE

            if (r.stringIndex >= 0) {
                val devs = s.headDevs.toMutableList()
                if (r.stringIndex < devs.size) devs[r.stringIndex] = r.cents
                headView.update(
                    s.instrument.strings.map { NoteUtil.noteName(it) },
                    devs, r.stringIndex, s.lockedString, s.toleranceCents)
            } else {
                headView.update(
                    s.instrument.strings.map { NoteUtil.noteName(it) },
                    s.headDevs, s.headActive, s.lockedString, s.toleranceCents)
            }

            r.rawCents?.let {
                graphView.add(it.toFloat())
                strobeView.cents = it.toFloat()
            }
            strobeView.active = true
            strobeView.locked = abs(r.cents) <= 1.0 && r.confidence >= 80

            if (vizOn && !s.simpleMode) {
                r.spectrum?.let { spectrumView.update(it, r.frequency ?: -1.0) }
                harmonicView.update(r.harmonics)
                keyboardView.setActiveMidi(r.midi)
            }
            inharmonicityText.text =
                if (r.inharmonicity > 1e-6)
                    getString(R.string.inharmonicity_fmt, r.inharmonicity) else ""
        } else {
            guideArrow.set(null)
            if (s.heldReading && r != null) {
                noteText.alpha = 0.35f
                centsText.alpha = 0.35f
                statusText.setText(R.string.held)
            } else {
                noteText.text = "–"; centsText.text = "—"
                freqText.text = ""; targetText.text = ""
                centsBar.cents = 0f; confidenceBar.confidence = 0
                lowBadge.visibility = View.GONE
                octaveBadge.visibility = View.GONE
                overpullBadge.visibility = View.GONE
                inharmonicityText.text = ""
                intervalText.visibility = View.GONE
                beatText.visibility = View.GONE
                clearViz()
            }
            statusText.setTextColor(ContextCompat.getColor(requireContext(),
                android.R.color.secondary_text_dark))
            strobeView.active = false
            strobeView.locked = false
        }

        levelMeter.setLevel(rmsToDb(r?.rms ?: 0.0))
        renderPoly(s)

        // ----- audit + drill + pb -----
        auditProgress.visibility = if (s.auditProgress != null) View.VISIBLE else View.GONE
        s.auditProgress?.let { (i, total) ->
            auditProgress.text = getString(R.string.audit_progress, i, total)
        }
        val d = s.drill
        btnDrill.setText(if (d == null) R.string.drill_start else R.string.drill_stop)
        if (d == null) {
            drillHold.text = getString(R.string.drill_hold, 0.0)
            drillHold.alpha = 0.55f
            drillStats.text = ""
        } else {
            drillHold.text = getString(R.string.drill_hold, d.currentMs / 1000.0)
            drillHold.alpha = if (d.holding) 1f else 0.55f
            drillStats.text = getString(R.string.drill_stats,
                secs(d.bestMs), secs(d.inTuneMs), d.attempts)
        }
        drillPb.text = s.drillPb?.let { (midi, best) ->
            if (best > 0) getString(R.string.drill_pb, NoteUtil.noteName(midi), pbWithStars(best))
            else getString(R.string.drill_pb_none)
        } ?: getString(R.string.drill_pb_none)

        // ----- curve capture bar -----
        btnCurve.setText(if (s.curveCapture) R.string.stop else R.string.tuning_curve)
        curveBar.visibility = if (s.curveCapture) View.VISIBLE else View.GONE
        curveText.text = getString(R.string.curve_captured, s.curveCount)

        // ----- setup wizard panel -----
        val wz = s.wizard
        wizardPanel.visibility = if (wz != null) View.VISIBLE else View.GONE
        btnSetup.setText(if (wz != null) R.string.stop else R.string.setup_wizard)
        if (wz != null) {
            wizardInstr.text = (if (wz.phase == 0)
                getString(R.string.setup_play_open, wz.stringIndex + 1)
                else if (wz.kind == 1)
                    getString(R.string.setup_play_fret1, wz.stringIndex + 1, wz.stringName)
                else getString(R.string.setup_play_fret, wz.stringIndex + 1, wz.stringName))
            wizardProgress.text = "${wz.samples} / ${wz.needed}"
            wizardLast.text = wz.results.lastOrNull()?.let { line ->
                if (wz.kind == 1) {
                    val verdict = if (abs(line.devCents) > 3.0)
                        getString(R.string.setup_nut_high) else getString(R.string.setup_nut_ok)
                    getString(R.string.setup_result_ok, line.stringName, line.devCents) +
                        " — " + verdict
                } else if (abs(line.devCents) < 3.0)
                    getString(R.string.setup_result_ok, line.stringName, line.devCents) +
                        " — " + getString(R.string.setup_ok)
                else getString(R.string.setup_result, line.stringName, line.devCents,
                    line.offsetMm, getString(if (line.offsetMm > 0)
                        R.string.setup_toward_bridge else R.string.setup_toward_nut))
            } ?: ""
            btnSetupShare.visibility = if (wz.results.isEmpty()) View.GONE else View.VISIBLE
        }

        // ----- ear panel -----
        btnEar.setText(if (s.ear.active) R.string.ear_stop else R.string.ear_start)
        earSharp.isEnabled = s.ear.awaiting
        earFlat.isEnabled = s.ear.awaiting
        earScore.text = getString(R.string.ear_score,
            s.ear.round, s.ear.correct, s.ear.streak, s.ear.best)

        midiStatus.text = getString(R.string.midi_status_prefix,
            s.bleStatus.ifEmpty { if (s.bleActive) "?" else "" })
        if (switchBle.isChecked != s.bleActive) switchBle.isChecked = s.bleActive

        updateKeepAwake(s.captureRunning || s.tonePlaying)
    }

    private fun secs(ms: Long): String = getString(R.string.drill_hold, ms / 1000.0)

    private fun pbWithStars(bestMs: Long): String {
        val g = DrillRecords.grade(bestMs)
        return "${getString(R.string.drill_hold, bestMs / 1000.0)} " +
            (if (g == 0) "—" else "★".repeat(g))
    }

    private fun rebuildPolyRows(s: TunerViewModel.UiState) {
        polyCount = s.instrument.strings.size
        polyList.removeAllViews()
        polyViews.clear()
        if (!s.instrument.supportsStringModes) return
        val d = resources.displayMetrics.density
        repeat(polyCount) {
            val row = TextView(requireContext()).apply {
                textSize = 18f
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
            }
            polyList.addView(row)
            polyViews.add(row)
        }
    }

    private fun renderPoly(s: TunerViewModel.UiState) {
        if (polyViews.isEmpty()) return
        val byIndex = s.poly.associateBy { it.stringIndex }
        polyViews.forEachIndexed { i, row ->
            val str = byIndex[i]
            if (str == null) {
                row.alpha = 0.35f
                row.text = getString(
                    R.string.string_row, i + 1, NoteUtil.noteName(s.instrument.strings[i]))
                row.setTextColor(ContextCompat.getColor(requireContext(),
                    android.R.color.secondary_text_dark))
            } else {
                row.alpha = 1f
                row.text = getString(R.string.string_row_active, i + 1, str.noteName, str.cents)
                row.setTextColor(ContextCompat.getColor(requireContext(),
                    if (abs(str.cents) <= s.toleranceCents) R.color.in_tune
                    else R.color.off_tune))
            }
        }
    }

    private fun clearViz() {
        spectrumView.update(null, -1.0)
        harmonicView.update(emptyList())
        keyboardView.setActiveMidi(-1)
    }

    private fun rmsToDb(rms: Double): Float =
        if (rms <= 0.0) -100f else (20.0 * log10(rms)).toFloat().coerceIn(-60f, 0f)

    private fun updateKeepAwake(active: Boolean) {
        val a = activity ?: return
        if (active && Settings.keepScreenOn)
            a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else
            a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ---------- practice bar ----------

    private fun updatePracticeBar(running: Boolean) {
        if (!isAdded) return
        practiceBar.visibility = if (running) View.VISIBLE else View.GONE
        if (running) {
            practiceBpm.text = com.example.metrotune.audio.MetronomeEngine.liveBpm.toString()
            buildPracticeDots(com.example.metrotune.audio.MetronomeEngine.beatsPerBar)
        }
    }

    private fun buildPracticeDots(count: Int) {
        practiceDots.removeAllViews()
        practiceBarDots.clear()
        val d = resources.displayMetrics.density
        repeat(count.coerceIn(1, 12)) { i ->
            val dot = View(requireContext()).apply {
                setBackgroundResource(
                    if (i == 0) R.drawable.beat_dot_downbeat else R.drawable.beat_dot)
            }
            practiceDots.addView(dot, LinearLayout.LayoutParams(
                (12 * d).toInt(), (12 * d).toInt()
            ).apply { marginEnd = (6 * d).toInt() })
            practiceBarDots.add(dot)
        }
    }

    private fun flashPracticeBeat(beat: Int) {
        practiceBarDots.forEachIndexed { i, dot -> dot.isSelected = i == beat }
    }

    // ---------- messages ----------

    private fun handle(m: TunerViewModel.Msg) {
        if (!isAdded) return
        when (m) {
            is TunerViewModel.Msg.StringInTune -> Toast.makeText(requireContext(),
                getString(R.string.string_in_tune, m.index + 1, m.name),
                Toast.LENGTH_SHORT).show()
            TunerViewModel.Msg.AllTuned -> {
                Toast.makeText(requireContext(), R.string.all_strings_tuned,
                    Toast.LENGTH_LONG).show()
                checkAutoAdvance.isChecked = false
            }
            is TunerViewModel.Msg.Calibrated -> Toast.makeText(requireContext(),
                getString(R.string.calibrated_to, m.a4), Toast.LENGTH_LONG).show()
            TunerViewModel.Msg.CalibrateTimeout -> Toast.makeText(requireContext(),
                R.string.calibrate_timeout, Toast.LENGTH_SHORT).show()
            TunerViewModel.Msg.MicBusy -> Snackbar.make(requireView(), R.string.mic_busy,
                Snackbar.LENGTH_LONG).setAction(R.string.retry) { vm.startCapture() }.show()
            TunerViewModel.Msg.MicDisconnected -> {
                Toast.makeText(requireContext(), R.string.mic_disconnected,
                    Toast.LENGTH_LONG).show()
                buildMicSpinner()
            }
            is TunerViewModel.Msg.NewRecord -> Toast.makeText(requireContext(),
                getString(R.string.record_new, m.note,
                    getString(R.string.drill_hold, m.ms / 1000.0)), Toast.LENGTH_LONG).show()
            TunerViewModel.Msg.DrillNoTarget -> Toast.makeText(requireContext(),
                R.string.drill_no_target, Toast.LENGTH_SHORT).show()
            is TunerViewModel.Msg.ShareAudit -> shareText(m.subject, m.csv)
            TunerViewModel.Msg.CustomInvalid -> Toast.makeText(requireContext(),
                R.string.ci_invalid, Toast.LENGTH_LONG).show()
            TunerViewModel.Msg.FocusDenied -> Toast.makeText(requireContext(),
                R.string.audio_focus_lost, Toast.LENGTH_SHORT).show()
            is TunerViewModel.Msg.SessionStarted -> Toast.makeText(requireContext(),
                getString(R.string.session_started, m.count, m.first),
                Toast.LENGTH_LONG).show()
            is TunerViewModel.Msg.SessionAdvance -> Toast.makeText(requireContext(),
                getString(R.string.session_now, m.name, m.index, m.count),
                Toast.LENGTH_LONG).show()
            TunerViewModel.Msg.SessionDone -> Toast.makeText(requireContext(),
                R.string.session_done, Toast.LENGTH_LONG).show()
            TunerViewModel.Msg.SessionCancelled -> Toast.makeText(requireContext(),
                R.string.session_cancelled, Toast.LENGTH_SHORT).show()
            is TunerViewModel.Msg.RecallPitch -> Snackbar.make(requireView(),
                getString(R.string.recall_msg, vm.ui.value.instrument.name, m.a4),
                Snackbar.LENGTH_LONG)
                .setAction(getString(R.string.recall_apply, m.a4)) { vm.applyRecalledA4(m.a4) }
                .show()
            TunerViewModel.Msg.WizardDone -> {
                Toast.makeText(requireContext(), R.string.setup_done,
                    Toast.LENGTH_LONG).show()
                vm.setupReport()?.let { (subject, csv) -> shareText(subject, csv) }
            }
            is TunerViewModel.Msg.SuggestTuning -> Snackbar.make(requireView(),
                getString(R.string.suggest_msg, m.name), Snackbar.LENGTH_LONG)
                .setAction(getString(R.string.suggest_switch)) {
                    vm.presets().firstOrNull { it.id == m.presetId }
                        ?.let { vm.selectInstrument(it) }
                }
                .show()
            is TunerViewModel.Msg.EarFeedback -> {
                earFeedback.text = if (m.correct)
                    getString(R.string.ear_right, m.actualCents, m.streak)
                else getString(R.string.ear_wrong, m.actualCents)
                earFeedback.setTextColor(ContextCompat.getColor(requireContext(),
                    if (m.correct) R.color.in_tune else R.color.off_tune))
            }
        }
    }

    // ---------- dialogs ----------

    private fun showSessionDialog() {
        val presets = vm.presets().filter { it.supportsStringModes }
        val names = presets.map { it.name }.toTypedArray()
        val saved = Settings.sessionInstruments.split(",").filter { it.isNotBlank() }.toSet()
        val checked = BooleanArray(presets.size) { presets[it].id in saved }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.session_pick)
            .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.session_start) { _, _ ->
                val ids = presets.filterIndexed { i, _ -> checked[i] }.map { it.id }
                if (ids.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.session_empty,
                        Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Settings.sessionInstruments = ids.joinToString(",")
                vm.startSession(ids)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun onSetupClicked() {
        if (vm.ui.value.wizard != null) { vm.cancelWizard(); return }
        val kinds = arrayOf(
            getString(R.string.setup_kind_saddle), getString(R.string.setup_kind_nut))
        var kind = 0
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.setup_kind)
            .setSingleChoiceItems(kinds, 0) { _, which -> kind = which }
            .setPositiveButton(R.string.continue_label) { _, _ -> showScaleDialog(kind) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showScaleDialog(kind: Int) {
        val d = resources.displayMetrics.density
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.setup_scale_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
            setText(InstrumentLibrary.scaleMm(vm.ui.value.instrument.id)?.toString() ?: "")
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.setup_wizard)
            .setView(FrameLayout(requireContext()).apply {
                setPadding((24 * d).toInt(), (8 * d).toInt(), (24 * d).toInt(), 0)
                addView(input)
            })
            .setPositiveButton(R.string.save) { _, _ ->
                val mm = input.text.toString().toDoubleOrNull()
                if (mm != null && mm in 200.0..2000.0) vm.startWizard(mm, kind)
                else Toast.makeText(requireContext(), R.string.setup_scale_needed,
                    Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCurveSaveDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.curve_name_hint); setSingleLine()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.curve_save)
            .setView(wrapInput(input))
            .setPositiveButton(R.string.save) { _, _ ->
                val n = input.text.toString().trim()
                if (n.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.ci_invalid,
                        Toast.LENGTH_SHORT).show()
                } else {
                    vm.finishCurveCapture(n)
                    Toast.makeText(requireContext(), R.string.curve_saved,
                        Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCurvePicker() {
        val curves = vm.curves()
        val col = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        val d = resources.displayMetrics.density
        fun row(text: String, onTap: () -> Unit, onLong: (() -> Unit)? = null) {
            col.addView(TextView(requireContext()).apply {
                this.text = text; textSize = 16f; minHeight = (44 * d).toInt()
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(android.R.attr.selectableItemBackground)
                setOnClickListener { onTap() }
                onLong?.let { l -> setOnLongClickListener { l(); true } }
            })
        }
        row(getString(R.string.curve_none), { vm.useRailsbackCurve() })
        curves.forEach { c ->
            row("${c.name} · ${c.points.size} notes",
                { vm.selectCurve(c.id) },
                {
                    AlertDialog.Builder(requireContext())
                        .setMessage(getString(R.string.ci_delete_confirm, c.name))
                        .setPositiveButton(R.string.delete) { _, _ -> vm.deleteCurve(c.id) }
                        .setNegativeButton(R.string.cancel, null).show()
                })
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.curve_pick)
            .setView(ScrollView(requireContext()).apply {
                addView(col)
                setPadding((24 * d).toInt(), (8 * d).toInt(), (24 * d).toInt(), 0)
            })
            .setNeutralButton(R.string.curve_capture_new) { _, _ -> vm.startCurveCapture() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showHealth(rows: List<TunerViewModel.HealthRow>) {
        if (!isAdded) return
        val col = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        if (rows.isEmpty()) {
            col.addView(TextView(requireContext()).apply {
                text = getString(R.string.health_empty); alpha = 0.6f; textSize = 14f
            })
        } else rows.forEach { r ->
            col.addView(TextView(requireContext()).apply {
                val line = getString(R.string.health_row, r.name, r.avg, r.last, r.count)
                text = if (r.flagged) "⚠ " + line else line
                textSize = 15f
                setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
            })
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.health_title, vm.ui.value.instrument.name))
            .setView(ScrollView(requireContext()).apply {
                addView(col)
                setPadding((24 * resources.displayMetrics.density).toInt(),
                    (8 * resources.displayMetrics.density).toInt(),
                    (24 * resources.displayMetrics.density).toInt(), 0)
            })
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showDetectResult() {
        val top = vm.detectTop()
        if (top == null) {
            Toast.makeText(requireContext(), R.string.detect_none, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.detect_title)
            .setMessage(top.name)
            .setPositiveButton(R.string.suggest_switch) { _, _ ->
                vm.presets().firstOrNull { it.id == top.presetId }?.let { vm.selectInstrument(it) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCustomEditor() {
        val d = resources.displayMetrics.density
        val name = EditText(requireContext()).apply {
            hint = getString(R.string.ci_name_hint); setSingleLine()
        }
        val spec = EditText(requireContext()).apply {
            hint = getString(R.string.ci_strings_hint); setSingleLine()
        }
        val col = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * d).toInt(), (8 * d).toInt(), (24 * d).toInt(), 0)
            addView(name); addView(spec)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.ci_title)
            .setView(col)
            .setPositiveButton(R.string.save) { _, _ ->
                if (!vm.saveCustom(name.text.toString(), spec.text.toString()))
                    handle(TunerViewModel.Msg.CustomInvalid)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteCustom(p: InstrumentPreset) {
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.ci_delete_confirm, p.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                vm.deleteCustom(p.id)
                if (vm.ui.value.instrument.id == p.id)
                    vm.selectInstrument(InstrumentLibrary.CHROMATIC)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRecords(records: List<DrillRecords.Entry>) {
        if (!isAdded) return
        val col = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        if (records.isEmpty()) {
            col.addView(TextView(requireContext()).apply {
                text = getString(R.string.records_empty); alpha = 0.6f; textSize = 14f
            })
        } else {
            records.forEach { e ->
                col.addView(TextView(requireContext()).apply {
                    text = getString(R.string.records_row,
                        NoteUtil.noteName(e.midi),
                        getString(R.string.drill_hold, e.bestMs / 1000.0),
                        if (DrillRecords.grade(e.bestMs) == 0) "—"
                        else "★".repeat(DrillRecords.grade(e.bestMs)),
                        e.attempts)
                    textSize = 15f
                    setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
                })
            }
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.records_title)
            .setView(ScrollView(requireContext()).apply {
                addView(col)
                setPadding((24 * resources.displayMetrics.density).toInt(),
                    (8 * resources.displayMetrics.density).toInt(),
                    (24 * resources.displayMetrics.density).toInt(), 0)
            })
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showOffsetEditor(stringIndex: Int) {
        val strings = vm.ui.value.instrument.strings
        val name = NoteUtil.noteName(strings.getOrNull(stringIndex) ?: return)
        val d = resources.displayMetrics.density
        val value = TextView(requireContext()).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
        }
        fun refresh() {
            value.text = getString(R.string.cents_format, vm.userOffset(stringIndex))
        }
        fun adjust(delta: Double) {
            vm.adjustUserOffset(stringIndex, vm.userOffset(stringIndex) + delta)
            refresh()
        }
        val row = LinearLayout(requireContext()).apply {
            gravity = Gravity.CENTER
            addView(Button(requireContext()).apply {
                text = "−0.5"; setOnClickListener { adjust(-0.5) }
            })
            addView(value)
            addView(Button(requireContext()).apply {
                text = "+0.5"; setOnClickListener { adjust(+0.5) }
            })
        }
        val col = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * d).toInt(), (8 * d).toInt(), (24 * d).toInt(), 0)
            addView(TextView(requireContext()).apply {
                text = getString(R.string.offset_hint); alpha = 0.6f; textSize = 12f
            })
            addView(row)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.my_offsets) + " · " + name)
            .setView(col)
            .setNeutralButton(R.string.offset_reset) { _, _ ->
                vm.adjustUserOffset(stringIndex, 0.0)
            }
            .setPositiveButton(R.string.ok, null)
            .show()
        refresh()
    }

    private fun showTemperamentEditor() {
        val d = resources.displayMetrics.density
        val name = EditText(requireContext()).apply {
            hint = getString(R.string.preset_name_hint); setSingleLine()
        }
        val bases = Temperaments.ALL
        val baseSpin = Spinner(requireContext())
        val offs = DoubleArray(12)
        val values = Array(12) { TextView(requireContext()) }
        val fifths = FifthsCircleView(requireContext())

        fun load(i: Int) { for (k in 0..11) offs[k] = bases[i].centsFor(k, 0) }
        fun fmt(v: Double) = String.format("%+.1f", v)
        fun refresh() {
            for (k in 0..11) values[k].text = fmt(offs[k])
            fifths.setDeviations(
                Temperaments.fifthDeviations(bases[baseSpin.selectedItemPosition], 0))
        }

        val grid = android.widget.GridLayout(requireContext()).apply {
            columnCount = 4; rowCount = 6
            for (k in 0..11) {
                addView(TextView(requireContext()).apply { text = NOTE_NAMES[k] })
                addView(Button(requireContext()).apply {
                    text = "−"
                    setOnClickListener {
                        offs[k] = ((offs[k] - 0.5) * 2).roundToInt() / 2.0
                            .coerceIn(-100.0, 100.0)
                        refresh()
                    }
                })
                addView(values[k].apply {
                    typeface = android.graphics.Typeface.MONOSPACE
                })
                addView(Button(requireContext()).apply {
                    text = "+"
                    setOnClickListener {
                        offs[k] = ((offs[k] + 0.5) * 2).roundToInt() / 2.0
                            .coerceIn(-100.0, 100.0)
                        refresh()
                    }
                })
            }
        }
        val col = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * d).toInt(), (8 * d).toInt(), (24 * d).toInt(), 0)
            addView(name)
            addView(TextView(requireContext()).apply {
                text = getString(R.string.temp_base); alpha = 0.6f; textSize = 13f
            })
            addView(baseSpin)
            addView(ScrollView(requireContext()).apply { addView(grid) },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (300 * d).toInt()))
            addView(fifths, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (210 * d).toInt()))
        }
        baseSpin.setup(bases.map { it.name }) { load(it); refresh() }
        load(0); refresh()

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.temp_editor)
            .setView(col)
            .setPositiveButton(R.string.save) { _, _ ->
                if (vm.saveCustomTemperament(name.text.toString(), offs.toList()))
                    Toast.makeText(requireContext(), R.string.temp_saved,
                        Toast.LENGTH_SHORT).show()
                else handle(TunerViewModel.Msg.CustomInvalid)
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

    private fun shareText(subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching {
            startActivity(Intent.createChooser(send, getString(R.string.share_analysis)))
        }
    }

    private fun shareCsv() {
        val snap = vm.csvSnapshot()
        if (snap == null) {
            Toast.makeText(requireContext(), R.string.no_analysis, Toast.LENGTH_SHORT).show()
            return
        }
        shareText(snap.first, snap.second)
    }

    private fun maybeEnableBle() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val perms = arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT)
            if (perms.any {
                    ContextCompat.checkSelfPermission(requireContext(), it) !=
                        PackageManager.PERMISSION_GRANTED
                }) {
                switchBle.isChecked = false
                blePermLauncher.launch(perms)
                return
            }
        }
        vm.setBleEnabled(true)
        if (!vm.ui.value.bleActive) {
            switchBle.isChecked = false
            midiStatus.setText(R.string.midi_unavailable)
        }
    }

    private fun requestMicWithRationale() {
        if (Settings.micRationaleShown) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.mic_rationale_title)
            .setMessage(R.string.mic_rationale_message)
            .setPositiveButton(R.string.continue_label) { _, _ ->
                Settings.micRationaleShown = true
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- lifecycle ----------

    override fun onResume() {
        super.onResume()
        buildMicSpinner()
        if (vm.ui.value.micGranted && !vm.ui.value.tonePlaying) vm.startCapture()
    }

    override fun onPause() {
        super.onPause()
        vm.pauseForBackground()   // stops tone; keeps capture only for BLE streaming
        vm.persistOnPause()
        updateKeepAwake(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .unregisterAudioDeviceCallback(deviceCallback)
        com.example.metrotune.audio.MetronomeEngine.removeListener(metroListener)
    }
}
