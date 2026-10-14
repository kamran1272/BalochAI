package com.baloch.ai.agents

import android.content.Context
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.DeviceAction
import com.baloch.ai.brain.L3
import com.baloch.ai.engine.pro.EmergencyStore
import com.baloch.ai.gesture.GestureControlService
import com.baloch.ai.gesture.SensorGestureService
import com.baloch.ai.life.FocusManager
import com.baloch.ai.life.LifeSettings
import com.baloch.ai.power.CricketAlerts
import com.baloch.ai.power.DrivingMode
import com.baloch.ai.power.PowerPrefs
import com.baloch.ai.power.RamadanMode
import com.baloch.ai.service.BalochListenerService
import com.baloch.ai.settings.BalochMode
import com.baloch.ai.settings.LanguageManager
import com.baloch.ai.settings.ModeManager
import com.baloch.ai.voice.PinManager
import com.baloch.ai.voice.VoiceIdentityManager
import com.baloch.ai.voice.VoiceLockManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * SETTINGS AGENT — "bolun setting on kar do to kare."
 *
 * Every Baloch setting is voice-toggleable through this agent. It writes to
 * the REAL stores (the same ones SettingsScreen uses), so a toggle flipped
 * by voice and a toggle flipped on screen can never disagree.
 *
 * Two entry points:
 *  1. [matchSettingsCommand] — parses a single utterance (ONE part; the
 *     Orchestrator already splits compounds on conjunctions) into a
 *     [SettingsCmd]. Trilingual incl. Roman Urdu, fuzzy name matching.
 *  2. [SettingsAgent] — a [SpecialistAgent] handling
 *     [DeviceAction.ApplySetting]; the parent converts a [SettingsCmd] with
 *     [SettingsCmd.toDeviceAction].
 *
 * SECURITY GATE: voice-lock enable/disable, PIN changes, app lock,
 * biometric lock and forget-everything are NEVER executed by voice. They
 * return ok=false with a pointer to Settings → Security. This gate is
 * enforced at EXECUTION time (not match time) so even a future UI path
 * that consults the registry gets the same refusal.
 */
sealed interface SettingsCmd {
    data class ToggleSetting(val key: String, val on: Boolean) : SettingsCmd
    data class QuerySettings(val topic: String) : SettingsCmd
    data object OpenSettings : SettingsCmd

    /** Bridge into the brain's action model. */
    fun toDeviceAction(): DeviceAction = when (this) {
        is ToggleSetting -> DeviceAction.ApplySetting(key, on)
        is QuerySettings -> DeviceAction.ApplySetting("query:$topic", true)
        is OpenSettings -> DeviceAction.ApplySetting("open", true)
    }
}

// ------------------------------------------------------------------
// Setting registry — single source of truth for toggles.
// Both this agent and the future Settings UI read from here.
// ------------------------------------------------------------------

enum class SettingCategory { VOICE, BEHAVIOUR, GESTURES, AUTOMATION, ALERTS, POWER, SECURITY }

/**
 * One toggleable setting.
 *
 * [get]/[set] take a plain [Context] and hit the same store the Settings
 * UI uses. Suspend-backed stores (DataStore) are wrapped in runBlocking —
 * they are tiny reads/writes, never the speak hot path.
 */
data class SettingEntry(
    val key: String,
    val names: L3,
    val category: SettingCategory,
    /** Lowercase match phrases (already normalised the way the matcher normalises). */
    val aliases: List<String>,
    /** Gated settings match for reporting but refuse execution. */
    val gated: Boolean = false,
    val get: (Context) -> Boolean,
    val set: (Context, Boolean) -> Unit,
)

private fun balochSettings(ctx: Context, key: String, def: Boolean): Boolean =
    ctx.getSharedPreferences("baloch_settings", Context.MODE_PRIVATE).getBoolean(key, def)

private fun setBalochSetting(ctx: Context, key: String, on: Boolean) =
    ctx.getSharedPreferences("baloch_settings", Context.MODE_PRIVATE)
        .edit().putBoolean(key, on).apply()

object SettingRegistry {

