# VERSIONS.md — factory dev ledger

**Not shipped in-app.** Continuity notes for whoever builds the next
release: what went into each version, the sharp edges found, and the
migration state. The user-facing notes live in [CHANGELOG.md](CHANGELOG.md).

## v1.0.2 (code 3) — 2026-10-14 — Protection mode toggle + voice transparency + polish

### What went in

- `settings/ProtectionMode.kt` (new): `protection_mode` boolean in
  `baloch_settings` prefs, default true; single read/write point for UI
  and MainActivity.
- `MainActivity.kt`: unconditional FLAG_SECURE replaced with
  `applyProtectionMode()` — set when Protection mode is ON, cleared when
  OFF. Called in onCreate AND onResume (toggle takes effect on return
  from Settings, no restart).
- `ui/SettingsScreen.kt`: "Protection mode" toggle row at the top of the
  Privacy & Permissions section; "Test voice input" nav row in the Voice
  section with a calm amber NoticeCard showing the heard text or failure
  reason; `SettingsState.protectionMode` / `voiceTestResult` fields;
  `SettingsActions.onProtectionModeChange` / `onTestVoiceInput`.
- `voice/VoiceInput.kt`: `describeError(code)` maps every
  SpeechRecognizer error + "unavailable"/"empty" to a human-readable,
  en-GB reason; new `isOfflineReady()` exposes Vosk model provisioning.
- `ui/BalochViewModel.kt`: `_lastVoiceError` StateFlow; `startVoice()`
  no longer gates on the platform recogniser when the offline model is
  ready (this was wrongly killing voice on devices without Google voice
  services); onError now surfaces the real reason instead of MISHEARD /
  silence. Removed the now-unused MISHEARD template.
- `ui/MainScreen.kt`: `JarvisVoicePanel` takes `lastVoiceError` and shows
  it as a calm notice under the mic button.
- Polish: `ui/components/Polish.kt` (new) — `balochSwitchColors()`,
  `SettingsDivider()`, `NoticeCard` (INFO/WARN tones), `EmptyState`;
  `SettingsToggleRow` is whole-row tappable (Switch display-only, proper
  Role.Switch); Memory + Schedule empty states use `EmptyState`.
- Version bump 1.0.1 (code 2) → 1.0.2 (code 3) in build.gradle +
  VersionInfo.kt (kept in agreement per the release rules).
- `.github/workflows/build-apk.yml`: the CI release step now names the
  release per the UpdateChecker factory contract — `v{version} (code {n})`
  derived from VersionInfo.kt — which is what makes the in-app updater
  actually see new versions. (v1.0.0/v1.0.1 releases were named
  "Baloch vX.Y.Z", which the updater's name pattern cannot parse — that
  is why "check for updates" showed nothing.)

### Known edges (fix or carry forward)

- `describeError` strings are English-only (en-GB); the surrounding voice
  pipeline already localises everything else — localising these is a
  future pass.
- The voice self-test runs a real 5s listen; it never processes commands.

## v1.0.1 (code 2) — 2026-10-14 — JARVIS voice mode + branded notifications

### What went in

- `ui/MainScreen.kt`: JARVIS mode renders `JarvisVoicePanel` (large orb + status + big mic), chat LazyColumn and text input row hidden; MANUAL mode unchanged.
- `notify/BalochNotifications.kt` (new): central notification helper — `baloch_service` (LOW) and `baloch_alerts` (HIGH) channels, `R.drawable.ic_stat_baloch` icon, cyan accent.
- `res/drawable/ic_stat_baloch.xml` (new): white Arc Reactor vector mark for notification small icons.
- Foreground service notifications (`SensorGestureService`, `GestureControlService`, `BalochListenerService`) migrated to the helper; emoji dropped from titles.
- Version bump 1.0.0 (code 1) → 1.0.1 (code 2); workflow release tag follows to v1.0.1 so the in-app updater picks it up.

## v1.0.0 (code 1) — 2026-10-14 — first living release

### What went in

Full feature build across ~14 subsystems (see CHANGELOG.md for the
user-facing list). Package `com.baloch.ai`, minSdk 26. Source map:

