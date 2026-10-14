package com.baloch.ai.settings

import android.content.Context

/**
 * Protection mode (Fortress Privacy): when ON (the default), FLAG_SECURE
 * blocks screenshots and screen-recording of Baloch and blanks the system
 * recents thumbnail. When OFF, the screen can be captured — for example to
 * report an issue.
 *
 * The toggle lives in Settings → Privacy & Permissions. MainActivity reads
 * this in onCreate AND onResume, so flipping the switch takes effect the
 * moment the user returns — no restart needed.
 *
 * Stored in the shared "baloch_settings" prefs so both the UI layer and
 * MainActivity read the same value.
 */
object ProtectionMode {
    private const val PREFS_NAME = "baloch_settings"
    private const val KEY_PROTECTION_MODE = "protection_mode"

    /** Default true — the historical behaviour (screenshots blocked). */
    fun isOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PROTECTION_MODE, true)

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PROTECTION_MODE, on).apply()
    }
}
