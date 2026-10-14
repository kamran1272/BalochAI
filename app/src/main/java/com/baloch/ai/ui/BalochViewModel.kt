package com.baloch.ai.ui

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baloch.ai.agents.AgentChip
import com.baloch.ai.agents.AgentDeps
import com.baloch.ai.agents.BriefProviders
import com.baloch.ai.agents.OrchestratedResult
import com.baloch.ai.agents.Orchestrator
import com.baloch.ai.agents.PendingAction
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.BrainEngine
import com.baloch.ai.brain.DAILY_THOUGHTS
import com.baloch.ai.brain.DeviceAction
import com.baloch.ai.brain.HybridBrain
import com.baloch.ai.brain.L3
import com.baloch.ai.brain.LanguageDetector
import com.baloch.ai.brain.LlmBrain
import com.baloch.ai.brain.LlmModelManager
import com.baloch.ai.brain.Replies
import com.baloch.ai.brain.RuleBasedBrain
import com.baloch.ai.settings.AiBrainPrefs
import com.baloch.ai.settings.BrainMode
import com.baloch.ai.life.BalochTranslator
import com.baloch.ai.life.FocusManager
import com.baloch.ai.life.LifeActions
import com.baloch.ai.life.LifeUiState
import com.baloch.ai.life.loadLifeUiState
import com.baloch.ai.power.PowerPrefs
import com.baloch.ai.callerid.SpamShield
import com.baloch.ai.callerid.CallerIdAnnouncer
import com.baloch.ai.engine.ActionResult
import com.baloch.ai.engine.DeviceActions
import com.baloch.ai.engine.NotesStore
import com.baloch.ai.engine.NotificationReader
import com.baloch.ai.engine.pro.EmergencyStore
import com.baloch.ai.engine.pro.RoutineStore
import com.baloch.ai.gesture.GestureCalibrator
import com.baloch.ai.gesture.GestureControlService
import com.baloch.ai.gesture.GestureMap
import com.baloch.ai.gesture.SensorGestureMap
import com.baloch.ai.gesture.SensorGestureService
import com.baloch.ai.memory.MemoryBackup
import com.baloch.ai.memory.MemoryRepository
import com.baloch.ai.memory.MemoryStore
import com.baloch.ai.privacy.PrivacyKind
import com.baloch.ai.privacy.PrivacyLog
import com.baloch.ai.rules.GuidanceRule
import com.baloch.ai.rules.GuidanceRuleStore
import com.baloch.ai.schedule.ScheduledTask
import com.baloch.ai.schedule.TaskScheduler
import com.baloch.ai.schedule.TaskStore
import com.baloch.ai.settings.BalochMode
import com.baloch.ai.settings.LanguageManager
import com.baloch.ai.settings.ModeManager
import com.baloch.ai.ui.components.OrbState
import com.baloch.ai.update.ReleaseInfo
import com.baloch.ai.update.UpdateChecker
import com.baloch.ai.update.UpdateResult
import com.baloch.ai.voice.BiometricLock
import com.baloch.ai.voice.PinManager
import com.baloch.ai.voice.VoiceIdentityManager
import com.baloch.ai.voice.VoiceInput
import com.baloch.ai.voice.VoiceLockManager
import com.baloch.ai.voice.VoiceOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(val fromUser: Boolean, val text: String)

/** Every screen in the app. Main is the orb + chat; the rest are pushed. */
sealed interface BalochScreen {
    data object Main : BalochScreen
    data object Settings : BalochScreen
    data object Schedule : BalochScreen
    data object Memory : BalochScreen
    data object Rules : BalochScreen
    data object VoiceSettings : BalochScreen
    data object VoiceLock : BalochScreen
    data object CallLog : BalochScreen
    data object GestureLab : BalochScreen
    data object GestureRemap : BalochScreen
    data object Routines : BalochScreen
    data object Privacy : BalochScreen
    data object Qibla : BalochScreen
}

/**
 * Owns all UI state and wires brain → orchestrator → agents → voice together.
 * Flow: input → orchestrator splits into parts → brain decides per part →
 * specialist agents run in parallel → one synthesised reply. SAFE runs at
 * once; RISKY waits for the confirm dialog.
 *
 * Also owns: screen navigation, the settings-screen state (every toggle
 * wired to a real store — SETTINGS LAW), first-run splash/onboarding/tour,
 * the update checker, and the privacy audit log.
 */
class BalochViewModel(application: Application) : AndroidViewModel(application) {

    internal val appCtx: Context = application.applicationContext

    private val languageManager = LanguageManager(application)
    val language: StateFlow<BalochLanguage> = languageManager.language

