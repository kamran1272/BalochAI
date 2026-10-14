package com.baloch.ai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.baloch.ai.brain.BalochLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.Locale

/** DataStore holding the locked voice identities (one per language) + volume. */
private val Context.voiceIdStore: DataStore<Preferences> by
    preferencesDataStore("voice_identity")

/**
 * Voice Identity Lock — Kamran's order: Baloch ALWAYS speaks in ONE locked
 * male voice per language (English / Hindi / Urdu) and never auto-changes.
 *
 * The lock is per-locale because a saved voice id is tied to its language's
 * voice data (an en-US id means nothing on the hi-IN voice set). Once a voice
 * is chosen it is stored in DataStore and re-applied on every setLanguage();
 * ensureVoice() returns the saved voice as long as the TTS engine still
 * offers it. It is only re-matched when the saved voice has genuinely
 * vanished (e.g. TTS engine updated) — in that case the closest male match is
 * picked and [voiceChangedFallback] is set so the UI can inform Kamran once
 * ("Baloch's voice was re-matched, sir.") instead of silently drifting.
 *
 * Changing the voice is ALWAYS Kamran's choice (picker + preview in the
 * settings screen) — this manager never picks a new voice on its own.
 */
class VoiceIdentityManager(private val context: Context) {

    private val store: DataStore<Preferences> =
        context.applicationContext.voiceIdStore

    private fun voiceKey(lang: BalochLanguage) =
        stringPreferencesKey("voice_id_${lang.code}")

    /**
     * True when the last ensureVoice() had to re-match because the saved
     * voice id was no longer offered by the engine. The settings screen
     * reads this once to show a one-time note, then calls
     * [clearVoiceChangedFallback].
     */
    @Volatile
    var voiceChangedFallback: Boolean = false
        private set

    fun clearVoiceChangedFallback() {
        voiceChangedFallback = false
    }

    /**
     * Pick the best male-sounding voice for [locale] from [voices].
     *
     * Heuristic, in order:
     *  1. Only voices whose [Voice.getLocale] matches [locale] are
     *     candidates (language equality; country is secondary).
     *  2. Prefer a voice whose name contains "male" (case-insensitive).
     *     This covers Google TTS male ids such as
     *     "en-us-x-sfg#male_1-local", "hi-in-x-hia#male_1-local" and
     *     "ur-pk-x-urm#male_1-local", as well as engines that literally
     *     name voices "Male 1", "Daniel (male)", etc.
     *  3. Otherwise take any locale-matching voice whose name does NOT
     *     contain "female" — most stock voices are either male or unlabeled,
     *     and we must avoid drifting onto a female voice.
     *  4. Fallback: the first voice for the locale.
     *  5. null when no voice exists for the locale at all.
     */
    fun pickBestMaleVoice(voices: Set<Voice>, locale: Locale): Voice? {
        val forLocale = voicesForLocale(voices, locale)
        if (forLocale.isEmpty()) return null

        // 1 — explicit male marker in the voice name.
        forLocale.firstOrNull { it.name.contains("male", ignoreCase = true) }
            ?.let { return it }

        // 2 — any voice that isn't explicitly female.
        forLocale.firstOrNull { !it.name.contains("female", ignoreCase = true) }
            ?.let { return it }

        // 3 — last resort: first voice for the locale.
        return forLocale.first()
    }

    /**
     * Ensure the locked voice for [lang] on this [tts] instance.
     *
     * - Saved id present AND still in tts.voices → use it (NEVER auto-change).
     * - Saved id gone (engine update) → pickBestMaleVoice() → save new id →
     *   set [voiceChangedFallback] so the UI informs Kamran once.
     * - Nothing saved yet (first run) → pickBestMaleVoice() → save → return.
     */
    suspend fun ensureVoice(lang: BalochLanguage, tts: TextToSpeech): Voice? =
        withContext(Dispatchers.IO) {
            val all = try { tts.voices } catch (_: Exception) { null } ?: emptySet()
            val saved = store.data.map { it[voiceKey(lang)] }.first()

            if (saved != null) {
                val stillThere = all.firstOrNull { it.name == saved }
                if (stillThere != null) return@withContext stillThere

                // Saved voice vanished (TTS engine updated its voice list):
                // re-match the closest male voice and flag it for a one-time
                // note instead of silently changing the identity.
                val rematch = pickBestMaleVoice(all, lang.ttsLocale)
                if (rematch != null) {
                    store.edit { it[voiceKey(lang)] = rematch.name }
                    voiceChangedFallback = true
                }
                return@withContext rematch
            }

            val pick = pickBestMaleVoice(all, lang.ttsLocale)
            if (pick != null) {
                store.edit { it[voiceKey(lang)] = pick.name }
            }
            pick
        }

    /** Explicit user choice — only called from the voice picker (Kamran's tap). */
    suspend fun setVoiceId(lang: BalochLanguage, id: String) {
        withContext(Dispatchers.IO) {
            store.edit { it[voiceKey(lang)] = id }
        }
    }

    /** Synchronous read of the locked voice id for [lang], or null if none saved. */
    fun getVoiceId(lang: BalochLanguage): String? =
        runBlocking(Dispatchers.IO) {
            store.data.map { it[voiceKey(lang)] }.first()
        }

    /**
     * Voices for the picker: all engine voices for [locale], male-first,
     * then non-female, then the rest; stable-sorted by name.
     */
    fun voicesForLocale(all: Set<Voice>, locale: Locale): List<Voice> {
        return all
            .filter { it.locale.language.equals(locale.language, ignoreCase = true) }
            .sortedWith(
                compareBy(
                    { v -> if (v.name.contains("male", ignoreCase = true)) 0 else 1 },
                    { v -> if (v.name.contains("female", ignoreCase = true)) 1 else 0 },
                    { it.name },
                ),
            )
    }

    // ---- Voice volume (default FULL 100%) ----

    /** Flow of the voice-volume percentage (0–100, default 100). */
    fun volumeFlow() = store.data.map { it[KEY_VOLUME_PCT] ?: 100 }

    suspend fun getVolumePct(): Int = withContext(Dispatchers.IO) {
        store.data.map { it[KEY_VOLUME_PCT] ?: 100 }.first()
    }

    /** Blocking read used on the speak() hot path (small DataStore read on IO). */
    fun getVolumePctBlocking(): Int = runBlocking(Dispatchers.IO) {
        store.data.map { it[KEY_VOLUME_PCT] ?: 100 }.first()
    }

    suspend fun setVolumePct(pct: Int) {
        withContext(Dispatchers.IO) {
            store.edit { it[KEY_VOLUME_PCT] = pct.coerceIn(0, 100) }
        }
    }

    companion object {
        private val KEY_VOLUME_PCT = intPreferencesKey("voice_volume_pct")
    }
}