| Area | Package | Key files |
|---|---|---|
| UI | `ui/` | SplashScreen (cinematic boot + version footer), MainScreen, SettingsScreen, MemoryScreen, VoiceLockSetupScreen, GestureLabScreen, ScheduleScreen, CallLogScreen |
| Brain | `brain/` | RuleBasedBrain, Intents, L3 trilingual strings, BalochPersona, BalochLanguage |
| Agents | `agents/` | Orchestrator + 6 specialists (System/Info/Comms/Time/Memory/Brief) |
| Device actions | `engine/` | DeviceActions, `pro/` pack (briefing, routines, currency, clipboard, battery guardian, emergency ICE) |
| Scheduling | `schedule/` | TaskStore (`scheduled_tasks.json`), TaskScheduler, RescheduleReceiver (boot re-arm) |
| Always-on | `service/`, `voice/` | BalochListenerService, WakeWordEngine (Vosk), UtteranceCapture, VoiceLockManager (DataStore `voice_lock`), PinManager, whisper pipeline |
| Memory | `memory/room/` | BalochDatabase v1 (SQLCipher, fallback plain), Fact/Conversation/Entity entities + DAOs |
| Rules/privacy | `rules/` | PersonalDataGuard, GuidanceRuleStore (`guidance_rules_prefs`) |
| Caller ID | `callerid/` | CallScreeningService path + PhoneStateListener fallback |
| Gestures | `gesture/` | Camera2 hand-gesture service + sensor fallback |
| Updates (new) | `update/` | VersionInfo, UpdateChecker, MigrationContract |
| Settings state | `settings/` | LanguageManager (`baloch_prefs`), ModeManager (DataStore `baloch_mode`) |

### Known edges (fix or carry forward)

1. **build.gradle versionName is `0.1.0`, code targets v1.0.0.**
   `app/build.gradle` still reads `versionCode 1` / `versionName "0.1.0"`
   while `update/VersionInfo.kt` declares `1.0.0` / `1`. The factory
   MUST set `versionName "1.0.0"` before building the v1.0.0 APK, or the
   OS-reported version and the in-app version disagree and the updater's
   comparison logic misfires. (Parent owns build.gradle.)
2. **`exportSchema = false`.** Flip to `true` and commit `app/schemas/`
   before writing the first Room migration — auto-migrations are
   build-verified against those schemas.
3. **UpdateChecker is infrastructure, not yet wired to UI.** The parent
   owns: Settings → About → "Check for updates" row, the
   Available→confirm→`downloadAndPromptInstall` flow, and the splash
   version footer (snippet in UPDATES-DOCS-ADD.md). No release exists
   yet at `kamran1272/BalochAI` — the factory creates the repo and the
   first `v1.0.0 (code 1)` release with the APK asset.
4. **SpeechRecognizer availability** varies by device/OEM (needs Google
   voice services on some); the app degrades to typed chat with an
   honest message — do not "fix" by bundling a cloud STT.
5. **Vosk model download** (~50 MB, first run) needs ~160 MB free and
   network; failure disables always-on with an honest status.
6. **DownloadManager + `setDestinationInExternalPublicDir`** is
   deprecated on API 29+ but functional; keep it (public Downloads is
   where Kamran expects the APK). No storage permission needed — the
   system service performs the write.

### Migration notes (v1.0.0 baseline)

- Room `baloch_memory.db` at schema **v1** — the first migration ever
  written will be 1→2. Additive → `@AutoMigration`; destructive →
  manual `Migration` carrying rows forward (see MigrationContract).
- DataStore `"voice_lock"`, DataStore `"baloch_mode"`, SharedPreferences
  `"baloch_prefs"` / `"guidance_rules_prefs"` / `"baloch_pro"`, files
  `scheduled_tasks.json` / `baloch_routines.json` — key-versioning rule
  is `<key>_v2` + migrate-read, never key reuse (see MigrationContract).
- Update QA checklist per release: install old APK → populate every
  `MigrationContract.MUST_SURVIVE` store → install new APK → assert all
  read back → assert scheduled tasks re-armed.

---

## Template for the next release

```markdown
## vX.Y.Z (code N) — YYYY-MM-DD — one-line theme

### What went in
- ...

### Known edges
- ...

### Migration notes
- Room vA → vB via (autoMigration | manual Migration(A, B)): tables/columns touched.
- DataStore/SharedPreferences key changes: <key>_v1 → <key>_v2 (migrate-read added).
- QA: MUST_SURVIVE walk on device — pass/fail.
```
