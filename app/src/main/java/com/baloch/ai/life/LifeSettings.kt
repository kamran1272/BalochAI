package com.baloch.ai.life

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * LIFE SETTINGS LAW — every life feature has a WORKING toggle stored
 * here. These are real persisted prefs; the ui/LifeSections.kt rows
 * read and write them through this class, so no switch is ever dead.
 */
private val Context.lifeDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "baloch_life")

class LifeSettings(private val context: Context) {

    companion object {
        private val KEY_PRAYER_ALERTS = booleanPreferencesKey("prayer_alerts")
        private val KEY_COPILOT_NUDGES = booleanPreferencesKey("copilot_nudges")
        private val KEY_EXPENSE_CURRENCY = stringPreferencesKey("expense_currency")
        private val KEY_FOCUS_MIN = intPreferencesKey("focus_min")
        private val KEY_BREAK_MIN = intPreferencesKey("break_min")
        private val KEY_GEO_ENABLED = booleanPreferencesKey("geo_enabled")
        private val KEY_TRANSLATOR_AUTO_DL = booleanPreferencesKey("translator_auto_download")

        const val CURRENCY_PKR = "PKR"
        const val CURRENCY_USD = "USD"
        const val CURRENCY_INR = "INR"
        val CURRENCIES = listOf(CURRENCY_PKR, CURRENCY_USD, CURRENCY_INR)

        const val DEFAULT_FOCUS_MIN = 25
        const val DEFAULT_BREAK_MIN = 5
    }

    // ---------------- prayer ----------------
    val prayerAlerts: Flow<Boolean> =
        context.lifeDataStore.data.map { it[KEY_PRAYER_ALERTS] ?: false }
    suspend fun setPrayerAlerts(v: Boolean) =
        context.lifeDataStore.edit { it[KEY_PRAYER_ALERTS] = v }
    suspend fun prayerAlertsNow(): Boolean = prayerAlerts.first()

    // ---------------- freelance copilot ----------------
    val copilotNudges: Flow<Boolean> =
        context.lifeDataStore.data.map { it[KEY_COPILOT_NUDGES] ?: true }
    suspend fun setCopilotNudges(v: Boolean) =
        context.lifeDataStore.edit { it[KEY_COPILOT_NUDGES] = v }
    suspend fun copilotNudgesNow(): Boolean = copilotNudges.first()

    // ---------------- expenses ----------------
    val expenseCurrency: Flow<String> =
        context.lifeDataStore.data.map { it[KEY_EXPENSE_CURRENCY] ?: CURRENCY_PKR }
    suspend fun setExpenseCurrency(code: String) =
        context.lifeDataStore.edit { it[KEY_EXPENSE_CURRENCY] = code }
    suspend fun expenseCurrencyNow(): String = expenseCurrency.first()

    /** Trilingual currency formatting, PKR default. */
    fun formatAmount(amount: Double, currency: String): String {
        val rounded = if (amount % 1.0 == 0.0) amount.toLong().toString()
        else "%.2f".format(amount)
        return when (currency) {
            CURRENCY_USD -> "\$$rounded"
            CURRENCY_INR -> "₹$rounded"
            else -> "Rs $rounded"
        }
    }

    // ---------------- focus mode ----------------
    val focusMin: Flow<Int> =
        context.lifeDataStore.data.map { it[KEY_FOCUS_MIN] ?: DEFAULT_FOCUS_MIN }
    suspend fun setFocusMin(v: Int) =
        context.lifeDataStore.edit { it[KEY_FOCUS_MIN] = v.coerceIn(5, 120) }
    val breakMin: Flow<Int> =
        context.lifeDataStore.data.map { it[KEY_BREAK_MIN] ?: DEFAULT_BREAK_MIN }
    suspend fun setBreakMin(v: Int) =
        context.lifeDataStore.edit { it[KEY_BREAK_MIN] = v.coerceIn(1, 60) }

    // ---------------- location reminders ----------------
    val geoEnabled: Flow<Boolean> =
        context.lifeDataStore.data.map { it[KEY_GEO_ENABLED] ?: false }
    suspend fun setGeoEnabled(v: Boolean) =
        context.lifeDataStore.edit { it[KEY_GEO_ENABLED] = v }
    suspend fun geoEnabledNow(): Boolean = geoEnabled.first()

    // ---------------- translator ----------------
    val translatorAutoDownload: Flow<Boolean> =
        context.lifeDataStore.data.map { it[KEY_TRANSLATOR_AUTO_DL] ?: false }
    suspend fun setTranslatorAutoDownload(v: Boolean) =
        context.lifeDataStore.edit { it[KEY_TRANSLATOR_AUTO_DL] = v }
    suspend fun translatorAutoDownloadNow(): Boolean = translatorAutoDownload.first()
}
