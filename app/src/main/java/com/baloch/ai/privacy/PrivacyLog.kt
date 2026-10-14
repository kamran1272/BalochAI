package com.baloch.ai.privacy

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * FORTRESS PRIVACY — the append-only transparency log.
 *
 * Every time Baloch touches Kamran's PRIVATE data (a photo, a contact, a
 * file, the voiceprint, a memory export), the call site logs ONE line here:
 *
 *     PrivacyLog.get(context).log(PrivacyKind.PHOTO, "viewed photo for wallpaper command")
 *
 * The log lives in app-internal storage ONLY (filesDir — never external,
 * never the SD card) and is encrypted at rest with AES-256-GCM through
 * AndroidX Security's [EncryptedFile], keyed by a MasterKey held in the
 * Android Keystore. No other app can read it, and it survives app updates.
 *
 * CONTRACT FOR CALL SITES (the parent wires these in):
 *  - `detail` must be a DESCRIPTION of the access ("read contact list to
 *    find a number"), NEVER raw personal data — no names, no phone numbers,
 *    no message bodies, no file contents. The log says WHAT was touched,
 *    never the private content itself.
 *  - Call [log] ONLY at real private-data touch points (media pick, contact
 *    read, file read, voiceprint verify/enroll, memory export). Routine
 *    commands (weather, calculator) are NOT private-data touches.
 *
 * Append-only by design: [log] only adds; the ONLY removal path is [clear],
 * which the user triggers deliberately from the Privacy screen (with a
 * confirm dialog).
 */
enum class PrivacyKind {
    PHOTO, CONTACT, FILE, VOICEPRINT, EXPORT, OTHER
}

/**
 * One transparency entry: when (epoch ms), what kind of data, and a
 * human-readable description of the access.
 */
data class PrivacyEvent(
    val timestamp: Long,
    val kind: PrivacyKind,
    val detail: String,
)

class PrivacyLog private constructor(private val appContext: Context) {

    private val lock = Any()
    private val _events = MutableStateFlow<List<PrivacyEvent>>(emptyList())

    init {
        // Small bounded file — load synchronously on IO, matching the
        // codebase's existing runBlocking(Dispatchers.IO) pattern.
        runBlocking(Dispatchers.IO) { _events.value = readAll() }
    }

    /**
     * Append one transparency event. Safe to call from any thread/coroutine.
     * The store is capped at [MAX_EVENTS] newest entries — oldest first out.
     */
    suspend fun log(kind: PrivacyKind, detail: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val clean = sanitize(detail)
            val updated = (readAll() + PrivacyEvent(System.currentTimeMillis(), kind, clean))
                .takeLast(MAX_EVENTS)
            writeAll(updated)
            _events.value = updated
        }
    }

    /** Live view of the log for the Privacy screen. */
    fun events(): Flow<List<PrivacyEvent>> = _events.asStateFlow()

    /**
     * Wipe the log. User-initiated only (Privacy screen → confirm dialog).
     * Non-suspend to keep the wiring simple; IO runs on Dispatchers.IO.
     */
    fun clear() {
        runBlocking(Dispatchers.IO) {
            synchronized(lock) {
                logFile().delete()
                tempFile().delete()
                _events.value = emptyList()
            }
        }
    }

    // ------------------------------------------------------------------
    // Encrypted storage
    // ------------------------------------------------------------------

    private fun masterKey(): MasterKey =
        MasterKey.Builder(appContext, MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private fun encryptedFile(file: File): EncryptedFile =
        EncryptedFile.Builder(
            file,
            appContext,
            masterKey(),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()

    private fun logFile(): File = File(appContext.filesDir, LOG_NAME)
    private fun tempFile(): File = File(appContext.filesDir, "$LOG_NAME.tmp")

    private fun readAll(): List<PrivacyEvent> {
        val file = logFile()
        if (!file.exists()) return emptyList()
        return try {
            encryptedFile(file).openFileInput().use { ins ->
                val text = ins.readBytes().toString(Charsets.UTF_8)
                if (text.isBlank()) return emptyList()
                val arr = JSONArray(text)
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    PrivacyEvent(
                        timestamp = o.getLong("ts"),
                        kind = runCatching { PrivacyKind.valueOf(o.getString("kind")) }
                            .getOrDefault(PrivacyKind.OTHER),
                        detail = o.optString("detail", ""),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList() // corrupted log → start fresh, never crash the app
        }
    }

    /**
     * Atomic write: build the new encrypted content in a temp file IN THE
     * SAME directory, then rename over the live file. A crash mid-write
     * leaves the old log intact — never a half-written log.
     */
    private fun writeAll(events: List<PrivacyEvent>) {
        val arr = JSONArray()
        events.forEach { e ->
            arr.put(
                JSONObject()
                    .put("ts", e.timestamp)
                    .put("kind", e.kind.name)
                    .put("detail", e.detail),
            )
        }
        val bytes = arr.toString().toByteArray(Charsets.UTF_8)
        val tmp = tempFile()
        encryptedFile(tmp).openFileOutput().use { it.write(bytes) }
        if (!tmp.renameTo(logFile())) {
            // Rename failed (shouldn't happen same-dir) — fall back to
            // direct overwrite rather than losing the log.
            encryptedFile(logFile()).openFileOutput().use { it.write(bytes) }
            tmp.delete()
        }
    }

    /**
     * Keep details safe for the log: single line, bounded length.
     * Call sites must STILL pass descriptions, not raw PII — this is the
     * belt to their suspenders.
     */
    private fun sanitize(detail: String): String =
        detail.replace(Regex("\\s+"), " ").trim().take(MAX_DETAIL_LEN)

    companion object {
        private const val MASTER_KEY_ALIAS = "baloch_master_key" // shared with BalochDatabase
        private const val LOG_NAME = "privacy_log.enc" // filesDir only — never external storage
        private const val MAX_EVENTS = 500
        private const val MAX_DETAIL_LEN = 160

        @Volatile
        private var INSTANCE: PrivacyLog? = null

        fun get(context: Context): PrivacyLog =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrivacyLog(context.applicationContext).also { INSTANCE = it }
            }
    }
}
