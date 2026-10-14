package com.baloch.ai.memory

import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import com.baloch.ai.memory.room.Tier

/**
 * Watches EVERY input and extracts personal facts without ever asking
 * "should I remember this?" — Kamran's order: "har baat, mera data uski
 * memory main rahy". Just remembers, then confirms warmly.
 *
 * [extractCandidates] is a PURE function (no I/O, fully unit-testable).
 * Patterns are trilingual: English, Roman Urdu, Urdu script, Hindi.
 * Precision is kept high on purpose — only confident matches are returned;
 * vague or question-like inputs are skipped.
 *
 * Tier rules (two-tier memory):
 * - PERMANENT when the input carries a pin marker ("yaad rakhna",
 *   "keep in mind", ...) OR the fact is auto-important (people/clients,
 *   deadlines, preferences, personal identity).
 * - TEMPORARY otherwise — a 30-day expiry is attached by the repository.
 */
data class ParsedFact(
    val key: String,
    val value: String,
    /** personal|preference|people|work|health|other */
    val category: String,
    val confidence: Float,
    val sourceQuote: String,
    val tier: Tier,
)

/** Pin markers in all three languages — turn the whole exchange PERMANENT. */
private val PIN_MARKERS = listOf(
    "keep in mind", "remember this", "remember that", "save in your memories",
    "save this in your memory", "note kar lo", "yaad rakhna", "hamesha yaad rakhna",
    "یاد رکھنا", "یاد رکھو", "याद रखना", "याद रखो",
)

/** One regex + how to build a ParsedFact from its groups. */
private data class FactPattern(
    val regex: Regex,
    val key: (MatchResult) -> String,
    val value: (MatchResult) -> String,
    val category: String,
    val confidence: Float,
    /** Auto-important categories become PERMANENT without a pin marker. */
    val autoImportant: Boolean = false,
)

