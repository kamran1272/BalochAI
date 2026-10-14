package com.baloch.ai.life

/**
 * Life-intent matching — trilingual (English, Urdu script, Devanagari)
 * PLUS Roman Urdu, because Kamran types and speaks Roman Urdu himself.
 *
 * The parent (RuleBasedBrain owner) calls [matchLife] before its own
 * patterns; a non-null [LifeHit] carries the parsed arguments and the
 * parent maps it to reply text + DeviceActions.
 *
 * Priority order inside [matchLife] matters: SOS first (safety), then
 * the most specific patterns, generic ones last.
 */

sealed interface LifeHit {
    // --- prayer + qibla ---
    data object PrayerTimes : LifeHit
    data object Qibla : LifeHit
    // --- freelance copilot ---
    data class AddClient(val name: String) : LifeHit
    data object ListClients : LifeHit
    data object ClientSummary : LifeHit
    // --- content ---
    data class VideoIdeas(val count: Int) : LifeHit
    data class ScriptOutline(val topic: String) : LifeHit
    data object TrendScan : LifeHit
    // --- expenses ---
    data class LogExpense(val parsed: ParsedExpense) : LifeHit
    data object MonthlyExpenses : LifeHit
    // --- focus ---
    data class StartFocus(val minutes: Int?) : LifeHit
    data object StopFocus : LifeHit
    // --- location reminders ---
    data class GeoReminder(val parsed: ParsedGeoReminder) : LifeHit
    // --- translator ---
    data class Translate(val parsed: ParsedTranslation) : LifeHit
    data object DownloadTranslationModel : LifeHit
    // --- sos ---
    data object Sos : LifeHit
}

/**
 * Matches one user input against all life intents. Returns null when
 * nothing matches — the parent then falls through to its own brain.
 */
fun matchLife(rawInput: String): LifeHit? {
    val input = rawInput.trim()
    if (input.isBlank()) return null
    val lower = input.lowercase()

    // ---------- 1. SOS (safety first — never shadowed) ----------
    if (isSos(lower)) return LifeHit.Sos

    // ---------- 2. Prayer times ----------
    if (isPrayerTime(lower)) return LifeHit.PrayerTimes
    if (isQibla(lower)) return LifeHit.Qibla

    // ---------- 3. Translator ----------
    parseTranslateVoice(input)?.let { return LifeHit.Translate(it) }
    if (isDownloadModel(lower)) return LifeHit.DownloadTranslationModel

    // ---------- 4. Expenses ----------
    parseExpenseVoice(input)?.let { return LifeHit.LogExpense(it) }
    if (isMonthlyExpense(lower)) return LifeHit.MonthlyExpenses

    // ---------- 5. Focus ----------
    if (isStopFocus(lower)) return LifeHit.StopFocus
    matchFocusStart(lower)?.let { return it }

    // ---------- 6. Location reminders ----------
    parseGeoReminderVoice(input)?.let { return LifeHit.GeoReminder(it) }

    // ---------- 7. Freelance copilot ----------
    matchAddClient(input, lower)?.let { return it }
    if (isListClients(lower)) return LifeHit.ListClients
    if (isClientSummary(lower)) return LifeHit.ClientSummary

    // ---------- 8. Content assistant ----------
    matchVideoIdeas(lower)?.let { return it }
    matchScript(input, lower)?.let { return it }
    if (isTrendScan(lower)) return LifeHit.TrendScan

    return null
}

// ------------------------------------------------------------------
// SOS
// ------------------------------------------------------------------

private fun isSos(lower: String): Boolean {
    // Standalone word "sos" anywhere — safety must never be shadowed
    // by a missed exact-match.
    val words = lower.split(Regex("[\\s,.:;!؟۔\\-]+"))
    if ("sos" in words) return true
    return listOf(
        "sos bhejo", "sos send", "send sos", "emergency sos",
        "sos مدد", "مدد sos", "sos मदद",
    ).any { lower.contains(it) }
}

// ------------------------------------------------------------------
// Prayer + Qibla
// ------------------------------------------------------------------

private fun isPrayerTime(lower: String): Boolean = listOf(
    "namaz ka time", "namaz ka waqt", "prayer time", "prayer times",
    "namaz time", "azan ka time", "azaan ka time", "next prayer",
    "namaz kab hai", "prayer kab hai",
    "نماز کا وقت", "نماز کا ٹائم", "اگلی نماز",
    "नमाज़ का समय", "नमाज का समय", "अगली नमाज़", "नमाज कब है",
).any { lower.contains(it) }

private fun isQibla(lower: String): Boolean = listOf(
    "qibla", "kibla", "qiblah",
    "قبلہ", "क़िबला", "किबला",
    "qibla kidhar", "qibla direction",
).any { lower.contains(it) }

// ------------------------------------------------------------------
// Translator
// ------------------------------------------------------------------

private fun isDownloadModel(lower: String): Boolean = listOf(
    "download translation model", "translation model download",
    "download tarjuma", "model download karo",
).any { lower.contains(it) }

