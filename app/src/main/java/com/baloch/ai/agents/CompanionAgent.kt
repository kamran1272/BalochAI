package com.baloch.ai.agents

import android.content.Context
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.DeviceAction
import com.baloch.ai.brain.L3
import com.baloch.ai.companion.CHECKIN_MESSAGE
import com.baloch.ai.companion.COMPANION_FACTS
import com.baloch.ai.companion.COMPANION_JOKES
import com.baloch.ai.companion.COMPANION_STORIES
import com.baloch.ai.companion.CompanionPicker
import com.baloch.ai.companion.FALLBACK_LINES
import com.baloch.ai.companion.MOOD_REPLIES
import com.baloch.ai.companion.SMALLTALK_AUR_SUNAO
import com.baloch.ai.companion.SMALLTALK_HOW_ARE_YOU
import com.baloch.ai.companion.SMALLTALK_TUM_KAISE_HO
import com.baloch.ai.memory.MemoryStore

/**
 * Companion — Baloch's friendship layer.
 *
 * Kamran's order: Baloch is also a real-human-like FRIEND. Chit-chat when
 * he's bored, asks about his wellbeing, remembers personal things. Warm,
 * playful, loyal — "sir" with affection.
 *
 * Responsibilities:
 *  - Content triggers: jokes, fun facts, stories, small talk (trilingual,
 *    incl. Roman Urdu input).
 *  - Mood awareness: bored / tired / happy / stressed / excited keyword
 *    sets in 3 languages -> empathetic reply + gentle offer. Never clinical.
 *  - Fallback role: [chatFallback] answers when NO command intent matched,
 *    so the user never gets a dead "I don't understand".
 *  - Personal memory weave: occasionally references a fact from
 *    [MemoryStore.facts()] naturally in conversation.
 *
 * HONESTY BOUNDARY (standing): warm but honest. Baloch never claims to be
 * human, never fakes feelings ("as an AI I don't feel things the way you
 * do"), never pretends to have a body, a family, or a life outside this
 * phone. It can be playful and loyal without lying about what it is.
 *
 * Routing note: Companion never executes device actions, so [canHandle]
 * always returns false. The parent routes conversational input here via
 * [chat]/[chatFallback] directly, or through [execute] with the raw text
 * in [AgentTask.partText] (e.g. when the Orchestrator splits a multi-part
 * command and one part is conversational).
 */
