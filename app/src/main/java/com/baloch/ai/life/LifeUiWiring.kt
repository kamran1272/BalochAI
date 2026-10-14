package com.baloch.ai.life

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.baloch.ai.ui.CopilotUiState
import com.baloch.ai.ui.ExpenseUiState
import com.baloch.ai.ui.FocusUiState
import com.baloch.ai.ui.GeoRemindersUiState
import com.baloch.ai.ui.PrayerAlertsUiState
import com.baloch.ai.ui.SosUiState
import com.baloch.ai.ui.TranslatorUiState
import java.util.Calendar

/**
 * Aggregated UI state for the 8 life features' Settings sections.
 * Loaded by the parent ViewModel via [loadLifeUiState]; the Settings
 * screen fans it out to the individual section composables.
 */
data class LifeUiState(
    val prayer: PrayerAlertsUiState,
    val copilot: CopilotUiState,
    val expense: ExpenseUiState,
    val focus: FocusUiState,
    val geo: GeoRemindersUiState,
    val translator: TranslatorUiState,
    val sos: SosUiState,
)

/** One callback per life-feature control — the parent writes through the real store. */
interface LifeActions {
    fun onPrayerAlertsChanged(on: Boolean)
    fun onOpenQibla()
    fun onCopilotNudgesChanged(on: Boolean)
    fun onOpenClients()
    fun onExpenseCurrencyChanged(currency: String)
    fun onOpenExpenseLog()
    fun onFocusMinChanged(minutes: Int)
    fun onBreakMinChanged(minutes: Int)
    fun onStartFocusNow()
    fun onGeoRemindersChanged(on: Boolean)
    fun onGrantLocation()
    fun onOpenAddGeoReminder()
    fun onTranslatorAutoDownloadChanged(on: Boolean)
    fun onDownloadTranslatorNow()
    fun onSaveSosIce(number: String)
    fun onGrantSms()
}

fun loadLifeUiState(context: Context): LifeUiState {
    val lifePrefs = context.getSharedPreferences("baloch_life", Context.MODE_PRIVATE)

    // Prayer
    val prayerOn = lifePrefs.getBoolean("prayer_alerts", false)
    val nextPrayerText = try {
        val timings = PrayerTimesApi.todaysTimings(context)
        val next = PrayerTimesApi.nextPrayer(timings, System.currentTimeMillis())
        next?.let { PrayerTimesApi.countdownText(it, System.currentTimeMillis(), com.baloch.ai.brain.BalochLanguage.ENGLISH) }
            ?: "—"
    } catch (_: Exception) { "—" }

    // Freelance copilot
    val clients = try { ClientStore(context).all() } catch (_: Exception) { emptyList() }
    val stale = try { staleClients(clients, System.currentTimeMillis()).size } catch (_: Exception) { 0 }

    // Expenses
    val currency = lifePrefs.getString("expense_currency", "PKR") ?: "PKR"
    val monthlyText = try {
        val now = Calendar.getInstance()
        val list = expensesForMonth(
            ExpenseStore(context).all(),
            now.get(Calendar.YEAR), now.get(Calendar.MONTH),
        )
        val total = list.sumOf { it.amount }
        "Rs ${"%,.0f".format(total)} · ${list.size} entries"
    } catch (_: Exception) { "—" }

    // Focus
    val focusMin = lifePrefs.getInt("focus_min", 25)
    val breakMin = lifePrefs.getInt("break_min", 5)
    val sessionsDone = try { FocusHistoryStore(context).all().size } catch (_: Exception) { 0 }

    // Geo reminders
    val geoOn = lifePrefs.getBoolean("geo_reminders", false)
    val geoCount = try { GeoReminderStore(context).all().size } catch (_: Exception) { 0 }
    val locGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    // Translator
    val autoDl = lifePrefs.getBoolean("translator_auto_dl", true)

    // SOS
    val ice = com.baloch.ai.engine.pro.EmergencyStore.get(context).orEmpty()
    val smsGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.SEND_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    return LifeUiState(
        prayer = PrayerAlertsUiState(prayerOn, nextPrayerText),
        copilot = CopilotUiState(
            nudgesEnabled = lifePrefs.getBoolean("copilot_nudges", true),
            clientCount = clients.size,
            staleCount = stale,
        ),
        expense = ExpenseUiState(currency, monthlyText),
        focus = FocusUiState(focusMin, breakMin, sessionsDone),
        geo = GeoRemindersUiState(geoOn, geoCount, locGranted),
        translator = TranslatorUiState(autoDl, "EN↔UR", false),
        sos = SosUiState(ice, smsGranted),
    )
}
