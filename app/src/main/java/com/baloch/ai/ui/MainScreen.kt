package com.baloch.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import com.baloch.ai.life.SosHelper
import com.baloch.ai.life.qiblaBearing
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.baloch.ai.R
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.settings.BalochMode
import com.baloch.ai.ui.components.AgentChipRow
import com.baloch.ai.ui.components.AgentChipState
import com.baloch.ai.ui.components.BalochOrb
import com.baloch.ai.ui.components.ChatBubble
import com.baloch.ai.ui.components.ConfirmDialog
import com.baloch.ai.ui.components.ModeToggle
import com.baloch.ai.ui.components.OrbState
import com.baloch.ai.ui.components.ScanlineOverlay
import com.baloch.ai.ui.components.TypingIndicator
import com.baloch.ai.ui.theme.BalochBlack
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochPanel
import com.baloch.ai.ui.theme.BalochText
import com.baloch.ai.ui.theme.BalochTextDim

@Composable
fun MainScreen(viewModel: BalochViewModel) {
    val context = LocalContext.current
    val screen by viewModel.screen.collectAsState()

    // First-run gates.
    if (viewModel.showSplash.collectAsState().value) {
        SplashScreen(onDone = { viewModel.splashDone() })
        return
    }
    if (viewModel.showOnboarding.collectAsState().value) {
        PrivacyOnboarding(onAccept = { viewModel.onboardingAccepted() })
        return
    }
    // First-run feature tour (once) — after onboarding, before the main UI.
    if (viewModel.showTour.collectAsState().value) {
        val lang by viewModel.language.collectAsState()
        FeatureTour(lang = lang, onFinish = { viewModel.tourDone() })
        return
    }

    when (screen) {
        BalochScreen.Main -> MainContent(viewModel)
        BalochScreen.Settings -> SettingsScreen(
            state = viewModel.settingsState.collectAsState().value,
            actions = viewModel.settingsActions,
            lifeState = viewModel.lifeUiState.collectAsState().value,
            lifeActions = viewModel.lifeActions,
            powerState = viewModel.powerUiState.collectAsState().value,
            powerActions = viewModel.powerActions,
        )
        BalochScreen.Schedule -> ScheduleScreen(
            tasks = viewModel.scheduledTasks.collectAsState().value,
            onToggle = viewModel::toggleScheduledTask,
            onDelete = viewModel::deleteScheduledTask,
            onBack = viewModel::goBack,
        )
        BalochScreen.Memory -> MemoryScreenHost(viewModel)
        BalochScreen.Rules -> RulesScreen(
            state = RulesScreenState(viewModel.rules.collectAsState().value),
            onDelete = viewModel::deleteRule,
            onBack = viewModel::goBack,
        )
        BalochScreen.VoiceSettings -> VoiceSettingsScreen(
            state = viewModel.voiceSettings.collectAsState().value,
            onPickVoice = viewModel::pickVoice,
            onPreview = viewModel::previewVoice,
            onVolumeChange = viewModel::setVoiceVolume,
            onBack = viewModel::goBack,
        )
        BalochScreen.VoiceLock -> VoiceLockSetupScreen(
            state = viewModel.voiceLockState.collectAsState().value,
            actions = viewModel.voiceLockActions,
            modifier = Modifier,
        )
        BalochScreen.CallLog -> CallLogScreen(
            entries = viewModel.callLogEntries.collectAsState().value,
            isScreenerActive = false,
            hasCallLogPermission = viewModel.hasCallLogPerm.collectAsState().value,
            callbacks = viewModel.callLogCallbacks,
        )
        BalochScreen.GestureLab -> GestureLabScreen(
            state = viewModel.gestureLabState.collectAsState().value,
            actions = viewModel.gestureLabActions,
        )
        BalochScreen.GestureRemap -> GestureRemapScreen(
            cameraMap = viewModel.cameraRemap.collectAsState().value,
            sensorMap = viewModel.sensorRemap.collectAsState().value,
            onCameraRemap = viewModel::remapCameraGesture,
            onSensorRemap = viewModel::remapSensorGesture,
            onBack = viewModel::goBack,
        )
        BalochScreen.Routines -> RoutinesScreen(
            routines = viewModel.routines.collectAsState().value,
            onDelete = viewModel::deleteRoutine,
            onBack = viewModel::goBack,
        )
        BalochScreen.Privacy -> PrivacyDashboardScreen(
            events = viewModel.privacyEvents.collectAsState().value,
            onClear = viewModel::clearPrivacyLog,
            onBack = viewModel::goBack,
        )
        BalochScreen.Qibla -> {
            val loc = try { SosHelper.bestLocation(viewModel.appCtx) } catch (_: Exception) { null }
            val bearing = if (loc != null) qiblaBearing(loc.latitude, loc.longitude, 21.4225, 39.8262) else 0.0
            QiblaCompassScreen(
                azimuthDeg = null,
                qiblaBearingDeg = bearing,
                onBack = viewModel::goBack,
            )
        }
    }
}

