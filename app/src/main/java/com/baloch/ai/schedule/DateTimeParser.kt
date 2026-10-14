package com.baloch.ai.schedule

import java.util.Calendar

/**
 * Multilingual REGEX datetime parser — no date libraries beyond Calendar.
 *
 * Understands English, Roman Urdu (first-class), Urdu script and Hindi for
 * dates and times in one pass:
 *   "tomorrow 9am" · "kal subah 7 baje" · "کل صبح 7 بجے" · "कल सुबह 7 बजे"
 *   "today 6pm" · "aaj shaam 6 baje" · "in 30 minutes" · "30 minute me"
 *   day names EN + Roman (peer/mangal/budh/jumeraat/jumma/hafta/itwar) +
 *   Urdu script + Hindi, "every day"/"roz"/"ہر روز"/"रोज़" (DAILY),
 *   "every monday"/"har peer" (WEEKLY), times "9am"/"9:30pm"/
 *   "subah 7 baje"/"7 bajay"/"raat 11 baje".
 *
 * Period words resolve 12h ambiguity: subah → AM, dopahar/shaam/raat → PM.
 * A time that has already passed for its target day rolls forward
 * (bare "9am" at 10am → tomorrow 9am; "today 6pm" at 7pm → tomorrow 6pm).
 */
data class ParsedTime(
    val triggerAtMillis: Long,
    val recurrence: Recurrence,
    /** ISO weekdays 1=Monday..7=Sunday; only set for WEEKLY. */
    val days: Set<Int>,
)

/**
 * Full parse result: [ParsedTime] plus [remainder] — the input with every
 * matched date/time fragment removed, so ScheduleKit can extract the
 * inner command ("remind me tomorrow 9am to call Ahmed" → "call Ahmed").
 */
data class ParsedResult(
    val triggerAtMillis: Long,
    val recurrence: Recurrence,
    val days: Set<Int>,
    val remainder: String,
    /** True when a real time-of-day (or "in N minutes") was parsed. */
    val hasExplicitTime: Boolean,
)

object DateTimeParser {

    // ------------------------------------------------------------------
    // Normalisation
    // ------------------------------------------------------------------

