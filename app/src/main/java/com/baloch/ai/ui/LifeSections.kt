package com.baloch.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baloch.ai.ui.theme.BalochAmber
import com.baloch.ai.ui.theme.BalochBlack
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochPanelBorder
import com.baloch.ai.ui.theme.BalochText
import com.baloch.ai.ui.theme.BalochTextDim

// ======================================================================
// LIFE SECTIONS — settings rows for the 8 life features.
//
// SETTINGS LAW: every toggle/row here is wired to a real persisted
// setting (see life/LifeSettings.kt). Zero dead switches.
//
// These are standalone: the parent splices them into the Settings
// screen, passing explicit state + callbacks, e.g.
//
//   val context = LocalContext.current
//   val life = remember { LifeSettings(context) }
//   val prayerOn by life.prayerAlerts.collectAsState(initial = false)
//   PrayerAlertsSection(
//       state = PrayerAlertsUiState(
//           enabled = prayerOn,
//           nextPrayerText = "...",
//       ),
//       onToggle = { v ->
//           scope.launch {
//               life.setPrayerAlerts(v)
//               if (v) PrayerAlertWorker.schedule(context)
//               else PrayerAlertWorker.cancel(context)
//           }
//       },
//       onOpenQibla = { /* navigate to the Qibla compass */ },
//   )
// ======================================================================

private val mono = FontFamily.Monospace

// ------------------------------------------------------------------
// Shared atoms (copied shape language from SettingsScreen rows)
// ------------------------------------------------------------------

@Composable
private fun LifeSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BalochBlack.copy(alpha = 0.35f))
            .border(1.dp, BalochPanelBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(text = title, fontFamily = mono, fontSize = 13.sp,
            fontWeight = FontWeight.Bold, color = BalochCyan)
        Spacer(Modifier.height(8.dp))
        content()
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun LifeToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, fontFamily = mono, fontSize = 13.sp, color = BalochText)
            Text(text = subtitle, fontFamily = mono, fontSize = 11.sp, color = BalochTextDim)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
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
}

@Composable
private fun LifeStatusLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, fontFamily = mono, fontSize = 11.sp, color = BalochTextDim,
            modifier = Modifier.weight(1f))
        Text(text = value, fontFamily = mono, fontSize = 11.sp, color = BalochText,
            textAlign = TextAlign.End)
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun <T> LifeSegmentedRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
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
                    .padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun LifeNavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .border(1.dp, BalochPanelBorder, RoundedCornerShape(10.dp))
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

// ------------------------------------------------------------------
// 1. Prayer alerts + Qibla entry
// ------------------------------------------------------------------

data class PrayerAlertsUiState(
    val enabled: Boolean,
    /** e.g. "Maghrib in 2h 14m" — parent computes from PrayerTimesApi. */
    val nextPrayerText: String,
)

@Composable
fun PrayerAlertsSection(
    state: PrayerAlertsUiState,
    onToggle: (Boolean) -> Unit,
    onOpenQibla: () -> Unit,
) {
    LifeSectionCard(title = "🕌 Prayer times & Qibla") {
        LifeToggleRow(
            title = "Prayer alerts",
            subtitle = "Notify at each prayer time (JARVIS mode only)",
            checked = state.enabled,
            onChecked = onToggle,
        )
        Spacer(Modifier.height(4.dp))
        LifeStatusLine(label = "Next prayer", value = state.nextPrayerText)
        Spacer(Modifier.height(4.dp))
        LifeNavRow(
            title = "🧭 Qibla compass",
            subtitle = "Live direction to the Kaaba",
            onClick = onOpenQibla,
        )
    }
}

// ------------------------------------------------------------------
// 2. Freelance copilot nudges
// ------------------------------------------------------------------

data class CopilotUiState(
    val nudgesEnabled: Boolean,
    val clientCount: Int,
    val staleCount: Int,
)

@Composable
fun CopilotNudgesSection(
    state: CopilotUiState,
    onToggle: (Boolean) -> Unit,
    onOpenClients: () -> Unit,
) {
    LifeSectionCard(title = "💼 Freelance copilot") {
        LifeToggleRow(
            title = "Follow-up nudges",
            subtitle = "Daily reminder for clients gone quiet (JARVIS mode only)",
            checked = state.nudgesEnabled,
            onChecked = onToggle,
        )
        Spacer(Modifier.height(4.dp))
        LifeStatusLine(label = "Tracked clients", value = state.clientCount.toString())
        LifeStatusLine(
            label = "Need follow-up",
            value = if (state.staleCount > 0) "${state.staleCount} ⚠" else "none 🎉",
        )
        Spacer(Modifier.height(4.dp))
        LifeNavRow(
            title = "Open client board",
            subtitle = "Add clients, log contacts, see the pipeline",
            onClick = onOpenClients,
        )
    }
}

