package com.baloch.ai.power

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * RAMADAN MODE — sehri/iftari alerts with a daily countdown.
 *
 * Timings come from the free, keyless Aladhan API
 * (https://api.aladhan.com/v1/timingsByCity/{DD-MM-YYYY}?city=…&country=Pakistan&method=1),
 * method 1 = University of Islamic Sciences, Karachi — the calculation
 * convention used across Pakistan. A fresh fetch runs once a day; the
 * day's sehri (Fajr) and iftari (Maghrib) fire as one-shot alarms with a
 * spoken + notification alert.
 *
 * Works year-round:
 *  - In Ramadan (Hijri month 9, arithmetic calendar) → full alerts.
 *  - Off-season → the "iftari in X hours" query answers with the
 *    estimated date of the next Ramadan instead.
 *
 * HONEST NOTES:
 *  - The Hijri month is computed with the standard arithmetic (Kuwaiti)
 *    algorithm — actual Ramadan follows the MOON SIGHTING, so dates can
 *    differ by a day or two. Every off-season answer says "estimated".
 *  - Aladhan timings are calculation-based, not a local mosque's announcement.
 */
object RamadanMode {

    private val KARACHI = ZoneId.of("Asia/Karachi")

    fun isOn(context: Context): Boolean = PowerPrefs.isOn(context, PowerPrefs.RAMADAN)

    fun setOn(context: Context, on: Boolean) {
        PowerPrefs.setOn(context, PowerPrefs.RAMADAN, on)
        if (on) RamadanWorker.schedule(context) else RamadanWorker.cancel(context)
    }

    fun city(context: Context): String =
        PowerPrefs.getString(context, PowerPrefs.RAMADAN_CITY, "Lahore")

    fun setCity(context: Context, city: String) {
        PowerPrefs.setString(context, PowerPrefs.RAMADAN_CITY, city.trim().ifBlank { "Lahore" })
    }

    /**
     * Standalone Aladhan fetch — same endpoint style as the prayer-times
     * pattern, implemented here so this feature never depends on the life
     * package. @return Pair(sehri "HH:MM", iftari "HH:MM") or null.
     */
    fun fetchTimings(city: String, date: LocalDate): Pair<String, String>? {
        return try {
            val ddMMyyyy = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            val url = URL(
                "https://api.aladhan.com/v1/timingsByCity/$ddMMyyyy" +
                    "?city=${URLEncoder.encode(city, "UTF-8")}" +
                    "&country=Pakistan&method=1"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "BalochAI/1.0")
            }
            try {
                conn.connect()
                if (conn.responseCode != 200) return null
                val timings = JSONObject(conn.inputStream.bufferedReader().readText())
                    .optJSONObject("data")?.optJSONObject("timings") ?: return null
                cleanTime(timings.optString("Fajr")) to cleanTime(timings.optString("Maghrib"))
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** "05:04 (PKT)" → "05:04". */
    private fun cleanTime(raw: String): String =
        raw.substringBefore(" ").trim().takeIf { it.matches(Regex("\\d{1,2}:\\d{2}")) } ?: ""

    /**
     * The query behind "iftari in X hours?" / "aaj ka lafz"-style voice asks.
     * Uses today's cached timings; off-season answers with the estimated
     * next Ramadan date. Fully offline once the day's timings are cached.
     */
    fun countdownText(context: Context, lang: BalochLanguage): String {
        val today = LocalDate.now(KARACHI).format(DateTimeFormatter.ISO_DATE)
        val cachedDate = PowerPrefs.getString(context, PowerPrefs.RAMADAN_TIMINGS_DATE, "")
        val now = ZonedDateTime.now(KARACHI)
        if (cachedDate == today) {
            val sehri = PowerPrefs.getString(context, PowerPrefs.RAMADAN_SEHRI, "")
            val iftari = PowerPrefs.getString(context, PowerPrefs.RAMADAN_IFTARI, "")
            val sehriAt = timeToday(sehri)
            val iftariAt = timeToday(iftari)
            if (sehriAt != null && now.isBefore(sehriAt)) {
                return L3(
                    "Sehri in ${describe(sehriAt, now, lang)}, sir — last chance for suhoor. 🌙",
                    "سحری میں ${describe(sehriAt, now, lang)} باقی ہیں، سر — سحری کا آخری موقع۔ 🌙",
                    "सहरी में ${describe(sehriAt, now, lang)} बाकी हैं, सर — सहरी का आखिरी मौका। 🌙",
                ).pick(lang)
            }
            if (iftariAt != null && now.isBefore(iftariAt)) {
                return L3(
                    "Iftari in ${describe(iftariAt, now, lang)}, sir — almost there. 🤲",
                    "افطاری میں ${describe(iftariAt, now, lang)} باقی ہیں، سر — بس تھوڑا سا صبر۔ 🤲",
                    "इफ़्तार में ${describe(iftariAt, now, lang)} बाकी हैं, सर — बस थोड़ा सा सब्र। 🤲",
                ).pick(lang)
            }
            if (iftariAt != null) {
                return L3(
                    "Iftari time has passed for today, sir — sehri timings will refresh overnight. 🌙",
                    "آج افطاری کا وقت گزر چکا، سر — سحری کا وقت رات کو اپڈیٹ ہو جائے گا۔ 🌙",
                    "आज इफ़्तार का वक़्त गुज़र चुका, सर — सहरी का वक़्त रात को अपडेट हो जाएगा। 🌙",
                ).pick(lang)
            }
        }
        // Off-season (or timings not fetched yet): estimated next Ramadan.
        val next = nextRamadanEstimate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(KARACHI), next)
        return L3(
            "Ramadan begins around ${next.format(DateTimeFormatter.ofPattern("d MMM yyyy"))} " +
                "(about $days days away) — that's an arithmetic estimate, sir; the moon sighting decides. 🌙",
            "رمضان تقریباً ${next.format(DateTimeFormatter.ofPattern("d MMM yyyy"))} سے شروع ہوگا " +
                "(تقریباً $days دن باقی) — یہ حسابی اندازہ ہے، سر؛ چاند دیکھ کر فیصلہ ہوگا۔ 🌙",
            "रमज़ान लगभग ${next.format(DateTimeFormatter.ofPattern("d MMM yyyy"))} से शुरू होगा " +
                "(लगभग $days दिन बाकी) — यह हिसाबी अंदाज़ा है, सर; चाँद देखकर फ़ैसला होगा। 🌙",
        ).pick(lang)
    }

    private fun timeToday(hhmm: String): ZonedDateTime? {
        if (!hhmm.matches(Regex("\\d{1,2}:\\d{2}"))) return null
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return ZonedDateTime.now(KARACHI).withHour(h).withMinute(m).withSecond(0).withNano(0)
    }

    private fun describe(target: ZonedDateTime, now: ZonedDateTime, lang: BalochLanguage): String {
        val mins = java.time.Duration.between(now, target).toMinutes().coerceAtLeast(0)
        val h = mins / 60
        val m = mins % 60
        return when (lang) {
            BalochLanguage.ENGLISH ->
                if (h == 0L) "$m minutes" else "$h hour${if (h == 1L) "" else "s"} $m min"
            BalochLanguage.URDU ->
                if (h == 0L) "$m منٹ" else "$h گھنٹے $m منٹ"
            BalochLanguage.HINDI ->
                if (h == 0L) "$m मिनट" else "$h घंटे $m मिनट"
        }
    }

    // -- Hijri arithmetic (Kuwaiti algorithm) --------------------------------
    // Standard arithmetic conversion — NOT the moon sighting. Good for
    // "is it Ramadan season?" and the off-season countdown; every user
    // facing string calls it an estimate.

    /** @return Triple(hijriYear, hijriMonth, hijriDay) */
    fun gregorianToHijri(y: Int, m: Int, d: Int): Triple<Int, Int, Int> {
        val jd = (d + ((153 * (m + 12 * ((14 - m) / 12) - 3) + 2) / 5) +
            (365 * (y + 4800 - ((14 - m) / 12))) +
            ((y + 4800 - ((14 - m) / 12)) / 4) -
            ((y + 4800 - ((14 - m) / 12)) / 100) +
            ((y + 4800 - ((14 - m) / 12)) / 400) - 32045)
        var l = jd - 1948440 + 10632
        val n = (l - 1) / 10631
        l = l - 10631 * n + 354
        val j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238)
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) - ((j / 16) * ((15238 * j) / 43)) + 29
        val hm = (24 * l) / 709
        val hd = l - (709 * hm) / 24
        val hy = 30 * n + j - 30
        return Triple(hy, hm, hd)
    }

    fun isRamadanSeason(today: LocalDate = LocalDate.now(KARACHI)): Boolean =
        gregorianToHijri(today.year, today.monthValue, today.dayOfMonth).second == 9

    /**
     * Estimated Gregorian date of 1 Ramadan on/after today. Anchored on
     * 1 Ramadan 1447 ≈ 18 Feb 2026, stepping by 354-day lunar cycles.
     * ±2 days vs the moon sighting — always labelled an estimate.
     */
    fun nextRamadanEstimate(today: LocalDate = LocalDate.now(KARACHI)): LocalDate {
        var candidate = LocalDate.of(2026, 2, 18)
        while (candidate.isBefore(today)) candidate = candidate.plusDays(354)
        return candidate
    }
}

