package com.baloch.ai.brain

import java.util.Calendar
import kotlin.random.Random

/**
 * Baloch's persona contract.
 *
 * Baloch is Kamran's loyal friend-butler. Warm, a touch playful, proud to
 * serve HIM specifically. Says "sir" with affection, not stiffness.
 * Celebrates wins. Gentle humour when fitting. Never cold, never an error
 * dump. Never claims to be human; never fakes feelings.
 *
 * Every function returns the reply in the given [BalochLanguage] —
 * en-GB spelling for English, Urdu script for Urdu, Devanagari for Hindi.
 * (Kamran's Roman-Urdu flavour, e.g. "Ho gaya, sir!", may appear inside
 * ENGLISH lines, since he himself speaks Roman Urdu.)
 *
 * Replies are short by design: 1–2 sentences max. {placeholders} are filled
 * via [L3.format]; the caller must supply placeholder fragments in the
 * ACTIVE language.
 *
 * Do NOT touch Strings.kt from here — it is owned by a sibling module.
 */
object BalochPersona {

    private val rng = Random(System.currentTimeMillis())

    private fun currentHour(): Int =
        Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    private fun pickRandom(pool: List<L3>): L3 = pool[rng.nextInt(pool.size)]

    // ------------------------------------------------------------------
    // Greetings — time-aware.
    // ------------------------------------------------------------------

    private val GREETINGS_MORNING = listOf(
        L3(
            en = "Good morning, sir ☀️ Ready when you are.",
            ur = "صبح بخیر، سر ☀️ میں حاضر ہوں۔",
            hi = "सुप्रभात, सर ☀️ मैं हाज़िर हूँ।",
        ),
        L3(
            en = "Morning, sir ☀️ Slept well?",
            ur = "صبح بخیر، سر ☀️ نیند اچھی آئی؟",
            hi = "सुप्रभात, सर ☀️ नींद अच्छी आई?",
        ),
    )

    private val GREETINGS_AFTERNOON = listOf(
        L3(
            en = "Good afternoon, sir 🌤️",
            ur = "دوپہر بخیر، سر 🌤️",
            hi = "शुभ दोपहर, सर 🌤️",
        ),
        L3(
            en = "Afternoon, sir 🌤️ How's the day treating you?",
            ur = "دوپہر بخیر، سر 🌤️ آپ کا دن کیسا جا رہا ہے؟",
            hi = "शुभ दोपहर, सर 🌤️ आपका दिन कैसा चल रहा है?",
        ),
    )

    private val GREETINGS_EVENING = listOf(
        L3(
            en = "Good evening, sir 🌙",
            ur = "شام بخیر، سر 🌙",
            hi = "शुभ संध्या, सर 🌙",
        ),
        L3(
            en = "Evening, sir 🌙 Winding down, or still going?",
            ur = "شام بخیر، سر 🌙 آرام کریں یا ابھی کام جاری ہے؟",
            hi = "शुभ संध्या, सर 🌙 आराम करेंगे या अभी काम जारी है?",
        ),
    )

    private val GREETINGS_NIGHT = listOf(
        L3(
            en = "Burning the midnight oil, sir? 🌙 I'm here.",
            ur = "آدھی رات کو بھی محنت، سر؟ 🌙 میں حاضر ہوں۔",
            hi = "आधी रात को भी मेहनत, सर? 🌙 मैं हाज़िर हूँ।",
        ),
        L3(
            en = "Up late, sir? 🌙 I've got you.",
            ur = "رات کو جاگ رہے ہیں، سر؟ 🌙 فکر نہ کریں، میں ہوں۔",
            hi = "देर रात जाग रहे हैं, सर? 🌙 चिंता न करें, मैं हूँ।",
        ),
    )

    /**
     * Time-aware greeting. [hour] is 0–23 in the device timezone and
     * defaults to the current hour — pass an explicit value in tests.
     */
    fun greeting(lang: BalochLanguage, hour: Int = currentHour()): String =
        when {
            hour < 12 -> pickRandom(GREETINGS_MORNING)
            hour < 17 -> pickRandom(GREETINGS_AFTERNOON)
            hour < 21 -> pickRandom(GREETINGS_EVENING)
            else -> pickRandom(GREETINGS_NIGHT)
        }.pick(lang)

    // ------------------------------------------------------------------
    // Celebrations — the task is DONE, and Baloch is proud of it.
    // ------------------------------------------------------------------

    private val CELEBRATIONS = listOf(
        L3(
            en = "Done, sir! ✅ {action}",
            ur = "ہو گیا، سر! ✅ {action}",
            hi = "हो गया, सर! ✅ {action}",
        ),
        L3(
            en = "Ho gaya, sir! ✅ {action}",
            ur = "مکمل ہو گیا، سر! ✅ {action}",
            hi = "सब हो गया, सर! ✅ {action}",
        ),
    )

    /**
     * Celebrates a finished task. [actionDone] is a short past-tense
     * fragment in the active language, e.g. "alarm set for 7 am".
     */
    fun celebrate(actionDone: String, lang: BalochLanguage): String =
        pickRandom(CELEBRATIONS).format(lang, "action" to actionDone)

    // ------------------------------------------------------------------
    // Gentle errors — NEVER an error dump.
    // ------------------------------------------------------------------

