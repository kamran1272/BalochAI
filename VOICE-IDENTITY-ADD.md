# VOICE IDENTITY LOCK

Kamran's order (Oct 14 2026): Baloch **always** speaks in **one locked male
voice per language** (English / Hindi / Urdu) — never auto-changes, deeper
pitch (~0.85), **LOUD** (voice volume default FULL).

## Concept

The voice lock is a per-locale identity, not a one-time setting. A saved
voice id is tied to its language's voice data, so DataStore
(`voice_identity`) keeps three keys — `voice_id_en`, `voice_id_ur`,
`voice_id_hi` — plus `voice_volume_pct` (default 100).

- **First run:** `VoiceIdentityManager.pickBestMaleVoice()` picks a male
  voice for the locale (see heuristic below) and saves it.
- **Every `setLanguage()`:** the saved id is re-applied *if it still exists
  in the TTS engine's voice list*. Baloch never swaps voices on its own.
- **Saved voice vanished** (e.g. TTS engine update removed it): the closest
  male match is picked, saved, and `voiceChangedFallback` is set so the
  settings screen shows a one-time note: *"Baloch's voice was re-matched,
  sir."* — identity drift is announced, never silent.

## Male-voice picking heuristic

1. Only voices whose locale matches (language equality) are candidates.
2. Prefer a name containing "male" (case-insensitive) — covers Google TTS
   ids like `en-us-x-sfg#male_1-local`, `hi-in-x-hia#male_1-local`,
   `ur-pk-x-urm#male_1-local`, plus engines that name voices "Male 1" etc.
3. Otherwise any locale-matching voice whose name does NOT contain
   "female".
4. Fallback: first voice for the locale. Null if none exist.

## Pitch and volume

- **Pitch 0.87, rate 1.0x** — applied on every `setLanguage()` and at TTS
  init (`applyAcoustics()`). This is Baloch's locked acoustic identity:
  deeper male voice, normal speed.
- **LOUD:** `speak()` boosts the **media stream** (`STREAM_MUSIC`, which TTS
  routes through) to the saved volume percentage before every utterance —
  `AudioManager.setStreamVolume(STREAM_MUSIC, max * pct/100, 0)` with
  flags=0 (silent, no volume UI popup). Default 100% (FULL), adjustable in
  the settings screen.

## Changing the voice — Kamran's choice only

The **only** place the voice can change is the `VoiceSettingsScreen`
(`ui/VoiceSettingsScreen.kt`, standalone so `MainScreen` stays untouched):

- Current locked voice card (name + language) with a **PREVIEW** button —
  speaks `"I am Baloch, sir."` in the active language (parent supplies the
  sample text; with the friendly persona, keep it warm, e.g. "I am Baloch,
  sir." suits the loyal-sir tone).
- Voice picker list: all engine voices for the active locale, male-first;
  tapping a row calls `onPickVoice(id)` → `setVoiceId()` persists the new
  lock. Nothing here ever auto-picks.
- Voice volume slider 0–100 (LOUD default note) → `VoiceOutput.setVoiceVolume()`.

## Files

| File | Role |
|---|---|
| `voice/VoiceIdentityManager.kt` | DataStore lock (3 voice ids + volume), male-voice heuristic, `ensureVoice()` |
| `voice/VoiceOutput.kt` | Applies locked voice + pitch 0.87 on every language switch, boosts STREAM_MUSIC before each utterance, `previewVoice()` |
| `ui/VoiceSettingsScreen.kt` | Standalone settings UI: current-voice card, picker, preview, volume slider |

## Parent wiring (out of scope for this add)

- Plumb `VoiceSettingsScreen` into Settings with state from
  `identityManager` + `VoiceOutput` (current voice name, locale voices,
  volume, `voiceChangedFallback`).
- Clear the re-match note after showing it (`clearVoiceChangedNote()`).
- `LanguageManager` untouched — language choice and voice identity are
  independent locks.