/**
 * Runs once a day (first tick lands at 04:00 PKT): fetches today's
 * sehri/iftari for the saved city, caches them, and arms two one-shot
 * alarms for the actual times — but only during Ramadan season.
 * If the fetch fails, yesterday's cache stays and nothing fires.
 */
class RamadanWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!RamadanMode.isOn(applicationContext)) return Result.success()
        if (!RamadanMode.isRamadanSeason()) return Result.success() // off-season: silent

        val ctx = applicationContext
        val today = LocalDate.now(ZoneId.of("Asia/Karachi"))
        val timings = RamadanMode.fetchTimings(RamadanMode.city(ctx), today)
        if (timings == null || timings.first.isBlank() || timings.second.isBlank()) {
            return Result.retry() // network/API hiccup — WorkManager backs off
        }
        PowerPrefs.setString(ctx, PowerPrefs.RAMADAN_SEHRI, timings.first)
        PowerPrefs.setString(ctx, PowerPrefs.RAMADAN_IFTARI, timings.second)
        PowerPrefs.setString(
            ctx, PowerPrefs.RAMADAN_TIMINGS_DATE,
            today.format(DateTimeFormatter.ISO_DATE)
        )
        armAlarm(ctx, timings.first, "sehri")
        armAlarm(ctx, timings.second, "iftari")
        return Result.success()
    }

    private fun armAlarm(ctx: Context, hhmm: String, kind: String) {
        if (!hhmm.matches(Regex("\\d{1,2}:\\d{2}"))) return
        val (h, m) = hhmm.split(":").map { it.toInt() }
        val at = ZonedDateTime.now(ZoneId.of("Asia/Karachi"))
            .withHour(h).withMinute(m).withSecond(0).withNano(0)
        val delayMs = java.time.Duration.between(ZonedDateTime.now(ZoneId.of("Asia/Karachi")), at)
            .toMillis()
        if (delayMs <= 60_000) return // already passed — no stale alarm
        val req = OneTimeWorkRequestBuilder<RamadanAlertWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString("kind", kind).putString("time", hhmm).build())
            .addTag("ramadan_$kind")
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "ramadan_$kind", ExistingWorkPolicy.REPLACE, req
        )
    }

    companion object {
        private const val UNIQUE = "ramadan_daily"

        fun schedule(context: Context) {
            val now = ZonedDateTime.now(ZoneId.of("Asia/Karachi"))
            var next4am = now.withHour(4).withMinute(0).withSecond(0).withNano(0)
            if (!next4am.isAfter(now)) next4am = next4am.plusDays(1)
            val delay = java.time.Duration.between(now, next4am).toMillis()
            val req = PeriodicWorkRequestBuilder<RamadanWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }

        fun cancel(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(UNIQUE)
            wm.cancelUniqueWork("ramadan_sehri")
            wm.cancelUniqueWork("ramadan_iftari")
        }
    }
}

