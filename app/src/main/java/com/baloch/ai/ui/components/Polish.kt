package com.baloch.ai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baloch.ai.ui.theme.BalochAmber
import com.baloch.ai.ui.theme.BalochBlack
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochPanelBorder
import com.baloch.ai.ui.theme.BalochText
import com.baloch.ai.ui.theme.BalochTextDim

// ── Shared switch styling ─────────────────────────────────────────
// One visual language for every toggle in the app: cyan track when on,
// dim thumb when off. Screens should use this instead of hand-rolling
// SwitchDefaults.colors — that is how switches drifted apart before.

@Composable
fun balochSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = BalochBlack,
    checkedTrackColor = BalochCyan,
    checkedBorderColor = BalochCyan,
    uncheckedThumbColor = BalochTextDim,
    uncheckedTrackColor = BalochPanelBorder,
    uncheckedBorderColor = BalochPanelBorder,
    disabledCheckedThumbColor = BalochTextDim.copy(alpha = 0.4f),
    disabledCheckedTrackColor = BalochCyan.copy(alpha = 0.25f),
    disabledUncheckedThumbColor = BalochTextDim.copy(alpha = 0.4f),
    disabledUncheckedTrackColor = BalochPanelBorder.copy(alpha = 0.5f),
)

// ── Hairline divider ──────────────────────────────────────────────
// Separates rows inside a settings section without shouting.

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 2.dp),
        thickness = 1.dp,
        color = BalochPanelBorder.copy(alpha = 0.6f),
    )
}

// ── Notice card ───────────────────────────────────────────────────
// Calm, non-alarming presentation for informational states and failure
// reasons (e.g. "I couldn't understand — try speaking closer").
// WARN uses warm amber, never red: a failed listen is a hint, not an
// emergency. Red is reserved for destructive confirmations.

enum class NoticeTone { INFO, WARN }

@Composable
fun NoticeCard(
    text: String,
    tone: NoticeTone = NoticeTone.INFO,
    modifier: Modifier = Modifier,
) {
    val accent = if (tone == NoticeTone.WARN) BalochAmber else BalochCyan
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = accent.copy(alpha = 0.08f),
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = BalochText,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
    Spacer(Modifier.height(6.dp))
}

// ── Empty state ───────────────────────────────────────────────────
// Centred, composed empty states for list screens — an icon, a calm
// title and one helpful hint, instead of a bare line of dim text.

@Composable
fun EmptyState(
    icon: String,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = icon,
            fontSize = 34.sp,
            textAlign = TextAlign.Center,
            color = Color.Unspecified,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = BalochText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = hint,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = BalochTextDim,
            textAlign = TextAlign.Center,
        )
    }
}
