# GESTURE CONTROL + PROFESSIONAL SETTINGS — builder docs

Kamran's orders: real Iron Man hand gestures (his toggle), hardened until they
"achy sy work krain — ALWAYS", sensor gestures as the dark-friendly backup,
and one mission-control Settings screen. Everything below is on-device.

---

## 1. Camera hand gestures — how it works

**Pipeline** (`gesture/GestureControlService.kt`, foreground service):

1. Front camera → Camera2 + `ImageReader` 640×480 YUV → stride-aware
   NV21 → JPEG → Bitmap (no CameraX — lean by design).
2. Per-frame orientation fix (portrait AND landscape): rotate by
   `(sensorOrientation − displayRotation)`, then un-mirror horizontally so
   coordinates match the user's own mirror-view (swipe right = hand really
   moved right).
3. Throttled to **12fps** (83ms between submits; skipped while inference is
   in flight) — enough for gestures, kind to battery.
4. MediaPipe `HandLandmarker` (tasks-vision 0.10.14), LIVE_STREAM, GPU
   delegate with CPU fallback. Model `hand_landmarker.task` downloads once
   (~7.8MB) from the official MediaPipe storage URL, SHA-256 verified
   against the pinned hash; honest failure (toggle reverts, one notification)
   if the download fails.
5. 21 landmarks → `GestureClassifier` compares against **Kamran's calibrated
   hand** (not generic thresholds) → anti-flicker window → fire.

**The six gestures** (`gesture/GestureMap.kt`, user-remappable JSON in
`filesDir/gesture_map.json`):

| Gesture | Default command | Meaning |
|---|---|---|
| PALM_HOLD (open palm, held) | `wake baloch` | starts voice input |
| SWIPE_RIGHT (palm flick right) | `next track` | media key |
| SWIPE_LEFT (palm flick left) | `previous track` | media key |
| FIST | `pause media` | |
| THUMB_UP | `yes` | confirm pending risky action |
| THUMB_DOWN | `no` | cancel pending |

`GestureMap.getMap()` / `remap(kind, commandText)` / `resetDefaults()` /
`commandFor(kind)`. Remap UI is the parent's (Settings → Gestures → Remap).

**Routing:** each fire invokes `onGesture(GestureKind)` AND the convenience
`onCommand(commandText)` (already map-resolved), plus a same-package
broadcast `com.baloch.ai.gesture.GESTURE_DETECTED`. The parent feeds the
command text through the **Orchestrator** — voice lock, risk tiers and
confirmations apply exactly as for typed/voice commands. A gesture is just
another way to say the words.

### Reliability (quality order)

- **Mandatory calibration** (`gesture/GestureCalibrator.kt`): palm 5s +
  fist 5s on first run. Stores palm size + per-finger extended/curled
  tip-distances + thumb ranges + brightness reference. **Firing is gated
  behind completed calibration** — uncalibrated = watch-only
  (`NOT_CALIBRATED`). Re-run any time from Settings or the Lab.
- **Anti-flicker:** a static pose must classify identically in ≥70% of
  frames with the hand continuously present for **800ms** before firing.
  Uncertain frames (between his open/closed ranges) cast no vote.
- **Cooldown:** 1.5s after every trigger — no double-fires.
- **Swipes** use a motion signature instead of a hold: ≥1.2 palm-widths of
  lateral travel within 600ms, vertical drift < 0.8 palm-widths.
- **Lighting watchdog:** hand visible in <25% of frames for 5s →
  `onSpeakRequest` fires the trilingual line *"Sir, thori roshni chahiye —
  haath saaf nazar nahi aa raha"* (parent speaks via VoiceOutput);
  detection never stops and resumes silently when light recovers.
- **Camera resilience:** disconnect / `CameraAccessException` / another app
  grabbing the camera → status `CAMERA_BUSY`, quiet 5s backoff, auto-resume.
  Status is exposed via `onStatusChange(GestureStatus)` for the Settings row.
- **Screen off → camera pauses** (`PAUSED_SCREEN_OFF`); resumes on screen-on.
  The persistent low-importance notification "👆 Gesture control active" is
  the camera-active indicator — the camera is never on silently.

**Honest physics limits** (also in the UI): normal indoor light, arm's
length. Pitch-dark rooms and extreme hand angles are physics, not bugs —
the watchdog says so out loud instead of failing silently.

### Gesture Lab (`ui/GestureLabScreen.kt`)

Settings → Gestures → **Gesture Lab**: live preview of EXACTLY what the
classifier sees (oriented/un-mirrored frame) + 21-point skeleton overlay
+ detected-gesture readout + fps + status. This is how Kamran verifies each
gesture fires before trusting it.

- Lifecycle-safe by construction: `onStartLab()` on entry, `onStopLab()`
  on dispose — camera released when he leaves.
- Parent contract: bind the service; `binder.attachLab { frame -> }`
  (background thread — post to main, update `GestureLabState`); on stop,
  `detachLab()` + unbind. Lab mode runs the camera even with the master
  toggle off; gestures still fire so he can verify them.