private val PATTERNS: List<FactPattern> = listOf(
    // ---- English ----
    FactPattern(
        regex = Regex("""(?i)\bmy\s+birthday\s+is\s+(.{2,40}?)[.!]?$"""),
        key = { "birthday" }, value = { it.groupValues[1].trim() },
        category = "personal", confidence = 0.95f, autoImportant = true,
    ),
    FactPattern(
        regex = Regex("""(?i)\bmy\s+name\s+is\s+([a-z][a-z .'-]{1,40}?)[.!]?$"""),
        key = { "name" }, value = { it.groupValues[1].trim() },
        category = "personal", confidence = 0.95f, autoImportant = true,
    ),
    FactPattern(
        regex = Regex("""(?i)\bmy\s+client\s+([a-z][a-z .'-]{1,40}?)(?:\s+hai)?[.!]?$"""),
        key = { "client" }, value = { it.groupValues[1].trim() },
        category = "people", confidence = 0.92f, autoImportant = true,
    ),
    FactPattern(
        regex = Regex("""(?i)\bdeadline\s+(?:is\s+)?(.{2,60}?)[.!]?$"""),
        key = { "deadline" }, value = { it.groupValues[1].trim() },
        category = "work", confidence = 0.92f, autoImportant = true,
    ),
    FactPattern(
        regex = Regex("""(?i)\bi\s+work\s+as\s+(?:an?\s+)?(.{2,60}?)[.!]?$"""),
        key = { "job" }, value = { it.groupValues[1].trim() },
        category = "work", confidence = 0.9f, autoImportant = true,
    ),
    FactPattern(
        regex = Regex("""(?i)\bi\s+(?:really\s+)?(?:like|love)\s+(.{2,60}?)[.!]?$"""),
        key = { "likes" }, value = { it.groupValues[1].trim() },
        category = "preference", confidence = 0.88f, autoImportant = true,
    ),
    FactPattern(
        regex = Regex("""(?i)\bmy\s+(favourite|favorite)\s+([a-z][a-z ]{0,20}?)\s+is\s+(.{2,60}?)[.!]?$"""),
        key = { m -> "favourite ${m.groupValues[2].trim()}" },
        value = { it.groupValues[3].trim() },
        category = "preference", confidence = 0.9f, autoImportant = true,
    ),
    FactPattern(
        regex = Regex("""(?i)\bmy\s+([a-z][a-z ]{0,28}?)\s+is\s+(.{2,80}?)[.!]?$"""),
        key = { it.groupValues[1].trim() }, value = { it.groupValues[2].trim() },
        category = "personal", confidence = 0.8f, autoImportant = true,
    ),
    // ---- Roman Urdu ----
    FactPattern(
        regex = Regex("""(?i)\bmer[ai]\s+birthday\s+(.{2,40}?)\s+ko\s+hai[.!]?$"""),
        key = { "birthday" }, value = { it.groupValues[1].trim() },
        category = "personal", confidence = 0.93f, autoImportant = true,
    ),
    FactPattern(
        regex = Regex("""(?i)\bmer[ai]\s+naam\s+(.{2,40}?)\s+hai[.!]?$"""),
        key = { "name" }, value = { it.groupValues[1].trim() },
        category = "personal", confidence = 0.93f, autoImportant = true,
    ),
    FactPattern(
        regex = Regex("""(?i)\bmer[ai]\s+client\s+([a-z][a-z .'-]{1,40}?)\s+hai[.!]?$"""),
        key = { "client" }, value = { it.groupValues[1].trim() },
        category = "people", confidence = 0.9f, autoImportant = true,
    ),
    FactPattern(
        regex = Regex("""(?i)\bmujhe\s+(.{2,60}?)\s+pasand\s+hai[.!]?$"""),
        key = { "likes" }, value = { it.groupValues[1].trim() },
        category = "preference", confidence = 0.88f, autoImportant = true,
    ),
    FactPattern(
        regex = Regex("""(?i)\bdeadline\s+(.{2,50}?)\s+ko\s+hai[.!]?$"""),
        key = { "deadline" }, value = { it.groupValues[1].trim() },
        category = "work", confidence = 0.9f, autoImportant = true,
    ),
    // ---- Urdu script ----
    FactPattern(
        regex = Regex("""مجھے\s+(.{2,60}?)\s+پسند\s+ہے[۔.!]?$"""),
        key = { "likes" }, value = { it.groupValues[1].trim() },
        category = "preference", confidence = 0.88f, autoImportant = true,
    ),
    FactPattern(
        regex = Regex("""میر[ای]\s+نام\s+(.{2,40}?)\s+ہے[۔.!]?$"""),
        key = { "name" }, value = { it.groupValues[1].trim() },
        category = "personal", confidence = 0.93f, autoImportant = true,
    ),
    FactPattern(
        regex = Regex("""میر[ای]\s+کلائنٹ\s+(.{2,40}?)\s+ہے[۔.!]?$"""),
        key = { "client" }, value = { it.groupValues[1].trim() },
        category = "people", confidence = 0.9f, autoImportant = true,
    ),
    // ---- Hindi (Devanagari) ----
    FactPattern(
        regex = Regex("""मुझे\s+(.{2,60}?)\s+पसंद\s+है[।.!]?$"""),
        key = { "likes" }, value = { it.groupValues[1].trim() },
        category = "preference", confidence = 0.88f, autoImportant = true,
    ),
    FactPattern(
        regex = Regex("""मेरा\s+नाम\s+(.{2,40}?)\s+है[।.!]?$"""),
        key = { "name" }, value = { it.groupValues[1].trim() },
        category = "personal", confidence = 0.93f, autoImportant = true,
    ),
    FactPattern(
        regex = Regex("""मेरा\s+क्लाइंट\s+(.{2,40}?)\s+है[।.!]?$"""),
        key = { "client" }, value = { it.groupValues[1].trim() },
        category = "people", confidence = 0.9f, autoImportant = true,
    ),
)

/** True when the input explicitly asks Baloch to keep something forever. */
fun isPinRequest(input: String): Boolean {
    val lower = input.lowercase()
    return PIN_MARKERS.any { marker ->
        // ASCII markers match case-insensitively; script markers match as-is.
        lower.contains(marker.lowercase()) || input.contains(marker)
    }
}

/**
 * Pure extraction: returns confident fact candidates, or an empty list.
 * Skips questions, very long inputs, and command-like lines.
 */
