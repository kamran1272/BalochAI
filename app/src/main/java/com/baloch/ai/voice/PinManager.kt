package com.baloch.ai.voice

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * 4-digit PIN — the HARD guarantee behind Voice Lock.
 *
 * POLICY (Kamran's order): voice verification is convenient but NOT
 * spoof-proof against recordings, so the PIN is the deadbolt:
 *  - Voice fails twice in a row, or the mic is unavailable → Baloch asks
 *    for the PIN before executing anything.
 *  - Turning Voice Lock OFF requires voiceprint OR PIN (see
 *    VoiceLockManager.canDisable() for the voice half).
 *  - The PIN is NEVER stored in plain text — only its SHA-256 hash lives
 *    in DataStore, and comparisons are CONSTANT-TIME (see below).
 *
 * Honest note: a 4-digit PIN has 10,000 combinations, so a determined
 * attacker WITH the unlocked phone could brute-force it in theory. In
 * practice it is a local fallback behind the app/device lock — good enough
 * as the second factor it is, not as a bank vault.
 */
class PinManager(private val appContext: Context) {

    /**
     * Saves a new PIN. Rejects anything that isn't exactly 4 digits —
     * silently "fixing" a bad PIN would teach Kamran the wrong one.
     */
    suspend fun setPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidPin(pin)) return@withContext false
        appContext.voiceLockStore.edit { it[KEY_PIN_HASH] = hash(pin) }
        true
    }

    /** True when the entered PIN matches the stored hash. Constant-time. */
    suspend fun checkPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidPin(pin)) return@withContext false
        val stored = appContext.voiceLockStore.data
            .map { it[KEY_PIN_HASH] }
            .first() ?: return@withContext false
        constantTimeEquals(stored, hash(pin))
    }

    /** Blocking read for UI hot paths (tiny DataStore read on IO). */
    fun hasPin(): Boolean = runBlocking(Dispatchers.IO) {
        appContext.voiceLockStore.data
            .map { it[KEY_PIN_HASH] != null }
            .first()
    }

    /** Removes the PIN (only callable after voice/PIN proof — parent enforces). */
    suspend fun clearPin() {
        withContext(Dispatchers.IO) {
            appContext.voiceLockStore.edit { it.remove(KEY_PIN_HASH) }
        }
    }

    // ------------------------------------------------------------------

    private fun isValidPin(pin: String): Boolean =
        pin.length == PIN_LENGTH && pin.all { it.isDigit() }

    /**
     * SHA-256 of a fixed app-local pepper + the PIN. The pepper means a
     * leaked DataStore file can't be matched against rainbow tables of
     * plain 4-digit hashes; it is NOT a per-user salt (overkill here —
     * the threat is a casual snooper, not a lab).
     */
    private fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$PEPPER:$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * CONSTANT-TIME comparison: a normal `==` on strings quits at the first
     * wrong character, so an attacker measuring response times could learn
     * the hash one character at a time ("timing attack"). This loop ALWAYS
     * walks both full strings and only reveals the final verdict — the
     * timing tells the attacker nothing.
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    companion object {
        const val PIN_LENGTH = 4
        private const val PEPPER = "baloch-voice-lock-v1"
        private val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
    }
}
