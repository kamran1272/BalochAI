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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baloch.ai.engine.pro.Routine
import com.baloch.ai.ui.theme.BalochBlack
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochPanel
import com.baloch.ai.ui.theme.BalochText
import com.baloch.ai.ui.theme.BalochTextDim

/**
 * Routines / macros: built-ins (good night, meeting mode) plus Kamran's
 * custom trigger phrases. Built-ins can't be deleted; custom ones can.
 */
@Composable
fun RoutinesScreen(
    routines: List<Routine>,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(BalochBlack).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = BalochCyan, fontSize = 24.sp,
                modifier = Modifier.clickable { onBack() }.padding(8.dp))
            Spacer(Modifier.width(8.dp))
            Text("🔁 ROUTINES", color = BalochCyan, fontSize = 16.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Teach new ones by voice: \"when I say 'leaving home', turn off wifi and set brightness to 20\".",
            color = BalochTextDim, fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
        if (routines.isEmpty()) {
            Text("No routines yet, sir — teach me one by voice. 🎓", color = BalochTextDim)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(routines, key = { it.triggerPhrase }) { routine ->
                    val builtin = routine.triggerPhrase in setOf(
                        "good night", "shab bakhair", "شب بخیر", "शुभ रात्रि", "meeting mode"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BalochPanel)
                            .border(1.dp, BalochCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(routine.name, color = BalochCyan, fontSize = 14.sp)
                            Text(
                                "“${routine.triggerPhrase}” → ${routine.actions.size} steps",
                                color = BalochTextDim, fontSize = 12.sp,
                            )
                        }
                        if (builtin) {
                            Text("BUILT-IN", color = BalochTextDim, fontSize = 10.sp)
                        } else {
                            Text("✕", color = BalochCyan, fontSize = 18.sp,
                                modifier = Modifier.clickable { onDelete(routine.triggerPhrase) }.padding(8.dp))
                        }
                    }
                }
            }
        }
    }
}