    val all: List<SettingEntry> = listOf(
        // ---------- VOICE ----------
        SettingEntry(
            key = "language_urdu",
            names = L3("Urdu language", "اردو زبان", "उर्दू भाषा"),
            category = SettingCategory.VOICE,
            aliases = listOf("urdu", "اردو"),
            get = { LanguageManager(it).current() == BalochLanguage.URDU },
            set = { ctx, on -> if (on) LanguageManager(ctx).setLanguage(BalochLanguage.URDU) },
        ),
        SettingEntry(
            key = "language_hindi",
            names = L3("Hindi language", "ہندی زبان", "हिन्दी भाषा"),
            category = SettingCategory.VOICE,
            aliases = listOf("hindi", "ہندی", "हिंदी"),
            get = { LanguageManager(it).current() == BalochLanguage.HINDI },
            set = { ctx, on -> if (on) LanguageManager(ctx).setLanguage(BalochLanguage.HINDI) },
        ),
        SettingEntry(
            key = "language_english",
            names = L3("English language", "انگریزی زبان", "अंग्रेज़ी भाषा"),
            category = SettingCategory.VOICE,
            aliases = listOf("english", "angrezi", "انگریزی"),
            get = { LanguageManager(it).current() == BalochLanguage.ENGLISH },
            set = { ctx, on -> if (on) LanguageManager(ctx).setLanguage(BalochLanguage.ENGLISH) },
        ),
        SettingEntry(
            key = "quiet_voice_mode",
            names = L3("Quiet voice mode", "ہلکی آواز موڈ", "धीमी आवाज़ मोड"),
            category = SettingCategory.VOICE,
            aliases = listOf("quiet voice mode", "quiet mode", "whisper mode", "whisper detection"),
            get = {
                it.getSharedPreferences("quiet_voice_mode", Context.MODE_PRIVATE)
                    .getBoolean("enabled", true) // default true = SensitiveAudioCapture default
            },
            // Same store+key as BalochViewModel.onQuietVoiceModeChange.
            set = { ctx, on ->
                ctx.getSharedPreferences("quiet_voice_mode", Context.MODE_PRIVATE)
                    .edit().putBoolean("enabled", on).apply()
            },
        ),
        SettingEntry(
            key = "always_listening",
            names = L3("Always listening", "ہمیشہ سننا", "हमेशा सुनना"),
            category = SettingCategory.VOICE,
            aliases = listOf("always listening", "hamesha suno", "hamesha sunna", "sunte raho"),
            get = { BalochListenerService.isAlwaysListening(it) },
            set = { ctx, on -> BalochListenerService.setAlwaysListening(ctx, on) },
        ),

        // ---------- BEHAVIOUR ----------
        SettingEntry(
            key = "jarvis_mode",
            names = L3("JARVIS mode", "جاروس موڈ", "जार्विस मोड"),
            category = SettingCategory.BEHAVIOUR,
            aliases = listOf("jarvis mode", "jarvis", "manual mode"),
            get = { runBlocking { ModeManager(it).isJarvis() } },
            // Same store the Settings UI writes via BalochViewModel.onDefaultModeChange.
            set = { ctx, on ->
                runBlocking {
                    ModeManager(ctx).setMode(if (on) BalochMode.JARVIS else BalochMode.MANUAL)
                }
            },
        ),
        SettingEntry(
            key = "checkins",
            names = L3("Companion check-ins", "ساتھی چیک اِن", "साथी चेक-इन"),
            category = SettingCategory.BEHAVIOUR,
            aliases = listOf("check in", "checkin", "khairiyat", "haal chaal", "haalchal"),
            get = {
                it.getSharedPreferences("baloch_companion", Context.MODE_PRIVATE)
                    .getString("checkin_level", "normal") != "off"
            },
            // Same store+key as BalochViewModel.onCheckInFrequencyChange.
            set = { ctx, on ->
                ctx.getSharedPreferences("baloch_companion", Context.MODE_PRIVATE)
                    .edit().putString("checkin_level", if (on) "normal" else "off").apply()
            },
        ),

        // ---------- GESTURES ----------
        SettingEntry(
            key = "gesture_control",
            names = L3("Gesture control (camera)", "اشارہ کنٹرول (کیمرہ)", "जेस्चर कंट्रोल (कैमरा)"),
            category = SettingCategory.GESTURES,
            aliases = listOf("gesture control", "camera gesture", "hand gesture", "haath gesture", "ishara control"),
            get = { GestureControlService.isMasterEnabled(it) },
            set = { ctx, on -> GestureControlService.setMasterEnabled(ctx, on) },
        ),
        SettingEntry(
            key = "sensor_gestures",
            names = L3("Sensor gestures", "سینسر اشارے", "सेंसर जेस्चर"),
            category = SettingCategory.GESTURES,
            aliases = listOf("sensor gesture"),
            get = { SensorGestureService.isEnabled(it) },
            set = { ctx, on -> SensorGestureService.setEnabled(ctx, on) },
        ),
        SettingEntry(
            key = "sensor_wave",
            names = L3("Wave gesture", "ہاتھ ہلانے کا اشارہ", "वेव जेस्चर"),
            category = SettingCategory.GESTURES,
            aliases = listOf("wave gesture"),
            get = { SensorGestureService.isGestureOn(it, SensorGestureService.KEY_WAVE, true) },
            set = { ctx, on -> SensorGestureService.setGestureOn(ctx, SensorGestureService.KEY_WAVE, on) },
        ),
        SettingEntry(
            key = "sensor_flip",
            names = L3("Flip gesture", "الٹنے کا اشارہ", "फ्लिप जेस्चर"),
            category = SettingCategory.GESTURES,
            aliases = listOf("flip gesture"),
            get = { SensorGestureService.isGestureOn(it, SensorGestureService.KEY_FLIP, true) },
            set = { ctx, on -> SensorGestureService.setGestureOn(ctx, SensorGestureService.KEY_FLIP, on) },
        ),
        SettingEntry(
            key = "sensor_shake",
            names = L3("Shake gesture", "ہلانے کا اشارہ", "शेक जेस्चर"),
            category = SettingCategory.GESTURES,
            aliases = listOf("shake gesture"),
            get = { SensorGestureService.isGestureOn(it, SensorGestureService.KEY_SHAKE, true) },
            set = { ctx, on -> SensorGestureService.setGestureOn(ctx, SensorGestureService.KEY_SHAKE, on) },
        ),
        SettingEntry(
            key = "sensor_pickup",
            names = L3("Pick-up to wake", "اٹھانے پر جاگنا", "उठाने पर जगना"),
            category = SettingCategory.GESTURES,
            aliases = listOf("pick up gesture", "pickup gesture", "pick up to wake"),
            get = { SensorGestureService.isGestureOn(it, SensorGestureService.KEY_PICK_UP, false) },
            set = { ctx, on -> SensorGestureService.setGestureOn(ctx, SensorGestureService.KEY_PICK_UP, on) },
        ),

        // ---------- AUTOMATION ----------
        SettingEntry(
            key = "battery_guardian",
            names = L3("Battery guardian", "بیٹری محافظ", "बैटरी गार्जियन"),
            category = SettingCategory.AUTOMATION,
            aliases = listOf("battery guardian", "battery alert"),
            get = { balochSettings(it, "battery_guardian_on", true) }, // default true = worker default
            set = { ctx, on -> setBalochSetting(ctx, "battery_guardian_on", on) },
        ),
        SettingEntry(
            key = "prayer_alerts",
            names = L3("Prayer alerts", "نماز الرٹس", "नमाज़ अलर्ट"),
            category = SettingCategory.AUTOMATION,
            aliases = listOf("prayer alert", "namaz alert", "namaz reminder", "azaan alert"),
            get = { runBlocking { LifeSettings(it).prayerAlertsNow() } },
            set = { ctx, on -> runBlocking { LifeSettings(ctx).setPrayerAlerts(on) } },
        ),
        SettingEntry(
            key = "copilot_nudges",
            names = L3("Client nudges", "کلائنٹ یاددہانی", "क्लाइंट रिमाइंडर"),
            category = SettingCategory.AUTOMATION,
            aliases = listOf("copilot nudge", "client nudge", "freelance nudge"),
            get = { runBlocking { LifeSettings(it).copilotNudgesNow() } },
            set = { ctx, on -> runBlocking { LifeSettings(ctx).setCopilotNudges(on) } },
        ),
        SettingEntry(
            key = "geo_reminders",
            names = L3("Location reminders", "مقام کی یاددہانی", "लोकेशन रिमाइंडर"),
            category = SettingCategory.AUTOMATION,
            aliases = listOf("location reminder", "geo reminder", "jagah reminder"),
            get = { runBlocking { LifeSettings(it).geoEnabledNow() } },
            set = { ctx, on -> runBlocking { LifeSettings(ctx).setGeoEnabled(on) } },
        ),
        SettingEntry(
            key = "translator_auto_dl",
            names = L3("Translator auto-download", "مترجم خودکار ڈاؤن لوڈ", "अनुवादक ऑटो-डाउनलोड"),
            category = SettingCategory.AUTOMATION,
            aliases = listOf("translator auto download", "translation auto download"),
            get = { runBlocking { LifeSettings(it).translatorAutoDownloadNow() } },
            set = { ctx, on -> runBlocking { LifeSettings(ctx).setTranslatorAutoDownload(on) } },
        ),

        // ---------- ALERTS (power) ----------
        SettingEntry(
            key = "cricket_alerts",
            names = L3("Cricket alerts", "کرکٹ الرٹس", "क्रिकेट अलर्ट"),
            category = SettingCategory.ALERTS,
            aliases = listOf("cricket alert", "cricket"),
            get = { CricketAlerts.isOn(it) },
            set = { ctx, on -> CricketAlerts.setOn(ctx, on) },
        ),
        SettingEntry(
            key = "currency_alerts",
            names = L3("Currency alerts", "کرنسی الرٹس", "करेंसी अलर्ट"),
            category = SettingCategory.ALERTS,
            aliases = listOf("currency alert", "dollar alert", "exchange alert"),
            get = { PowerPrefs.isOn(it, PowerPrefs.CURRENCY) },
            set = { ctx, on -> PowerPrefs.setOn(ctx, PowerPrefs.CURRENCY, on) },
        ),
        SettingEntry(
            key = "petrol_alerts",
            names = L3("Petrol alerts", "پٹرول الرٹس", "पेट्रोल अलर्ट"),
            category = SettingCategory.ALERTS,
            aliases = listOf("petrol alert", "fuel alert"),
            get = { PowerPrefs.isOn(it, PowerPrefs.PETROL) },
            set = { ctx, on -> PowerPrefs.setOn(ctx, PowerPrefs.PETROL, on) },
        ),
        SettingEntry(
            key = "rain_alerts",
            names = L3("Rain alerts", "بارش الرٹس", "बारिश अलर्ट"),
            category = SettingCategory.ALERTS,
            aliases = listOf("rain alert", "barish alert", "mausam alert"),
            get = { PowerPrefs.isOn(it, PowerPrefs.RAIN) },
            set = { ctx, on -> PowerPrefs.setOn(ctx, PowerPrefs.RAIN, on) },
        ),
        SettingEntry(
            key = "charge_alert",
            names = L3("Full-charge alert", "مکمل چارج الرٹ", "फुल-चार्ज अलर्ट"),
            category = SettingCategory.ALERTS,
            aliases = listOf("charge alert", "charging alert", "full charge alert"),
            get = { PowerPrefs.isOn(it, PowerPrefs.CHARGE) },
            set = { ctx, on -> PowerPrefs.setOn(ctx, PowerPrefs.CHARGE, on) },
        ),
        SettingEntry(
            key = "daily_word",
            names = L3("Daily word", "روز کا لفظ", "रोज़ का शब्द"),
            category = SettingCategory.ALERTS,
            aliases = listOf("daily word", "word of the day", "roz ka lafz"),
            get = { PowerPrefs.isOn(it, PowerPrefs.WORD) },
            set = { ctx, on -> PowerPrefs.setOn(ctx, PowerPrefs.WORD, on) },
        ),

        // ---------- POWER ----------
        SettingEntry(
            key = "driving_mode",
            names = L3("Driving mode", "ڈرائیونگ موڈ", "ड्राइविंग मोड"),
            category = SettingCategory.POWER,
            aliases = listOf("driving mode", "drive mode", "driving", "gaari mode"),
            get = { DrivingMode.isOn(it) },
            set = { ctx, on -> DrivingMode.setOn(ctx, on) },
        ),
        SettingEntry(
            key = "ramadan_mode",
            names = L3("Ramadan mode", "رمضان موڈ", "रमज़ान मोड"),
            category = SettingCategory.POWER,
            aliases = listOf("ramadan mode", "ramzan mode", "ramadan", "ramzan", "roza mode"),
            get = { RamadanMode.isOn(it) },
            set = { ctx, on -> RamadanMode.setOn(ctx, on) },
        ),
        SettingEntry(
            key = "meeting_recorder",
            names = L3("Meeting recorder", "میٹنگ ریکارڈر", "मीटिंग रिकॉर्डर"),
            category = SettingCategory.POWER,
            aliases = listOf("meeting recorder", "meeting record", "auto transcribe"),
            get = { PowerPrefs.isOn(it, PowerPrefs.RECORDER) },
            set = { ctx, on -> PowerPrefs.setOn(ctx, PowerPrefs.RECORDER, on) },
        ),

        // ---------- SECURITY (gated: match for reporting, never execute by voice) ----------
        SettingEntry(
            key = "voice_lock",
            names = L3("Voice lock", "وائس لاک", "वॉइस लॉक"),
            category = SettingCategory.SECURITY,
            aliases = listOf("voice lock", "voiceprint", "voice authentication"),
            gated = true,
            get = { VoiceLockManager(it).isEnabled(it) }, // READ-ONLY use
            set = { _, _ -> /* gated — never executed by voice */ },
        ),
        SettingEntry(
            key = "app_lock",
            names = L3("App lock", "ایپ لاک", "ऐप लॉक"),
            category = SettingCategory.SECURITY,
            aliases = listOf("app lock", "applock"),
            gated = true,
            get = { PowerPrefs.isOn(it, PowerPrefs.APPLOCK) },
            set = { _, _ -> /* gated — never executed by voice */ },
        ),
        SettingEntry(
            key = "biometric_lock",
            names = L3("Biometric lock", "بائیومیٹرک لاک", "बायोमेट्रिक लॉक"),
            category = SettingCategory.SECURITY,
            aliases = listOf("biometric lock", "fingerprint lock"),
            gated = true,
            get = { balochSettings(it, "biometric_lock_on", false) },
            set = { _, _ -> /* gated — never executed by voice */ },
        ),
        SettingEntry(
            key = "pin",
            names = L3("PIN code", "پن کوڈ", "पिन कोड"),
            category = SettingCategory.SECURITY,
            aliases = listOf("pin change", "change pin", "pin code", "set pin"),
            gated = true,
            get = { PinManager(it).hasPin() }, // READ-ONLY use
            set = { _, _ -> /* gated — never executed by voice */ },
        ),
        SettingEntry(
            key = "forget_all",
            names = L3("Forget everything", "سب بھول جاؤ", "सब भूल जाओ"),
            category = SettingCategory.SECURITY,
            aliases = listOf("forget all", "forget everything", "sab bhool jao", "memory wipe", "delete memory", "wipe memory"),
            gated = true,
            get = { false },
            set = { _, _ -> /* gated — never executed by voice */ },
        ),
    )

