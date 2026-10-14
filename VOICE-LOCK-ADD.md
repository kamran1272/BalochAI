# VOICE LOCK — Speaker Authentication

Kamran's security order: **Baloch obeys ONLY Kamran's voice.** Strangers get
refused, nothing executes. 100% on-device — no cloud, no network, no
downloaded ML models.

## How it works (the MFCC pipeline, in 5 bullets)

1. **Capture** — `UtteranceCapture` records raw mic audio: 16 kHz, mono,
   16-bit PCM via `AudioRecord` (RECORD_AUDIO permission already declared).
2. **Pre-emphasis + framing** — each recording is boosted at high frequencies
   (×0.97 pre-emphasis, like turning up treble), then chopped into 25 ms
   frames stepping every 10 ms, each faded at the edges with a Hamming window.
3. **FFT + mel filterbank** — a hand-rolled 512-point radix-2 FFT turns every
   frame into its frequency content; 26 triangular filters spaced on the
   perceptual *mel* scale (300–8000 Hz, the way human ears hear pitch) measure
   energy per band, then the log is taken (log matches perceived loudness).
4. **DCT → 13 MFCCs** — a Discrete Cosine Transform compresses the 26
   log-energies into 13 compact coefficients: the frame's tonal fingerprint.
5. **Voiceprint** — the utterance vector = mean of the 13 MFCCs over *voiced*
   frames only (energy gate: frames louder than 10% of the loudest frame —
   silence carries no identity) **plus** the mean of their frame-to-frame
   deltas (speech rhythm), L2-normalised → 26 dimensions. Enrollment averages
   3 samples into the stored voiceprint; verification accepts when the cosine
   distance to the voiceprint is ≤ **0.30** (similarity ≥ 0.70).

All DSP is hand-written in `voice/VoiceLockManager.kt`, stage by stage, with
plain-language comments — no third-party DSP libraries.

## Enrollment steps

1. Open the **Voice Lock setup screen** (`ui/VoiceLockSetupScreen.kt`).
2. Tap **Record** on each of the 3 sample slots and say the fixed phrase:
   **"Hey Baloch, it is me"** (~2.5 s each). Bad samples (mic failure / no
   speech detected) return null and the slot stays open — a partial
   enrollment is never stored.
3. Set a **4-digit PIN** (+ confirmation). Stored as SHA-256 hash only,
   compared in constant time.
4. Flip the **Voice Lock toggle** to arm it. Optionally enable the
   **biometric app lock** (fingerprint/face/device credential on app open —
   off by default).

## PIN fallback policy

- Voice verification fails **twice in a row**, or the mic is unavailable →
  Baloch asks for the PIN (`Replies.VOICE_LOCK_PIN_PROMPT`).
- **Never executes without voice-or-PIN.** There is no silent bypass path.
- The PIN is a local second factor (10,000 combinations — honest limits
  below), not a bank vault.

## Disable-protection rule

Turning Voice Lock OFF is security-sensitive, so `matchVoiceLock()` returns
`VoiceLockCmd.DisableRequest` — a *request*, never an order. The parent
**must** pass identity proof before calling `setEnabled(false)`:

- `VoiceLockManager.canDisable(ctx)` — captures a fresh utterance and
  verifies it against the voiceprint (the *voice* half), **OR**
- `PinManager.checkPin(pin)` — the PIN typed into the UI (the *PIN* half).

A stranger saying "voice lock band karo" must never disable the lock — that
would defeat its entire purpose.

## Honest limits

- Reliably **rejects casual strangers** (a different voice won't match the
  voiceprint).
- **NOT spoof-proof against recordings** — a clear recording of Kamran's voice
  played through a good speaker can fool it. No liveness detection here.
- The **PIN + optional biometric app lock are the hard guarantees**;
  Voice Lock is the convenient front door, the PIN is the deadbolt.
- Threshold `VERIFY_THRESHOLD = 0.30`: lower = stricter (fewer false accepts,
  more false rejects when ill/tired/in noise); higher = more lenient. Tune
  with real on-device recordings, not theory.

## Files

| File | Role |
|---|---|
| `voice/VoiceLockManager.kt` | MFCC pipeline, enroll/verify, DataStore `voice_lock` |
| `voice/UtteranceCapture.kt` | AudioRecord wrapper (enrollment + verification) |
| `voice/PinManager.kt` | 4-digit PIN, SHA-256, constant-time compare |
| `voice/VoiceLockIntents.kt` | Trilingual voice-lock commands incl. Roman Urdu |
| `voice/BiometricLock.kt` | Optional biometric/device-credential app lock |
| `voice/VoiceInput.kt` | + `captureUtterance()` (existing API untouched) |
| `ui/VoiceLockSetupScreen.kt` | Holographic-cyan setup UI (state + callbacks; parent wires) |
| `brain/Strings.kt` | `Replies.VOICE_LOCK_*` trilingual messages |
