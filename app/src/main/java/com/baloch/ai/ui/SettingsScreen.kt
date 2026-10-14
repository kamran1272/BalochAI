package com.baloch.ai.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.gesture.GestureControlService
import com.baloch.ai.gesture.SensorGestureService
import com.baloch.ai.life.LifeActions
import com.baloch.ai.life.LifeSettings
import com.baloch.ai.life.LifeUiState
import com.baloch.ai.settings.BalochMode
import com.baloch.ai.settings.BrainMode
import com.baloch.ai.ui.components.BackLabel
import com.baloch.ai.ui.theme.BalochAmber
import com.baloch.ai.ui.theme.BalochBlack
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochCyanBright
import com.baloch.ai.ui.theme.BalochCyanDim
import com.baloch.ai.ui.theme.BalochPanel
import com.baloch.ai.ui.theme.BalochPanelBorder
import com.baloch.ai.ui.theme.BalochText
import com.baloch.ai.ui.theme.BalochTextDim

/**
 * How often Baloch's companion check-ins may surface (JARVIS mode only;
 * Manual mode silences all proactivity regardless).
 */
enum class CheckInFrequency { OFF, SUBTLE, NORMAL }

/**
 * Everything the Settings screen displays. The parent owns this state
 * (e.g. in a ViewModel) and persists every change inside the matching
 * [SettingsActions] callback — this screen never writes prefs itself.
 *
 * Wiring map (state field → source of truth):
 * - activeLanguage → LanguageManager / BalochLanguage
 * - quietVoiceMode → whisper/quiet-voice prefs
 * - voiceLockEnabled/Enrolled → VoiceLockManager
 * - biometricAvailable/LockOn → BiometricLock
 * - rulesCount → GuidanceRuleStore.all().size
 * - mode → ModeManager
 * - checkInFrequency → companion check-in prefs
 * - gestureControlEnabled → GestureControlService.isMasterEnabled()
 * - gestureCalibrationDone → GestureCalibrator.isCalibrated()
 * - gestureStatus → GestureControlService.GestureStatus via onStatusChange
 * - sensor* → SensorGestureService prefs
 * - scheduledTaskCount → TaskStore; routinesCount → RoutineStore
 * - batteryGuardianOn → BatteryGuardianWorker prefs
 * - iceContact → EmergencyStore
 * - permissionStatuses → ContextCompat.checkSelfPermission per key
 *   (parent re-queries on resume — the Grant buttons leave the app)
 * - brainMode → AiBrainPrefs (RULES_ONLY / HYBRID)
 * - aiModelDownloaded/aiModelSizeBytes → LlmModelManager
 * - aiModelDownloadProgress → non-null while a download coroutine runs
 * - aiModelRamGb/aiModelRamOk → LlmModelManager.deviceRamGb()/ramOk()
 */
data class SettingsState(
    val activeLanguage: BalochLanguage = BalochLanguage.ENGLISH,
    val quietVoiceMode: Boolean = true,
    val voiceLockEnabled: Boolean = false,
    val voiceLockEnrolled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val biometricLockOn: Boolean = false,
    val rulesCount: Int = 0,
    val mode: BalochMode = BalochMode.MANUAL,
    val checkInFrequency: CheckInFrequency = CheckInFrequency.NORMAL,
    val gestureControlEnabled: Boolean = false,
    val gestureCalibrationDone: Boolean = false,
    /** e.g. "ACTIVE", "LOW LIGHT", "CAMERA BUSY", "NOT CALIBRATED", "OFF". */
    val gestureStatus: String = "OFF",
    val sensorGesturesEnabled: Boolean = false,
    val sensorWaveOn: Boolean = true,
    val sensorFlipOn: Boolean = true,
    val sensorShakeOn: Boolean = true,
    val sensorPickupOn: Boolean = false,
    val sensorFlipDndOn: Boolean = false,
    /** e.g. "ACTIVE", "OFF". */
    val sensorStatus: String = "OFF",
    val scheduledTaskCount: Int = 0,
    val routinesCount: Int = 0,
    val batteryGuardianOn: Boolean = true,
    val iceContact: String = "",
    /** Keyed by Manifest.permission.* constants (see PERMISSION_ROWS). */
    val permissionStatuses: Map<String, Boolean> = emptyMap(),
    // ---------- AI BRAIN 🧠 (see AI-BRAIN-ADD.md for the wiring contract) ----------
    /** RULES_ONLY = instant rule engine; HYBRID = rules first, on-device LLM on miss. */
    val brainMode: BrainMode = BrainMode.HYBRID,
    /** True when gemma-2b-it-cpu-int4.bin is fully present in app storage. */
    val aiModelDownloaded: Boolean = false,
    /** 0f..1f while downloading, null when idle. */
    val aiModelDownloadProgress: Float? = null,
    /** Bytes of the model file (0 when absent) — display via LlmModelManager.sizeLabel. */
    val aiModelSizeBytes: Long = 0L,
    /** Total device RAM in GB (honest, from ActivityManager). */
    val aiModelRamGb: Double = 0.0,
    /** False on < 4 GB devices — download button must be hidden/disabled. */
    val aiModelRamOk: Boolean = true,
)