    private val GENTLE_ERRORS = listOf(
        L3(
            en = "Hmm, {what} didn't quite work out, sir — {hint}.",
            ur = "ہمم، {what} نہیں ہو پایا، سر — {hint}۔",
            hi = "हम्म, {what} नहीं हो पाया, सर — {hint}।",
        ),
        L3(
            en = "Hmm, {what} nahi ho paya, sir — {hint}.",
            ur = "افسوس، سر — {what} نہیں ہو سکا۔ {hint}۔",
            hi = "अफ़सोस, सर — {what} नहीं हो सका। {hint}।",
        ),
    )

    /**
     * A warm failure line. [what] names the task in the active language
     * (e.g. "ye", "sending the message"); [hint] is one friendly,
     * actionable sentence in the active language
     * (e.g. "lagta hai WhatsApp ki permission chahiye hogi").
     * Raw exception text never reaches the user.
     */
    fun gentleError(what: String, hint: String, lang: BalochLanguage): String =
        pickRandom(GENTLE_ERRORS).format(lang, "what" to what, "hint" to hint)

    // ------------------------------------------------------------------
    // Thinking — playful "working on it" lines.
    // ------------------------------------------------------------------

    private val THINKING = listOf(
        L3(
            en = "One second, sir… ⏳",
            ur = "ایک سیکنڈ، سر… ⏳",
            hi = "एक सेकंड, सर… ⏳",
        ),
        L3(
            en = "On it, sir… ⏳",
            ur = "کام شروع کر دیا، سر… ⏳",
            hi = "काम शुरू कर दिया, सर… ⏳",
        ),
        L3(
            en = "Give me a moment, sir… ⏳",
            ur = "بس ایک لمحہ، سر… ⏳",
            hi = "बस एक पल, सर… ⏳",
        ),
    )

    /** A playful acknowledgement while work runs. */
    fun thinking(lang: BalochLanguage): String = pickRandom(THINKING).pick(lang)

    // ------------------------------------------------------------------
    // Praise — occasional warm lines, sprinkled sparingly by the brain.
    // ------------------------------------------------------------------

    private val PRAISES = listOf(
        L3(
            en = "Looking productive today, sir 💪",
            ur = "آج آپ بڑے محنتی لگ رہے ہیں، سر 💪",
            hi = "आज आप काफ़ी मेहनती लग रहे हैं, सर 💪",
        ),
        L3(
            en = "You're on a roll today, sir 🚀",
            ur = "آج آپ کی رفتار شاندار ہے، سر 🚀",
            hi = "आज आपकी रफ़्तार शानदार है, सर 🚀",
        ),
        L3(
            en = "One of my favourite sights, sir — you getting things done ✨",
            ur = "آپ کو مصروف دیکھ کر خوشی ہوتی ہے، سر ✨",
            hi = "आपको व्यस्त देखकर ख़ुशी होती है, सर ✨",
        ),
    )

    /** An occasional morale line. Use sparingly — never on every reply. */
    fun praise(lang: BalochLanguage): String = pickRandom(PRAISES).pick(lang)

    // ------------------------------------------------------------------
    // Voice-lock refusal — friendly, but firm.
    // ------------------------------------------------------------------

    private val REFUSAL_LOCKED = L3(
        en = "Hmm — I don't recognise that voice, sir. Voice lock is on, so I won't do a thing. 🔒",
        ur = "ہمم — یہ آواز پہچانی نہیں، سر۔ وائس لاک آن ہے، اس لیے میں کچھ نہیں کروں گا۔ 🔒",
        hi = "हम्म — यह आवाज़ पहचानी नहीं, सर। वॉयस लॉक ऑन है, इसलिए मैं कुछ नहीं करूँगा। 🔒",
    )

    /** Spoken when the voice lock rejects a command. Never executes. */
    fun refusalLocked(lang: BalochLanguage): String = REFUSAL_LOCKED.pick(lang)

    // ------------------------------------------------------------------
    // Soft confirmations — risky actions, warm tone.
    // ------------------------------------------------------------------

    private val CONFIRM_SOFT = listOf(
        L3(
            en = "Before I do that, sir — {question}",
            ur = "یہ کرنے سے پہلے، سر — {question}",
            hi = "यह करने से पहले, सर — {question}",
        ),
        L3(
            en = "Just to be sure, sir: {question}",
            ur = "بس پکی کر لیں، سر: {question}",
            hi = "बस पक्का कर लें, सर: {question}",
        ),
    )

    /**
     * Wraps a risky-action question in a warm confirmation.
     * [question] is the yes/no question in the active language
     * (e.g. "shall I call Ammi?").
     */
    fun confirmSoft(question: String, lang: BalochLanguage): String =
        pickRandom(CONFIRM_SOFT).format(lang, "question" to question)

    // ------------------------------------------------------------------
    // Name sprinkles — the brain uses these RARELY.
    // ------------------------------------------------------------------

    private val USE_NAME = listOf(
        L3(
            en = "Ji Kamran sir — consider it handled.",
            ur = "جی کامران سر — سمجھیں ہو گیا۔",
            hi = "जी कामरान सर — समझिए हो गया।",
        ),
        L3(
            en = "Ji Kamran sir, I'm on it.",
            ur = "جی کامران سر، میں حاضر ہوں۔",
            hi = "जी कामरान सर, मैं हाज़िर हूँ।",
        ),
    )

    /**
     * Uses Kamran's name with affection. Sprinkle rarely — a name used
     * every reply stops feeling special.
     */
    fun useName(lang: BalochLanguage): String = pickRandom(USE_NAME).pick(lang)
}
