package com.baloch.ai.power

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.baloch.ai.R
import com.baloch.ai.brain.BalochLanguage
import java.util.Locale

/**
 * Shared notification + one-shot speech helpers for the power package.
 *
 * Notifications all ride ONE channel ("Baloch power features") so the ten
 * features never spam ten channels. Speech is the CallerIdService one-shot
 * pattern: a dedicated TextToSpeech on the caller's context, Kamran's
 * locked deeper pitch (0.87), STREAM_MUSIC (his loud-voice setting), shut
 * down the moment the utterance finishes — nothing kept alive.
 */
object PowerNotify {

    const val CHANNEL_ID = "baloch_power"
    private const val CHANNEL_NAME = "Baloch power features"

    fun notify(
        context: Context,
        title: String,
        text: String,
        id: Int,
    ) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            )
            val n = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(id, n)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied — stay silent, try again next tick.
        } catch (_: Exception) {
            // Never crash a background path.
        }
    }
}

/**
 * One-shot TTS for receivers/workers that have no VoiceOutput handy.
 * Fire-and-forget: Baloch speaks the text once in the active language and
 * the engine is released. Safe to call from a BroadcastReceiver (it does
 * no blocking work on the calling thread).
 */
object PowerSpeak {

    fun speak(context: Context, text: String) =
        speak(context, PowerPrefs.activeLang(context), text)

    fun speak(context: Context, lang: BalochLanguage, text: String) {
        if (text.isBlank()) return
        try {
            val holder = arrayOfNulls<TextToSpeech>(1)
            holder[0] = TextToSpeech(context.applicationContext) { status ->
                val tts = holder[0] ?: return@TextToSpeech
                try {
                    if (status == TextToSpeech.SUCCESS) {
                        val availability = tts.isLanguageAvailable(lang.ttsLocale)
                        tts.language =
                            if (availability == TextToSpeech.LANG_MISSING_DATA ||
                                availability == TextToSpeech.LANG_NOT_SUPPORTED
                            ) Locale.US else lang.ttsLocale
                        tts.setPitch(0.87f) // Kamran's locked deeper voice
                        tts.setSpeechRate(1.0f)
                        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {
                                try { tts.shutdown() } catch (_: Exception) {}
                            }
                            @Suppress("DEPRECATION", "OverridingDeprecatedMember")
                            override fun onError(utteranceId: String?) {
                                try { tts.shutdown() } catch (_: Exception) {}
                            }
                        })
                        val params = Bundle().apply {
                            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                        }
                        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "power")
                    } else {
                        tts.shutdown()
                    }
                } catch (_: Exception) {
                    try { tts.shutdown() } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {
            // TTS unavailable — the notification (if any) still carries the message.
        }
    }
}