/** Standalone privacy dashboard wrapping the PrivacySection component. */
@Composable
private fun PrivacyDashboardScreen(
    events: List<com.baloch.ai.privacy.PrivacyEvent>,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(BalochBlack).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = BalochCyan, fontSize = 24.sp,
                modifier = Modifier.clickable { onBack() }.padding(8.dp))
            Spacer(Modifier.width(8.dp))
            Text("PRIVACY DASHBOARD", color = BalochCyan, fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        com.baloch.ai.privacy.PrivacySection(
            events = events,
            onClear = onClear,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun MainContent(viewModel: BalochViewModel) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val input by viewModel.input.collectAsState()
    val listening by viewModel.listening.collectAsState()
    val orbState by viewModel.orbState.collectAsState()
    val lang by viewModel.language.collectAsState()
    val pending by viewModel.pendingConfirmation.collectAsState()
    val voiceOn by viewModel.voiceOn.collectAsState()
    val chips by viewModel.agentChips.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val exportRequest by viewModel.exportRequest.collectAsState()
    val importRequest by viewModel.importRequest.collectAsState()
    val voiceMode by viewModel.voiceMode.collectAsState()
    var showLangPicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Cinematic 400ms crossfade between CHAT MODE and VOICE MODE.
    AnimatedContent(
        targetState = voiceMode,
        transitionSpec = {
            (fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.96f))
                .togetherWith(fadeOut(tween(400)) + scaleOut(tween(400), targetScale = 0.96f))
        },
        label = "mode-switch",
    ) { vm ->
        if (vm) {
            VoiceModeScreen(viewModel)
        } else {
            ChatModeScreen(viewModel)
        }
    }
}

/**
 * VOICE MODE — pure JARVIS: fullscreen orb animation only, no chat chrome,
 * minimal status text. Auto-entered on voice activity; tap 💬 to return.
 */
@Composable
private fun VoiceModeScreen(viewModel: BalochViewModel) {
    val orbState by viewModel.orbState.collectAsState()
    val listening by viewModel.listening.collectAsState()
    val lang by viewModel.language.collectAsState()
    val lastReply by viewModel.messages.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(BalochBlack)) {
        // The hero: large centered orb.
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 120.dp),
            contentAlignment = Alignment.Center,
        ) {
            BalochOrb(
                state = if (listening) OrbState.LISTENING else orbState,
                modifier = Modifier.size(300.dp),
            )
        }
        // Minimal status text.
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val status = when {
                listening -> when (lang) {
                    BalochLanguage.ENGLISH -> "Listening…"
                    BalochLanguage.URDU -> "سن رہا ہوں…"
                    BalochLanguage.HINDI -> "सुन रहा हूँ…"
                }
                orbState == OrbState.THINKING -> "…"
                orbState == OrbState.SPEAKING -> lastReply.lastOrNull { !it.fromUser }?.text?.take(90) ?: ""
                else -> ""
            }
            if (status.isNotEmpty()) {
                Text(status, color = BalochCyan, fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 32.dp))
            }
        }
        // Minimal controls: stop mic / back to chat.
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("🎙️", fontSize = 26.sp,
                modifier = Modifier.clickable { viewModel.stopVoice() }.padding(12.dp))
            Text("💬", fontSize = 26.sp,
                modifier = Modifier.clickable { viewModel.setVoiceMode(false) }.padding(12.dp))
        }
        ScanlineOverlay()
    }
}

