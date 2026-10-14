package com.baloch.ai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Official Baloch visual identity: Holographic Cyan — Iron Man style,
 * tightened to the Obsidian Cube's DNA (Logo 8).
 * - OBSIDIAN surfaces: deep cut-black glass (#05070A), never flat black.
 * - CYAN GLOW = the cube's glowing B (#00E5FF): sparing, powerful.
 * Cards get a subtle top-edge bevel highlight like cut glass.
 */
val BalochCyan = Color(0xFF00E5FF)
val BalochCyanBright = Color(0xFF80F5FF)
val BalochCyanDim = Color(0xFF0E6E7E)
val BalochCyanFaint = Color(0x1400E5FF)
val BalochBlack = Color(0xFF05070A)
val BalochObsidian = Color(0xFF05070A)
val BalochObsidianFacet = Color(0xFF0B1116)
val BalochPanel = Color(0xFF070D12)
val BalochPanelBorder = Color(0xFF0F2E36)
val BalochBevelHighlight = Color(0x3300E5FF)
val BalochText = Color(0xFFE4FBFF)
val BalochTextDim = Color(0xFF7FA3AC)
val BalochAmber = Color(0xFFFFB300)
val BalochRed = Color(0xFFFF5252)

private val BalochColorScheme = darkColorScheme(
    primary = BalochCyan,
    onPrimary = BalochBlack,
    secondary = BalochCyanDim,
    background = BalochBlack,
    surface = BalochPanel,
    onBackground = BalochText,
    onSurface = BalochText,
    surfaceVariant = BalochPanelBorder,
    error = BalochRed,
)

private val BalochTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = BalochText,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        color = BalochCyan,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        color = BalochTextDim,
    ),
)

@Composable
fun BalochTheme(content: @Composable () -> Unit) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = BalochColorScheme,
        typography = BalochTypography,
        content = content,
    )
}
