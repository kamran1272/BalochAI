package com.baloch.ai.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.baloch.ai.MainActivity
import com.baloch.ai.R

/**
 * Central branded notification builders for Baloch.
 *
 * All persistent service indicators and alerts share one look:
 * the white Arc Reactor status icon ([R.drawable.ic_stat_baloch]),
 * Baloch cyan accent, and a tap target that opens [MainActivity].
 */
object BalochNotifications {

    const val CHANNEL_SERVICE = "baloch_service"
    const val CHANNEL_ALERTS = "baloch_alerts"

    /** Cyan accent used across Baloch branding. */
    private const val BALOCH_CYAN = 0xFF00E5FF.toInt()

    /**
     * Creates the notification channels if they do not exist yet.
     * Safe to call repeatedly and from any thread that owns a Context.
     */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_SERVICE) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SERVICE,
                    "Baloch background services",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Persistent indicators for Baloch's always-on features"
                },
            )
        }
        if (mgr.getNotificationChannel(CHANNEL_ALERTS) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ALERTS,
                    "Baloch alerts",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Important alerts from Baloch"
                },
            )
        }
    }

    private fun tapIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Branded ongoing notification for foreground services.
     * Low profile: no timestamp, stays until the service stops.
     */
    fun serviceNotification(context: Context, title: String, text: String): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_baloch)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(BALOCH_CYAN)
            .setContentIntent(tapIntent(context))
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    /**
     * Posts a high-priority auto-cancel alert. Silent if notifications
     * are revoked (SecurityException) — never crashes the caller.
     */
    fun alert(context: Context, title: String, text: String, id: Int) {
        try {
            ensureChannels(context)
            val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_baloch)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setColor(BALOCH_CYAN)
                .setContentIntent(tapIntent(context))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission revoked — stay silent.
        }
    }
}