- After calibration succeeds, parent calls `binder.notifyCalibrated()` to
  lift the gate without restarting the pipeline.

### Privacy

Every frame is classified on-device and discarded immediately. No video is
stored, no frame ever leaves the phone. The model downloads once from
Google's official storage; after that the pipeline is fully offline
(matches the OFFLINE-FIRST doctrine).

---

## 2. Sensor gestures — the dark-friendly backup

**Pipeline** (`gesture/SensorGestureService.kt`, foreground service, shares
the gesture notification channel/style):

| Gesture | Sensor | Default |
|---|---|---|
| WAVE (near→far→near ≤1.5s) | proximity (IR — works in darkness, screen off) | `wake baloch`; while music plays → `toggle media`; while ringing → `silence ringing` (fixed, safety) |
| FLIP (face-down, held 0.5s) | accelerometer z | `silence ringing` (+ optional DND on/off, configurable) |
| SHAKE (2 sharp shakes ≤2s) | accelerometer, high-pass peak math (commented in code) | `toggle flashlight`, 3s cooldown |
| PICK_UP (default OFF) | significant-motion trigger | readiness signal → parent chimes "Ji, sir?" — nothing executes |

- **Sensor fusion anti-misfire:** proximity-near (pocket/against ear)
  suppresses shake + flip. A phone in a pocket cannot misfire.
- **Screen-off is DELIBERATE here** (unlike camera gestures): the proximity
  wave with a dark screen is the killer feature — silence the alarm without
  looking. Accelerometer gestures need the device awake; on most phones they
  simply don't fire with the screen off (documented, not a bug).
- **Parent flags ringing:** `binder.setRinging(true/false)` (or the
  `SET_RINGING` intent) when an alarm rings / call arrives / stops — the
  service has no telephony permission by design.
- Remap via `gesture/SensorGestureMap.kt`
  (`filesDir/sensor_gesture_map.json`); `REMAPPABLE` = WAVE, WAVE_MEDIA,
  FLIP, SHAKE. PICK_UP is intentionally not command-mapped.
- Routing: `onCommand(commandText)` — same shape as the camera service's,
  plus broadcast `com.baloch.ai.sensor.GESTURE_DETECTED`.

### PERMISSIONS note

**Sensor gestures need NO new permissions.** Proximity, accelerometer and
significant-motion are normal sensor APIs; `AudioManager.isMusicActive()`
needs none either. Manifest additions for gestures are only the two
foreground-service permissions + the two `<service>` declarations — see
`manifest-snippets-gesture.txt`. (CAMERA and INTERNET were already declared.)

### Battery

Camera gestures ~5–8%/hr (continuous front camera). Sensor gestures sip —
proximity + accelerometer at `SENSOR_DELAY_NORMAL`, roughly an order of
magnitude cheaper. The Settings screen says it plainly:
*"sensor gestures sip battery; camera gestures drink it 🥤"*.

---

## 3. Voice control (both pipelines)

- `gesture/GestureIntents.kt` — `matchGestureCommand()`: "gesture control
  on/off", "hand gesture band kar do", "gesture control chalu karo", "hand
  gestures off", + Urdu script + Devanagari. Ignores "sensor…" inputs.
- `gesture/SensorGestureIntents.kt` — `matchSensorGestureCommand()`:
  "sensor gestures on/off", "sensor gesture band kar do",
  "sensor wale gesture chalu karo". Requires the word "sensor" — the two
  matchers can never collide.
- Both ship trilingual `*Reply()` helpers (EN/HI/UR via `L3`).
- Parent wiring: match in the brain before the generic fallback; enable →
  `setMasterEnabled(ctx, …)` / `SensorGestureService.setEnabled(ctx, …)`.

---

## 4. Professional Settings screen (`ui/SettingsScreen.kt`)

One scrollable column of holographic-cyan section cards. `SettingsState`
aggregates everything; `SettingsActions` has one callback per control —
every toggle is wired, persistence is the parent's job (each callback's
contract is in KDoc).

| Section | Contents |
|---|---|
| 🗣️ Voice | EN/HI/UR segmented language row · quiet-voice-mode toggle · "Voice identity & volume" → VoiceSettingsScreen |
| 🔐 Security | "Voice Lock setup" → VoiceLockSetupScreen (status subtitle) · "My Rules (n)" → RulesScreen · biometric app-lock toggle |
| 🤖 Behaviour | MANUAL/JARVIS segmented default-mode · check-in frequency Off/Subtle/Normal · companionship note |
| 👆 Gestures | **Camera / Sensor tabs.** Camera: master toggle · live status row · calibration entry (highlighted until done) · remap entry · Gesture Lab entry · camera-indicator + battery + physics notes. Sensor: master toggle · status · wave/flip/shake/pick-up toggles · flip-DND toggle · remap entry · battery note |
| 🧠 Memory | "Total Memory" → MemoryScreen · "Backup & export" · auto-expiry info (temporary facts fade after 30 days) |
| ⏰ Automation | "Scheduled tasks (n)" → ScheduleScreen · "Routines (n)" · battery-guardian toggle |
| 🆘 Emergency | `IceContactRow` (from ProSettingsRows) wired to `onIceContactSave` |
| 🔒 Privacy & Permissions | per-permission rows: name + plain-language why + ●/○ status + GRANT → deep-link to the app's system settings page; statuses via `permissionStatuses: Map<String, Boolean>` keyed by `Manifest.permission.*` (parent re-queries on resume) |
| ℹ️ About | "Baloch v0.1 — Built by the Baloch Factory for Kamran." |