class CompanionAgent(
    private val memory: MemoryStore,
    private val context: Context,
) : SpecialistAgent {

    override val name: String = "Companion"
    override val icon: String = "💬"

    override fun canHandle(a: DeviceAction): Boolean = false

    override suspend fun execute(t: AgentTask): AgentResult =
        AgentResult(agentName = name, ok = true, line = chat(t.partText, t.lang))

    // ------------------------------------------------------------------
    // Main entry: conversational input -> reply
    // ------------------------------------------------------------------

    /**
     * Full conversational pipeline: content triggers, then mood awareness,
     * then [chatFallback]. Occasionally appends a personal-memory weave.
     */
    fun chat(input: String, lang: BalochLanguage): String {
        val triggerReply = matchTrigger(input, lang)
        val moodReply = if (triggerReply == null) matchMood(input, lang) else null
        val base = triggerReply ?: moodReply ?: chatFallback(input, lang)
        val weave = maybeWeaveMemory(lang)
        return if (weave != null) "$base\n\n$weave" else base
    }

    /**
     * FALLBACK ROLE — called when no command intent matched anywhere.
     * Warm acknowledgement + concrete options, never a dead end.
     * Content banks give it variety (no immediate repeats).
     */
    fun chatFallback(input: String, lang: BalochLanguage): String {
        val line = CompanionPicker.pickL3("fallback", FALLBACK_LINES, lang)
        // Every ~4th fallback, attach a concrete taster so the user discovers
        // what Baloch can do conversationally.
        val taster = if ((0..3).random() == 0) {
            "\n\n" + CompanionPicker.pickL3("fallback_taster", FALLBACK_TASTERS, lang)
        } else ""
        return line + taster
    }

    // ------------------------------------------------------------------
    // Content triggers (trilingual, incl. Roman Urdu)
    // ------------------------------------------------------------------

    private fun matchTrigger(input: String, lang: BalochLanguage): String? {
        val l = input.lowercase().trim()
        return when {
            containsAny(l, TRIGGER_STORY) ->
                CompanionPicker.pickL3("story", COMPANION_STORIES, lang)
            containsAny(l, TRIGGER_JOKE) ->
                CompanionPicker.pickL3("joke", COMPANION_JOKES, lang)
            containsAny(l, TRIGGER_FACT) ->
                CompanionPicker.pickL3("fact", COMPANION_FACTS, lang)
            containsAny(l, TRIGGER_HOW_ARE_YOU) ->
                CompanionPicker.pickL3("howareyou", SMALLTALK_HOW_ARE_YOU, lang)
            containsAny(l, TRIGGER_TUM_KAISE_HO) ->
                CompanionPicker.pickL3("tumkaiseho", SMALLTALK_TUM_KAISE_HO, lang)
            containsAny(l, TRIGGER_AUR_SUNAO) ->
                CompanionPicker.pickL3("aursunao", SMALLTALK_AUR_SUNAO, lang)
            else -> null
        }
    }

    // ------------------------------------------------------------------
    // Mood awareness — empathetic reply + gentle offer, never clinical
    // ------------------------------------------------------------------

    private fun matchMood(input: String, lang: BalochLanguage): String? {
        val l = input.lowercase().trim()
        for ((mood, keywords) in MOOD_KEYWORDS) {
            if (containsAny(l, keywords)) {
                val bank = MOOD_REPLIES[mood] ?: continue
                return CompanionPicker.pickL3("mood_$mood", bank, lang)
            }
        }
        return null
    }

    // ------------------------------------------------------------------
    // Personal memory weave
    // ------------------------------------------------------------------

    /**
     * ~1 in 3 conversational replies, naturally reference one personal fact
     * Kamran shared (via MemoryStore). Reads ONLY — the parent's brain owns
     * writes. Returns null when there is nothing to weave or the dice say no.
     *
     * Honesty boundary: facts are quoted as things *he told Baloch* — Baloch
     * never invents shared experiences ("remember when we...") because there
     * are none. It remembers what it was told, like a good friend would.
     */
    private fun maybeWeaveMemory(lang: BalochLanguage): String? {
        if ((0..2).random() != 0) return null // ~1/3 chance
        val facts = try {
            memory.facts().filter { it.isNotBlank() }
        } catch (_: Exception) {
            return null
        }
        if (facts.isEmpty()) return null
        val fact = facts.random()
        val short = if (fact.length > 90) fact.take(87) + "..." else fact
        val low = fact.lowercase()
        val hook: L3 = when {
            "cricket" in low -> L3(
                en = "Waise sir, cricket ka kya scene hai aj kal? 🏏",
                ur = "ویسے سر، کرکٹ کا کیا سین ہے آج کل؟ 🏏",
                hi = "वैसे सर, क्रिकेट का क्या सीन है आजकल? 🏏",
            )
            "football" in low || "soccer" in low -> L3(
                en = "Waise sir, football ka kya chal raha hai aj kal? ⚽",
                ur = "ویسے سر، فٹ بال کا کیا چل رہا ہے آج کل؟ ⚽",
                hi = "वैसे सर, फ़ुटबॉल का क्या चल रहा है आजकल? ⚽",
            )
            "exam" in low || "paper" in low || "test" in low || "امتحان" in low || "परीक्षा" in low -> L3(
                en = "Waise sir, aap ne bataya tha — \"{fact}\". Tayyari kaisi chal rahi hai? 📚",
                ur = "ویسے سر، آپ نے بتایا تھا — \"{fact}\"۔ تیاری کیسی چل رہی ہے؟ 📚",
                hi = "वैसे सर, आपने बताया था — \"{fact}\"। तैयारी कैसी चल रही है? 📚",
            )
            "gym" in low || "workout" in low || "exercise" in low -> L3(
                en = "Waise sir, gym ka routine kaisa chal raha hai? 💪",
                ur = "ویسے سر، جم کا روٹین کیسا چل رہا ہے؟ 💪",
                hi = "वैसे सर, जिम का रूटीन कैसा चल रहा है? 💪",
            )
            "client" in low || "project" in low || "freelanc" in low || "order" in low -> L3(
                en = "Waise sir, aap ne bataya tha — \"{fact}\". Us ka kya bana? 💼",
                ur = "ویسے سر، آپ نے بتایا تھا — \"{fact}\"۔ اس کا کیا بنا؟ 💼",
                hi = "वैसे सर, आपने बताया था — \"{fact}\"। उसका क्या बना? 💼",
            )
            else -> L3(
                en = "Waise sir, aap ne bataya tha — \"{fact}\". Us ka kya scene hai ab? 😊",
                ur = "ویسے سر، آپ نے بتایا تھا — \"{fact}\"۔ اس کا کیا سین ہے اب؟ 😊",
                hi = "वैसे सर, आपने बताया था — \"{fact}\"। उसका क्या सीन है अब? 😊",
            )
        }
        return hook.format(lang, "fact" to short)
    }

    // ------------------------------------------------------------------
    // Keyword tables
    // ------------------------------------------------------------------

    private fun containsAny(haystack: String, needles: List<String>): Boolean =
        needles.any { it in haystack }

    companion object {
        // Explicit content requests. Order in matchTrigger(): story, joke,
        // fact first (e.g. "koi joke sunao" contains "sunao" — joke must win
        // over the "aur sunao" small-talk branch).
        private val TRIGGER_STORY = listOf(
            "story", "stories", "kahani", "کہانی", "कहानी",
            "qissa", "kissa", "قصہ", "किस्सा", "قصه",
        )
        private val TRIGGER_JOKE = listOf(
            "joke", "jokes", "mazak", "لطیفہ", "मज़ाक",
            "hansao", "hasao", "ہنساؤ", "हँसाओ", "funny",
            "لطیفہ سناؤ", "چٹکلہ",
        )
        private val TRIGGER_FACT = listOf(
            "fun fact", "fact batao", "facts", "mazedar", "مزیدار", "मज़ेदार",
            "interesting", "دلچسپ", "दिलचस्प", "maloomat", "معلومات", "जानकारी",
        )
        private val TRIGGER_HOW_ARE_YOU = listOf(
            "how are you", "how r u", "how are u", "how're you",
            "aap kaise", "آپ کیسے", "आप कैसे",
        )
        private val TRIGGER_TUM_KAISE_HO = listOf(
            "tum kaise ho", "تم کیسے ہو", "तुम कैसे हो",
            "tu kaisa", "تم کیسا", "तुम कैसा",
        )
        private val TRIGGER_AUR_SUNAO = listOf(
            "aur sunao", "اور سناؤ", "और सुनाओ",
            "aur suna", "اور سنا", "और सुना",
            "kya haal", "کیا حال", "क्या हाल",
            "haal chaal", "حال چال", "हाल चाल",
            "kese ho", "کیسے ہو", "कैसे हो",
            "gup shup", "gupshup", "گپ شپ", "गपशप",
        )

        // Mood keyword sets — checked only when no content trigger matched.
        // Order: tired, stressed, bored, happy, excited.
        private val MOOD_KEYWORDS: List<Pair<String, List<String>>> = listOf(
            "tired" to listOf(
                "tired", "thak gaya", "thak gayi", "thaka", "thaki",
                "تھکا", "تھکی", "थका", "थकी",
                "thakan", "تھکن", "थकान", "sleepy", "neend",
                "نیند", "नींद", "exhausted",
            ),
            "stressed" to listOf(
                "stress", "tension", "tention", "pareshan", "preshan",
                "پریشان", "परेशान", "fikar", "فکر", "फ़िक्र",
                "worried", "deadline", "anxious", "دباؤ",
            ),
            "bored" to listOf(
                "bore ho", "bored", "boring", "boriyat",
                "بوریت", "बोरियत", "bore horaha", "bore ho raha",
            ),
            "happy" to listOf(
                "happy", "khush", "خوش", "खुश", "maza aa",
                "مزہ", "मज़ा", "awesome", "badhiya", "زبردست", "जबरदस्त",
                "great news", "kamal", "کمال", "कमाल", "party", "jeet",
                "جیت", "जीत",
            ),
            "excited" to listOf(
                "excited", "so excited", "can't wait", "cant wait",
                "bechain", "بے چین", "बेचैन",
            ),
        )

        /** Taster lines attached to every ~4th fallback, so misses teach. */
        private val FALLBACK_TASTERS: List<L3> = listOf(
            L3(
                en = "Psst — try 'koi joke sunao', 'fun fact', or 'kahani sunao'. I keep a whole library for slow evenings. 📚",
                ur = "سنو — 'کوئی جوک سناؤ'، 'فن فیکٹ'، یا 'کہانی سناؤ' آزماؤ۔ میں سست شاموں کے لیے پوری لائبریری رکھتا ہوں۔ 📚",
                hi = "सुनो — 'कोई जोक सुनाओ', 'फ़न फ़ैक्ट', या 'कहानी सुनाओ' आज़माओ। मैं धीमी शामों के लिए पूरी लाइब्रेरी रखता हूँ। 📚",
            ),
            L3(
                en = "By the way — if you're bored, just say 'bore ho raha hun' and I'll keep you company. 🎧",
                ur = "ویسے — اگر بور ہو رہے ہو تو بس کہو 'بور ہو رہا ہوں' اور میں آپ کا ساتھ دوں گا۔ 🎧",
                hi = "वैसे — अगर बोर हो रहे हो तो बस कहो 'बोर हो रहा हूँ' और मैं आपका साथ दूँगा। 🎧",
            ),
            L3(
                en = "Tip: tell me things like 'remember I like cricket' and I'll bring them up later, like a proper friend. 🧠",
                ur = "ٹپ: مجھے 'یاد رکھو مجھے کرکٹ پسند ہے' جیسی باتیں بتاؤ اور میں بعد میں ان کا ذکر کروں گا، ایک اچھے دوست کی طرح۔ 🧠",
                hi = "टिप: मुझे 'याद रखो मुझे क्रिकेट पसंद है' जैसी बातें बताओ और मैं बाद में उनका ज़िक्र करूँगा, एक अच्छे दोस्त की तरह। 🧠",
            ),
        )

        /** Exposed for tests / the parent: the proactive check-in line. */
        val checkInMessage: L3 = CHECKIN_MESSAGE
    }
}
