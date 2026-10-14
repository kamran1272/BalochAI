package com.baloch.ai.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.baloch.ai.MainActivity
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import com.baloch.ai.brain.Replies
import com.baloch.ai.notify.BalochNotifications
import com.baloch.ai.voice.UtteranceCapture
import com.baloch.ai.voice.VoiceInput
import com.baloch.ai.voice.VoiceOutput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * ALWAYS-ON VOICE — Kamran's order: Baloch listens and works even with the
 * phone LOCKED / screen off, fully hands-free.
 *
 * HOW IT RUNS:
 * 1. Foreground service (type "microphone", API 34 rule) with a persistent
 *    low-importance notification: "◈ Baloch is listening — say 'hey baloch'".
 *    A foreground service + PARTIAL_WAKE_LOCK keeps the mic loop alive with
 *    the screen off. This is NOT the DSP hotword path Google Assistant uses
 *    (see ALWAYSON-DOCS-ADD.md for the honest battery note).
 * 2. WakeWordEngine spots "hey baloch" fully offline via Vosk.
 * 3. On wake: chime → capture the command utterance as RAW 16 kHz PCM
 *    (for speaker verification) while VoiceInput's SpeechRecognizer
 *    transcribes it in the active language (ur-PK / hi-IN / en-US).
 * 4. RMS energy gate: silence/whisper-quiet captures get an honest
 *    "I didn't hear anything" instead of a hallucinated command.
 * 5. Voice lock: if HandsFreeWiring.pipeline reports the lock enabled, the
 *    raw audio is verified — a failed check speaks a refusal and executes
 *    NOTHING.
 * 6. The pipeline executes; the reply is spoken via VoiceOutput (created on
 *    the main thread, per TTS rules).
 * 7. RISKY commands: screen off → verbal yes/no confirmation
 *    (VerbalConfirm); screen on → a full-screen-intent notification opens
 *    MainActivity's confirm dialog (ACTION_CONFIRM_RISKY seam, handled by
 *    the UI builder in the final pass).
 *
 * Typed chat is untouched — it stays first-class on the main screen.
 */
class BalochListenerService : Service() {

    private lateinit var powerManager: PowerManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var mainHandler: Handler

    private lateinit var serviceScope: CoroutineScope
    private lateinit var voiceOutput: VoiceOutput
    private lateinit var voiceInput: VoiceInput

    private var wakeEngine: WakeWordEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val interactionActive = AtomicBoolean(false)

    @Volatile
    private var started = false

    private var ttsCreatedAtMs = 0L

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        // onCreate runs on the main thread — safe for VoiceOutput (TTS).
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mainHandler = Handler(Looper.getMainLooper())
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        createChannels()
        voiceOutput = VoiceOutput(this)
        ttsCreatedAtMs = SystemClock.elapsedRealtime()
        voiceInput = VoiceInput(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                return START_NOT_STICKY
            }
            else -> {
                ensureStarted()
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            wakeEngine?.stop()
        } catch (_: Exception) {
            /* ignore */
        }
        wakeEngine = null
        try {
            serviceScope.cancel()
        } catch (_: Exception) {
            /* ignore */
        }
        try {
            voiceInput.destroy()
        } catch (_: Exception) {
            /* ignore */
        }
        try {
            voiceOutput.shutdown()
        } catch (_: Exception) {
            /* ignore */
        }
        releaseWakeLock()
        started = false
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Start / stop
    // ------------------------------------------------------------------

    @Synchronized
    private fun ensureStarted() {
        if (started) return
        if (!isAlwaysListening(this)) {
            Log.i(TAG, "always-listening toggle is off — not starting")
            stopSelf()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Honest: without the mic we cannot listen. Tell the user via a
            // plain notification and stop — never spin silently.
            Log.w(TAG, "RECORD_AUDIO not granted — cannot start listening")
            notificationManager.notify(
                NOTIF_ID_MIC_DENIED,
                NotificationCompat.Builder(this, CHANNEL_CONFIRM)
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .setContentTitle("Baloch can't listen yet")
                    .setContentText("Microphone permission is off — grant it in Settings to enable always-on voice.")
                    .setAutoCancel(true)
                    .build(),
            )
            stopSelf()
            return
        }

        ServiceCompat.startForeground(
            this,
            NOTIF_ID_LISTENING,
            buildListeningNotification(STATUS_STARTING),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        acquireWakeLock()

        wakeEngine = WakeWordEngine(
            applicationContext,
            onWakeWord = { onWakeWordFired() },
            onStatus = { status -> onEngineStatus(status) },
        ).also { it.start() }

        started = true
        Log.i(TAG, "always-on voice started")
    }

    private fun shutdown() {
        Log.i(TAG, "always-on voice stopping")
        try {
            wakeEngine?.stop()
        } catch (_: Exception) {
            /* ignore */
        }
        wakeEngine = null
        releaseWakeLock()
        started = false
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            /* ignore */
        }
        stopSelf()
    }

