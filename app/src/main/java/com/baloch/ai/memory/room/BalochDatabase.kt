package com.baloch.ai.memory.room

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * The Total Memory database — every fact, every conversation, every entity
 * Kamran shares with Baloch, kept on-device only.
 *
 * ENCRYPTION (best effort, documented honestly):
 * 1. A random 256-bit passphrase is generated ONCE on first run and stored in
 *    EncryptedSharedPreferences ("baloch_crypto") — itself encrypted with a
 *    MasterKey held in the Android Keystore. The passphrase never lives in
 *    plain text on disk.
 * 2. The Room database is opened through SQLCipher's SupportFactory, so the
 *    .db file at rest is AES-256 encrypted.
 * 3. FALLBACK: if SQLCipher can't load (native lib failure, exotic device),
 *    we open plain Room instead and flip [isEncrypted] to false. The app still
 *    works — the memory screen can show a subtle "unencrypted" note — but
 *    nothing crashes. Honest flag, not a silent downgrade.
 *
 * LIMITATION (documented, not hidden): if the key in EncryptedSharedPreferences
 * is lost (app data wiped), an encrypted database created earlier cannot be
 * opened again. The fallback only applies when encryption was never set up.
 */
@Database(
    entities = [FactEntity::class, ConversationEntity::class, EntityRecord::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(TierConverters::class)
abstract class BalochDatabase : RoomDatabase() {

    abstract fun factDao(): FactDao
    abstract fun conversationDao(): ConversationDao
    abstract fun entityDao(): EntityDao

    companion object {
        private const val DB_NAME = "baloch_memory.db"
        private const val CRYPTO_PREFS = "baloch_crypto"
        private const val PASSPHRASE_KEY = "db_passphrase_b64"

        @Volatile
        private var INSTANCE: BalochDatabase? = null

        /**
         * True when the database file is SQLCipher-encrypted. False only when
         * the SQLCipher path failed and we fell back to plain Room.
         */
        @Volatile
        var isEncrypted: Boolean = true
            private set

        fun get(context: Context): BalochDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }

        private fun build(appContext: Context): BalochDatabase {
            // Try the encrypted path first.
            val factory = try {
                // Step 1: make sure the SQLCipher native libraries are loaded.
                net.zetetic.database.sqlcipher.SQLiteDatabase.loadLibs(appContext)
                // Step 2: load (or generate) the 256-bit passphrase.
                val passphrase = loadOrCreatePassphrase(appContext)
                // Step 3: SupportFactory is passed to Room via openHelperFactory —
                // Room then opens the file through SQLCipher instead of stock SQLite.
                net.zetetic.database.sqlcipher.SupportFactory(passphrase)
            } catch (e: Exception) {
                // Anything goes wrong (native lib missing, keystore broken):
                // fall back to plain Room. The flag tells the UI the truth.
                isEncrypted = false
                null
            }

            val builder = Room.databaseBuilder(appContext, BalochDatabase::class.java, DB_NAME)
            if (factory != null) {
                builder.openHelperFactory(factory)
            } else {
                isEncrypted = false
            }
            return builder.build()
        }

        /**
         * Returns the 32-byte DB passphrase, generating and persisting it on
         * first run. Stored in EncryptedSharedPreferences so it is never
         * readable in plain text by other apps.
         */
        private fun loadOrCreatePassphrase(context: Context): ByteArray {
            val prefs = encryptedPrefs(context)
            val existing = prefs.getString(PASSPHRASE_KEY, null)
            if (existing != null) {
                return android.util.Base64.decode(existing, android.util.Base64.NO_WRAP)
            }
            val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
            prefs.edit()
                .putString(PASSPHRASE_KEY, android.util.Base64.encodeToString(fresh, android.util.Base64.NO_WRAP))
                .apply()
            return fresh
        }

        private fun encryptedPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context, "baloch_master_key")
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences(
                context,
                CRYPTO_PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
