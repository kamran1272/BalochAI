package com.baloch.ai.brain

import com.baloch.ai.settings.BrainMode

/**
 * The default Baloch brain: rules first, LLM only on a clean miss.
 *
 * Flow per input:
 * ```
 * input → RuleBasedBrain ── match ──► reply (+ device actions)   [instant]
 *              │
 *              └─ pure fallback ──► BrainMode.HYBRID? ── yes ──► LlmBrain ──► reply
 *                                        │                          │
 *                                        no                         └─ missing/OOM/error
 *                                        │                                │
 *                                        ▼                                ▼
 *                                   rule fallback              warm companion-style
 *                                   (unchanged)                fallback (never an
 *                                                              error dump)
 * ```
 *
 * Why this shape:
 * - Every device effect stays deterministic: only the rule engine emits
 *   [DeviceAction]s (see the safety rule in [LlmBrain]). The LLM is
 *   text-only, so it can never be prompt-injected into calling or
 *   messaging anyone.
 * - Offline-safe: when the model isn't downloaded, is still loading, or
 *   the device OOMs, the user gets a friendly line — never a crash, never
 *   a stack trace.
 * - Fast: the LLM engine (10–15 s cold start, seconds per reply on CPU)
 *   only spins up for inputs the instant rule engine genuinely doesn't
 *   understand.
 *
 * A "pure fallback" is exactly [Replies.NOT_UNDERSTOOD] with no actions.
 * Real rule answers (contact-not-found, permission-needed, …) are never
 * overridden by the LLM.
 */
class HybridBrain(
    private val rules: RuleBasedBrain,
    private val llm: LlmBrain,
    /** Reads [BrainMode] from [com.baloch.ai.settings.AiBrainPrefs]; parent-wired. */
    private val brainMode: () -> BrainMode = { BrainMode.HYBRID },
) : BrainEngine {

    override suspend fun respond(input: String, context: DeviceContext): BrainResponse {
        val ruleResp = rules.respond(input, context)
        if (!isPureFallback(ruleResp, context.language)) return ruleResp
        if (brainMode() == BrainMode.RULES_ONLY) return ruleResp
        return try {
            val llmResp = llm.respond(input, context)
            // LlmBrain never throws, but an empty/garbage reply still
            // degrades to the warm companion line below.
            if (llmResp.text.isBlank()) BrainResponse(COMPANION_FALLBACK.pick(context.language))
            else llmResp
        } catch (_: Exception) {
            BrainResponse(COMPANION_FALLBACK.pick(context.language))
        }
    }

    private fun isPureFallback(resp: BrainResponse, lang: BalochLanguage): Boolean =
        resp.actions.isEmpty() && resp.text == Replies.NOT_UNDERSTOOD.pick(lang)

    companion object {
        /**
         * CompanionAgent-style fallback: warm, a little playful, points
         * Kamran at things that DO work. Used when rules miss AND the LLM
         * is unavailable (not downloaded, OOM, timed out).
         */
        private val COMPANION_FALLBACK = L3(
            en = "That one's outside my rulebook, sir, and my AI brain is having a nap " +
                "😴 Try 'open WhatsApp', 'set an alarm', or download the AI brain from " +
                "Settings → AI Brain 🧠 and ask me anything.",
            ur = "یہ میرے رول بک سے باہر ہے، سر، اور میرا اے آئی دماغ ذرا سو رہا ہے 😴 " +
                "'واٹس ایپ کھولو' یا 'الارم لگاؤ' آزمائیں، یا سیٹنگز ← AI Brain 🧠 سے " +
                "اے آئی دماغ ڈاؤن لوڈ کر کے مجھ سے کچھ بھی پوچھیں۔",
            hi = "यह मेरे रूलबुक से बाहर है, सर, और मेरा एआई दिमाग़ ज़रा सो रहा है 😴 " +
                "'व्हाट्सऐप खोलो' या 'अलार्म लगाओ' आज़माएँ, या सेटिंग्स → AI Brain 🧠 से " +
                "एआई दिमाग़ डाउनलोड करके मुझसे कुछ भी पूछें।",
        )
    }
}
