package com.baloch.ai.rules

/**
 * Trilingual rule-teaching parser (English + Roman Urdu + key Hindi/Urdu cues).
 *
 * Kamran teaches Baloch boundaries in conversation, e.g.
 *   "call karne se pehle hamesha poochna"
 *   "meri permission ke baghair kabhi call mat karna"
 *   "raat 12 ke baad mujhe disturb mat karna"
 *   "call karne se pehle mat poochna"
 *
 * The parent wires this BEFORE the normal intent pipeline:
 *   1. `parseRuleTeach(input)` → non-null → store.add(rule), confirm to Kamran,
 *      do NOT run intent parsing on that utterance.
 *   2. `matchRuleQuery(input)` → ListRules → open MY RULES;
 *      DeleteRule(n) → resolve the 1-based row index against the sorted
 *      rule list from [GuidanceRuleStore.all], then store.delete(id).
 * Return null when the input is not rule-teaching — ordinary commands
 * must never be swallowed by this parser.
 *
 * Every returned rule carries `text` = Kamran's cleaned-up own words
 * (leading "Baloch," stripped, spacing tidied, first letter capitalised)
 * so MY RULES reads back exactly what he taught.
 */

sealed interface RuleQuery {
    /** "show my rules" / "mere rules dikhao" → open the MY RULES screen. */
    data object ListRules : RuleQuery

    /** "delete rule 2" / "rule 2 hata do" — 1-based row index as shown in MY RULES. */
    data class DeleteRule(val index: Int) : RuleQuery
}

// ---------- keyword tables ----------

private val CALL_WORDS = listOf("call", "calling", "phone", "fona", "کال")
private val MSG_WORDS =
    listOf("message", "messages", "sms", "msg", "text", "whatsapp", "پیغام", "میسج", "संदेश")

/** "ask me first" family — permission qualifiers included. */
private val ASK_WORDS = listOf(
    "poochna", "pooch", "puchna", "puch", "pooch le", "pooch lena",
    "permission", "ijazat", "baghair", "bager", "bina pooche",
    "ask me", "ask first", "ask before", "check with me", "confirm first",
    "پوچھنا", "اجازت", "पूछना", "अनुमति",
)

/** Explicit relax family — "don't ask me first". */
private val RELAX_WORDS = listOf(
    "mat poochna", "mat pooch", "don't ask", "dont ask", "do not ask",
    "no need to ask", "poochne ki zaroorat",
)

/** Hard "never do X" family (no permission qualifier → true FORBID). */
private val FORBID_MARKERS = listOf("kabhi bhi", "never", "bilkul", "ہرگز", "कभी भी")
private val NEG_WORDS = listOf(
    "mat karna", "mat karo", "mat kar", "na karna", "na karo", "na kar",
    "don't", "dont", "do not", "مت کرنا",
)

private val DISTURB_WORDS = listOf("disturb", "tung", "dnd", "pareshan", "پریشان", "डिस्टर्ब")
private val NIGHT_WORDS = listOf("raat", "night", "رات", "रात")
private val MORNING_WORDS = listOf("subah", "morning", "صبح", "सुबह")

// ---------- teaching ----------

fun parseRuleTeach(input: String): GuidanceRule? {
    val raw = input.trim()
    if (raw.length < 6) return null
    val t = " ${raw.lowercase()} "

    // 1) Quiet hours — "raat 12 ke baad mujhe disturb mat karna" / "tung na karo"
    if (DISTURB_WORDS.any { it in t } && NEG_WORDS.any { it in t }) {
        return parseQuietRule(raw, t)
    }

    val hasCall = CALL_WORDS.any { it in t }
    val hasMsg = MSG_WORDS.any { it in t }

    // 2) Explicit relax — "call karne se pehle mat poochna" / "don't ask before calling".
    //    Checked BEFORE ask-words: "pooch" sits inside ASK_WORDS and would
    //    otherwise misread "mat poochna" as REQUIRE_CONFIRM (inverted meaning).
    //    A bare "mat poochna" (no domain named) relaxes everything.
    if (RELAX_WORDS.any { it in t }) {
        val scope = when {
            hasCall && hasMsg -> RuleScope.ALL
            hasCall -> RuleScope.CALLS
            hasMsg -> RuleScope.MESSAGES
            else -> RuleScope.ALL
        }
        return GuidanceRule(0, cleanup(raw), scope, Constraint.ALLOW)
    }

    // 3) Confirm — permission qualifiers or "ask first" language.
    //    A permission qualifier ("meri permission ke baghair kabhi call mat karna")
    //    beats the FORBID reading of "kabhi … mat karna".
    if (ASK_WORDS.any { it in t }) {
        val scope = when {
            hasCall && hasMsg -> RuleScope.ALL
            hasCall -> RuleScope.CALLS
            hasMsg -> RuleScope.MESSAGES
            // Bare "hamesha pehle mujhse poochna" (no domain named) → ask before everything.
            else -> RuleScope.ALL
        }
        return GuidanceRule(0, cleanup(raw), scope, Constraint.REQUIRE_CONFIRM)
    }

    // 4) True FORBID — "kabhi bhi X mat karna" WITHOUT a permission qualifier.
    //    Always stored ALL-scope with the action named in the text; the
    //    Orchestrator matches FORBID rules by scope only, so scope stays ALL.
    if (FORBID_MARKERS.any { it in t } && NEG_WORDS.any { it in t }) {
        return GuidanceRule(0, cleanup(raw), RuleScope.ALL, Constraint.FORBID)
    }

    return null
}

