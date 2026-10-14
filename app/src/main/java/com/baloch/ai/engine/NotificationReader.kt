package com.baloch.ai.engine

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Captures active notifications so Baloch can summarise them.
 * Kamran must grant "Notification access" once (see PERMISSIONS.md).
 */
class NotificationReader : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        cache[sbn.key] = render(sbn)
        trim()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        cache.remove(sbn.key)
    }

    private fun render(sbn: StatusBarNotification): String {
        val app = packageLabel(sbn.packageName)
        val title = sbn.notification.extras.getCharSequence("android.title")?.toString()?.trim().orEmpty()
        val text = sbn.notification.extras.getCharSequence("android.text")?.toString()?.trim().orEmpty()
        return listOf(app, title, text).filter { it.isNotBlank() }.joinToString(": ").take(140)
    }

    private fun packageLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg.substringAfterLast('.')
    }

    private fun trim() {
        while (cache.size > 30) cache.remove(cache.keys.first())
    }

    companion object {
        private val cache = LinkedHashMap<String, String>()

        /** Most recent first. Empty when access is off or nothing arrived. */
        fun summary(): List<String> = cache.values.toList().asReversed()

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ).orEmpty()
            val me = ComponentName(context, NotificationReader::class.java).flattenToString()
            return flat.split(":").any { it.equals(me, ignoreCase = true) }
        }
    }
}
