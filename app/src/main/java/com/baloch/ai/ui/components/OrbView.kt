package com.baloch.ai.ui.components

import android.accessibilityservice.AccessibilityManager
import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochCyanBright
import com.baloch.ai.ui.theme.BalochCyanDim
import com.baloch.ai.ui.theme.BalochCyanFaint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** What Baloch is currently doing — drives the orb's energy. */
enum class OrbState { IDLE, LISTENING, SPEAKING, THINKING }

/**
 * True when the user asked for reduced motion: either the system
 * accessibility switch or the animator duration scale forced to 0.
 * Every cinematic component in this package honours it and renders
 * static frames instead of looping animation.
 */
internal fun isReducedMotion(context: Context): Boolean {
    return try {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (am?.isEnabled == true) return true
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    } catch (_: Exception) {
        false
    }
}

private data class Mote(
    val angleDeg: Float,
    val radiusFrac: Float,
    val speed: Float,
    val size: Float,
    val alpha: Float,
)

/** Draws one arc segment of a holographic ring. Hoisted so the draw scope stays allocation-free. */
private fun DrawScope.arcSeg(
    c: Offset,
    radius: Float,
    startAngle: Float,
    sweepAngle: Float,
    color: Color,
    width: Float,
) {
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(c.x - radius, c.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = width),
    )
}

/**
 * The Baloch orb — the hero of the main screen. Canvas-drawn hologram:
 * halo glow, dashed counter-rotating ring, arc segments, bright core.
 *
 * Four cinematic states crossfade into each other via animated
 * visibility weights (no popping):
 * - IDLE: slow 4s breathing pulse + faint drifting motes
 * - LISTENING: expanding sonar rings + waveform ring modulated by [micLevel]
 * - THINKING: 12 particles swirling on 2 tilted elliptical orbits
 * - SPEAKING: core scale + glow intensity driven by [ttsLevel]
 *
 * [micLevel] / [ttsLevel] are live 0..1 amplitudes fed by the parent
 * (mic capture / TTS engine); they are smoothed internally so the orb
 * never snaps. Pure Compose Canvas, zero assets, no per-frame
 * allocation inside the draw scope.
 */