fun extractCandidates(input: String): List<ParsedFact> {
    val text = input.trim()
    if (text.isEmpty() || text.length > 300) return emptyList()
    if (text.contains("?") || text.contains("؟")) return emptyList()
    // Don't mine facts out of commands ("open whatsapp", "remind me...").
    if (text.startsWith("/", ignoreCase = true)) return emptyList()

    val pinned = isPinRequest(text)
    val found = mutableListOf<ParsedFact>()
    val usedKeys = mutableSetOf<String>()

    for (pattern in PATTERNS) {
        val match = pattern.regex.find(text) ?: continue
        val key = pattern.key(match).lowercase()
        val value = pattern.value(match)
        if (key.isBlank() || value.isBlank()) continue
        if (key in usedKeys) continue
        // Guard against capturing whole sentences as "my X is Y".
        if (key.split(" ").size > 4 || value.split(" ").size > 12) continue
        usedKeys.add(key)
        found.add(
            ParsedFact(
                key = key,
                value = value,
                category = pattern.category,
                confidence = pattern.confidence,
                sourceQuote = text.take(300),
                tier = if (pinned || pattern.autoImportant) Tier.PERMANENT else Tier.TEMPORARY,
            )
        )
    }
    return found
}

/** Detailed result for call sites that need the tier link (conversation tiering). */
data class ExtractionResult(
    /** Warm confirmation to show/speak, or null when nothing was extracted. */
    val confirmation: String?,
    /** How many facts were actually saved (after de-duplication). */
    val savedCount: Int,
    /** True when at least one saved fact is PERMANENT — pass to logConversation. */
    val savedPermanent: Boolean,
)

private val CONFIRM_TEMPLATES = listOf(
    L3(
        en = "Noted, sir — {v}. I'll remember it. ✅",
        ur = "نوٹ کر لیا، سر — {v}۔ یاد رکھوں گا۔ ✅",
        hi = "नोट कर लिया, सर — {v}। याद रखूँगा। ✅",
    ),
    L3(
        en = "Got it, sir — {v}. Locked in my memory. ✅",
        ur = "سمجھ گیا، سر — {v}۔ میری یادداشت میں محفوظ۔ ✅",
        hi = "समझ गया, सर — {v}। मेरी याददाश्त में सुरक्षित। ✅",
    ),
)

private val PIN_CONFIRM = L3(
    en = "Hamesha yaad rahega, sir 📌",
    ur = "ہمیشہ یاد رہے گا، سر 📌",
    hi = "हमेशा याद रहेगा, सर 📌",
)

/**
 * Extracts facts from [input] and saves the new ones (near-duplicates are
 * skipped via findByKey + value comparison). Returns a warm confirmation in
 * the active language, or null when nothing worth remembering was found.
 *
 * NEVER asks "should I remember this?" — it just remembers and confirms.
 *
 * Pinned/auto-important facts get the 📌 permanent confirmation; ordinary
 * ones get the regular confirmation.
 */
suspend fun autoExtractAndSave(
    input: String,
    repo: MemoryRepository,
    lang: BalochLanguage,
): String? = extractAndSaveDetailed(input, repo, lang).confirmation

/**
 * Same as [autoExtractAndSave] but also reports whether a PERMANENT fact was
 * saved, so the brain can mark the conversation [producedPermanentFact] and
 * the janitor keeps that exchange forever.
 */
suspend fun extractAndSaveDetailed(
    input: String,
    repo: MemoryRepository,
    lang: BalochLanguage,
): ExtractionResult {
    val candidates = extractCandidates(input)
    if (candidates.isEmpty()) return ExtractionResult(null, 0, false)

    var saved = 0
    var savedPermanent = false
    var firstValue: String? = null

    for (c in candidates) {
        // Near-duplicate guard: same key + same value (case-insensitive) → skip.
        val existing = repo.findFactByKey(c.key)
        if (existing != null && existing.value.equals(c.value, ignoreCase = true)) continue

        repo.saveFact(
            key = c.key,
            value = c.value,
            category = c.category,
            confidence = c.confidence,
            sourceQuote = c.sourceQuote,
            tier = c.tier,
        )
        saved++
        if (firstValue == null) firstValue = c.value
        if (c.tier == Tier.PERMANENT) savedPermanent = true
    }

    if (saved == 0) return ExtractionResult(null, 0, false)

    val confirmation = if (savedPermanent && isPinRequest(input)) {
        // Explicit pin → the pin confirmation Kamran asked for.
        PIN_CONFIRM.pick(lang)
    } else {
        CONFIRM_TEMPLATES.random().format(lang, "v" to (firstValue ?: "it"))
    }
    return ExtractionResult(confirmation, saved, savedPermanent)
}