/**
 * One callback per control. EVERY toggle/row has a real callback — no dead
 * switches. Persistence is the parent's job: each callback must write the
 * new value to its store (DataStore/prefs/manager) and refresh [SettingsState].
 */
interface SettingsActions {
    /** Back out of Settings. */
    fun onBack()

    /**
     * Active language changed. Contract: persist via LanguageManager,
     * retarget STT/TTS locales (ur-PK/hi-IN/en-US), refresh state.
     */
    fun onLanguageChange(language: BalochLanguage)

    /**
     * Quiet-voice mode. Contract: persist; mic gain + wake thresholds adapt.
     * Baloch replies softly when Kamran whispers.
     */
    fun onQuietVoiceModeChange(enabled: Boolean)

    /** Open the standalone VoiceSettingsScreen (voice identity + volume). */
    fun onOpenVoiceSettings()

    /** Open the standalone VoiceLockSetupScreen (enrollment + PIN). */
    fun onOpenVoiceLockSetup()

    /**
     * Biometric app lock quick-toggle. Contract: same backing store as the
     * toggle inside VoiceLockSetupScreen (BiometricLock) — the two stay in
     * sync because the parent is the single writer.
     */
    fun onBiometricLockChange(enabled: Boolean)

    /** Open the standalone RulesScreen ("My Rules"). */
    fun onOpenRules()

    /**
     * Default mode. Contract: persist via ModeManager; gates ALL background
     * proactivity (JARVIS = speaks up, MANUAL = only answers).
     */
    fun onDefaultModeChange(mode: BalochMode)

    /** Companion check-in frequency. Contract: persist; honoured in JARVIS mode only. */
    fun onCheckInFrequencyChange(frequency: CheckInFrequency)

    /**
     * Camera-gesture master toggle. Contract: call
     * GestureControlService.setMasterEnabled(ctx, enabled). Default OFF —
     * this is Kamran's choice, always. If enabling without calibration, the
     * service idles in NOT_CALIBRATED until he calibrates.
     */
    fun onGestureControlChange(enabled: Boolean)

    /**
     * Start the mandatory palm-5s + fist-5s calibration flow (parent shows
     * the calibration UI, feeds GestureCalibrator with the service's
     * latestSample). On success refresh gestureCalibrationDone=true.
     */
    fun onCalibrateGestures()

    /** Open the gesture remap UI (GestureMap entries, per-gesture command text). */
    fun onOpenGestureRemap()

    /** Open the Gesture Lab (live preview + skeleton overlay). */
    fun onOpenGestureLab()

    /**
     * Sensor-gesture master toggle. Contract: call
     * SensorGestureService.setEnabled(ctx, enabled). Independent from the
     * camera toggle — sensors work in the dark, screen off.
     */
    fun onSensorGesturesChange(enabled: Boolean)

    /** Proximity wave on/off. Contract: SensorGestureService.setGestureOn(KEY_WAVE). */
    fun onSensorWaveChange(enabled: Boolean)

    /** Flip-to-silence on/off. Contract: setGestureOn(KEY_FLIP). */
    fun onSensorFlipChange(enabled: Boolean)

