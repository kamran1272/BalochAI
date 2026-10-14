package com.baloch.ai.callerid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import com.baloch.ai.settings.LanguageManager
import java.util.Locale

/**
 * Incoming-call detection — TWO paths, both honest about what they can do.
 *
 * PATH A — [BalochCallScreeningService] (API 24+):
 *   The system hands us every incoming call via onScreenCall() with the real
 *   caller number. This needs the ROLE_CALL_SCREENING role (RoleManager,
 *   API 29+): Kamran grants it once from the Call Log screen. On API 26–28
 *   the screening role does not exist, so Path A is unavailable there.
 *
 * PATH B — [CallStateWatcher] (fallback, NO role needed):
 *   A PhoneStateListener / TelephonyCallback watching CALL_STATE_RINGING
 *   with only READ_PHONE_STATE. Works on API 26+ without being the screening
 *   app. Honest limit: on API 31+ the framework withholds the incoming number
 *   from non-default-dialer apps, so Path B degrades to ring-timing only
 *   (Baloch announces a generic "unknown number" line); on API 26–30 the
 *   deprecated listener still delivers the number when READ_PHONE_STATE is
 *   granted (OEM-dependent).
 *
 * What Baloch does NOT do: hard-reject (end) calls. A network-level reject
 * needs MODIFY_PHONE_STATE (system apps only) or the default-dialer role
 * with TelecomManager.endCall(). Blocked numbers get their ringer SILENCED
 * via AudioManager (needs no special permission) and the previous ringer
 * mode is restored when the call ends.
 *
 * Hot path: no network anywhere — contact lookup + in-memory prefix tables
 * + JSON blocklist, target <200ms to first speech.
 */

// ----------------------------------------------------------------------
// Path A: system call screening
// ----------------------------------------------------------------------

class BalochCallScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        // The framework REQUIRES a response — allow the call through always;
        // Baloch only announces (and silences blocked numbers), never blocks.
        val allow = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
        try {
            respondToCall(details, allow)
        } catch (_: Exception) {
            // Last resort: still answer the framework so the call isn't stuck.
            try {
                respondToCall(details, CallResponse.Builder().build())
            } catch (_: Exception) { /* give up quietly */ }
            return
        }
        val raw = details.handle?.schemeSpecificPart // tel:+923001234567 -> +923001234567
        CallerIdAnnouncer.handleIncomingCall(this, raw)
    }
}

// ----------------------------------------------------------------------
// Path B: no-role ring watcher
// ----------------------------------------------------------------------

object CallStateWatcher {

    private val lock = Any()

    @Suppress("DEPRECATION")
    private var legacyListener: PhoneStateListener? = null
    private var modernCallback: TelephonyCallback? = null

    /** Start watching. No-op (returns false) without READ_PHONE_STATE. */
    fun start(context: Context): Boolean {
        val appCtx = context.applicationContext
        if (ContextCompat.checkSelfPermission(
                appCtx, Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        val tm = appCtx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        synchronized(lock) {
            stopLocked(tm)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        // API 31+: no number delivered to non-dialer apps — timing only.
                        when (state) {
                            TelephonyManager.CALL_STATE_RINGING ->
                                CallerIdAnnouncer.handleIncomingCall(appCtx, null)
                            TelephonyManager.CALL_STATE_IDLE ->
                                CallerIdAnnouncer.onCallIdle(appCtx)
                        }
                    }
                }
                modernCallback = cb
                tm.registerTelephonyCallback(appCtx.mainExecutor, cb)
            } else {
                @Suppress("DEPRECATION")
                val pl = object : PhoneStateListener() {
                    @Deprecated(
                        "Deprecated in API 31; kept for the API 26–30 path " +
                            "where the number is still delivered."
                    )
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        when (state) {
                            TelephonyManager.CALL_STATE_RINGING ->
                                CallerIdAnnouncer.handleIncomingCall(appCtx, phoneNumber)
                            TelephonyManager.CALL_STATE_IDLE ->
                                CallerIdAnnouncer.onCallIdle(appCtx)
                        }
                    }
                }
                legacyListener = pl
                @Suppress("DEPRECATION")
                tm.listen(pl, PhoneStateListener.LISTEN_CALL_STATE)
            }
        }
        return true
    }

    fun stop(context: Context) {
        val tm = context.applicationContext
            .getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        synchronized(lock) { stopLocked(tm) }
    }

    private fun stopLocked(tm: TelephonyManager) {
        legacyListener?.let {
            @Suppress("DEPRECATION")
            tm.listen(it, PhoneStateListener.LISTEN_NONE)
            legacyListener = null
        }
        modernCallback?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                tm.unregisterTelephonyCallback(it)
            }
            modernCallback = null
        }
    }
}

