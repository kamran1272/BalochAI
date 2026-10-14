package com.baloch.ai.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * AI BRAIN — user-facing settings.
 *
 * Which engine answers Kamran:
 * - [BrainMode.RULES_ONLY]: [com.baloch.ai.brain.RuleBasedBrain] alone.
 *   Instant, zero RAM beyond the app, works on every device.
 * - [BrainMode.HYBRID] (default): rules answer everything they recognise;
 *   only a pure rule-miss falls through to
 *   [com.baloch.ai.brain.LlmBrain] (on-device Gemma via MediaPipe).
 *
 * Persisted in the same SharedPreferences file as the language setting
 * ("baloch_prefs"), so there is exactly one prefs file for user choices.
 * [com.baloch.ai.brain.HybridBrain] reads this through the provider lambda
 * the parent wires at construction — never through a static singleton.
 */
enum class BrainMode { RULES_ONLY, HYBRID }

class AiBrainPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Current brain mode. Defaults to HYBRID (rules first, LLM as backup). */
    var brainMode: BrainMode
        get() = runCatching {
            BrainMode.valueOf(
                prefs.getString(KEY_BRAIN_MODE, BrainMode.HYBRID.name)!!
            )
        }.getOrDefault(BrainMode.HYBRID)
        set(value) {
            prefs.edit().putString(KEY_BRAIN_MODE, value.name).apply()
        }

    /**
     * Optional override for the model download URL. Empty = use the
     * built-in [com.baloch.ai.brain.LlmModelManager.MODEL_URL].
     * Useful if Kamran mirrors the .bin on his own storage.
     */
    var customModelUrl: String
        get() = prefs.getString(KEY_MODEL_URL, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_MODEL_URL, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "baloch_prefs"
        private const val KEY_BRAIN_MODE = "ai_brain_mode"
        private const val KEY_MODEL_URL = "ai_model_url"
    }
}