    /**
     * Maps non-ASCII digits to ASCII so "۷ بجے" and "७ बजे" parse like "7".
     * Covers Urdu/Arabic-Indic ۰۱۲۳۴۵۶۷۸۹, Eastern Arabic ٠١٢٣٤٥٦٧٨٩ and
     * Devanagari ०१२३४५६७८९.
     */
    private fun asciiDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when (c) {
                    in '۰'..'۹' -> '0' + (c - '۰')
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '०'..'९' -> '0' + (c - '०')
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    private fun normalise(raw: String): String =
        asciiDigits(raw.lowercase())
            .replace(Regex("[.·]"), " ")          // "9 a.m." → "9 a m" handled below
            .replace("a m", "am").replace("p m", "pm")
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Word boundary that also works for Urdu-script/Hindi tokens, where
     * Java's \b is ASCII-only. Matches when the token is delimited by
     * string edges, whitespace or punctuation on both sides.
     */
    private fun tok(vararg alts: String): Regex =
        Regex("(?<=^|[\\s.,!?;:\"])(?:" + alts.joinToString("|") + ")(?=$|[\\s.,!?;:\"])")

    // ------------------------------------------------------------------
    // Vocabulary
    // ------------------------------------------------------------------

    /** ISO weekday 1=Monday..7=Sunday. Longer synonyms first (regex tries in order). */
    private val DAYS: Map<String, Int> = mapOf(
        "monday" to 1, "tuesday" to 2, "wednesday" to 3, "thursday" to 4,
        "friday" to 5, "saturday" to 6, "sunday" to 7,
        // Roman Urdu / Roman Hindi
        "somwar" to 1, "somvar" to 1, "peer" to 1, "pir" to 1,
        "mangal" to 2,
        "budhwar" to 3, "budhvar" to 3, "budh" to 3,
        "jumeraat" to 4, "jumerat" to 4, "guruwar" to 4, "guruvar" to 4,
        "shukrawar" to 5, "shukravar" to 5, "jumma" to 5, "juma" to 5,
        "sanichar" to 6, "shanivar" to 6, "shanwar" to 6, "hafta" to 6,
        "ravivar" to 7, "itvaar" to 7, "itwar" to 7, "etwar" to 7,
        // Urdu script
        "پیر" to 1, "منگل" to 2, "بدھ" to 3, "جمعرات" to 4,
        "جمعہ" to 5, "ہفتہ" to 6, "اتوار" to 7,
        // Hindi (Devanagari)
        "सोमवार" to 1, "मंगलवार" to 2, "बुधवार" to 3, "बृहस्पतिवार" to 4,
        "गुरुवार" to 4, "शुक्रवार" to 5, "शनिवार" to 6, "रविवार" to 7,
    )

    /** Day-name alternation, longest first so "budhwar" wins over "budh". */
    private val DAY_ALT: String =
        DAYS.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

    private val AM_WORDS = listOf("subah", "صبح", "सुबह")
    private val PM_WORDS = listOf(
        "dopahar", "dopehar", "shaam", "sham", "raat",
        "دوپہر", "سہ پہر", "شام", "رات",
        "दोपहर", "शाम", "रात",
    )
    private val BAJE_WORDS = listOf("baje", "bajay", "bajey", "بجے", "बजे", "o'clock", "oclock")

    private val PERIOD_ALT = (AM_WORDS + PM_WORDS).joinToString("|") { Regex.escape(it) }
    private val BAJE_ALT = BAJE_WORDS.joinToString("|") { Regex.escape(it) }

    private val TOMORROW = tok("tomorrow", "kal", "کل", "कल")
    private val DAY_AFTER = tok("day after tomorrow", "parson", "پرسوں", "परसों")
    private val TODAY = tok("today", "aaj", "آج", "आज")

    /**
     * "every day" / "roz" / "ہر روز" / "रोज़" → DAILY.
     * TRICKY: every alternative needs real boundaries — without them "roz"
     * would match inside "frozen" and "daily" inside "dailymotion".
     */
    private val EVERY_DAY = Regex(
        "(?<=^|[\\s.,!?;:\"])(?:every\\s+day|daily)(?=$|[\\s.,!?;:\"])|" +
            tok("ہر روز", "روزانہ", "روز", "रोज़", "रोज", "हर दिन", "roz", "rozana").pattern,
        RegexOption.IGNORE_CASE,
    )

    /** "every monday" / "har peer" / "ہر پیر" / "हर सोमवार" → WEEKLY. */
    private val EVERY_WEEKDAY = Regex(
        "(?<=^|[\\s.,!?;:\"])(?:every|each|har|ہر|हर)\\s+(?:" + DAY_ALT + ")(?=$|[\\s.,!?;:\"])",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Bare day name → one-shot on its next occurrence.
     * TRICKY: boundaries are load-bearing — Roman "peer" (Monday) is a
     * substring of "appear"/"peerless", so an unanchored match would shred
     * innocent commands ("remind me to appear at 9am").
     */
    private val BARE_DAY = Regex(
        "(?<=^|[\\s.,!?;:\"])(?:" + DAY_ALT + ")(?=$|[\\s.,!?;:\"])",
        RegexOption.IGNORE_CASE,
    )

    /** "in 30 minutes" / "30 minute me" / "30 منٹ میں" / "30 मिनट में". */
    private val IN_MINUTES = Regex(
        "(?:in\\s+(\\d+)\\s*min(?:ute)?s?)" +
            "|(?:(\\d+)\\s*min(?:ute)?s?\\s*(?:mein|me|main|میں|में))",
        RegexOption.IGNORE_CASE,
    )

    /**
     * "9am", "9:30pm", "9 am". Tried before the baje pattern.
     * Leading boundary so "9am" can't match inside "x9am".
     */
    private val TIME_AMPM = Regex(
        "(?<=^|[\\s.,!?;:\"'(\\[])(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b",
        RegexOption.IGNORE_CASE,
    )

    /**
     * "subah 7 baje", "7 bajay", "raat 11 baje", "9:30 بجے", "7 o'clock".
     * TRICKY: both the period prefix (group 1) and the baje suffix (group 4)
     * are optional in the regex, so a BARE number technically matches — the
     * caller must discard matches where both are empty (see [firstValidBaje]).
     */
    private val TIME_BAJE = Regex(
        "(?:($PERIOD_ALT)\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(?:($BAJE_ALT)\\b)?",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Fallback "at 9" / "at 9:30" (English only — Roman "ko" is too common).
     * Leading boundary so "at" can't match inside "seat"/"great".
     */
    private val TIME_AT = Regex(
        "(?<=^|[\\s.,!?;:\"'(\\[])(?:at|@)\\s+(\\d{1,2})(?::(\\d{2}))?",
        RegexOption.IGNORE_CASE,
    )

    // ------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------

    /** Returns null when no date/time can be found in [raw]. */
    fun parse(raw: String): ParsedResult? {
        val s = normalise(raw)
        if (s.isEmpty()) return null
        val now = System.currentTimeMillis()

        // Ranges of consumed fragments, deleted at the end to build remainder.
        val consumed = mutableListOf<IntRange>()

        // ---- 1. Relative "in N minutes" (handled first: no time-of-day). ----
        IN_MINUTES.find(s)?.let { m ->
            val n = (m.groups[1]?.value ?: m.groups[2]?.value)?.toIntOrNull() ?: return null
            if (n <= 0 || n > 24 * 60) return null
            consumed.add(m.range)
            return ParsedResult(
                now + n * 60_000L, Recurrence.ONCE, emptySet(), remainder(s, consumed),
                hasExplicitTime = true,
            )
        }

        // ---- 2. Recurrence: DAILY / WEEKLY. ----
        var recurrence = Recurrence.ONCE
        var weekDay = -1
        EVERY_DAY.find(s)?.let { m ->
            recurrence = Recurrence.DAILY
            consumed.add(m.range)
        } ?: EVERY_WEEKDAY.find(s)?.let { m ->
            recurrence = Recurrence.WEEKLY
            consumed.add(m.range)
            // The day name sits at the tail of the match.
            weekDay = dayOf(m.value)
        }

        // ---- 3. Calendar date: tomorrow / today / parson / named day. ----
        var dayOffset: Int? = null
        var namedDay = -1
        if (recurrence != Recurrence.WEEKLY) {
            // Order matters: "day after tomorrow" contains "tomorrow".
            DAY_AFTER.find(s)?.let { m -> dayOffset = 2; consumed.add(m.range) }
                ?: TOMORROW.find(s)?.let { m -> dayOffset = 1; consumed.add(m.range) }
                ?: TODAY.find(s)?.let { m -> dayOffset = 0; consumed.add(m.range) }
                ?: BARE_DAY.find(s)?.let { m ->
                    val d = dayOf(m.value)
                    if (d > 0) {
                        namedDay = d
                        val todayIso = isoDow(Calendar.getInstance())
                        dayOffset = (d - todayIso + 7) % 7
                        consumed.add(m.range)
                    }
                }
        } else {
            // WEEKLY: the weekday came from the "every <day>" match.
            namedDay = weekDay
        }

        // ---- 4. Time of day: explicit am/pm first, then baje-style, then "at H". ----
        var hour = -1
        var minute = 0
        var timeFound = false
        val ampmM = TIME_AMPM.find(s)
        val bajeM = if (ampmM == null) firstValidBaje(s) else null
        val atM = if (ampmM == null && bajeM == null) TIME_AT.find(s) else null
        when {
            ampmM != null -> {
                consumed.add(ampmM.range)
                val raw = ampmM.groupValues[1].toIntOrNull()
                if (raw != null && raw in 0..23) {
                    hour = resolveHour(raw, ampmM.groupValues[3], null)
                    minute = ampmM.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: 0
                    timeFound = true
                }
            }
            bajeM != null -> {
                consumed.add(bajeM.range)
                val raw = bajeM.groupValues[2].toIntOrNull()
                if (raw != null && raw in 0..23) {
                    hour = resolveHour(raw, null, bajeM.groupValues[1])
                    minute = bajeM.groupValues[3].toIntOrNull()?.takeIf { it in 0..59 } ?: 0
                    timeFound = true
                }
            }
            atM != null -> {
                consumed.add(atM.range)
                val raw = atM.groupValues[1].toIntOrNull()
                if (raw != null && raw in 0..23) {
                    hour = raw // "at 21" is 24h; "at 9" is 09:00
                    minute = atM.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: 0
                    timeFound = true
                }
            }
        }
        // Nothing date- or time-like was found at all → not a datetime.
        if (consumed.isEmpty()) return null
        // No (valid) time given → default 09:00 (predictable, documented).
        if (hour !in 0..23) { hour = 9; minute = 0 }

        // ---- 5. Combine into the trigger. ----
        val trigger = when {
            recurrence == Recurrence.WEEKLY && namedDay in 1..7 -> {
                nextWeekdayAt(namedDay, hour, minute, now)
            }
            dayOffset != null -> {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.DAY_OF_YEAR, dayOffset!!)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                var t = cal.timeInMillis
                if (t <= now) {
                    // Time already passed on the target day:
                    // named weekday → same day next week; otherwise next day.
                    cal.add(Calendar.DAY_OF_YEAR, if (namedDay in 1..7 && dayOffset == 0) 7 else 1)
                    t = cal.timeInMillis
                }
                t
            }
            else -> {
                // Time only: today if still ahead, else tomorrow.
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                var t = cal.timeInMillis
                if (t <= now) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    t = cal.timeInMillis
                }
                t
            }
        }

        val days = if (recurrence == Recurrence.WEEKLY && namedDay in 1..7) setOf(namedDay) else emptySet()
        return ParsedResult(trigger, recurrence, days, remainder(s, consumed), timeFound)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * First baje-style match that carries a real time signal — i.e. a period
     * word ("subah 7"), a baje word ("7 bajay"), or both. Bare numbers are
     * skipped so "call Ahmed 2 times" never parses as a time.
     */
    private fun firstValidBaje(s: String): MatchResult? =
        TIME_BAJE.findAll(s).firstOrNull {
            it.groupValues[1].isNotEmpty() || it.groupValues[4].isNotEmpty()
        }

    /** ISO weekday 1=Monday..7=Sunday for a Calendar. */
    private fun isoDow(cal: Calendar): Int = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1

    /** Looks up a day-name token in DAYS (case-insensitive, trims punctuation). */
    private fun dayOf(token: String): Int {
        val t = token.lowercase().trim().trim('.', ',', '!', '?')
        return DAYS[t] ?: DAYS.entries.firstOrNull { (k, _) -> t.endsWith(k) }?.value ?: -1
    }

    /**
     * Resolves 12h → 24h. Explicit am/pm wins; else period words:
     * subah → AM, dopahar/shaam/raat → PM; no marker → hour as-is.
     */
    private fun resolveHour(h: Int, ampm: String?, period: String?): Int {
        if (!ampm.isNullOrEmpty()) {
            return when {
                ampm.equals("am", true) -> if (h == 12) 0 else h
                else -> if (h == 12) 12 else h + 12
            }
        }
        if (!period.isNullOrEmpty()) {
            val p = period.lowercase()
            val isAm = AM_WORDS.any { p.contains(it) }
            val isPm = PM_WORDS.any { p.contains(it) }
            return when {
                isAm && !isPm -> if (h == 12) 0 else h
                isPm && !isAm -> if (h == 12) 12 else (h % 12) + 12
                else -> h
            }
        }
        return h
    }

    /** Next occurrence of ISO [weekday] at h:mm strictly after [now]. */
    private fun nextWeekdayAt(weekday: Int, hour: Int, minute: Int, now: Long): Long {
        for (offset in 0..7) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (isoDow(cal) == weekday && cal.timeInMillis > now) return cal.timeInMillis
        }
        // Unreachable in practice; fall back to +7 days.
        return now + 7L * 24 * 60 * 60 * 1000
    }

    /** Removes consumed ranges (longest-first) and collapses whitespace. */
    private fun remainder(s: String, consumed: List<IntRange>): String {
        if (consumed.isEmpty()) return s
        val sb = StringBuilder(s)
        consumed.sortedByDescending { it.first }.forEach { r ->
            sb.delete(r.first, (r.last + 1).coerceAtMost(sb.length))
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim(' ', ',', '.', '!', '?', ';', ':')
    }
}
