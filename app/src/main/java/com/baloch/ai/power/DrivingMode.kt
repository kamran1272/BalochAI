package com.baloch.ai.power

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.SmsManager
import android.telephony.SmsMessage
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import java.util.concurrent.TimeUnit

/**
 * DRIVING MODE — auto-reply + read-aloud while Kamran drives.
 *
 * When ON:
 *  - Incoming SMS get an automatic reply ("I'm driving, will reply later")
 *    and are read aloud via TTS.
 *  - Other app notifications are read aloud (title + text) via
 *    [DrivingNotifications] — this is a NotificationListenerService and
 *    needs "Notification access" granted manually in Android Settings;
 *    without it, only SMS are read aloud.
 *  - The mode auto-switches OFF after [PowerPrefs.DRIVING_AUTO_OFF_MIN]
 *    minutes (0 = never, manual toggle only).
 *
 * HONEST NOTES:
 *  - Needs RECEIVE_SMS (to see the SMS) and SEND_SMS (to send the reply)
 *    — both are runtime permissions Kamran grants once. READ_SMS is NOT
 *    needed: the message body is parsed from the broadcast PDUs.
 *  - The auto-reply goes to EVERY sender, including unknown numbers and
 *    potential OTP/bank senders. It is a plain text; no link, no data.
 *  - Activity-recognition auto-detect is deliberately NOT implemented —
 *    it needs ACTIVITY_RECOGNITION + constant location/sensor polling for
 *    a feature Kamran toggles manually in one tap. Kept simple on purpose.
 */
object DrivingMode {

    fun isOn(context: Context): Boolean = PowerPrefs.isOn(context, PowerPrefs.DRIVING)

    fun setOn(context: Context, on: Boolean) {
        PowerPrefs.setOn(context, PowerPrefs.DRIVING, on)
        if (on) scheduleAutoOff(context) else cancelAutoOff(context)
    }

    fun autoOffMinutes(context: Context): Int =
        PowerPrefs.getInt(context, PowerPrefs.DRIVING_AUTO_OFF_MIN, 120)

    fun setAutoOffMinutes(context: Context, minutes: Int) {
        PowerPrefs.setInt(context, PowerPrefs.DRIVING_AUTO_OFF_MIN, minutes.coerceIn(0, 24 * 60))
        if (isOn(context)) scheduleAutoOff(context) // re-arm with the new timer
    }

    fun autoReplyOn(context: Context): Boolean =
        context.getSharedPreferences("baloch_power", Context.MODE_PRIVATE)
            .getBoolean(PowerPrefs.DRIVING_AUTO_REPLY, true)

    fun setAutoReplyOn(context: Context, on: Boolean) =
        PowerPrefs.setOn(context, PowerPrefs.DRIVING_AUTO_REPLY, on)

    private const val AUTO_OFF_WORK = "driving_auto_off"

    private fun scheduleAutoOff(context: Context) {
        cancelAutoOff(context)
        val minutes = autoOffMinutes(context)
        if (minutes <= 0) return
        val req = OneTimeWorkRequestBuilder<DrivingAutoOffWorker>()
            .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
            .addTag(AUTO_OFF_WORK)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(AUTO_OFF_WORK, ExistingWorkPolicy.REPLACE, req)
    }

    private fun cancelAutoOff(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(AUTO_OFF_WORK) }
    }

    private val AUTO_REPLY = L3(
        "I'm driving right now, sir — Kamran will reply when he's parked. 🚗",
        "میں اس وقت ڈرائیونگ کر رہا ہوں، سر — کامران پارک کر کے جواب دیں گے۔ 🚗",
        "मैं इस वक़्त ड्राइविंग कर रहा हूँ, सर — कामरान पार्क करके जवाब देंगे। 🚗",
    )

    fun autoReplyText(lang: BalochLanguage): String = AUTO_REPLY.pick(lang)
}

