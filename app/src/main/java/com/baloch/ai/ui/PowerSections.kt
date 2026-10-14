package com.baloch.ai.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baloch.ai.power.CricketAlerts
import com.baloch.ai.power.CurrencyAlerts
import com.baloch.ai.power.DrivingMode
import com.baloch.ai.power.PetrolAlerts
import com.baloch.ai.power.PowerPrefs
import com.baloch.ai.power.RainAlerts
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochPanel
import com.baloch.ai.ui.theme.BalochPanelBorder
import com.baloch.ai.ui.theme.BalochText
import com.baloch.ai.ui.theme.BalochTextDim

// ======================================================================
// PLACEMENT NOTE (for the parent / SettingsScreen owner):
//
// SettingsScreen.kt is owned by another builder, so this file stays
// standalone. To wire the section in, add inside the settings column:
//
//   val context = LocalContext.current
//   var powerState by remember { mutableStateOf(loadPowerUiState(context)) }
//   val powerActions = remember {
//       object : PowerActions {
//           override fun onDrivingChanged(on: Boolean) {
//               DrivingMode.setOn(context, on)
//               powerState = loadPowerUiState(context)
//           }
//           ... one override per callback: each writes through the
//           feature's real store, then reloads state from PowerPrefs.
//       }
//   }
//   PowerFeaturesSection(
//       state = powerState,
//       actions = powerActions,
//       recordings = loadPowerRecordings(context),
//       playingPath = null, // parent tracks RecordingPlayer.playingFile
//       onPlayFile = { path -> RecordingPlayer.play(java.io.File(path)) },
//       onStopPlayback = { RecordingPlayer.stop() },
//       onDeleteFile = { path -> MeetingRecorder.deleteRecording(java.io.File(path)) },
//   )
//
// [loadPowerUiState] reads every toggle + tuning value from PowerPrefs
// (the single backing store), so screen and voice can never disagree.
// Every callback must end with `powerState = loadPowerUiState(context)`
// — that is what makes each toggle real instead of decorative.
// ======================================================================

/** All ten power features, every toggle and tuning, one snapshot. */
data class PowerUiState(
    val drivingOn: Boolean = false,
    val drivingAutoReply: Boolean = true,
    val drivingAutoOffMin: Int = 120,
    val ramadanOn: Boolean = false,
    val ramadanCity: String = "Lahore",
    val cricketOn: Boolean = false,
    val cricketMatchDaysOnly: Boolean = true,
    val cricketSourceOk: Boolean = true,
    val currencyOn: Boolean = false,
    val currencyThreshold: Double = 290.0,
    val currencyLastRate: Double? = null,
    val petrolOn: Boolean = false,
    val petrolPrice: Double? = null,
    val petrolDaysAgo: Long = Long.MAX_VALUE,
    val wordOn: Boolean = false,
    val recorderOn: Boolean = false,
    val recorderTranscribe: Boolean = false,
    val chargeOn: Boolean = false,
    val rainOn: Boolean = false,
    val rainLastPct: Int? = null,
    val appLockOn: Boolean = false,
)

/** One callback per control — the parent writes through the real store. */
interface PowerActions {
    fun onDrivingChanged(on: Boolean)
    fun onDrivingAutoReplyChanged(on: Boolean)
    fun onDrivingAutoOffChanged(minutes: Int)
    fun onRamadanChanged(on: Boolean)
    fun onRamadanCityChanged(city: String)
    fun onCricketChanged(on: Boolean)
    fun onCricketMatchDaysOnlyChanged(on: Boolean)
    fun onCurrencyChanged(on: Boolean)
    fun onCurrencyThresholdChanged(threshold: Double)
    fun onPetrolChanged(on: Boolean)
    fun onWordChanged(on: Boolean)
    fun onRecorderChanged(on: Boolean)
    fun onRecorderTranscribeChanged(on: Boolean)
    fun onChargeChanged(on: Boolean)
    fun onRainChanged(on: Boolean)
    fun onAppLockChanged(on: Boolean)
}

data class PowerRecording(val path: String, val name: String, val sizeKb: Long)

