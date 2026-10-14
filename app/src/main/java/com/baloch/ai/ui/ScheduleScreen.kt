package com.baloch.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baloch.ai.schedule.Recurrence
import com.baloch.ai.schedule.ScheduledTask
import com.baloch.ai.ui.components.BackChevron
import com.baloch.ai.ui.theme.BalochAmber
import com.baloch.ai.ui.theme.BalochBlack
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochCyanBright
import com.baloch.ai.ui.theme.BalochPanel
import com.baloch.ai.ui.theme.BalochPanelBorder
import com.baloch.ai.ui.theme.BalochRed
import com.baloch.ai.ui.theme.BalochText
import com.baloch.ai.ui.theme.BalochTextDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Standalone scheduled-tasks screen — holographic cyan on deep black.
 * Lists every [ScheduledTask] with its next fire time, command, recurrence
 * badge, enable switch and delete affordance.
 */
@Composable
fun ScheduleScreen(
    tasks: List<ScheduledTask>,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BalochBlack)
            .padding(16.dp)
    ) {
        // Header — 48dp RTL-mirrored back chevron (see components/NavChrome).
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackChevron(onBack = onBack)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "◈ SCHEDULED TASKS",
                color = BalochCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(16.dp))

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No scheduled tasks, sir.",
                    color = BalochTextDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        onToggle = { onToggle(task.id, it) },
                        onDelete = { onDelete(task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: ScheduledTask,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val whenText = remember(task.triggerAtMillis) {
        SimpleDateFormat("EEE, d MMM · h:mm a", Locale.UK)
            .format(Date(task.triggerAtMillis))
    }
    val badgeColor = when (task.recurrence) {
        Recurrence.ONCE -> BalochAmber
        Recurrence.DAILY -> BalochCyan
        Recurrence.WEEKLY -> BalochCyanBright
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BalochPanel)
            .border(1.dp, BalochPanelBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = whenText,
                color = BalochText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // Recurrence badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, badgeColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = task.recurrence.name,
                    color = badgeColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }

        if (task.recurrence == Recurrence.WEEKLY && task.daysOfWeek.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = task.daysOfWeek.sorted().joinToString(" · ") { shortDay(it) },
                color = BalochTextDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = task.commandText,
            color = BalochTextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (task.enabled) "ENABLED" else "PAUSED",
                color = if (task.enabled) BalochCyan else BalochTextDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = task.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BalochBlack,
                    checkedTrackColor = BalochCyan,
                    checkedBorderColor = BalochCyan,
                    uncheckedThumbColor = BalochTextDim,
                    uncheckedTrackColor = BalochPanelBorder,
                    uncheckedBorderColor = BalochPanelBorder,
                )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "✕",
                color = BalochRed,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

private fun shortDay(iso: Int): String = when (iso) {
    1 -> "Mon"; 2 -> "Tue"; 3 -> "Wed"; 4 -> "Thu"
    5 -> "Fri"; 6 -> "Sat"; 7 -> "Sun"; else -> "?"
}
