package com.baloch.ai.schedule

import android.content.Context
import com.baloch.ai.brain.BalochLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Parent-facing facade for task scheduling. The brain calls [parse] on user
 * input; when it returns a [Draft], the UI shows [confirmText] and, on the
 * user's go-ahead, calls [commit] to persist + arm the trigger.
 */
object ScheduleKit {

    data class Draft(
        /** The inner command to run at fire time, e.g. "call Ahmed". */
        val commandText: String,
        val triggerAtMillis: Long,
        val recurrence: Recurrence,
        /** ISO weekdays 1=Monday..7=Sunday (WEEKLY only). */
        val days: Set<Int>,
        /** English "when" phrase for logs, e.g. "tomorrow at 9:00 AM". */
        val summaryEn: String,
    )

    // ------------------------------------------------------------------
    // parse
    // ------------------------------------------------------------------

    /**
     * Strong scheduling wrappers — a verb that unambiguously means "schedule
     * this". A datetime may follow, or the 09:00 default applies.
     */
    private val VERB_LEADS = listOf(
        "remind me", "remind", "set a reminder", "schedule", "mujhe", "مجھے", "मुझे",
    )

    /**
     * Date/recurrence leads ("roz raat 11 baje …", "every day at 8am …").
     * These only count when an EXPLICIT time is also parsed — otherwise plain
     * sentences like "today is a good day" would be hijacked as schedules.
     */
    private val DATE_LEADS = listOf(
        "every", "roz", "kal", "aaj", "tomorrow", "today", "parson", "day after",
        "har ", "ہر", "हर", "کل", "कल", "آج", "आज", "پرسوں", "परसों",
    )

    /**
     * Detects a scheduling command and splits it into inner command +
     * trigger. Returns null when the input is not a scheduling command or
     * no date/time can be parsed.
     *
     *   "remind me tomorrow at 9am to call Ahmed" → ("call Ahmed", tomorrow 9:00, ONCE)
     *   "mujhe kal subah 7 baje utha dena"       → ("wake me up", tomorrow 7:00, ONCE)
     *   "every day at 8am give me briefing"      → ("give me briefing", next 8:00, DAILY)
     *   "roz raat 11 baje wifi band kar dena"    → ("wifi band kar dena", next 23:00, DAILY)
     *   "schedule: open Spotify at 6pm"          → ("open Spotify", next 18:00, ONCE)
     */
    fun parse(input: String): Draft? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val lowered = trimmed.lowercase()

        val hasVerbLead = VERB_LEADS.any { lowered.startsWith(it) } ||
            "yaad dila" in lowered || "یاد دلا" in lowered || "याद दिला" in lowered
        // Date-lead inputs ("roz raat 11 baje …") need an explicit time too,
        // or plain sentences like "today is a good day" would be hijacked.
        val hasDateLead = DATE_LEADS.any { lowered.startsWith(it) }
        if (!hasVerbLead && !hasDateLead) return null

        val p = DateTimeParser.parse(trimmed) ?: return null
        if (!hasVerbLead && !p.hasExplicitTime) return null
        var cmd = p.remainder

        // 1. Strip the wrapper lead.
        cmd = cmd.replaceFirst(
            Regex("^(remind(\\s+me)?|set\\s+a\\s+reminder|schedule\\s*:?|mujhe|مجھے|मुझे)\\s*"),
            ""
        ).trim()
        // 2. Strip leading connectors left behind ("to", "that", "at", "about").
        cmd = cmd.replaceFirst(Regex("^(to|that|about|at)\\s+"), "").trim()
        // 2b. Strip trailing connectors left behind ("... Spotify at 6pm" → "at" dangles).
        cmd = cmd.replaceFirst(Regex("\\s+(at|on|to|for)$"), "").trim()
        // 3. Roman-Urdu wake/remind verbs → natural command the brain understands.
        cmd = mapWakeVerbs(cmd)