/** Fires once at sehri / iftari time: notification + spoken alert. */
class RamadanAlertWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!RamadanMode.isOn(applicationContext)) return Result.success()
        val ctx = applicationContext
        val lang = PowerPrefs.activeLang(ctx)
        val kind = inputData.getString("kind") ?: return Result.success()
        val time = inputData.getString("time") ?: ""
        val (title, text) = if (kind == "sehri") {
            L3("🌙 Sehri time", "🌙 سحری کا وقت", "🌙 सहरी का वक़्त").pick(lang) to
                L3(
                    "Sehri time, sir ($time) — eat well and make your intention. 🌙",
                    "سحری کا وقت ہو گیا، سر ($time) — اچھی طرح کھائیں اور نیت کر لیں۔ 🌙",
                    "सहरी का वक़्त हो गया, सर ($time) — अच्छी तरह खाएँ और नीयत कर लें। 🌙",
                ).pick(lang)
        } else {
            L3("🤲 Iftari time", "🤲 افطاری کا وقت", "🤲 इफ़्तार का वक़्त").pick(lang) to
                L3(
                    "Iftari time, sir ($time) — open your fast. Ramadan Mubarak! 🤲",
                    "افطاری کا وقت ہو گیا، سر ($time) — روزہ کھول لیں۔ رمضان مبارک! 🤲",
                    "इफ़्तार का वक़्त हो गया, सर ($time) — रोज़ा खोल लें। रमज़ान मुबारक! 🤲",
                ).pick(lang)
        }
        PowerNotify.notify(ctx, title, text, if (kind == "sehri") 6201 else 6202)
        PowerSpeak.speak(ctx, lang, text)
        return Result.success()
    }
}
