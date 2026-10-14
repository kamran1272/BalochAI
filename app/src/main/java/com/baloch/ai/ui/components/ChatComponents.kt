package com.baloch.ai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baloch.ai.ui.theme.BalochAmber
import com.baloch.ai.ui.theme.BalochBlack
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochCyanDim
import com.baloch.ai.ui.theme.BalochPanel
import com.baloch.ai.ui.theme.BalochPanelBorder
import com.baloch.ai.ui.theme.BalochRed
import com.baloch.ai.ui.theme.BalochText
import com.baloch.ai.ui.theme.BalochTextDim

/**
 * A chat message. Slides up 24dp + fades in on a spring when it
 * arrives. Baloch's bubbles additionally get a one-time cyan
 * edge-sweep (~600ms) that travels across the card and rests.
 */
@Composable
fun ChatBubble(fromUser: Boolean, text: String) {
    val density = LocalDensity.current
    val slidePx = remember(density) { with(density) { 24.dp.roundToPx() } }

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioMediumBouncy,
            ),
            initialOffsetY = { slidePx },
        ) + fadeIn(
            animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioNoBouncy,
            ),
        ),
    ) {
        // One-shot edge sweep for Baloch bubbles (Animatable starts at 0).
        val sweep = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            if (!fromUser) sweep.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        }
        // Hoisted: pixel-exact 8px gradient band, never built per frame.
        val edgeBrush = remember {
            Brush.horizontalGradient(
                0f to Color.Transparent,
                0.5f to BalochCyan,
                1f to Color.Transparent,
                startX = 0f,
                endX = 8f,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
        ) {
            Box(
                modifier = Modifier.drawWithContent {
                    drawContent()
                    if (!fromUser && sweep.value < 1f) {
                        translate(left = sweep.value * size.width - 4f) {
                            drawRect(
                                brush = edgeBrush,
                                alpha = 1f - sweep.value * 0.4f,
                                size = Size(8f, size.height),
                            )
                        }
                    }
                },
            ) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (fromUser) BalochCyan.copy(alpha = 0.16f) else BalochPanel,
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (fromUser) BalochCyan.copy(alpha = 0.5f) else BalochPanelBorder,
                    ),
                ) {
                    Text(
                        text = text,
                        color = BalochText,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            Text(
                text = if (fromUser) "YOU" else "◈ BALOCH",
                color = BalochTextDim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

/**
 * Risky actions (call, SMS) always stop here first. Nothing executes
 * until Kamran taps Confirm.
 */
@Composable
fun ConfirmDialog(question: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BalochPanel,
        title = { Text("Confirm, sir?", color = BalochCyan) },
        text = { Text(question, color = BalochText) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BalochCyan,
                    contentColor = BalochBlack,
                ),
            ) { Text("CONFIRM") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = BalochTextDim) }
        },
    )
}

/**
 * Three bouncing cyan dots — shown while Baloch is composing a reply.
 * Dots are staggered via start offsets on one shared infinite transition.
 */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reduced = remember { isReducedMotion(context) }
    val density = LocalDensity.current
    val liftPx = remember(density) { with(density) { 6.dp.toPx() } }

    val anim = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "◈",
            color = BalochCyanDim,
            style = MaterialTheme.typography.labelSmall,
        )
        repeat(3) { i ->
            val bounce by anim.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(480, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(i * 160),
                ),
                label = "dot$i",
            )
            val b = if (reduced) 0.5f else bounce
            Canvas(
                modifier = Modifier
                    .size(9.dp)
                    .offset { IntOffset(0, -(b * liftPx).toInt()) },
            ) {
                drawCircle(
                    color = BalochCyan.copy(alpha = 0.35f + 0.65f * b),
                    radius = size.minDimension / 2f * (0.65f + 0.35f * b),
                    center = center,
                )
            }
        }
    }
}

/**
 * UI-local mirror of the domain `agents.AgentChip`
 * (`agents/Agents.kt`: `data class AgentChip(name, icon, state: ChipState)`
 * with `ChipState { WORKING, DONE, FAILED }`).
 *
 * The parent maps the domain chip to this UI state before handing it
 * here: 0 = working (amber pulse), 1 = done (cyan ✓), 2 = failed (red ✗).
 * Kept as an Int-based local copy so this file never imports the
 * agents package — the parent owns the mapping.
 */
data class AgentChipState(val name: String, val icon: String, val state: Int) {
    companion object {
        const val WORKING = 0
        const val DONE = 1
        const val FAILED = 2
    }
}

/**
 * Live per-agent status chips (the multi-agent parallel execution row).
 * Each chip springs/scales in, crossfades its tint as its state changes,
 * and the working state pulses amber. Horizontally scrollable — six agents
 * overflow a 320dp screen as a fixed Row.
 */
@Composable
fun AgentChipRow(chips: List<AgentChipState>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (chip in chips) {
            androidx.compose.runtime.key(chip.name) {
                AgentChipItem(chip = chip)
            }
        }
    }
}

