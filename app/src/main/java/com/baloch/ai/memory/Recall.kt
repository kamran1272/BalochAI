package com.baloch.ai.memory

import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import com.baloch.ai.memory.room.Tier

/**
 * "What do you remember about me?" — recall entry points. The parent wires
 * [matchRecall]/[answerRecall] into the brain before the generic fallbacks.
 *
 * Trilingual, friendly, never robotic. Specific questions ("mera favourite
 * colour kya hai") look up the matching fact; general questions get a warm
 * summary of the most important things Baloch remembers.
 */

/** True when the input is a memory-recall question. */
fun matchRecall(input: String): Boolean {
    val t = input.trim().lowercase()
    val patterns = listOf(
        // English
        "what do you remember", "do you remember", "what do u remember",
        "tell me what you remember", "my favourite", "my favorite",
        "what's my", "what is my",
        // Roman Urdu
        "kya yaad hai", "tumhe kya yaad", "tumhain kya yaad", "mera favourite",
        "meri pasand", "mujhe kya pasand",
        // Urdu script
        "تمہیں کیا یاد ہے", "تمھیں کیا یاد ہے", "کیا یاد ہے",
        // Hindi
        "तुम्हें क्या याद है", "तुम्हे क्या याद है", "क्या याद है",
        "मेरा फेवरेट", "मेरी पसंद",
    )
    return patterns.any { t.contains(it) }
}

/**
 * Answers a recall question. Specific fact lookup first ("mera favourite
 * colour kya hai" → the stored value), then a warm general summary listing
 * top PERMANENT facts, then temporary ones, plus known people.
 */
suspend fun answerRecall(input: String, repo: MemoryRepository, lang: BalochLanguage): String {
    val specific = answerSpecificRecall(input, repo, lang)
    if (specific != null) return specific
    return answerGeneralRecall(repo, lang)
}

// ------------------------------------------------------------- specific lookup

/** "mera favourite colour kya hai" → "Aapka favourite colour blue hai, sir." */
private suspend fun answerSpecificRecall(
    input: String,
    repo: MemoryRepository,
    lang: BalochLanguage,
): String? {
    val t = input.lowercase()

    // "favourite X" / "favorite X" / "pasand" + topic → search key containing X.
    val topic = extractTopic(t) ?: keywordTopic(t, repo)
    if (topic == null || topic.length < 2) return null

    val hits = repo.searchFacts(topic)
    val best = hits.maxByOrNull { it.confidence } ?: return null

    return SPECIFIC_HIT.format(lang, "k" to best.key, "v" to best.value)
}

/**
 * Fallback for free-form recall ("do you remember my birthday?"): strip
 * stopwords, search each significant word, return the first that hits.
 */
private val RECALL_STOPWORDS = setOf(
    "do", "you", "remember", "what", "about", "tell", "me", "my", "the", "a",
    "kya", "hai", "tumhe", "tumhain", "yaad", "mera", "meri", "mujhe", "batao",
    "کیا", "ہے", "تمہیں", "یاد", "میرا", "میری", "مجھے",
    "क्या", "है", "तुम्हें", "याद", "मेरा", "मेरी", "मुझे", "बताओ",
)

private suspend fun keywordTopic(t: String, repo: MemoryRepository): String? {
    val words = t.split(Regex("""[\s؟?.,!]+"""))
        .map { it.trim() }
        .filter { it.length >= 4 && it !in RECALL_STOPWORDS }
    for (w in words) {
        if (repo.searchFacts(w).isNotEmpty()) return w
    }
    return null
}

private val SPECIFIC_HIT = L3(
    en = "Yes, sir — your {k} is {v}. I never forget the important things. 😊",
    ur = "جی سر — آپ کا {k} یہ ہے: {v}۔ اہم باتیں میں کبھی نہیں بھولتا۔ 😊",
    hi = "जी सर — आपका {k} यह है: {v}। ज़रूरी बातें मैं कभी नहीं भूलता। 😊",
)

