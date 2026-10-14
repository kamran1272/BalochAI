package com.baloch.ai.ui

// Placed by parent into Settings — standalone so MainScreen stays untouched.

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baloch.ai.ui.components.BackLabel
import com.baloch.ai.ui.theme.BalochAmber
import com.baloch.ai.ui.theme.BalochBlack
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochPanel
import com.baloch.ai.ui.theme.BalochPanelBorder
import com.baloch.ai.ui.theme.BalochText
import com.baloch.ai.ui.theme.BalochTextDim

/**
 * Voice Identity Lock settings — the ONLY place Baloch's voice can change.
 *
 * Kamran's order: Baloch always speaks in ONE locked male voice per
 * language, never auto-changes. This screen shows the current locked voice
 * (name + language), lets him pick a different voice from the engine's
 * voices for the active locale (male voices first), preview it, and set the
 * voice volume (LOUD default = 100%).
 *
 * The parent wires this into the Settings flow and supplies the state;
 * tapping a row calls [onPickVoice] with the voice id, which persists the
 * new lock via VoiceIdentityManager.setVoiceId(). The preview button speaks
 * the parent's sample text ("I am Baloch, sir." in the active language).
 */
data class VoiceOption(val id: String, val displayName: String, val selected: Boolean)

data class VoiceSettingsState(
    val languageName: String,
    val currentVoiceName: String,
    val voices: List<VoiceOption>,
    val volumePct: Int,
    val voiceChangedNote: Boolean,
)

@Composable
fun VoiceSettingsScreen(
    state: VoiceSettingsState,
    onPickVoice: (String) -> Unit,
    onPreview: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val mono = FontFamily.Monospace

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BalochBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // ── Header ──────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BackLabel(onBack = onBack)
            Text(
                text = "◈ BALOCH VOICE",
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = BalochCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Locked voice identity — one male voice per language, never auto-changes.",
            fontFamily = mono,
            fontSize = 12.sp,
            color = BalochTextDim,
        )
        Spacer(Modifier.height(16.dp))

        // ── Current voice card ──────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BalochPanel),
            border = BorderStroke(1.dp, BalochPanelBorder),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "CURRENT VOICE",
                    fontFamily = mono,
                    fontSize = 11.sp,
                    color = BalochTextDim,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.currentVoiceName.ifBlank { "— not set yet —" },
                    fontFamily = mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = BalochText,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Language: ${state.languageName}",
                    fontFamily = mono,
                    fontSize = 13.sp,
                    color = BalochTextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onPreview,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BalochCyan,
                        contentColor = BalochBlack,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "▶ PREVIEW — \"I am Baloch, sir.\"",
                        fontFamily = mono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ── One-time re-match note ──────────────────────────────
        if (state.voiceChangedNote) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BalochPanel),
                border = BorderStroke(1.dp, BalochAmber),
            ) {
                Text(
                    text = "⚠ Baloch's voice was re-matched, sir — the locked voice " +
                        "disappeared from the TTS engine, so the closest male voice " +
                        "was chosen. Pick a new one below if you prefer.",
                    fontFamily = mono,
                    fontSize = 12.sp,
                    color = BalochAmber,
                    modifier = Modifier.padding(14.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Voice picker ────────────────────────────────────────
        Text(
            text = "CHOOSE VOICE (${state.languageName})",
            fontFamily = mono,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = BalochCyan,
        )
        Spacer(Modifier.height(8.dp))
        if (state.voices.isEmpty()) {
            Text(
                text = "No voices found for this language on this device.",
                fontFamily = mono,
                fontSize = 13.sp,
                color = BalochTextDim,
            )
        } else {
            state.voices.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickVoice(option.id) }
                        .padding(vertical = 6.dp),
                ) {
                    RadioButton(
                        selected = option.selected,
                        onClick = { onPickVoice(option.id) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = BalochCyan,
                            unselectedColor = BalochTextDim,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = option.displayName,
                        fontFamily = mono,
                        fontSize = 13.sp,
                        color = if (option.selected) BalochText else BalochTextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (option.selected) {
                        Text(
                            text = "● LOCKED",
                            fontFamily = mono,
                            fontSize = 10.sp,
                            color = BalochCyan,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        // ── Volume ──────────────────────────────────────────────
        Text(
            text = "VOICE VOLUME",
            fontFamily = mono,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = BalochCyan,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Loud by default — Baloch is always heard.",
                fontFamily = mono,
                fontSize = 12.sp,
                color = BalochTextDim,
            )
            Text(
                text = "${state.volumePct}%",
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = BalochCyan,
            )
        }
        Slider(
            value = state.volumePct.toFloat(),
            onValueChange = { onVolumeChange(it.toInt()) },
            valueRange = 0f..100f,
            steps = 99,
            colors = SliderDefaults.colors(
                thumbColor = BalochCyan,
                activeTrackColor = BalochCyan,
                inactiveTrackColor = BalochPanelBorder,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Default 100% (FULL). Applied to the media stream before every utterance.",
            fontFamily = mono,
            fontSize = 11.sp,
            color = BalochTextDim,
        )
        Spacer(Modifier.height(24.dp))

        // ── Lock note ───────────────────────────────────────────
        Text(
            text = "🔒 Only Kamran can change this voice — Baloch never picks a new " +
                "voice on its own. If the locked voice vanishes from the device, " +
                "the closest male voice is re-matched and he is told once.",
            fontFamily = mono,
            fontSize = 11.sp,
            color = BalochTextDim,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