    /** Shake on/off. Contract: setGestureOn(KEY_SHAKE). */
    fun onSensorShakeChange(enabled: Boolean)

    /**
     * Pick-up-to-wake on/off (default off). Contract: setGestureOn(KEY_PICK_UP).
     * When on, lifting the phone chimes "Ji, sir?" — nothing executes.
     */
    fun onSensorPickupChange(enabled: Boolean)

    /**
     * "Flip also toggles DND". Contract: persist KEY_FLIP_DND; face-down
     * routes "dnd on" through the normal command path, face-up routes "dnd off".
     */
    fun onSensorFlipDndChange(enabled: Boolean)

    /** Open the sensor-gesture remap UI (SensorGestureMap.REMAPPABLE). */
    fun onOpenSensorRemap()

    /** Open the standalone MemoryScreen (Total Memory). */
    fun onOpenMemory()

    /**
     * Backup & export entry. Contract: parent triggers the encrypted
     * memory export (MemoryBackup → share/save file) / import picker.
     */
    fun onMemoryBackup()

    /** Open the standalone ScheduleScreen. */
    fun onOpenScheduled()

    /** Open the routines screen (RoutineStore). */
    fun onOpenRoutines()

    /** Battery guardian. Contract: persist; enables BatteryGuardianWorker alerts. */
    fun onBatteryGuardianChange(enabled: Boolean)

    /**
     * ICE contact saved. Contract: persist via EmergencyStore (E.164 with
     * country code). Baloch calls this number on "emergency" — always with
     * confirmation first.
     */
    fun onIceContactSave(number: String)

    /**
     * Check for app updates. Contract: parent runs UpdateChecker against the
     * GitHub releases feed (online only), shows changelog highlights, and
     * offers the APK download — never auto-installs.
     */
    fun onCheckForUpdates()

    /** Open the privacy dashboard ("What Baloch accessed" audit log). */
    fun onOpenPrivacyDashboard()

    // ------------------------------------------------------------------
    // AI BRAIN 🧠 — every callback below is wired by the parent to a real
    // LlmModelManager / AiBrainPrefs function. No dead switches.
    // ------------------------------------------------------------------

    /**
     * Brain mode selector. Contract: persist via AiBrainPrefs.brainMode and
     * refresh state; [HybridBrain] reads the same prefs through the lambda
     * the parent passed at construction, so the switch takes effect on the
     * very next message.
     */
    fun onBrainModeChange(mode: BrainMode)

    /**
     * "Download model" button. Contract: parent launches
     * `viewModelScope.launch { modelManager.downloadModel { p ->
     * state.aiModelDownloadProgress = p } }`; on each DownloadResult:
     * Success → toast/say + refresh; BlockedLowRam → honest "4GB+ RAM
     * chahiye" message (button should already be hidden when !aiModelRamOk);
     * NeedsManualImport → explain the one-time Kaggle download + offer
     * onImportAiModelFile; NetworkError → show reason, keep .part for
     * resume. RAM-guard lives INSIDE LlmModelManager — the UI never
     * re-implements it.
     */
    fun onDownloadAiModel()

    /**
     * Cancel an in-flight model download. Contract: parent cancels the
     * coroutine Job started by onDownloadAiModel (the .part file is kept
     * for resume) and resets aiModelDownloadProgress to null.
     */
    fun onCancelAiModelDownload()

    /**
     * Delete the downloaded model. Contract: parent shows a confirm dialog
     * first ("~1.35 GB freed, AI brain falls back to rules"), then calls
     * LlmModelManager.deleteModel() and refreshes state. The brain keeps
     * working — HybridBrain degrades to rules + companion fallback.
     */
    fun onDeleteAiModel()

    /**
     * Manual model import (Kaggle/HF sign-in-gate path). Contract: parent
     * opens a Storage Access Framework picker (any file type), then calls
     * `modelManager.importModelFile(uri) { p -> progress }`; InvalidFile
     * results are shown verbatim — they already name the expected size.
     */
    fun onImportAiModelFile()
}

private enum class GestureTab { CAMERA, SENSOR }

private data class PermissionInfo(val key: String, val title: String, val why: String)

