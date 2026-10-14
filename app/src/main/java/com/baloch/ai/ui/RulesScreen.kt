package com.baloch.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baloch.ai.rules.Constraint
import com.baloch.ai.rules.GuidanceRule
import com.baloch.ai.ui.components.BackChevron
import com.baloch.ai.ui.theme.BalochAmber
import com.baloch.ai.ui.theme.BalochBlack
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochCyanBright
import com.baloch.ai.ui.theme.BalochCyanDim
import com.baloch.ai.ui.theme.BalochPanel
import com.baloch.ai.ui.theme.BalochPanelBorder
import com.baloch.ai.ui.theme.BalochRed
import com.baloch.ai.ui.theme.BalochText
import com.baloch.ai.ui.theme.BalochTextDim

/** Snapshot of rules for the MY RULES screen (sorted oldest → newest). */
data class RulesScreenState(val rules: List<GuidanceRule>)

/**
 * Standalone MY RULES screen — holographic cyan on deep black.
 *
 * Kamran's words, not code locks: every row shows his cleaned-up teaching
 * plus scope/constraint chips and a DEFAULT badge for the two safety
 * starters. The ✕ deletes; deleting a default asks once for confirmation
 * ("This is a safety default, sir — delete anyway?") and then genuinely
 * relaxes that scope — nothing locks him out.
 *
 * Wiring (parent): pass `RulesScreenState(store.all())`; `onDelete` should
 * call `store.delete(id)` and refresh state; `onBack` pops navigation.
 */
@Composable
fun RulesScreen(
    state: RulesScreenState,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit,
) {
    var pendingDefaultDelete by remember { mutableStateOf<GuidanceRule?>(null) }

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
                text = "◈ MY RULES",
                color = BalochCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Your words, sir — teach me in chat and they land here.",
            color = BalochTextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(16.dp))

        if (state.rules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No rules yet, sir — teach me like:\n'call karne se pehle hamesha poochna'.",
                    color = BalochTextDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.rules, key = { it.id }) { rule ->
                    RuleRow(
                        rule = rule,
                        onDeleteClick = {
                            if (rule.isDefault) pendingDefaultDelete = rule
                            else onDelete(rule.id)
                        },
                    )
                }
            }
        }
    }

    // Confirm dialog for deleting a safety default — one ask, then it goes.
    pendingDefaultDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { pendingDefaultDelete = null },
            containerColor = BalochPanel,
            title = {
                Text(
                    text = "Delete safety default?",
                    color = BalochCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            },
            text = {
                Text(
                    text = "This is a safety default, sir — delete anyway?\n\n“${rule.text}”",
                    color = BalochText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(rule.id)
                    pendingDefaultDelete = null
                }) {
                    Text(
                        text = "DELETE",
                        color = BalochRed,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDefaultDelete = null }) {
                    Text(
                        text = "KEEP",
                        color = BalochCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleRow(
    rule: GuidanceRule,
    onDeleteClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BalochPanel)
            .border(1.dp, BalochPanelBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = rule.text,
                color = BalochText,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier.weight(1f)
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
                    .clickable(onClick = onDeleteClick)
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }

        Spacer(Modifier.height(8.dp))
        // FlowRow: chips wrap on 320dp screens / 1.3x font scale instead of
        // clipping ("REQUIRE CONFIRM" + scope + DEFAULT overflowed a Row).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip(rule.scope.name, BalochCyanDim)
            Chip(
                text = rule.constraint.name.replace('_', ' '),
                color = constraintColor(rule.constraint),
            )
            if (rule.isDefault) Chip("DEFAULT", BalochAmber)
        }

        if (rule.constraint == Constraint.QUIET_HOURS) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Quiet ${rule.quietStartHour}:00 → ${rule.quietEndHour}:00 PKT " +
                    "(proactive outputs only)",
                color = BalochTextDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}

private fun constraintColor(c: Constraint): Color = when (c) {
    Constraint.FORBID -> BalochRed
    Constraint.REQUIRE_CONFIRM -> BalochAmber
    Constraint.ALLOW -> BalochCyanBright
    Constraint.QUIET_HOURS -> BalochCyanDim
}

@Composable
private fun Chip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, color, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}
