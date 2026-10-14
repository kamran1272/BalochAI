package com.baloch.ai.power

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * DAILY ENGLISH WORD — "aaj ka lafz". 📚
 *
 * Fully OFFLINE: a hand-written bank of 64 English words, each with part
 * of speech, Urdu meaning, and an example sentence. The day's word is
 * `dayOfYear % bank.size` — deterministic, no network, no repeats within
 * a year cycle. A morning notification delivers it; "aaj ka lafz" answers
 * instantly from the same bank.
 */
object DailyWord {

    data class Word(
        val word: String,
        val pos: String,
        val urdu: String,
        val example: String,
    )

    val BANK: List<Word> = listOf(
        Word("Ambition", "noun", "عزم، بلند حوصلہ", "Her ambition is to become a doctor."),
        Word("Benevolent", "adjective", "نیک خواہ، مہربان", "The benevolent man feeds the poor every Friday."),
        Word("Courage", "noun", "ہمت، بہادری", "It takes courage to speak the truth."),
        Word("Diligent", "adjective", "محنتی", "A diligent student studies every day."),
        Word("Eloquent", "adjective", "فصیح، خوش گفتار", "His eloquent speech impressed everyone."),
        Word("Fragile", "adjective", "نازک", "Handle this box carefully — it is fragile."),
        Word("Gratitude", "noun", "شکرگزاری", "Show gratitude to those who help you."),
        Word("Honest", "adjective", "ایماندار", "An honest man never cheats."),
        Word("Illuminate", "verb", "روشن کرنا", "The lamp illuminates the whole room."),
        Word("Journey", "noun", "سفر", "Life is a long and beautiful journey."),
        Word("Kindness", "noun", "مہربانی", "Kindness costs nothing but means everything."),
        Word("Luminous", "adjective", "چمکدار، روشن", "The luminous moon lit up the night."),
        Word("Meticulous", "adjective", "باریک بین، محتاط", "She is meticulous about her work."),
        Word("Nourish", "verb", "پرورش کرنا، غذا دینا", "Good food nourishes the body."),
        Word("Obstacle", "noun", "رکاوٹ", "Hard work removes every obstacle."),
        Word("Persevere", "verb", "ثابت قدم رہنا", "Persevere and you will succeed."),
        Word("Quest", "noun", "تلاش، جستجو", "He is on a quest for knowledge."),
        Word("Resilient", "adjective", "مضبوط، لچکدار", "Resilient people recover from failure fast."),
        Word("Serene", "adjective", "پُرسکون", "The lake looked serene at dawn."),
        Word("Thrive", "verb", "پھلنا پھولنا، ترقی کرنا", "Plants thrive in sunlight."),
        Word("Unity", "noun", "اتحاد", "Unity makes a team strong."),
        Word("Vivid", "adjective", "واضح، جاندار", "She gave a vivid description of the city."),
        Word("Wisdom", "noun", "دانش، حکمت", "Wisdom comes with experience."),
        Word("Yearn", "verb", "شدید خواہش کرنا", "He yearns to meet his family."),
        Word("Zeal", "noun", "جوش، ولولہ", "She works with great zeal."),
        Word("Abundant", "adjective", "وافر، کثیر", "Pakistan has abundant sunshine."),
        Word("Brave", "adjective", "بہادر", "The brave soldier saved the child."),
        Word("Compassion", "noun", "ہمدردی", "Compassion for the poor is a true virtue."),
        Word("Discover", "verb", "دریافت کرنا", "Scientists discover new medicines every year."),
        Word("Empathy", "noun", "ہمدردی، دوسرے کا احساس", "A good leader always has empathy."),
        Word("Flourish", "verb", "پھلنا پھولنا", "Small businesses flourish with honesty."),
        Word("Generous", "adjective", "سخی، فیاض", "He is generous with his time."),
        Word("Harmony", "noun", "ہم آہنگی", "Music brings harmony to the soul."),
        Word("Integrity", "noun", "دیانتداری", "Integrity builds lasting trust."),
        Word("Joyful", "adjective", "خوش، مسرور", "The children looked joyful at Eid."),
        Word("Keen", "adjective", "تیز، شوقین", "She has a keen interest in design."),
        Word("Loyal", "adjective", "وفادار", "A loyal friend stands by you always."),
        Word("Modest", "adjective", "عاجز، سادہ مزاج", "Despite his success, he remains modest."),
        Word("Nimble", "adjective", "پھرتیلا", "The nimble cat caught the mouse."),
        Word("Opulent", "adjective", "شان دار", "They live in an opulent house."),
        Word("Patient", "adjective", "صابر", "Be patient — good things take time."),
        Word("Quaint", "adjective", "انوکھا، دلکش", "We stayed in a quaint old village."),
        Word("Radiant", "adjective", "چمکتی ہوئی، نورانی", "Her radiant smile lit the room."),
        Word("Sincere", "adjective", "مخلص", "He gave a sincere apology."),
        Word("Tenacious", "adjective", "ثابت قدم", "Tenacious people never give up."),
        Word("Uplift", "verb", "بلند کرنا، حوصلہ بڑھانا", "Kind words uplift the heart."),
        Word("Valiant", "adjective", "بہادر", "The valiant fireman entered the burning house."),
        Word("Wholesome", "adjective", "صحت بخش، عمدہ", "Eat wholesome food every day."),
        Word("Yield", "verb", "پیداوار دینا", "The farm yields excellent wheat."),
        Word("Adapt", "verb", "مطابقت اختیار کرنا", "Humans adapt to new places quickly."),
        Word("Bold", "adjective", "دلیر", "He made a bold decision."),
        Word("Cherish", "verb", "قدر کرنا، عزیز رکھنا", "Cherish every moment with family."),
        Word("Delight", "noun", "خوشی، مسرت", "The good news filled her with delight."),
        Word("Endure", "verb", "برداشت کرنا", "He endured the pain silently."),
        Word("Faithful", "adjective", "وفادار", "Dogs are faithful animals."),
        Word("Graceful", "adjective", "شائستہ، خوش اسلوب", "She gave a graceful performance."),
        Word("Humble", "adjective", "عاجز", "Stay humble no matter how high you rise."),
        Word("Inspire", "verb", "حوصلہ دینا، متاثر کرنا", "His story inspires millions."),
        Word("Jubilant", "adjective", "خوش و خرم", "The jubilant crowd cheered loudly."),
        Word("Kindle", "verb", "بھڑکانا، روشن کرنا", "Her words kindled hope in us."),
        Word("Lavish", "adjective", "شاہانہ", "They arranged a lavish wedding."),
        Word("Mellow", "adjective", "نرم، شیریں", "He speaks in a mellow voice."),
        Word("Nurture", "verb", "پرورش کرنا", "Parents nurture their children with love."),
        Word("Optimistic", "adjective", "پرامید", "Stay optimistic in hard times."),
    )

