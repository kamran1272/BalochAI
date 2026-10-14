package com.baloch.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.baloch.ai.brain.BalochLanguage
import kotlin.concurrent.thread

/**
 * Speech-to-text wrapper. Listens in the active language (ur-PK / hi-IN /
 * en-US); if the recogniser rejects the locale it falls back to en-US
 * once and reports the fallback so the UI can say so.
 */
class VoiceInput(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var triedFallback = false
    private var language = BalochLanguage.ENGLISH

    /**
     * OFFLINE-FIRST: when true (default), a captured utterance is decoded
     * on-device with the Vosk model whenever it is present; the platform
     * SpeechRecognizer becomes the online enhancement, not the default.
     * Flip to false to force the platform recognizer (e.g. for debugging).
     */
    var preferOffline: Boolean = true

    private var offlineInput: OfflineSpeechInput? = null
    private var offlineThread: Thread? = null

    private var onPartial: (String) -> Unit = {}
    private var onFinal: (String) -> Unit = {}
    private var onError: (String) -> Unit = {}
    private var onFallback: () -> Unit = {}

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(
        lang: BalochLanguage,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
        onFallback: () -> Unit = {},
    ) {
        stopListening()
        this.language = lang
        this.triedFallback = false
        this.onPartial = onPartial
        this.onFinal = onFinal
        this.onError = onError
        this.onFallback = onFallback
        if (preferOffline) {
            val offline = OfflineSpeechInput(context)
            if (offline.isReady()) {
                // Vosk model present → the fully-offline command path.
                runOffline(offline)
                return
            }
            // Model not provisioned yet → fall through to the platform
            // recognizer (still with EXTRA_PREFER_OFFLINE set below).
        }
        if (!isAvailable()) {
            onError("unavailable")
            return
        }
        listenWith(lang)
    }

    private fun listenWith(lang: BalochLanguage) {
        destroyRecognizer()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.speechLocaleTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (preferOffline) {
                // Honest hint to the platform: prefer its on-device model.
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
        listening = true
        recognizer?.startListening(intent)
    }

    /**
     * Fully-offline command path: capture raw mic audio, decode it with
     * Vosk on-device, deliver [onFinal]/[onError] on the main looper —
     * the same thread contract as the SpeechRecognizer callbacks above.
     * Runs on a background thread because captureUtterance blocks for the
     * capture window.
     */
    private fun runOffline(offline: OfflineSpeechInput) {
        stopListening()
        offlineInput = offline
        listening = true
        val main = Handler(Looper.getMainLooper())
        offlineThread = thread(name = "voiceinput-offline", isDaemon = true) {
            val samples = try {
                captureUtterance(OFFLINE_CAPTURE_MS)
            } catch (_: Exception) {
                null
            }
            val text = if (samples != null) offline.recognize(samples) else null
            main.post {
                if (!listening) return@post // cancelled mid-capture; swallow
                listening = false
                offlineThread = null
                if (text.isNullOrBlank()) onError("empty") else onFinal(text)
            }
        }
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(results: Bundle?) {
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.let { onPartial(it) }
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (text.isNullOrBlank()) onError("empty") else onFinal(text)
        }

        override fun onError(error: Int) {
            listening = false
            // Locale not supported → one graceful retry in English.
            if (!triedFallback && language != BalochLanguage.ENGLISH &&
                (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                 error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
            ) {
                triedFallback = true
                onFallback()
                listenWith(BalochLanguage.ENGLISH)
                return
            }
            onError("error:$error")
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun stopListening() {
        if (listening) {
            listening = false
            try { recognizer?.stopListening() } catch (_: Exception) { /* already stopped */ }
            // Interrupts a pending offline capture; its delivery runnable
            // re-checks `listening`, so no stale result can leak through.
            offlineThread?.interrupt()
        }
        offlineThread = null
    }

    /**
     * Records raw microphone audio for [timeoutMs] and returns the 16 kHz
     * mono 16-bit PCM samples — used by VoiceLockManager for enrollment and
     * per-command speaker verification.
     *
     * BLOCKING: sleeps for the full timeout, so call it from a background
     * thread / coroutine (Dispatchers.IO), never the main thread.
     * Returns null when the mic can't be opened (permission denied etc.).
     * Existing speech-recognition API above is untouched.
     */
    fun captureUtterance(timeoutMs: Long = 3000): ShortArray? {
        val capture = UtteranceCapture()
        if (!capture.start()) return null
        try {
            Thread.sleep(timeoutMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return capture.stop()
    }

    fun destroy() {
        stopListening()
        destroyRecognizer()
        try { offlineInput?.close() } catch (_: Exception) { /* ignore */ }
        offlineInput = null
    }

    private fun destroyRecognizer() {
        try { recognizer?.destroy() } catch (_: Exception) { /* ignore */ }
        recognizer = null
    }

    companion object {
        /** Mic window for the offline command capture (see captureUtterance). */
        private const val OFFLINE_CAPTURE_MS = 5000L
    }
}
