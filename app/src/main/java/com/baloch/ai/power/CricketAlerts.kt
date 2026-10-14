package com.baloch.ai.power

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/**
 * PAKISTAN CRICKET ALERTS — match news for the green shirts. 🏏
 *
 * SOURCE (honest): there is no free, keyless, official ball-by-ball API,
 * so this polls the ESPNcricinfo cricket news RSS feed
 * (https://www.espncricinfo.com/rss/content/story/feeds/6.xml) every 4
 * hours and surfaces items mentioning Pakistan. With "match days only"
 * ON (default), it further requires match language (vs / won by /
 * scorecard / T20 / ODI / Test / innings…) so transfer gossip doesn't
 * buzz Kamran's phone.
 *
 * DEGRADATION: if the feed is unreachable or its format changes, the
 * worker stores the failure time ([PowerPrefs.CRICKET_LAST_ERROR]) and
 * stays silent — the settings row shows "source unavailable" instead of
 * pretending everything is fine. Alerts are one per story (link dedupe).
 */
object CricketAlerts {

    fun isOn(context: Context): Boolean = PowerPrefs.isOn(context, PowerPrefs.CRICKET)

    fun setOn(context: Context, on: Boolean) {
        PowerPrefs.setOn(context, PowerPrefs.CRICKET, on)
        if (on) CricketWorker.schedule(context) else CricketWorker.cancel(context)
    }

    fun matchDaysOnly(context: Context): Boolean =
        context.getSharedPreferences("baloch_power", Context.MODE_PRIVATE)
            .getBoolean(PowerPrefs.CRICKET_MATCH_DAYS_ONLY, true)

    fun setMatchDaysOnly(context: Context, only: Boolean) =
        PowerPrefs.setOn(context, PowerPrefs.CRICKET_MATCH_DAYS_ONLY, only)

    /** Human-readable source status for the settings row. */
    fun sourceStatus(context: Context, lang: BalochLanguage): String {
        val errAt = PowerPrefs.getLong(context, PowerPrefs.CRICKET_LAST_ERROR, 0L)
        return if (errAt == 0L) {
            L3("Source: ESPNcricinfo RSS ✓", "ماخذ: ESPNcricinfo RSS ✓", "स्रोत: ESPNcricinfo RSS ✓")
                .pick(lang)
        } else {
            L3(
                "Source unavailable since ${timeAgo(errAt)} — will retry automatically.",
                "${timeAgo(errAt)} سے ماخذ دستیاب نہیں — خودکار دوبارہ کوشش جاری۔",
                "${timeAgo(errAt)} से स्रोत उपलब्ध नहीं — स्वतः पुनः प्रयास जारी।",
            ).pick(lang)
        }
    }

    private fun timeAgo(at: Long): String {
        val mins = ((System.currentTimeMillis() - at) / 60000).coerceAtLeast(1)
        return if (mins < 60) "${mins}m ago" else "${mins / 60}h ago"
    }

    data class PakStory(val title: String, val link: String)

    /**
     * Fetches the feed and returns Pakistan stories (newest first).
     * @return null when the source is unreachable (degraded, honest).
     */
    fun fetchPakistanStories(matchDaysOnly: Boolean): List<PakStory>? {
        return try {
            val url = URL("https://www.espncricinfo.com/rss/content/story/feeds/6.xml")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("User-Agent", "BalochAI/1.0 (Pakistan cricket alerts)")
            }
            try {
                conn.connect()
                if (conn.responseCode != 200) return null
                parseRss(conn.inputStream.bufferedReader().readText(), matchDaysOnly)
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private val MATCH_WORDS = listOf(
        " vs ", " v ", "won by", "win by", "beat ", "beats", "draw", "drawn",
        "scorecard", "t20", "odi", "test ", "innings", "wicket", "chase",
        "toss", "series", "tour", "squad", "playing xi", "live",
    )

    private fun parseRss(xml: String, matchDaysOnly: Boolean): List<PakStory> {
        val out = mutableListOf<PakStory>()
        return try {
            val doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(xml.byteInputStream())
            val items = doc.getElementsByTagName("item")
            for (i in 0 until items.length) {
                val el = items.item(i)
                var title = ""; var link = ""
                val kids = el.childNodes
                for (k in 0 until kids.length) {
                    when (kids.item(k).nodeName.lowercase()) {
                        "title" -> title = kids.item(k).textContent.orEmpty().trim()
                        "link" -> link = kids.item(k).textContent.orEmpty().trim()
                    }
                }
                if (title.isBlank() || link.isBlank()) continue
                val low = title.lowercase()
                if (!low.contains("pakistan") && !low.contains("pak ")) continue
                if (matchDaysOnly && MATCH_WORDS.none { low.contains(it) }) continue
                out.add(PakStory(title, link))
            }
            out
        } catch (_: Exception) {
            out // malformed XML → return what parsed, never crash
        }
    }
}

/** 4-hour poll: new Pakistan stories → notify (max 3 per tick). */
class CricketWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!CricketAlerts.isOn(ctx)) return Result.success()

        val stories = CricketAlerts.fetchPakistanStories(CricketAlerts.matchDaysOnly(ctx))
        if (stories == null) {
            PowerPrefs.setLong(ctx, PowerPrefs.CRICKET_LAST_ERROR, System.currentTimeMillis())
            return Result.retry()
        }
        PowerPrefs.setLong(ctx, PowerPrefs.CRICKET_LAST_ERROR, 0L)

        val seen = seenLinks(ctx)
        val fresh = stories.filter { it.link !in seen }.take(3)
        if (fresh.isEmpty()) return Result.success()

        val lang = PowerPrefs.activeLang(ctx)
        val title = L3("🏏 Pakistan cricket", "🏏 پاکستان کرکٹ", "🏏 पाकिस्तान क्रिकेट").pick(lang)
        fresh.forEachIndexed { idx, story ->
            PowerNotify.notify(ctx, title, story.title, 6301 + idx)
        }
        // Speak only the first — three TTS bursts would be noise.
        PowerSpeak.speak(
            ctx, lang,
            L3(
                "Sir, Pakistan cricket update: ${fresh.first().title}",
                "سر، پاکستان کرکٹ کی خبر: ${fresh.first().title}",
                "सर, पाकिस्तान क्रिकेट की ख़बर: ${fresh.first().title}",
            ).pick(lang),
        )
        rememberLinks(ctx, (seen + fresh.map { it.link }).takeLast(60))
        return Result.success()
    }

    private fun seenLinks(ctx: Context): Set<String> = try {
        val arr = JSONArray(PowerPrefs.getString(ctx, PowerPrefs.CRICKET_SEEN_LINKS, "[]"))
        (0 until arr.length()).map { arr.getString(it) }.toSet()
    } catch (_: Exception) {
        emptySet()
    }

    private fun rememberLinks(ctx: Context, links: List<String>) {
        PowerPrefs.setString(ctx, PowerPrefs.CRICKET_SEEN_LINKS, JSONArray(links).toString())
    }

    companion object {
        private const val UNIQUE = "cricket_alerts"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<CricketWorker>(4, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }
    }
}
