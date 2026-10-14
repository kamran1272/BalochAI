package com.baloch.ai.power

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * PETROL PRICE ALERTS (Pakistan) — user-taught, honestly.
 *
 * HONEST DESIGN NOTE (also shown in the settings UI): there is NO free
 * official API for Pakistan's fortnightly OGRA petrol price. Any app
 * claiming "live petrol prices" is scraping or guessing. So Baloch does
 * the honest thing:
 *
 *  1. Kamran TEACHES the price once: "petrol price 278".
 *  2. When he teaches a NEW price that differs by Rs 5 or more, Baloch
 *     announces the change: "Sir, petrol 278 se 292 ho gaya — 5% up."
 *  3. A daily worker nudges once a month if the price hasn't been
 *     updated ("prices change fortnightly — teach me the new one").
 *
 * Nothing is fetched, nothing is invented. The stored price is always
 * labelled as the last price Kamran taught.
 */
object PetrolAlerts {

    /** Minimum rupee difference that counts as a "price change". */
    const val CHANGE_DELTA = 5.0

    private const val MONTH_MS = 30L * 24 * 60 * 60 * 1000

    fun isOn(context: Context): Boolean = PowerPrefs.isOn(context, PowerPrefs.PETROL)

    fun setOn(context: Context, on: Boolean) {
        PowerPrefs.setOn(context, PowerPrefs.PETROL, on)
        if (on) PetrolNudgeWorker.schedule(context) else PetrolNudgeWorker.cancel(context)
    }

    fun lastPrice(context: Context): Double? =
        if (PowerPrefs.hasDouble(context, PowerPrefs.PETROL_PRICE))
            PowerPrefs.getDouble(context, PowerPrefs.PETROL_PRICE, 0.0) else null

    fun daysSinceUpdate(context: Context): Long {
        val at = PowerPrefs.getLong(context, PowerPrefs.PETROL_UPDATED_AT, 0L)
        if (at == 0L) return Long.MAX_VALUE
        return (System.currentTimeMillis() - at) / (24 * 60 * 60 * 1000)
    }

    /**
     * Teaches a new price. Returns an L3 reply:
     *  - first ever price → stored quietly with a confirmation,
     *  - change ≥ Rs 5 → the "Sir, petrol X se Y ho gaya" announcement
     *    (also fired as notification + speech by the caller),
     *  - tiny change → stored silently.
     */
    fun teachPrice(context: Context, price: Double): TeachResult {
        val sane = price.coerceIn(1.0, 10000.0)
        val old = lastPrice(context)
        PowerPrefs.setDouble(context, PowerPrefs.PETROL_PRICE, sane)
        PowerPrefs.setLong(context, PowerPrefs.PETROL_UPDATED_AT, System.currentTimeMillis())
        val lang = PowerPrefs.activeLang(context)
        return when {
            old == null -> TeachResult(
                changed = false, first = true,
                text = L3(
                    "Noted, sir — petrol at Rs ${fmt(sane)} per litre. I'll announce any change of Rs 5 or more. ⛽",
                    "نوٹ کر لیا، سر — پٹرول ${fmt(sane)} روپے فی لیٹر۔ 5 روپے یا زیادہ کی تبدیلی پر بتا دوں گا۔ ⛽",
                    "नोट कर लिया, सर — पेट्रोल ${fmt(sane)} रुपये प्रति लीटर। 5 रुपये या ज़्यादा के बदलाव पर बता दूँगा। ⛽",
                ).pick(lang),
            )
            abs(sane - old) >= CHANGE_DELTA -> {
                val pct = (sane - old) / old * 100
                val dir = if (sane > old)
                    L3("up", "بڑھ گیا", "बढ़ गया").pick(lang)
                else
                    L3("down", "کم ہو گیا", "कम हो गया").pick(lang)
                TeachResult(
                    changed = true, first = false,
                    text = L3(
                        "Sir, petrol ${fmt(old)} se ${fmt(sane)} ho gaya — ${"%.1f".format(abs(pct))}% $dir! ⛽",
                        "سر، پٹرول ${fmt(old)} سے ${fmt(sane)} ہو گیا — ${"%.1f".format(abs(pct))} فیصد $dir! ⛽",
                        "सर, पेट्रोल ${fmt(old)} से ${fmt(sane)} हो गया — ${"%.1f".format(abs(pct))} प्रतिशत $dir! ⛽",
                    ).pick(lang),
                )
            }
            else -> TeachResult(
                changed = false, first = false,
                text = L3(
                    "Updated, sir — petrol at Rs ${fmt(sane)} (small change, no alert). ⛽",
                    "اپڈیٹ ہو گیا، سر — پٹرول ${fmt(sane)} روپے (معمولی تبدیلی، کوئی الرٹ نہیں)۔ ⛽",
                    "अपडेट हो गया, सर — पेट्रोल ${fmt(sane)} रुपये (मामूली बदलाव, कोई अलर्ट नहीं)। ⛽",
                ).pick(lang),
            )
        }
    }

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

