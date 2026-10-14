# Task Scheduling — README section + permissions

Merge the two sections below into the project README / PERMISSIONS docs.
(Parent merges — this file is the source text.)

---

## How scheduling works

Say it in English, Roman Urdu, Urdu or Hindi and Baloch schedules it:

- "remind me tomorrow at 9am to call Ahmed"
- "mujhe kal subah 7 baje utha dena"
- "every day at 8am give me briefing"
- "roz raat 11 baje wifi band kar dena"
- "schedule: open Spotify at 6pm"
- "remind me in 30 minutes to check the oven"
- "every monday at 9am send me the briefing"

The brain shows a trilingual confirmation ("Scheduled, sir — I will …").
On confirm, the task is saved to `scheduled_tasks.json` and armed:

| Recurrence | Trigger | Fired by |
|---|---|---|
| ONCE | Exact alarm (`AlarmManager.setExactAndAllowWhileIdle`, RTC_WAKEUP) | `TaskFireReceiver` |
| DAILY | WorkManager periodic work, 24 h interval | `TaskWorker` |
| WEEKLY | WorkManager periodic work, 7 d interval | `TaskWorker` |

At fire time the inner command runs through the **real brain**
(`RuleBasedBrain`) with the **real device actions** (`DeviceActions`), then
Baloch posts a "◈ Baloch" notification **and speaks the result aloud**.
One-shot tasks delete themselves after firing; recurring tasks advance to
their next trigger.

**Modes:** scheduled tasks are *explicit user orders* — they fire in BOTH
Manual and JARVIS mode. They are never gated on mode. (Proactive JARVIS
behaviour is separate; a schedule is an order, not proactivity.)

**Safety at fire time:**
- RISKY actions (call, SMS) are NEVER auto-executed — the notification says
  "That needs your confirmation, sir — open Baloch to approve it."
- Mode changes don't run from scheduled tasks (handled by the in-app
  ViewModel only).
- `AddNote` / `RememberFact` write straight to the stores.
- Failed / permission-denied actions append their message to the result.

**Screens:** `ui/ScheduleScreen.kt` lists every task (next fire time,
command, ONCE/DAILY/WEEKLY badge, weekday chips for weekly, enable switch,
delete). Parent wires it to `TaskStore` + `TaskScheduler.cancel/schedule`.

**Reboot:** `RescheduleReceiver` re-arms every enabled task on
`BOOT_COMPLETED` (needs `RECEIVE_BOOT_COMPLETED` — see below).

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

## Files (package `com.baloch.ai.schedule`, one UI file)

- `schedule/ScheduledTask.kt` — data class + `Recurrence` + `nextTriggerAfter()`
- `schedule/TaskStore.kt` — JSON CRUD in `filesDir/scheduled_tasks.json`
- `schedule/DateTimeParser.kt` — multilingual regex datetime parser
- `schedule/ScheduleKit.kt` — parent facade: `parse()` / `confirmText()` / `commit()`
- `schedule/TaskScheduler.kt` — arms/disarms alarms + WorkManager work
- `schedule/TaskExecutor.kt` — shared brain→execute→notify pipeline
- `schedule/TaskFireReceiver.kt` — one-shot alarm receiver
- `schedule/RescheduleReceiver.kt` — BOOT_COMPLETED re-arm
- `schedule/TaskWorker.kt` — WorkManager worker for DAILY/WEEKLY
- `ui/ScheduleScreen.kt` — task list UI (holographic cyan)