    private val modeManager = ModeManager(application)
    val mode: StateFlow<BalochMode> = modeManager.modeFlow.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.Eagerly,
        BalochMode.MANUAL,
    )

    private val deviceActions = DeviceActions(application)
    private val memory = MemoryStore(application)
    private val memoryRepo = MemoryRepository.get(application)
    private val notes = NotesStore(application)
    private val ruleStore = GuidanceRuleStore(application)
    private val voiceLockManager = VoiceLockManager(application)
    private val pinManager = PinManager(application)
    private val privacyLog = PrivacyLog.get(application)
    private val prefs = application.getSharedPreferences("baloch_settings", Context.MODE_PRIVATE)

    // Hybrid brain: rules = instant fast-path, on-device LLM = smart fallback.
    // The LLM lands via MediaPipe GenAI (model downloads on first run, never in the APK).
    private val brain: BrainEngine = HybridBrain(
        rules = RuleBasedBrain(
            memory = memory,
            notes = notes,
            resolveContact = deviceActions::resolveContact,
            notificationSummary = {
                if (NotificationReader.isEnabled(getApplication())) NotificationReader.summary() else null
            },
        ),
        llm = LlmBrain(
            appContext = application.applicationContext,
            modelManager = LlmModelManager(application.applicationContext),
        ),
        brainMode = { AiBrainPrefs(application.applicationContext).brainMode },
    )

    private val voiceInput = VoiceInput(application)
    private val voiceOutput = VoiceOutput(application)
    private val voiceIdentity = VoiceIdentityManager(application)

    // ------------------------------------------------------------------
    // Navigation + first-run
    // ------------------------------------------------------------------

    private val _screen = MutableStateFlow<BalochScreen>(BalochScreen.Main)
    val screen: StateFlow<BalochScreen> = _screen.asStateFlow()

    fun openScreen(s: BalochScreen) {
        when (s) {
            BalochScreen.Settings -> refreshSettings()
            BalochScreen.Schedule -> refreshSchedule()
            BalochScreen.Memory -> refreshMemory()
            BalochScreen.Rules -> refreshRules()
            BalochScreen.VoiceSettings -> refreshVoiceSettings()
            BalochScreen.VoiceLock -> refreshVoiceLock()
            BalochScreen.CallLog -> refreshCallLog()
            BalochScreen.GestureLab -> { /* lab binds on entry via its actions */ }
            BalochScreen.GestureRemap -> refreshRemap()
            BalochScreen.Routines -> refreshRoutines()
            BalochScreen.Privacy -> refreshPrivacy()
            BalochScreen.Qibla -> { /* qibla binds on entry via its actions */ }
            BalochScreen.Main -> { /* nothing */ }
        }
        _screen.value = s
    }

    fun goBack() {
        _screen.value = BalochScreen.Main
    }

    /** Opens this app's system settings page (for permission grants). */
    fun openAppSettings() {
        try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${appCtx.packageName}"),
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            appCtx.startActivity(intent)
        } catch (_: Exception) { }
    }

    /** The orchestrator's navigateTo hints land here. */
    private fun handleNavigateTo(hint: String?) {
        when (hint) {
            "rules" -> openScreen(BalochScreen.Rules)
            "voicelock" -> openScreen(BalochScreen.VoiceLock)
            "gesturelab" -> openScreen(BalochScreen.GestureLab)
            "settings_gestures" -> openScreen(BalochScreen.Settings)
            "settings" -> openScreen(BalochScreen.Settings)
        }
    }

    private val _showSplash = MutableStateFlow(true)
    val showSplash: StateFlow<Boolean> = _showSplash.asStateFlow()
    fun splashDone() { _showSplash.value = false }

    private val _showOnboarding = MutableStateFlow(false)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    private val _showTour = MutableStateFlow(false)
    val showTour: StateFlow<Boolean> = _showTour.asStateFlow()

    fun onboardingAccepted() {
        prefs.edit().putString("privacy_accepted", "1").apply()
        _showOnboarding.value = false
        _showTour.value = true
    }

    fun tourDone() {
        prefs.edit().putString("tour_done", "1").apply()
        _showTour.value = false
    }

    // ------------------------------------------------------------------
    // Core UI state (existing)
    // ------------------------------------------------------------------

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _orbState = MutableStateFlow(OrbState.IDLE)
    val orbState: StateFlow<OrbState> = _orbState.asStateFlow()

    private val _pending = MutableStateFlow<PendingAction?>(null)
    val pendingConfirmation: StateFlow<PendingAction?> = _pending.asStateFlow()

    private val _voiceOn = MutableStateFlow(true)
    val voiceOn: StateFlow<Boolean> = _voiceOn.asStateFlow()

    /**
     * VOICE MODE vs CHAT MODE (Kamran's immersive-UI order).
     * CHAT MODE: bubbles + input (default). VOICE MODE: fullscreen orb
     * only, no chat chrome, minimal status text — pure JARVIS feeling.
     * Auto-switches on voice activity; Kamran can override manually.
     */
    private val _voiceMode = MutableStateFlow(false)
    val voiceMode: StateFlow<Boolean> = _voiceMode.asStateFlow()

    fun setVoiceMode(on: Boolean) { _voiceMode.value = on }

    private val _agentChips = MutableStateFlow<List<AgentChip>>(emptyList())
    val agentChips: StateFlow<List<AgentChip>> = _agentChips.asStateFlow()

    private var chipClearJob: Job? = null

    private val orchestrator: Orchestrator

    init {
        val deps = AgentDeps(
            deviceActions = deviceActions,
            notes = notes,
            memory = memory,
            appContext = application.applicationContext,
            onSetMode = { jarvis ->
                viewModelScope.launch { runAction(DeviceAction.SetMode(jarvis)) }
            },
        )
        orchestrator = Orchestrator(deps, brain, briefProviders())
        say(WELCOME.pick(languageManager.current()), speak = false)
        // Keep TTS in step with the language setting.
        viewModelScope.launch {
            languageManager.language.collect { lang -> voiceOutput.setLanguage(lang) }
        }
        // First-run gates.
        if (prefs.getString("privacy_accepted", null) == null) {
            _showOnboarding.value = true
        } else if (prefs.getString("tour_done", null) == null) {
            _showTour.value = true
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    fun onInputChange(text: String) { _input.value = text }

    fun setLanguage(lang: BalochLanguage) {
        languageManager.setLanguage(lang)
        val msg = L3(
            en = "Language set to English, sir.",
            ur = "زبان اردو کر دی گئی، سر۔",
            hi = "भाषा हिन्दी कर दी गई, सर।",
        ).pick(lang)
        say(msg)
        refreshSettings()
    }

    fun toggleVoice() {
        _voiceOn.value = !_voiceOn.value
        voiceOutput.enabled = _voiceOn.value
        if (!_voiceOn.value) voiceOutput.stop()
    }

    fun sendText() {
        val text = _input.value.trim()
        if (text.isBlank() || _orbState.value == OrbState.THINKING) return
        _input.value = ""
        process(text)
    }

    // ------------------------------------------------------------------
    // Voice
    // ------------------------------------------------------------------

    /** Called by the UI after the mic button is tapped and RECORD_AUDIO is granted. */
    fun startVoice() {
        if (_listening.value) {
            stopVoice()
            return
        }
        if (!voiceInput.isAvailable()) {
            say(Replies.VOICE_UNAVAILABLE.pick(languageManager.current()))
            return
        }
        _listening.value = true
        _orbState.value = OrbState.LISTENING
        _voiceMode.value = true // auto-switch to immersive voice mode
        voiceInput.startListening(
            lang = languageManager.current(),
            onPartial = { _input.value = it },
            onFinal = { text ->
                _listening.value = false
                _orbState.value = OrbState.IDLE
                _input.value = ""
                process(text)
            },
            onError = { code ->
                _listening.value = false
                _orbState.value = OrbState.IDLE
                if (code != "error:7") { // 7 = no speech; stay silent on that one
                    say(MISHEARD.pick(languageManager.current()))
                }
            },
            onFallback = {
                say(Replies.VOICE_FALLBACK_ENGLISH.pick(languageManager.current()), speak = false)
            },
        )
    }

    fun stopVoice() {
        voiceInput.stopListening()
        _listening.value = false
        if (_orbState.value == OrbState.LISTENING) _orbState.value = OrbState.IDLE
        // Back to chat mode only if nothing is being processed.
        if (_orbState.value == OrbState.IDLE) _voiceMode.value = false
    }

    // ------------------------------------------------------------------
    // Core loop
    // ------------------------------------------------------------------

    private fun process(userText: String) {
        say(userText, fromUser = true, speak = false)
        _orbState.value = OrbState.THINKING
        chipClearJob?.cancel()
        _agentChips.value = emptyList()
        viewModelScope.launch(Dispatchers.Default) {
            // Auto language detection (default ON): Baloch detects each
            // utterance's language himself and replies in it. Falls back to
            // the manual selector silently on low confidence.
            val manual = languageManager.current()
            val lang = if (prefs.getBoolean("auto_lang_detect", true))
                LanguageDetector.detect(userText, manual).language else manual
            val ctx = deviceActions.currentContext(lang)
            val result = try {
                orchestrator.run(
                    userText,
                    ctx,
                    onChips = { chips ->
                        // onChips fires from IO worker threads — marshal to Main.
                        viewModelScope.launch(Dispatchers.Main) { _agentChips.value = chips }
                    },
                )
            } catch (e: Exception) {
                OrchestratedResult(
                    reply = L3(
                        "Something glitched, sir.",
                        "کچھ خرابی ہوئی، سر۔",
                        "कुछ गड़बड़ हुई, सर।",
                    ).pick(lang),
                    pending = null,
                    chips = emptyList(),
                )
            }
            // TTS follows the detected language for this turn.
            voiceOutput.setLanguage(lang)
            withContext(Dispatchers.Main) {
                _orbState.value = OrbState.SPEAKING
                say(result.reply)
                result.pending?.let { _pending.value = it }
                result.navigateTo?.let { handleNavigateTo(it) }
                _orbState.value = OrbState.IDLE
                // Voice session over → glide back to chat mode.
                _voiceMode.value = false
                if (result.chips.isNotEmpty()) {
                    chipClearJob?.cancel()
                    chipClearJob = viewModelScope.launch {
                        delay(4000)
                        _agentChips.value = emptyList()
                    }
                }
            }
        }
    }

    /** Live answer providers for the BriefAgent's info actions. */
    private fun briefProviders(): BriefProviders = BriefProviders(
        timeLine = { lang ->
            val c = deviceActions.currentContext(lang)
            L3(
                en = "It's ${c.timeString}, sir.",
                ur = "${c.timeString} بجے ہیں، سر۔",
                hi = "${c.timeString} बजे हैं, सर।",
            ).pick(lang)
        },
        batteryLine = { lang ->
            val c = deviceActions.currentContext(lang)
            val charge = if (c.isCharging) {
                L3(" and charging", " اور چارج ہو رہی ہے", " और चार्ज हो रही है").pick(lang)
            } else ""
            L3(
                en = "Battery is at ${c.batteryPercent}%$charge, sir.",
                ur = "بیٹری ${c.batteryPercent} فیصد ہے$charge، سر۔",
                hi = "बैटरी ${c.batteryPercent} प्रतिशत है$charge, सर।",
            ).pick(lang)
        },
        statsLine = { lang ->
            val c = deviceActions.currentContext(lang)
            L3(
                en = "Battery ${c.batteryPercent}%. Time ${c.timeString}, ${c.dateString}. All systems nominal, sir.",
                ur = "بیٹری ${c.batteryPercent} فیصد۔ وقت ${c.timeString}، ${c.dateString}۔ تمام سسٹم درست ہیں، سر۔",
                hi = "बैटरी ${c.batteryPercent} प्रतिशत। समय ${c.timeString}, ${c.dateString}। सभी सिस्टम सही हैं, सर।",
            ).pick(lang)
        },
        notificationsLine = { lang ->
            val items = if (NotificationReader.isEnabled(getApplication())) {
                NotificationReader.summary()
            } else null
            when {
                items == null -> Replies.NOTIFICATIONS_DISABLED.pick(lang)
                items.isEmpty() -> Replies.NOTIFICATIONS_EMPTY.pick(lang)
                else -> L3(
                    en = "Top notifications, sir:",
                    ur = "اہم اطلاعات، سر:",
                    hi = "मुख्य सूचनाएँ, सर:",
                ).pick(lang) + "\n• " + items.take(5).joinToString("\n• ")
            }
        },
        thoughtLine = { lang -> DAILY_THOUGHTS.random().pick(lang) },
    )

    private suspend fun runAction(action: DeviceAction) {
        // Dual-mode switch is applied here, never through agents.
        if (action is DeviceAction.SetMode) {
            modeManager.setMode(if (action.jarvis) BalochMode.JARVIS else BalochMode.MANUAL)
            return
        }
        // Stores live in the ViewModel; hardware goes through DeviceActions.
        when (action) {
            is DeviceAction.AddNote -> {
                notes.add(action.text)
                return
            }
            is DeviceAction.RememberFact -> {
                memory.saveFact(action.fact)
                return
            }
            else -> { /* fall through to hardware */ }
        }
        val result = withContext(Dispatchers.IO) { deviceActions.execute(action) }
        when (result) {
            is ActionResult.Ok -> { /* brain already announced it */ }
            is ActionResult.NeedPermission -> withContext(Dispatchers.Main) {
                say(withLang { lang ->
                    Replies.PERMISSION_NEEDED.format(lang, "perm" to result.permission.ifBlank { "the required" })
                })
            }
            is ActionResult.Failed -> withContext(Dispatchers.Main) { say(result.message) }
        }
        withContext(Dispatchers.Main) { _orbState.value = OrbState.IDLE }
    }

    // ------------------------------------------------------------------
    // Confirmation dialog
    // ------------------------------------------------------------------

    fun confirmPending() {
        val pending = _pending.value ?: return
        _pending.value = null
        // Privacy audit: personal-data actions that leave the phone are logged.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (pending.action) {
                    is DeviceAction.CallContact ->
                        privacyLog.log(PrivacyKind.CONTACT, "Call confirmed: ${(pending.action as DeviceAction.CallContact).name}")
                    is DeviceAction.SendSms ->
                        privacyLog.log(PrivacyKind.CONTACT, "SMS confirmed: ${(pending.action as DeviceAction.SendSms).name}")
                    is DeviceAction.EmergencyCall ->
                        privacyLog.log(PrivacyKind.CONTACT, "Emergency call confirmed")
                    is DeviceAction.WhatsAppDraft ->
                        privacyLog.log(PrivacyKind.CONTACT, "WhatsApp draft confirmed: ${(pending.action as DeviceAction.WhatsAppDraft).name}")
                    else -> { /* not a personal-data touch */ }
                }
            } catch (_: Exception) { /* audit must never break the action */ }
        }
        viewModelScope.launch { runAction(pending.action) }
    }

    fun dismissPending() {
        _pending.value = null
        say(withLang { lang ->
            L3("Cancelled, sir.", "منسوخ کر دیا، سر۔", "रद्द कर दिया, सर।").pick(lang)
        }, speak = false)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun withLang(block: (BalochLanguage) -> String): String =
        block(languageManager.current())

    private fun say(text: String, fromUser: Boolean = false, speak: Boolean = true) {
        _messages.value = _messages.value + ChatMessage(fromUser, text)
        if (!fromUser && speak && _voiceOn.value) voiceOutput.speak(text)
    }

    fun shutdown() {
        voiceInput.destroy()
        voiceOutput.shutdown()
    }

    // ==================================================================
    // SETTINGS — mission control (SETTINGS LAW: every toggle is real)
    // ==================================================================

    private val _settingsState = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    // Life + Power sections (their own state holders; refreshed with settings).
    private val _lifeUiState = MutableStateFlow<LifeUiState?>(null)
    val lifeUiState: StateFlow<LifeUiState?> = _lifeUiState.asStateFlow()
    private val _powerUiState = MutableStateFlow(PowerUiState())
    val powerUiState: StateFlow<PowerUiState> = _powerUiState.asStateFlow()

    val lifeActions: LifeActions = object : LifeActions {
        override fun onPrayerAlertsChanged(on: Boolean) {
            appCtx.getSharedPreferences("baloch_life", Context.MODE_PRIVATE)
                .edit().putBoolean("prayer_alerts", on).apply()
            refreshSettings()
        }
        override fun onOpenQibla() = openScreen(BalochScreen.Qibla)
        override fun onCopilotNudgesChanged(on: Boolean) {
            appCtx.getSharedPreferences("baloch_life", Context.MODE_PRIVATE)
                .edit().putBoolean("copilot_nudges", on).apply()
            refreshSettings()
        }
        override fun onOpenClients() = openScreen(BalochScreen.Settings)
        override fun onExpenseCurrencyChanged(currency: String) {
            appCtx.getSharedPreferences("baloch_life", Context.MODE_PRIVATE)
                .edit().putString("expense_currency", currency).apply()
            refreshSettings()
        }
        override fun onOpenExpenseLog() = openScreen(BalochScreen.Settings)
        override fun onFocusMinChanged(minutes: Int) {
            appCtx.getSharedPreferences("baloch_life", Context.MODE_PRIVATE)
                .edit().putInt("focus_min", minutes).apply()
            refreshSettings()
        }
        override fun onBreakMinChanged(minutes: Int) {
            appCtx.getSharedPreferences("baloch_life", Context.MODE_PRIVATE)
                .edit().putInt("break_min", minutes).apply()
            refreshSettings()
        }
        override fun onStartFocusNow() {
            val mins = appCtx.getSharedPreferences("baloch_life", Context.MODE_PRIVATE)
                .getInt("focus_min", 25)
            FocusManager.startFocus(appCtx, mins, 5)
            say(withLang { lang ->
                L3("Focus mode: $mins minutes, sir. 🎯",
                    "فوکس موڈ: $mins منٹ، سر۔ 🎯",
                    "फ़ोकस मोड: $mins मिनट, सर। 🎯").pick(lang)
            }, speak = false)
        }
        override fun onGeoRemindersChanged(on: Boolean) {
            appCtx.getSharedPreferences("baloch_life", Context.MODE_PRIVATE)
                .edit().putBoolean("geo_reminders", on).apply()
            refreshSettings()
        }
        override fun onGrantLocation() = openAppSettings()
        override fun onOpenAddGeoReminder() = openScreen(BalochScreen.Settings)
        override fun onTranslatorAutoDownloadChanged(on: Boolean) {
            appCtx.getSharedPreferences("baloch_life", Context.MODE_PRIVATE)
                .edit().putBoolean("translator_auto_dl", on).apply()
            refreshSettings()
        }
        override fun onDownloadTranslatorNow() {
            viewModelScope.launch(Dispatchers.IO) {
                BalochTranslator(appCtx).downloadModel(
                    BalochLanguage.ENGLISH, BalochLanguage.URDU, requireWifi = false,
                )
                withContext(Dispatchers.Main) { refreshSettings() }
            }
        }
        override fun onSaveSosIce(number: String) {
            val clean = number.filter { it.isDigit() || it == '+' }
            if (clean.length >= 7) {
                EmergencyStore.set(appCtx, clean)
                say(withLang { lang ->
                    L3("Emergency contact saved, sir — $clean.",
                        "ایمرجنسی رابطہ محفوظ، سر — $clean۔",
                        "आपातकालीन संपर्क सहेजा, सर — $clean।").pick(lang)
                }, speak = false)
            }
            refreshSettings()
        }
        override fun onGrantSms() = openAppSettings()
    }

    val powerActions: PowerActions = object : PowerActions {
        private fun update() {
            _powerUiState.value = loadPowerUiState(appCtx)
        }
        override fun onDrivingChanged(on: Boolean) {
            PowerPrefs.setOn(appCtx, PowerPrefs.DRIVING, on); update() }
        override fun onDrivingAutoReplyChanged(on: Boolean) {
            PowerPrefs.setOn(appCtx, PowerPrefs.DRIVING_AUTO_REPLY, on); update() }
        override fun onDrivingAutoOffChanged(minutes: Int) {
            PowerPrefs.setInt(appCtx, PowerPrefs.DRIVING_AUTO_OFF_MIN, minutes); update() }
        override fun onRamadanChanged(on: Boolean) {
            PowerPrefs.setOn(appCtx, PowerPrefs.RAMADAN, on); update() }
        override fun onRamadanCityChanged(city: String) {
            PowerPrefs.setString(appCtx, PowerPrefs.RAMADAN_CITY, city); update() }
        override fun onCricketChanged(on: Boolean) {
            PowerPrefs.setOn(appCtx, PowerPrefs.CRICKET, on); update() }
        override fun onCricketMatchDaysOnlyChanged(on: Boolean) {
            PowerPrefs.setOn(appCtx, PowerPrefs.CRICKET_MATCH_DAYS_ONLY, on); update() }
        override fun onCurrencyChanged(on: Boolean) {
            PowerPrefs.setOn(appCtx, PowerPrefs.CURRENCY, on); update() }
        override fun onCurrencyThresholdChanged(threshold: Double) {
            PowerPrefs.setDouble(appCtx, PowerPrefs.CURRENCY_THRESHOLD, threshold); update() }
        override fun onPetrolChanged(on: Boolean) {
            PowerPrefs.setOn(appCtx, PowerPrefs.PETROL, on); update() }
        override fun onWordChanged(on: Boolean) {
            PowerPrefs.setOn(appCtx, PowerPrefs.WORD, on); update() }
        override fun onRecorderChanged(on: Boolean) {
            PowerPrefs.setOn(appCtx, PowerPrefs.RECORDER, on); update() }
        override fun onRecorderTranscribeChanged(on: Boolean) {
            PowerPrefs.setOn(appCtx, PowerPrefs.RECORDER_TRANSCRIBE, on); update() }
        override fun onChargeChanged(on: Boolean) {
            PowerPrefs.setOn(appCtx, PowerPrefs.CHARGE, on); update() }
        override fun onRainChanged(on: Boolean) {
            PowerPrefs.setOn(appCtx, PowerPrefs.RAIN, on); update() }
        override fun onAppLockChanged(on: Boolean) {
            PowerPrefs.setOn(appCtx, PowerPrefs.APPLOCK, on); update() }
    }

    private fun refreshSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val lang = languageManager.current()
            val sensorPrefs = appCtx.getSharedPreferences("baloch_sensor_gesture", Context.MODE_PRIVATE)
            val state = SettingsState(
                activeLanguage = lang,
                quietVoiceMode = appCtx.getSharedPreferences("quiet_voice_mode", Context.MODE_PRIVATE)
                    .getBoolean("enabled", true),
                voiceLockEnabled = voiceLockManager.isEnabled(appCtx),
                voiceLockEnrolled = voiceLockManager.isEnrolled(appCtx),
                biometricAvailable = BiometricLock.canUseBiometric(appCtx),
                biometricLockOn = prefs.getBoolean("biometric_lock_on", false),
                rulesCount = ruleStore.all().size,
                mode = mode.value,
                checkInFrequency = when (
                    appCtx.getSharedPreferences("baloch_companion", Context.MODE_PRIVATE)
                        .getString("checkin_level", "normal")
                ) {
                    "off" -> CheckInFrequency.OFF
                    "subtle" -> CheckInFrequency.SUBTLE
                    else -> CheckInFrequency.NORMAL
                },
                gestureControlEnabled = GestureControlService.isMasterEnabled(appCtx),
                gestureCalibrationDone = GestureCalibrator.isCalibrated(appCtx),
                gestureStatus = if (GestureControlService.isMasterEnabled(appCtx)) "READY" else "OFF",
                sensorGesturesEnabled = SensorGestureService.isEnabled(appCtx),
                sensorWaveOn = SensorGestureService.isGestureOn(appCtx, SensorGestureService.KEY_WAVE, true),
                sensorFlipOn = SensorGestureService.isGestureOn(appCtx, SensorGestureService.KEY_FLIP, true),
                sensorShakeOn = SensorGestureService.isGestureOn(appCtx, SensorGestureService.KEY_SHAKE, true),
                sensorPickupOn = SensorGestureService.isGestureOn(appCtx, SensorGestureService.KEY_PICK_UP, false),
                sensorFlipDndOn = sensorPrefs.getBoolean(SensorGestureService.KEY_FLIP_DND, false),
                sensorStatus = if (SensorGestureService.isEnabled(appCtx)) "ACTIVE" else "OFF",
                scheduledTaskCount = TaskStore(appCtx).all().size,
                routinesCount = RoutineStore(appCtx).all().size,
                batteryGuardianOn = prefs.getBoolean("battery_guardian_on", true),
                iceContact = EmergencyStore.get(appCtx).orEmpty(),
                permissionStatuses = computePermissionStatuses(),
                brainMode = AiBrainPrefs(appCtx).brainMode,
                aiModelDownloaded = LlmModelManager(appCtx).isDownloaded(),
                aiModelSizeBytes = LlmModelManager(appCtx).storageUsedBytes(),
                aiModelRamGb = LlmModelManager(appCtx).deviceRamGb(),
                aiModelRamOk = LlmModelManager(appCtx).deviceRamGb() >= 4.0,
            )
            _settingsState.value = state
            // Life + Power sections (their own holders).
            _lifeUiState.value = try { loadLifeUiState(appCtx) } catch (_: Exception) { null }
            _powerUiState.value = try { loadPowerUiState(appCtx) } catch (_: Exception) { PowerUiState() }
        }
    }

    private fun computePermissionStatuses(): Map<String, Boolean> {
        val keys = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.SCHEDULE_EXACT_ALARM,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        val map = keys.associateWith {
            ContextCompat.checkSelfPermission(appCtx, it) == PackageManager.PERMISSION_GRANTED
        }.toMutableMap()
        // Special accesses (not runtime permissions).
        map["usage_stats"] = hasUsageAccess()
        map["notification_listener"] = NotificationReader.isEnabled(appCtx)
        map["dnd_access"] = try {
            (appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .isNotificationPolicyAccessGranted
        } catch (_: Exception) {
            false
        }
        return map
    }

    private fun hasUsageAccess(): Boolean = try {
        val appOps = appCtx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), appCtx.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), appCtx.packageName,
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }

    /** Every settings control, wired to its real store. No dead switches. */
    val settingsActions: SettingsActions = object : SettingsActions {
        override fun onBack() = goBack()
        override fun onLanguageChange(language: BalochLanguage) = setLanguage(language)
        override fun onQuietVoiceModeChange(enabled: Boolean) {
            appCtx.getSharedPreferences("quiet_voice_mode", Context.MODE_PRIVATE)
                .edit().putBoolean("enabled", enabled).apply()
            refreshSettings()
        }
        override fun onOpenVoiceSettings() = openScreen(BalochScreen.VoiceSettings)
        override fun onOpenVoiceLockSetup() = openScreen(BalochScreen.VoiceLock)
        override fun onBiometricLockChange(enabled: Boolean) {
            prefs.edit().putBoolean("biometric_lock_on", enabled).apply()
            refreshSettings()
            refreshVoiceLock()
        }
        override fun onOpenRules() = openScreen(BalochScreen.Rules)
        override fun onDefaultModeChange(mode: BalochMode) {
            viewModelScope.launch { modeManager.setMode(mode) }
            val msg = if (mode == BalochMode.JARVIS) {
                L3("JARVIS mode engaged, sir. I am now watching over everything.",
                    "جاروس موڈ فعال، سر۔", "जार्विस मोड सक्रिय, सर।")
            } else {
                L3("Manual mode, sir. I'll stay quiet until you call on me.",
                    "مینوئل موڈ، سر۔", "मैनुअल मोड, सर।")
            }
            say(msg.pick(languageManager.current()))
            refreshSettings()
        }
        override fun onCheckInFrequencyChange(frequency: CheckInFrequency) {
            val level = when (frequency) {
                CheckInFrequency.OFF -> "off"
                CheckInFrequency.SUBTLE -> "subtle"
                CheckInFrequency.NORMAL -> "normal"
            }
            appCtx.getSharedPreferences("baloch_companion", Context.MODE_PRIVATE)
                .edit().putString("checkin_level", level).apply()
            refreshSettings()
        }
        override fun onGestureControlChange(enabled: Boolean) {
            GestureControlService.setMasterEnabled(appCtx, enabled)
            refreshSettings()
        }
        override fun onCalibrateGestures() = openScreen(BalochScreen.GestureLab)
        override fun onOpenGestureRemap() = openScreen(BalochScreen.GestureRemap)
        override fun onOpenGestureLab() = openScreen(BalochScreen.GestureLab)
        override fun onSensorGesturesChange(enabled: Boolean) {
            SensorGestureService.setEnabled(appCtx, enabled)
            refreshSettings()
        }
        override fun onSensorWaveChange(enabled: Boolean) {
            SensorGestureService.setGestureOn(appCtx, SensorGestureService.KEY_WAVE, enabled)
            refreshSettings()
        }
        override fun onSensorFlipChange(enabled: Boolean) {
            SensorGestureService.setGestureOn(appCtx, SensorGestureService.KEY_FLIP, enabled)
            refreshSettings()
        }
        override fun onSensorShakeChange(enabled: Boolean) {
            SensorGestureService.setGestureOn(appCtx, SensorGestureService.KEY_SHAKE, enabled)
            refreshSettings()
        }
        override fun onSensorPickupChange(enabled: Boolean) {
            SensorGestureService.setGestureOn(appCtx, SensorGestureService.KEY_PICK_UP, enabled)
            refreshSettings()
        }
        override fun onSensorFlipDndChange(enabled: Boolean) {
            appCtx.getSharedPreferences("baloch_sensor_gesture", Context.MODE_PRIVATE)
                .edit().putBoolean(SensorGestureService.KEY_FLIP_DND, enabled).apply()
            refreshSettings()
        }
        override fun onOpenSensorRemap() = openScreen(BalochScreen.GestureRemap)
        override fun onOpenMemory() = openScreen(BalochScreen.Memory)
        override fun onMemoryBackup() {
            say(withLang { lang ->
                L3("Opening the export picker, sir — choose where to save your encrypted backup.",
                    "ایکسپورٹ چنیں، سر۔", "एक्सपोर्ट चुनें, सर।").pick(lang)
            }, speak = false)
            _exportRequest.value = true
        }
        override fun onOpenScheduled() = openScreen(BalochScreen.Schedule)
        override fun onOpenRoutines() = openScreen(BalochScreen.Routines)
        override fun onBatteryGuardianChange(enabled: Boolean) {
            prefs.edit().putBoolean("battery_guardian_on", enabled).apply()
            refreshSettings()
        }
        override fun onIceContactSave(number: String) {
            val clean = number.trim()
            if (clean.length >= 7) {
                EmergencyStore.set(appCtx, clean)
                say(withLang { lang ->
                    L3("Emergency contact saved, sir — $clean.",
                        "ایمرجنسی رابطہ محفوظ، سر — $clean۔",
                        "आपातकालीन संपर्क सहेजा, सर — $clean।").pick(lang)
                })
            }
            refreshSettings()
        }
        override fun onCheckForUpdates() = checkForUpdates()
        override fun onOpenPrivacyDashboard() = openScreen(BalochScreen.Privacy)

        // ---- AI Brain ----
        private val aiModelManager = LlmModelManager(application.applicationContext)
        private var aiDownloadJob: kotlinx.coroutines.Job? = null

        override fun onBrainModeChange(mode: BrainMode) {
            AiBrainPrefs(application.applicationContext).brainMode = mode
            refreshSettings()
            say(withLang { lang ->
                val label = if (mode == BrainMode.HYBRID) "Hybrid" else "Rules-only"
                L3("AI brain: $label mode, sir 🧠",
                    "اے آئی دماغ: $label موڈ، سر 🧠",
                    "एआई दिमाग़: $label मोड, सर 🧠").pick(lang)
            }, speak = false)
        }

        override fun onDownloadAiModel() {
            aiDownloadJob?.cancel()
            aiDownloadJob = viewModelScope.launch(Dispatchers.IO) {
                aiModelManager.downloadModel { p ->
                    viewModelScope.launch(Dispatchers.Main) {
                        _settingsState.value = _settingsState.value.copy(aiModelDownloadProgress = p)
                    }
                }.let { result ->
                    withContext(Dispatchers.Main) {
                        _settingsState.value = _settingsState.value.copy(aiModelDownloadProgress = null)
                        val msg = when (result) {
                            is LlmModelManager.DownloadResult.Success -> L3(
                                "AI brain downloaded, sir — Hybrid mode is fully live. 🧠",
                                "اے آئی دماغ ڈاؤن لوڈ، سر — ہائبرڈ موڈ مکمل فعال۔ 🧠",
                                "एआई दिमाग़ डाउनलोड, सर — हाइब्रिड मोड पूर्ण सक्रिय। 🧠",
                            )
                            is LlmModelManager.DownloadResult.BlockedLowRam -> L3(
                                "Sir, the AI brain needs 4GB+ RAM — rules mode stays perfect on this phone.",
                                "سر، اے آئی دماغ کے لیے 4GB+ ریم چاہیے — اس فون پر رول موڈ بہترین رہے گا۔",
                                "सर, एआई दिमाग़ के लिए 4GB+ रैम चाहिए — इस फ़ोन पर रूल मोड बढ़िया रहेगा।",
                            )
                            is LlmModelManager.DownloadResult.NeedsManualImport -> L3(
                                "Sir, the model needs a one-time manual download — Settings → AI Brain → Import model file.",
                                "سر، ماڈل ایک بار دستی ڈاؤن لوڈ چاہتا ہے — Settings → AI Brain → Import model file۔",
                                "सर, मॉडल को एक बार मैन्युअल डाउनलोड चाहिए — Settings → AI Brain → Import model file।",
                            )
                            is LlmModelManager.DownloadResult.NetworkError -> L3(
                                "Download paused, sir — I'll resume when the network is back.",
                                "ڈاؤن لوڈ رکا، سر — نیٹ ورک آتے ہی جاری رکھوں گا۔",
                                "डाउनलोड रुका, सर — नेटवर्क आते ही जारी रखूँगा।",
                            )
                            is LlmModelManager.DownloadResult.Cancelled -> L3(
                                "Download cancelled, sir.",
                                "ڈاؤن لوڈ منسوخ، سر۔",
                                "डाउनलोड रद्द, सर।",
                            )
                        }
                        say(withLang { lang -> msg.pick(lang) }, speak = false)
                        refreshSettings()
                    }
                }
            }
        }

        override fun onCancelAiModelDownload() {
            aiDownloadJob?.cancel()
            aiDownloadJob = null
            viewModelScope.launch(Dispatchers.Main) {
                _settingsState.value = _settingsState.value.copy(aiModelDownloadProgress = null)
            }
        }

        override fun onDeleteAiModel() {
            viewModelScope.launch(Dispatchers.IO) {
                aiModelManager.deleteModel()
                withContext(Dispatchers.Main) { refreshSettings() }
            }
        }

        override fun onImportAiModelFile() {
            _aiModelImportRequest.value = true
        }
    }

    // ==================================================================
    // Sub-screens
    // ==================================================================

    // ---- Voice settings (voice identity) ----
    private val _voiceSettings = MutableStateFlow(
        VoiceSettingsState("", "—", emptyList(), 100, false)
    )
    val voiceSettings: StateFlow<VoiceSettingsState> = _voiceSettings.asStateFlow()

    private fun refreshVoiceSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val lang = languageManager.current()
            val all = voiceOutput.availableVoices()
            val current = voiceOutput.currentVoiceName()
            val options = voiceIdentity.voicesForLocale(all, lang.ttsLocale).map {
                VoiceOption(it.name, it.name, it.name == current)
            }
            _voiceSettings.value = VoiceSettingsState(
                languageName = lang.displayName,
                currentVoiceName = current ?: "—",
                voices = options,
                volumePct = voiceOutput.voiceVolumePct(),
                voiceChangedNote = voiceOutput.voiceChangedNotePending(),
            )
        }
    }

    fun pickVoice(id: String) {
        voiceOutput.pickVoice(languageManager.current(), id)
        viewModelScope.launch {
            delay(600)
            refreshVoiceSettings()
        }
    }

    fun previewVoice() {
        voiceOutput.previewVoice(
            withLang { lang ->
                L3("I am Baloch, sir.", "میں بلوچ ہوں، سر۔", "मैं बलोच हूँ, सर।").pick(lang)
            }
        )
    }

    fun setVoiceVolume(pct: Int) {
        voiceOutput.setVoiceVolume(pct)
        refreshVoiceSettings()
    }

    // ---- Voice lock setup ----
    private val _voiceLockState = MutableStateFlow(VoiceLockSetupState())
    val voiceLockState: StateFlow<VoiceLockSetupState> = _voiceLockState.asStateFlow()

    private fun refreshVoiceLock() {
        viewModelScope.launch(Dispatchers.IO) {
            _voiceLockState.value = _voiceLockState.value.copy(
                isEnrolled = voiceLockManager.isEnrolled(appCtx),
                isEnabled = voiceLockManager.isEnabled(appCtx),
                hasPin = pinManager.hasPin(),
                biometricAvailable = BiometricLock.canUseBiometric(appCtx),
                biometricLockOn = prefs.getBoolean("biometric_lock_on", false),
            )
        }
    }

    val voiceLockActions = VoiceLockSetupActions(
        onRecordSample = {
            viewModelScope.launch(Dispatchers.IO) {
                _voiceLockState.value = _voiceLockState.value.copy(isRecording = true, message = null)
                val sample = voiceLockManager.enrollSample()
                val done = _voiceLockState.value.enrolledSamples + if (sample != null) 1 else 0
                _voiceLockState.value = _voiceLockState.value.copy(
                    isRecording = false,
                    enrolledSamples = done.coerceAtMost(3),
                    message = if (sample == null) "Sample failed — try again, sir." else null,
                )
                if (done >= 3) {
                    val ok = voiceLockManager.enroll(appCtx)
                    _voiceLockState.value = _voiceLockState.value.copy(
                        isEnrolled = ok,
                        message = if (ok) "Voiceprint stored, sir. 🔐" else "Enrollment failed — try again.",
                    )
                }
            }
        },
        onToggleEnabled = { on ->
            viewModelScope.launch(Dispatchers.IO) {
                if (!on) {
                    // Disabling needs proof it's him (voice or PIN path in UI).
                    _voiceLockState.value = _voiceLockState.value.copy(
                        message = "To disable, verify your voice or enter your PIN, sir."
                    )
                    return@launch
                }
                voiceLockManager.setEnabled(appCtx, true)
                refreshVoiceLock()
            }
        },
        onPinFirstChange = { _voiceLockState.value = _voiceLockState.value.copy(pinFirst = it.filter { c -> c.isDigit() }.take(4)) },
        onPinConfirmChange = { _voiceLockState.value = _voiceLockState.value.copy(pinConfirm = it.filter { c -> c.isDigit() }.take(4)) },
        onSavePin = {
            viewModelScope.launch(Dispatchers.IO) {
                val s = _voiceLockState.value
                val msg = when {
                    s.pinFirst.length != 4 || s.pinFirst != s.pinConfirm -> "PINs must match (4 digits), sir."
                    pinManager.setPin(s.pinFirst) -> "PIN saved, sir. 🔢"
                    else -> "PIN save failed — try again."
                }
                _voiceLockState.value = s.copy(message = msg, pinFirst = "", pinConfirm = "", hasPin = pinManager.hasPin())
            }
        },
        onToggleBiometric = { on ->
            prefs.edit().putBoolean("biometric_lock_on", on).apply()
            refreshVoiceLock()
            refreshSettings()
        },
        onDismissMessage = { _voiceLockState.value = _voiceLockState.value.copy(message = null) },
    )

    // ---- Scheduled tasks ----
    private val _scheduledTasks = MutableStateFlow<List<ScheduledTask>>(emptyList())
    val scheduledTasks: StateFlow<List<ScheduledTask>> = _scheduledTasks.asStateFlow()

    private fun refreshSchedule() {
        viewModelScope.launch(Dispatchers.IO) {
            _scheduledTasks.value = TaskStore(appCtx).all()
        }
    }

    fun toggleScheduledTask(id: Long, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val store = TaskStore(appCtx)
            store.setEnabled(id, enabled)
            val task = store.get(id)
            if (task != null) {
                TaskScheduler.cancel(appCtx, task.id)
                if (enabled) TaskScheduler.schedule(appCtx, task)
            }
            _scheduledTasks.value = store.all()
        }
    }

    fun deleteScheduledTask(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val store = TaskStore(appCtx)
            TaskScheduler.cancel(appCtx, id)
            store.delete(id)
            _scheduledTasks.value = store.all()
        }
    }

    // ---- Memory ----
    private val _memoryFacts = MutableStateFlow<List<com.baloch.ai.memory.room.FactEntity>>(emptyList())
    val memoryFacts: StateFlow<List<com.baloch.ai.memory.room.FactEntity>> = _memoryFacts.asStateFlow()
    private val _memoryEntities = MutableStateFlow<List<com.baloch.ai.memory.room.EntityRecord>>(emptyList())
    val memoryEntities: StateFlow<List<com.baloch.ai.memory.room.EntityRecord>> = _memoryEntities.asStateFlow()
    private val _memorySearch = MutableStateFlow("")
    val memorySearch: StateFlow<String> = _memorySearch.asStateFlow()

    private fun refreshMemory() {
        viewModelScope.launch(Dispatchers.IO) {
            _memoryFacts.value = memoryRepo.allFacts()
            _memoryEntities.value = memoryRepo.entities()
        }
    }

    fun onMemorySearchChange(q: String) {
        _memorySearch.value = q
        viewModelScope.launch(Dispatchers.IO) {
            val (facts, entities, _) = memoryRepo.searchAll(q)
            _memoryFacts.value = facts
            _memoryEntities.value = entities
        }
    }

    fun deleteFact(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            memoryRepo.deleteFact(id)
            refreshMemory()
        }
    }

    fun deleteEntity(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            memoryRepo.deleteEntity(id)
            refreshMemory()
        }
    }

    fun forgetAllMemories() {
        viewModelScope.launch(Dispatchers.IO) {
            memoryRepo.forgetAll()
            refreshMemory()
        }
    }

    private val _exportRequest = MutableStateFlow(false)
    val exportRequest: StateFlow<Boolean> = _exportRequest.asStateFlow()
    fun exportRequestConsumed() { _exportRequest.value = false }

    private val _importRequest = MutableStateFlow(false)
    val importRequest: StateFlow<Boolean> = _importRequest.asStateFlow()
    fun importRequestConsumed() { _importRequest.value = false }
    fun requestImport() { _importRequest.value = true }

    // AI model file import (separate from the memory-backup import).
    private val _aiModelImportRequest = MutableStateFlow(false)
    val aiModelImportRequest: StateFlow<Boolean> = _aiModelImportRequest.asStateFlow()
    fun aiModelImportRequestConsumed() { _aiModelImportRequest.value = false }

    fun importAiModelFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val manager = LlmModelManager(appCtx)
            val result: LlmModelManager.ImportResult = try {
                manager.importModelFile(uri) { p ->
                    viewModelScope.launch(Dispatchers.Main) {
                        _settingsState.value = _settingsState.value.copy(aiModelDownloadProgress = p)
                    }
                }
            } catch (e: Exception) {
                LlmModelManager.ImportResult.InvalidFile(e.message ?: "failed")
            }
            withContext(Dispatchers.Main) {
                _settingsState.value = _settingsState.value.copy(aiModelDownloadProgress = null)
                val label = when (result) {
                    is LlmModelManager.ImportResult.Success -> "done ✅"
                    is LlmModelManager.ImportResult.BlockedLowRam -> "blocked — needs 4GB+ RAM"
                    is LlmModelManager.ImportResult.InvalidFile -> "invalid file (${result.reason})"
                    is LlmModelManager.ImportResult.Cancelled -> "cancelled"
                }
                say(withLang { lang ->
                    L3("Model import $label, sir.",
                        "ماڈل امپورٹ $label، سر۔",
                        "मॉडल आयात $label, सर।").pick(lang)
                }, speak = false)
                refreshSettings()
            }
        }
    }

    fun exportMemory(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = MemoryBackup.exportToFile(appCtx, uri)
            withContext(Dispatchers.Main) {
                say(withLang { lang ->
                    (if (ok) L3("Encrypted backup saved, sir. 🔐", "بیک اپ محفوظ، سر۔ 🔐", "बैकअप सहेजा, सर। 🔐")
                    else L3("Backup failed, sir — try again.", "بیک اپ ناکام، سر۔", "बैकअप विफल, सर।")).pick(lang)
                })
            }
            try {
                privacyLog.log(PrivacyKind.EXPORT, "Memory backup exported")
            } catch (_: Exception) { }
        }
    }

    fun importMemory(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = MemoryBackup.importFromFile(appCtx, uri)
            withContext(Dispatchers.Main) {
                say(withLang { lang ->
                    L3("Imported $count memories, sir. 📥", "$count یادیں درآمد، سر۔ 📥", "$count यादें आयात, सर। 📥").pick(lang)
                })
            }
            refreshMemory()
        }
    }

    // ---- Rules ----
    private val _rules = MutableStateFlow<List<GuidanceRule>>(emptyList())
    val rules: StateFlow<List<GuidanceRule>> = _rules.asStateFlow()

    private fun refreshRules() {
        viewModelScope.launch(Dispatchers.IO) {
            _rules.value = ruleStore.all()
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            ruleStore.delete(id)
            _rules.value = ruleStore.all()
            refreshSettings()
        }
    }

    // ---- Call log ----
    private val _callLogEntries = MutableStateFlow<List<UnknownCallerEntry>>(emptyList())
    val callLogEntries: StateFlow<List<UnknownCallerEntry>> = _callLogEntries.asStateFlow()
    private val _hasCallLogPerm = MutableStateFlow(false)
    val hasCallLogPerm: StateFlow<Boolean> = _hasCallLogPerm.asStateFlow()

    private fun refreshCallLog() {
        viewModelScope.launch(Dispatchers.IO) {
            _hasCallLogPerm.value = ContextCompat.checkSelfPermission(
                appCtx, Manifest.permission.READ_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED
            _callLogEntries.value = loadRecentUnknownCallers(appCtx)
        }
    }

    val callLogCallbacks = object : CallLogCallbacks {
        override fun onBlock(number: String) {
            SpamShield.block(appCtx, number)
            say(withLang { lang ->
                L3("Blocked $number, sir. 🚫", "$number بلاک، سر۔ 🚫", "$number ब्लॉक, सर। 🚫").pick(lang)
            }, speak = false)
            refreshCallLog()
        }
        override fun onUnblock(number: String) {
            SpamShield.unblock(appCtx, number)
            refreshCallLog()
        }
        override fun onReportSpam(number: String) {
            SpamShield.block(appCtx, number)
            say(withLang { lang ->
                L3("Reported and blocked, sir.", "رپورٹ اور بلاک، سر۔", "रिपोर्ट और ब्लॉक, सर।").pick(lang)
            }, speak = false)
            refreshCallLog()
        }
        override fun onDeepLookup(number: String) {
            val intent = CallerIdAnnouncer.deepLookupIntent(number)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appCtx.startActivity(intent)
        }
        override fun onRequestScreenerRole() {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val rm = appCtx.getSystemService(android.app.role.RoleManager::class.java)
                    if (rm != null && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING)) {
                        // Must be called from an Activity; the ViewModel signals via a toast.
                        say(withLang { lang ->
                            L3("Sir, grant the Call Screening role in system settings — Settings → Apps → Baloch → set as Call Screening app.",
                                "سر، سسٹم سیٹنگز میں کال اسکریننگ رول دیں۔",
                                "सर, सिस्टम सेटिंग्स में कॉल स्क्रीनिंग रोल दें।").pick(lang)
                        }, speak = false)
                    }
                }
            } catch (_: Exception) { }
            refreshCallLog()
        }
        override fun onBack() = goBack()
    }

    // ---- Gesture lab ----
    private val _gestureLabState = MutableStateFlow(GestureLabState())
    val gestureLabState: StateFlow<GestureLabState> = _gestureLabState.asStateFlow()

    private var labConnection: ServiceConnection? = null
    private var labBinder: GestureControlService.GestureBinder? = null

    val gestureLabActions = object : GestureLabActions {
        override fun onStartLab() {
            val intent = Intent(appCtx, GestureControlService::class.java)
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    labBinder = service as? GestureControlService.GestureBinder
                    labBinder?.attachLab { frame ->
                        viewModelScope.launch(Dispatchers.Main) {
                            _gestureLabState.value = _gestureLabState.value.copy(
                                frame = frame.bitmap,
                                landmarks = frame.landmarks,
                                lastGestureText = frame.gestureVote?.name ?: "—",
                                fpsText = "%.1f".format(frame.fps),
                                statusText = frame.status.name.replace('_', ' '),
                            )
                        }
                    }
                }
                override fun onServiceDisconnected(name: ComponentName?) {
                    labBinder = null
                }
            }
            labConnection = conn
            appCtx.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        }
        override fun onStopLab() {
            try {
                labBinder?.detachLab()
                labConnection?.let { appCtx.unbindService(it) }
            } catch (_: Exception) { }
            labBinder = null
            labConnection = null
        }
        override fun onCalibrate() {
            viewModelScope.launch(Dispatchers.IO) {
                _gestureLabState.value = _gestureLabState.value.copy(statusText = "CALIBRATING…")
                try {
                    val binder = labBinder
                    if (binder == null) {
                        _gestureLabState.value = _gestureLabState.value.copy(statusText = "LAB NOT BOUND")
                        return@launch
                    }
                    GestureCalibrator(appCtx).calibrate(
                        sampleProvider = { binder.latestSample() },
                        onPhase = { phase ->
                            viewModelScope.launch(Dispatchers.Main) {
                                _gestureLabState.value = _gestureLabState.value.copy(
                                    statusText = if (phase == 0) "SHOW PALM…" else "MAKE FIST…"
                                )
                            }
                        },
                        onProgress = { },
                    )
                    _gestureLabState.value = _gestureLabState.value.copy(
                        calibrated = true,
                        statusText = "CALIBRATED ✓",
                    )
                    withContext(Dispatchers.Main) {
                        say(withLang { lang ->
                            L3("Calibrated, sir — I know your hand now. ✋",
                                "کیلیبریٹ ہو گیا، سر — اب میں آپ کا ہاتھ پہچانتا ہوں۔ ✋",
                                "कैलिब्रेट हो गया, सर — अब मैं आपका हाथ पहचानता हूँ। ✋").pick(lang)
                        })
                    }
                    refreshSettings()
                } catch (e: Exception) {
                    _gestureLabState.value = _gestureLabState.value.copy(statusText = "CALIBRATION FAILED")
                }
            }
        }
        override fun onBack() = goBack()
    }

    // ---- Gesture remap ----
    private val _cameraRemap = MutableStateFlow<Map<com.baloch.ai.gesture.GestureKind, String>>(emptyMap())
    val cameraRemap: StateFlow<Map<com.baloch.ai.gesture.GestureKind, String>> = _cameraRemap.asStateFlow()
    private val _sensorRemap = MutableStateFlow<Map<com.baloch.ai.gesture.SensorGestureKind, String>>(emptyMap())
    val sensorRemap: StateFlow<Map<com.baloch.ai.gesture.SensorGestureKind, String>> = _sensorRemap.asStateFlow()

    private fun refreshRemap() {
        _cameraRemap.value = GestureMap(appCtx).getMap()
        _sensorRemap.value = SensorGestureMap(appCtx).getMap()
    }

    fun remapCameraGesture(kind: com.baloch.ai.gesture.GestureKind, command: String) {
        GestureMap(appCtx).remap(kind, command)
        refreshRemap()
    }

    fun remapSensorGesture(kind: com.baloch.ai.gesture.SensorGestureKind, command: String) {
        SensorGestureMap(appCtx).remap(kind, command)
        refreshRemap()
    }

    // ---- Routines ----
    private val _routines = MutableStateFlow<List<com.baloch.ai.engine.pro.Routine>>(emptyList())
    val routines: StateFlow<List<com.baloch.ai.engine.pro.Routine>> = _routines.asStateFlow()

    private fun refreshRoutines() {
        _routines.value = RoutineStore(appCtx).all()
    }

    fun deleteRoutine(trigger: String) {
        val store = RoutineStore(appCtx)
        store.delete(trigger)
        refreshRoutines()
    }

    // ---- Privacy dashboard ----
    private val _privacyEvents = MutableStateFlow<List<com.baloch.ai.privacy.PrivacyEvent>>(emptyList())
    val privacyEvents: StateFlow<List<com.baloch.ai.privacy.PrivacyEvent>> = _privacyEvents.asStateFlow()

    private fun refreshPrivacy() {
        viewModelScope.launch {
            privacyLog.events().collect { _privacyEvents.value = it }
        }
    }

    fun clearPrivacyLog() {
        viewModelScope.launch(Dispatchers.IO) {
            privacyLog.clear()
            _privacyEvents.value = emptyList()
        }
    }

    // ==================================================================
    // Update checker
    // ==================================================================

    private val _updateInfo = MutableStateFlow<ReleaseInfo?>(null)
    val updateInfo: StateFlow<ReleaseInfo?> = _updateInfo.asStateFlow()
    fun dismissUpdate() { _updateInfo.value = null }

    fun checkForUpdates() {
        viewModelScope.launch {
            say(withLang { lang ->
                L3("Checking for updates, sir…", "اپ ڈیٹس دیکھ رہا ہوں، سر…", "अपडेट देख रहा हूँ, सर…").pick(lang)
            }, speak = false)
            val result = withContext(Dispatchers.IO) { UpdateChecker.checkForUpdates(appCtx) }
            when (result) {
                is UpdateResult.UpToDate -> say(withLang { lang ->
                    L3("You're on the latest, sir — Baloch v${com.baloch.ai.update.VersionInfo.APP_VERSION_NAME}. ✅",
                        "آپ تازہ ترین پر ہیں، سر۔ ✅", "आप नवीनतम पर हैं, सर। ✅").pick(lang)
                })
                is UpdateResult.Available -> {
                    _updateInfo.value = result.release
                    say(withLang { lang ->
                        L3("Update found, sir — v${result.release.versionName}. Tap download when ready; I'll never install without your tap.",
                            "اپ ڈیٹ ملی، سر — v${result.release.versionName}۔", "अपडेट मिला, सर — v${result.release.versionName}।").pick(lang)
                    })
                }
                is UpdateResult.Offline -> say(withLang { lang ->
                    L3("Internet chahiye, sir — baad mein check kar lena.",
                        "انٹرنیٹ چاہیے، سر — بعد میں دیکھ لینا۔",
                        "इंटरनेट चाहिए, सर — बाद में देख लेना।").pick(lang)
                })
                is UpdateResult.Error -> say(result.msg)
            }
        }
    }

    fun downloadUpdate() {
        val info = _updateInfo.value ?: return
        _updateInfo.value = null
        UpdateChecker.downloadAndPromptInstall(appCtx, info)
        say(withLang { lang ->
            L3("Downloading, sir — the system will ask you to install when it's ready.",
                "ڈاؤن لوڈ ہو رہا ہے، سر۔", "डाउनलोड हो रहा है, सर।").pick(lang)
        }, speak = false)
    }

    companion object {
        private val WELCOME = L3(
            en = "Systems online, sir. I understand English, اردو and हिन्दी — tap the mic or type below.",
            ur = "سسٹم آن لائن ہے، سر۔ میں انگریزی، اردو اور ہندی سمجھتا ہوں — مائیک دبائیں یا نیچے لکھیں۔",
            hi = "सिस्टम ऑनलाइन है, सर। मैं अंग्रेज़ी, उर्दू और हिन्दी समझता हूँ — माइक दबाएँ या नीचे लिखें।",
        )
        private val MISHEARD = L3(
            en = "I didn't catch that, sir — one more time?",
            ur = "میں سن نہیں پایا، سر — ایک بار پھر؟",
            hi = "मैं सुन नहीं पाया, सर — एक बार फिर?",
        )
    }
}
