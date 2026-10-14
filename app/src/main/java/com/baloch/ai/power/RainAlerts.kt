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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * RAIN ALERTS — "aaj barish ka chance 70% hai, sir — umbrella le lena ☔".
 *
 * Source: Open-Meteo (https://open-meteo.com), free and keyless — the
 * same API style as the weather fetcher. One check each morning (first
 * tick lands at 07:00 PKT): takes the MAX hourly precipitation
 * probability over the next 12 hours for Lahore and alerts when it
 * reaches 60%+.
 *
 * HONEST NOTES:
 *  - Fixed to LAHORE (31.5204, 74.3587) — Kamran's city. Not the device
 *    location (no location permission needed, by design).
 *  - A probability is not a promise: "70% chance" means 7-in-10 model
 *    runs saw rain, not "it will rain at 3pm".
 */
object RainAlerts {

    /** Alert threshold, in percent. */
    const val ALERT_AT_PCT = 60

    // Lahore, Pakistan — fixed by design (see KDoc).
    private const val LAT = 31.5204
    private const val LON = 74.3587

    fun isOn(context: Context): Boolean = PowerPrefs.isOn(context, PowerPrefs.RAIN)

    fun setOn(context: Context, on: Boolean) {
        PowerPrefs.setOn(context, PowerPrefs.RAIN, on)
        if (on) RainWorker.schedule(context) else RainWorker.cancel(context)
    }

    /** Last checked max probability (for the settings row), or null. */
    fun lastPct(context: Context): Int? {
        val v = PowerPrefs.getInt(context, PowerPrefs.RAIN_LAST_PCT, -1)
        return v.takeIf { it >= 0 }
    }

    /**
     * @return max hourly precipitation probability over the next 12h,
     *         or null when the API is unreachable.
     */
    fun fetchMaxProbability(): Int? {
        return try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$LAT&longitude=$LON" +
                    "&hourly=precipitation_probability" +
                    "&forecast_days=2&timezone=Asia%2FKarachi"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "BalochAI/1.0")
            }
            try {
                conn.connect()
                if (conn.responseCode != 200) return null
                val hourly = JSONObject(conn.inputStream.bufferedReader().readText())
                    .optJSONObject("hourly") ?: return null
                val times = hourly.optJSONArray("time") ?: return null
                val probs = hourly.optJSONArray("precipitation_probability") ?: return null

                val nowHour = ZonedDateTime.now(ZoneId.of("Asia/Karachi"))
                    .withMinute(0).withSecond(0).withNano(0)
                var max = 0
                for (i in 0 until minOf(times.length(), probs.length())) {
                    val t = runCatching {
                        ZonedDateTime.parse(times.getString(i) + ":00+05:00[Asia/Karachi]")
                    }.getOrNull() ?: continue
                    val hoursAhead = java.time.Duration.between(nowHour, t).toHours()
                    if (hoursAhead in 0..12) {
                        max = maxOf(max, probs.optInt(i, 0))
                    }
                }
                max
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Instant answer for "barish hogi?" — uses the last check, offline. */
    fun chanceText(context: Context, lang: BalochLanguage): String {
        val pct = lastPct(context)
        return if (pct == null) {
            L3(
                "No rain check yet, sir — turn the alert on and I'll check every morning.",
                "ابھی بارش کا کوئی چیک نہیں ہوا، سر — الرٹ آن کریں، ہر صبح چیک کروں گا۔",
                "अभी बारिश का कोई चेक नहीं हुआ, सर — अलर्ट ऑन करें, हर सुबह चेक करूँगा।",
            ).pick(lang)
        } else if (pct >= ALERT_AT_PCT) {
            L3(
                "High chance, sir — $pct% probability of rain in Lahore today. Umbrella le lena! ☔",
                "زیادہ امکان ہے، سر — لاہور میں آج بارش کا $pct فیصد امکان۔ چھتری لے لینا! ☔",
                "ज़्यादा संभावना है, सर — लाहौर में आज बारिश की $pct प्रतिशत संभावना। छाता ले लेना! ☔",
            ).pick(lang)
        } else {
            L3(
                "Looks clear, sir — only $pct% chance of rain in Lahore today. ☀️",
                "موسم صاف لگ رہا ہے، سر — لاہور میں آج صرف $pct فیصد بارش کا امکان۔ ☀️",
                "मौसम साफ़ लग रहा है, सर — लाहौर में आज सिर्फ़ $pct प्रतिशत बारिश की संभावना। ☀️",
            ).pick(lang)
        }
    }
}

/** Morning check: max 12h probability ≥ 60% → alert + speak. */
class RainWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!RainAlerts.isOn(ctx)) return Result.success()

        val pct = RainAlerts.fetchMaxProbability()
        if (pct == null) return Result.retry() // API hiccup — back off and retry
        PowerPrefs.setInt(ctx, PowerPrefs.RAIN_LAST_PCT, pct)
        PowerPrefs.setLong(ctx, PowerPrefs.RAIN_LAST_CHECK, System.currentTimeMillis())

        if (pct >= RainAlerts.ALERT_AT_PCT) {
            val lang = PowerPrefs.activeLang(ctx)
            val text = L3(
                "Sir, aaj barish ka chance $pct% hai — umbrella le lena! ☔",
                "سر، آج بارش کا چانس $pct فیصد ہے — چھتری لے لینا! ☔",
                "सर, आज बारिश का चांस $pct प्रतिशत है — छाता ले लेना! ☔",
            ).pick(lang)
            PowerNotify.notify(
                ctx,
                L3("☔ Rain alert — Lahore", "☔ بارش الرٹ — لاہور", "☔ बारिश अलर्ट — लाहौर").pick(lang),
                text, 6801,
            )
            PowerSpeak.speak(ctx, lang, text)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "rain_alerts"

        fun schedule(context: Context) {
            val now = ZonedDateTime.now(ZoneId.of("Asia/Karachi"))
            var next = now.withHour(7).withMinute(0).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = java.time.Duration.between(now, next).toMillis()
            val req = PeriodicWorkRequestBuilder<RainWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }
    }
}
