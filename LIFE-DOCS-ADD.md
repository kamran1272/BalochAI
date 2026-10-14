# LIFE FEATURES — README ADDENDUM (Mobile Baloch)

8 life features, package `com.baloch.ai.life`, all trilingual
(English / Urdu script / Devanagari + Roman Urdu voice patterns),
friendly "sir" tone, en-GB spelling. Settings law: every feature has
a WORKING toggle/row in `ui/LifeSections.kt` backed by `LifeSettings`
(DataStore `baloch_life`) — zero dead switches.

## 1. Prayer times + Qibla — `life/PrayerTimes.kt`

- Aladhan API `timingsByCity?city=Lahore&country=Pakistan&method=2`,
  `HttpURLConnection`, 8 s connect/read timeout.
- Cached daily in `prayer_times_cache.json` (atomic temp+rename);
  offline = serves cache, no cache = honest "check connection" reply.
- `nextPrayer()` + `countdownText()` ("Maghrib in 2h 14m, sir").
- Prayer alerts: `PrayerAlertWorker` every 30 min, JARVIS-gated
  (stands down silently in MANUAL mode), one notification per prayer
  per day. Toggle in Settings → `PrayerAlertWorker.schedule/cancel`.
- Qibla: `qiblaBearing(lat1, lon1, lat2, lon2)` (great-circle, degrees
  from true north) + `QiblaCompass` (rotation-vector sensor,
  start/stop, no-sensor fallback text ~257° from Lahore).
  Device default location: Lahore 31.52, 74.36 (Kaaba 21.4225, 39.8262).
- Voice: "namaz ka time", "next prayer", "qibla kidhar hai".

## 2. Freelance copilot — `life/FreelanceCopilot.kt`

- `Client(name, project, amount, status, lastContact, followUpDue)` in
  atomic `freelance_clients.json` (`ClientStore`).
- Follow-up nudges: `FollowUpNudgeWorker` daily, JARVIS-gated +
  respects the copilot toggle; nudges clients quiet 5+ days, max 3 per
  day, once per day: "Ahmed ko 5 din se follow-up nahi kiya, sir".
- `clientSummary()` — pipeline value + counts, trilingual.
- Voice: "add client Ahmed", "mere clients batao", "freelance summary".

## 3. Content assistant — `life/ContentAssistant.kt`

- `VIDEO_IDEAS`: 30 video ideas for a dev freelancer, each an L3.
- `randomIdeas(n)`, `scriptOutline(topic, lang)` (hook → 3 points → CTA),
  `trendScanQuery(lang)` — Baloch never invents trends; the query is
  mapped by the parent to a WebSearch DeviceAction.
- Voice: "video idea do", "script banao: React hooks", "trending".

## 4. Expense tracker — `life/ExpenseTracker.kt`

- Voice logging: "500 kharcha chai" → amount + category guess + note.
  Digits in ASCII, Urdu/Persian (۰۱۲۳۴۵۶۷۸۹), Arabic-Indic (٠١٢٣٤٥٦٧٨٩),
  Devanagari (०१२३४५६७८९). Trilingual category keywords.
- Atomic `expenses.json`; `monthlyHisaab()` category totals biggest-first;
  `monthlySummaryText()` — "is mahine kitna kharcha".
- Currency PKR default (USD/INR), setting persisted.
- Voice: "500 kharcha — chai", "is mahine kitna kharcha".

## 5. Focus mode — `life/FocusMode.kt`

- Pomodoro 25/5 default (tunable 15/25/45/60 + 5/10/15 in Settings).
- DND on during focus via `NotificationManager.setInterruptionFilter`;
  needs the policy grant — when missing, focus still runs and Baloch
  says the exact grant path
  (Settings → Apps → Baloch → Do Not Disturb access).
- Boundaries via `AlarmManager` (`FocusAlarmReceiver`); exact alarms
  when `canScheduleExactAlarms()`, graceful inexact fallback otherwise.
- History: `focus_history.json` (last 200 sessions), completed count
  shown in Settings. `FocusSweepWorker` closes sessions honestly if an
  alarm is ever missed.
- Voice: "focus mode start karo", "focus start 30", "focus band".

## 6. Location reminders — `life/LocationReminders.kt`

- "jab market jaun to yaad dilana: doodh lena" → 100 m geofence via
  `GeofencingClient`; one-shot (fires once, then deleted).
