# WHISPER / QUIET-VOICE DETECTION

Kamran's order (Oct 14 2026): even when he speaks softly or whispers, Baloch
must catch every word — and if he whispers, **Baloch whispers back** (replies
softly, context-aware).

Implementation: `app/src/main/java/com/baloch/ai/voice/SensitiveAudioCapture.kt`
— one self-contained class, pure Kotlin + Android SDK. VoiceInput.kt and
UtteranceCapture.kt are untouched; the parent wires this into the audio path
in the final pass.

## How quiet-voice detection works

Four stages, in order, inside every `readChunk()` (100 ms of 16 kHz mono
16-bit PCM):

**1. Software mic-gain + soft limiter** — `applyGainAndLimit()`

The raw mic signal is multiplied by `gain` (default **2.0x**, configurable
1x–4x, clamped in the setter). A whisper is a tiny electrical wobble; 2x
lifts it ~6 dB into a range the rest of the chain can work with.

Loud sounds get the opposite problem: gain would push them past the 16-bit
ceiling (±32767) and **hard-clip** — the waveform's tops get chopped flat,
which sounds harsh and confuses recognisers. So every sample goes through a
tanh soft limiter:

    out = 30000 × tanh((in × gain) / 30000)

For small inputs tanh(x) ≈ x (untouched); near ±30000 the curve flattens and
peaks get *rounded* instead of chopped. A loud laugh right after a whisper
stays clean instead of distorting.

**2. RMS normalisation** — `normalise()`

Every chunk is scaled so its loudness (RMS — the average *power* of the
signal, which tracks perceived volume far better than peak amplitude) lands
near a fixed target of **~6000 (≈ −14.7 dBFS)**:

- Whisper / quiet speech (below target) → scaled UP toward the target.
- Normal/loud speech (already at or above target) → **scale 1.0, untouched**
  — the ceiling that stops loud environments from blowing out.
- Near-silence (below the noise floor, RMS < 40) → untouched — lifting pure
  noise would just amplify hiss.
- Scale capped at **16x** so a freak faint chunk can't explode into noise.

**3. Built-in Android effects** — `attachBuiltInEffects()`

`NoiseSuppressor` (eats steady drone: fan, AC, traffic) and
`AutomaticGainControl` (the OS's own volume-riding loop) are attached to the
AudioRecord session, each guarded by `isAvailable()` and try/catch. If the
device doesn't offer them, capture works exactly as before — effects are a
bonus stage, never a requirement.

**4. Energy gating** — `captureUtterance()`

`suspend fun captureUtterance(timeoutMs: Long = 6000): Utterance` waits for
the first chunk whose RMS crosses the energy gate (speech starts), then
records until continuous below-gate audio exceeds the silence timeout (speech
ends), with a hard overall cap of `timeoutMs`. Silence before/after is
discarded, never fed to the recogniser.

## Quiet voice mode (default ON)

`var quietMode: Boolean` — persisted in SharedPreferences `"quiet_voice_mode"`.
When ON, two things change:

| | Normal mode | Quiet mode |
|---|---|---|
| Energy gate | RMS ≥ 350 (≈ −33 dBFS) | RMS ≥ 120 (≈ −49 dBFS) — ~9 dB more sensitive |
| Silence timeout | 800 ms | **1500 ms** — whispers have longer natural pauses; a short timeout would chop one whispered sentence into two half-utterances |

**Honest trade-off:** the lower gate means background noise (keyboard, AC
hum, a TV nearby) is more likely to trip the gate too — more false wakes in
noisy rooms. In a quiet room it is pure win. If false wakes ever annoy,
Kamran flips quiet mode OFF and normal thresholds apply.

## "If he whispers, Baloch whispers back"

Whisper classification: `fun isWhisper(samples: ShortArray): Boolean` —
input RMS **below 250 (≈ −42 dBFS) but above the noise floor (40)** is the
whisper band: genuinely soft, but voice rather than room hiss. The threshold
sits deliberately *below* the quiet energy gate (120), so a whisper both
wakes the gate and gets classified as a whisper.

`captureUtterance()` takes a **majority vote** over the utterance's speech
chunks (≥ half the chunks whisper-classified → whisper) and returns it as
`Utterance.wasWhisper`. Single-chunk flips don't decide — only the whole
utterance does.

**Parent seam (the wiring contract):** when `Utterance.wasWhisper == true`,
the parent MUST reply softly — lower the TTS/media-stream volume for that
one reply instead of VoiceOutput's usual full-volume boost. Suggested:
`voiceOutput` speaks at ~35–45% of the saved volume for the whisper reply,
then restores. The class itself never touches TTS; it only reports the
verdict.

```kotlin
val capture = SensitiveAudioCapture(context)
val utterance = capture.captureUtterance(timeoutMs = 8000)
val text = recogniser.transcribe(utterance.pcm)      // recogniser-ready PCM
if (utterance.wasWhisper) voiceOutput.speakSoftly(text) // parent implements
else voiceOutput.speak(text)
```

Push-style alternative: set `whisperListener: WhisperListener` and call
`readChunk()` in a loop — every chunk arrives as
`onAudioChunk(pcm, rmsDb, isWhisper)` with processed PCM, raw dB, and the
chunk's whisper verdict. `lastInputRmsDb`, `lastWasWhisper`, and `dbString()`
(`"-41.2 dB | whisper | quiet=true"`) are there for logcat tuning.

## Tuning notes

- **Gain cap (4x):** above 4x the mic's own electronic noise grows faster
  than the voice — 4x is the sane ceiling, enforced in the setter.
- **RMS target (6000):** recogniser sweet spot; loud-enough without kissing
  the limiter. Only chunks *below* it get scaled.
- **Whisper threshold (250):** calibrate with `dbString()` in logcat —
  whisper a command in the target room, note the dB, and nudge the constant
  so whispers land inside the band and room noise stays below it.
- **If whispers still miss:** raise `gain` toward 3–4x first (cheap, no
  threshold changes). If false wakes appear instead, quiet mode OFF or raise
  `QUIET_ENERGY_THRESHOLD`.
- `AudioRecord` uses **2x the device minimum buffer** — enough headroom for
  the 100 ms read cadence without adding latency (UtteranceCapture uses 4x;
  this path is tuned leaner).

## Honest limits

- **Extreme whisper + loud background** (whispering next to a TV, in traffic,
  on a windy street): physics wins. The whisper's energy sits *inside* the
  noise's energy and no gain stage can separate them — NoiseSuppressor only
  eats *steady* drone, not speech-like noise. Expect misses there; normal
  voice still works.
- **Far-field whispers** (phone across the room): inverse-square law — the
  mic gets almost nothing. 4x gain can't recover what isn't there.
- Whisper *recognition accuracy* ultimately belongs to the recogniser
  (on-device or cloud): this class guarantees the whisper reaches it loud,
  clean, and correctly gated — it can't make the recogniser understand
  phonemes it was never trained on.
- Built-in effects are device-dependent: some OEMs only enable
  NoiseSuppressor/AGC on `VOICE_RECOGNITION` source; we use `MIC` for widest
  compatibility and degrade gracefully when the effect is absent.
