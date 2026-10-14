package com.baloch.ai.engine.pro

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import java.util.Calendar

/**
 * Screen-time insights via UsageStatsManager.
 *
 * PACKAGE_USAGE_STATS is a *special* access, not a runtime permission:
 * it can only be granted in Settings → Apps → Special app access →
 * Usage access → Baloch. [hasAccess] checks via AppOpsManager; when it
 * returns false the brain replies with those exact steps instead of
 * failing.
 */
object UsageHelper {

    data class AppEntry(val label: String, val packageName: String, val minutes: Long)
    data class UsageReport(val totalMinutes: Long, val top: List<AppEntry>)

    fun hasAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Opens the exact Settings page where usage access is granted. */
    fun openAccessSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Today's usage, midnight → now. When [filter] is non-blank only apps
     * whose label or package contains it (case-insensitive) are returned.
     * Returns null when access was revoked mid-call.
     */
    fun query(context: Context, filter: String?): UsageReport? {
        if (!hasAccess(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        val stats = try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
        } catch (_: Exception) {
            return null
        } ?: return null

        val q = filter?.trim()?.lowercase()
        val entries = stats
            .filter { it.totalTimeInForeground > 0 }
            .mapNotNull { s ->
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(s.packageName, 0)).toString()
                } catch (_: Exception) {
                    s.packageName.substringAfterLast('.')
                }
                if (q != null && !label.lowercase().contains(q) && !s.packageName.lowercase().contains(q)) {
                    null
                } else {
                    AppEntry(label, s.packageName, s.totalTimeInForeground / 60_000L)
                }
            }
            .sortedByDescending { it.minutes }
        val total = stats.sumOf { it.totalTimeInForeground } / 60_000L
        return UsageReport(total, entries.take(5))
    }

    /** 125 → "2h 5m" / "2 گھنٹے 5 منٹ" / "2 घंटे 5 मिनट". */
    fun formatMinutes(minutes: Long, lang: BalochLanguage): String {
        val h = minutes / 60
        val m = minutes % 60
        return when (lang) {
            BalochLanguage.ENGLISH -> if (h > 0) "${h}h ${m}m" else "${m}m"
            BalochLanguage.URDU -> buildString {
                if (h > 0) append("$h گھنٹے ")
                append("$m منٹ")
            }.trim()
            BalochLanguage.HINDI -> buildString {
                if (h > 0) append("$h घंटे ")
                append("$m मिनट")
            }.trim()
        }
    }

    fun header(totalMinutes: Long, lang: BalochLanguage): String =
        L3(
            "📱 Screen time today: ${formatMinutes(totalMinutes, lang)}, sir. Here's where it went —",
            "📱 آج کا اسکرین ٹائم: ${formatMinutes(totalMinutes, lang)}، سر۔ یہ رہا حساب —",
            "📱 आज का स्क्रीन टाइम: ${formatMinutes(totalMinutes, lang)}, सर। यह रहा हिसाब —",
        ).pick(lang)
}