/** Reads the whole section state from the single PowerPrefs backing store. */
fun loadPowerUiState(context: Context): PowerUiState = PowerUiState(
    drivingOn = DrivingMode.isOn(context),
    drivingAutoReply = DrivingMode.autoReplyOn(context),
    drivingAutoOffMin = DrivingMode.autoOffMinutes(context),
    ramadanOn = PowerPrefs.isOn(context, PowerPrefs.RAMADAN),
    ramadanCity = PowerPrefs.getString(context, PowerPrefs.RAMADAN_CITY, "Lahore"),
    cricketOn = CricketAlerts.isOn(context),
    cricketMatchDaysOnly = CricketAlerts.matchDaysOnly(context),
    cricketSourceOk = PowerPrefs.getLong(context, PowerPrefs.CRICKET_LAST_ERROR, 0L) == 0L,
    currencyOn = CurrencyAlerts.isOn(context),
    currencyThreshold = CurrencyAlerts.threshold(context),
    currencyLastRate = CurrencyAlerts.lastRate(context),
    petrolOn = PetrolAlerts.isOn(context),
    petrolPrice = PetrolAlerts.lastPrice(context),
    petrolDaysAgo = PetrolAlerts.daysSinceUpdate(context),
    wordOn = PowerPrefs.isOn(context, PowerPrefs.WORD),
    recorderOn = PowerPrefs.isOn(context, PowerPrefs.RECORDER),
    recorderTranscribe = PowerPrefs.isOn(context, PowerPrefs.RECORDER_TRANSCRIBE),
    chargeOn = PowerPrefs.isOn(context, PowerPrefs.CHARGE),
    rainOn = RainAlerts.isOn(context),
    rainLastPct = RainAlerts.lastPct(context),
    appLockOn = PowerPrefs.isOn(context, PowerPrefs.APPLOCK),
)

/** Builds the recordings list for [RecorderSection]. */
fun loadPowerRecordings(context: Context): List<PowerRecording> =
    com.baloch.ai.power.MeetingRecorder.listRecordings(context).map { f ->
        PowerRecording(
            path = f.absolutePath,
            name = com.baloch.ai.power.MeetingRecorder.displayName(f),
            sizeKb = f.length() / 1024,
        )
    }

// ----------------------------------------------------------------------
// Shared building blocks
// ----------------------------------------------------------------------

@Composable
private fun PowerCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BalochPanel),
        border = BorderStroke(1.dp, BalochPanelBorder),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, color = BalochCyan, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = BalochTextDim, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun PowerToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    desc: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = BalochText, fontSize = 13.sp)
            if (desc != null) {
                Text(text = desc, color = BalochTextDim, fontSize = 11.sp)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BalochCyan,
                checkedTrackColor = BalochCyan.copy(alpha = 0.35f),
                uncheckedThumbColor = BalochTextDim,
                uncheckedTrackColor = BalochPanelBorder,
            ),
        )
    }
}

@Composable
private fun HonestNote(text: String) {
    Spacer(modifier = Modifier.height(6.dp))
    Text(text = "Note: $text", color = BalochTextDim, fontSize = 11.sp)
}

@Composable
private fun PowerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholder, color = BalochTextDim, fontSize = 13.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = BalochText,
            unfocusedTextColor = BalochText,
            cursorColor = BalochCyan,
            focusedBorderColor = BalochCyan,
            unfocusedBorderColor = BalochPanelBorder,
        ),
    )
}

@Composable
private fun PowerSaveButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = BalochCyan),
    ) {
        Text(text, color = BalochPanel, fontSize = 13.sp)
    }
}

// ----------------------------------------------------------------------
// The ten sections — each standalone: explicit state + callbacks only.
// ----------------------------------------------------------------------

