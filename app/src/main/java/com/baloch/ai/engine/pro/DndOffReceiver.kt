package com.baloch.ai.engine.pro

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.baloch.ai.R
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3

/**
 * Fires when a [com.baloch.ai.brain.DeviceAction.DisableDndDelayed] timer
 * elapses (e.g. "meeting mode" ending after 60 minutes) and lifts
 * do-not-disturb automatically.
 *
 * Honest fallback: if Do-Not-Disturb access was revoked, we cannot lift
 * DND ourselves — so we post a notification telling Kamran instead of
 * failing silently.
 */
class DndOffReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                return
            }
        } catch (_: Exception) {
            // fall through to the honest notification below
        }
        notifyStillOn(context, nm)
    }

    private fun notifyStillOn(context: Context, nm: NotificationManager) {
        val lang = BalochLanguage.fromCode(
            context.getSharedPreferences("baloch_prefs", Context.MODE_PRIVATE)
                .getString("language", BalochLanguage.ENGLISH.code)
                ?: BalochLanguage.ENGLISH.code
        )
        val title = L3("◈ Baloch — meeting mode", "◈ بلوچ — میٹنگ موڈ", "◈ बलोच — मीटिंग मोड").pick(lang)
        val text = L3(
            "Your hour is up, sir — but DND is still on. Grant me Do-Not-Disturb access and I'll lift it automatically next time. 🤫",
            "آپ کا ایک گھنٹہ پورا ہو گیا، سر — لیکن ڈی این ڈی ابھی آن ہے۔ مجھے ڈو ناٹ ڈسٹرب کی رسائی دیں تو اگلی بار میں خود بند کر دوں گا۔ 🤫",
            "आपका एक घंटा पूरा हो गया, सर — लेकिन DND अभी चालू है। मुझे डू-नॉट-डिस्टर्ब एक्सेस दें तो अगली बार मैं खुद बंद कर दूँगा। 🤫",
        ).pick(lang)
        try {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Baloch routines", NotificationManager.IMPORTANCE_HIGH)
            )
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(ID, n)
        } catch (_: Exception) {
            // Nothing more we can do from a receiver — stay silent.
        }
    }

    companion object {
        private const val CHANNEL_ID = "baloch_routines"
        private const val ID = 4201
    }
}
