# Power Features x10 — README addition

> Merge into README.md under a `## Power Features` heading, and merge the
> permission rows into PERMISSIONS.md. All ten features are OFF BY DEFAULT
> and live in the "Power Features" settings section. SETTINGS LAW holds:
> every toggle has a working backing store (`power/PowerPrefs.kt`) —
> zero dead switches.

## 1. Driving mode (`power/DrivingMode.kt`)

When ON (toggle or "driving mode on"): incoming SMS get an automatic
"I'm driving, will reply later, sir" reply and are read aloud; other app
notifications are read aloud too. Auto-switches OFF after a timer
(Never / 30m / 1h / 2h / 4h — default 2h).

- Auto-reply text is trilingual, sent via `SmsManager`.
- Notification read-aloud uses `DrivingNotifications`
  (NotificationListenerService) — needs "Notification access" granted
  manually in Android Settings; without it, only SMS are read aloud.
- Activity-recognition auto-detect deliberately NOT implemented — one
  manual tap beats constant sensor polling.

## 2. Ramadan mode (`power/RamadanMode.kt`)

Sehri/iftari alerts + "iftari in X hours" countdown. Timings from the
free keyless Aladhan API (`timingsByCity`, Karachi method — Pakistan's
convention), fetched once daily at 4 AM; the day's sehri (Fajr) and
iftari (Maghrib) fire as one-shot alarms with spoken alerts.
City is tunable (default Lahore). Works year-round: off-season answers
with the estimated next Ramadan date (arithmetic Hijri calendar —
always labelled an estimate; the moon sighting decides).

## 3. Pakistan cricket alerts (`power/CricketAlerts.kt`)

Polls the ESPNcricinfo cricket news RSS feed every 4 hours for Pakistan
stories. "Match days only" (default ON) requires match language
(vs / won by / scorecard / T20 / ODI / Test…) so gossip doesn't buzz.
One notification per story (link dedupe); speaks the first headline.

- Honest: no free official live-score API exists — this is the news
  feed, not ball-by-ball. If the feed dies, the settings row says
  "source unavailable" instead of pretending.

## 4. Dollar alert (`power/CurrencyAlerts.kt`)

"Alert me when the dollar crosses 290" — watches USD→PKR via the free
keyless open.er-api.com (same API as the currency converter), polled
every 6 hours. One-shot per crossing: fires once at/above the threshold,
re-arms when the rate drops back under. Threshold tunable (default 290);
the last fetched rate shows in settings and answers "dollar kitna hai?"
instantly, offline.

## 5. Petrol price alert (`power/PetrolAlerts.kt`)

User-taught, honestly: there is NO free official API for Pakistan's
fortnightly OGRA price, so Baloch fetches nothing and invents nothing.
Kamran teaches the price ("petrol price 278"); when a newly taught
price differs by Rs 5+, Baloch announces
"Sir, petrol 278 se 292 ho gaya". A daily worker nudges once a month
if the price hasn't been re-taught.

## 6. Daily English word (`power/DailyWord.kt`)

Fully offline: a hand-written bank of 64 words (word, part of speech,
Urdu meaning, example sentence). The day's word is
`dayOfYear % 64` — deterministic, no repeats within a cycle. Morning
notification at 7:30 AM; "aaj ka lafz" answers instantly.

## 7. Meeting recorder (`power/MeetingRecorder.kt`)

"Meeting record karo" → MediaRecorder AAC/MP4 in app-private storage
(`filesDir/meetings/`); "recording band karo" stops. Settings list with
play/stop/delete. Optional on-device transcription via Vosk: the AAC is
decoded with MediaExtractor+MediaCodec, resampled to 16 kHz mono, and
run through the same Vosk model the wake-word engine uses — no second
download. Best-effort quality (noisy rooms degrade it); files never
leave the device. Needs RECORD_AUDIO.

## 8. Charge-complete alert (`power/ChargeAlert.kt`)

Manifest receiver on plug/unplug: announces "charger lag gaya" /
"charger nikaal diya", and "Sir, charging complete — 100%" the moment
full charge arrives (a 15-minute monitor worker self-cancels after).
Complements the battery guardian — that one watches LOW battery
(20%/10%); this one watches the charge cycle. No conflict.

## 9. Rain alert — Lahore (`power/RainAlerts.kt`)

Morning check at 7 AM: max hourly precipitation probability over the
next 12h for Lahore via Open-Meteo (free, keyless). At 60%+:
"aaj barish ka chance 70% hai, sir — umbrella le lena".
Fixed to Lahore by design (no location permission needed).

## 10. App lock (`power/AppLock.kt`)

Master toggle gates Baloch itself: when ON, the parent checks
`isAppLockOn(context)` in MainActivity and calls
`AppLockGate.requireUnlock()` — system biometric prompt via the
existing `BiometricLock`, PIN fallback via the existing `PinManager`
(the Voice Lock PIN — one PIN for the whole app, SHA-256 in DataStore).
Reused, not duplicated.

## Voice control (`power/PowerIntents.kt` + `power/PowerToggles.kt`)

`matchPower(input)` matches all ten features trilingually — English,
Urdu script, Devanagari, and Roman Urdu ("driving mode on karo",
"aaj ka lafz", "meeting record karo", "petrol price 278",
"dollar 290 par alert"). `PowerToggles.applyHit()` is the single
execution path for voice AND settings — one path, no divergence.

---

## PERMISSIONS.md additions

| Permission | Used by | Notes |
|---|---|---|
| `RECEIVE_SMS` | Driving mode | See incoming SMS to auto-reply; body parsed from broadcast PDUs (READ_SMS not needed) |
| `SEND_SMS` | Driving mode | Sends the "I'm driving" auto-reply |
| `ACTIVITY_RECOGNITION` | (declared, unused) | Reserved for future driving auto-detect; current build is manual toggle + timer |
| `BIND_NOTIFICATION_LISTENER` | Driving mode | Reads other apps' notifications aloud; user grants "Notification access" in Settings manually |
| `RECORD_AUDIO` | Meeting recorder | Already declared; asked when the recorder is first enabled |
| `POST_NOTIFICATIONS` | All ten alerts | Already declared |

## Honest source/limit notes (for the in-app "about" copy)

- Cricket: ESPNcricinfo RSS (news, not live scores) — degrades openly.
- Currency: open.er-api.com free tier, 6-hour poll.
- Ramadan: Aladhan API, Karachi method; off-season dates are arithmetic estimates.
- Petrol: NO source — user-taught by design; never presented as live data.
- Rain: Open-Meteo, Lahore fixed; probability ≠ promise.
- Transcription: on-device Vosk, best-effort; needs the speech model installed.
