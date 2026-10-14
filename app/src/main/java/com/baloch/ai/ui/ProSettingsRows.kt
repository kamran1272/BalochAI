package com.baloch.ai.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochPanel
import com.baloch.ai.ui.theme.BalochPanelBorder
import com.baloch.ai.ui.theme.BalochText
import com.baloch.ai.ui.theme.BalochTextDim

// ======================================================================
// PLACEMENT NOTE (for the parent / MainScreen owner):
//
// MainScreen.kt is owned by another builder, so this file stays
// standalone. To wire it in, add the following inside the Settings
// section of MainScreen.kt (below the language/mode rows):
//
//   val context = LocalContext.current
//   var ice by remember { mutableStateOf(EmergencyStore.get(context) ?: "") }
//   IceContactRow(
//       current = ice,
//       onSave = { number ->
//           EmergencyStore.set(context, number)
//           ice = number
//       },
//       modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), // standalone only
//   )
//
// The default [modifier] already carries that standalone padding; pass
// `Modifier` (bare) when embedding inside an already-padded section card
// (as SettingsScreen's EMERGENCY section does) to avoid double insets.
//
// Imports needed there:
//   import androidx.compose.runtime.getValue / setValue / mutableStateOf / remember
//   import androidx.compose.ui.platform.LocalContext
//   import com.baloch.ai.engine.pro.EmergencyStore
//   import com.baloch.ai.ui.IceContactRow
// ======================================================================

/**
 * Emergency (ICE) contact row — holographic cyan styling to match the
 * Baloch theme. [current] is the saved number ("" when unset);
 * [onSave] persists it (the parent should call EmergencyStore.set).
 */
@Composable
fun IceContactRow(
    current: String,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
) {
    var draft by remember(current) { mutableStateOf(current) }
    var savedFlash by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BalochPanel),
        border = BorderStroke(1.dp, BalochPanelBorder),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "🚨 Emergency contact",
                color = BalochCyan,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Baloch calls this number when you say \"emergency\" — always with your confirmation first.",
                color = BalochTextDim,
                fontSize = 12.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it; savedFlash = false },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("+92 3XX XXXXXXX", color = BalochTextDim) },
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
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(draft.trim())
                        savedFlash = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BalochCyan,
                        contentColor = BalochPanel,
                    ),
                ) {
                    Text(if (savedFlash) "Saved ✓" else "Save")
                }
            }
        }
    }
}
