package com.baloch.ai.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baloch.ai.ui.components.isReducedMotion
import com.baloch.ai.ui.theme.BalochBlack
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochCyanBright
import com.baloch.ai.ui.theme.BalochCyanDim
import com.baloch.ai.ui.theme.BalochTextDim
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val BOOT_MS = 2600
private const val TAGLINE = "Systems online, sir."

// Hoisted: allocated once, not on every animation frame in the draw scope.
private val RING_FRACS = listOf(0.92f, 0.72f, 0.54f)
private val JITTER_X = listOf(0, -7, 6, -4, 3)
private val JITTER_ALPHA = listOf(1f, 0.25f, 1f, 0.55f, 1f)

/**
 * Cinematic cold-start boot: cyan wireframe rings assemble (arcs
 * drawing in with comet heads), "BALOCH" glitches into place, then
 * the tagline types out — then [onDone].
 *
 * Tap anywhere to skip. Under reduced motion the finished frame is
 * shown statically and the splash exits quickly. Pure Compose, no assets.
 *
 * @param tagline shown under BALOCH (default English). The parent may pass
 * the L3-picked line for the active language — trilingual wiring seam.
 */
@Composable
fun SplashScreen(onDone: () -> Unit, tagline: String = TAGLINE) {
    val context = LocalContext.current
    val reduced = remember { isReducedMotion(context) }

    var finished by remember { mutableStateOf(false) }
    val finish: () -> Unit = {
        if (!finished) {
            finished = true
            onDone()
        }
    }

    LaunchedEffect(Unit) {
        delay(if (reduced) 700L else BOOT_MS.toLong())
        finish()
    }

    // Single progress clock drives the whole sequence.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val bootProgress by animateFloatAsState(
        targetValue = if (started || reduced) 1f else 0f,
        animationSpec = tween(if (reduced) 1 else BOOT_MS, easing = LinearEasing),
        label = "boot",
    )
    val elapsed = bootProgress * BOOT_MS

    // "BALOCH" glitch-in: 3 quick alpha/offset jitters.
    val glitchActive = elapsed in 550f..1000f
    val jitterStep = (elapsed / 90f).toInt()
    val jitterX = if (glitchActive) JITTER_X[jitterStep % 5] else 0
    val jitterAlpha = if (glitchActive) JITTER_ALPHA[jitterStep % 5] else 1f

    // Tagline typewriter.
    val chars = ((elapsed - 1350f) / 45f).toInt().coerceIn(0, tagline.length)
    val cursorOn = (elapsed / 400f).toInt() % 2 == 0

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BalochBlack)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { finish() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Landscape survival: the wireframe canvas shrinks with the
        // viewport instead of clipping (portrait keeps ~original size).
        val canvasSize = minOf(260.dp, maxWidth * 0.7f, maxHeight * 0.42f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // THE OBSIDIAN CUBE — logo artwork assembles with glow.
            // (Rings assemble behind it; the cube is the hero.)
            val logoAlpha by animateFloatAsState(
                targetValue = if (bootProgress > 0.25f) 1f else 0f,
                animationSpec = tween(800),
                label = "logo-in",
            )
            val logoScale by animateFloatAsState(
                targetValue = if (bootProgress > 0.25f) 1f else 0.85f,
                animationSpec = tween(800),
                label = "logo-scale",
            )
            Box(
                modifier = Modifier.size(canvasSize * 0.62f),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(
                        id = context.resources.getIdentifier(
                            "ic_launcher_foreground", "drawable", context.packageName,
                        )
                    ),
                    contentDescription = "Baloch",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = logoAlpha
                            scaleX = logoScale
                            scaleY = logoScale
                            shadowElevation = 24f
                        },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Assembling wireframe rings.
            Canvas(modifier = Modifier.size(canvasSize)) {
                val c = center
                val maxR = size.minDimension / 2f
                for ((idx, frac) in RING_FRACS.withIndex()) {
                    val radius = maxR * frac
                    // Faint full track circle.
                    drawCircle(
                        color = BalochCyanDim.copy(alpha = 0.25f),
                        radius = radius,
                        center = c,
                        style = Stroke(width = 1f),
                    )
                    // Arc drawing in.
                    val prog = ((elapsed - idx * 170f) / 700f).coerceIn(0f, 1f)
                    val sweep = prog * 300f
                    if (sweep > 0.5f) {
                        val start = -90f
                        drawArc(
                            color = BalochCyan,
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(c.x - radius, c.y - radius),
                            size = Size(radius * 2f, radius * 2f),
                            style = Stroke(width = 3f),
                        )
                        // Comet head at the drawing tip.
                        val a = (start + sweep) * PI.toFloat() / 180f
                        drawCircle(
                            color = BalochCyanBright,
                            radius = 5f,
                            center = Offset(c.x + cos(a) * radius, c.y + sin(a) * radius),
                        )
                    }
                    // Tick marks on the outer ring.
                    if (idx == 0) {
                        for (k in 0 until 24) {
                            val a = k * 15f * PI.toFloat() / 180f
                            val inner = radius - 8f
                            drawLine(
                                color = BalochCyanDim.copy(alpha = 0.5f * prog),
                                start = Offset(c.x + cos(a) * inner, c.y + sin(a) * inner),
                                end = Offset(c.x + cos(a) * radius, c.y + sin(a) * radius),
                                strokeWidth = 2f,
                            )
                        }
                    }
                }
                // Core dot swelling in.
                val coreR = (9f * (elapsed / 900f).coerceIn(0f, 1f)).coerceAtLeast(0.5f)
                drawCircle(color = BalochCyan, radius = coreR, center = c)
                drawCircle(color = BalochCyanBright, radius = coreR * 0.5f, center = c)
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "BALOCH",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 14.sp,
                ),
                modifier = Modifier
                    .offset { IntOffset(jitterX, 0) }
                    .graphicsLayer { alpha = jitterAlpha },
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = tagline.substring(0, chars) + if (cursorOn) "▍" else " ",
                color = BalochCyanDim,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 14.sp,
                    letterSpacing = 2.sp,
                ),
            )
        }

        Text(
            text = "MAGSI DIGITAL · BALOCH OS",
            color = BalochTextDim.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
        )
    }
}
