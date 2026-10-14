package com.baloch.ai.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baloch.ai.gesture.GestureKind
import com.baloch.ai.gesture.SensorGestureKind
import com.baloch.ai.ui.theme.BalochBlack
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochPanel
import com.baloch.ai.ui.theme.BalochText
import com.baloch.ai.ui.theme.BalochTextDim

/**
 * Gesture remap UI: every gesture → the command text it fires.
 * Kamran types a new command per gesture; it routes through the
 * Orchestrator like any typed command (voice-lock still applies).
 */
@Composable
fun GestureRemapScreen(
    cameraMap: Map<GestureKind, String>,
    sensorMap: Map<SensorGestureKind, String>,
    onCameraRemap: (GestureKind, String) -> Unit,
    onSensorRemap: (SensorGestureKind, String) -> Unit,
    onBack: () -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    Column(
        modifier = Modifier.fillMaxSize().background(BalochBlack).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = BalochCyan, fontSize = 24.sp,
                modifier = Modifier.clickable { onBack() }.padding(8.dp))
            Spacer(Modifier.width(8.dp))
            Text("👆 GESTURE MAP", color = BalochCyan, fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Camera", "Sensor").forEachIndexed { i, name ->
                Text(
                    text = name,
                    color = if (tab == i) BalochBlack else BalochCyan,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (tab == i) BalochCyan else BalochPanel)
                        .clickable { tab = i }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (tab == 0) {
                cameraMap.forEach { (kind, command) ->
                    RemapRow(
                        title = kind.name.replace("_", " ").lowercase()
                            .replaceFirstChar { it.uppercase() },
                        command = command,
                        onSave = { onCameraRemap(kind, it) },
                    )
                }
                Text(
                    "Each command runs through the Orchestrator — voice lock and your rules still apply.",
                    color = BalochTextDim, fontSize = 11.sp,
                )
            } else {
                sensorMap.forEach { (kind, command) ->
                    RemapRow(
                        title = kind.name.lowercase().replaceFirstChar { it.uppercase() },
                        command = command,
                        onSave = { onSensorRemap(kind, it) },
                    )
                }
                Text(
                    "Sensor gestures sip battery; they work in the dark, even with the screen off.",
                    color = BalochTextDim, fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun RemapRow(title: String, command: String, onSave: (String) -> Unit) {
    var draft by remember(command) { mutableStateOf(command) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BalochPanel)
            .border(1.dp, BalochCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(title, color = BalochCyan, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = BalochText,
                    unfocusedTextColor = BalochText,
                    cursorColor = BalochCyan,
                    focusedContainerColor = BalochBlack,
                    unfocusedContainerColor = BalochBlack,
                    focusedIndicatorColor = BalochCyan,
                    unfocusedIndicatorColor = BalochTextDim,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Save",
                color = BalochBlack,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(BalochCyan)
                    .clickable { if (draft.isNotBlank()) onSave(draft.trim()) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}
