package com.baloch.ai.engine.pro

import com.baloch.ai.BalochApp
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.DAILY_THOUGHTS
import com.baloch.ai.brain.DeviceContext
import com.baloch.ai.brain.L3
import com.baloch.ai.connectivity.ConnectivityGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

/**
 * The smart briefing: one premium report built from live device state.
 *
 *   greeting by time of day → current time → battery → top 3 notifications
 *   (or "all quiet") → Lahore weather (Open-Meteo, no location permission
 *   needed — fixed coordinates) → one daily thought.
 *
 * Weather failure is swallowed: the line is simply omitted, never an
 * error. Nothing here throws.
 */
object BriefingHelper {

    /** Lahore, Pakistan — fixed coordinates, no location permission needed. */
    private const val WEATHER_URL =
        "https://api.open-meteo.com/v1/forecast?latitude=31.52&longitude=74.36" +
            "&current=temperature_2m,weather_code"

    /**
     * OFFLINE-FIRST gate for the weather step: the only briefing line that
     * needs the network. Uses BalochApp.instance so no signature changes
     * for callers. On any doubt (instance null, gate throws) we fall back
     * to the old behaviour — weatherLine already degrades silently.
     */
    private fun isOnlineForWeather(): Boolean =
        try {
            BalochApp.instance?.let { ConnectivityGate.isOnline(it) } ?: true
        } catch (_: Exception) {
            true
        }

    /** Honest offline line, trilingual — replaces the weather line. */
    private val OFFLINE_WEATHER_NOTE = L3(
        en = "Sir, the weather needs internet — everything else is ready. 🌤️",
        ur = "سر، موسم کے لیے انٹرنیٹ چاہیے — باقی سب حاضر ہے۔ 🌤️",
        hi = "सर, मौसम के लिए इंटरनेट चाहिए — बाक़ी सब हाज़िर है। 🌤️",
    )

    suspend fun buildBriefing(
        ctx: DeviceContext,
        notifications: () -> List<String>?,
        lang: BalochLanguage,
    ): String = withContext(Dispatchers.IO) {
        val lines = mutableListOf<String>()
        lines += greeting(lang)
        lines += timeLine(ctx, lang)
        lines += batteryLine(ctx, lang)
        lines += notificationsLine(notifications, lang)
        if (isOnlineForWeather()) {
            weatherLine(lang)?.let { lines += it }
        } else {
            // OFFLINE-FIRST: weather is the only briefing line that needs
            // the network. Skip the HTTP call and say so honestly — never
            // a silent gap, never a hang on a dead connection.
            lines += OFFLINE_WEATHER_NOTE.pick(lang)
        }
        lines += "💭 " + DAILY_THOUGHTS.random().pick(lang)
        lines.joinToString("\n")
    }

    // ------------------------------------------------------------------
    // Greeting by time of day
    // ------------------------------------------------------------------

