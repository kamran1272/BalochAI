package com.baloch.ai.service

import android.util.Log
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import com.baloch.ai.voice.VoiceOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Verbal yes/no confirmation for risky hands-free actions when the screen
 * is off (locked phone → no dialog possible, so Baloch asks out loud).
 *
 * Usage:
 *     val ok = VerbalConfirm.askYesNo(
 *         tts = voiceOutput,
 *         listen = { listenOnce(lang, 10_000) },   // caller's STT, null on timeout
 *         question = VerbalConfirm.confirmQuestion("call Ammi"),
 *         lang = lang,
 *     )
 *
 * SAFETY: anything unrecognized — silence, timeout, or words that match
 * neither set — returns FALSE. A risky action only proceeds on an explicit
 * yes. This is deliberate: on a locked phone there is no second chance to
 * catch a misheard "yes".
 */
object VerbalConfirm {
    private const val TAG = "VerbalConfirm"

    /**
     * Trilingual yes tokens. Roman Urdu variants included because Kamran
     * answers in Roman Urdu ("haan", "jee", "karo").
     */
    private val YES_TOKENS = setOf(
        "yes", "yeah", "yep", "yup", "ok", "okay", "sure", "doit",
        "haan", "han", "haa", "hanji", "jee", "ji", "karo", "kar",
        "theek", "zaroor",
        "ہاں", "جي",
        "हाँ", "हां", "करो",
    )

    /** Trilingual no tokens. Checked only after the yes set misses. */
    private val NO_TOKENS = setOf(
        "no", "nope", "nah", "not",
        "nahi", "nahin", "na", "mat", "ruko", "ruk", "bas",
        "stop", "cancel", "rehne",
        "نہیں",
        "नहीं", "नही", "मत", "रुको", "बस",
    )

    suspend fun askYesNo(
        tts: VoiceOutput,
        listen: suspend () -> String?,
        question: L3,
        lang: BalochLanguage,
        timeoutMs: Long = 10_000,
    ): Boolean {
        try {
            withContext(Dispatchers.Main) {
                tts.setLanguage(lang)
                tts.speak(question.pick(lang))
            }
        } catch (e: Exception) {
            Log.w(TAG, "failed to speak the confirmation question", e)
        }

        val heard: String? = try {
            withTimeoutOrNull(timeoutMs) { listen() }
        } catch (_: Exception) {
            null
        }

        // Token-based matching (not substring): "na" must not match inside
        // "nahi", and Urdu "ہاں" must not match inside "نہیں".
        val tokens = heard
            ?.lowercase()
            ?.split(Regex("[^\\p{L}]+"))
            ?.filter { it.isNotBlank() }
            .orEmpty()

        if (tokens.any { it in YES_TOKENS }) return true
        if (tokens.any { it in NO_TOKENS }) return false

        // Never log the transcription itself — it's Kamran's private speech.
        Log.i(TAG, "unrecognized confirmation answer — defaulting to NO")
        return false
    }

    /**
     * Builds "Should I {action}? Say yes or no." trilingually. The
     * [actionSummary] is a short English/Roman-Urdu phrase ("call Ammi",
     * "send the message") — mixed-language output is acceptable for a
     * confirmation and keeps the action itself unambiguous.
     */
    fun confirmQuestion(actionSummary: String): L3 = L3(
        en = "Should I $actionSummary, sir? Say yes or no.",
        ur = "کیا میں $actionSummary کروں، سر؟ ہاں یا نہیں کہیں۔",
        hi = "क्या मैं $actionSummary करूँ, सर? हाँ या नहीं कहें।",
    )
}
