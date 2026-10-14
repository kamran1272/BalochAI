package com.baloch.ai.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Offline "hey baloch" keyword spotter built on Vosk (org.vosk:vosk-android).
 *
 * Runs a continuous 16 kHz mic loop on a dedicated thread and calls
 * [onWakeWord] whenever the recognizer's partial/final result contains the
 * wake phrase. Everything is on-device — no audio ever leaves the phone.
 *
 * FIRST RUN: downloads the small English Vosk model (~50 MB) from
 * alphacephei.com into filesDir/vosk-model, with progress reported through
 * [onStatus]. If the download (or unzip) fails, the engine reports itself
 * disabled with an honest reason instead of silently doing nothing.
 *
 * SENSITIVITY: a simple energy gate keeps near-silent frames out of the
 * recognizer (saves CPU). [quietMode] lowers the gate so whispers pass —
 * default ON per Kamran's whisper order. The sibling whisper builder owns
 * the settings toggle; this engine just reads the SharedPreferences value
 * ("quiet_voice_mode", default true) at start() and exposes the var.
 *
 * LIFECYCLE: start() → suspendListening()/resumeListening() around each
 * command interaction (so the chime + Baloch's own TTS can't re-trigger) →
 * stop() for a clean shutdown.
 */
class WakeWordEngine(
    context: Context,
    private val onWakeWord: () -> Unit,
    private val onStatus: (String) -> Unit = {},
) {
    private val appContext: Context = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Lowered when whispers are expected; read from prefs at start(). */
    @Volatile
    var quietMode: Boolean = true

    @Volatile
    private var running = false

    @Volatile
    private var paused = false

    /** True once the mic loop is feeding the recognizer. */
    @Volatile
    var isReady: Boolean = false
        private set

    /** Set when the engine gave up (e.g. model download failed). */
    @Volatile
    var disabledReason: String? = null
        private set

    private var worker: Thread? = null
    private var cooldownUntilMs = 0L

    fun start() {
        if (running) return
        quietMode = prefs.getBoolean(KEY_QUIET_MODE, true)
        disabledReason = null
        running = true
        paused = false
        worker = thread(name = "wake-word-engine", isDaemon = true) { runLoop() }
    }

    /** Pause feeding (wake words ignored) while a command is handled. */
    fun suspendListening() {
        paused = true
    }

    /** Resume feeding after the command interaction finishes. */
    fun resumeListening() {
        // Cool down so the tail of Baloch's own reply can't instantly re-wake.
        cooldownUntilMs = System.currentTimeMillis() + WAKE_COOLDOWN_MS
        paused = false
    }

    /** Full shutdown: stops the loop, releases mic + Vosk resources. */
    fun stop() {
        running = false
        try {
            worker?.join(WORKER_JOIN_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        worker = null
        isReady = false
    }

    private fun runLoop() {
        var recorder: AudioRecord? = null
        var model: Model? = null
        var recognizer: Recognizer? = null
        try {
            val modelDir = ensureModel()
            if (modelDir == null) {
                // ensureModel already reported the honest reason via onStatus.
                disabledReason = "voice model unavailable"
                return
            }
            // Vosk JNI calls: wrapped because a missing/corrupt model or a
            // native-load failure must disable the engine, never crash the
            // service. (Constructor shapes per vosk-android 0.3.47 docs:
            // Model(path), Recognizer(model, sampleRate, grammarJson).)
            model = try {
                Model(modelDir.absolutePath)
            } catch (e: Exception) {
                fail("voice model failed to load: ${e.message}")
                return
            } catch (e: UnsatisfiedLinkError) {
                fail("vosk native library missing: ${e.message}")
                return
            }
            recognizer = try {
                Recognizer(model, SAMPLE_RATE.toFloat(), GRAMMAR_JSON)
            } catch (e: Exception) {
                fail("recognizer init failed: ${e.message}")
                return
            }
            recorder = openRecorder()
            if (recorder == null) {
                fail("microphone unavailable (permission denied?)")
                return
            }
            recorder.startRecording()
            isReady = true
            onStatus(STATUS_READY)

            val shorts = ShortArray(READ_SAMPLES)
            val bytes = ByteArray(READ_SAMPLES * 2)
            var latch = 0
            while (running) {
                if (paused) {
                    try {
                        Thread.sleep(PAUSE_POLL_MS)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                    continue
                }
                val n = try {
                    recorder.read(shorts, 0, shorts.size)
                } catch (_: Exception) {
                    break
                }
                if (n <= 0) continue

                // Energy gate: only frames that sound like speech (or the
                // trailing latch after one) reach the recognizer. The latch
                // keeps ~0.5 s of audio after the last loud frame so word
                // endings aren't clipped when the gate flutters.
                val gate = if (quietMode) ENERGY_GATE_QUIET else ENERGY_GATE_NORMAL
                if (rms(shorts, n) >= gate) latch = LATCH_FRAMES
                if (latch <= 0) continue
                latch--

                shortsToLittleEndian(shorts, n, bytes)
                val isFinal = try {
                    recognizer.acceptWaveForm(bytes, n * 2)
                } catch (_: Exception) {
                    false
                }
                val json = try {
                    if (isFinal) recognizer.finalResult else recognizer.partialResult
                } catch (_: Exception) {
                    continue
                }
                val heard = try {
                    val key = if (isFinal) "text" else "partial"
                    JSONObject(json).optString(key, "")
                } catch (_: Exception) {
                    ""
                }
                if (heard.contains(WAKE_PHRASE, ignoreCase = true) &&
                    System.currentTimeMillis() >= cooldownUntilMs
                ) {
                    // Clear recognizer state so the same phrase can't
                    // immediately re-fire. reset() exists on recent Vosk
                    // builds; if it doesn't, the pause/cooldown below still
                    // protects us — hence try/catch, not a hard dependency.
                    try {
                        recognizer.reset()
                    } catch (_: Exception) {
                        /* older Vosk build without reset(); cooldown covers it */
                    }
                    cooldownUntilMs = System.currentTimeMillis() + WAKE_COOLDOWN_MS
                    try {
                        onWakeWord()
                    } catch (e: Exception) {
                        Log.w(TAG, "onWakeWord callback failed", e)
                    }
                }
            }
        } finally {
            isReady = false
            try {
                recorder?.stop()
            } catch (_: Exception) {
                /* already stopped */
            }
            try {
                recorder?.release()
            } catch (_: Exception) {
                /* ignore */
            }
            try {
                recognizer?.close()
            } catch (_: Exception) {
                /* ignore */
            }
            try {
                model?.close()
            } catch (_: Exception) {
                /* ignore */
            }
        }
    }

    private fun fail(reason: String) {
        disabledReason = reason
        Log.w(TAG, reason)
        onStatus("disabled: $reason")
    }

    // ------------------------------------------------------------------
    // Model provisioning (first run)
    // ------------------------------------------------------------------

    /**
     * Returns the unzipped model dir, downloading + unzipping it on first
     * run. Returns null (after an honest onStatus) when provisioning fails.
     */
    private fun ensureModel(): File? {
        val dir = File(appContext.filesDir, MODEL_DIR_NAME)
        if (File(dir, MODEL_MARKER).exists()) return dir
        onStatus("downloading voice model (one-time, ~50 MB)…")
        if (!downloadModel(dir)) {
            onStatus("disabled: voice-model download failed — check connection, then restart listening")
            return null
        }
        return dir
    }

    private fun downloadModel(dir: File): Boolean {
        // ~50 MB model + zip + headroom.
        if (appContext.filesDir.usableSpace < MIN_FREE_BYTES) {
            Log.w(TAG, "not enough storage for the voice model")
            return false
        }
        if (dir.exists()) dir.deleteRecursively()
        val tmpZip = File(appContext.filesDir, "vosk-model.zip.tmp")
        tmpZip.delete()
        var downloaded = false
        for (url in MODEL_URLS) {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                }
                conn.connect()
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    conn.disconnect()
                    continue
                }
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    FileOutputStream(tmpZip).use { out ->
                        val buf = ByteArray(DOWNLOAD_BUF)
                        var done = 0L
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            if (!running) {
                                conn.disconnect()
                                tmpZip.delete()
                                return false // service stopped mid-download
                            }
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                onStatus("downloading voice model… ${(done * 100 / total).toInt()}%")
                            }
                        }
                    }
                }
                conn.disconnect()
                downloaded = true
                break
            } catch (e: Exception) {
                Log.w(TAG, "model download failed, trying next URL: $url", e)
            }
        }
        if (!downloaded) {
            tmpZip.delete()
            return false
        }
        return try {
            unzipStripTopLevel(tmpZip, dir)
            tmpZip.delete()
            File(dir, MODEL_MARKER).exists()
        } catch (e: Exception) {
            Log.w(TAG, "model unzip failed", e)
            dir.deleteRecursively()
            tmpZip.delete()
            false
        }
    }

    /**
     * The zip contains one top-level folder (vosk-model-small-en-us-0.15/);
     * strip it so files land directly in [dest]. Zip-slip guarded.
     */
    private fun unzipStripTopLevel(zip: File, dest: File) {
        val destCanonical = dest.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val rel = entry.name.substringAfter('/', "")
                if (rel.isNotEmpty()) {
                    val out = File(dest, rel)
                    if (!out.canonicalPath.startsWith(destCanonical)) {
                        throw SecurityException("zip-slip entry: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zin.copyTo(it) }
                    }
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
    }

    // ------------------------------------------------------------------
    // Audio helpers
    // ------------------------------------------------------------------

    private fun openRecorder(): AudioRecord? {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return null
        return try {
            val rec = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 4,
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                null
            } else {
                rec
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "RECORD_AUDIO not granted", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "bad audio config", e)
            null
        }
    }

    private fun rms(samples: ShortArray, n: Int): Double {
        var sum = 0.0
        for (i in 0 until n) {
            val s = samples[i].toDouble()
            sum += s * s
        }
        return sqrt(sum / n)
    }

    private fun shortsToLittleEndian(src: ShortArray, n: Int, dst: ByteArray) {
        for (i in 0 until n) {
            val s = src[i].toInt()
            dst[i * 2] = (s and 0xFF).toByte()
            dst[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
    }

    companion object {
        private const val TAG = "WakeWordEngine"

        private const val PREFS_NAME = "baloch_prefs"
        private const val KEY_QUIET_MODE = "quiet_voice_mode"

        private const val SAMPLE_RATE = 16000

        /** 1024 samples = 64 ms per frame — fine granularity for the latch. */
        private const val READ_SAMPLES = 1024

        private const val WAKE_PHRASE = "hey baloch"

        /**
         * Grammar-constrained recognizer: only the wake phrase (plus [unk]
         * for everything else) is decoded. This is what makes a 50 MB
         * model reliable at keyword spotting — it never has to consider
         * the rest of English.
         */
        private const val GRAMMAR_JSON = "[\"hey baloch\", \"[unk]\"]"

        private const val MODEL_DIR_NAME = "vosk-model"

        /** Marker file proving a complete model (am/final.mdl is the acoustic model). */
        private const val MODEL_MARKER = "am/final.mdl"

        private val MODEL_URLS = listOf(
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.22.zip",
        )

        private const val MIN_FREE_BYTES = 160L * 1024 * 1024

        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val DOWNLOAD_BUF = 32 * 1024

        /**
         * Normal-mode energy gate (RMS of 16-bit PCM). Frames below this
         * are treated as silence and never reach the recognizer — saves
         * CPU in a quiet room. Heuristic: room tone ≈ 40–120, speech
         * ≈ 800–6000. Conservative (low) so detection is never sacrificed
         * for battery.
         */
        private const val ENERGY_GATE_NORMAL = 320.0

        /**
         * Quiet-mode gate: whispers live around 150–800 RMS, so the gate
         * drops near the noise floor. Slightly more CPU (more frames
         * decoded), but Kamran's whisper order wins over battery here.
         */
        private const val ENERGY_GATE_QUIET = 110.0

        /** After a loud frame, keep feeding this many frames (~0.5 s). */
        private const val LATCH_FRAMES = 8

        /** Ignore repeat wake matches inside this window. */
        private const val WAKE_COOLDOWN_MS = 3_000L

        private const val PAUSE_POLL_MS = 100L
        private const val WORKER_JOIN_MS = 3_000L

        const val STATUS_READY = "listening"
    }
}
