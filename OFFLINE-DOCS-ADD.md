# OFFLINE-FIRST — Offline Capability Matrix

Kamran's order (Oct 14 2026): **"Baloch must work fully WITHOUT mobile data — unlike cloud assistants, no-data = no problem."**

## The promise (graceful degradation)

- Baloch **never** crashes, hangs, or shows a dead error screen when data drops.
- Every online-only feature checks `ConnectivityGate.isOnline()` **first**.
- When offline, a feature does exactly one honest thing: run its offline behaviour,
  serve **cached** data with a "cached" label, skip with one spoken line, or queue
  a network-constrained retry for later. Silent gaps and spinners are bugs.
- No audio, command text, or personal data ever leaves the phone for offline features.

## Capability matrix

### [offline] — works with zero data

- **Voice I/O** — Vosk STT on-device (`voice/OfflineSpeechInput`, the PRIMARY
  command path) + on-device TTS (`voice/VoiceOutput`) in ur-PK / hi-IN / en-US.
- **All device controls** — Wi-Fi, Bluetooth, torch, volume, DND, airplane mode,
  brightness, app launching (`engine/DeviceActions`).
- **Alarms, timers, scheduling** — exact `AlarmManager` one-shots + WorkManager
  periodic work; scheduled tasks fire in BOTH Manual and JARVIS mode.
- **Notes & memory** — Room/SQLCipher store, encrypted at rest; auto-extract,
  recall, backup all local.
- **Voice lock** — MFCC voiceprint enrollment + per-command verification on-device;
  PIN/biometric fallback.
- **Gestures** — camera hand-gesture control (MediaPipe TFLite, on-device) + sensors.
- **Routines / macros** — good-night, meeting mode, custom (`engine/pro/RoutineStore`).
- **Briefing (minus weather)** — greeting, time, battery, notifications, daily
  thought are all local; weather is skipped offline with one honest line
  (`engine/pro/BriefingHelper`).
- **Calculator** — local expression parser (`engine/pro/CalcParser`).
- **Caller-ID prefix tables + spam DB** — PK mobile-network / landline-city prefix
  intelligence and the learnable offline spam shield; nothing uploaded.
- **Jokes, facts, stories** — on-device content banks (`companion/ContentBanks`).
- **Check-ins** — local `CheckInWorker`.

### [online] — needs data

- **Web search** — results only; offline → honest "needs internet" line, never fake results.
- **Weather** — Open-Meteo; briefing skips it offline with one trilingual line.
- **Live currency rates** — fallback: last cached rates with a **"cached"** label
  (`engine/pro/CurrencyConverter`, `ConvertResult.dated`); null when never cached.
- **Number deep-lookup** — "is number ka pata lagao" (web lookup, on demand).
- **One-time downloads** — Vosk voice model (~50 MB), TTS voice data (see below).

## TTS voice-data: one-time download note

Baloch speaks through Android's on-device TTS engine. English voice data ships with
most devices and works offline immediately. **Urdu (ur-PK) and Hindi (hi-IN) voice
data may need a one-time download** (system Settings → Text-to-speech → voice data)
on first use — after that, all three languages speak fully offline. If a language's
voice data is missing, Baloch falls back to English cleanly and says so (`VoiceOutput`).

The Vosk model (~50 MB, `filesDir/vosk-model`) is the same story: downloaded once by
the wake-word engine, then shared by offline command recognition — no second copy.

## How it is enforced (for builders)

1. `connectivity/ConnectivityGate.kt` — the single gate: `isOnline()` (VALIDATED
   internet capability), `requireOnline()`, and `withOnline(ctx, online, offline)`.
   **Every online-only feature calls this first.**
2. `voice/VoiceInput.kt` — `var preferOffline = true` (default): Vosk decode first
   when the model is present; `EXTRA_PREFER_OFFLINE` hint on the platform intent;
   falls back to `SpeechRecognizer` only when the model is missing.
3. `engine/pro/BriefingHelper.kt` — weather step gated; offline → one honest
   trilingual line, rest of briefing unaffected.
4. `schedule/TaskWorker.kt` — **queue-and-sync**: web-dependent scheduled commands
   detected via online-only markers; when offline they enqueue a one-time
   WorkManager retry constrained to `NetworkType.CONNECTED` instead of failing.
   AlarmManager one-shots are local and always run.
5. `engine/pro/CurrencyConverter.kt` — offline fallback to per-base-currency cache,
   flagged `dated` so the brain says "rates may be dated, sir" honestly.

## Not yet true (honest gaps)

- Offline STT quality follows the small English Vosk model; Urdu/Hindi command
  recognition depends on model availability and will lag English until dedicated
  models are bundled.
- A scheduled web-dependent task may speak twice (retry + next periodic fire) if
  data returns between them — duplication is preferred over silent drops.
