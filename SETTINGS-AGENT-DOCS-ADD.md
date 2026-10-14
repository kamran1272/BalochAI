# Settings Agent — README section (merge into mobile README)

## Settings Agent — "bolun setting on kar do to kare" ⚙️

Every Baloch setting is voice-toggleable. The Settings Agent
(`agents/SettingsAgent.kt`) parses one utterance part into a `SettingsCmd`,
converts it to `DeviceAction.ApplySetting(key, on)`, and executes it by
writing to the **real stores** — the same ones `SettingsScreen` uses — so a
toggle flipped by voice and a toggle flipped on screen can never disagree.

Wiring (parent's job): register `SettingsAgent(deps)` in the
`Orchestrator`'s agent list, and route `matchSettingsCommand(part)` results
through `SettingsCmd.toDeviceAction()`. `canHandle` matches only
`DeviceAction.ApplySetting`, so it never steals another agent's action.

```
"jarvis mode on kar do aur wifi band kar do"
        │ Orchestrator splits on "aur"
        ├─ "jarvis mode on kar do" ──► matchSettingsCommand ──► ApplySetting("jarvis_mode", true) ──► ⚙️ Settings
        └─ "wifi band kar do" ──► ToggleWifi(false) ──► 🖥️ System
```

The matcher handles ONE part (no conjunction logic inside). Returns `null`
when the utterance is not a settings command — other agents' commands
("wifi band kar do", "dnd on karo", "translate to urdu", "remember this")
are never hijacked.

### Command model

```kotlin
sealed interface SettingsCmd {
    data class ToggleSetting(val key: String, val on: Boolean) : SettingsCmd
    data class QuerySettings(val topic: String) : SettingsCmd   // "alerts" | "gestures" | "modes" | "all"
    data object OpenSettings : SettingsCmd
}
```

`SettingsCmd.toDeviceAction()` bridges to the brain: toggles become
`ApplySetting(key, on)`; queries become `ApplySetting("query:<topic>", true)`;
open becomes `ApplySetting("open", true)`.

**Navigation pseudo-keys** (agent confirms out loud; the parent maps them to
`openScreen(...)` in the ViewModel wiring — the agent itself never touches UI):
- `"open"` → "Settings open, sir ⚙️"
- `"screen_scheduled"` ("scheduled tasks dikhao") → "Scheduled tasks open, sir 📅"
- `"screen_memory"` ("memory screen kholo") → "Memory open, sir 🧠"

### Voice command reference

Every command works in English, Roman Urdu, Urdu script and Hindi script.
ON/OFF detection: OFF words win ("band kar do" beats "karo"); bare setting
names with no on/off intent return `null` (the parent should ask
"on karun ya off, sir?" — never a silent flip).

**Voice — language & listening**

| English | Roman Urdu | اردو / हिन्दी |
|---|---|---|
| "speak urdu" / "talk to me in urdu" | "urdu mein baat karo" | "اردو میں بات کرو" / "उर्दू में बात करो" |
| "speak english" | "english mein baat karo" | "انگریزی میں بات کرو" / "अंग्रेज़ी में बात करो" |
| "speak hindi" | "hindi mein baat karo" | "ہندی میں بات کرو" / "हिन्दी में बात करो" |
| "voice volume up" | "awaz tez karo" | "آواز تیز کرو" / "आवाज़ तेज़ करो" |
| "voice volume down" | "awaz kam karo" | "آواز کم کرو" / "आवाज़ कम करो" |
| "set voice volume to 80" | "awaz 80 percent karo" | "آواز 80 فیصد کرو" / "आवाज़ 80 प्रतिशत करो" |
| "quiet voice mode on/off" | "quiet voice mode on/band karo" | "ہلکی آواز موڈ آن/بند کرو" |
| "always listening on/off" | "hamesha sunna on/band karo" | "ہمیشہ سننا آن/بند کرو" |

**Behaviour**

| English | Roman Urdu | اردو / हिन्दी |
|---|---|---|
| "jarvis mode on/off" | "jarvis mode on/band kar do" | "جاروس موڈ آن/بند کر دو" |
| "manual mode" | "manual mode" | "مینوئل موڈ" |
| "check-ins off" | "check-in band kar do" | "چیک اِن بند کر دو" |

**Gestures**

| English | Roman Urdu | اردو / हिन्दी |
|---|---|---|
| "gesture control on/off" | "gesture control on/band karo" | "اشارہ کنٹرول آن/بند کرو" |
| "sensor gestures on/off" | "sensor gestures on/band karo" | "سینسر اشارے آن/بند کرو" |
| "wave gesture off" | "wave gesture band karo" | "ہاتھ ہلانے کا اشارہ بند کرو" |
| "flip gesture on" | "flip gesture on karo" | "الٹنے کا اشارہ آن کرو" |
| "shake gesture off" | "shake gesture band karo" | "ہلانے کا اشارہ بند کرو" |
| "pick up gesture on" | "pick up gesture on karo" | "اٹھانے کا اشارہ آن کرو" |

**Automation**

| English | Roman Urdu | اردو / हिन्दी |
|---|---|---|
| "battery guardian on/off" | "battery guardian on/band karo" | "بیٹری محافظ آن/بند کرو" |
| "prayer alerts on/off" | "namaz alert on/band karo" | "نماز الرٹ آن/بند کرو" |
| "client nudges on/off" | "client nudge on/band karo" | "کلائنٹ یاددہانی آن/بند کرو" |
| "location reminders on/off" | "location reminder on/band karo" | "مقام کی یاددہانی آن/بند کرو" |
| "translator auto-download on/off" | "translator auto download on/band karo" | "مترجم خودکار ڈاؤن لوڈ آن/بند کرو" |

**Alerts**

| English | Roman Urdu | اردو / हिन्दी |
|---|---|---|
| "cricket alerts on/off" | "cricket alerts on/band karo" | "کرکٹ الرٹس آن/بند کرو" |
| "currency alerts on/off" | "dollar alert on/band karo" | "کرنسی الرٹ آن/بند کرو" |
| "petrol alerts on/off" | "petrol alert on/band karo" | "پٹرول الرٹ آن/بند کرو" |
| "rain alerts on/off" | "barish alert on/band karo" | "بارش الرٹ آن/بند کرو" |
| "full charge alert on/off" | "charge alert on/band karo" | "مکمل چارج الرٹ آن/بند کرو" |
| "daily word on/off" | "daily word on/band karo" | "روز کا لفظ آن/بند کرو" |

**Power & sessions**

| English | Roman Urdu | اردو / हिन्दी |
|---|---|---|
| "driving mode on/off" | "driving mode on/band kar do" | "ڈرائیونگ موڈ آن/بند کر دو" |
| "ramadan mode on/off" | "ramadan mode on/band karo" | "رمضان موڈ آن/بند کرو" |
| "meeting recorder on/off" | "meeting recorder on/band karo" | "میٹنگ ریکارڈر آن/بند کرو" |
| "start focus mode" | "focus mode start karo" | "فوکس موڈ شروع کرو" |
| "stop focus mode" | "focus mode band karo" | "فوکس موڈ بند کرو" |

**Emergency, screens, queries**

| English | Roman Urdu | اردو / हिन्दी |
|---|---|---|
| "set emergency contact 0300…" | "emergency contact set karo 0300…" | "ایمرجنسی رابطہ سیٹ کرو 0300…" |
| "open settings" | "settings kholo" | "سیٹنگز کھولو" / "सेटिंग्स खोलो" |
| "show scheduled tasks" | "scheduled tasks dikhao" | "شیڈیولڈ ٹاسکس دکھاؤ" |
| "open memory screen" | "memory screen kholo" | "میموری اسکرین کھولو" |
| "which alerts are on?" | "kaun se alerts on hain?" | "کون سے الرٹس آن ہیں؟" / "कौन-से अलर्ट चालू हैं?" |
| "what is on?" | "kya kya on hai?" | "کیا کیا آن ہے؟" / "क्या-क्या चालू है?" |

Confirmations are trilingual: "Done, sir — Driving mode on ✅" /
"ہو گیا، سر — ڈرائیونگ موڈ آن ✅" / "हो गया, सर — ड्राइविंग मोड चालू ✅".
Unknown names never dead-end: "Couldn't find that setting, sir — shall I
open Settings? ⚙️"

### Registry table (single source of truth)

`SettingRegistry.all` — key → trilingual display name, category, real store.
The future Settings UI should read from here too.

| Key | Display name (en) | Category | Backing store |
|---|---|---|---|
| `language_urdu` / `language_hindi` / `language_english` | Urdu/Hindi/English language | VOICE | `LanguageManager` (`baloch_prefs`/`language`) |
| `quiet_voice_mode` | Quiet voice mode | VOICE | `quiet_voice_mode` prefs / `enabled` (default true) |
| `always_listening` | Always listening | VOICE | `BalochListenerService` (`baloch_prefs`/`always_listening`, default true) |
| `jarvis_mode` | JARVIS mode | BEHAVIOUR | `ModeManager` (baloch_mode DataStore) |
| `checkins` | Companion check-ins | BEHAVIOUR | `baloch_companion` prefs / `checkin_level` (`off` vs `normal`) |
| `gesture_control` | Gesture control (camera) | GESTURES | `GestureControlService` (`baloch_gesture` prefs) |
| `sensor_gestures` | Sensor gestures | GESTURES | `SensorGestureService` (`baloch_sensor_gesture` prefs) |
| `sensor_wave` / `sensor_flip` / `sensor_shake` / `sensor_pickup` | Wave/Flip/Shake/Pick-up gesture | GESTURES | `SensorGestureService` per-gesture keys |
| `battery_guardian` | Battery guardian | AUTOMATION | `baloch_settings` prefs / `battery_guardian_on` (default true) |
| `prayer_alerts` | Prayer alerts | AUTOMATION | `LifeSettings` (baloch_life DataStore) |
| `copilot_nudges` | Client nudges | AUTOMATION | `LifeSettings` |
| `geo_reminders` | Location reminders | AUTOMATION | `LifeSettings` |
| `translator_auto_dl` | Translator auto-download | AUTOMATION | `LifeSettings` |
| `cricket_alerts` | Cricket alerts | ALERTS | `CricketAlerts` → `PowerPrefs` (baloch_power) |
| `currency_alerts` / `petrol_alerts` / `rain_alerts` / `charge_alert` / `daily_word` | Currency/Petrol/Rain/Full-charge/Daily word | ALERTS | `PowerPrefs` (baloch_power) |
| `driving_mode` | Driving mode | POWER | `DrivingMode` → `PowerPrefs` |
| `ramadan_mode` | Ramadan mode | POWER | `RamadanMode` → `PowerPrefs` |
| `meeting_recorder` | Meeting recorder | POWER | `PowerPrefs.RECORDER` |
| `voice_lock` 🔐 | Voice lock | SECURITY | `VoiceLockManager` (read-only) |
| `app_lock` 🔐 | App lock | SECURITY | `PowerPrefs.APPLOCK` (read-only by voice) |
| `biometric_lock` 🔐 | Biometric lock | SECURITY | `baloch_settings`/`biometric_lock_on` (read-only by voice) |
| `pin` 🔐 | PIN code | SECURITY | `PinManager.hasPin()` (read-only) |
| `forget_all` 🔐 | Forget everything | SECURITY | n/a (gated) |

Special (non-registry) keys handled in `execute()`: `volume_up` /
`volume_down` / `volume_set_<0–100>` (via `VoiceIdentityManager`, Kamran's
locked voice volume), `ice_contact:<number>` (via `EmergencyStore`),
`focus_mode` (via `FocusManager.startFocus/stopFocus` with the LifeSettings
durations).

Status queries (`QuerySettings`) read every getter in the topic and speak a
bounded summary: "On: JARVIS mode, Battery guardian, Cricket alerts ✅ —
5 more alerts off." Gated entries are reported with a 🔐 marker (state is
read-only, never changed).

