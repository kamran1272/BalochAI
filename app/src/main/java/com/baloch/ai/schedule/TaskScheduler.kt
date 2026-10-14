package com.baloch.ai.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Arms and disarms scheduled-task triggers.
 *
 *  - ONCE   → exact AlarmManager alarm (RTC_WAKEUP) → [TaskFireReceiver].
 *  - DAILY / WEEKLY → WorkManager periodic work → [TaskWorker].
 *
 * Fires in BOTH Manual and JARVIS mode: scheduled tasks are explicit user
 * orders, never proactivity, so ModeManager is never consulted.
 */
object TaskScheduler {

    /** Intent extra carrying the ScheduledTask id. */
    const val EXTRA_TASK_ID = "com.baloch.ai.schedule.TASK_ID"

    /** WorkManager input-data key carrying the ScheduledTask id. */
    const val KEY_TASK_ID = "taskId"

    private const val WORK_PREFIX = "baloch_task_"

    fun schedule(context: Context, task: ScheduledTask) {
        val app = context.applicationContext
        when (task.recurrence) {
            Recurrence.ONCE -> scheduleExact(app, task)
            Recurrence.DAILY, Recurrence.WEEKLY -> schedulePeriodic(app, task)
        }
    }

    fun cancel(context: Context, id: Long) {
        val app = context.applicationContext
        // FLAG_NO_CREATE: null when no alarm was armed — skip silently then.
        alarmIntent(app, id, PendingIntent.FLAG_NO_CREATE)?.let { pi ->
            (app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
            pi.cancel()
        }
        WorkManager.getInstance(app).cancelUniqueWork(WORK_PREFIX + id)
    }

    // ------------------------------------------------------------------
    // ONCE → exact alarm
    // ------------------------------------------------------------------

    private fun alarmIntent(app: Context, id: Long, flags: Int): PendingIntent? {
        val intent = Intent(app, TaskFireReceiver::class.java)
            .putExtra(EXTRA_TASK_ID, id)
        // Extras are NOT part of PendingIntent matching, so FLAG_NO_CREATE
        // finds the armed alarm even though the extra is set here.
        return PendingIntent.getBroadcast(
            app, id.toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or flags,
        )
    }

    private fun scheduleExact(app: Context, task: ScheduledTask) {
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmIntent(app, task.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        // Android 12+ needs the SCHEDULE_EXACT_ALARM grant for exact alarms;
        // without it we degrade to an inexact alarm instead of crashing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.triggerAtMillis, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.triggerAtMillis, pi)
        }
    }

    // ------------------------------------------------------------------
    // DAILY / WEEKLY → WorkManager periodic work
    // ------------------------------------------------------------------

    private fun schedulePeriodic(app: Context, task: ScheduledTask) {
        val intervalHours = if (task.recurrence == Recurrence.DAILY) 24L else 24L * 7
        val delayMs = (task.triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = PeriodicWorkRequestBuilder<TaskWorker>(intervalHours, TimeUnit.HOURS)
            .setInputData(Data.Builder().putLong(KEY_TASK_ID, task.id).build())
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag(WORK_PREFIX + task.id)
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            WORK_PREFIX + task.id,
            ExistingPeriodicWorkPolicy.REPLACE,
            request,
        )
    }
}