        if (cmd.isEmpty()) return null
        return Draft(
            commandText = cmd,
            triggerAtMillis = p.triggerAtMillis,
            recurrence = p.recurrence,
            days = p.days,
            summaryEn = formatWhen(p.triggerAtMillis, p.recurrence, p.days, BalochLanguage.ENGLISH),
        )
    }

    /**
     * "mujhe kal subah 7 baje utha dena" leaves "utha dena" after stripping —
     * the brain has no such intent, so map the common wake/remind verbs.
     */
    private fun mapWakeVerbs(cmd: String): String {
        val c = cmd.trim()
        if (Regex("^(utha|uthaa|jaga|jagaa)\\s*d[ei]na\\s*$", RegexOption.IGNORE_CASE).matches(c)) {
            return "wake me up"
        }
        Regex("^(.*?)\\s+yaad\\s+dila(na|dena)?\\s*$", RegexOption.IGNORE_CASE)
            .matchEntire(c)?.let { m ->
                val what = m.groupValues[1].trim()
                return if (what.isEmpty()) "remind me" else "remind me about $what"
            }
        Regex("^(.*?)\\s+(bata|bta)\\s*d[ei]na\\s*$", RegexOption.IGNORE_CASE)
            .matchEntire(c)?.let { m ->
                val what = m.groupValues[1].trim()
                return if (what.isEmpty()) c else "tell me $what"
            }
        return c
    }

    // ------------------------------------------------------------------
    // confirmText
    // ------------------------------------------------------------------

    /**
     * Trilingual confirmation shown before commit.
     * en-GB spelling; "sir" in all three; Urdu script / Devanagari.
     */
    fun confirmText(d: Draft, lang: BalochLanguage): String {
        val whenStr = formatWhen(d.triggerAtMillis, d.recurrence, d.days, lang)
        return when (lang) {
            BalochLanguage.ENGLISH ->
                "Scheduled, sir — I will ${d.commandText} $whenStr."
            BalochLanguage.URDU ->
                "طے ہو گیا، سر — میں $whenStr «${d.commandText}» کروں گا۔"
            BalochLanguage.HINDI ->
                "तय हो गया, सर — मैं $whenStr «${d.commandText}» करूँगा।"
        }
    }

    // ------------------------------------------------------------------
    // commit
    // ------------------------------------------------------------------

    /** Persists the task and arms its trigger (AlarmManager / WorkManager). IO thread. */
    suspend fun commit(context: Context, d: Draft): Long = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val store = TaskStore(app)
        val id = store.add(
            ScheduledTask(
                id = 0L, // TaskStore assigns the real id
                commandText = d.commandText,
                triggerAtMillis = d.triggerAtMillis,
                recurrence = d.recurrence,
                daysOfWeek = d.days,
                enabled = true,
            )
        )
        val saved = store.get(id) ?: return@withContext id
        TaskScheduler.schedule(app, saved)
        id
    }

    // ------------------------------------------------------------------
    // "when" formatting (trilingual)
    // ------------------------------------------------------------------

    private val EN_DAYS = arrayOf("", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val UR_DAYS = arrayOf("", "پیر", "منگل", "بدھ", "جمعرات", "جمعہ", "ہفتہ", "اتوار")
    private val HI_DAYS = arrayOf("", "सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार", "रविवार")

    /** Period-of-day word for the given 24h hour, per language. */
    private fun periodWord(hour: Int, lang: BalochLanguage): String = when (lang) {
        BalochLanguage.ENGLISH -> ""
        BalochLanguage.URDU -> when (hour) {
            in 5..11 -> "صبح"
            in 12..16 -> "دوپہر"
            in 17..19 -> "شام"
            else -> "رات"
        }
        BalochLanguage.HINDI -> when (hour) {
            in 5..11 -> "सुबह"
            in 12..16 -> "दोपहर"
            in 17..19 -> "शाम"
            else -> "रात"
        }
    }

    /**
     * "tomorrow at 9:00 AM" / "کل صبح 9:00 بجے" / "कल सुबह 9:00 बजे",
     * "every day at 8:00 AM" / "روزانہ صبح 8:00 بجے" / "रोज़ सुबह 8:00 बजे",
     * "every Monday at 9:00 AM" / "ہر پیر کو صبح 9:00 بجے" / "हर सोमवार को सुबह 9:00 बजे".
     */
    fun formatWhen(triggerAtMillis: Long, recurrence: Recurrence, days: Set<Int>, lang: BalochLanguage): String {
        val cal = Calendar.getInstance().apply { timeInMillis = triggerAtMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        return when (lang) {
            BalochLanguage.ENGLISH -> {
                val t = SimpleDateFormat("h:mm a", Locale.UK).format(cal.time)
                when (recurrence) {
                    Recurrence.DAILY -> "every day at $t"
                    Recurrence.WEEKLY -> {
                        val d = days.firstOrNull()?.let { EN_DAYS.getOrElse(it) { "" } }.orEmpty()
                        if (d.isEmpty()) "weekly at $t" else "every $d at $t"
                    }
                    Recurrence.ONCE -> "${dayLabelEn(triggerAtMillis)} at $t"
                }
            }
            BalochLanguage.URDU -> {
                val t = "${periodWord(hour, lang)} $hour:${"%02d".format(minute)} بجے"
                when (recurrence) {
                    Recurrence.DAILY -> "روزانہ $t"
                    Recurrence.WEEKLY -> {
                        val d = days.firstOrNull()?.let { UR_DAYS.getOrElse(it) { "" } }.orEmpty()
                        if (d.isEmpty()) "ہر ہفتے $t" else "ہر $d کو $t"
                    }
                    Recurrence.ONCE -> "${dayLabelUr(triggerAtMillis)} $t"
                }
            }
            BalochLanguage.HINDI -> {
                val t = "${periodWord(hour, lang)} $hour:${"%02d".format(minute)} बजे"
                when (recurrence) {
                    Recurrence.DAILY -> "रोज़ $t"
                    Recurrence.WEEKLY -> {
                        val d = days.firstOrNull()?.let { HI_DAYS.getOrElse(it) { "" } }.orEmpty()
                        if (d.isEmpty()) "हर हफ़्ते $t" else "हर $d को $t"
                    }
                    Recurrence.ONCE -> "${dayLabelHi(triggerAtMillis)} $t"
                }
            }
        }
    }

    private fun dayLabelEn(trigger: Long): String {
        val now = Calendar.getInstance()
        val t = Calendar.getInstance().apply { timeInMillis = trigger }
        fun sameDay(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        if (sameDay(now, t)) return "today"
        val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        if (sameDay(tomorrow, t)) return "tomorrow"
        val iso = ((t.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        return "on ${EN_DAYS[iso]}"
    }

    private fun dayLabelUr(trigger: Long): String = dayLabelNonEn(trigger, "آج", "کل", UR_DAYS, "کو")

    private fun dayLabelHi(trigger: Long): String = dayLabelNonEn(trigger, "आज", "कल", HI_DAYS, "को")

    private fun dayLabelNonEn(trigger: Long, today: String, tomorrow: String, days: Array<String>, ko: String): String {
        val now = Calendar.getInstance()
        val t = Calendar.getInstance().apply { timeInMillis = trigger }
        fun sameDay(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        if (sameDay(now, t)) return today
        val tm = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        if (sameDay(tm, t)) return tomorrow
        val iso = ((t.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        return "${days[iso]} $ko"
    }
}
