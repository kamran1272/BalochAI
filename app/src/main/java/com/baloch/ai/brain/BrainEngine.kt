package com.baloch.ai.brain

/**
 * Everything Baloch can DO on the device. The brain only *decides*;
 * [com.baloch.ai.engine.DeviceActions] executes.
 *
 * Risk tiers: SAFE actions run instantly. RISKY actions (call, SMS)
 * set [needsConfirmation] — the UI shows a dialog and only executes
 * after Kamran taps confirm.
 */
sealed class DeviceAction {
    // ---------- SAFE ----------
    data class OpenApp(val appName: String) : DeviceAction()
    data class SetAlarm(val hour: Int, val minute: Int, val label: String = "") : DeviceAction()
    data class StartTimer(val seconds: Int, val label: String = "") : DeviceAction()
    data class AddReminder(val text: String, val minutesFromNow: Int) : DeviceAction()
    data class ToggleWifi(val on: Boolean) : DeviceAction()
    data class ToggleBluetooth(val on: Boolean) : DeviceAction()
    data class ToggleFlashlight(val on: Boolean) : DeviceAction()
    data class SetVolume(val levelPercent: Int) : DeviceAction()
    data class AdjustVolume(val up: Boolean) : DeviceAction()
    data class SetMuted(val muted: Boolean) : DeviceAction()
    data class SetBrightness(val levelPercent: Int) : DeviceAction()
    /** Applies a named settings toggle — routed to SettingsAgent. SAFE: only flips persisted toggles, never destructive. */
    data class ApplySetting(val key: String, val on: Boolean) : DeviceAction()
    data class WebSearch(val query: String) : DeviceAction()
    data class AddNote(val text: String) : DeviceAction()
    data class RememberFact(val fact: String) : DeviceAction()
    object ListNotes : DeviceAction()
    object ReadNotifications : DeviceAction()
    object GetTime : DeviceAction()
    object GetBattery : DeviceAction()
    object GetDeviceStats : DeviceAction()
    object TellThought : DeviceAction()

    // ---------- PRO PACK (safe) ----------
    /** Do-not-disturb on/off. Needs "Do Not Disturb access" (special grant). */
    data class SetDnd(val on: Boolean) : DeviceAction()
    /** Lifts DND automatically after [minutes] via AlarmManager. */
    data class DisableDndDelayed(val minutes: Int) : DeviceAction()
    /** Opens WhatsApp with a pre-filled message — SAFE, never auto-sends. */
    data class WhatsAppDraft(val name: String, val number: String, val text: String) : DeviceAction()

    // ---------- LIFE FEATURES (safe) ----------
    /** Shows today's prayer times + next-prayer countdown. */
    object ShowPrayerTimes : DeviceAction()
    /** Opens the Qibla compass screen. */
    object ShowQiblaCompass : DeviceAction()
    /** Logs a voice-captured expense. */
    data class LogExpense(val amount: Double, val note: String, val category: String) : DeviceAction()
    /** Shows the monthly hisaab. */
    object ShowExpenseSummary : DeviceAction()
    /** Adds a client to the freelance copilot tracker. */
    data class AddClient(val name: String) : DeviceAction()
    /** Shows the client board / freelance summary. */
    object ShowClients : DeviceAction()
    /** Shows [count] random video ideas. */
    data class ShowVideoIdeas(val count: Int) : DeviceAction()
    /** Builds a script outline for [topic]. */
    data class ShowScriptOutline(val topic: String) : DeviceAction()
    /** Runs a web trend scan for content ideas. */
    object TrendScan : DeviceAction()
    /** Starts a Pomodoro focus session. */
    data class StartFocus(val minutes: Int) : DeviceAction()
    /** Adds a location reminder ("yaad dilana") — needs a pinned place. */
    data class AddGeoReminder(val message: String, val label: String) : DeviceAction()
    /** Translates [text] into [targetLang] ("ur"/"hi"/"en"). */
    data class TranslateText(val text: String, val targetLang: String) : DeviceAction()
    /** Downloads the ML Kit translation model for [pairName] (e.g. "en->ur"). */
    data class DownloadTranslationModel(val pairName: String) : DeviceAction()

    // ---------- RISKY (need user confirmation) ----------
    data class CallContact(val name: String, val number: String) : DeviceAction()
    data class SendSms(val name: String, val number: String, val message: String) : DeviceAction()
    /** Emergency call to the ICE contact. Always confirmed first. */
    data class EmergencyCall(val name: String, val number: String) : DeviceAction()
    /** SOS: sends the location SMS to the ICE contact. Always confirmed first. */
    data class SendSosAlert(val number: String, val locationLink: String) : DeviceAction()

    // ---------- MODE (handled by the ViewModel via ModeManager) ----------
    data class SetMode(val jarvis: Boolean) : DeviceAction()

    val needsConfirmation: Boolean
        get() = this is CallContact || this is SendSms || this is EmergencyCall ||
            this is SendSosAlert
}

/** What the brain returns for one user input. */
data class BrainResponse(
    /** What Baloch says (already in the active language). */
    val text: String,
    /** Device actions to run, in order. */
    val actions: List<DeviceAction> = emptyList(),
)

/** Pluggable brain. Swap [RuleBasedBrain] for [LlmBrain] when ready. */
interface BrainEngine {
    suspend fun respond(input: String, context: DeviceContext): BrainResponse
}
