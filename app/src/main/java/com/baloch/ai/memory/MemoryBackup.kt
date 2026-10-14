package com.baloch.ai.memory

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One-tap encrypted memory backup. The export JSON from [MemoryRepository] is
 * written through [EncryptedFile] (AES-256-GCM), so even the backup file is
 * unreadable without the device's Keystore key.
 *
 * FLOW (documented for the parent wiring the buttons):
 * 1. Export: user taps "Backup" → ActivityResultContracts.CreateDocument
 *    ("baloch-memory-backup.enc") → onExport(uri) → [exportToFile].
 * 2. Import: user taps "Restore" → ActivityResultContracts.OpenDocument →
 *    onImport(uri) → [importFromFile]. The caller should confirm "replace
 *    current memory?" first — import ADDS rows; for a clean restore call
 *    repo.forgetAll() before importFromFile.
 *
 * WHY THE TEMP FILE: EncryptedFile works on java.io.File, not on a content
 * Uri — so we encrypt to a private temp file, then stream the bytes to the
 * user's chosen Uri (and the reverse on import). The temp file is deleted
 * immediately after.
 *
 * REINSTALL LIMITATION (honest): the encryption key lives in the Android
 * Keystore and is WIPED on uninstall. A backup can only be restored on the
 * SAME app install that created it. After a reinstall, export fresh — the old
 * file cannot be decrypted. This is the price of zero-cloud true privacy.
 */

/** One-tap encrypted memory backup, as an object for call-site namespacing. */
object MemoryBackup {

/** Exports the whole memory, encrypted, to [uri]. True on success. */
suspend fun exportToFile(context: Context, uri: Uri): Boolean =
    withContext(Dispatchers.IO) {
        var tmp: File? = null
        try {
            val json = MemoryRepository.get(context).exportJson()
            tmp = File(context.cacheDir, "baloch_memory_backup.enc").also {
                if (it.exists()) it.delete()
            }
            encryptedFile(context, tmp).openFileOutput().use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            }
            context.contentResolver.openOutputStream(uri)?.use { out ->
                tmp.inputStream().use { input -> input.copyTo(out) }
            } ?: return@withContext false
            true
        } catch (_: Exception) {
            false
        } finally {
            tmp?.delete()
        }
    }

/**
 * Imports an encrypted backup from [uri]. Returns the number of rows
 * restored, or -1 on failure (wrong file / wrong key / corrupt).
 */
suspend fun importFromFile(context: Context, uri: Uri): Int =
    withContext(Dispatchers.IO) {
        var tmp: File? = null
        try {
            tmp = File(context.cacheDir, "baloch_memory_restore.enc").also {
                if (it.exists()) it.delete()
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out -> input.copyTo(out) }
            } ?: return@withContext -1
            val bytes = encryptedFile(context, tmp).openFileInput().use { it.readBytes() }
            val count = MemoryRepository.get(context).importJson(bytes.toString(Charsets.UTF_8))
            count
        } catch (_: Exception) {
            -1
        } finally {
            tmp?.delete()
        }
    }

private fun encryptedFile(context: Context, file: File): EncryptedFile {
    val masterKey = MasterKey.Builder(context, "baloch_master_key")
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedFile.Builder(
        file,
        context,
        masterKey,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
    ).build()
}

} // object MemoryBackup