// ----------------------------------------------------------------------
// Shared announce core
// ----------------------------------------------------------------------

/** The unanswered "pick up or cut it?" question — the parent wires yes/no. */
data class SpamQuestion(
    val rawNumber: String,
    val normalizedNumber: String,
    val askedAtMs: Long,
)

object CallerIdAnnouncer {

    @Volatile
    private var pendingQuestion: SpamQuestion? = null

    /** Seam for the parent: the last unanswered spam question, if any. */
    fun pendingSpamQuestion(): SpamQuestion? = pendingQuestion

    fun clearSpamQuestion() {
        pendingQuestion = null
    }

    /** Ringer mode saved before silencing a blocked caller; -1 = none saved. */
    @Volatile
    private var savedRingerMode: Int = -1

    // -- announcement templates ------------------------------------------

    private val KNOWN_CALLER = L3(
        en = "Sir, {name} is calling.",
        ur = "سر، {name} کی کال آ رہی ہے",
        hi = "सर, {name} का कॉल आ रहा है",
    )
    private val SPAM_QUESTION = L3(
        en = "Sir, this is a reported spam number — pick up or cut it?",
        ur = "سر، یہ رپورٹڈ اسپام نمبر ہے — اٹھاؤں یا کاٹ دوں؟",
        hi = "सर, यह रिपोर्टेड स्पैम नंबर है — उठाऊँ या काट दूँ?",
    )
    private val GENERIC_RING = L3(
        en = "Sir, incoming call from an unknown number.",
        ur = "سر، نامعلوم نمبر سے کال آ رہی ہے",
        hi = "सर, अज्ञात नंबर से कॉल आ रहा है",
    )

    /**
     * The hot path. Runs on the binder/callback thread — keeps work small:
     * contact lookup -> blocklist -> spam -> prefix tables -> one TTS line.
     */
    fun handleIncomingCall(context: Context, rawNumber: String?) {
        val appCtx = context.applicationContext
        val lang = LanguageManager(appCtx).current()
        val normalized = rawNumber?.let { PrefixTables.normalize(it) }
            ?.takeIf { it.isNotBlank() }

        // 1) Known contact -> announce by name (same pattern as
        //    DeviceActions.resolveContact, reversed: number -> name).
        val name = normalized?.let { lookupContactName(appCtx, it) }
        if (name != null) {
            speak(appCtx, lang, KNOWN_CALLER.format(lang, "name" to name))
            return
        }

        // 2) Blocked -> silence the ringer, no announcement.
        if (normalized != null && SpamShield.isBlocked(appCtx, normalized)) {
            silenceRinger(appCtx)
            return
        }

        // 3) Spam pattern -> ask Kamran what to do; parent wires yes/no.
        if (normalized != null && SpamShield.isSpam(normalized)) {
            pendingQuestion = SpamQuestion(
                rawNumber = rawNumber ?: "",
                normalizedNumber = normalized,
                askedAtMs = System.currentTimeMillis(),
            )
            speak(appCtx, lang, SPAM_QUESTION.pick(lang))
            return
        }

        // 4) Unknown -> offline network/city description (or generic line
        //    when Path B couldn't see the number at all).
        val text = if (normalized != null) PrefixTables.describe(normalized, lang)
        else GENERIC_RING.pick(lang)
        speak(appCtx, lang, text)
    }