    fun isOn(context: Context): Boolean = PowerPrefs.isOn(context, PowerPrefs.WORD)

    fun setOn(context: Context, on: Boolean) {
        PowerPrefs.setOn(context, PowerPrefs.WORD, on)
        if (on) WordWorker.schedule(context) else WordWorker.cancel(context)
    }

    fun today(date: LocalDate = LocalDate.now(ZoneId.of("Asia/Karachi"))): Word =
        BANK[date.dayOfYear % BANK.size]

    /** Instant answer for "aaj ka lafz?" — trilingual presentation. */
    fun todayText(lang: BalochLanguage, date: LocalDate = LocalDate.now(ZoneId.of("Asia/Karachi"))): String {
        val w = today(date)
        return when (lang) {
            BalochLanguage.ENGLISH ->
                "Today's word, sir: ${w.word} (${w.pos}) — ${w.urdu}. Example: \"${w.example}\" 📚"
            BalochLanguage.URDU ->
                "آج کا لفظ، سر: ${w.word} (${w.pos}) — ${w.urdu}۔ مثال: \"${w.example}\" 📚"
            BalochLanguage.HINDI ->
                "आज का लफ़्ज़, सर: ${w.word} (${w.pos}) — ${w.urdu}। उदाहरण: \"${w.example}\" 📚"
        }
    }
}

/** Morning delivery: one notification a day at 07:30 PKT. */
class WordWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!DailyWord.isOn(ctx)) return Result.success()
        val lang = PowerPrefs.activeLang(ctx)
        PowerNotify.notify(
            ctx,
            L3("📚 Aaj ka lafz", "📚 آج کا لفظ", "📚 आज का लफ़्ज़").pick(lang),
            DailyWord.todayText(lang),
            6601,
        )
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "daily_word"

        fun schedule(context: Context) {
            val now = ZonedDateTime.now(ZoneId.of("Asia/Karachi"))
            var next = now.withHour(7).withMinute(30).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = java.time.Duration.between(now, next).toMillis()
            val req = PeriodicWorkRequestBuilder<WordWorker>(24, TimeUnit.HOURS)
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