@Composable
private fun AgentChipItem(chip: AgentChipState) {
    val context = LocalContext.current
    val reduced = remember { isReducedMotion(context) }

    // Hooks stay unconditional; only the *read* is gated on state.
    val pulseAnim = rememberInfiniteTransition(label = "chipPulse")
    val pulse by pulseAnim.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val tint by animateColorAsState(
        targetValue = when (chip.state) {
            AgentChipState.DONE -> BalochCyan
            AgentChipState.FAILED -> BalochRed
            else -> BalochAmber
        },
        animationSpec = tween(300),
        label = "chipTint",
    )
    val pulseAlpha = if (chip.state == AgentChipState.WORKING && !reduced) pulse else 1f

    AnimatedVisibility(
        visible = true,
        enter = scaleIn(
            animationSpec = spring(
                stiffness = Spring.StiffnessMedium,
                dampingRatio = Spring.DampingRatioMediumBouncy,
            ),
        ) + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BalochPanel.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, tint.copy(alpha = 0.7f * pulseAlpha)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = chip.icon, fontSize = 13.sp)
                Text(
                    text = chip.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = BalochText,
                )
                Text(
                    text = when (chip.state) {
                        AgentChipState.DONE -> "✓"
                        AgentChipState.FAILED -> "✗"
                        else -> "◌"
                    },
                    color = tint.copy(alpha = pulseAlpha),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Prominent MANUAL ⟷ JARVIS mode switch. The track morphs from dim
 * steel to bright cyan glow, the thumb slides on a bouncy spring, the
 * labels crossfade, and a haptic tick fires on toggle. The parent
 * places it on the main screen and owns the persisted mode.
 */
@Composable
fun ModeToggle(
    isJarvis: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val trackColor by animateColorAsState(
        targetValue = if (isJarvis) BalochCyan.copy(alpha = 0.30f) else Color(0xFF22333B),
        animationSpec = tween(300),
        label = "modeTrack",
    )
    val thumbPos by animateFloatAsState(
        targetValue = if (isJarvis) 1f else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioMediumBouncy,
        ),
        label = "modeThumb",
    )
    val glowAmt by animateFloatAsState(
        targetValue = if (isJarvis) 1f else 0f,
        animationSpec = tween(300),
        label = "modeGlow",
    )

    val trackW = 76.dp
    val trackH = 48.dp // 48dp minimum touch target — the track IS the button.
    val thumbD = 38.dp
    val pad = 5.dp
    val travelPx = remember(density) { with(density) { (trackW - thumbD - pad * 2).toPx() } }
    val padPx = remember(density) { with(density) { pad.toPx() } }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ModeLabel(text = "MANUAL", active = !isJarvis, activeColor = BalochTextDim)
        Box(
            modifier = Modifier
                .size(trackW, trackH)
                .shadow(
                    elevation = (14f * glowAmt).dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = BalochCyan,
                )
                .clip(RoundedCornerShape(24.dp))
                .background(trackColor)
                .border(
                    width = 1.dp,
                    color = BalochCyan.copy(alpha = 0.25f + 0.65f * glowAmt),
                    shape = RoundedCornerShape(24.dp),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle(!isJarvis)
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((padPx + thumbPos * travelPx).toInt(), 0) }
                    .size(thumbD)
                    .shadow(
                        elevation = (8f * glowAmt).dp,
                        shape = CircleShape,
                        spotColor = BalochCyan,
                    )
                    .clip(CircleShape)
                    .background(if (isJarvis) BalochCyan else Color(0xFF5C6F77)),
            )
        }
        ModeLabel(text = "JARVIS", active = isJarvis, activeColor = BalochCyan)
    }
}

@Composable
private fun ModeLabel(text: String, active: Boolean, activeColor: Color) {
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0.38f,
        animationSpec = tween(250),
        label = "modeLabel",
    )
    Text(
        text = text,
        color = activeColor.copy(alpha = alpha),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = 2.sp,
        ),
    )
}

/**
 * Cyan glow-ripple press state for IconButtons (and any pressable).
 *
 * A radial cyan bloom fades in while pressed and out on release. The
 * press is observed with `detectTapGestures(onPress)` which does NOT
 * consume the down event, so the wrapped clickable / IconButton still
 * receives the tap normally. The gradient brush is hoisted per size —
 * nothing is allocated inside the draw scope.
 *
 * Usage: `IconButton(onClick = {...}, modifier = Modifier.glowPress()) { ... }`
 * Pair with `indication = null` on the clickable if you want only the glow.
 */
fun Modifier.glowPress(): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    var viewSize by remember { mutableStateOf(Size.Zero) }

    val glow by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(160),
        label = "glowPress",
    )

    // Hoisted per size — never built inside the draw scope.
    val brush = remember(viewSize) {
        if (viewSize == Size.Zero) {
            null
        } else {
            Brush.radialGradient(
                0f to BalochCyan.copy(alpha = 0.55f),
                1f to Color.Transparent,
                center = Offset(viewSize.width / 2f, viewSize.height / 2f),
                radius = viewSize.maxDimension * 0.8f,
            )
        }
    }

    this
        .onSizeChanged { viewSize = Size(it.width.toFloat(), it.height.toFloat()) }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    try {
                        tryAwaitRelease()
                    } finally {
                        pressed = false
                    }
                },
            )
        }
        .drawWithContent {
            drawContent()
            val b = brush
            if (b != null && glow > 0.01f) {
                drawCircle(
                    brush = b,
                    radius = size.maxDimension * 0.8f,
                    center = center,
                    alpha = glow,
                )
            }
        }
}
