package com.baloch.ai.service

import com.baloch.ai.brain.BalochLanguage

/**
 * SEAM INTERFACES — wired by the parent in the final pass.
 *
 * The always-on voice layer (BalochListenerService / WakeWordEngine) never
 * touches concrete sibling classes directly, because they are built
 * concurrently and may not exist yet at compile time. Everything the
 * hands-free path needs from the rest of the app flows through
 * [HandsFreePipeline]:
 *
 * - `execute` → owned by the sibling builder of agents/Orchestrator.kt.
 *   Takes the transcribed command and the active language, runs the
 *   multi-agent pipeline, and returns the reply text to speak.
 * - `verifySpeaker` → owned by the sibling builder of
 *   voice/VoiceLockManager.kt (its `verify(context, audio)` has identical
 *   semantics: 16 kHz mono PCM in, Boolean out).
 * - `isVoiceLockEnabled` → likewise owned by VoiceLockManager's enabled
 *   flag. When false (or no voiceprint enrolled), the listener skips
 *   verification and executes directly.
 *
 * CONTRACT the parent must honour when wiring:
 * 1. `verifySpeaker` returning false means "not Kamran" — the listener
 *    speaks a refusal and executes NOTHING. Never wire this to a stub
 *    that always returns true.
 * 2. `execute` is the single source of truth for risk classification.
 *    The listener additionally applies its own conservative keyword
 *    fail-safe (see BalochListenerService.isLocallyRisky) for destructive
 *    commands issued while the screen is off, because a missed risky
 *    action on a locked phone is unacceptable. The two layers are
 *    complementary, not contradictory.
 * 3. Both suspend functions run on Dispatchers.Default from the listener;
 *    they must never touch the main thread directly.
 */
interface HandsFreePipeline {
    /** → Orchestrator: run the command, return the reply to speak. */
    suspend fun execute(commandText: String, lang: BalochLanguage): String

    /** → VoiceLockManager: 16 kHz mono 16-bit PCM in, true = Kamran. */
    suspend fun verifySpeaker(audio: ShortArray): Boolean

    /** → VoiceLockManager: is the voice lock currently enforcing? */
    fun isVoiceLockEnabled(): Boolean
}

/**
 * Wiring point. The parent (final pass) sets this once at app start,
 * e.g.:
 *
 *     HandsFreeWiring.pipeline = object : HandsFreePipeline {
 *         override suspend fun execute(commandText: String, lang: BalochLanguage) =
 *             orchestrator.execute(commandText, lang)
 *         override suspend fun verifySpeaker(audio: ShortArray) =
 *             voiceLockManager.verify(appContext, audio)
 *         override fun isVoiceLockEnabled() =
 *             voiceLockManager.isEnabled(appContext)
 *     }
 *
 * Until it is set, BalochListenerService handles every wake word honestly:
 * it says the pipeline isn't wired yet and does nothing else.
 */
object HandsFreeWiring {
    @Volatile
    var pipeline: HandsFreePipeline? = null
}
