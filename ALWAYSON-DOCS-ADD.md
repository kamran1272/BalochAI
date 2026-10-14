# Always-On Voice — README section

## How it works

Baloch listens for **"hey baloch"** even with the phone **locked / screen off**,
fully hands-free. Typed chat is untouched — it stays first-class on the main
screen.

Pipeline (`com.baloch.ai.service`):

1. **BalochListenerService** — foreground service (`foregroundServiceType="microphone"`)
   with a persistent low-importance notification
   ("◈ Baloch is listening — say 'hey baloch'") and a `PARTIAL_WAKE_LOCK`.
   Started/stopped via `BalochListenerService.start(ctx)` / `.stop(ctx)`;
   the "Always listening" toggle persists in SharedPreferences
   (`always_listening`, default **ON** — Kamran's order).
2. **WakeWordEngine** — offline keyword spotting with Vosk
   (`org.vosk:vosk-android:0.3.47`). A grammar-constrained recognizer
   (`["hey baloch", "[unk]"]`) decodes 16 kHz mic audio on a dedicated
   thread; on a match it fires `onWakeWord()`. An energy gate (lowered in
   `quietMode`, default ON) keeps silence out of the decoder and lets
   whispers through.
3. On wake: chime → the command utterance is captured **two ways at once** —
   raw 16 kHz PCM (`UtteranceCapture`, for the RMS gate + speaker
   verification) and transcribed text (`VoiceInput`'s SpeechRecognizer in
   the active language ur-PK / hi-IN / en-US). Both read the mic
   concurrently; Android allows multiple AudioRecord clients on one input.
4. **Voice lock first**: if the voice lock is enabled, the raw audio is
   verified against Kamran's voiceprint. A failed check speaks a refusal
   and executes **nothing** (fail-closed).
5. **Risky commands**: screen off → Baloch asks out loud
   ("Should I …? Say yes or no.", trilingual via `VerbalConfirm`; anything
   unrecognized = **no**). Screen on → a full-screen-intent notification
   opens MainActivity's confirm dialog (`ACTION_CONFIRM_RISKY` seam).
6. The pipeline executes the command and the reply is spoken in the active
   language with the locked male voice.

Seams wired by the parent in the final pass (`HandsFreeSeams.kt`):
`HandsFreePipeline.execute` → Orchestrator, `verifySpeaker` /
`isVoiceLockEnabled` → VoiceLockManager. Until wired, a wake word gets an
honest "my command pipeline isn't connected yet" and nothing else happens.

## One-time Vosk model download (~50 MB)

On the very first run the engine downloads `vosk-model-small-en-us-0.15`
(or the `-0.22` fallback URL) from `https://alphacephei.com/vosk/models`
into the app's private files dir (`filesDir/vosk-model`), with progress in
the persistent notification ("downloading voice model… 42%"), then unzips it
(zip-slip guarded) and deletes the zip. Requirements: ~160 MB free storage,
checked before downloading. If the download fails, the engine disables
itself with an honest status instead of pretending to listen.

Note: the model is English-only and is used **only for the "hey baloch"
keyword**. Actual commands are transcribed by Android's SpeechRecognizer in
ur-PK / hi-IN / en-US, so Urdu/Hindi/Roman Urdu commands work normally.

## Honest battery note

> **Always-on listening uses ~3–5% battery per hour. Turn off to save
> battery.** (Toggle: Settings → Always listening.)

## Honest DSP note

True zero-battery hotword detection (a DSP chip listening while the phone
sleeps) is reserved for Google Assistant and a handful of system apps —
third-party apps cannot access it. Baloch's always-on voice is a
**foreground-service mic listener**: the mic stays open and a low-power
CPU loop decodes audio. That is why the battery cost above is real and why
the persistent notification is mandatory (Android 14 kills mic background
work without it). Detection quality is deliberately prioritized over
battery: the energy gate is conservative so a wake word is never sacrificed
to save CPU.

## OEM whitelisting (required, or the OS will kill the listener)

- **Xiaomi / Redmi / POCO:** Settings → Apps → Manage apps → Baloch →
  **Autostart → enable**; then Battery saver → **No restrictions**.
- **Oppo / Vivo / Realme:** Settings → Battery → App battery management →
  Baloch → **Allow background activity** (and enable auto-start in the
  app's App info → Battery usage).
- **Samsung:** Settings → Battery → Background usage limits → **Never
  sleeping apps** → add Baloch. Also tap the "lock" icon on Baloch in the
  Recents screen so it isn't swiped away.
- **All:** Settings → Apps → Baloch → Battery → **Unrestricted**.

## Permissions

- **Microphone** (`RECORD_AUDIO`, runtime): without it the service posts a
  one-time "Baloch can't listen yet" notification and stops — it never
  spins silently.
- **Notifications** (`POST_NOTIFICATIONS`, runtime): required for the
  persistent "listening" notification; on Android 14 a mic foreground
  service **cannot run without a visible notification**.
- **Battery-optimisation exemption** (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`):
  prompt flow for the settings screen —
  1. `PowerManager.isIgnoringBatteryOptimizations(packageName)` — if true, done.
  2. Else fire `Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)`
     with `data = Uri.parse("package:$packageName")`.
  3. Explain first (the OS shows its own scary dialog): "Baloch needs this
     so always-on listening isn't killed while your phone sleeps."
- **Wake lock** (`WAKE_LOCK`, normal permission): held only while the
  service runs; released on stop/destroy.
- **Full-screen intent** (`USE_FULL_SCREEN_INTENT`, Android 14): lets the
  risky-command confirmation pop over the lock screen when the display is on.
