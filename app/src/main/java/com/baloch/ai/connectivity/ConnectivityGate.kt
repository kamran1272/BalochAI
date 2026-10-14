package com.baloch.ai.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Central connectivity gate for Mobile Baloch's OFFLINE-FIRST design.
 *
 * EVERY online-only feature calls [isOnline] first (web search, weather,
 * live currency rates, number deep-lookup, one-time model downloads).
 * Offline-capable features never touch this gate at all. When offline,
 * features degrade honestly — run their offline behaviour, use cached
 * data labelled "cached", skip with one spoken line, or queue a
 * network-constrained retry (WorkManager) — never a crash, a hang, or a
 * dead error screen.
 *
 * [isOnline] requires a VALIDATED, internet-capable network (not just a
 * connected Wi-Fi behind a captive portal). It is fast and allocation-light;
 * safe to call on the hot path.
 */
object ConnectivityGate {

    /**
     * True when the device has a validated, internet-capable network.
     * Never throws — false on any doubt (no service, no network, any
     * SecurityException from a restricted context, ...).
     */
    fun isOnline(ctx: Context): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                // minSdk 26 means this branch never runs; kept defensively.
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.isConnectedOrConnecting == true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Same as [isOnline], named for call sites that read like a guard:
     * `if (!requireOnline(ctx)) { return } // offline path`.
     */
    fun requireOnline(ctx: Context): Boolean = isOnline(ctx)

    /**
     * Runs [online] when connected, [offline] otherwise. The single funnel
     * for features that have both an online and an offline behaviour.
     */
    suspend fun <T> withOnline(
        ctx: Context,
        online: suspend () -> T,
        offline: suspend () -> T,
    ): T = if (isOnline(ctx)) online() else offline()
}
