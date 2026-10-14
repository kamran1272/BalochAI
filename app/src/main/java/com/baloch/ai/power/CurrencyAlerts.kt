package com.baloch.ai.power

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * CURRENCY RATE ALERTS — "alert me when the dollar crosses 290".
 *
 * Watches USD→PKR via the same free, keyless rates API the currency
 * converter uses (https://open.er-api.com/v6/latest/USD), polled every
 * 6 hours. One-shot per crossing: the alert fires once when the rate
 * moves at/above the threshold, and re-arms only after the rate drops
 * back below it — no repeat buzzing every 6 hours.
 *
 * The last fetched rate is kept in [PowerPrefs.CURRENCY_LAST_RATE] so
 * the settings row and the "dollar rate?" voice query answer instantly
 * (offline), labelled with the check time.
 */
object CurrencyAlerts {

    fun isOn(context: Context): Boolean = PowerPrefs.isOn(context, PowerPrefs.CURRENCY)

    fun setOn(context: Context, on: Boolean) {
        PowerPrefs.setOn(context, PowerPrefs.CURRENCY, on)
        if (on) CurrencyWorker.schedule(context) else CurrencyWorker.cancel(context)
    }

    fun threshold(context: Context): Double =
        PowerPrefs.getDouble(context, PowerPrefs.CURRENCY_THRESHOLD, 290.0)

    fun setThreshold(context: Context, value: Double) {
        val v = value.coerceIn(1.0, 10000.0)
        PowerPrefs.setDouble(context, PowerPrefs.CURRENCY_THRESHOLD, v)
        // New threshold = new watch: re-arm so the next tick evaluates fresh.
        PowerPrefs.setOn(context, PowerPrefs.CURRENCY_ALERTED_ABOVE, false)
    }

    fun lastRate(context: Context): Double? =
        if (PowerPrefs.hasDouble(context, PowerPrefs.CURRENCY_LAST_RATE))
            PowerPrefs.getDouble(context, PowerPrefs.CURRENCY_LAST_RATE, 0.0) else null

    /** Instant offline answer for "dollar kitna hai?". */
    fun rateText(context: Context, lang: BalochLanguage): String {
        val rate = lastRate(context)
        val t = threshold(context)
        return if (rate == null) {
            L3(
                "No rate fetched yet, sir — turn the alert on and I'll check within 6 hours.",
                "ابھی کوئی ریٹ نہیں ملا، سر — الرٹ آن کریں، 6 گھنٹے میں چیک کر لوں گا۔",
                "अभी कोई रेट नहीं मिला, सर — अलर्ट ऑन करें, 6 घंटे में चेक कर लूँगा।",
            ).pick(lang)
        } else {
            val state = if (rate >= t)
                L3("above your $t alert line 📈", "آپ کی $t والی لائن سے اوپر 📈", "आपकी $t वाली लाइन से ऊपर 📈").pick(lang)
            else
                L3("below your $t alert line", "آپ کی $t والی لائن سے نیچے", "आपकी $t वाली लाइन से नीचे").pick(lang)
            L3(
                "Dollar is Rs ${"%.2f".format(rate)}, sir — $state.",
                "ڈالر ${"%.2f".format(rate)} روپے ہے، سر — $state۔",
                "डॉलर ${"%.2f".format(rate)} रुपये है, सर — $state।",
            ).pick(lang)
        }
    }

    fun fetchUsdPkr(): Double? {
        return try {
            val url = URL("https://open.er-api.com/v6/latest/USD")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "BalochAI/1.0")
            }
            try {
                conn.connect()
                if (conn.responseCode != 200) return null
                val rates = JSONObject(conn.inputStream.bufferedReader().readText())
                    .optJSONObject("rates") ?: return null
                if (!rates.has("PKR")) return null
                rates.getDouble("PKR").takeIf { it > 0 }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** 6-hour poll with one-shot crossing logic. */
class CurrencyWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!CurrencyAlerts.isOn(ctx)) return Result.success()

        val rate = CurrencyAlerts.fetchUsdPkr() ?: return Result.retry()
        PowerPrefs.setDouble(ctx, PowerPrefs.CURRENCY_LAST_RATE, rate)
        PowerPrefs.setLong(ctx, PowerPrefs.CURRENCY_LAST_CHECK, System.currentTimeMillis())

        val threshold = CurrencyAlerts.threshold(ctx)
        val alerted = PowerPrefs.isOn(ctx, PowerPrefs.CURRENCY_ALERTED_ABOVE)
        val lang = PowerPrefs.activeLang(ctx)

        when {
            rate >= threshold && !alerted -> {
                val text = L3(
                    "Sir, the dollar has crossed Rs ${"%.0f".format(threshold)} — now at Rs ${"%.2f".format(rate)}. 📈",
                    "سر، ڈالر ${"%.0f".format(threshold)} روپے سے اوپر چلا گیا — اب ${"%.2f".format(rate)} روپے ہے۔ 📈",
                    "सर, डॉलर ${"%.0f".format(threshold)} रुपये से ऊपर चला गया — अब ${"%.2f".format(rate)} रुपये है। 📈",
                ).pick(lang)
                PowerNotify.notify(
                    ctx,
                    L3("💵 Dollar alert", "💵 ڈالر الرٹ", "💵 डॉलर अलर्ट").pick(lang),
                    text, 6401,
                )
                PowerSpeak.speak(ctx, lang, text)
                PowerPrefs.setOn(ctx, PowerPrefs.CURRENCY_ALERTED_ABOVE, true)
            }
            rate < threshold && alerted -> {
                // Back under the line — re-arm for the next crossing.
                PowerPrefs.setOn(ctx, PowerPrefs.CURRENCY_ALERTED_ABOVE, false)
            }
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "currency_alerts"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<CurrencyWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }
    }
}