@Composable
private fun ChatModeScreen(viewModel: BalochViewModel) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val input by viewModel.input.collectAsState()
    val listening by viewModel.listening.collectAsState()
    val orbState by viewModel.orbState.collectAsState()
    val lang by viewModel.language.collectAsState()
    val pending by viewModel.pendingConfirmation.collectAsState()
    val voiceOn by viewModel.voiceOn.collectAsState()
    val chips by viewModel.agentChips.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val exportRequest by viewModel.exportRequest.collectAsState()
    val importRequest by viewModel.importRequest.collectAsState()
    var showLangPicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startVoice() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        viewModel.exportRequestConsumed()
        if (uri != null) viewModel.exportMemory(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        viewModel.importRequestConsumed()
        if (uri != null) viewModel.importMemory(uri)
    }
    // AI model file import (separate picker — accepts .bin/.task).
    val aiModelImportRequest by viewModel.aiModelImportRequest.collectAsState()
    val aiModelImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        viewModel.aiModelImportRequestConsumed()
        if (uri != null) viewModel.importAiModelFile(uri)
    }

    if (exportRequest) {
        exportLauncher.launch("baloch-memory-backup.enc")
    }
    if (importRequest) {
        importLauncher.launch(arrayOf("application/octet-stream"))
    }
    if (aiModelImportRequest) {
        aiModelImportLauncher.launch(arrayOf("*/*"))
    }

    fun onMicTap() {
        if (listening) {
            viewModel.stopVoice()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startVoice() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Box(modifier = Modifier.fillMaxSize().background(BalochBlack)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ---- Top bar ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("◈ BALOCH", color = BalochCyan, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Nav: scheduled + settings
                    Text("⏰", fontSize = 18.sp,
                        modifier = Modifier.clickable { viewModel.openScreen(BalochScreen.Schedule) }.padding(8.dp))
                    Text("🧠", fontSize = 18.sp,
                        modifier = Modifier.clickable { viewModel.openScreen(BalochScreen.Memory) }.padding(8.dp))
                    Text("⚙️", fontSize = 18.sp,
                        modifier = Modifier.clickable { viewModel.openScreen(BalochScreen.Settings) }.padding(8.dp))
                    Text(
                        text = if (voiceOn) "🔊" else "🔇",
                        modifier = Modifier.clickable { viewModel.toggleVoice() }.padding(8.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = lang.displayName,
                        color = BalochCyan,
                        modifier = Modifier
                            .border(1.dp, BalochCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { showLangPicker = !showLangPicker }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            // ---- Language picker ----
            if (showLangPicker) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BalochLanguage.entries.forEach { l ->
                        val selected = l == lang
                        Text(
                            text = l.displayName,
                            color = if (selected) BalochBlack else BalochCyan,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) BalochCyan else BalochPanel)
                                .border(1.dp, BalochCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setLanguage(l)
                                    showLangPicker = false
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ---- Mode toggle (MANUAL / JARVIS) ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                ModeToggle(
                    isJarvis = mode == BalochMode.JARVIS,
                    onToggle = { jarvis ->
                        viewModel.settingsActions.onDefaultModeChange(
                            if (jarvis) BalochMode.JARVIS else BalochMode.MANUAL
                        )
                    },
                )
            }
            Spacer(Modifier.height(4.dp))

            // ---- Chat ----
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                reverseLayout = true,
            ) {
                items(messages.reversed()) { msg -> ChatBubble(msg.fromUser, msg.text) }
            }

            // ---- Typing indicator while agents work ----
            if (orbState == OrbState.THINKING) {
                TypingIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            // ---- Orb ----
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                BalochOrb(state = if (listening) OrbState.LISTENING else orbState)
            }

            // ---- Agent status chips (multi-agent runs) ----
            if (chips.isNotEmpty()) {
                AgentChipRow(chips = chips.map {
                    AgentChipState(
                        name = it.name,
                        icon = it.icon,
                        state = when (it.state) {
                            com.baloch.ai.agents.ChipState.WORKING -> 0
                            com.baloch.ai.agents.ChipState.DONE -> 1
                            com.baloch.ai.agents.ChipState.FAILED -> 2
                        },
                    )
                })
            }

            if (listening) {
                Text(
                    text = when (lang) {
                        BalochLanguage.ENGLISH -> "Listening…"
                        BalochLanguage.URDU -> "سن رہا ہوں…"
                        BalochLanguage.HINDI -> "सुन रहा हूँ…"
                    },
                    color = BalochCyan,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp),
                )
            }

            // ---- Input row ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = input,
                    onValueChange = viewModel::onInputChange,
                    placeholder = {
                        Text(
                            when (lang) {
                                BalochLanguage.ENGLISH -> "Command, sir…"
                                BalochLanguage.URDU -> "حکم کریں، سر…"
                                BalochLanguage.HINDI -> "आदेश दें, सर…"
                            },
                            color = BalochTextDim,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Send,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = { viewModel.sendText() }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = BalochText,
                        unfocusedTextColor = BalochText,
                        cursorColor = BalochCyan,
                        focusedContainerColor = BalochPanel,
                        unfocusedContainerColor = BalochPanel,
                        focusedIndicatorColor = BalochCyan,
                        unfocusedIndicatorColor = BalochTextDim,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.width(8.dp))
                // Mic button
                IconButton(
                    onClick = { onMicTap() },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (listening) BalochCyan else Color.Transparent)
                        .border(1.dp, BalochCyan, CircleShape),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mic),
                        contentDescription = "Voice input",
                        tint = if (listening) BalochBlack else BalochCyan,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                // Send button
                IconButton(
                    onClick = { viewModel.sendText() },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(BalochCyan),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_send),
                        contentDescription = "Send",
                        tint = BalochBlack,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        // Hologram overlay on top of everything (touch-transparent).
        ScanlineOverlay()

        pending?.let { p ->
            ConfirmDialog(
                question = p.question,
                onConfirm = { viewModel.confirmPending() },
                onDismiss = { viewModel.dismissPending() },
            )
        }

        // Update available dialog.
        updateInfo?.let { info ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdate() },
                title = { Text("Update available — v${info.versionName}", color = BalochCyan) },
                text = {
                    Text(
                        info.changelog.take(800),
                        color = BalochText,
                        fontSize = 13.sp,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.downloadUpdate() }) {
                        Text("Download", color = BalochCyan)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissUpdate() }) {
                        Text("Later", color = BalochTextDim)
                    }
                },
                containerColor = BalochPanel,
            )
        }
    }
}
