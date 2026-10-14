package com.baloch.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baloch.ai.ui.components.BackChevron
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
 * Voice Lock setup — standalone screen in the holographic-cyan identity.
 *
 * FLOW:
 *  1. Three sample slots: Kamran taps Record on each and says the fixed
 *     phrase ("Hey Baloch, it is me"). Each slot shows progress while
 *     recording and ✓ when its voice vector is stored.
 *  2. PIN setup: 4-digit PIN + confirmation (the hard guarantee).
 *  3. Voice Lock toggle: arms the lock (only enabled after enrollment).
 *  4. Biometric app-lock toggle: optional device-level gate on app open.
 *
 * The parent wires everything: it owns a [VoiceLockSetupState] (e.g. in a
 * ViewModel) and passes [VoiceLockSetupActions] that call VoiceLockManager,
 * PinManager and BiometricLock. This screen is pure UI — no managers here.
 */
data class VoiceLockSetupState(
    /** 0..3 — how many enrollment samples are stored. */
    val enrolledSamples: Int = 0,
    /** True while a sample is being recorded (2.5 s). */
    val isRecording: Boolean = false,
    /** True after all 3 samples enrolled and the voiceprint is stored. */
    val isEnrolled: Boolean = false,
    /** The lock's on/off switch. */
    val isEnabled: Boolean = false,
    /** True once a PIN hash exists in DataStore. */
    val hasPin: Boolean = false,
    /** Draft PIN being typed (never persisted until Save). */
    val pinFirst: String = "",
    /** PIN confirmation draft. */
    val pinConfirm: String = "",
    /** False on devices without biometric hardware/enrollment. */
    val biometricAvailable: Boolean = false,
    /** Optional app-open gate. Off by default. */
    val biometricLockOn: Boolean = false,
    /** One-shot status/error line shown under the header. */
    val message: String? = null,
)

data class VoiceLockSetupActions(
    // TODO(parent): every one of these defaults is a no-op stub — the parent
    // MUST wire real implementations (VoiceLockManager / PinManager /
    // BiometricLock). With the defaults, taps do nothing: a graceful
    // no-op for previews, a dead button in production if left unwired.
    /** Record the next pending enrollment sample. */
    val onRecordSample: () -> Unit = {},
    /** Arm/disarm the lock (disabling needs voice-or-PIN — parent enforces). */
    val onToggleEnabled: (Boolean) -> Unit = {},
    val onPinFirstChange: (String) -> Unit = {},
    val onPinConfirmChange: (String) -> Unit = {},
    /** Validate + store the PIN (must be 4 digits, both fields equal). */
    val onSavePin: () -> Unit = {},
    /** Toggle the optional biometric app lock. */
    val onToggleBiometric: (Boolean) -> Unit = {},
    val onDismissMessage: () -> Unit = {},
)

