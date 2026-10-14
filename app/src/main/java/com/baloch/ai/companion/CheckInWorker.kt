package com.baloch.ai.companion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baloch.ai.MainActivity
import com.baloch.ai.R
import com.baloch.ai.settings.LanguageManager
import com.baloch.ai.settings.isJarvisMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Proactive companionship check-ins — JARVIS MODE ONLY.
 *
 * Kamran's order: in JARVIS mode Baloch "speaks up" — it asks about his
 * wellbeing and offers company when he seems idle or it's evening. In
 * MANUAL mode this worker stands down silently (first line of [doWork]).
 *
 * Policy:
 *  - Quota: at most 2/day on "normal", 1 per 2 days on "subtle", none on
 *    "off". Tracked in SharedPreferences ([PREFS]) via [KEY_LAST_DATE] +
 *    [KEY_COUNT].
 *  - Smart timing: fires only ~8pm–10pm local time, OR when 4+ hours have
 *    passed since the last interaction ([KEY_LAST_INTERACTION]).
 *  - Tapping the notification opens MainActivity.
 *
 * PARENT CONTRACT — [KEY_LAST_INTERACTION] ("last_interaction_at"):
 * the parent chat/voice pipeline MUST write System.currentTimeMillis()
 * to this key on every user interaction (typed or spoken). The worker only
 * reads it; it never writes it. Without this, the "4+ hours idle" branch
 * treats the user as always-idle (lastInteraction == 0L).
 *
 * Setting "checkin_level" (off / subtle / normal, default normal) lives in
 * the same prefs file; the settings UI writes it.
 */
class CheckInWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!isJarvisMode(ctx)) return Result.success()

        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val level = prefs.getString(KEY_LEVEL, LEVEL_NORMAL) ?: LEVEL_NORMAL
        if (level == LEVEL_OFF) return Result.success()

        if (!timingAllows(prefs)) return Result.success()
        if (!quotaAllows(prefs, level)) return Result.success()

        val lang = LanguageManager(ctx).current()
        val text = CHECKIN_MESSAGE.pick(lang)

        postNotification(ctx, text)
        recordCheckIn(prefs)
        return Result.success()
    }

    // ------------------------------------------------------------------
    // Policy
    // ------------------------------------------------------------------

    /** Evening window (~8pm–10pm device-local) OR 4+ hours since last chat. */
    private fun timingAllows(prefs: SharedPreferences): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val eveningWindow = hour in 20..21
        val lastInteraction = prefs.getLong(KEY_LAST_INTERACTION, 0L)
        val idleLongEnough = lastInteraction == 0L ||
            System.currentTimeMillis() - lastInteraction >= IDLE_THRESHOLD_MS
        return eveningWindow || idleLongEnough
    }

    /** Daily quota per check-in level. */
    private fun quotaAllows(prefs: SharedPreferences, level: String): Boolean {
        val today = todayString()
        val lastDate = prefs.getString(KEY_LAST_DATE, "")
        val countToday = if (lastDate == today) prefs.getInt(KEY_COUNT, 0) else 0
        return when (level) {
            LEVEL_SUBTLE -> {
                // At most one check-in per two days.
                if (countToday >= 1) return false
                lastDate != yesterdayString()
            }
            else -> countToday < MAX_PER_DAY_NORMAL // "normal": max 2/day
        }
    }

    private fun recordCheckIn(prefs: SharedPreferences) {
        val today = todayString()
        val lastDate = prefs.getString(KEY_LAST_DATE, "")
        val countToday = if (lastDate == today) prefs.getInt(KEY_COUNT, 0) else 0
        prefs.edit()
            .putString(KEY_LAST_DATE, today)
            .putInt(KEY_COUNT, countToday + 1)
            .apply()
    }

    private fun todayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun yesterdayString(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    // ------------------------------------------------------------------
    // Notification (tap -> MainActivity)
    // ------------------------------------------------------------------

    private fun postNotification(ctx: Context, text: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Baloch check-ins",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        val openApp = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            ctx,
            REQUEST_CODE,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("◈ Baloch")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        /** Unique WorkManager name. */
        const val WORK_NAME = "baloch_checkin"

        // ---- SharedPreferences ----
        const val PREFS = "baloch_companion"

        /** "off" | "subtle" (1 per 2 days) | "normal" (max 2/day). Default "normal". */
        const val KEY_LEVEL = "checkin_level"
        const val LEVEL_OFF = "off"
        const val LEVEL_SUBTLE = "subtle"
        const val LEVEL_NORMAL = "normal"

        /** yyyy-MM-dd of the last check-in (device-local). */
        const val KEY_LAST_DATE = "last_checkin_date"

        /** Check-ins already sent on [KEY_LAST_DATE]. */
        const val KEY_COUNT = "checkin_count"

        /**
         * Epoch-ms of the last user interaction (typed or spoken).
         * WRITTEN BY THE PARENT pipeline, READ by this worker.
         */
        const val KEY_LAST_INTERACTION = "last_interaction_at"

        private const val MAX_PER_DAY_NORMAL = 2
        private const val IDLE_THRESHOLD_MS = 4L * 60 * 60 * 1000

        private const val CHANNEL_ID = "baloch_checkins"
        private const val NOTIFICATION_ID = 4102
        private const val REQUEST_CODE = 4102

        /**
         * Enqueue the periodic check-in loop. The worker runs every 4 hours
         * but only notifies when the quota + smart-timing policy allows.
         * Call once at app start (idempotent — KEEP policy).
         */
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<CheckInWorker>(4, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }

        /** Fire one check-in evaluation immediately (still policy-gated). */
        fun poke(context: Context) {
            val req = androidx.work.OneTimeWorkRequestBuilder<CheckInWorker>().build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}