private val PERMISSION_ROWS = listOf(
    PermissionInfo(
        Manifest.permission.CAMERA, "Camera",
        "Hand-gesture control watches your hand on-device. No video is stored or uploaded — ever."),
    PermissionInfo(
        Manifest.permission.RECORD_AUDIO, "Microphone",
        "Voice commands, wake word and voice lock. Processed on-device."),
    PermissionInfo(
        Manifest.permission.POST_NOTIFICATIONS, "Notifications",
        "Briefings, reminders, and the gesture-active indicator."),
    PermissionInfo(
        Manifest.permission.SCHEDULE_EXACT_ALARM, "Exact alarms",
        "Scheduled tasks and reminders that fire exactly on time."),
    PermissionInfo(
        Manifest.permission.CALL_PHONE, "Phone calls",
        "Emergency ICE calls — always with your confirmation first."),
    PermissionInfo(
        Manifest.permission.READ_CONTACTS, "Contacts",
        "Find who to call or message by name."),
    PermissionInfo(
        Manifest.permission.PACKAGE_USAGE_STATS, "Usage access",
        "App-usage insights for the pro pack. Special access — granted in system Settings."),
    PermissionInfo(
        Manifest.permission.ACCESS_NOTIFICATION_POLICY, "Do-not-disturb access",
        "Meeting-mode DND control. Special access — granted in system Settings."),
)