@Composable
fun DrivingSection(state: PowerUiState, actions: PowerActions) {
    val chips = listOf(0 to "Never", 30 to "30m", 60 to "1h", 120 to "2h", 240 to "4h")
    PowerCard(
        title = "Driving mode",
        subtitle = "Auto-replies to SMS and reads messages aloud while you drive.",
    ) {
        PowerToggleRow("Driving mode", state.drivingOn, actions::onDrivingChanged)
        if (state.drivingOn) {
            Spacer(modifier = Modifier.height(6.dp))
            PowerToggleRow(
                "Auto-reply to SMS",
                state.drivingAutoReply,
                actions::onDrivingAutoReplyChanged,
                desc = "\"I'm driving, will reply later, sir\"",
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Auto-off timer", color = BalochText, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { (min, label) ->
                    val selected = state.drivingAutoOffMin == min
                    OutlinedButton(
                        onClick = { actions.onDrivingAutoOffChanged(min) },
                        border = BorderStroke(1.dp, if (selected) BalochCyan else BalochPanelBorder),
                    ) {
                        Text(
                            label,
                            color = if (selected) BalochCyan else BalochTextDim,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        HonestNote("Needs SMS permission for the auto-reply. Reading other apps' notifications needs \"Notification access\" in Android Settings.")
    }
}

@Composable
fun RamadanSection(state: PowerUiState, actions: PowerActions) {
    var cityDraft by remember(state.ramadanCity) { mutableStateOf(state.ramadanCity) }
    PowerCard(
        title = "Ramadan mode",
        subtitle = "Sehri/iftari alerts with a daily countdown.",
    ) {
        PowerToggleRow("Ramadan mode", state.ramadanOn, actions::onRamadanChanged)
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PowerTextField(
                value = cityDraft,
                onValueChange = { cityDraft = it },
                placeholder = "City (e.g. Lahore)",
                keyboardType = KeyboardType.Text,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            PowerSaveButton("Save") { actions.onRamadanCityChanged(cityDraft) }
        }
        HonestNote("Timings: Aladhan API, Karachi method. Off-season shows the estimated next Ramadan — moon sighting may shift it a day or two.")
    }
}

@Composable
fun CricketSection(state: PowerUiState, actions: PowerActions) {
    PowerCard(
        title = "Pakistan cricket alerts",
        subtitle = "Match news for the green shirts, every 4 hours.",
    ) {
        PowerToggleRow("Cricket alerts", state.cricketOn, actions::onCricketChanged)
        Spacer(modifier = Modifier.height(6.dp))
        PowerToggleRow(
            "Match days only",
            state.cricketMatchDaysOnly,
            actions::onCricketMatchDaysOnlyChanged,
            desc = "Skip gossip — only match news buzzes you",
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            if (state.cricketSourceOk) "Source: ESPNcricinfo RSS"
            else "Source unavailable — retrying automatically",
            color = if (state.cricketSourceOk) BalochTextDim else BalochCyan,
            fontSize = 11.sp,
        )
        HonestNote("No free official live-score API exists — this reads the ESPNcricinfo news feed for Pakistan stories.")
    }
}

@Composable
fun CurrencySection(state: PowerUiState, actions: PowerActions) {
    var draft by remember(state.currencyThreshold) {
        mutableStateOf(
            if (state.currencyThreshold == state.currencyThreshold.toLong().toDouble())
                state.currencyThreshold.toLong().toString()
            else state.currencyThreshold.toString()
        )
    }
    PowerCard(
        title = "Dollar alert",
        subtitle = "One buzz when USD/PKR crosses your line.",
    ) {
        PowerToggleRow("Dollar alert", state.currencyOn, actions::onCurrencyChanged)
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PowerTextField(
                value = draft,
                onValueChange = { draft = it.filter { c -> c.isDigit() || c == '.' } },
                placeholder = "Alert at Rs... (e.g. 290)",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            PowerSaveButton("Save") {
                draft.toDoubleOrNull()?.let(actions::onCurrencyThresholdChanged)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = state.currencyLastRate?.let { "Last check: Rs ${"%.2f".format(it)}" }
                ?: "No rate fetched yet",
            color = BalochTextDim,
            fontSize = 11.sp,
        )
        HonestNote("Free rates API, checked every 6 hours. One alert per crossing — it re-arms when the rate drops back under.")
    }
}

@Composable
fun PetrolSection(state: PowerUiState, actions: PowerActions) {
    PowerCard(
        title = "Petrol price alert",
        subtitle = "You teach the price — Baloch announces big changes.",
    ) {
        PowerToggleRow("Petrol alerts", state.petrolOn, actions::onPetrolChanged)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = state.petrolPrice?.let {
                val ago = if (state.petrolDaysAgo == Long.MAX_VALUE) "never updated"
                else "${state.petrolDaysAgo} days ago"
                "Last taught: Rs $it / litre ($ago)"
            } ?: "No price taught yet — say \"petrol price 278\"",
            color = BalochTextDim,
            fontSize = 12.sp,
        )
        HonestNote("No official free API exists for Pakistan's fortnightly OGRA price — so nothing is fetched or guessed. Baloch alerts when a newly taught price differs by Rs 5+.")
    }
}

@Composable
fun WordSection(state: PowerUiState, actions: PowerActions) {
    PowerCard(
        title = "Daily English word",
        subtitle = "\"Aaj ka lafz\" — with Urdu meaning, every morning.",
    ) {
        PowerToggleRow(
            "Daily word",
            state.wordOn,
            actions::onWordChanged,
            desc = "Notification at 7:30 AM, fully offline",
        )
    }
}

@Composable
fun RecorderSection(
    state: PowerUiState,
    actions: PowerActions,
    recordings: List<PowerRecording>,
    playingPath: String?,
    onPlayFile: (String) -> Unit,
    onStopPlayback: () -> Unit,
    onDeleteFile: (String) -> Unit,
) {
    PowerCard(
        title = "Meeting recorder",
        subtitle = "\"Meeting record karo\" then \"recording band karo\".",
    ) {
        PowerToggleRow("Meeting recorder", state.recorderOn, actions::onRecorderChanged)
        Spacer(modifier = Modifier.height(6.dp))
        PowerToggleRow(
            "Auto-transcribe on device",
            state.recorderTranscribe,
            actions::onRecorderTranscribeChanged,
            desc = "Vosk, offline — best effort",
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (recordings.isEmpty()) {
            Text("No recordings yet, sir.", color = BalochTextDim, fontSize = 12.sp)
        } else {
            recordings.forEach { rec ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rec.name, color = BalochText, fontSize = 13.sp)
                        Text("${rec.sizeKb} KB", color = BalochTextDim, fontSize = 11.sp)
                    }
                    val playing = playingPath == rec.path
                    OutlinedButton(
                        onClick = { if (playing) onStopPlayback() else onPlayFile(rec.path) },
                        border = BorderStroke(1.dp, BalochCyan),
                    ) {
                        Text(if (playing) "Stop" else "Play", color = BalochCyan, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedButton(
                        onClick = { onDeleteFile(rec.path) },
                        border = BorderStroke(1.dp, BalochPanelBorder),
                    ) {
                        Text("Delete", color = BalochTextDim, fontSize = 12.sp)
                    }
                }
            }
        }
        HonestNote("Files stay in app-private storage — never uploaded. Transcription needs the offline speech model; noisy audio degrades it.")
    }
}

@Composable
fun ChargeSection(state: PowerUiState, actions: PowerActions) {
    PowerCard(
        title = "Charge-complete alert",
        subtitle = "Announces plug-in and 100%.",
    ) {
        PowerToggleRow(
            "Charge alerts",
            state.chargeOn,
            actions::onChargeChanged,
            desc = "\"Sir, charging complete — 100%\"",
        )
        HonestNote("Complements the battery guardian (that one watches low battery; this one watches the charge cycle).")
    }
}

@Composable
fun RainSection(state: PowerUiState, actions: PowerActions) {
    PowerCard(
        title = "Rain alert — Lahore",
        subtitle = "Morning check: umbrella or sunshine?",
    ) {
        PowerToggleRow("Rain alerts", state.rainOn, actions::onRainChanged)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = state.rainLastPct?.let { "Last check: $it% chance of rain" }
                ?: "No check yet — alerts run at 7:00 AM",
            color = BalochTextDim,
            fontSize = 11.sp,
        )
        HonestNote("Fixed to Lahore (no location permission needed). Open-Meteo, free and keyless — a probability is not a promise.")
    }
}

@Composable
fun AppLockSection(state: PowerUiState, actions: PowerActions) {
    PowerCard(
        title = "App lock",
        subtitle = "Fingerprint or PIN before Baloch opens.",
    ) {
        PowerToggleRow(
            "App lock",
            state.appLockOn,
            actions::onAppLockChanged,
            desc = "Uses your device biometrics — or your Baloch PIN as fallback",
        )
        HonestNote("Reuses the existing Voice Lock PIN/biometrics — nothing new to set up. The parent checks isAppLockOn() in MainActivity.")
    }
}

// ----------------------------------------------------------------------
// Master section — the parent drops this one call into SettingsScreen.
// ----------------------------------------------------------------------

@Composable
fun PowerFeaturesSection(
    state: PowerUiState,
    actions: PowerActions,
    recordings: List<PowerRecording> = emptyList(),
    playingPath: String? = null,
    onPlayFile: (String) -> Unit = {},
    onStopPlayback: () -> Unit = {},
    onDeleteFile: (String) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Power Features",
            color = BalochCyan,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            text = "Dormant superpowers, sir — each off until you wake it. Every toggle below is live.",
            color = BalochTextDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        DrivingSection(state, actions)
        RamadanSection(state, actions)
        CricketSection(state, actions)
        CurrencySection(state, actions)
        PetrolSection(state, actions)
        WordSection(state, actions)
        RecorderSection(state, actions, recordings, playingPath, onPlayFile, onStopPlayback, onDeleteFile)
        ChargeSection(state, actions)
        RainSection(state, actions)
        AppLockSection(state, actions)
    }
}
