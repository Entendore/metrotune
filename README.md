# MetroTune

Metronome, studio tuner, and practice tracker for Android (Kotlin, minSdk 24).
**Fully offline**: no INTERNET permission, no analytics, no crash reporting, no
Play-services dependencies. All data stays on the device; the only exports are
user-initiated (backup JSON, CSV reports) via the system share/save sheets.

## Features

**Tuner** — 88 instrument presets + custom instruments, chromatic / per-string /
poly modes, FFT-accelerated YIN + multi-hypothesis tracking + harmonic-sieve
octave repair + confidence gating, adaptive frame size for low notes, strobe,
spectrum, harmonic profile (incl. mallet partial series), inharmonicity, auto
octave correction, 6 built-in + user temperaments, sweetened tunings, Railsback
stretch + captured tuning curves, pitch-raise overpull, match-band A4
calibration, per-instrument pitch memory, personal per-string offsets,
intonation setup wizard (saddle mm + nut diagnosis), string stability
diagnostics + health history, double-stop interval check (just vs equal),
tuning detection, multi-instrument sessions, intonation drill with per-note
records, ear training, USB/BT mic selection, BLE-MIDI note/pitch-bend output.
Simple/Pro progressive disclosure.

**Metronome** — 30–250 BPM, beat-pattern editor, subdivisions, tap tempo,
fast ± buttons, haptics, click volume, downbeat flash, pendulum, count-in,
foreground-service background playback, tempo ramp & gap drills, polyrhythm
(3:2 … 5:3), saved tempo presets, setlist runner with per-part patterns,
MIDI clock out (BLE), home-screen widget.

**Practice** — daily goal, session log, per-day notes, 7-day chart, long-tone
records — Room-persisted, auto-imported from legacy prefs, included in the
JSON backup.

## Build & test (all local)
./gradlew testDebugUnitTest lintDebug assembleDebug

Optional local tooling: `./gradlew spotlessCheck detekt`
(format/lint — no CI gate; remove the plugins if unwanted).

## Architecture
- `audio/` — engines (MetronomeEngine, TunerEngine, BleMidiServer,
  PracticeSessionManager facade) and pure-Kotlin DSP/math, each pure piece
  unit-tested (17 JVM test files). Real-time audio runs on dedicated threads
  by design (AudioTrack/AudioRecord blocking calls must not share coroutine
  dispatchers); coroutines/StateFlow own the control plane.
- `data/` — Room v2 (practice days, sessions, drill records, string health)
  + repositories + one-time prefs importer.
- `di/` — Hilt (SingletonComponent).
- `ui/` — HiltViewModels per tab + custom Canvas views; fragments render only.
- `MetronomeService` — foreground service (screen-off playback), drives the
  home-screen widget.

## Regenerating this project
Run the generator scripts in order: `metrotune_01…09` (see each header), or
simply `bash run_all.sh`. Then:
    gradle wrapper --gradle-version 8.7
    ./gradlew spotlessApply && ./gradlew testDebugUnitTest lintDebug assembleDebug