    private fun hourNow(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    private fun greeting(lang: BalochLanguage): String {
        val l3 = when (hourNow()) {
            in 5..11 -> L3(
                "Good morning, sir! ☀️ Here's your briefing —",
                "صبح بخیر، سر! ☀️ آپ کی بریفنگ حاضر ہے —",
                "सुप्रभात, सर! ☀️ आपकी ब्रीफिंग हाज़िर है —",
            )
            in 12..16 -> L3(
                "Good afternoon, sir! 🌤️ Here's your briefing —",
                "دوپہر بخیر، سر! 🌤️ آپ کی بریفنگ حاضر ہے —",
                "नमस्कार, सर! 🌤️ आपकी ब्रीफिंग हाज़िर है —",
            )
            in 17..21 -> L3(
                "Good evening, sir! 🌙 Here's your briefing —",
                "شام بخیر، سر! 🌙 آپ کی بریفنگ حاضر ہے —",
                "शुभ संध्या, सर! 🌙 आपकी ब्रीफिंग हाज़िर है —",
            )
            else -> L3(
                "Up late, sir? 🌃 Here's your briefing —",
                "دیر تک جاگ رہے ہیں، سر؟ 🌃 آپ کی بریفنگ حاضر ہے —",
                "देर तक जाग रहे हैं, सर? 🌃 आपकी ब्रीफिंग हाज़िर है —",
            )
        }
        return l3.pick(lang)
    }

    private fun timeLine(ctx: DeviceContext, lang: BalochLanguage): String =
        L3(
            "🕐 It's ${ctx.timeString}, ${ctx.dateString}.",
            "🕐 وقت ${ctx.timeString}، ${ctx.dateString}۔",
            "🕐 समय ${ctx.timeString}, ${ctx.dateString}।",
        ).pick(lang)

    private fun batteryLine(ctx: DeviceContext, lang: BalochLanguage): String {
        val charging = if (ctx.isCharging) {
            L3(" and charging ⚡", " اور چارج ہو رہی ہے ⚡", " और चार्ज हो रही है ⚡").pick(lang)
        } else ""
        return L3(
            "🔋 Battery at ${ctx.batteryPercent}%$charging.",
            "🔋 بیٹری ${ctx.batteryPercent} فیصد$charging۔",
            "🔋 बैटरी ${ctx.batteryPercent} प्रतिशत$charging।",
        ).pick(lang)
    }

    private fun notificationsLine(
        notifications: () -> List<String>?,
        lang: BalochLanguage,
    ): String {
        val items = try {
            notifications()
        } catch (_: Exception) {
            null
        }
        if (items.isNullOrEmpty()) {
            return L3(
                "🔔 All quiet — no new notifications, sir. Enjoy the calm. 🍃",
                "🔔 سب خاموش ہے — کوئی نئی اطلاع نہیں، سر۔ سکون کا لطف اٹھائیں۔ 🍃",
                "🔔 सब शांत है — कोई नई सूचना नहीं, सर। शांति का आनंद लें। 🍃",
            ).pick(lang)
        }
        val header = L3(
            "🔔 Top notifications:",
            "🔔 اہم اطلاعات:",
            "🔔 मुख्य सूचनाएँ:",
        ).pick(lang)
        return "$header\n• " + items.take(3).joinToString("\n• ")
    }

    // ------------------------------------------------------------------
    // Lahore weather via Open-Meteo (fixed coords — no permission needed)
    // ------------------------------------------------------------------

    private fun weatherLine(lang: BalochLanguage): String? {
        return try {
            val url = URL(WEATHER_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "BalochAI/1.0")
            }
            try {
                conn.connect()
                if (conn.responseCode != 200) return null
                val body = conn.inputStream.bufferedReader().readText()
                val current = JSONObject(body).optJSONObject("current") ?: return null
                val temp = current.optDouble("temperature_2m", Double.NaN)
                val code = current.optInt("weather_code", -1)
                if (temp.isNaN() || code < 0) return null
                val desc = weatherDescription(code, lang)
                val t = "%.0f".format(temp)
                L3(
                    "🌤️ Lahore: $t°C, $desc.",
                    "🌤️ لاہور: $t ڈگری، $desc۔",
                    "🌤️ लाहौर: $t डिग्री, $desc।",
                ).pick(lang)
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null // omitted on failure — never an error, never a crash
        }
    }

    /** WMO weather-code → friendly trilingual description. */
    private fun weatherDescription(code: Int, lang: BalochLanguage): String {
        val l3 = when (code) {
            0 -> L3("clear sky", "صاف آسمان", "साफ़ आसमान")
            1 -> L3("mostly clear", "زیادہ تر صاف", "अधिकतर साफ़")
            2 -> L3("partly cloudy", "جزوی بادل", "आंशिक बादल")
            3 -> L3("overcast", "گھنے بادل", "घने बादल")
            45, 48 -> L3("foggy", "دھند", "धुंध")
            51, 53, 55 -> L3("drizzle", "ہلکی بوندا باندی", "हल्की बूँदाबाँदी")
            56, 57 -> L3("freezing drizzle", "جمادینے والی بوندا باندی", "जमाने वाली बूँदाबाँदी")
            61, 63, 65 -> L3("rain", "بارش", "बारिश")
            66, 67 -> L3("freezing rain", "جمادینے والی بارش", "जमाने वाली बारिश")
            71, 73, 75, 77 -> L3("snow", "برف باری", "बर्फ़बारी")
            80, 81, 82 -> L3("rain showers", "بارش کے چھینٹے", "बारिश की बौछार")
            85, 86 -> L3("snow showers", "برف کے چھینٹے", "बर्फ़ की बौछार")
            95 -> L3("thunderstorm", "گرج چمک", "गरज-चमक")
            96, 99 -> L3("thunderstorm with hail", "گرج چمک کے ساتھ اولے", "ओलों के साथ गरज-चमक")
            else -> L3("changing skies", "بدلتا موسم", "बदलता मौसम")
        }
        return l3.pick(lang)
    }
}