/**
 * Parses "raat 12 ke baad mujhe disturb mat karna" →
 * DISTURB / QUIET_HOURS(start=0, end=7).
 *
 * ASSUMPTION (documented): "12" + "raat/night/ke baad" means MIDNIGHT (0),
 * not noon — Kamran means "don't wake me after midnight". End hour defaults
 * to 7 (morning). General night hours 1–11 map to 13–23; "subah/morning" +
 * a number maps to that number as-is (AM).
 */
private fun parseQuietRule(raw: String, t: String): GuidanceRule? {
    val number = Regex("""\d{1,2}""").find(t)?.value?.toIntOrNull() ?: 0
    val isNight = NIGHT_WORDS.any { it in t }
    val isMorning = MORNING_WORDS.any { it in t }
    val start = when {
        number == 12 && isNight -> 0          // "raat 12" → midnight, not noon
        number in 1..11 && isNight -> number + 12
        isMorning && number in 1..11 -> number
        "pm" in t && number in 1..11 -> number + 12
        "am" in t && number == 12 -> 0
        number in 0..23 -> number
        else -> 0                             // no time named → from midnight
    }.coerceIn(0, 23)
    return GuidanceRule(
        id = 0,
        text = cleanup(raw),
        scope = RuleScope.DISTURB,
        constraint = Constraint.QUIET_HOURS,
        quietStartHour = start,
        quietEndHour = 7, // morning default; Kamran can rephrase to change it
    )
}

/** Strips a leading "Baloch,"/wake word, tidies spacing, capitalises. */
private fun cleanup(raw: String): String {
    var s = raw.trim().replace(Regex("\\s+"), " ")
    s = s.replace(Regex("^(hey\\s+)?baloch[,\\s]+", RegexOption.IGNORE_CASE), "")
    s = s.trimEnd('.', '!', '؟')
    return s.replaceFirstChar { it.uppercase() }
}

// ---------- queries (list / delete) ----------

private val LIST_CUES = listOf(
    "show my rules", "my rules", "list rules", "rules list",
    "mere rules", "meray rules", "rules dikhao", "rules batao", "rules dekh",
    "mere usool", "guidance rules", "mere qawaneen",
    "मेरे रूल्स", "नियम दिखाओ", "میرے رولز",
)

private val DELETE_PATTERNS = listOf(
    // "delete rule 2", "remove rule 1", "rule 2 hata do", "rule 1 delete karo"
    Regex("""(?:delete|remove|hatao?|hata\s*do|mita\s*do|delete\s*karo)\s+rule\s+(\d+)"""),
    Regex("""rule\s+(\d+)\s+(?:hata\s*do|hatao|delete|remove|mita\s*do|delete\s*karo|hatao?\s*do)"""),
)

fun matchRuleQuery(input: String): RuleQuery? {
    val t = input.trim().lowercase()
    if (t.isEmpty() || t.length > 140) return null // queries are short by nature
    if (LIST_CUES.any { it in t }) return RuleQuery.ListRules
    for (pattern in DELETE_PATTERNS) {
        val n = pattern.find(t)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (n != null && n >= 1) return RuleQuery.DeleteRule(n)
    }
    return null
}
