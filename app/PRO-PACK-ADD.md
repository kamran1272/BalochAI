# PRO Feature Pack — merge notes for the parent

All code is in place. Merge the two sections below into the app's
`README.md` and `PERMISSIONS.md` respectively (create them if missing).

---

## README addition

### PRO Feature Pack

Ten pro features, all real Android APIs, all trilingual (English / Urdu /
Hindi + first-class Roman Urdu patterns), all in Baloch's warm "sir"
personality.

**1. Smart briefing** — "good morning", "brief me", "subah bakhair",
"آج کی briefing", "सुबह की खबर"
One premium report: time-of-day greeting → current time → battery %
(+ charging) → top 3 notifications (or "all quiet") → Lahore weather via
Open-Meteo (fixed coordinates 31.52, 74.36 — no location permission) →
one daily thought. Weather failure silently omits the line, never an
error. Builder: `engine/pro/BriefingHelper.kt`.

**2. App usage insights** — "screen time", "app usage", "aaj kitna time",
"time on instagram", "اسکرین ٹائم", "स्क्रीन टाइम"
Top 5 apps by foreground minutes + daily total; per-app filter via
"on {name}". Needs Usage access (see PERMISSIONS addition).
`engine/pro/UsageHelper.kt`.

**3. Routines / macros** — "when I say {trigger}, {commands}",
"jab main {trigger} kahun, {commands}"
Each command clause (split on "and"/"aur"/،/اور/और) is parsed with the
normal intent matcher; safe actions are stored as JSON in
`baloch_routines.json`. Built-ins: "good night" / "shab bakhair" /
"شب بخیر" / "शुभ रात्रि" → DND on + brightness 20% (+ alarm suggestion);
"meeting mode" → DND on + auto-off in 60 min via
`DeviceAction.DisableDndDelayed` → AlarmManager → `DndOffReceiver`.
Risky steps (calls/SMS) are never stored in routines.
`engine/pro/RoutineStore.kt`.

**4. Calculator + currency converter** — "calculate 25% of 800",
"5+5 kitna hota hai", "حساب", "गणना", "convert 50 dollars to pkr",
"50 dollar ko pkr me convert karo"
Hand-written recursive-descent parser (+ − × ÷ % ^, parentheses,
decimals; "X% of Y" special-cased). Rates from the free keyless
`open.er-api.com` (8s timeout), cached per base currency; offline falls
back to cache with an honest "rates may be dated, sir" note.
`engine/pro/CalcParser.kt`, `engine/pro/CurrencyConverter.kt`.

**5. WhatsApp draft** — "whatsapp ammi: hello", "{name} ko whatsapp karo: {text}"
Resolves the contact first (CONTACT_NOT_FOUND when missing), then opens
WhatsApp with the message pre-filled — SAFE, never auto-sends. Falls
back to `api.whatsapp.com/send` in the browser when WhatsApp isn't
installed. `DeviceAction.WhatsAppDraft`.

**6. Clipboard history** — "what did I copy", "clipboard history",
"میں نے کیا کاپی کیا", "मैंने क्या कॉपी किया"
Last 20 clips, persisted app-privately, searchable ("clipboard about
milk"). Honest limit: on Android 10+ the system only delivers clipboard
changes to the foreground app, so clips copied while Baloch wasn't
active are NOT captured — no API can read them in the background.
`engine/pro/ClipboardWatcher.kt` (listener registered in
`BalochApp.onCreate`).

**7. Battery guardian** — proactive, JARVIS mode only
`BatteryGuardianWorker` (30-min periodic WorkManager, KEEP policy)
checks mode FIRST: in MANUAL mode it returns silently. Thresholds 20%
/ 10% (once per discharge cycle — flags reset on charge/full) + one
"fully charged" notice. High-priority "◈ Baloch" notifications,
trilingual via the saved language. `engine/pro/BatteryGuardianWorker.kt`.

**8. Emergency** — "emergency", "help me", "bachao", "madad", "بچاؤ", "बचाओ"
`DeviceAction.EmergencyCall` is RISKY → always shows the confirm dialog
first. Number comes from `EmergencyStore` (Settings → Emergency contact
via `ui/ProSettingsRows.kt` → `IceContactRow` — standalone file, wire it
into MainScreen.kt's Settings section per the placement comment inside).
Unset → friendly "set your emergency contact first, sir".

**9. DND control** — "dnd on/off", "silence my phone", "phone silent karo",
"do not disturb", "ڈسٹرب نہ کرو", "परेशान न करो"
`DeviceAction.SetDnd` via `NotificationManager.setInterruptionFilter`.
Needs Do-Not-Disturb access (see PERMISSIONS addition).

**10. App search** — "find app X", "app dhoondo X", "search app X"
Text-only: lists up to 5 matching launchable apps and tells Kamran to
say "open {name}". No new DeviceAction.

### Tone

Every new string follows the friendly-persona order: warm, a little
playful, "sir" with affection, celebrations ("Ho gaya, sir! ✅"),
gentle humour — never cold error dumps. Failures sound like
"Hmm, ye nahi ho paya, sir — …" with a next step, never a stack trace.

---

## PERMISSIONS addition

### New in the PRO pack

**INTERNET** (normal permission — granted at install)
Used for: Lahore weather (Open-Meteo) in the smart briefing, and live
currency rates (open.er-api.com). No API keys, no location permission —
weather uses fixed Lahore coordinates. Both degrade gracefully offline
(weather line omitted; converter uses cached rates with a "may be dated"
note).

**PACKAGE_USAGE_STATS** (special access — NOT a runtime permission)
Used for: App usage insights ("screen time").
Grant steps — there is no system prompt; Kamran must do this once:
`Settings → Apps → Special app access → Usage access → Baloch → Allow`
(On some phones: `Settings → Privacy → Permission manager →` … no —
it is specifically under *Special app access*, not the normal permission
manager.)
Until granted, "screen time" replies with these exact steps instead of
failing. Check via `AppOpsManager.unsafeCheckOpNoThrow`.

**ACCESS_NOTIFICATION_POLICY** (special access — NOT a runtime permission)
Used for: DND control ("dnd on", "good night", "meeting mode") and the
automatic DND lift after meeting mode.
Grant steps: `Settings → Apps → Baloch → Do Not Disturb access → Allow`
(Samsung: `Settings → Apps → Special access → Do Not Disturb access`).
Until granted, DND replies ask for the grant with the path. If the grant
is revoked later, the meeting-mode auto-lift posts an honest notification
instead of failing silently (`DndOffReceiver`).

**Existing permissions reused:** POST_NOTIFICATIONS (battery guardian +
DND-lift notifications; silent when denied), CALL_PHONE (emergency call,
confirmed first), READ_CONTACTS (WhatsApp draft contact resolution),
SCHEDULE_EXACT_ALARM is NOT needed for the DND auto-lift — it uses an
inexact `setAndAllowWhileIdle` alarm on purpose.
