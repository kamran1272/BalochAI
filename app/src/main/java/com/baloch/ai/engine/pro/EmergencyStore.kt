package com.baloch.ai.engine.pro

import android.content.Context

/**
 * The ICE (In Case of Emergency) contact number.
 *
 * Set from the Settings screen (see ui/ProSettingsRows.kt → IceContactRow).
 * Stored in plain SharedPreferences — it's Kamran's own number for his own
 * phone, and the emergency call itself always asks for confirmation first.
 */
object EmergencyStore {

    private const val PREFS = "baloch_pro"
    private const val KEY_ICE_NUMBER = "ice_number"

    fun get(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ICE_NUMBER, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    fun set(context: Context, number: String) {
        val digits = number.trim().replace(Regex("[^+\\d]"), "")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ICE_NUMBER, digits)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ICE_NUMBER)
            .apply()
    }
}
