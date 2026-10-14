package com.baloch.ai.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baloch.ai.ui.theme.BalochCyan

/**
 * Shared back-navigation chrome for the Baloch screens.
 *
 * - 48dp minimum touch targets (platform accessibility floor).
 * - Auto-mirrored for RTL: Urdu readers get a right-pointing chevron /
 *   "BACK ▶", English/Hindi readers get the left-pointing variant.
 */
@Composable
fun BackChevron(onBack: () -> Unit) {
    val chevron = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "›" else "‹"
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = chevron,
            color = BalochCyan,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Text-style back button ("◀ BACK"), same 48dp + RTL guarantees as
 * [BackChevron].
 */
@Composable
fun BackLabel(onBack: () -> Unit) {
    val label = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "BACK ▶" else "◀ BACK"
    Text(
        text = label,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = BalochCyan,
        maxLines = 1,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onBack)
            .padding(end = 12.dp),
    )
}