- Atomic `geo_reminders.json`; `BootReceiver` re-registers on
  BOOT_COMPLETED (geofences don't survive reboots).
- Needs `play-services-location` (see build.gradle addition below).
- Permissions: `ACCESS_FINE_LOCATION` then `ACCESS_BACKGROUND_LOCATION`
  (API 29+ sends the user to Settings → Location → Allow all the time).

## 7. Translator — `life/Translator.kt`

- ML Kit on-device translate 17.0.3 (`TranslateLanguage.ENGLISH/URDU/HINDI`,
  `TranslatorOptions`, `DownloadConditions`, `Translation.getClient`).
- Download each pair ONCE on Wi-Fi (~30 MB), then fully offline.
- `TranslateResult`: Done / ModelMissing (warm "download it" reply) /
  Failed — never a cold error.
- Auto-download toggle in Settings; source auto-detected from script.
- Voice: "translate karo: where is the station", "iska tarjuma karo".

## 8. SOS — `life/SosHelper.kt`

- "SOS" → SMS to the ICE contact (`EmergencyStore` number):
  "SOS! I need help. My location: <maps link>".
- RISKY tier: new `DeviceAction.SendSosAlert` is in `needsConfirmation`
  — the parent's confirmation dialog runs BEFORE any SMS.
- Battery-friendly single fix: last-known location first (free), one
  8 s network fix only when stale; SMS still goes out (without
  location) when location permission is missing.
- Needs `SEND_SMS` runtime permission.

## Intent routing — `life/LifeIntents.kt`

`matchLife(input): LifeHit?` — sealed `LifeHit` types carrying parsed
args, trilingual incl. Roman Urdu, priority-ordered (SOS first, then
prayer, translator, expenses, focus, geofence, copilot, content).
The parent calls it before its own patterns and maps each hit to reply
text (new `Replies.*` entries in `brain/Strings.kt`) + the new
`DeviceAction`s in `brain/BrainEngine.kt`.

## Settings UI — `ui/LifeSections.kt`

8 standalone sections (explicit state + callbacks; the parent splices
them into the Settings screen — see the placement example at the top
of the file):

1. `PrayerAlertsSection` — prayer alerts toggle + next-prayer line + Qibla entry row
2. `CopilotNudgesSection` — follow-up nudges toggle + client/stale counts + board entry
3. `ExpenseSection` — currency segmented row (PKR/USD/INR) + monthly total + log entry
4. `FocusDefaultsSection` — focus/break segmented rows + sessions count + "Start focus now"
5. `LocationRemindersSection` — geofence toggle + permission warning row + add entry
6. `TranslatorSection` — auto-download toggle + model status + "Download UR + HI models"
7. `SosSetupSection` — ICE number row (saves to `EmergencyStore`) + SMS permission row
8. `QiblaCompassScreen` — standalone live compass screen (needle rotation, alignment text)

## PERMISSIONS — additions

| Permission | Feature | Why | Grant steps |
|---|---|---|---|
| `ACCESS_FINE_LOCATION` | Location reminders | Register geofences | Runtime prompt on first use |
| `ACCESS_BACKGROUND_LOCATION` | Location reminders | Fire when app is closed (API 29+) | Settings → Location → Baloch → Allow all the time |
| `SEND_SMS` | SOS | Send SOS SMS to ICE contact | Runtime prompt (after SOS confirmation) |
| `POST_NOTIFICATIONS` | Prayer/focus/geo alerts | Show alert notifications (API 33+) | Runtime prompt |
| `SCHEDULE_EXACT_ALARM` | Focus mode | Precise Pomodoro boundaries (API 31+) | Falls back to inexact when denied — no action needed |
| Notification-policy access | Focus DND | Silence notifications during focus | Settings → Apps → Baloch → Do Not Disturb access |

## build.gradle — additions for the parent

```gradle
// Life features
implementation 'com.google.mlkit:translate:17.0.3'          // on-device EN<->UR<->HI
implementation 'com.google.android.gms:play-services-location:21.3.0'  // geofencing
```

Manifest additions: see `manifest-snippets-life.txt`
(FocusAlarmReceiver, GeofenceTransitionReceiver, BootReceiver +
location/SMS/notification/alarm permissions).

## Wiring checklist for the parent

- [ ] Add the two gradle deps above + manifest snippets
- [ ] Splice the 8 `LifeSections.kt` sections into the Settings screen
- [ ] Call `matchLife()` in the brain before existing patterns; map each
      `LifeHit` → reply + the new `DeviceAction`s (already added)
- [ ] On prayer-alerts toggle: `PrayerAlertWorker.schedule/cancel(context)`
- [ ] On nudges toggle: `FollowUpNudgeWorker.schedule/cancel(context)`
- [ ] On translator auto-download toggle: kick `BalochTranslator.downloadModel(EN, UR/HI)`
- [ ] Navigate to `QiblaCompassScreen` from the Qibla entry row, feeding it
      `QiblaCompass` azimuth updates + `qiblaBearing(deviceLat, deviceLon, KAABA_LAT, KAABA_LON)`
- [ ] Route `DeviceAction.SendSosAlert` through the existing confirmation dialog
