package com.baloch.ai.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import java.util.concurrent.TimeUnit

/**
 * CHARGE-COMPLETE ALERT — speaks when the charger connects and when the
 * battery hits 100%. 🔌
 *
 * No conflict with the battery guardian: that one watches LOW battery
 * (20%/10% thresholds); this one watches the CHARGE cycle (plug-in +
 * full). Different thresholds, different moments.
 *
 * Mechanics: [ChargeAlertReceiver] is a manifest receiver for
 * ACTION_POWER_CONNECTED / ACTION_POWER_DISCONNECTED. On connect it reads
 * the sticky battery level immediately; if already ~full it announces
 * completion, otherwise it announces the plug-in and arms
 * [ChargeMonitorWorker] (15-min ticks) which announces 100% the moment it
 * arrives and then cancels itself. On disconnect the monitor is cancelled.
 */
object ChargeAlert {

    fun isOn(context: Context): Boolean = PowerPrefs.isOn(context, PowerPrefs.CHARGE)

    /** The toggle is the whole feature — receiver + monitor both check it. */
    fun setOn(context: Context, on: Boolean) {
        PowerPrefs.setOn(context, PowerPrefs.CHARGE, on)
        if (!on) ChargeMonitorWorker.cancel(context)
    }
}

class ChargeAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ChargeAlert.isOn(context)) return
        val lang = PowerPrefs.activeLang(context)
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                val pct = batteryPct(context)
                if (pct >= 99) {
                    announceFull(context, lang)
                } else {
                    val text = L3(
                        "Charger connected, sir — at $pct%. 🔌",
                        "چارجر لگ گیا، سر — $pct فیصد پر ہے۔ 🔌",
                        "चार्जर लग गया, सर — $pct प्रतिशत पर है। 🔌",
                    ).pick(lang)
                    PowerNotify.notify(
                        context,
                        L3("🔌 Charging", "🔌 چارجنگ", "🔌 चार्जिंग").pick(lang),
                        text, 6701,
                    )
                    PowerSpeak.speak(context, lang, text)
                    ChargeMonitorWorker.schedule(context)
                }
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                ChargeMonitorWorker.cancel(context)
                val text = L3(
                    "Charger disconnected, sir.",
                    "چارجر نکال دیا گیا، سر۔",
                    "चार्जर निकाल दिया गया, सर।",
                ).pick(lang)
                PowerNotify.notify(
                    context,
                    L3("🔌 Charger removed", "🔌 چارجر ہٹا دیا", "🔌 चार्जर हटाया").pick(lang),
                    text, 6702,
                )
            }
        }
    }

    companion object {
        fun batteryPct(context: Context): Int {
            val sticky: Intent? = context.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        }

        fun announceFull(context: Context, lang: BalochLanguage) {
            val text = L3(
                "Sir, charging complete — 100%! Unplug and shine. ⚡✅",
                "سر، چارجنگ مکمل — 100 فیصد! چارجر نکالیں۔ ⚡✅",
                "सर, चार्जिंग पूरी — 100 प्रतिशत! चार्जर निकालें। ⚡✅",
            ).pick(lang)
            PowerNotify.notify(
                context,
                L3("⚡ Fully charged", "⚡ چارج مکمل", "⚡ चार्ज पूरा").pick(lang),
                text, 6703,
            )
            PowerSpeak.speak(context, lang, text)
            ChargeMonitorWorker.cancel(context) // job done — stop ticking
        }
    }
}

/**
 * Ticks every 15 min while a charge is in progress; announces 100% once
 * and cancels itself. Also self-cancels if the charger was pulled without
 * the disconnect broadcast arriving.
 */
class ChargeMonitorWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!ChargeAlert.isOn(ctx)) {
            ChargeMonitorWorker.cancel(ctx)
            return Result.success()
        }
        val pct = ChargeAlertReceiver.batteryPct(ctx)
        if (pct < 0) return Result.success() // sensor unavailable — next tick
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        if (!bm.isCharging) {
            ChargeMonitorWorker.cancel(ctx)
            return Result.success()
        }
        if (pct >= 100) {
            ChargeAlertReceiver.announceFull(ctx, PowerPrefs.activeLang(ctx))
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "charge_monitor"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<ChargeMonitorWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE, ExistingPeriodicWorkPolicy.KEEP, req
            )
        }

        fun cancel(context: Context) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE) }
        }
    }
}