// ------------------------------------------------------------------
// Expenses
// ------------------------------------------------------------------

private fun isMonthlyExpense(lower: String): Boolean = listOf(
    "is mahine kitna kharcha", "is mahine kharcha", "monthly expense",
    "monthly kharcha", "mahine ka hisaab", "hisaab batao",
    "kitna kharcha hua", "total kharcha",
    "اس مہینے خرچ", "اس مہینے کتنا خرچ", "ماہانہ خرچ", "حساب بتاؤ",
    "इस महीने खर्च", "इस महीने कितना खर्च", "मासिक खर्च", "हिसाब बताओ",
).any { lower.contains(it) }

// ------------------------------------------------------------------
// Focus
// ------------------------------------------------------------------

private fun isStopFocus(lower: String): Boolean = listOf(
    "focus stop", "stop focus", "focus band", "focus mode band",
    "فوکس بند", "फोकस बंद",
).any { lower.contains(it) }

private fun matchFocusStart(lower: String): LifeHit.StartFocus? {
    val triggers = listOf(
        "focus mode", "focus start", "start focus", "deep work",
        "فوکس موڈ", "فوکس شروع", "फोकस मोड", "फोकस शुरू",
    )
    if (triggers.none { lower.contains(it) }) return null
    val minutes = Regex("""(\d+)\s*(min|minute|mnt|منٹ|मिनट)""")
        .find(normaliseDigits(lower))
        ?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?.takeIf { it in 5..180 }
    return LifeHit.StartFocus(minutes)
}

// ------------------------------------------------------------------
// Freelance copilot
// ------------------------------------------------------------------

private fun matchAddClient(input: String, lower: String): LifeHit.AddClient? {
    // "add client Ahmed" / "client add karo: Ahmed" / "نیا کلائنٹ Ahmed"
    val patterns = listOf(
        Regex("""add client\s+(.+)""", RegexOption.IGNORE_CASE),
        Regex("""client add karo[:\s]+(.+)""", RegexOption.IGNORE_CASE),
        Regex("""new client\s+(.+)""", RegexOption.IGNORE_CASE),
        Regex("""نیا کلائنٹ\s+(.+)"""),
        Regex("""नया क्लाइंट\s+(.+)"""),
    )
    for (p in patterns) {
        val name = p.find(input)?.groupValues?.getOrNull(1)?.trim()
            ?.trimEnd('.', '!', '۔')?.takeIf { it.isNotBlank() }
        if (name != null) return LifeHit.AddClient(name)
    }
    // "Ahmed ko client banao" — name-first Roman Urdu form
    Regex("""(.+?)\s+ko client (banao|add karo)""", RegexOption.IGNORE_CASE)
        .find(input)?.groupValues?.getOrNull(1)?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { return LifeHit.AddClient(it) }
    return null
}

private fun isListClients(lower: String): Boolean = listOf(
    "mere clients", "my clients", "clients batao", "clients dikhao",
    "client list", "sab clients", "all clients",
    "میرے کلائنٹس", "کلائنٹس بتاؤ",
    "मेरे क्लाइंट", "क्लाइंट बताओ",
).any { lower.contains(it) }

private fun isClientSummary(lower: String): Boolean = listOf(
    "freelance summary", "client summary", "earnings batao", "kamai batao",
    "pipeline batao", "kitni kamai",
    "فری لانس خلاصہ", "آمدنی بتاؤ",
    "फ्रीलांस सारांश", "कमाई बताओ",
).any { lower.contains(it) }

// ------------------------------------------------------------------
// Content assistant
// ------------------------------------------------------------------

private fun matchVideoIdeas(lower: String): LifeHit.VideoIdeas? {
    val triggers = listOf(
        "video idea", "video ka idea", "content idea",
        "ویڈیو آئیڈیا", "آئیڈیا دو",
        "वीडियो आइडिया", "आइडिया दो",
    )
    if (triggers.none { lower.contains(it) }) return null
    val count = Regex("""(\d+)""").find(normaliseDigits(lower))
        ?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?.coerceIn(1, 10) ?: 3
    return LifeHit.VideoIdeas(count)
}

private fun matchScript(input: String, lower: String): LifeHit.ScriptOutline? {
    val triggers = listOf("script banao", "script likho", "اسکرپٹ بناؤ", "स्क्रिप्ट बनाओ")
    val trigger = triggers.firstOrNull { lower.contains(it) } ?: return null
    val topic = input.substring(lower.indexOf(trigger) + trigger.length)
        .trimStart(':', ' ', '—', '-', '۔')
        .trimEnd('.', '!', '۔')
        .trim()
    if (topic.isBlank()) return null
    return LifeHit.ScriptOutline(topic)
}

private fun isTrendScan(lower: String): Boolean = listOf(
    "trending", "trend scan", "kya trend", "what's trending",
    "ٹرینڈ", "ट्रेंड",
).any { lower.contains(it) }