    /** Restore the ringer after a silenced blocked call ends. */
    fun onCallIdle(context: Context) {
        val saved = savedRingerMode
        if (saved == -1) return
        savedRingerMode = -1
        try {
            val am = context.applicationContext
                .getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.ringerMode = saved
        } catch (_: Exception) { /* never break the idle path */ }
    }

    // -- contact reverse lookup ------------------------------------------

    private fun lookupContactName(ctx: Context, normalized: String): String? {
        if (ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) return null
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(normalized),
        )
        val cursor = ctx.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null,
        ) ?: return null
        cursor.use {
            if (it.moveToFirst()) return it.getString(0)?.takeIf { s -> s.isNotBlank() }
        }
        return null
    }

    // -- ringer silence (NOT a call reject — see class KDoc) --------------

    private fun silenceRinger(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (savedRingerMode == -1) savedRingerMode = am.ringerMode
            // RINGER_MODE_SILENT needs no special permission (unlike DND).
            am.ringerMode = AudioManager.RINGER_MODE_SILENT
        } catch (_: Exception) { /* best effort */ }
    }

    // -- one-shot TTS from the service context ----------------------------

    /**
     * Dedicated TextToSpeech on the service context with an OnInitListener;
     * short utterance on STREAM_MUSIC (same stream VoiceOutput uses, so
     * Kamran's loud-voice setting applies), then shutdown on completion.
     * Nothing is kept alive between calls.
     */
    private fun speak(ctx: Context, lang: BalochLanguage, text: String) {
        if (text.isBlank()) return
        val holder = arrayOfNulls<TextToSpeech>(1)
        holder[0] = TextToSpeech(ctx) { status ->
            val tts = holder[0] ?: return@TextToSpeech
            try {
                if (status == TextToSpeech.SUCCESS) {
                    val availability = tts.isLanguageAvailable(lang.ttsLocale)
                    tts.language =
                        if (availability == TextToSpeech.LANG_MISSING_DATA ||
                            availability == TextToSpeech.LANG_NOT_SUPPORTED
                        ) Locale.US else lang.ttsLocale
                    tts.setPitch(0.87f)   // Kamran's locked deeper voice
                    tts.setSpeechRate(1.0f)
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            try { tts.shutdown() } catch (_: Exception) {}
                        }
                        // Single-arg onError is deprecated since API 23 but
                        // remains the abstract method — overriding it warns,
                        // it does not fail. The two-arg default delegates here.
                        @Suppress("DEPRECATION", "OverridingDeprecatedMember")
                        override fun onError(utteranceId: String?) {
                            try { tts.shutdown() } catch (_: Exception) {}
                        }
                    })
                    val params = Bundle().apply {
                        putInt(
                            TextToSpeech.Engine.KEY_PARAM_STREAM,
                            AudioManager.STREAM_MUSIC,
                        )
                    }
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "callerid")
                } else {
                    tts.shutdown()
                }
            } catch (_: Exception) {
                try { tts.shutdown() } catch (_: Exception) {}
            }
        }
    }

    // -- deep lookup seam ---------------------------------------------------

    /**
     * Follow-up: "is number ka pata lagao".
     * Honest framing — this opens a web search for the number; results are
     * whatever the internet says, NOT a verified identity. The parent shows
     * "Internet par is number ke baare mein ye mila — certain nahi hai, sir."
     * Requires connectivity (offline-first: parent checks ConnectivityGate).
     */
    fun deepLookupIntent(rawNumber: String): Intent =
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse(
                "https://html.duckduckgo.com/html/?q=" + Uri.encode(rawNumber)
            ),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
