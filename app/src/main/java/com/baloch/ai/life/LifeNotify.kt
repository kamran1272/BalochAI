package com.baloch.ai.life

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.baloch.ai.R

/**
 * Small shared notification helper for the life-feature workers.
 * One channel per feature so Kamran can silence them individually
 * in system settings. Follows the BatteryGuardianWorker pattern.
 */
object LifeNotify {

    const val CH_PRAYER = "baloch_prayer"
    const val CH_COPILOT = "baloch_copilot"
    const val CH_FOCUS = "baloch_focus"
    const val CH_GEO = "baloch_geo"

    fun ensureChannel(context: Context, channelId: String, name: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                channelId,
                name,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Baloch life-feature alerts" }
        )
    }

    fun send(
        context: Context,
        channelId: String,
        channelName: String,
        notifId: Int,
        title: String,
        text: String,
    ) {
        try {
            ensureChannel(context, channelId, channelName)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notif = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build()
            nm.notify(notifId, notif)
        } catch (_: Exception) {
            // Never crash a background worker on a notification failure.
        }
    }
}
