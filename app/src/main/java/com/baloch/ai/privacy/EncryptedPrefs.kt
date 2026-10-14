package com.baloch.ai.privacy

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * FORTRESS PRIVACY — one home for every app secret.
 *
 * [get] returns an [EncryptedSharedPreferences] instance backed by a
 * Keystore-held MasterKey (AES256_GCM): keys are encrypted with AES256_SIV
 * and values with AES256_GCM, so the preference FILES on disk are unreadable
 * to any other app or to anyone pulling files off the phone.
 *
 * What belongs here: PIN hashes, voiceprint vectors, the Room passphrase,
 * any future token or key. What does NOT belong here: anything big or
 * frequently rewritten (that's what [PrivacyLog]'s EncryptedFile is for).
 *
 * The MasterKey alias "baloch_master_key" is SHARED with
 * [com.baloch.ai.memory.room.BalochDatabase] and [PrivacyLog] on purpose —
 * one Keystore key for the whole vault, so a future key-rotation touches one
 * place.
 *
 * -------------------------------------------------------------------------
 * MIGRATION GUIDE — moving VoiceLockManager / PinManager off plain storage
 * -------------------------------------------------------------------------
 * TODAY (audit, Oct 14 2026): the voiceprint ("voiceprint", "voiceprint_variance")
 * and the PIN hash ("pin_hash") live in the PLAIN DataStore file
 * `voice_lock.preferences_pb` — readable by anyone with file access to the
 * app's private dir (rooted device, backup extraction). The PIN is only a
 * salted SHA-256 (peppered), and the voiceprint is 26 floats, but Kamran's
 * order is "never leak", so both should move under this encrypted store.
 *
 * DO NOT edit those two files blindly — the voice/PIN sibling owns them.
 * When the parent is ready, the migration is:
 *
 *     // 1) Copy the plain values into the encrypted vault (one-time, on upgrade):
 *     suspend fun migrateVoiceLockToEncrypted(context: Context) {
 *         val plain: DataStore<Preferences> = context.voiceLockStore // existing delegate
 *         val vault = EncryptedPrefs.get(context, "voice_lock_secure")
 *         val prefs = plain.data.first()
 *         vault.edit().apply {
 *             prefs[stringPreferencesKey("voiceprint")]?.let { putString("voiceprint", it) }
 *             prefs[stringPreferencesKey("voiceprint_variance")]?.let { putString("voiceprint_variance", it) }
 *             prefs[stringPreferencesKey("pin_hash")]?.let { putString("pin_hash", it) }
 *             prefs[booleanPreferencesKey("enrolled")]?.let { putBoolean("enrolled", it) }
 *             prefs[booleanPreferencesKey("enabled")]?.let { putBoolean("enabled", it) }
 *         }.apply()
 *         // 2) Wipe the plain file so the old values can't be recovered:
 *         EncryptedPrefs.deletePlainDataStore(context, "voice_lock")
 *         // 3) From then on, VoiceLockManager/PinManager read/write ONLY
 *         //    through EncryptedPrefs.get(context, "voice_lock_secure").
 *     }
 *
 *     // DataStore file convention: <filesDir>/datastore/<name>.preferences_pb
 *     EncryptedPrefs.deletePlainDataStore(context, "voice_lock")
 *
 * EDGE CASES (don't skip): if the Keystore is broken on an exotic device,
 * EncryptedSharedPreferences throws — catch it at the call site and fall back
 * to the plain store WITH a user-visible "secrets unencrypted" note, the same
 * honest-flag pattern BalochDatabase uses for SQLCipher (isEncrypted). Never
 * fail silently and never crash the lock screen.
 */
object EncryptedPrefs {

    const val MASTER_KEY_ALIAS = "baloch_master_key"

    @Volatile
    private var masterKey: MasterKey? = null

    private fun masterKey(context: Context): MasterKey =
        masterKey ?: synchronized(this) {
            masterKey ?: MasterKey.Builder(context.applicationContext, MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
                .also { masterKey = it }
        }

    /**
     * Returns the encrypted preferences file [name]. Creates it on first
     * access. All reads/writes behave exactly like SharedPreferences.
     */
    fun get(context: Context, name: String): SharedPreferences =
        EncryptedSharedPreferences.create(
            context.applicationContext,
            name,
            masterKey(context),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    /** Convenience: read an encrypted String, or [default] when absent. */
    fun getString(context: Context, store: String, key: String, default: String? = null): String? =
        get(context, store).getString(key, default)

    /** Convenience: write an encrypted String. */
    fun putString(context: Context, store: String, key: String, value: String) {
        get(context, store).edit().putString(key, value).apply()
    }

    /** Convenience: read an encrypted Boolean. */
    fun getBoolean(context: Context, store: String, key: String, default: Boolean = false): Boolean =
        get(context, store).getBoolean(key, default)

    /** Convenience: write an encrypted Boolean. */
    fun putBoolean(context: Context, store: String, key: String, value: Boolean) {
        get(context, store).edit().putBoolean(key, value).apply()
    }

    /** Convenience: remove one encrypted key. */
    fun remove(context: Context, store: String, key: String) {
        get(context, store).edit().remove(key).apply()
    }

    /**
     * Deletes a PLAIN DataStore preferences file after its values have been
     * migrated into the encrypted vault (see migration guide in the KDoc).
     * DataStore keeps its files at `<filesDir>/datastore/<name>.preferences_pb`.
     */
    fun deletePlainDataStore(context: Context, name: String): Boolean {
        val file = File(context.applicationContext.filesDir, "datastore/$name.preferences_pb")
        return !file.exists() || file.delete()
    }
}