@Composable
fun BalochOrb(
    state: OrbState,
    modifier: Modifier = Modifier,
    micLevel: Float = 0f,
    ttsLevel: Float = 0f,
) {
    val context = LocalContext.current
    val reduced = remember { isReducedMotion(context) }

    // Smooth level followers — cheap retargeting, never snaps.
    val smoothMic by animateFloatAsState(
        targetValue = micLevel.coerceIn(0f, 1f),
        animationSpec = tween(120),
        label = "micLevel",
    )
    val smoothTts by animateFloatAsState(
        targetValue = ttsLevel.coerceIn(0f, 1f),
        animationSpec = tween(120),
        label = "ttsLevel",
    )

    // Per-state visibility weights — this is what makes state changes crossfade.
    val idleW by animateFloatAsState(
        targetValue = if (state == OrbState.IDLE) 1f else 0f,
        animationSpec = tween(600),
        label = "wIdle",
    )
    val listenW by animateFloatAsState(
        targetValue = if (state == OrbState.LISTENING) 1f else 0f,
        animationSpec = tween(600),
        label = "wListen",
    )
    val thinkW by animateFloatAsState(
        targetValue = if (state == OrbState.THINKING) 1f else 0f,
        animationSpec = tween(600),
        label = "wThink",
    )
    val speakW by animateFloatAsState(
        targetValue = if (state == OrbState.SPEAKING) 1f else 0f,
        animationSpec = tween(500),
        label = "wSpeak",
    )

    // One shared infinite transition; every loop reads from it.
    // (Created unconditionally — hooks can't be conditional — but when
    // reduced motion is on, nothing reads the animated values.)
    val anim = rememberInfiniteTransition(label = "orb")
    val breath by anim.animateFloat(
        initialValue = 1f,
        targetValue = 1.09f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val spin by anim.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(12000, easing = LinearEasing)),
        label = "spin",
    )
    val spinRev by anim.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(24000, easing = LinearEasing)),
        label = "spinRev",
    )
    val spinFast by anim.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(4500, easing = LinearEasing)),
        label = "spinFast",
    )
    val sonar by anim.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(2600, easing = LinearEasing)),
        label = "sonar",
    )
    val orbit by anim.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(3600, easing = LinearEasing)),
        label = "orbit",
    )
    val drift by anim.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(30000, easing = LinearEasing)),
        label = "drift",
    )

    // Static substitutes when reduced motion is on.
    val effBreath = if (reduced) 1f else breath
    val effSpin = if (reduced) 0f else spin
    val effSpinRev = if (reduced) 0f else spinRev
    val effSpinFast = if (reduced) 0f else spinFast
    val effSonar = if (reduced) 0f else sonar
    val effOrbit = if (reduced) 0f else orbit
    val effDrift = if (reduced) 0f else drift

    // Precomputed once — never allocated per frame.
    val motes = remember {
        val rnd = Random(42)
        List(14) {
            Mote(
                angleDeg = rnd.nextFloat() * 360f,
                radiusFrac = 0.35f + rnd.nextFloat() * 0.65f,
                speed = 0.2f + rnd.nextFloat() * 0.8f,
                size = 1.5f + rnd.nextFloat() * 2.5f,
                alpha = 0.15f + rnd.nextFloat() * 0.35f,
            )
        }
    }

    Canvas(modifier = modifier.size(220.dp)) {
        val c = center
        val base = size.minDimension / 2f * 0.52f
        // Core scale: slow breathing in IDLE, TTS-amplitude driven when SPEAKING.
        val pulse = if (state == OrbState.SPEAKING) 1f + smoothTts * 0.25f else effBreath
        val r = base * pulse

        // ---- Halo glow (extra bloom while speaking, follows TTS level) ----
        drawCircle(color = BalochCyanFaint, radius = r * 1.95f, center = c)
        val glowAlpha = 0.08f + speakW * smoothTts * 0.30f + listenW * 0.08f
        drawCircle(
            color = BalochCyan.copy(alpha = glowAlpha),
            radius = r * (1.45f + smoothTts * 0.45f * speakW),
            center = c,
        )

        // ---- Sonar rings + waveform ring (LISTENING) ----
        if (listenW > 0.01f) {
            for (i in 0..1) {
                val phase = (effSonar + i * 0.5f) % 1f
                drawCircle(
                    color = BalochCyan.copy(alpha = (1f - phase) * 0.45f * listenW),
                    radius = r * (1.25f + 1.7f * phase),
                    center = c,
                    style = Stroke(width = 2f),
                )
            }
            val dots = 56
            val waveR = r * (1.55f + smoothMic * 0.55f)
            val wavePhase = effOrbit * PI.toFloat() / 180f * 3f
            for (i in 0 until dots) {
                val a = (i.toFloat() / dots) * 2f * PI.toFloat()
                val wobble = sin(a * 6f + wavePhase) * smoothMic
                val dotR = waveR + wobble * r * 0.22f
                drawCircle(
                    color = BalochCyan.copy(alpha = (0.35f + 0.65f * smoothMic) * listenW),
                    radius = 2.5f + smoothMic * 2f,
                    center = Offset(c.x + cos(a) * dotR, c.y + sin(a) * dotR),
                )
            }
        }

        // ---- Drifting motes (IDLE) ----
        if (idleW > 0.01f) {
            for (m in motes) {
                val a = (m.angleDeg + effDrift * m.speed) * PI.toFloat() / 180f
                val moteR = r * (1.30f + m.radiusFrac * 0.95f)
                drawCircle(
                    color = BalochCyanDim.copy(alpha = m.alpha * idleW),
                    radius = m.size,
                    center = Offset(c.x + cos(a) * moteR, c.y + sin(a) * moteR),
                )
            }
        }

        // ---- Orbiting particle swirl (THINKING): 12 dots, 2 tilted ellipses ----
        if (thinkW > 0.01f) {
            for (e in 0..1) {
                rotate(if (e == 0) 24f else -24f, pivot = c) {
                    val rx = r * 1.72f
                    val ry = r * 1.08f
                    val dir = if (e == 0) 1f else -1.35f
                    for (i in 0 until 6) {
                        val a = (effOrbit * dir + i * 60f + e * 30f) * PI.toFloat() / 180f
                        drawCircle(
                            color = (if (i % 2 == 0) BalochCyan else BalochCyanBright)
                                .copy(alpha = 0.85f * thinkW),
                            radius = 4f,
                            center = Offset(c.x + cos(a) * rx, c.y + sin(a) * ry),
                        )
                    }
                }
            }
        }

        // ---- Outer arc segments (always on; spin faster when busy) ----
        val arcSpin = if (state == OrbState.THINKING || state == OrbState.SPEAKING) effSpinFast else effSpin
        rotate(arcSpin, pivot = c) {
            arcSeg(c, r * 1.35f, 20f, 70f, BalochCyan, 5f)
            arcSeg(c, r * 1.35f, 200f, 50f, BalochCyanDim, 5f)
            arcSeg(c, r * 1.50f, 110f, 34f, BalochCyanBright, 3f)
        }

        // ---- Dashed counter-rotating ring ----
        rotate(effSpinRev, pivot = c) {
            val dashes = 24
            val dashR = r * 1.12f
            for (i in 0 until dashes) {
                val a = (360f / dashes) * i * PI.toFloat() / 180f
                drawCircle(
                    color = BalochCyanDim,
                    radius = 3f,
                    center = Offset(c.x + cos(a) * dashR, c.y + sin(a) * dashR),
                )
            }
        }

        // ---- Bright core ----
        drawCircle(color = BalochCyan, radius = r * 0.62f, center = c)
        drawCircle(color = BalochCyanBright, radius = r * 0.36f, center = c)
        drawCircle(color = Color.White.copy(alpha = 0.85f), radius = r * 0.15f, center = c)
    }
}