// ------------------------------------------------------------------
// 3. Expenses — currency row
// ------------------------------------------------------------------

data class ExpenseUiState(
    val currency: String,
    /** e.g. "Rs 12,400 · 23 entries". */
    val monthlyTotalText: String,
)

@Composable
fun ExpenseSection(
    state: ExpenseUiState,
    currencies: List<String>,
    onCurrency: (String) -> Unit,
    onOpenLog: () -> Unit,
) {
    LifeSectionCard(title = "💰 Expense tracker") {
        Text(text = "Currency", fontFamily = mono, fontSize = 11.sp, color = BalochTextDim)
        Spacer(Modifier.height(4.dp))
        LifeSegmentedRow(
            options = currencies,
            selected = state.currency,
            label = { it },
            onSelect = onCurrency,
        )
        LifeStatusLine(label = "This month", value = state.monthlyTotalText)
        Spacer(Modifier.height(4.dp))
        LifeNavRow(
            title = "Expense log",
            subtitle = "Voice-logged entries, monthly hisaab",
            onClick = onOpenLog,
        )
    }
}

// ------------------------------------------------------------------
// 4. Focus mode defaults
// ------------------------------------------------------------------

data class FocusUiState(
    val focusMin: Int,
    val breakMin: Int,
    val sessionsDone: Int,
)

@Composable
fun FocusDefaultsSection(
    state: FocusUiState,
    onFocusMin: (Int) -> Unit,
    onBreakMin: (Int) -> Unit,
    onStartNow: () -> Unit,
) {
    LifeSectionCard(title = "🎯 Focus mode") {
        Text(text = "Focus length", fontFamily = mono, fontSize = 11.sp, color = BalochTextDim)
        Spacer(Modifier.height(4.dp))
        LifeSegmentedRow(
            options = listOf(15, 25, 45, 60),
            selected = state.focusMin,
            label = { "$it min" },
            onSelect = onFocusMin,
        )
        Text(text = "Break length", fontFamily = mono, fontSize = 11.sp, color = BalochTextDim)
        Spacer(Modifier.height(4.dp))
        LifeSegmentedRow(
            options = listOf(5, 10, 15),
            selected = state.breakMin,
            label = { "$it min" },
            onSelect = onBreakMin,
        )
        LifeStatusLine(
            label = "Sessions completed",
            value = state.sessionsDone.toString(),
        )
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onStartNow,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = BalochCyan,
                contentColor = BalochBlack,
            ),
        ) {
            Text("Start focus now", fontFamily = mono, fontWeight = FontWeight.Bold)
        }
    }
}

// ------------------------------------------------------------------
// 5. Location reminders
// ------------------------------------------------------------------

data class GeoRemindersUiState(
    val enabled: Boolean,
    val reminderCount: Int,
    /** False when ACCESS_FINE_LOCATION is not granted. */
    val locationGranted: Boolean,
)

@Composable
fun LocationRemindersSection(
    state: GeoRemindersUiState,
    onToggle: (Boolean) -> Unit,
    onGrantLocation: () -> Unit,
    onOpenAdd: () -> Unit,
) {
    LifeSectionCard(title = "📍 Location reminders") {
        LifeToggleRow(
            title = "Geofence reminders",
            subtitle = "Remind me when I reach a place (100 m radius)",
            checked = state.enabled,
            onChecked = onToggle,
        )
        Spacer(Modifier.height(4.dp))
        if (!state.locationGranted) {
            Text(
                text = "⚠ Location permission needed — tap to grant, sir.",
                fontFamily = mono, fontSize = 11.sp, color = BalochAmber,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onGrantLocation)
                    .padding(8.dp),
            )
            Spacer(Modifier.height(4.dp))
        }
        LifeStatusLine(label = "Active reminders", value = state.reminderCount.toString())
        Spacer(Modifier.height(4.dp))
        LifeNavRow(
            title = "Add reminder",
            subtitle = "\"Jab market jaun to yaad dilana: doodh lena\"",
            onClick = onOpenAdd,
        )
    }
}

// ------------------------------------------------------------------
// 6. Translator
// ------------------------------------------------------------------

data class TranslatorUiState(
    val autoDownload: Boolean,
    /** e.g. "EN↔UR ready · HI downloading…" */
    val modelStatusText: String,
    val downloading: Boolean,
)