private fun extractTopic(t: String): String? {
    val patterns = listOf(
        Regex("""(?i)\bmy\s+(?:favourite|favorite)\s+([a-z ]{2,24}?)\s+(?:kya|kya hai|hai|is|what)"""),
        Regex("""(?i)\bmera\s+(?:favourite|favorite)\s+([a-z ]{2,24}?)\s+kya"""),
        Regex("""(?i)\bmeri\s+pasand\s+(?:ka|ki|ke)?\s*([a-z ]{2,24})"""),
        Regex("""(?i)\bwhat(?:'s| is)\s+my\s+([a-z ]{2,24})"""),
        Regex("""(?i)\bmera\s+([a-z ]{2,24}?)\s+kya\s+hai"""),
        Regex("""मेरा\s+([अ-ह ]{2,24}?)\s+क्या\s+है"""),
        Regex("""میر[ای]\s+([^\s؟?]{2,24}?)\s+کیا\s+ہے"""),
    )
    for (p in patterns) {
        val m = p.find(t) ?: continue
        val topic = m.groupValues[1].trim().trimEnd('?', '؟')
        if (topic.isNotBlank()) return topic
    }
    return null
}

// ------------------------------------------------------------- general summary

private val GENERAL_INTRO = L3(
    en = "Here's what I remember about you, sir:",
    ur = "آپ کے بارے میں مجھے یہ یاد ہے، سر:",
    hi = "आपके बारे में मुझे यह याद है, सर:",
)

private val PEOPLE_LINE = L3(
    en = "People I know: {v}",
    ur = "جن لوگوں کو میں جانتا ہوں: {v}",
    hi = "जिन लोगों को मैं जानता हूँ: {v}",
)

private val EMPTY_MEMORY = L3(
    en = "Honestly, sir — my memory of you is still a blank page. Tell me about yourself and I'll never forget. 📝",
    ur = "سچ کہوں سر — آپ کے بارے میں میری یادداشت ابھی خالی صفحہ ہے۔ اپنے بارے میں بتائیں، میں کبھی نہیں بھولوں گا۔ 📝",
    hi = "सच कहूँ सर — आपके बारे में मेरी याददाश्त अभी खाली पन्ना है। अपने बारे में बताएँ, मैं कभी नहीं भूलूँगा। 📝",
)

private suspend fun answerGeneralRecall(repo: MemoryRepository, lang: BalochLanguage): String {
    val facts = repo.allFacts()
    if (facts.isEmpty()) return EMPTY_MEMORY.pick(lang)

    val sb = StringBuilder(GENERAL_INTRO.pick(lang)).append("\n")

    // Permanent first — the important stuff Kamran pinned or Baloch inferred.
    val ordered = facts.sortedWith(
        compareBy({ if (it.tier == Tier.PERMANENT) 0 else 1 }, { -it.createdAt })
    )
    for (f in ordered.take(10)) {
        val icon = if (f.tier == Tier.PERMANENT) "📌" else "🕐"
        sb.append("\n$icon ${f.key}: ${f.value}")
    }

    val people = repo.entities().take(6)
    if (people.isNotEmpty()) {
        sb.append("\n\n").append(
            PEOPLE_LINE.format(lang, "v" to people.joinToString(", ") { it.name })
        )
    }

    if (facts.size > 10) {
        val more = L3(
            en = "\n\n...and ${facts.size - 10} more things. Open the Memory screen to browse them all, sir.",
            ur = "\n\n...اور ${facts.size - 10} باتیں مزید۔ سب دیکھنے کے لیے میموری اسکرین کھولیں، سر۔",
            hi = "\n\n...और ${facts.size - 10} बातें और। सब देखने के लिए मेमोरी स्क्रीन खोलें, सर।",
        )
        sb.append(more.pick(lang))
    }
    return sb.toString()
}
