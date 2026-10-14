# PERMISSIONS.md — Why Baloch asks for each permission

Baloch is 100% on-device. Permissions exist only to let it *act* for Kamran. Denying one never crashes — Baloch just says which grant it needs.

| Permission | Why | How to grant |
|---|---|---|
| RECORD_AUDIO | Voice commands, wake word, voice-lock enrolment | Runtime prompt on mic tap |
| POST_NOTIFICATIONS | Reminders, battery alerts, check-ins | Runtime prompt (Android 13+) |
| BIND_NOTIFICATION_LISTENER_SERVICE | Read/summarise notifications | Settings → Apps → Special access → Notification access → Baloch |
| SCHEDULE_EXACT_ALARM / SET_ALARM | Alarms, timed reminders, scheduled tasks | Settings → Apps → Baloch → Alarms & reminders |
| CALL_PHONE | Voice-dialling contacts (always confirmed first) | Runtime prompt |
| SEND_SMS | SMS on command (always confirmed first) | Runtime prompt |
| READ_CONTACTS | Resolve "call Ammi" → number | Runtime prompt |
| BLUETOOTH_CONNECT | Bluetooth toggle | Runtime prompt (Android 12+) |
| CAMERA | Torch/flashlight | Runtime prompt |
| WRITE_SETTINGS | Brightness control | Settings → Apps → Baloch → Modify system settings |
| RECEIVE_BOOT_COMPLETED | Re-schedule tasks after reboot | Granted at install |
| FOREGROUND_SERVICE / FOREGROUND_SERVICE_MICROPHONE | Always-on listening service | Granted at install |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Keep the listener alive in Doze | Settings prompt → "Allow" |
| PACKAGE_USAGE_STATS | App-usage insights | Settings → Special access → Usage access → Baloch |
| ACCESS_NOTIFICATION_POLICY | Do-Not-Disturb control | Settings → Special access → Do Not Disturb access → Baloch |
| READ_PHONE_STATE | Caller ID: detect incoming calls (no-role path) | Runtime prompt |
| READ_CALL_LOG | Caller ID screen: recent unknown callers | Runtime prompt |
| FOREGROUND_SERVICE_CAMERA | Camera hand-gesture service (Android 14 rule) | Granted at install |
| ACCESS_NETWORK_STATE | Offline detection, update checker | Granted at install |
| REQUEST_INSTALL_PACKAGES | System install prompt for updates (never silent) | Settings → Apps → Baloch → Install unknown apps |
| ACCESS_FINE_LOCATION / ACCESS_BACKGROUND_LOCATION | Location reminders (geofence), SOS location SMS | Runtime prompt → "Allow all the time" for geofences |

**Privacy promise:** your voice, memory and data never leave the phone. No accounts, no cloud, no analytics.

---

## PERMISSIONS — scheduling additions

### `SCHEDULE_EXACT_ALARM` (already declared)
One-shot tasks use exact alarms so "tomorrow at 9:00 AM" fires at 9:00,
even in Doze. Grant path on Android 12+:

1. If `AlarmManager.canScheduleExactAlarms()` is false, Baloch degrades to
   an inexact `setAndAllowWhileIdle` alarm rather than crashing.
2. To grant: Settings → Apps → Baloch → Alarms & reminders → Allow.
   (Parent may add an in-app "grant exact alarms" deep-link later via
   `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.)

Note: on Android 14+, the system may revoke this for apps that don't use
alarms for user-visible features — Baloch's alarms always end in a
notification + voice, which qualifies.

### `RECEIVE_BOOT_COMPLETED` (add — see manifest-snippets-schedule.txt)
Normal-level permission, granted automatically at install. Lets
`RescheduleReceiver` re-arm alarms and WorkManager schedules after reboot
so no scheduled task is silently lost. No user action needed.

### Notifications
Scheduled-task results post to the `baloch_scheduled` channel
(IMPORTANCE_HIGH) and are spoken via TTS. Requires the already-declared
`POST_NOTIFICATIONS` grant, same as reminders.

---

---

## Permissions

- **Microphone** (`RECORD_AUDIO`, runtime): without it the service posts a
  one-time "Baloch can't listen yet" notification and stops — it never
  spins silently.
- **Notifications** (`POST_NOTIFICATIONS`, runtime): required for the
  persistent "listening" notification; on Android 14 a mic foreground
  service **cannot run without a visible notification**.
- **Battery-optimisation exemption** (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`):
  prompt flow for the settings screen —
  1. `PowerManager.isIgnoringBatteryOptimizations(packageName)` — if true, done.
  2. Else fire `Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)`
     with `data = Uri.parse("package:$packageName")`.
  3. Explain first (the OS shows its own scary dialog): "Baloch needs this
     so always-on listening isn't killed while your phone sleeps."
- **Wake lock** (`WAKE_LOCK`, normal permission): held only while the
  service runs; released on stop/destroy.
- **Full-screen intent** (`USE_FULL_SCREEN_INTENT`, Android 14): lets the
  risky-command confirmation pop over the lock screen when the display is on.

---

## OEM whitelisting (Xiaomi / Oppo / Vivo / Samsung)

Aggressive OEM skins kill background services. If always-on listening, gestures, or scheduled tasks stop working after a while:

- **Xiaomi (MIUI/HyperOS):** Settings → Apps → Baloch → Autostart → ON; Battery saver → No restrictions.
- **Oppo/Realme (ColorOS):** Settings → Battery → App battery management → Baloch → Allow background activity; enable Autostart in App management.
- **Vivo (Funtouch):** Settings → Battery → Background power consumption management → Baloch → Allow.
- **Samsung (One UI):** Settings → Battery → Background usage limits → Never sleeping apps → add Baloch.
- **All:** Settings → Apps → Baloch → Battery → Unrestricted; grant the battery-optimisation exemption when Baloch asks.

## TTS voice data (one-time download)

Baloch's voice is on-device, but Google TTS needs the voice data pack for Urdu/Hindi once: Settings → System → Languages → Text-to-speech → Google TTS → Install voice data → اردو / हिन्दी. After that, Baloch speaks fully offline.

## Caller ID role (optional, improves announcements)

For system-level call screening: open the Call Log screen → "Set as call screener" (Android 9+). Without the role, Baloch still detects incoming calls via the phone-state path — announcements work, just without the screening hook.
