package com.baloch.ai.engine.pro

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.BatteryManager
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baloch.ai.R
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import com.baloch.ai.settings.isJarvisMode
import java.util.concurrent.TimeUnit

/**
 * Battery guardian — proactive low-battery alerts.
 *
 * DUAL MODE rule: the FIRST thing this worker does is check
 * [isJarvisMode]. In MANUAL mode it stands down silently — no alerts,
 * no notifications, no interruptions. Explicit user orders work in both
 * modes; only unasked-for proactivity is gated.
 *
 * Thresholds 20% / 10%, each notified once per discharge cycle (the
 * flags reset when the phone starts charging or hits full). A "fully
 * charged" notice fires once per charge cycle too.
 */
class BatteryGuardianWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // MANUAL mode = silent. This line is the whole dual-mode contract.
        if (!isJarvisMode(applicationContext)) return Result.success()
        // Settings toggle (default ON): Kamran can silence the guardian entirely.
        if (!applicationContext.getSharedPreferences("baloch_settings", Context.MODE_PRIVATE)
                .getBoolean("battery_guardian_on", true)
        ) return Result.success()

        val bm = applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        if (pct < 0) return Result.success() // sensor unavailable — try next tick

        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lang = BalochLanguage.fromCode(
            applicationContext.getSharedPreferences("baloch_prefs", Context.MODE_PRIVATE)
                .getString("language", BalochLanguage.ENGLISH.code)
                ?: BalochLanguage.ENGLISH.code
        )

        if (charging || pct >= 100) {
            // New charge cycle: allow the low-battery alerts again.
            prefs.edit().remove(KEY_NOTIFIED_20).remove(KEY_NOTIFIED_10).apply()
            if (pct >= 99 && !prefs.getBoolean(KEY_NOTIFIED_FULL, false)) {
                notify(FULL_TITLE.pick(lang), FULL_TEXT.pick(lang), ID_FULL)
                prefs.edit().putBoolean(KEY_NOTIFIED_FULL, true).apply()
            }
            return Result.success()
        }

        prefs.edit().remove(KEY_NOTIFIED_FULL).apply()

        when {
            pct <= 10 && !prefs.getBoolean(KEY_NOTIFIED_10, false) -> {
                notify(TITLE.pick(lang), TEXT_10.pick(lang).format(pct), ID_LOW)
                prefs.edit().putBoolean(KEY_NOTIFIED_10, true)
                    .putBoolean(KEY_NOTIFIED_20, true).apply()
            }
            pct <= 20 && !prefs.getBoolean(KEY_NOTIFIED_20, false) -> {
                notify(TITLE.pick(lang), TEXT_20.pick(lang).format(pct), ID_LOW)
                prefs.edit().putBoolean(KEY_NOTIFIED_20, true).apply()
            }
        }
        return Result.success()
    }

    private fun notify(title: String, text: String, id: Int) {
        try {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Baloch battery guardian", NotificationManager.IMPORTANCE_HIGH)
            )
            val n = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(id, n)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied — stay silent, try again next tick.
        } catch (_: Exception) {
            // Never crash a background worker.
        }
    }

    companion object {
        private const val PREFS = "battery_guardian"
        private const val KEY_NOTIFIED_20 = "notified_20"
        private const val KEY_NOTIFIED_10 = "notified_10"
        private const val KEY_NOTIFIED_FULL = "notified_full"
        private const val CHANNEL_ID = "baloch_guardian"
        private const val ID_LOW = 4101
        private const val ID_FULL = 4102
        private const val UNIQUE_NAME = "battery_guardian"

        private val TITLE = L3("◈ Baloch — battery", "◈ بلوچ — بیٹری", "◈ बलोच — बैटरी")
        private val FULL_TITLE = L3("◈ Baloch — charged", "◈ بلوچ — چارج مکمل", "◈ बलोच — चार्ज पूरा")
        private val TEXT_20 = L3(
            "Sir, battery at %d%% — might be a good time to plug in. 🔋",
            "سر، بیٹری %d فیصد رہ گئی ہے — چارج لگا لیں تو بہتر ہوگا۔ 🔋",
            "सर, बैटरी %d प्रतिशत रह गई है — चार्ज लगा लें तो बेहतर होगा। 🔋",
        )
        private val TEXT_10 = L3(
            "Sir, battery at %d%% — critical! Please plug in now. 🪫",
            "سر، بیٹری %d فیصد رہ گئی ہے — نازک صورتحال! ابھی چارج لگائیں۔ 🪫",
            "सर, बैटरी %d प्रतिशत रह गई है — नाज़ुक स्थिति! अभी चार्ज लगाएँ। 🪫",
        )
        private val FULL_TEXT = L3(
            "Fully charged, sir — unplug and shine. ⚡✅",
            "مکمل چارج ہو گئی، سر — چارجر نکالیں اور چمکیں۔ ⚡✅",
            "पूरी तरह चार्ज हो गई, सर — चार्जर निकालें और चमकें। ⚡✅",
        )

        /** Called once from BalochApp.onCreate. KEEP = never double-schedule. */
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<BatteryGuardianWorker>(30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, req
            )
        }
    }
}