@Composable
fun TranslatorSection(
    state: TranslatorUiState,
    onAutoDownload: (Boolean) -> Unit,
    onDownloadNow: () -> Unit,
) {
    LifeSectionCard(title = "🌐 Translator (on-device)") {
        LifeToggleRow(
            title = "Auto-download models",
            subtitle = "Fetch UR/HI models on Wi-Fi when needed",
            checked = state.autoDownload,
            onChecked = onAutoDownload,
        )
        Spacer(Modifier.height(4.dp))
        LifeStatusLine(label = "Models", value = state.modelStatusText)
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onDownloadNow,
            enabled = !state.downloading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = BalochCyan,
                contentColor = BalochBlack,
                disabledContainerColor = BalochPanelBorder,
                disabledContentColor = BalochTextDim,
            ),
        ) {
            Text(
                if (state.downloading) "Downloading…" else "Download UR + HI models",
                fontFamily = mono, fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ------------------------------------------------------------------
// 7. SOS setup
// ------------------------------------------------------------------

data class SosUiState(
    /** Current ICE number ("" when unset). */
    val iceNumber: String,
    val smsPermissionGranted: Boolean,
)

@Composable
fun SosSetupSection(
    state: SosUiState,
    onSaveIce: (String) -> Unit,
    onGrantSms: () -> Unit,
) {
    var draft by remember(state.iceNumber) { mutableStateOf(state.iceNumber) }
    var savedFlash by remember { mutableStateOf(false) }

    LifeSectionCard(title = "🚨 SOS") {
        Text(
            text = "Saying \"SOS\" sends your live location to this contact by SMS — always with your confirmation first.",
            fontFamily = mono, fontSize = 11.sp, color = BalochTextDim,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it; savedFlash = false },
                modifier = Modifier.weight(1f),
                placeholder = { Text("+92 3XX XXXXXXX", fontFamily = mono, color = BalochTextDim) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = BalochText,
                    unfocusedTextColor = BalochText,
                    cursorColor = BalochCyan,
                    focusedBorderColor = BalochCyan,
                    unfocusedBorderColor = BalochPanelBorder,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onSaveIce(draft.trim()); savedFlash = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BalochCyan, contentColor = BalochBlack,
                ),
            ) {
                Text(if (savedFlash) "Saved ✓" else "Save", fontFamily = mono)
            }
        }
        Spacer(Modifier.height(6.dp))
        if (!state.smsPermissionGranted) {
            Text(
                text = "⚠ SMS permission needed — tap to grant.",
                fontFamily = mono, fontSize = 11.sp, color = BalochAmber,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onGrantSms)
                    .padding(8.dp),
            )
        } else {
            LifeStatusLine(label = "SMS permission", value = "granted ✓")
        }
    }
}

// ------------------------------------------------------------------
// 8. Qibla compass — standalone screen the parent navigates to.
// ------------------------------------------------------------------

/**
 * Live Qibla dial. [azimuthDeg] is the device heading (0 = north),
 * [qiblaBearingDeg] the bearing to the Kaaba from the device location.
 * The needle rotates by (qibla − azimuth); when it points straight up,
 * Kamran faces the Qibla.
 */
@Composable
fun QiblaCompassScreen(
    azimuthDeg: Float?,
    qiblaBearingDeg: Double,
    onBack: () -> Unit,
) {
    val needleRotation = if (azimuthDeg != null) {
        ((qiblaBearingDeg - azimuthDeg + 360.0) % 360.0).toFloat()
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹ Back",
                fontFamily = mono, fontSize = 13.sp, color = BalochCyan,
                modifier = Modifier.clickable(onClick = onBack).padding(8.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "🧭 Qibla compass",
                fontFamily = mono, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, color = BalochText,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(56.dp))
        }
        Spacer(Modifier.height(24.dp))
        if (azimuthDeg == null) {
            Text(
                text = "Waiting for the compass sensor, sir…\nhold the phone flat and steady. 📱",
                fontFamily = mono, fontSize = 12.sp, color = BalochTextDim,
                textAlign = TextAlign.Center,
            )
        } else {
            // Needle: rotates so that "up" = Qibla direction.
            Text(
                text = "▲",
                fontFamily = mono,
                fontSize = 96.sp,
                color = BalochCyan,
                modifier = Modifier.graphicsLayer { rotationZ = needleRotation },
            )
            Spacer(Modifier.height(12.dp))
            val aligned = needleRotation < 5f || needleRotation > 355f
            Text(
                text = if (aligned) "You are facing the Qibla, sir 🕌"
                else "Turn ${"%.0f".format(if (needleRotation <= 180) needleRotation else needleRotation - 360)}°",
                fontFamily = mono, fontSize = 13.sp,
                color = if (aligned) BalochCyan else BalochTextDim,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Qibla bearing: ${"%.0f".format(qiblaBearingDeg)}°",
                fontFamily = mono, fontSize = 11.sp, color = BalochTextDim,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Tip: keep the phone away from magnets and metal cases for a steady needle.",
            fontFamily = mono, fontSize = 11.sp, color = BalochTextDim,
            textAlign = TextAlign.Center,
        )
    }
}
