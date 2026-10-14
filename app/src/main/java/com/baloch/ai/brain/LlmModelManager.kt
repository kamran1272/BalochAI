package com.baloch.ai.brain

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Owns the on-device LLM model file lifecycle.
 *
 * The model is NEVER bundled in the APK (~1.35 GB would blow the install
 * size). It is downloaded once to app-private storage
 * ([Context.getFilesDir]/llm/) and memory-mapped by MediaPipe at inference
 * time. Everything here runs on Dispatchers.IO; callers report progress to
 * the UI.
 *
 * RAM GUARD: Gemma-2B INT4 needs ~2.5–3 GB of working RAM on top of the
 * OS. If the device reports under [MIN_RAM_GB] total RAM, downloads are
 * refused with an honest message — the rule brain stays the default and
 * works perfectly.
 */
class LlmModelManager(private val appContext: Context) {

    // ------------------------------------------------------------------
    // Model identity
    // ------------------------------------------------------------------

    /** File as stored under [modelDir]. */
    fun modelFile(): File = File(modelDir(), MODEL_FILE_NAME)

    /** Absolute path for MediaPipe's setModelPath(), or null when missing. */
    fun modelPath(): String? =
        modelFile().takeIf { isDownloaded() }?.absolutePath

    private fun modelDir(): File =
        File(appContext.filesDir, "llm").also { it.mkdirs() }

    /**
     * True only when a COMPLETE model file is present. A partial download
     * (smaller than [MIN_VALID_BYTES]) counts as missing so a corrupt
     * half-file can never reach the inference engine.
     */
    fun isDownloaded(): Boolean {
        val f = modelFile()
        return f.exists() && f.length() >= MIN_VALID_BYTES
    }

    /** Bytes currently occupied by the model file (0 when absent). */
    fun storageUsedBytes(): Long =
        modelFile().takeIf { it.exists() }?.length() ?: 0L

    /** Deletes the model file. Returns true when nothing remains. */
    fun deleteModel(): Boolean {
        // Also sweep a leftover .part file from an interrupted download.
        partFile().delete()
        modelFile().delete()
        return !modelFile().exists()
    }

    // ------------------------------------------------------------------
    // RAM guard
    // ------------------------------------------------------------------

    /** Total device RAM in GB, via ActivityManager (honest, not estimated). */
    fun deviceRamGb(): Double {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem / 1_000_000_000.0
    }

    /** False on < 4 GB devices — the AI brain is not offered there. */
    fun ramOk(): Boolean = deviceRamGb() >= MIN_RAM_GB

    // ------------------------------------------------------------------
    // Download
    // ------------------------------------------------------------------

    sealed class DownloadResult {
        /** The model file is complete and verified by size. */
        data object Success : DownloadResult()
        /** Device has < 4 GB RAM — refused before any network use. */
        data class BlockedLowRam(val ramGb: Double) : DownloadResult()
        /**
         * The URL needs sign-in (Kaggle/HF gate). Kamran must download the
         * file manually once, then use [importModelFile].
         */
        data object NeedsManualImport : DownloadResult()
        /** Network failure with a human-readable reason. */
        data class NetworkError(val reason: String) : DownloadResult()
        /** Caller cancelled via coroutine cancellation. */
        data object Cancelled : DownloadResult()
    }

    private fun partFile(): File = File(modelDir(), "$MODEL_FILE_NAME.part")

    private fun resolveUrl(): String {
        val custom = try {
            com.baloch.ai.settings.AiBrainPrefs(appContext).customModelUrl
        } catch (_: Exception) { "" }
        return custom.ifBlank { MODEL_URL }
    }

