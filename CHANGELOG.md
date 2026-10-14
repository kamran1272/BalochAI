# Changelog — Baloch for Android

All notable changes to this project are documented here, in
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/) style.
Versioning: `MAJOR.MINOR.PATCH` + an always-incrementing `versionCode`
(see `update/VersionInfo.kt` — the single source of truth).

## [1.0.0] - 2026-10-14

The first living release. 100% on-device, $0 forever, trilingual
(English · اردو · हिन्दी, including Roman Urdu), holographic-cyan
Iron Man identity.

### Added

- **Voice in / out** — tap the mic or speak hands-free; speech
  recognition in ur-PK / hi-IN / en-US locales, Roman Urdu understood
  through the intent patterns; every capability works typed too.
- **Voice identity lock** — one locked male voice per language, deep
  pitch, LOUD by default; never auto-changes.
- **Whisper / quiet-voice mode** (default ON) — software mic gain, RMS
  normalisation and lowered wake thresholds catch soft speech; Baloch
  whispers back when Kamran whispers.
- **Brain** — trilingual rule-based intent engine, warm friendly
  `BalochPersona` ("sir", never robotic), multi-part command
  understanding.
- **AI Brain 🧠 (REAL on-device LLM)** — Gemma 2B instruct (INT4, ~1.35 GB)
  via MediaPipe LLM Inference, downloaded on first run (never in the APK),
  resumable, with a 4 GB RAM guard. `HybridBrain` answers everything through
  rules first and consults the LLM only on a clean rule miss; the LLM is
  text-only and can never emit call/SMS/transmit actions. Settings → AI
  Brain 🧠: mode selector (Rules only / Hybrid), download + progress,
  manual import (Kaggle sign-in path), storage display, delete. See
  `AI-BRAIN-ADD.md`.
- **Multi-agent parallel execution** — the Orchestrator splits one
  utterance ("alarm lagao aur wifi band karo aur mausam batao"), runs
  six specialist agents (System, Info, Comms, Time, Memory, Brief)
  concurrently via coroutines, and synthesises one reply; per-agent
  failure isolation.
- **Total memory** — Room database of facts, full conversation history
  and entities; automatic fact extraction from chat; Memory screen;
  encrypted backup.
- **Device control** — open apps (fuzzy search), alarms, timers,
  Wi-Fi / Bluetooth / torch, volume, brightness, Do-Not-Disturb.
- **Pro pack** — smart morning briefing, app-usage insights,
  routines/macros (good-night, meeting mode, custom), calculator +
  currency converter, WhatsApp draft intents (opens pre-filled, never
  auto-sends), clipboard history, battery-guardian alerts, emergency
  ICE call, DND control, fuzzy app search.
- **Task scheduling** — one-time and recurring (daily/weekly) tasks via
  trilingual voice ("roz raat 11 baje wifi band kar dena"); multilingual
  datetime parser; AlarmManager + WorkManager; boot-resilient re-arm;
  scheduled tasks fire in both modes.
- **Dual mode** — MANUAL (fully passive, proactivity paused) vs JARVIS
  (autonomous: proactive alerts, briefings, routine triggers); prominent
  toggle + voice commands, persisted.
- **Always-on voice** — "hey baloch" offline wake word (Vosk) works with
  the phone locked / screen off via a foreground service; risky actions
  become verbal yes/no confirmations when the screen is off.
- **Voice lock** — Baloch obeys only Kamran's voice: on-device MFCC
  voiceprint (3 samples of "Hey Baloch, it is me", cosine-distance
  check), strangers refused with nothing executed; 4-digit PIN fallback;
  optional biometric app lock.
- **Fortress privacy** — learn-vs-act doctrine: learning always works,
  nothing personal ever leaves the phone without explicit confirmation;
  standing allowances ("always send WhatsApp to Ami"), code-level guard
  that survives rule deletion, voice payload re-confirmation on
  mishearing.
- **Caller ID** — announces unknown callers 100% offline; personal
  blocklist silences the ringer; optional call-screening role.
- **Gesture Lab** — camera hand gestures (Iron Man style) plus
  sensor-gesture fallback; per-gesture toggles in Settings.
- **Companion** — chit-chat, jokes, stories, mood awareness, proactive
  check-ins, remembers personal things.
- **Cinematic experience** — animated boot splash with version footer,
  glowing holographic-cyan theme, scanlines, deep black.
- **Offline-first** — no account, no cloud, no analytics; everything
  above runs on the phone.
- **Professional versioned updates** (infrastructure) — in-app update
  check against the GitHub releases feed, DownloadManager delivery to
  `Downloads/BalochAI/`, system install prompt (never auto-install),
  and the migration contract: updates never wipe his memory.

### Security

- Voice lock: MFCC voiceprint + 4-digit PIN (SHA-256, constant-time
  compare) + optional biometric app lock; stranger commands fail closed.
- Fortress privacy: explicit-confirm gate on every outbound action in
  both modes; voice-heard payloads are re-spoken before sending.
- Memory database encrypted at rest (SQLCipher AES-256) with an honest
  unencrypted-fallback flag; passphrase in EncryptedSharedPreferences.
- Updates install only through the OS prompt after Kamran's explicit
  "yes"; `REQUEST_INSTALL_PACKAGES` declared, unknown-app-sources grant
  requested only when needed.

### Notes — honest limits

- The Vosk wake-word model is English-only and spots just "hey baloch";
  commands themselves are transcribed in Urdu/Hindi/English.
- Voice lock rejects casual strangers; it is not spoof-proof against
  recordings — the PIN + biometric layers exist for that.
- Always-on listening costs roughly 3–5% battery per hour; the toggle
  is one tap in Settings.
- On Android 12+, the caller-ID fallback path degrades to ring-timing
  only (the framework withholds the number from non-dialer apps).
- Android speech recognition may need network for some locales; the
  wake word itself is fully offline.
- Installing an update needs the one-time "Install unknown apps" grant
  for Baloch — the app deep-links to the exact settings page.
- Update checks read the public GitHub releases feed and need
  internet; everything else stays offline.

[1.0.0]: https://github.com/kamran1272/BalochAI/releases/tag/v1.0.0
