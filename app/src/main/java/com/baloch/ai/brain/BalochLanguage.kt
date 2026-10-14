package com.baloch.ai.brain

import java.util.Locale

/**
 * The three languages Baloch speaks. Roman Urdu is handled inside the
 * intent patterns (RuleBasedBrain) rather than as a separate locale,
 * because Kamran types and speaks Roman Urdu himself.
 */
enum class BalochLanguage(
    val code: String,
    /** Locale tag passed to SpeechRecognizer (EXTRA_LANGUAGE). */
    val speechLocaleTag: String,
    /** Locale passed to TextToSpeech.setLanguage(). */
    val ttsLocale: Locale,
    val displayName: String,
) {
    ENGLISH("en", "en-US", Locale.US, "English"),
    URDU("ur", "ur-PK", Locale("ur", "PK"), "اردو"),
    HINDI("hi", "hi-IN", Locale("hi", "IN"), "हिन्दी");

    companion object {
        fun fromCode(code: String): BalochLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

/**
 * One reply in all three languages. Urdu replies are written in Urdu
 * script so the ur-PK TTS voice pronounces them correctly; Hindi replies
 * in Devanagari for the same reason.
 */
data class L3(val en: String, val ur: String, val hi: String) {
    fun pick(lang: BalochLanguage): String = when (lang) {
        BalochLanguage.ENGLISH -> en
        BalochLanguage.URDU -> ur
        BalochLanguage.HINDI -> hi
    }

    /** Fill {placeholders} in the picked reply. */
    fun format(lang: BalochLanguage, vararg args: Pair<String, String>): String {
        var s = pick(lang)
        args.forEach { (k, v) -> s = s.replace("{$k}", v) }
        return s
    }
}
