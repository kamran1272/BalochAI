package com.baloch.ai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochPanel
import com.baloch.ai.ui.theme.BalochText
import com.baloch.ai.ui.theme.BalochTextDim

private val STR_KICKER = L3(
    en = "BALOCH TIP",
    ur = "بلوچ ٹپ",
    hi = "बलोच टिप",
)

/**
 * Dismissible holographic-cyan tip card. The parent decides when to show it
 * (e.g. on the main screen: "Try: 'subah 7 baje alarm lagao aur wifi band
 * kar do'") and owns the dismissed state — [onDismiss] just signals the tap.
 *
 * The tip text wraps (no truncation — tips are meant to be read); the
 * dismiss affordance is a full 48dp target. Pass the already-localised tip
 * string; [lang] localises the card chrome.
 */
@Composable
fun TipsCard(
    tip: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    lang: BalochLanguage = BalochLanguage.ENGLISH,
) {
    val mono = FontFamily.Monospace
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BalochPanel),
        border = BorderStroke(1.dp, BalochCyan.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "💡",
                fontSize = 20.sp,
            )
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = STR_KICKER.pick(lang),
                    fontFamily = mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    color = BalochCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tip,
                    fontFamily = mono,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = BalochText,
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✕",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = BalochTextDim,
                )
            }
        }
    }
}
