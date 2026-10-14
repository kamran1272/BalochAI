package com.baloch.ai.voice

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import com.baloch.ai.brain.BalochLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Text-to-speech wrapper. Speaks in the active language (ur-PK / hi-IN /
 * en-US); checks voice-data availability and falls back to US English
 * cleanly when the selected language voice is missing.
 *
 * Voice Identity Lock (Kamran's order): Baloch ALWAYS speaks in ONE locked
 * male voice per language, never auto-changes. The [VoiceIdentityManager]
 * persists the locked voice id per locale in DataStore and re-applies it on
 * every setLanguage(). Pitch is held at 0.87 (deeper), rate at 1.0x.
 *
 * Loud by design: before every utterance the MEDIA stream volume
 * (STREAM_MUSIC, which TTS routes through) is boosted to Kamran's saved
 * volume percentage — default 100% (FULL).
 */
class VoiceOutput(context: Context) {

    private val appContext: Context = context.applicationContext

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingLanguage: BalochLanguage? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Lazy so VoiceOutput construction never touches DataStore on the main thread. */
    private val identityManager: VoiceIdentityManager by lazy {
        VoiceIdentityManager(appContext)
    }

    /**
     * Cached voice-volume percentage (0–100). Loaded from DataStore at init;
     * kept in a volatile var so speak() can read it without blocking.
     */
    @Volatile
    private var volumePct: Int = 100

    /** User toggle from the settings row. */
    var enabled: Boolean = true

    /** True when the last setLanguage() landed on the requested language. */
    var usingRequestedLanguage: Boolean = true
        private set

    init {
        scope.launch { volumePct = identityManager.getVolumePct() }
        tts = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                val engine = this.tts ?: return@TextToSpeech
                applyAcoustics(engine)
                // Apply whatever language (and its locked voice) was requested
                // before init finished.
                pendingLanguage?.let { setLanguage(it) }
                pendingLanguage = null
            }
        }
    }

    /**
     * Switch the spoken language. Returns true if the exact language is
     * available, false if we fell back to English.
     *
     * Also re-applies the locked male voice for the effective language
     * (never auto-changes: the saved id wins whenever it still exists).
     */
    fun setLanguage(lang: BalochLanguage): Boolean {
        if (!ready) {
            pendingLanguage = lang
            return true // applied when init completes
        }
        val tts = this.tts ?: return false
        val availability = tts.isLanguageAvailable(lang.ttsLocale)
        val effectiveLang: BalochLanguage
        val ok = if (availability == TextToSpeech.LANG_MISSING_DATA ||
            availability == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            tts.language = Locale.US
            usingRequestedLanguage = false
            effectiveLang = BalochLanguage.ENGLISH
            false
        } else {
            tts.language = lang.ttsLocale
            usingRequestedLanguage = true
            effectiveLang = lang
            true
        }

        // Deeper male voice, normal speed — on every language switch.
        applyAcoustics(tts)

        // Locked voice identity: saved id wins; a new voice is only picked
        // when none is saved or the saved one vanished from the engine.
        scope.launch {
            val engine = this@VoiceOutput.tts ?: return@launch
            val voice = identityManager.ensureVoice(effectiveLang, engine)
            if (voice != null) {
                engine.voice = voice
            }
        }
        return ok
    }

    fun speak(text: String) {
        if (!enabled || !ready) return
        // Boost the media-stream volume so Baloch is always heard, even if
        // the user left the phone's media volume low. TTS audio routes
        // through STREAM_MUSIC, and setStreamVolume with flags=0 applies it
        // silently (no UI popup) right before the utterance.
        boostMusicVolume()
        // Strip markdown-ish bullets so the voice doesn't read asterisks.
        val clean = text.replace("• ", "").replace("*", "")
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "baloch-${System.currentTimeMillis()}")
    }

    /** Preview a voice from the settings screen — same path as speak(). */
    fun previewVoice(sampleText: String) {
        speak(sampleText)
    }

    /**
     * Persist a new voice-volume percentage (0–100) and apply it to the
     * speak() hot path immediately. The settings slider calls this.
     */
    fun setVoiceVolume(pct: Int) {
        volumePct = pct.coerceIn(0, 100)
        scope.launch { identityManager.setVolumePct(pct) }
    }

    fun voiceVolumePct(): Int = volumePct

    /** Voices installed for TTS — used by the voice picker. Never throws. */
    fun availableVoices(): Set<android.speech.tts.Voice> = try {
        tts?.voices ?: emptySet()
    } catch (_: Exception) {
        emptySet()
    }

    /** Name of the currently active TTS voice, if any. */
    fun currentVoiceName(): String? = try {
        tts?.voice?.name
    } catch (_: Exception) {
        null
    }

    /**
     * Kamran picked a voice explicitly — persist the choice and apply it
     * now. His choice is never overridden afterwards (voice identity lock).
     */
    fun pickVoice(lang: BalochLanguage, voiceId: String) {
        scope.launch {
            identityManager.setVoiceId(lang, voiceId)
            withContext(Dispatchers.Main) {
                try {
                    val engine = tts ?: return@withContext
                    engine.voices?.firstOrNull { it.name == voiceId }?.let {
                        engine.voice = it
                        applyAcoustics(engine)
                    }
                } catch (_: Exception) {
                    // Voice switch is best-effort; the id is saved regardless.
                }
            }
        }
    }

    /**
     * True when the saved voice had to be re-matched (engine dropped the
     * locked voice) since the last [clearVoiceChangedNote]. The settings
     * screen shows a one-time note for this.
     */
    fun voiceChangedNotePending(): Boolean = identityManager.voiceChangedFallback

    fun clearVoiceChangedNote() = identityManager.clearVoiceChangedFallback()

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        try {
            scope.cancel()
            tts?.shutdown()
        } catch (_: Exception) { /* ignore */ }
        tts = null
    }

    /** Deeper pitch (~0.85) + normal rate — Kamran's locked acoustic identity. */
    private fun applyAcoustics(engine: TextToSpeech) {
        try {
            engine.setPitch(0.87f)
            engine.setSpeechRate(1.0f)
        } catch (_: Exception) { /* some engines reject these; keep defaults */ }
    }

    private fun boostMusicVolume() {
        try {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = (max * volumePct.coerceIn(0, 100) / 100f).toInt().coerceIn(0, max)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        } catch (_: Exception) {
            // Never let volume handling kill speech.
        }
    }
}