    fun find(key: String): SettingEntry? = all.firstOrNull { it.key == key }

    /** Query topics for [SettingsCmd.QuerySettings]. */
    fun forTopic(topic: String): List<SettingEntry> = when (topic) {
        "alerts" -> all.filter {
            it.category == SettingCategory.ALERTS ||
                it.key == "battery_guardian" || it.key == "prayer_alerts"
        }
        "gestures" -> all.filter { it.category == SettingCategory.GESTURES }
        "modes" -> all.filter { it.key == "jarvis_mode" || it.key == "driving_mode" || it.key == "ramadan_mode" }
        else -> all // "all"
    }
}

// ------------------------------------------------------------------
// Matcher — trilingual incl. Roman Urdu, fuzzy name matching.
// Handles ONE command part (the Orchestrator splits compounds).
// Returns null when the utterance is not a settings command.
// ------------------------------------------------------------------

/** Lowercase, punctuation/symbols → space, collapse whitespace. */
private fun normalizeSettingsInput(s: String): String =
    s.lowercase()
        .replace(Regex("[\\p{P}\\p{S}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

/** Substring hit, or every alias token present (fuzzy for reordered phrasing). */
private fun aliasHit(input: String, alias: String): Boolean {
    if (alias.isBlank()) return false
    if (input.contains(alias)) return true
    val toks = alias.split(' ').filter { it.length > 2 }
    return toks.isNotEmpty() && toks.all { input.contains(it) }
}

private val OFF_WORDS = listOf(
    "band", "bund", "off", "disable", "disabled", "stop", "rok", "roko", "بند", "बंद",
)
private val ON_WORDS = listOf(
    "chalu", "chalo", "enable", "enabled", "start", "shuru", "khol", "kholo", "lagao",
    "laga", "karo", "kar do", "on", "unmute", "فعال", "चालू", "शुरू",
)

private fun wordHit(input: String, w: String): Boolean =
    Regex("\\b${Regex.escape(w)}\\b").containsMatchIn(input)

/**
 * OFF wins over ON ("band kar do" contains "karo"). Null = no on/off
 * intent in the utterance (e.g. bare "driving mode").
 */
private fun detectOnOff(input: String): Boolean? {
    if (OFF_WORDS.any { wordHit(input, it) }) return false
    if (ON_WORDS.any { wordHit(input, it) }) return true
    return null
}

private val SPEECH_VERBS = listOf(
    "baat", "bolo", "bole", "speak", "talk", "language", "bhasha", "zuban",
    "switch", "بات", "बात", "बोल",
)

private val VOLUME_UP_ALIASES = listOf(
    "awaz tez", "awaz barhao", "awaz zyada", "volume up", "vol up",
    "zor se bolo", "zor se", "loud karo", "آواز تیز", "आवाज़ तेज़", "आवाज तेज",
)
private val VOLUME_DOWN_ALIASES = listOf(
    "awaz kam", "awaz dheemi", "awaz halki", "volume down", "vol down",
    "dheere bolo", "halka bolo", "آواز کم", "आवाज़ कम", "आवाज कम",
)
private val VOLUME_SET_RE = Regex("(?:volume|vol|awaz|آواز|आवाज)[^\\d]{0,14}(\\d{1,3})")
private val PCT_RE = Regex("(\\d{1,3})\\s*(?:percent|%|fisad|فیصد|प्रतिशत)")

private val ICE_WORDS = listOf("emergency contact", "ice contact", "emergency number")
private val NUM_RE = Regex("(\\+?\\d[\\d\\s\\-()]{5,}\\d)")

private val QUERY_WORDS = listOf(
    "kaun", "kon ", "konsi", "kaunsi", "konsay", "kya kya", "which", "batao", "dikhao list",
)

fun matchSettingsCommand(input: String): SettingsCmd? {
    val n = normalizeSettingsInput(input)
    if (n.isBlank()) return null

    // 1. Emergency contact + number ("emergency contact set karo 0300...")
    if (ICE_WORDS.any { n.contains(it) }) {
        val digits = NUM_RE.find(n)?.value
            ?.filter { c -> c.isDigit() || c == '+' }
            .orEmpty()
            .replace(Regex("^\\++"), "+")
        return SettingsCmd.ToggleSetting(
            key = if (digits.length >= 7) "ice_contact:$digits" else "ice_contact:",
            on = true,
        )
    }

    // 2. Voice volume → absolute ("volume 80 karo", "awaz 80 percent")
    val hasVolumeWord = listOf("volume", "vol", "awaz", "آواز", "आवाज").any { n.contains(it) }
    if (hasVolumeWord) {
        val num = (VOLUME_SET_RE.find(n) ?: PCT_RE.find(n))
            ?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 100)
        if (num != null) {
            return SettingsCmd.ToggleSetting("volume_set_$num", true)
        }
        // 3. Voice volume → step ("awaz tez karo" / "awaz kam karo")
        if (VOLUME_UP_ALIASES.any { aliasHit(n, it) }) {
            return SettingsCmd.ToggleSetting("volume_up", true)
        }
        if (VOLUME_DOWN_ALIASES.any { aliasHit(n, it) }) {
            return SettingsCmd.ToggleSetting("volume_down", true)
        }
    }

    // 4. Language switch ("urdu mein baat karo", "speak english")
    val langHits = listOf(
        "language_urdu" to listOf("urdu", "اردو"),
        "language_hindi" to listOf("hindi", "ہندی", "हिंदी"),
        "language_english" to listOf("english", "angrezi", "انگریزی"),
    )
    for ((key, names) in langHits) {
        if (names.any { n.contains(it) } && SPEECH_VERBS.any { n.contains(it) }) {
            return SettingsCmd.ToggleSetting(key, true)
        }
    }

    // 5. Status queries ("kaun se alerts on hain?", "kya kya on hai?")
    // "on" is word-boundary matched: "kaun phone kar raha hai" must NOT query.
    val looksLikeQuery = QUERY_WORDS.any { n.contains(it) } &&
        listOf("on", "enabled", "active", "chal", "chalu").any { wordHit(n, it) }
    if (looksLikeQuery) {
        val topic = when {
            n.contains("alert") -> "alerts"
            n.contains("gesture") -> "gestures"
            n.contains("mode") -> "modes"
            else -> "all"
        }
        return SettingsCmd.QuerySettings(topic)
    }

    // 6. Open settings / screens ("settings kholo", "scheduled tasks dikhao")
    if (n.contains("settings") && listOf("khol", "open", "dikhao", "show").any { n.contains(it) }) {
        return SettingsCmd.OpenSettings
    }
    if (n.contains("scheduled task") && listOf("dikhao", "show", "khol", "open", "list").any { n.contains(it) }) {
        return SettingsCmd.ToggleSetting("screen_scheduled", true)
    }
    if ((n.contains("memory screen") || n.contains("memory settings")) &&
        listOf("khol", "open", "dikhao", "show").any { n.contains(it) }
    ) {
        return SettingsCmd.ToggleSetting("screen_memory", true)
    }

    // 7. Focus mode session ("focus mode start karo" / "focus mode band karo")
    if (listOf("focus mode", "focus session", "pomodoro").any { aliasHit(n, it) }) {
        return SettingsCmd.ToggleSetting("focus_mode", detectOnOff(n) ?: true)
    }

    // 8. Registry fuzzy toggles (gated entries match too — the gate fires at execution)
    val entry = SettingRegistry.all.firstOrNull { e ->
        e.aliases.any { aliasHit(n, it) }
    } ?: return null
    val on = when {
        // "manual mode" names the OFF state of jarvis_mode directly.
        entry.key == "jarvis_mode" && n.contains("manual") -> false
        // Gated settings ("forget everything", bare "voice lock"): the named
        // action IS the intent; the security gate fires at execution.
        entry.gated -> detectOnOff(n) ?: true
        else -> detectOnOff(n)
    } ?: return null // named a setting but no on/off intent — not a command
    return SettingsCmd.ToggleSetting(entry.key, on)
}

// ------------------------------------------------------------------
// SettingsAgent
// ------------------------------------------------------------------

class SettingsAgent(private val deps: AgentDeps) : SpecialistAgent {
    override val name = "Settings"
    override val icon = "⚙️"

    override fun canHandle(a: DeviceAction): Boolean = a is DeviceAction.ApplySetting

    override suspend fun execute(t: AgentTask): AgentResult = runGuarded(t) {
        val a = t.action as? DeviceAction.ApplySetting
            ?: return@runGuarded AgentResult(name, false, failedLine(t.lang, "Not a settings action."))
        val ctx = deps.appContext
        val lang = t.lang
        val key = a.key

        // Navigation / pseudo-keys first — the parent wiring maps these to
        // openScreen(...) in the ViewModel; the agent confirms out loud.
        when {
            key == "open" -> return@runGuarded AgentResult(name, true, L3(
                en = "Settings open, sir ⚙️",
                ur = "سیٹنگز کھول دی، سر ⚙️",
                hi = "सेटिंग्स खोल दी, सर ⚙️",
            ).pick(lang))

            key == "screen_scheduled" -> return@runGuarded AgentResult(name, true, L3(
                en = "Scheduled tasks open, sir 📅",
                ur = "شیڈیولڈ ٹاسکس کھول دیے، سر 📅",
                hi = "शेड्यूल्ड टास्क खोल दिए, सर 📅",
            ).pick(lang))

            key == "screen_memory" -> return@runGuarded AgentResult(name, true, L3(
                en = "Memory open, sir 🧠",
                ur = "میموری کھول دی، سر 🧠",
                hi = "मेमोरी खोल दी, सर 🧠",
            ).pick(lang))

            key.startsWith("query:") -> return@runGuarded AgentResult(
                name, true, buildSummary(ctx, key.removePrefix("query:"), lang),
            )

            key.startsWith("ice_contact:") -> {
                val number = key.removePrefix("ice_contact:")
                if (number.length < 7) {
                    return@runGuarded AgentResult(name, false, L3(
                        en = "I didn't catch the number, sir — which number should I set? 📞",
                        ur = "نمبر سمجھ نہیں آیا، سر — کون سا نمبر سیٹ کروں؟ 📞",
                        hi = "नंबर समझ नहीं आया, सर — कौन-सा नंबर सेट करूँ? 📞",
                    ).pick(lang))
                }
                EmergencyStore.set(ctx, number)
                return@runGuarded AgentResult(name, true, L3(
                    en = "Done, sir — emergency contact set to $number 🆘",
                    ur = "ہو گیا، سر — ایمرجنسی رابطہ $number سیٹ کر دیا 🆘",
                    hi = "हो गया, सर — आपातकालीन संपर्क $number सेट कर दिया 🆘",
                ).pick(lang))
            }

            key == "volume_up" || key == "volume_down" -> {
                val vm = VoiceIdentityManager(ctx)
                val cur = vm.getVolumePctBlocking()
                val next = (cur + if (key == "volume_up") 20 else -20).coerceIn(0, 100)
                runBlocking { vm.setVolumePct(next) }
                return@runGuarded AgentResult(name, true, volumeLine(next, lang))
            }

            key.startsWith("volume_set_") -> {
                val pct = key.removePrefix("volume_set_").toIntOrNull()?.coerceIn(0, 100)
                    ?: return@runGuarded AgentResult(name, false, failedLine(t.lang, "Bad volume level."))
                runBlocking { VoiceIdentityManager(ctx).setVolumePct(pct) }
                return@runGuarded AgentResult(name, true, volumeLine(pct, lang))
            }

            key == "focus_mode" -> return@runGuarded focusAction(ctx, a.on, lang)
        }

        // Registry toggles.
        val entry = SettingRegistry.find(key)
            ?: return@runGuarded AgentResult(name, false, L3(
                en = "Couldn't find that setting, sir — shall I open Settings? ⚙️",
                ur = "یہ سیٹنگ نہیں ملی، سر — کیا سیٹنگز کھول دوں؟ ⚙️",
                hi = "यह सेटिंग नहीं मिली, सर — क्या सेटिंग्स खोलूँ? ⚙️",
            ).pick(lang))

        // SECURITY GATE — never by voice. Verify in Settings → Security first.
        if (entry.gated) {
            return@runGuarded AgentResult(name, false, L3(
                en = "Security setting, sir — verify in Settings → Security first 🔐",
                ur = "سیکیورٹی سیٹنگ ہے، سر — پہلے Settings → Security میں تصدیق کریں 🔐",
                hi = "सुरक्षा सेटिंग है, सर — पहले Settings → Security में सत्यापित करें 🔐",
            ).pick(lang))
        }

        entry.set(ctx, a.on)
        val stateWord = if (a.on) L3("on ✅", "آن ✅", "चालू ✅")
        else L3("off ⏸️", "آف ⏸️", "बंद ⏸️")
        AgentResult(name, true, L3(
            en = "Done, sir — ${entry.names.en} ${stateWord.en}",
            ur = "ہو گیا، سر — ${entry.names.ur} ${stateWord.ur}",
            hi = "हो गया, सर — ${entry.names.hi} ${stateWord.hi}",
        ).pick(lang))
    }

    // ---------- helpers ----------

    private fun volumeLine(pct: Int, lang: BalochLanguage): String = L3(
        en = "Done, sir — voice volume $pct% 🔊",
        ur = "ہو گیا، سر — آواز $pct% کر دی 🔊",
        hi = "हो गया, सर — आवाज़ $pct% कर दी 🔊",
    ).pick(lang)

    private fun focusAction(ctx: Context, on: Boolean, lang: BalochLanguage): AgentResult {
        return if (on) {
            val ls = LifeSettings(ctx)
            val focusMin = runBlocking { ls.focusMin.first() }
            val breakMin = runBlocking { ls.breakMin.first() }
            val r = FocusManager.startFocus(ctx, focusMin, breakMin)
            AgentResult(name, true, L3(
                en = "Done, sir — focus mode on 🎯 ${r.focusMin} min focus, ${r.breakMin} min break.",
                ur = "ہو گیا، سر — فوکس موڈ آن 🎯 ${r.focusMin} منٹ فوکس، ${r.breakMin} منٹ وقفہ۔",
                hi = "हो गया, सर — फोकस मोड चालू 🎯 ${r.focusMin} मिनट फोकस, ${r.breakMin} मिनट ब्रेक।",
            ).pick(lang))
        } else {
            FocusManager.stopFocus(ctx)
            AgentResult(name, true, L3(
                en = "Done, sir — focus mode off 🎯",
                ur = "ہو گیا، سر — فوکس موڈ آف 🎯",
                hi = "हो गया, सर — फोकस मोड बंद 🎯",
            ).pick(lang))
        }
    }

    /** Spoken summary of what's enabled for a query topic. */
    private fun buildSummary(ctx: Context, topic: String, lang: BalochLanguage): String {
        val entries = SettingRegistry.forTopic(topic)
        val on = entries.filter { runCatching { it.get(ctx) }.getOrDefault(false) }
        val off = entries.size - on.size
        val topicWord = when (topic) {
            "alerts" -> L3("alerts", "الرٹس", "अलर्ट").pick(lang)
            "gestures" -> L3("gestures", "اشارے", "जेस्चर").pick(lang)
            "modes" -> L3("modes", "موڈز", "मोड").pick(lang)
            else -> L3("settings", "سیٹنگز", "सेटिंग्स").pick(lang)
        }
        if (on.isEmpty()) {
            return L3(
                en = "All $topicWord are off, sir — want me to switch something on? 😌",
                ur = "تمام $topicWord آف ہیں، سر — کچھ آن کروں؟ 😌",
                hi = "सभी $topicWord बंद हैं, सर — कुछ चालू करूँ? 😌",
            ).pick(lang)
        }
        val names = on.take(6).joinToString(", ") { e ->
            val mark = if (e.gated) " 🔐" else ""
            e.names.pick(lang) + mark
        }
        val more = if (on.size > 6) " (+${on.size - 6})" else ""
        return L3(
            en = "On: $names$more ✅ — $off more $topicWord off.",
            ur = "آن ہیں: $names$more ✅ — $off مزید $topicWord آف ہیں۔",
            hi = "चालू हैं: $names$more ✅ — $off और $topicWord बंद हैं।",
        ).pick(lang)
    }
}
