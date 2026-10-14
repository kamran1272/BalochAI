package com.baloch.ai.brain

import android.content.Context

/**
 * Per-utterance auto language detection: figures out WHICH language Kamran
 * is speaking in each individual input, so Baloch replies in that language —
 * including mid-conversation switching ("whatsapp kholo" → Urdu reply, then
 * "set an alarm" → English reply, no manual toggle needed).
 *
 * Three stages, all on-device, instant, no downloads or network:
 *
 * 1. SCRIPT — Urdu (Arabic block) and Hindi (Devanagari) scripts are
 *    unambiguous, so script characters short-circuit everything.
 * 2. ROMAN — Latin-script input is scored against two marker-word sets
 *    (Roman-Urdu vs English); the winner needs a margin ≥ 0.15 of the word
 *    count to count as a decision.
 * 3. FALLBACK — anything low-confidence (< 0.5) or unrecognised returns the
 *    user's MANUAL setting silently. Never prompts "which language?" —
 *    guessing wrong is worse than staying put.
 *
 * Trade-offs (deliberate):
 * - Short utterances ("ok", "hi", "done") are inherently ambiguous → they
 *   land on fallback, not on a 50/50 guess. Kamran's standing preference
 *   for his default language wins over a coin-flip.
 * - Roman-Urdu and English share words ("me", "sir" appear in both marker
 *   sets). Shared markers hit both sides and wash out of the margin; only
 *   genuinely asymmetric utterances pass the margin test.
 * - Latin-script HINDI is NOT distinguished from Latin-script Roman-Urdu:
 *   both are Hindustani written in Latin and the marker sets can't separate
 *   them reliably. Latin input scores only toward URDU or ENGLISH. Hindi is
 *   detected only via Devanagari script. This is the project's standard
 *   convention (Roman Urdu lives inside the intent patterns) — not an
 *   oversight.
 * - A mixed-script input where Urdu and Hindi character counts tie exactly
 *   falls through to stage 2, which will almost certainly land on fallback.
 *   Ties this exact are vanishingly rare in real typing.
 */
data class DetectionResult(
    /** The reply language for THIS turn. On "fallback", equals [manualFallback]. */
    val language: BalochLanguage,
    /** 0..1. Script hits score 0.95; roman hits score the winner's margin.
     *  Below 0.5 means "not sure" → the caller should use the manual setting. */
    val confidence: Float,
    /** "script" | "roman-markers" | "fallback" — which stage decided. */
    val method: String,
)

object LanguageDetector {

    // ------------------------------------------------------------------
    // Settings contract: the AutoLang toggle.
    // SharedPreferences "baloch_settings", key "auto_lang_detect",
    // default TRUE (same prefs file the ViewModel already uses for every
    // other setting).
    // ------------------------------------------------------------------

    private const val PREFS_NAME = "baloch_settings"
    private const val KEY_AUTO_LANG_DETECT = "auto_lang_detect"

    fun isAutoDetectEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_LANG_DETECT, true)

    fun setAutoDetectEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_LANG_DETECT, enabled)
            .apply()
    }

    // ------------------------------------------------------------------
    // Detection.
    // ------------------------------------------------------------------

    /** Roman-stage winner needs at least this margin of the word count. */
    private const val ROMAN_MARGIN = 0.15f

    /** Below this confidence the result is treated as "not sure" → fallback. */
    private const val CONFIDENCE_FLOOR = 0.5f

    /**
     * Detects the language of one utterance. [manualFallback] is the user's
     * current manual setting (from LanguageManager); it is returned whenever
     * confidence is below [CONFIDENCE_FLOOR], on blank input, or when the
     * toggle integration calls detect while auto-detect is off.
     */
    fun detect(
        input: String,
        manualFallback: BalochLanguage = BalochLanguage.ENGLISH,
    ): DetectionResult {
        if (input.isBlank()) return DetectionResult(manualFallback, 0f, "fallback")

        // Stage 1 — script.
        scriptDetect(input)?.let { return it }

        // Stage 2 — roman marker scoring.
        val words = input.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return DetectionResult(manualFallback, 0f, "fallback")

        val n = words.size.toFloat()
        val urduScore = words.count { it in ROMAN_URDU } / n
        val englishScore = words.count { it in ENGLISH_MARKERS } / n
        val margin = kotlin.math.abs(urduScore - englishScore)

        if (margin < ROMAN_MARGIN) {
            // Too close to call — silent fallback.
            return DetectionResult(manualFallback, margin, "fallback")
        }
        val winner = if (urduScore > englishScore) BalochLanguage.URDU else BalochLanguage.ENGLISH
        return if (margin >= CONFIDENCE_FLOOR) {
            DetectionResult(winner, margin, "roman-markers")
        } else {
            // Winner found, but too weakly to trust — silent fallback.
            DetectionResult(manualFallback, margin, "fallback")
        }
    }

    /**
     * Returns non-null when any Urdu (Arabic block U+0600–U+06FF) or Hindi
     * (Devanagari block U+0900–U+097F) characters are present. Mixed script
     * → majority wins; an exact tie returns null so stage 2 gets a look.
     */
    private fun scriptDetect(input: String): DetectionResult? {
        var urdu = 0
        var hindi = 0
        for (c in input) {
            when {
                c in '\u0600'..'\u06FF' -> urdu++
                c in '\u0900'..'\u097F' -> hindi++
            }
        }
        return when {
            urdu > hindi && urdu > 0 -> DetectionResult(BalochLanguage.URDU, 0.95f, "script")
            hindi > urdu && hindi > 0 -> DetectionResult(BalochLanguage.HINDI, 0.95f, "script")
            else -> null
        }
    }

    /** Roman-Urdu markers — function words, verbs and Kamran's own phrasing. */
    private val ROMAN_URDU: Set<String> = setOf(
        "hai", "hain", "ho", "kya", "ka", "ki", "ke", "ko",
        "mein", "me", "ne", "se", "kaise", "kaisa", "acha",
        "nahi", "nahin", "mujhe", "tumhe", "apko", "bhai", "sir",
        "kar", "karna", "karo", "kare", "dena", "lena",
        "batao", "sunao", "kholo", "band", "lagao", "utha", "rakhna",
        "bhool", "yaad", "dilao",
        "kitni", "kitna", "kaun", "kahan", "kab", "abhi",
        "roz", "subah", "shaam", "raat", "aaj", "kal",
        "wala", "wali", "theek", "bas", "phir", "aur",
        "lekin", "magar", "ya",
    )

    /** English markers — common function words. */
    private val ENGLISH_MARKERS: Set<String> = setOf(
        "the", "a", "an", "is", "are", "was", "were",
        "will", "would", "can", "could", "should",
        "what", "when", "where", "which", "how",
        "do", "does", "did", "have", "has", "had",
        "my", "your", "his", "her", "their",
        "this", "that", "these", "those",
        "for", "from", "with", "about", "please", "thanks", "sir",
    )
}