    private fun acquireWakeLock() {
        try {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Baloch:always-on-voice",
            ).apply { acquire() }
        } catch (e: Exception) {
            Log.w(TAG, "wake lock acquire failed", e)
            wakeLock = null
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
            /* ignore */
        }
        wakeLock = null
    }

    // ------------------------------------------------------------------
    // Wake → interact loop
    // ------------------------------------------------------------------

    private fun onWakeWordFired() {
        // Never stack interactions: one command at a time.
        if (!interactionActive.compareAndSet(false, true)) return
        serviceScope.launch {
            try {
                handleInteraction()
            } catch (e: Exception) {
                Log.w(TAG, "interaction failed", e)
            } finally {
                interactionActive.set(false)
            }
        }
    }

    private fun onEngineStatus(status: String) {
        serviceScope.launch(Dispatchers.Main) {
            try {
                if (started) {
                    notificationManager.notify(
                        NOTIF_ID_LISTENING,
                        buildListeningNotification(status),
                    )
                }
            } catch (_: Exception) {
                /* notification update is best-effort */
            }
        }
    }

    private suspend fun handleInteraction() {
        val engine = wakeEngine ?: return
        engine.suspendListening()
        try {
            playChime()
            val lang = currentLanguage()
            ensureTtsWarm()

            val (audio, text) = captureCommand(lang, COMMAND_TIMEOUT_MS)

            // 1. Energy gate on the raw capture.
            val energy = if (audio != null) rms(audio) else 0.0
            if (audio == null || energy < MIN_UTTERANCE_RMS) {
                Log.i(TAG, "utterance too quiet (rms=$energy) — asking to repeat")
                speak(Strings.DIDNT_HEAR, lang)
                return
            }

            // 2. Pipeline must be wired (parent's final pass).
            val pipe = HandsFreeWiring.pipeline
            if (pipe == null) {
                Log.w(TAG, "wake handled but HandsFreeWiring.pipeline is not set yet")
                speak(Strings.NOT_WIRED, lang)
                return
            }

            // 3. Voice lock: verify the speaker BEFORE anything executes.
            if (pipe.isVoiceLockEnabled()) {
                val verified = try {
                    withContext(Dispatchers.Default) { pipe.verifySpeaker(audio) }
                } catch (e: Exception) {
                    Log.w(TAG, "speaker verification errored", e)
                    false // fail closed: errors never become "yes"
                }
                if (!verified) {
                    Log.i(TAG, "speaker verification failed — refusing")
                    speak(Strings.SPEAKER_REFUSED, lang)
                    return
                }
            }

            // 4. Transcription must exist.
            val command = text?.trim().orEmpty()
            if (command.isEmpty()) {
                speak(Replies.NOT_UNDERSTOOD, lang)
                return
            }
            // Never log the command text — it may contain names, numbers, requests.
            Log.i(TAG, "hands-free command received ($lang)")

            // 5. Risky commands need confirmation.
            if (isLocallyRisky(command)) {
                if (!powerManager.isInteractive) {
                    // Screen off / locked: ask out loud.
                    val approved = VerbalConfirm.askYesNo(
                        tts = voiceOutput,
                        listen = { listenOnce(lang, CONFIRM_LISTEN_MS) },
                        question = VerbalConfirm.confirmQuestion(shortAction(command)),
                        lang = lang,
                    )
                    if (!approved) {
                        speak(Strings.CANCELLED, lang)
                        return
                    }
                } else {
                    // Screen on: hand the decision to MainActivity's confirm
                    // dialog via full-screen intent (UI seam — see docs).
                    postConfirmNotification(command, lang)
                    speak(Strings.CONFIRM_ON_SCREEN, lang)
                    return
                }
            }

            // 6. Execute + speak the result.
            val result = try {
                withContext(Dispatchers.Default) { pipe.execute(command, lang) }
            } catch (e: Exception) {
                Log.w(TAG, "pipeline.execute failed", e)
                Strings.EXEC_FAILED.pick(lang)
            }
            speakText(result, lang)
        } finally {
            try {
                wakeEngine?.resumeListening()
            } catch (_: Exception) {
                /* ignore */
            }
        }
    }

    // ------------------------------------------------------------------
    // Capture + transcription
    // ------------------------------------------------------------------

    /**
     * Captures the command utterance two ways at once: raw 16 kHz PCM via
     * [UtteranceCapture] (for the RMS gate + speaker verification) and
     * transcribed text via [VoiceInput]'s SpeechRecognizer in the active
     * language. Both read the mic concurrently — Android allows multiple
     * AudioRecord clients on the same input.
     */
    private suspend fun captureCommand(
        lang: BalochLanguage,
        timeoutMs: Long,
    ): Pair<ShortArray?, String?> = withContext(Dispatchers.Default) {
        val capture = UtteranceCapture()
        val rawStarted = try {
            capture.start()
        } catch (_: Exception) {
            false
        }
        val textDeferred = CompletableDeferred<String?>()
        withContext(Dispatchers.Main) {
            try {
                voiceInput.startListening(
                    lang,
                    onPartial = {},
                    onFinal = { textDeferred.complete(it) },
                    onError = { textDeferred.complete(null) },
                    onFallback = {},
                )
            } catch (_: Exception) {
                textDeferred.complete(null)
            }
        }
        val text = try {
            withTimeoutOrNull(timeoutMs) { textDeferred.await() }
        } catch (_: Exception) {
            null
        }
        withContext(Dispatchers.Main) {
            try {
                voiceInput.stopListening()
            } catch (_: Exception) {
                /* ignore */
            }
        }
        val audio = if (rawStarted) {
            try {
                capture.stop()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        audio to text
    }

    /** One-shot STT used for yes/no answers (speaker already verified). */
    private suspend fun listenOnce(lang: BalochLanguage, timeoutMs: Long): String? =
        withContext(Dispatchers.Default) {
            val deferred = CompletableDeferred<String?>()
            withContext(Dispatchers.Main) {
                try {
                    voiceInput.startListening(
                        lang,
                        onPartial = {},
                        onFinal = { deferred.complete(it) },
                        onError = { deferred.complete(null) },
                    )
                } catch (_: Exception) {
                    deferred.complete(null)
                }
            }
            val text = try {
                withTimeoutOrNull(timeoutMs) { deferred.await() }
            } catch (_: Exception) {
                null
            }
            withContext(Dispatchers.Main) {
                try {
                    voiceInput.stopListening()
                } catch (_: Exception) {
                    /* ignore */
                }
            }
            text
        }

    private fun rms(audio: ShortArray): Double {
        if (audio.isEmpty()) return 0.0
        var sum = 0.0
        for (s in audio) {
            val v = s.toDouble()
            sum += v * v
        }
        return sqrt(sum / audio.size)
    }

    // ------------------------------------------------------------------
    // Speech
    // ------------------------------------------------------------------

    private suspend fun speak(msg: L3, lang: BalochLanguage) = speakText(msg.pick(lang), lang)

    private suspend fun speakText(text: String, lang: BalochLanguage) {
        if (text.isBlank()) return
        withContext(Dispatchers.Main) {
            try {
                voiceOutput.setLanguage(lang)
            } catch (_: Exception) {
                /* keep previous language rather than staying silent */
            }
            try {
                voiceOutput.speak(text)
            } catch (e: Exception) {
                Log.w(TAG, "speak failed", e)
            }
        }
    }

    /**
     * VoiceOutput.speak() silently drops when TTS isn't initialised yet.
     * After a cold service start, wait out the init window so the first
     * wake's reply (especially a voice-lock refusal) is actually heard.
     */
    private suspend fun ensureTtsWarm() {
        val elapsed = SystemClock.elapsedRealtime() - ttsCreatedAtMs
        if (elapsed < TTS_WARM_MS) delay(TTS_WARM_MS - elapsed)
    }

    private fun playChime() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
            try {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, CHIME_MS)
            } finally {
                mainHandler.postDelayed(
                    { try { tone.release() } catch (_: Exception) { /* ignore */ } },
                    (CHIME_MS + 150).toLong(),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "chime failed", e)
        }
    }

    // ------------------------------------------------------------------
    // Risky-command handling
    // ------------------------------------------------------------------

    /**
     * Conservative keyword fail-safe for destructive commands issued while
     * the screen is off. The pipeline (HandsFreePipeline.execute) owns real
     * risk classification; this list is a second net so a missed risky
     * action on a LOCKED phone can never execute silently. Kept
     * intentionally broad — false positives just cost one "say yes or no".
     */
    private fun isLocallyRisky(command: String): Boolean {
        val t = " ${command.lowercase()} "
        return RISKY_KEYWORDS.any { t.contains(it) }
    }

    /** Shorten the command for the spoken "Should I …?" question. */
    private fun shortAction(command: String): String {
        val c = command.trim()
        return if (c.length <= MAX_ACTION_CHARS) c else c.take(MAX_ACTION_CHARS).trimEnd() + "…"
    }

    /**
     * Screen is ON: post a high-priority full-screen-intent notification
     * that opens MainActivity with ACTION_CONFIRM_RISKY. SEAM — the UI
     * builder handles this intent in MainActivity in the final pass: show
     * the existing confirm dialog with the command text, and on "Yes" route
     * it through HandsFreeWiring.pipeline.execute() via the normal UI path.
     */
    private fun postConfirmNotification(command: String, lang: BalochLanguage) {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(ACTION_CONFIRM_RISKY)
            .putExtra(EXTRA_COMMAND, command)
            .putExtra(EXTRA_LANG, lang.code)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            this,
            REQ_CONFIRM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_CONFIRM)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Baloch needs your confirmation")
            .setContentText("“$command” — tap to review.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pending, true)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            notificationManager.notify(NOTIF_ID_CONFIRM, notification)
        } catch (e: Exception) {
            Log.w(TAG, "confirm notification failed", e)
        }
    }

    // ------------------------------------------------------------------
    // Notifications / channels
    // ------------------------------------------------------------------

    private fun createChannels() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CONFIRM,
                "Baloch confirmations",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Risky-action confirmations while the screen is on" },
        )
    }

    private fun buildListeningNotification(status: String): Notification {
        val text = when (status) {
            WakeWordEngine.STATUS_READY, STATUS_STARTING ->
                "Baloch is listening — say \"hey baloch\""
            else -> "Baloch — $status"
        }
        return BalochNotifications.serviceNotification(this, "Baloch", text)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun currentLanguage(): BalochLanguage {
        return try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            BalochLanguage.fromCode(
                prefs.getString(KEY_LANGUAGE, BalochLanguage.ENGLISH.code)
                    ?: BalochLanguage.ENGLISH.code,
            )
        } catch (_: Exception) {
            BalochLanguage.ENGLISH
        }
    }

    /** Trilingual strings for the hands-free path (en-GB "sir" throughout). */
    private object Strings {
        val DIDNT_HEAR = L3(
            en = "I didn't quite hear that, sir. Say 'hey baloch' and try again.",
            ur = "میں ٹھیک سے سن نہیں پایا، سر۔ 'ہے بالوچ' کہہ کر دوبارہ کوشش کریں۔",
            hi = "मैं ठीक से सुन नहीं पाया, सर। 'हे बालोच' कहकर दोबारा कोशिश करें।",
        )
        val SPEAKER_REFUSED = L3(
            en = "That's not Kamran's voice, sir. I can't help with that.",
            ur = "یہ کامران کی آواز نہیں، سر۔ میں اس میں مدد نہیں کر سکتا۔",
            hi = "यह कामरान की आवाज़ नहीं है, सर। मैं इसमें मदद नहीं कर सकता।",
        )
        val NOT_WIRED = L3(
            en = "My command pipeline isn't connected yet, sir. The main app still works — talk to me there.",
            ur = "میری کمانڈ پائپ لائن ابھی منسلک نہیں، سر۔ مین ایپ اب بھی کام کرتی ہے — وہیں بات کریں۔",
            hi = "मेरी कमांड पाइपलाइन अभी जुड़ी नहीं है, सर। मुख्य ऐप अभी भी काम करती है — वहीं बात करें।",
        )
        val CANCELLED = L3(
            en = "Cancelled, sir.",
            ur = "منسوخ کر دیا، سر۔",
            hi = "रद्द कर दिया, सर।",
        )
        val CONFIRM_ON_SCREEN = L3(
            en = "That needs your confirmation, sir — I've put it on your screen.",
            ur = "اس کے لیے آپ کی تصدیق درکار ہے، سر — میں نے اسے آپ کی اسکرین پر بھیج دیا ہے۔",
            hi = "इसके लिए आपकी पुष्टि चाहिए, सर — मैंने इसे आपकी स्क्रीन पर भेज दिया है।",
        )
        val EXEC_FAILED = L3(
            en = "Something went wrong running that, sir. Try again.",
            ur = "اسے چلاتے ہوئے کچھ غلط ہو گیا، سر۔ دوبارہ کوشش کریں۔",
            hi = "इसे चलाते समय कुछ गड़बड़ हो गई, सर। दोबारा कोशिश करें।",
        )
    }

    companion object {
        private const val TAG = "BalochListenerService"

        const val ACTION_START = "com.baloch.ai.action.START_LISTENING"
        const val ACTION_STOP = "com.baloch.ai.action.STOP_LISTENING"

        /**
         * SEAM — intent the confirm notification fires at MainActivity when
         * the screen is ON and a risky command needs a human decision.
         * Handled by the UI builder in the final pass (show confirm dialog;
         * on "Yes", execute via HandsFreeWiring.pipeline).
         */
        const val ACTION_CONFIRM_RISKY = "com.baloch.ai.action.CONFIRM_RISKY"
        const val EXTRA_COMMAND = "extra_command"
        const val EXTRA_LANG = "extra_lang"

        private const val CHANNEL_CONFIRM = "baloch_confirm"

        private const val NOTIF_ID_LISTENING = 1001
        private const val NOTIF_ID_CONFIRM = 1002
        private const val NOTIF_ID_MIC_DENIED = 1003
        private const val REQ_CONFIRM = 2001

        private const val PREFS_NAME = "baloch_prefs"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_ALWAYS_LISTENING = "always_listening"

        private const val STATUS_STARTING = "starting"

        /** Max mic time per command utterance before we stop and decide. */
        private const val COMMAND_TIMEOUT_MS = 7_000L

        /** Max mic time for a yes/no answer. */
        private const val CONFIRM_LISTEN_MS = 10_000L

        /**
         * RMS floor for a real utterance (16-bit PCM). Room tone sits
         * ~40–120; anything under 90 is treated as "didn't hear anything"
         * rather than fed to verification/execution.
         */
        private const val MIN_UTTERANCE_RMS = 90.0

        private const val CHIME_MS = 180
        private const val MAX_ACTION_CHARS = 42
        /** TTS engine init window after a cold service start (see ensureTtsWarm). */
        private const val TTS_WARM_MS = 1200L

        /**
         * Fail-safe keyword net for destructive commands (see
         * isLocallyRisky). Matched against " <command> " lowercased, so
         * entries carry their own word boundaries via leading/trailing
         * spaces where needed.
         */
        private val RISKY_KEYWORDS = listOf(
            "call ", "dial ", "phone ", "ring ",
            "send ", "sms", "message", "text ", "whatsapp",
            "delete", "remove ", "erase", "wipe", "format",
            "shutdown", "power off", "switch off", "restart", "reboot",
            "pay ", "payment", "transfer", "buy ", "order ", "purchase",
            "uninstall",
            // Roman Urdu variants Kamran actually speaks:
            "bhejo", "bhej ", "karo call", "delete karo", "band karo",
            "paise", "payment karo",
        )

        /** Honest battery note — shown in the notification/settings copy. */
        const val BATTERY_NOTE =
            "Always-on listening uses ~3–5% battery per hour. Turn off to save battery."

        /**
         * "Always listening" toggle state. Default ON per Kamran's order —
         * the service refuses to start when it reads false.
         */
        fun setAlwaysListening(ctx: Context, enabled: Boolean) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ALWAYS_LISTENING, enabled)
                .apply()
        }

        fun isAlwaysListening(ctx: Context): Boolean =
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ALWAYS_LISTENING, true)

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, BalochListenerService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(ctx: Context) {
            ctx.startService(
                Intent(ctx, BalochListenerService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