### Security-gate policy

These are **never executed by voice**, no matter how the command is phrased.
The gate fires at **execution** time (not match time), so even a future UI
path consulting the registry gets the same refusal. `execute()` returns
`ok=false` with:

> "Security setting, sir — verify in Settings → Security first 🔐"

Gated: **voice-lock enable/disable**, **PIN changes**, **app lock**,
**biometric lock**, **forget-everything**. Rationale: each of these removes a
protection or wipes identity data — a stranger's voice (or a misheard
phrase) must never be able to disarm them. Verification happens in
Settings → Security, where the voiceprint-or-PIN check (`VoiceLockManager`,
`PinManager`) runs with Kamran present.

The agent *may* **report** gated state read-only in status queries
(`VoiceLockManager.isEnabled`, `PinManager.hasPin`) — reading is safe,
writing is not.

### Verification notes

- `BrainEngine.kt` edit is purely additive: one `data class ApplySetting`
  in the SAFE section. All `when`s over `DeviceAction` have `else` arms
  (`confirmLine` in Agents.kt falls through to "Done, sir."), so no
  exhaustiveness break.
- Every store write was matched against the real API by reading the source:
  `LanguageManager.setLanguage`, `ModeManager.setMode`, `GestureControlService.setMasterEnabled`,
  `SensorGestureService.setEnabled/setGestureOn`, `BalochListenerService.setAlwaysListening`,
  `LifeSettings` setters, `DrivingMode/RamadanMode/CricketAlerts.setOn`,
  `PowerPrefs.setOn`, `EmergencyStore.set`, `VoiceIdentityManager.setVolumePct`,
  `FocusManager.startFocus/stopFocus`, and the exact SharedPreferences
  file/key pairs the ViewModel writes (`baloch_settings/battery_guardian_on`,
  `baloch_companion/checkin_level`, `quiet_voice_mode/enabled`).
- **Not compile-checked**: no Kotlin/Android toolchain on this machine
  (no `kotlinc`, no Android SDK). Symbols were verified by reading every
  referenced source file; brace/paren balance and import targets were
  machine-checked. The parent should run a Gradle build before merging.
- Matcher behaviour was validated with a Python simulation of the exact
  matching logic against 40 utterances (all task examples + negatives):
  every settings command routed to the right key, and non-settings commands
  ("wifi band kar do", "dnd on karo", "translate this to urdu",
  "remember this fact") correctly returned `null`.
