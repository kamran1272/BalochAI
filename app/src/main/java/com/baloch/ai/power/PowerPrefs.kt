package com.baloch.ai.power

import android.content.Context
import android.content.SharedPreferences
import com.baloch.ai.brain.BalochLanguage

/**
 * Single backing store for ALL ten dormant power features.
 *
 * SETTINGS LAW: every feature has a WORKING toggle + tuning — zero dead
 * switches. This object is the ground truth; the settings UI rows
 * (ui/PowerSections.kt) and the voice layer (PowerIntents / PowerToggles)
 * both read and write through it, so a toggle flipped by voice and a
 * toggle flipped on screen can never disagree.
 *
 * Every feature is OFF by default (Kamran's order — dormant until he
 * wakes them).
 */
object PowerPrefs {

    private const val FILE = "baloch_power"

    // -- master toggles ---------------------------------------------------
    const val DRIVING = "driving_on"
    const val RAMADAN = "ramadan_on"
    const val CRICKET = "cricket_on"
    const val CURRENCY = "currency_on"
    const val PETROL = "petrol_on"
    const val WORD = "word_on"
    const val RECORDER = "recorder_on"
    const val CHARGE = "charge_on"
    const val RAIN = "rain_on"
    const val APPLOCK = "applock_on"

    // -- tuning -----------------------------------------------------------
    const val DRIVING_AUTO_OFF_MIN = "driving_auto_off_min"   // 0 = never
    const val DRIVING_AUTO_REPLY = "driving_auto_reply"
    const val RAMADAN_CITY = "ramadan_city"
    const val CRICKET_MATCH_DAYS_ONLY = "cricket_match_days_only"
    const val CURRENCY_THRESHOLD = "currency_threshold"
    const val RECORDER_TRANSCRIBE = "recorder_transcribe"

    // -- runtime state (written by workers, shown in UI) -------------------
    const val CURRENCY_LAST_RATE = "currency_last_rate"
    const val CURRENCY_ALERTED_ABOVE = "currency_alerted_above"
    const val CURRENCY_LAST_CHECK = "currency_last_check"
    const val PETROL_PRICE = "petrol_price"
    const val PETROL_UPDATED_AT = "petrol_updated_at"
    const val RAMADAN_SEHRI = "ramadan_sehri"                 // "HH:MM" today
    const val RAMADAN_IFTARI = "ramadan_iftari"               // "HH:MM" today
    const val RAMADAN_TIMINGS_DATE = "ramadan_timings_date"   // yyyy-MM-dd
    const val CRICKET_LAST_ERROR = "cricket_last_error"
    const val CRICKET_SEEN_LINKS = "cricket_seen_links"       // JSON array
    const val RAIN_LAST_CHECK = "rain_last_check"
    const val RAIN_LAST_PCT = "rain_last_pct"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isOn(context: Context, key: String): Boolean =
        prefs(context).getBoolean(key, false)

    fun setOn(context: Context, key: String, on: Boolean) {
        prefs(context).edit().putBoolean(key, on).apply()
    }

    fun getString(context: Context, key: String, def: String): String =
        prefs(context).getString(key, def) ?: def

    fun setString(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
    }

    fun getInt(context: Context, key: String, def: Int): Int =
        prefs(context).getInt(key, def)

    fun setInt(context: Context, key: String, value: Int) {
        prefs(context).edit().putInt(key, value).apply()
    }

    fun getDouble(context: Context, key: String, def: Double): Double =
        prefs(context).getString(key, null)?.toDoubleOrNull() ?: def

    fun setDouble(context: Context, key: String, value: Double) {
        prefs(context).edit().putString(key, value.toString()).apply()
    }

    fun hasDouble(context: Context, key: String): Boolean =
        prefs(context).getString(key, null)?.toDoubleOrNull() != null

    fun getLong(context: Context, key: String, def: Long): Long =
        prefs(context).getLong(key, def)

    fun setLong(context: Context, key: String, value: Long) {
        prefs(context).edit().putLong(key, value).apply()
    }

    /** Active UI language — same "baloch_prefs"/"language" key the rest of the app uses. */
    fun activeLang(context: Context): BalochLanguage =
        BalochLanguage.fromCode(
            context.getSharedPreferences("baloch_prefs", Context.MODE_PRIVATE)
                .getString("language", BalochLanguage.ENGLISH.code)
                ?: BalochLanguage.ENGLISH.code
        )
}