@Composable
fun VoiceLockSetupScreen(
    state: VoiceLockSetupState,
    actions: VoiceLockSetupActions,
    modifier: Modifier = Modifier,
    /**
     * Optional back navigation — same contract as MemoryScreen's onBack:
     * null means no back chrome is shown (never a dead button).
     */
    onBack: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BalochBlack)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // ---- Header ----
        if (onBack != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackChevron(onBack = onBack)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "VOICE LOCK",
                    color = BalochCyan,
                    fontSize = 22.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                text = "VOICE LOCK",
                color = BalochCyan,
                fontSize = 22.sp,
            )
        }
        Text(
            text = "Baloch obeys only your voice. Enroll 3 samples of the phrase below, then set your PIN.",
            color = BalochTextDim,
            fontSize = 13.sp,
        )
        Text(
            text = "\"Hey Baloch, it is me\"",
            color = BalochCyanBright,
            fontSize = 16.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(BalochPanel)
                .border(1.dp, BalochPanelBorder, RoundedCornerShape(8.dp))
                .padding(12.dp)
                .fillMaxWidth(),
        )

        state.message?.let { msg ->
            Text(
                text = msg,
                color = BalochAmber,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(BalochPanel)
                    .border(1.dp, BalochAmber, RoundedCornerShape(8.dp))
                    .padding(10.dp)
                    .fillMaxWidth(),
            )
        }

        // ---- 3 sample slots ----
        Text(text = "ENROLLMENT SAMPLES (${state.enrolledSamples}/3)", color = BalochTextDim, fontSize = 12.sp)
        repeat(3) { index ->
            val done = index < state.enrolledSamples
            val isNext = index == state.enrolledSamples && !state.isEnrolled
            SampleSlotRow(
                slotNumber = index + 1,
                done = done,
                recording = isNext && state.isRecording,
                recordable = isNext && !state.isRecording,
                onRecord = actions.onRecordSample,
            )
        }
        if (state.isRecording) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = BalochCyan,
                trackColor = BalochPanelBorder,
            )
            Text(text = "Listening… say the phrase now.", color = BalochCyan, fontSize = 12.sp)
        }
        if (state.isEnrolled) {
            Text(text = "✓ Voiceprint stored on this device only.", color = BalochCyan, fontSize = 13.sp)
        }

        // ---- PIN setup ----
        Text(text = "FALLBACK PIN", color = BalochTextDim, fontSize = 12.sp)
        if (state.hasPin) {
            Text(text = "✓ PIN is set. It is stored as a hash, never as digits.", color = BalochCyan, fontSize = 13.sp)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PinField(
                label = "4-digit PIN",
                value = state.pinFirst,
                onChange = actions.onPinFirstChange,
                modifier = Modifier.weight(1f),
            )
            PinField(
                label = "Confirm PIN",
                value = state.pinConfirm,
                onChange = actions.onPinConfirmChange,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedButton(
            onClick = actions.onSavePin,
            enabled = state.pinFirst.length == 4 && state.pinConfirm.length == 4,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BalochCyan),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.hasPin) "UPDATE PIN" else "SAVE PIN")
        }

        // ---- Toggles ----
        ToggleRow(
            title = "Voice Lock",
            subtitle = if (state.isEnabled)
                "ON — strangers are refused, nothing executes without your voice or PIN."
            else
                "OFF — Baloch obeys any voice.",
            checked = state.isEnabled,
            enabled = state.isEnrolled,
            onChecked = actions.onToggleEnabled,
        )
        if (!state.isEnrolled) {
            Text(
                text = "Finish enrollment to arm the lock.",
                color = BalochTextDim,
                fontSize = 12.sp,
            )
        }
        ToggleRow(
            title = "Biometric app lock",
            subtitle = if (state.biometricAvailable)
                "Fingerprint / face (or device PIN) required to open Baloch."
            else
                "Not available on this device.",
            checked = state.biometricLockOn,
            enabled = state.biometricAvailable,
            onChecked = actions.onToggleBiometric,
        )

        // ---- Honest limits footnote (Kamran's order: say it straight) ----
        Text(
            text = "HONEST LIMITS — Voice Lock reliably rejects casual strangers, but it is " +
                "not spoof-proof against recordings of your voice. Your PIN is the hard " +
                "guarantee: voice fails twice or the mic is unavailable → Baloch asks " +
                "for the PIN, and never executes without voice-or-PIN. Disabling the " +
                "lock also needs your voice or your PIN.",
            color = BalochTextDim,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(BalochPanel)
                .border(1.dp, BalochPanelBorder, RoundedCornerShape(8.dp))
                .padding(12.dp)
                .fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SampleSlotRow(
    slotNumber: Int,
    done: Boolean,
    recording: Boolean,
    recordable: Boolean,
    onRecord: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BalochPanel)
            .border(
                1.dp,
                when {
                    done -> BalochCyan
                    recording -> BalochAmber
                    else -> BalochPanelBorder
                },
                RoundedCornerShape(10.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when {
                done -> "✓"
                recording -> "●"
                else -> "○"
            },
            color = when {
                done -> BalochCyan
                recording -> BalochAmber
                else -> BalochTextDim
            },
            fontSize = 18.sp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Sample $slotNumber", color = BalochText, fontSize = 14.sp)
            Text(
                text = when {
                    done -> "Captured"
                    recording -> "Recording…"
                    else -> "Waiting"
                },
                color = BalochTextDim,
                fontSize = 12.sp,
            )
        }
        if (recordable) {
            Button(
                onClick = onRecord,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BalochCyanDim,
                    contentColor = BalochCyanBright,
                ),
            ) {
                Text("RECORD")
            }
        }
    }
}

@Composable
private fun PinField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label, color = BalochTextDim, fontSize = 12.sp) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedTextColor = BalochText,
            unfocusedTextColor = BalochText,
            focusedContainerColor = BalochPanel,
            unfocusedContainerColor = BalochPanel,
            focusedIndicatorColor = BalochCyan,
            unfocusedIndicatorColor = BalochPanelBorder,
            cursorColor = BalochCyan,
        ),
        modifier = modifier,
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BalochPanel)
            .border(1.dp, BalochPanelBorder, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = BalochText, fontSize = 14.sp)
            Text(text = subtitle, color = BalochTextDim, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
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
}
