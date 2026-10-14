package com.baloch.ai.memory

import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3

/**
 * Manual memory controls — clean entry points for the brain. The parent wires
 * [matchMemoryCommand]/[executeMemoryCommand] into RuleBasedBrain; this file
 * never touches the brain itself.
 *
 * Commands (trilingual):
 * - "forget this/that" · "ye bhool jao" · "یہ بھول جاؤ" · "यह भूल जाओ"
 * - "make this permanent" · "ye hamesha yaad rakhna" · "یہ ہمیشہ یاد رکھنا"
 * - "pin this" · "isko pin karo" · "pin kar do"
 */
sealed interface MemoryCmd {
    /** Forget a specific memory. query="this"/"that" → the most recent one. */
    data class Forget(val query: String) : MemoryCmd
    /** Pin the most recent temporary fact/conversation forever. */
    data object MakePermanent : MemoryCmd
    /** Pin arbitrary content (the rest of the user's sentence) forever. */
    data class Pin(val content: String) : MemoryCmd
}

/** Returns the matched command, or null when the input is not a memory command. */
fun matchMemoryCommand(input: String): MemoryCmd? {
    val t = input.trim()
    val lower = t.lowercase()

    // ---- Pin: "pin this", "isko pin karo", "pin kar do" ----
    val pinPatterns = listOf("pin this", "pin that", "pin it", "isko pin karo", "isay pin karo", "pin kar do", "pin karde")
    if (pinPatterns.any { lower.contains(it) }) {
        val content = pinPatterns.fold(t) { acc, p -> acc.replace(p, "", ignoreCase = true) }
            .trim(' ', '.', '!', '،', '۔')
        return MemoryCmd.Pin(content.ifBlank { t })
    }

    // ---- Make permanent: "make this permanent", "ye hamesha yaad rakhna" ----
    val permanentPatterns = listOf(
        "make this permanent", "make it permanent", "keep this forever",
        "ye hamesha yaad rakhna", "yeh hamesha yaad rakhna", "isay hamesha yaad rakhna",
        "isay permanent kar do", "hamesha yaad rakhna",
        "یہ ہمیشہ یاد رکھنا", "اسے ہمیشہ یاد رکھنا",
        "यह हमेशा याद रखना", "इसे हमेशा याद रखना",
    )
    if (permanentPatterns.any { lower.contains(it) || t.contains(it) }) {
        return MemoryCmd.MakePermanent
    }

    // ---- Forget: "forget this", "ye bhool jao", "یہ بھول جاؤ", "यह भूल जाओ" ----
    val forgetRoots = listOf(
        "forget this", "forget that", "forget it", "delete this memory",
        "ye bhool jao", "yeh bhool jao", "isay bhool jao", "ise bhool jao",
        "bhool jao",
        "یہ بھول جاؤ", "اسے بھول جاؤ", "بھول جاؤ",
        "यह भूल जाओ", "इसे भूल जाओ", "भूल जाओ",
    )
    val matchedRoot = forgetRoots.firstOrNull { lower.contains(it.lowercase()) || t.contains(it) }
    if (matchedRoot != null) {
        val remainder = t.replace(matchedRoot, "", ignoreCase = true).trim(' ', '.', '!', '،', '۔')
        // "forget my birthday" → query "my birthday"; bare "forget this" → latest.
        val query = remainder
            .replace(Regex("""(?i)^forget\s+"""), "")
            .replace(Regex("""(?i)^delete\s+"""), "")
            .trim()
        return MemoryCmd.Forget(query.ifBlank { "this" })
    }

    return null
}

private val FORGOT_REPLY = L3(
    en = "Forgotten, sir 🗑",
    ur = "بھول گیا، سر 🗑",
    hi = "भूल गया, सर 🗑",
)

private val NOTHING_FOUND = L3(
    en = "I couldn't find that in my memory, sir — nothing to forget.",
    ur = "سر، میری یادداشت میں یہ نہیں ملا — بھولنے کو کچھ نہیں۔",
    hi = "सर, मेरी याददाश्त में यह नहीं मिला — भूलने को कुछ नहीं।",
)

private val PINNED_REPLY = L3(
    en = "Ab ye hamesha yaad rahega 📌",
    ur = "اب یہ ہمیشہ یاد رہے گا 📌",
    hi = "अब यह हमेशा याद रहेगा 📌",
)

private val NOTHING_TEMPORARY = L3(
    en = "Everything recent is already pinned forever, sir — nothing temporary to keep. 📌",
    ur = "سر، حالیہ سب کچھ پہلے ہی ہمیشہ کے لیے محفوظ ہے — رکھنے کو عارضی کچھ نہیں۔ 📌",
    hi = "सर, हाल की सब कुछ पहले ही हमेशा के लिए सुरक्षित है — रखने को अस्थायी कुछ नहीं। 📌",
)

/** Latest-pointers for "this/that" style commands. */
private val LATEST_WORDS = setOf("this", "that", "it", "ye", "yeh", "isay", "ise", "یہ", "اسے", "यह", "इसे")

/**
 * Executes a matched memory command and returns the confirmation reply in
 * the active language (warm, friendly — BalochPersona tone).
 */
suspend fun executeMemoryCommand(
    cmd: MemoryCmd,
    input: String,
    repo: MemoryRepository,
    lang: BalochLanguage,
): String = when (cmd) {
    is MemoryCmd.Forget -> executeForget(cmd.query, repo, lang)
    is MemoryCmd.MakePermanent -> executeMakePermanent(repo, lang)
    is MemoryCmd.Pin -> executePin(cmd.content, repo, lang)
}

private suspend fun executeForget(
    query: String,
    repo: MemoryRepository,
    lang: BalochLanguage,
): String {
    var removed = 0

    if (query.lowercase() in LATEST_WORDS) {
        // "forget this" → the most recent fact; fall back to latest entity.
        val latestFact = repo.allFacts().maxByOrNull { it.id }
        if (latestFact != null) {
            repo.deleteFact(latestFact.id)
            removed++
        } else {
            val latestEntity = repo.entities().maxByOrNull { it.id }
            if (latestEntity != null) {
                repo.deleteEntity(latestEntity.id)
                removed++
            }
        }
    } else {
        val (facts, entities, _) = repo.searchAll(query)
        facts.forEach { repo.deleteFact(it.id); removed++ }
        entities.forEach { repo.deleteEntity(it.id); removed++ }
    }

    return if (removed > 0) FORGOT_REPLY.pick(lang) else NOTHING_FOUND.pick(lang)
}

private suspend fun executeMakePermanent(
    repo: MemoryRepository,
    lang: BalochLanguage,
): String {
    val temp = repo.latestTemporaryFact()
    if (temp == null) return NOTHING_TEMPORARY.pick(lang)

    repo.upgradeToPermanent(temp.id)
    // Keep the exchange that carried it, too — it produced a permanent fact.
    repo.upgradeLatestTemporaryConversation()
    return PINNED_REPLY.pick(lang)
}

private suspend fun executePin(
    content: String,
    repo: MemoryRepository,
    lang: BalochLanguage,
): String {
    repo.saveFact(
        key = "pinned",
        value = content.take(300),
        category = "other",
        confidence = 1f,
        sourceQuote = content.take(300),
        tier = com.baloch.ai.memory.room.Tier.PERMANENT,
    )
    return PINNED_REPLY.pick(lang)
}