/**
 * PROFESSIONAL SETTINGS — mission control.
 *
 * One scrollable column of section cards: 🗣️ Voice, 🔐 Security,
 * 🤖 Behaviour, 👆 Gestures (Camera/Sensor tabs), 🧠 Memory, ⏰ Automation,
 * 🆘 Emergency, 🔒 Privacy & Permissions, ℹ️ About. Entries that need a full
 * screen navigate out via callbacks (onOpen*); everything else is inline.
 * Holographic-cyan identity throughout.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    actions: SettingsActions,
    lifeState: LifeUiState? = null,
    lifeActions: LifeActions? = null,
    powerState: PowerUiState? = null,
    powerActions: PowerActions? = null,
) {
    val mono = FontFamily.Monospace
    var gestureTab by remember { mutableStateOf(GestureTab.CAMERA) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BalochBlack)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── Header ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackLabel(onBack = { actions.onBack() })
            Text(
                text = "◈ SETTINGS",
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = BalochCyan,
            )
        }
        Text(
            text = "Mission control — every switch here works, sir.",
            fontFamily = mono,
            fontSize = 12.sp,
            color = BalochTextDim,
        )

        // ── 🗣️ VOICE ──
        SettingsSection("🗣️", "VOICE", "Language, quiet hearing, voice identity.") {
            Text("LANGUAGE", fontFamily = mono, fontSize = 11.sp,
                color = BalochTextDim, letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            SegmentedRow(
                options = BalochLanguage.entries,
                selected = state.activeLanguage,
                label = { it.displayName },
                onSelect = actions::onLanguageChange,
            )
            Spacer(Modifier.height(10.dp))
            SettingsToggleRow(
                title = "Quiet voice mode",
                subtitle = "Catches whispers and soft speech — and replies softly when you whisper.",
                checked = state.quietVoiceMode,
                onChecked = actions::onQuietVoiceModeChange,
            )
            SettingsNavRow(
                title = "Voice identity & volume",
                subtitle = "Locked male voice, preview, loudness (default FULL).",
                onClick = actions::onOpenVoiceSettings,
            )
        }

        // ── 🔐 SECURITY ──
        SettingsSection("🔐", "SECURITY", "Who Baloch obeys, and your boundaries.") {
            SettingsNavRow(
                title = "Voice Lock setup",
                subtitle = voiceLockSubtitle(state),
                onClick = actions::onOpenVoiceLockSetup,
            )
            SettingsNavRow(
                title = "My Rules",
                subtitle = "${state.rulesCount} rules — your words, your boundaries.",
                onClick = actions::onOpenRules,
            )
            SettingsToggleRow(
                title = "Biometric app lock",
                subtitle = if (state.biometricAvailable)
                    "Fingerprint / face (or device PIN) required to open Baloch."
                else "Not available on this device.",
                checked = state.biometricLockOn,
                enabled = state.biometricAvailable,
                onChecked = actions::onBiometricLockChange,
            )
        }

        // ── 🤖 BEHAVIOUR ──
        SettingsSection("🤖", "BEHAVIOUR", "How proactive Baloch is allowed to be.") {
            Text("DEFAULT MODE", fontFamily = mono, fontSize = 11.sp,
                color = BalochTextDim, letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            SegmentedRow(
                options = listOf(BalochMode.MANUAL, BalochMode.JARVIS),
                selected = state.mode,
                label = { if (it == BalochMode.MANUAL) "MANUAL" else "JARVIS" },
                onSelect = actions::onDefaultModeChange,
            )
            Text(
                text = if (state.mode == BalochMode.MANUAL)
                    "Manual: Baloch only answers. Nothing proactive — ever."
                else "JARVIS: Baloch speaks up — briefings, alerts, routine triggers.",
                fontFamily = mono, fontSize = 11.sp, color = BalochTextDim,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text("CHECK-IN FREQUENCY", fontFamily = mono, fontSize = 11.sp,
                color = BalochTextDim, letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            SegmentedRow(
                options = CheckInFrequency.entries,
                selected = state.checkInFrequency,
                label = {
                    when (it) {
                        CheckInFrequency.OFF -> "Off"
                        CheckInFrequency.SUBTLE -> "Subtle"
                        CheckInFrequency.NORMAL -> "Normal"
                    }
                },
                onSelect = actions::onCheckInFrequencyChange,
            )
            Text(
                text = "Companionship note: Baloch checks in warm and brief — never nagging. " +
                    "Frequency above; fully silent in Manual mode.",
                fontFamily = mono, fontSize = 11.sp, color = BalochTextDim,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // ── 👆 GESTURES ──
        SettingsSection("👆", "GESTURES", "Camera hands + sensor waves. Your toggle, always.") {
            SegmentedRow(
                options = GestureTab.entries,
                selected = gestureTab,
                label = { if (it == GestureTab.CAMERA) "📷 Camera" else "📳 Sensor" },
                onSelect = { gestureTab = it },
            )
            Spacer(Modifier.height(10.dp))
            if (gestureTab == GestureTab.CAMERA) {
                CameraGestureRows(state, actions)
            } else {
                SensorGestureRows(state, actions)
            }
        }

        // ── 🧠 MEMORY ──
        SettingsSection("🧠", "MEMORY", "What Baloch remembers about you.") {
            SettingsNavRow(
                title = "Total Memory",
                subtitle = "Facts, people, tiers — search, pin, forget.",
                onClick = actions::onOpenMemory,
            )
            SettingsNavRow(
                title = "Backup & export",
                subtitle = "Encrypted backup file — your memories, portable.",
                onClick = actions::onMemoryBackup,
            )
            InfoLine(
                "Auto-expiry: temporary facts fade after 30 days (Memory Janitor); " +
                    "permanent ones stay until you delete them."
            )
        }

        // ── ⏰ AUTOMATION ──
        SettingsSection("⏰", "AUTOMATION", "Things Baloch does on a schedule.") {
            SettingsNavRow(
                title = "Scheduled tasks",
                subtitle = "${state.scheduledTaskCount} tasks — one-time and recurring.",
                onClick = actions::onOpenScheduled,
            )
            SettingsNavRow(
                title = "Routines",
                subtitle = "${state.routinesCount} routines — good-night, meeting mode, custom.",
                onClick = actions::onOpenRoutines,
            )
            SettingsToggleRow(
                title = "Battery guardian",
                subtitle = "Warns before the battery dies at the wrong moment.",
                checked = state.batteryGuardianOn,
                onChecked = actions::onBatteryGuardianChange,
            )
        }

        // ── 🌿 LIFE ──
        if (lifeState != null && lifeActions != null) {
            SettingsSection("🌿", "LIFE", "Prayer, clients, expenses, focus — daily life, handled.") {
                PrayerAlertsSection(
                    state = lifeState.prayer,
                    onToggle = lifeActions::onPrayerAlertsChanged,
                    onOpenQibla = lifeActions::onOpenQibla,
                )
                CopilotNudgesSection(
                    state = lifeState.copilot,
                    onToggle = lifeActions::onCopilotNudgesChanged,
                    onOpenClients = lifeActions::onOpenClients,
                )
                ExpenseSection(
                    state = lifeState.expense,
                    currencies = LifeSettings.CURRENCIES,
                    onCurrency = lifeActions::onExpenseCurrencyChanged,
                    onOpenLog = lifeActions::onOpenExpenseLog,
                )
                FocusDefaultsSection(
                    state = lifeState.focus,
                    onFocusMin = lifeActions::onFocusMinChanged,
                    onBreakMin = lifeActions::onBreakMinChanged,
                    onStartNow = lifeActions::onStartFocusNow,
                )
                LocationRemindersSection(
                    state = lifeState.geo,
                    onToggle = lifeActions::onGeoRemindersChanged,
                    onGrantLocation = lifeActions::onGrantLocation,
                    onOpenAdd = lifeActions::onOpenAddGeoReminder,
                )
                TranslatorSection(
                    state = lifeState.translator,
                    onAutoDownload = lifeActions::onTranslatorAutoDownloadChanged,
                    onDownloadNow = lifeActions::onDownloadTranslatorNow,
                )
                SosSetupSection(
                    state = lifeState.sos,
                    onSaveIce = lifeActions::onSaveSosIce,
                    onGrantSms = lifeActions::onGrantSms,
                )
            }
        }

        // ── ⚡ POWER FEATURES ──
        if (powerState != null && powerActions != null) {
            SettingsSection("⚡", "POWER FEATURES", "Dormant superpowers — all off by default, your call.") {
                DrivingSection(powerState, powerActions)
                RamadanSection(powerState, powerActions)
                CricketSection(powerState, powerActions)
                CurrencySection(powerState, powerActions)
                PetrolSection(powerState, powerActions)
                WordSection(powerState, powerActions)
                RecorderSection(
                    powerState,
                    powerActions,
                    recordings = emptyList(),
                    playingPath = null,
                    onPlayFile = {},
                    onStopPlayback = {},
                    onDeleteFile = {},
                )
                ChargeSection(powerState, powerActions)
                RainSection(powerState, powerActions)
                AppLockSection(powerState, powerActions)
            }
        }

        // ── 🆘 EMERGENCY ──
        SettingsSection("🆘", "EMERGENCY", "One number Baloch can call for you.") {
            // Bare Modifier: the section card already pads; IceContactRow's
            // default padding is for standalone placement only.
            IceContactRow(
                current = state.iceContact,
                onSave = actions::onIceContactSave,
                modifier = Modifier,
            )
        }

        // ── 🔒 PRIVACY & PERMISSIONS ──
        SettingsSection("🔒", "PRIVACY & PERMISSIONS",
            "Why Baloch wants each permission. Grant opens the app's system settings — flip it there, come back.") {
            PERMISSION_ROWS.forEach { info ->
                PermissionRow(
                    info = info,
                    granted = state.permissionStatuses[info.key] == true,
                )
            }
            InfoLine(
                "On-device first: your voice, hands and memories never leave this phone. " +
                    "No video is ever stored — camera frames are classified and discarded."
            )
            SettingsNavRow(
                title = "Privacy dashboard",
                subtitle = "What Baloch accessed — every private-data touch, with timestamps.",
                onClick = actions::onOpenPrivacyDashboard,
            )
        }

        // ── ℹ️ ABOUT ──
        SettingsSection("ℹ️", "ABOUT", "") {
            Text(
                text = "Baloch v${com.baloch.ai.update.VersionInfo.APP_VERSION_NAME} — Built by the Baloch Factory for Kamran.",
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = BalochCyan,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Holographic Cyan edition. Your data stays yours.",
                fontFamily = mono,
                fontSize = 11.sp,
                color = BalochTextDim,
            )
            Spacer(Modifier.height(8.dp))
            SettingsNavRow(
                title = "Check for updates",
                subtitle = "Queries the BalochAI releases feed (needs internet). Never auto-installs — always your tap.",
                onClick = actions::onCheckForUpdates,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ── Gestures: camera tab ──────────────────────────────────────────
@Composable
private fun CameraGestureRows(state: SettingsState, actions: SettingsActions) {
    val mono = FontFamily.Monospace
    SettingsToggleRow(
        title = "Camera hand gestures",
        subtitle = if (state.gestureControlEnabled)
            "ON — front camera watches for your hand."
        else "OFF — the camera stays released.",
        checked = state.gestureControlEnabled,
        onChecked = actions::onGestureControlChange,
    )
    StatusLine(label = "STATUS", value = state.gestureStatus)
    SettingsNavRow(
        title = "Calibrate gestures",
        subtitle = if (state.gestureCalibrationDone)
            "Calibrated ✓ — tap to re-run (palm 5s + fist 5s)."
        else "REQUIRED before gestures fire — palm 5s + fist 5s.",
        onClick = actions::onCalibrateGestures,
        highlight = !state.gestureCalibrationDone,
    )
    SettingsNavRow(
        title = "Remap gestures",
        subtitle = "Palm → wake baloch, fist → pause media… change any mapping.",
        onClick = actions::onOpenGestureRemap,
    )
    SettingsNavRow(
        title = "Gesture Lab",
        subtitle = "See what the camera sees — live skeleton preview.",
        onClick = actions::onOpenGestureLab,
    )
    InfoLine(
        "📷 Camera indicator: while active, a persistent \"👆 Gesture control active\" " +
            "notification shows — the camera is never on silently."
    )
    InfoLine(GestureControlService.BATTERY_NOTE)
    InfoLine(
        "Works best in decent light at arm's length, sir 💡 — pitch-dark rooms " +
            "and extreme hand angles are physics, not bugs."
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = "Hold a pose ~1s to fire (anti-flicker); 1.5s cooldown between triggers.",
        fontFamily = mono, fontSize = 11.sp, color = BalochTextDim,
    )
}

// ── Gestures: sensor tab ──────────────────────────────────────────
@Composable
private fun SensorGestureRows(state: SettingsState, actions: SettingsActions) {
    SettingsToggleRow(
        title = "Sensor gestures",
        subtitle = if (state.sensorGesturesEnabled)
            "ON — wave, flip and shake are listening."
        else "OFF — sensors stay quiet.",
        checked = state.sensorGesturesEnabled,
        onChecked = actions::onSensorGesturesChange,
    )
    StatusLine(label = "STATUS", value = state.sensorStatus)
    SettingsToggleRow(
        title = "Wave",
        subtitle = "Proximity wave (near→far→near): wake Baloch · silence ringing · play/pause. Works in the dark, screen off.",
        checked = state.sensorWaveOn,
        onChecked = actions::onSensorWaveChange,
    )
    SettingsToggleRow(
        title = "Flip to silence",
        subtitle = "Face-down silences ringing. Pocket-safe: suppressed when the proximity sensor is covered.",
        checked = state.sensorFlipOn,
        onChecked = actions::onSensorFlipChange,
    )
    SettingsToggleRow(
        title = "Flip also toggles DND",
        subtitle = "Face-down → DND on; face-up → DND off. Routed through your normal confirmations.",
        checked = state.sensorFlipDndOn,
        onChecked = actions::onSensorFlipDndChange,
    )
    SettingsToggleRow(
        title = "Shake",
        subtitle = "Two sharp shakes → your mapped action (default: flashlight). 3s cooldown.",
        checked = state.sensorShakeOn,
        onChecked = actions::onSensorShakeChange,
    )
    SettingsToggleRow(
        title = "Pick up to wake",
        subtitle = "Lift the phone → Baloch chimes \"Ji, sir?\" Nothing executes. Default off.",
        checked = state.sensorPickupOn,
        onChecked = actions::onSensorPickupChange,
    )
    SettingsNavRow(
        title = "Remap sensor gestures",
        subtitle = "Wave, flip, shake → any command text.",
        onClick = actions::onOpenSensorRemap,
    )
    InfoLine(SensorGestureService.BATTERY_NOTE)
}

// ── Building blocks ───────────────────────────────────────────────
@Composable
private fun SettingsSection(
    icon: String,
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    val mono = FontFamily.Monospace
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BalochPanel),
        border = BorderStroke(1.dp, BalochPanelBorder),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "$icon  $title",
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = BalochCyan,
                letterSpacing = 1.sp,
            )
            if (description.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    fontFamily = mono,
                    fontSize = 11.sp,
                    color = BalochTextDim,
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingsNavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    highlight: Boolean = false,
) {
    val mono = FontFamily.Monospace
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .border(
                1.dp,
                if (highlight) BalochAmber else BalochPanelBorder,
                RoundedCornerShape(10.dp),
            )
            .padding(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, fontFamily = mono, fontSize = 13.sp, color = BalochText)
            Text(text = subtitle, fontFamily = mono, fontSize = 11.sp, color = BalochTextDim)
        }
        Spacer(Modifier.width(8.dp))
        Text(text = "›", fontFamily = mono, fontSize = 20.sp, color = BalochCyan)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    val mono = FontFamily.Monospace
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BalochBlack.copy(alpha = 0.35f))
            .padding(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, fontFamily = mono, fontSize = 13.sp,
                color = if (enabled) BalochText else BalochTextDim)
            Text(text = subtitle, fontFamily = mono, fontSize = 11.sp, color = BalochTextDim)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BalochBlack,
                checkedTrackColor = BalochCyan,
                uncheckedThumbColor = BalochTextDim,
                uncheckedTrackColor = BalochPanelBorder,
                checkedBorderColor = BalochCyan,
                uncheckedBorderColor = BalochPanelBorder,
            ),
        )
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun <T> SegmentedRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val mono = FontFamily.Monospace
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, BalochPanelBorder, RoundedCornerShape(10.dp)),
    ) {
        options.forEach { option ->
            val isSel = option == selected
            Text(
                text = label(option),
                fontFamily = mono,
                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp,
                color = if (isSel) BalochBlack else BalochTextDim,
                modifier = Modifier
                    .weight(1f)
                    .background(if (isSel) BalochCyan else BalochBlack.copy(alpha = 0.35f))
                    .clickable { onSelect(option) }
                    // 12sp ≈ 16dp + 32dp padding = 48dp minimum touch target.
                    .padding(vertical = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun StatusLine(label: String, value: String) {
    val mono = FontFamily.Monospace
    val color = when (value.uppercase()) {
        "ACTIVE" -> BalochCyan
        "LOW LIGHT" -> BalochAmber
        "CAMERA BUSY" -> BalochAmber
        "NOT CALIBRATED" -> BalochAmber
        else -> BalochTextDim
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Text(text = "●", fontSize = 10.sp, color = color)
        Spacer(Modifier.width(8.dp))
        Text(text = label, fontFamily = mono, fontSize = 11.sp, color = BalochTextDim)
        Spacer(Modifier.width(8.dp))
        Text(
            text = value, fontFamily = mono, fontWeight = FontWeight.Bold,
            fontSize = 12.sp, color = color,
        )
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun InfoLine(text: String) {
    val mono = FontFamily.Monospace
    Text(
        text = text,
        fontFamily = mono,
        fontSize = 11.sp,
        color = BalochTextDim,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun PermissionRow(info: PermissionInfo, granted: Boolean) {
    val mono = FontFamily.Monospace
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = if (granted) "●" else "○",
            fontSize = 10.sp,
            color = if (granted) BalochCyan else BalochAmber,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(text = info.title, fontFamily = mono, fontSize = 13.sp, color = BalochText)
            Text(text = info.why, fontFamily = mono, fontSize = 11.sp, color = BalochTextDim)
        }
        Spacer(Modifier.width(8.dp))
        if (granted) {
            Text(
                text = "✓ GRANTED",
                fontFamily = mono, fontSize = 10.sp, color = BalochCyan,
                letterSpacing = 1.sp,
            )
        } else {
            Button(
                onClick = {
                    // Deep-link to THIS app's system settings page.
                    val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .apply { data = Uri.fromParts("package", context.packageName, null) }
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BalochCyanDim, contentColor = BalochCyanBright),
            ) {
                Text("GRANT", fontFamily = mono, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun voiceLockSubtitle(state: SettingsState): String =
    when {
        state.voiceLockEnabled -> "ON — Baloch obeys only your voice."
        state.voiceLockEnrolled -> "Enrolled — tap to arm the lock."
        else -> "Not enrolled — 3 voice samples + PIN."
    }
