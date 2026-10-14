package com.baloch.ai.voice

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/**
 * Fully-offline speech-to-text for the command path, built on Vosk
 * (org.vosk:vosk-android).
 *
 * This is the PRIMARY command path: [VoiceInput] tries this first whenever
 * the Vosk model is present ([VoiceInput.preferOffline]), and the platform
 * SpeechRecognizer stays only as the online enhancement.
 *
 * MODEL: shares the exact model files the wake-word engine provisions —
 * filesDir/vosk-model with marker am/final.mdl (see WakeWordEngine). No
 * second download, no second copy: if "hey baloch" listening is set up,
 * offline command recognition is already set up.
 *
 * [recognize] takes a captured 16 kHz mono 16-bit PCM utterance (see
 * VoiceInput.captureUtterance) and returns the transcript, or null when
 * the model is unavailable or decoding fails — the caller then falls back
 * to the online recognizer. Never throws.
 *
 * THREADING: blocking JNI decode — call from a background thread
 * (Dispatchers.IO). The Vosk recognizer is NOT thread-safe; all access is
 * serialized on [lock].
 */
class OfflineSpeechInput(context: Context) {

    private val appContext: Context = context.applicationContext
    private val lock = Any()

    @Volatile
    private var model: Model? = null

    @Volatile
    private var recognizer: Recognizer? = null

    /**
     * Fast readiness check: true when the wake-word engine's model files
     * exist on disk. Loads nothing — safe on the hot path.
     */
    fun isReady(): Boolean =
        File(File(appContext.filesDir, MODEL_DIR_NAME), MODEL_MARKER).exists()

    /**
     * Decodes [utterance] (16 kHz mono 16-bit PCM) fully on-device.
     *
     * BLOCKING: does JNI decoding — call from a background thread, never
     * the main thread. Returns null when the model is unavailable or
     * decoding failed (caller falls back to the online recognizer).
     */
    fun recognize(utterance: ShortArray): String? {
        if (utterance.isEmpty()) return null
        val rec = ensureRecognizer() ?: return null
        return try {
            synchronized(lock) {
                // Fresh state per utterance. Older Vosk builds lack
                // reset() — then the grammar-free recognizer still decodes,
                // just without the state clear (try/catch, not a hard dep).
                try {
                    rec.reset()
                } catch (_: Exception) {
                    /* older Vosk build without reset(); decode still works */
                }
                val bytes = ByteArray(FEED_CHUNK * 2)
                var offset = 0
                while (offset < utterance.size) {
                    val n = minOf(FEED_CHUNK, utterance.size - offset)
                    for (i in 0 until n) {
                        val s = utterance[offset + i].toInt()
                        bytes[i * 2] = (s and 0xFF).toByte()
                        bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                    }
                    try {
                        rec.acceptWaveForm(bytes, n * 2)
                    } catch (e: Exception) {
                        Log.w(TAG, "acceptWaveForm failed", e)
                        return null
                    }
                    offset += n
                }
                val json = try {
                    rec.finalResult
                } catch (e: Exception) {
                    Log.w(TAG, "finalResult failed", e)
                    return null
                }
                JSONObject(json).optString("text", "").trim().ifEmpty { null }
            }
        } catch (_: Exception) {
            null // decode failure → caller falls back to the online recognizer
        }
    }

    /**
     * Releases the Vosk model + recognizer. Safe to call more than once.
     */
    fun close() {
        synchronized(lock) {
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
            recognizer = null
            model = null
        }
    }

    /** Lazily loads the shared Vosk model once; null when unavailable. */
    private fun ensureRecognizer(): Recognizer? {
        recognizer?.let { return it }
        synchronized(lock) {
            recognizer?.let { return it }
            val dir = File(appContext.filesDir, MODEL_DIR_NAME)
            if (!File(dir, MODEL_MARKER).exists()) return null
            val m = try {
                Model(dir.absolutePath)
            } catch (e: Exception) {
                Log.w(TAG, "vosk model failed to load", e)
                return null
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "vosk native library missing", e)
                return null
            }
            val r = try {
                // Grammar-free constructor: full-language decode (NOT the
                // keyword-spotter grammar the wake-word engine uses).
                Recognizer(m, SAMPLE_RATE.toFloat())
            } catch (e: Exception) {
                Log.w(TAG, "vosk recognizer init failed", e)
                try {
                    m.close()
                } catch (_: Exception) {
                    /* ignore */
                }
                return null
            }
            model = m
            recognizer = r
            return r
        }
    }

    companion object {
        private const val TAG = "OfflineSpeechInput"

        /**
         * MUST match WakeWordEngine's MODEL_DIR_NAME / MODEL_MARKER — the
         * model is downloaded once by the wake-word engine and shared.
         */
        private const val MODEL_DIR_NAME = "vosk-model"
        private const val MODEL_MARKER = "am/final.mdl"

        private const val SAMPLE_RATE = 16000

        /** Samples fed per acceptWaveForm call (400 ms of audio). */
        private const val FEED_CHUNK = 6400
    }
}