    /**
     * Downloads the model with resume support.
     *
     * - RAM guard runs FIRST: no bytes are fetched on < 4 GB devices.
     * - Resumes into `<name>.part` via HTTP Range when the server allows
     *   it; otherwise restarts cleanly.
     * - [onProgress] receives 0f..1f on the caller's thread context — the
     *   parent should hop to Main before touching UI state.
     * - Coroutine cancellation aborts the transfer and keeps the .part
     *   file so the next attempt resumes.
     * - On success the .part is atomically renamed to the final name and
     *   the size is verified against [MODEL_SIZE_BYTES] (±2% tolerance).
     */
    suspend fun downloadModel(onProgress: (Float) -> Unit): DownloadResult =
        withContext(Dispatchers.IO) {
            val ram = deviceRamGb()
            if (ram < MIN_RAM_GB) return@withContext DownloadResult.BlockedLowRam(ram)

            if (isDownloaded()) {
                onProgress(1f)
                return@withContext DownloadResult.Success
            }

            val part = partFile()
            var downloaded = if (part.exists()) part.length() else 0L
            // A stale .part larger than the real model is garbage — restart.
            if (downloaded > MODEL_SIZE_BYTES) {
                part.delete()
                downloaded = 0L
            }

            val url = resolveUrl()
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "BalochAI/1.0 (Android)")
                    if (downloaded > 0) setRequestProperty("Range", "bytes=$downloaded-")
                    instanceFollowRedirects = true
                }
                conn.connect()

                when (conn.responseCode) {
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    HttpURLConnection.HTTP_FORBIDDEN ->
                        return@withContext DownloadResult.NeedsManualImport
                    HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL -> Unit
                    else -> return@withContext DownloadResult.NetworkError(
                        "server answered ${conn.responseCode}"
                    )
                }

                val contentType = conn.contentType ?: ""
                // Kaggle/HF sign-in gates answer with HTML, not a binary.
                if (contentType.startsWith("text/html")) {
                    return@withContext DownloadResult.NeedsManualImport
                }
                // Server ignored our Range — restart from zero.
                if (conn.responseCode == HttpURLConnection.HTTP_OK && downloaded > 0) {
                    part.delete()
                    downloaded = 0L
                }

                val total = MODEL_SIZE_BYTES
                RandomAccessFile(part, "rw").use { raf ->
                    raf.seek(downloaded)
                    conn.inputStream.use { input ->
                        val buf = ByteArray(256 * 1024)
                        var lastReport = 0L
                        while (true) {
                            ensureActive() // honours coroutine cancellation
                            val n = input.read(buf)
                            if (n < 0) break
                            raf.write(buf, 0, n)
                            downloaded += n
                            if (downloaded - lastReport >= 4 * 1024 * 1024) {
                                lastReport = downloaded
                                onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                onProgress(1f)

                val finalSize = part.length()
                val ok = finalSize in
                    (MODEL_SIZE_BYTES * 0.98).toLong()..(MODEL_SIZE_BYTES * 1.02).toLong()
                if (!ok) {
                    return@withContext DownloadResult.NetworkError(
                        "size mismatch (got ${finalSize / 1_000_000} MB, " +
                            "expected ~${MODEL_SIZE_BYTES / 1_000_000} MB) — deleted, please retry"
                    ).also { part.delete() }
                }
                // Atomic publish: rename, never a half-visible final file.
                if (!part.renameTo(modelFile())) {
                    part.copyTo(modelFile(), overwrite = true)
                    part.delete()
                }
                DownloadResult.Success
            } catch (ce: kotlinx.coroutines.CancellationException) {
                DownloadResult.Cancelled
            } catch (e: Exception) {
                DownloadResult.NetworkError(e.message?.take(120) ?: e.javaClass.simpleName)
            } finally {
                conn?.disconnect()
            }
        }

    // ------------------------------------------------------------------
    // Manual import (Kaggle/HF sign-in gate fallback)
    // ------------------------------------------------------------------

    sealed class ImportResult {
        data object Success : ImportResult()
        data class BlockedLowRam(val ramGb: Double) : ImportResult()
        data class InvalidFile(val reason: String) : ImportResult()
        data object Cancelled : ImportResult()
    }

    /**
     * Copies a user-picked file (Storage Access Framework Uri) into the
     * model slot. The file MUST be the MediaPipe-compatible
     * `gemma-2b-it-cpu-int4.bin`; anything else is refused by size check.
     * Used when the direct URL hits a sign-in gate.
     */
    suspend fun importModelFile(source: Uri, onProgress: (Float) -> Unit): ImportResult =
        withContext(Dispatchers.IO) {
            val ram = deviceRamGb()
            if (ram < MIN_RAM_GB) return@withContext ImportResult.BlockedLowRam(ram)
            try {
                val resolver = appContext.contentResolver
                val size = resolver.query(source, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (c.moveToFirst() && idx >= 0) c.getLong(idx) else -1L
                } ?: -1L
                if (size > 0 && (size < MIN_VALID_BYTES || size > MODEL_SIZE_BYTES * 1.1)) {
                    return@withContext ImportResult.InvalidFile(
                        "that file is ${size / 1_000_000} MB — the AI model is " +
                            "~${MODEL_SIZE_BYTES / 1_000_000} MB"
                    )
                }
                val part = partFile()
                part.delete()
                var copied = 0L
                resolver.openInputStream(source)?.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            copied += n
                            if (size > 0) onProgress((copied.toFloat() / size).coerceIn(0f, 1f))
                        }
                    }
                } ?: return@withContext ImportResult.InvalidFile("could not open that file")
                onProgress(1f)
                if (part.length() < MIN_VALID_BYTES) {
                    part.delete()
                    return@withContext ImportResult.InvalidFile("file is incomplete")
                }
                if (!part.renameTo(modelFile())) {
                    part.copyTo(modelFile(), overwrite = true)
                    part.delete()
                }
                ImportResult.Success
            } catch (ce: kotlinx.coroutines.CancellationException) {
                ImportResult.Cancelled
            } catch (e: Exception) {
                ImportResult.InvalidFile(e.message?.take(120) ?: e.javaClass.simpleName)
            }
        }

    companion object {
        /** Canonical model: Gemma 2B instruct, INT4 CPU, MediaPipe .bin. */
        const val MODEL_FILE_NAME = "gemma-2b-it-cpu-int4.bin"

        /**
         * Kaggle API download for the MediaPipe-compatible Gemma 2B build.
         * NOTE: this endpoint requires Kaggle sign-in — when it answers
         * 401/403 or HTML, [downloadModel] reports NeedsManualImport and
         * Kamran imports the file manually (one-time, ~2 minutes).
         * Override any time via AiBrainPrefs.customModelUrl.
         */
        const val MODEL_URL =
            "https://www.kaggle.com/api/v1/models/google/gemma/tfLite/" +
                "gemma-2b-it-cpu-int4/1/download"

        /** Human page for the manual path (accept terms, download .bin). */
        const val MODEL_PAGE_URL =
            "https://www.kaggle.com/models/google/gemma/tfLite/gemma-2b-it-cpu-int4"

        /** Extracted size of gemma-2b-it-cpu-int4.bin in bytes (~1.35 GB). */
        const val MODEL_SIZE_BYTES = 1_354_301_440L

        /** Below this the file counts as a partial/corrupt download. */
        const val MIN_VALID_BYTES = 1_000_000_000L

        /** Devices under this total RAM never get the download button. */
        const val MIN_RAM_GB = 4.0

        /** "1.35 GB" style label for the settings screen. */
        fun sizeLabel(bytes: Long): String =
            if (bytes <= 0) "—"
            else "%.2f GB".format(bytes / 1_000_000_000.0)
    }
}