/** Fires when the auto-off timer expires — switches driving mode off. */
class DrivingAutoOffWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (DrivingMode.isOn(applicationContext)) {
            DrivingMode.setOn(applicationContext, false)
            val lang = PowerPrefs.activeLang(applicationContext)
            PowerNotify.notify(
                applicationContext,
                "🚗 " + L3("Driving mode off", "ڈرائیونگ موڈ بند", "ड्राइविंग मोड बंद").pick(lang),
                L3(
                    "Auto-off timer expired, sir — driving mode is off.",
                    "آٹو آف ٹائمر ختم، سر — ڈرائیونگ موڈ بند ہو گیا۔",
                    "ऑटो-ऑफ़ टाइमर खत्म, सर — ड्राइविंग मोड बंद हो गया।",
                ).pick(lang),
                6101,
            )
        }
        return Result.success()
    }
}

/**
 * Answers incoming SMS while driving mode is ON.
 *
 * Declared in the manifest with the SMS_RECEIVED action (exported=true,
 * guarded by RECEIVE_SMS). Parses the PDUs straight from the intent —
 * no READ_SMS needed. Sends the reply via SmsManager (needs SEND_SMS)
 * and reads the message aloud.
 */
class DrivingSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!DrivingMode.isOn(context)) return

        val lang = PowerPrefs.activeLang(context)
        val bundle: Bundle = intent.extras ?: return
        val format = bundle.getString("format")
        val pdus = bundle.get("pdus") as? Array<*> ?: return

        for (pdu in pdus) {
            val bytes = pdu as? ByteArray ?: continue
            val msg: SmsMessage = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    SmsMessage.createFromPdu(bytes, format)
                else
                    @Suppress("DEPRECATION") SmsMessage.createFromPdu(bytes)
            } catch (_: Exception) { continue }

            val sender = msg.originatingAddress ?: continue
            val body = msg.messageBody ?: ""

            // 1) Auto-reply (toggleable in Power Features).
            if (DrivingMode.autoReplyOn(context)) {
                sendAutoReply(context, sender, DrivingMode.autoReplyText(lang))
            }

            // 2) Read aloud: "Sir, SMS from <sender>: <body>".
            PowerSpeak.speak(
                context, lang,
                L3(
                    "Sir, SMS from $sender: $body",
                    "سر، $sender سے ایس ایم ایس آیا ہے: $body",
                    "सर, $sender से एसएमएस आया है: $body",
                ).pick(lang),
            )
        }
    }

    private fun sendAutoReply(context: Context, to: String, text: String) {
        try {
            smsManager(context).sendTextMessage(to, null, text, null, null)
        } catch (_: SecurityException) {
            // SEND_SMS not granted — the read-aloud still happened.
        } catch (_: Exception) {
            // Never crash the receiver.
        }
    }

    private fun smsManager(context: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            context.getSystemService(SmsManager::class.java)
        else
            @Suppress("DEPRECATION") SmsManager.getDefault()
}

/**
 * Reads non-SMS notifications aloud while driving mode is ON.
 *
 * REQUIRES the user to grant "Notification access" to Baloch once in
 * Android Settings (this cannot be requested as a normal runtime
 * permission — the system sends the user to Settings). Until granted,
 * this service simply never starts and only SMS are read aloud.
 * Baloch's own notifications are skipped.
 */
class DrivingNotifications : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!DrivingMode.isOn(this)) return
        if (sbn.packageName == packageName) return // skip our own
        val n: Notification = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val app = appLabel(sbn.packageName)
        val lang = PowerPrefs.activeLang(this)
        PowerSpeak.speak(
            this, lang,
            L3(
                "Sir, notification from $app: $title. $text",
                "سر، $app سے نوٹیفکیشن: $title۔ $text",
                "सर, $app से नोटिफिकेशन: $title। $text",
            ).pick(lang),
        )
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(pkg, 0)
        ).toString()
    } catch (_: Exception) {
        pkg
    }
}