    data class TeachResult(val changed: Boolean, val first: Boolean, val text: String)

    /** Instant answer for "petrol kitna hai?" — always labelled as taught. */
    fun priceText(context: Context, lang: BalochLanguage): String {
        val price = lastPrice(context)
        return if (price == null) {
            L3(
                "I don't know the petrol price yet, sir — teach me: \"petrol price 278\". ⛽",
                "مجھے ابھی پٹرول کی قیمت نہیں معلوم، سر — سکھا دیں: \"petrol price 278\"۔ ⛽",
                "मुझे अभी पेट्रोल की क़ीमत नहीं मालूम, सर — सिखा दें: \"petrol price 278\"। ⛽",
            ).pick(lang)
        } else {
            L3(
                "Last taught price: Rs ${fmt(price)} per litre, sir (${daysSinceUpdate(context)} days ago).",
                "آخری بتائی گئی قیمت: ${fmt(price)} روپے فی لیٹر، سر (${daysSinceUpdate(context)} دن پہلے)۔",
                "आखिरी बताई गई क़ीमत: ${fmt(price)} रुपये प्रति लीटर, सर (${daysSinceUpdate(context)} दिन पहले)।",
            ).pick(lang)
        }
    }

    internal fun needsNudge(context: Context): Boolean {
        if (!isOn(context)) return false
        val at = PowerPrefs.getLong(context, PowerPrefs.PETROL_UPDATED_AT, 0L)
        return System.currentTimeMillis() - at > MONTH_MS
    }
}

/** Daily check: one gentle nudge per month to re-teach the price. */
class PetrolNudgeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (PetrolAlerts.needsNudge(ctx)) {
            val lang = PowerPrefs.activeLang(ctx)
            // Nudge once: teaching a price resets the clock, so this
            // naturally fires at most once per month.
            PowerPrefs.setLong(ctx, PowerPrefs.PETROL_UPDATED_AT, System.currentTimeMillis())
            val text = L3(
                "Sir, it's been a month since the petrol price was updated — prices change fortnightly. Teach me the new one: \"petrol price …\" ⛽",
                "سر، پٹرول کی قیمت اپڈیٹ ہوئے ایک ماہ ہو گیا — قیمتیں ہر پندرہ دن بدلتی ہیں۔ نئی قیمت سکھا دیں: \"petrol price …\" ⛽",
                "सर, पेट्रोल की क़ीमत अपडेट हुए एक महीना हो गया — क़ीमतें हर पंद्रह दिन बदलती हैं। नई क़ीमत सिखा दें: \"petrol price …\" ⛽",
            ).pick(lang)
            PowerNotify.notify(
                ctx,
                L3("⛽ Petrol price check", "⛽ پٹرول قیمت یاددہانی", "⛽ पेट्रोल क़ीमत याद").pick(lang),
                text, 6501,
            )
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "petrol_nudge"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<PetrolNudgeWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE, ExistingPeriodicWorkPolicy.KEEP, req
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }
    }
}
