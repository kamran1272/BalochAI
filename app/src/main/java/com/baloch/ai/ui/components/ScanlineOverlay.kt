package com.baloch.ai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.baloch.ai.ui.theme.BalochCyan
import kotlin.math.sin
import kotlin.random.Random

private data class MicroParticle(
    val x0: Float,
    val y0: Float,
    val vx: Float,
    val vy: Float,
    val size: Float,
    val alpha: Float,
)

private fun mod1(v: Float): Float = ((v % 1f) + 1f) % 1f

/**
 * The Iron Man hologram backdrop: static CRT scanlines, ~40 slowly
 * drifting cyan micro-particles, one slow vertical beam sweep (~7s),
 * and a subtle top vignette. Drawn last, ignores touches.
 *
 * Single Canvas. Particle seeds are precomputed with [remember];
 * positions are pure functions of the animation clock (wrap-around),
 * so nothing is allocated or mutated per frame. Renders statically
 * when reduced motion is on.
 */
@Composable
fun ScanlineOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reduced = remember { isReducedMotion(context) }

    val anim = rememberInfiniteTransition(label = "scan")
    val beamY by anim.animateFloat(
        initialValue = -0.1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(animation = tween(7000, easing = LinearEasing)),
        label = "beam",
    )
    val driftT by anim.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(90000, easing = LinearEasing)),
        label = "drift",
    )

    val particles = remember {
        val rnd = Random(7)
        List(40) {
            MicroParticle(
                x0 = rnd.nextFloat(),
                y0 = rnd.nextFloat(),
                vx = (rnd.nextFloat() - 0.5f) * 0.25f,
                vy = (rnd.nextFloat() - 0.5f) * 0.25f,
                size = 1.5f + rnd.nextFloat() * 2.5f,
                alpha = 0.12f + rnd.nextFloat() * 0.30f,
            )
        }
    }

    val t = if (reduced) 0f else driftT

    Canvas(modifier = modifier.fillMaxSize()) {
        // Static scanlines every 7px.
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.Black.copy(alpha = 0.22f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2f,
            )
            y += 7f
        }

        // Drifting micro-particles (position = f(clock), wrap-around).
        for (p in particles) {
            val px = mod1(p.x0 + t * p.vx)
            val py = mod1(p.y0 + t * p.vy)
            val twinkle = 0.55f + 0.45f * sin(t * 25.13f + p.x0 * 43f)
            drawCircle(
                color = BalochCyan.copy(alpha = p.alpha * twinkle),
                radius = p.size,
                center = Offset(px * size.width, py * size.height),
            )
        }

        // Slow vertical beam sweep.
        if (!reduced) {
            val by = beamY * size.height
            drawRect(
                color = BalochCyan.copy(alpha = 0.05f),
                topLeft = Offset(0f, by - 60f),
                size = Size(size.width, 120f),
            )
            drawLine(
                color = BalochCyan.copy(alpha = 0.28f),
                start = Offset(0f, by),
                end = Offset(size.width, by),
                strokeWidth = 2f,
            )
        }

        // Subtle top vignette.
        drawRect(
            color = Color.Black.copy(alpha = 0.18f),
            topLeft = Offset.Zero,
            size = Size(size.width, size.height * 0.08f),
        )
    }
}