Composes the sibling screens' contracts without touching them:
`VoiceSettingsScreen(VoiceSettingsState, onPickVoice, onPreview,
onVolumeChange, onBack)` · `VoiceLockSetupScreen(VoiceLockSetupState,
VoiceLockSetupActions)` · `RulesScreen(RulesScreenState, onDelete, onBack)`
· `ScheduleScreen(tasks, onToggle, onDelete, onBack)` ·
`MemoryScreen(…)` · `IceContactRow(current, onSave)`.

---

## 5. Test checklist — gestures (Kamran's "achy sy work krain")

Run in the **Gesture Lab** (skeleton overlay = ground truth).

| Scenario | Expected |
|---|---|
| Bright room, arm's length, open palm held 1s | Skeleton locks on; readout shows `PALM_HOLD → wake baloch`; voice input starts |
| Bright room, fist held 1s | `FIST → pause media` |
| Bright room, thumb up / thumb down held 1s | `THUMB_UP → yes` / `THUMB_DOWN → no` |
| Bright room, quick palm flick left/right | `SWIPE_LEFT/RIGHT → previous/next track` (no hold needed) |
| Dim room (single bulb) | Still fires; may need the hand slightly closer |
| Very dim / pitch-dark room | Watchdog speaks the low-light line; status `LOW LIGHT`; no phantom fires |
| Hand at slight angle (~30°) | Fires (distance-based finger math is rotation-tolerant) |
| Hand edge-on / extreme angle | No fire, no phantom — uncertain frames cast no vote |
| Two quick palm holds | Exactly one fire (1.5s cooldown) |
| Screen off (camera gestures) | Camera pauses, status `PAUSED_SCREEN_OFF`; proximity wave still works |
| Another app opens the camera | Status `CAMERA_BUSY`; auto-resume when free |
| Not calibrated | Lab shows "calibrate first"; nothing fires; 10s palm+fist flow fixes it |
| Wave over phone in dark, screen off | `wake baloch` — voice input starts |
| Wave while music plays | `toggle media` |
| Flip face-down while ringing | ringing silenced (+ DND if enabled) |
| Phone in pocket, walking | nothing fires (proximity suppression) |
| Two sharp shakes | flashlight toggles (3s cooldown) |

---

## 6. Parent wiring checklist

1. Merge `manifest-snippets-gesture.txt` (FOREGROUND_SERVICE,
   FOREGROUND_SERVICE_CAMERA, two `<service>` entries).
2. Settings toggle → `GestureControlService.setMasterEnabled()` /
   `SensorGestureService.setEnabled()`. Voice intents → same.
3. Route `onGesture`/`onCommand` (both services) → `GestureMap`/`SensorGestureMap`
   → Orchestrator (voice-lock still applies).
4. `onSpeakRequest(L3)` → VoiceOutput in Kamran's active language.
5. `onStatusChange` → refresh `SettingsState.gestureStatus`.
6. Calibration UI → `GestureCalibrator.calibrate(service.latestSample)` →
   `binder.notifyCalibrated()`.
7. Gesture Lab → bind, `attachLab`/`detachLab` per `GestureLabActions` KDoc.
8. Ringing state → `SensorGestureService` binder `setRinging()`.
9. Feed `SettingsState` (all sources listed in its KDoc) and implement
   `SettingsActions` (each callback's persistence contract is in KDoc).
10. Re-query `permissionStatuses` in `onResume` (Grant buttons leave the app).

## 7. What was NOT tested (honest)

No Android SDK / emulator on this build machine — nothing here has been
compiled or run. API usage was verified against the MediaPipe tasks-vision
0.10.14 reference (verified: `HandLandmarker.createFromOptions`,
`HandLandmarkerOptions` builder with `setBaseOptions`/`setRunningMode`/
`setResultListener`/`setErrorListener`/`setNumHands`, `RunningMode.LIVE_STREAM`,
`detectAsync(MPImage, Long)`, `HandLandmarkerResult.landmarks()`,
`BaseOptions.setModelAssetBuffer`, `Delegate.GPU/CPU`). Two device-specific
behaviours need on-phone verification: the front-camera rotation/un-mirror
math (the Gesture Lab shows the exact image, so any flip is visible
immediately) and proximity-with-screen-off delivery (OEM-dependent).
