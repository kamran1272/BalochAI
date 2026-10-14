package com.baloch.ai.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baloch.ai.ui.components.BackLabel
import com.baloch.ai.ui.theme.BalochAmber
import com.baloch.ai.ui.theme.BalochBlack
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochCyanBright
import com.baloch.ai.ui.theme.BalochPanel
import com.baloch.ai.ui.theme.BalochText
import com.baloch.ai.ui.theme.BalochTextDim
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * Snapshot of the gesture pipeline for the Lab. The parent owns this state
 * (e.g. in its ViewModel) and feeds it from
 * [com.baloch.ai.gesture.GestureControlService.DebugFrame] on the main thread.
 *
 * [frame] is the oriented, un-mirrored bitmap — EXACTLY what the classifier
 * sees — and [landmarks] are in that bitmap's normalized coordinates, so the
 * skeleton overlay always lines up with the picture.
 */
data class GestureLabState(
    val calibrated: Boolean = false,
    /** e.g. "ACTIVE", "LOW LIGHT", "CAMERA BUSY", "NOT CALIBRATED". */
    val statusText: String = "—",
    /** e.g. "PALM_HOLD → wake baloch"; "—" when undecided. */
    val lastGestureText: String = "—",
    val fpsText: String = "—",
    val frame: Bitmap? = null,
    val landmarks: List<NormalizedLandmark>? = null,
)

/**
 * Parent contract for the Gesture Lab.
 */
interface GestureLabActions {
    /**
     * Called ONCE when the Lab opens (and only then). Parent contract:
     * bind [com.baloch.ai.gesture.GestureControlService], call
     * `binder.attachLab { frame -> /* post to main, update GestureLabState */ }`.
     * The service starts its camera for the Lab even if the master toggle is
     * off (lab mode); gestures still fire so Kamran can verify each one.
     */
    fun onStartLab()

    /**
     * Called ONCE when the Lab closes. Parent contract: `binder.detachLab()`
     * and unbind. The service releases the camera if the master toggle is off.
     */
    fun onStopLab()

    /** Open the 10-second palm+fist calibration flow. */
    fun onCalibrate()

    fun onBack()
}

/** MediaPipe hand topology: the 21-point skeleton connections. */
private val HAND_CONNECTIONS = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,          // thumb
    0 to 5, 5 to 6, 6 to 7, 7 to 8,          // index
    5 to 9, 9 to 10, 10 to 11, 11 to 12,     // middle
    9 to 13, 13 to 14, 14 to 15, 15 to 16,   // ring
    13 to 17, 17 to 18, 18 to 19, 19 to 20,  // pinky
    0 to 17,                                 // wrist → pinky base
)

/**
 * GESTURE LAB — so Kamran can SEE what the camera sees and trust it.
 *
 * Live preview (what the classifier sees) + 21-point skeleton overlay +
 * detected-gesture readout + fps + status. Lifecycle-safe by construction:
 * the camera starts on entry ([GestureLabActions.onStartLab]) and is
 * released on exit ([GestureLabActions.onStopLab]) — no leaks, no
 * background camera after leaving.
 */
@Composable
fun GestureLabScreen(
    state: GestureLabState,
    actions: GestureLabActions,
) {
    // Lifecycle-safe: start on entry, release on exit. No exceptions.
    DisposableEffect(Unit) {
        actions.onStartLab()
        onDispose { actions.onStopLab() }
    }

    val mono = FontFamily.Monospace

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BalochBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // ── Header ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackLabel(onBack = actions.onBack)
            Text(
                text = "◈ GESTURE LAB",
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = BalochCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "What the camera sees — verify each gesture fires before trusting it.",
            fontFamily = mono,
            fontSize = 12.sp,
            color = BalochTextDim,
        )
        Spacer(Modifier.height(12.dp))

        // ── Calibration banner ──
        if (!state.calibrated) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BalochPanel)
                    .padding(12.dp),
            ) {
                Text(
                    text = "⚠ Not calibrated — gestures won't fire until Baloch learns your hand (10s).",
                    fontFamily = mono,
                    fontSize = 12.sp,
                    color = BalochAmber,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = actions.onCalibrate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BalochCyan, contentColor = BalochBlack),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("CALIBRATE NOW — PALM 5s + FIST 5s",
                    fontFamily = mono, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── Preview + skeleton overlay ──
        // NOTE: no fixed aspect ratio — the Box wraps the frame's real
        // proportions and the Canvas matches it exactly, so the skeleton
        // always lines up (portrait AND landscape).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BalochPanel),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = state.frame
            // Hoisted: asImageBitmap() wraps the bitmap — recomputing it on
            // every frame delivery (~30fps) churns the GC for no reason.
            val imageBitmap = remember(bmp) { bmp?.asImageBitmap() }
            if (imageBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = imageBitmap,
                    contentDescription = "Camera view for gesture detection",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Starting camera…",
                        fontFamily = mono,
                        fontSize = 13.sp,
                        color = BalochTextDim,
                    )
                }
            }
            val lms = state.landmarks
            if (bmp != null && lms != null && lms.size >= 21) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    fun px(i: Int): Offset =
                        Offset(lms[i].x() * w, lms[i].y() * h)
                    // Bones.
                    for ((a, b) in HAND_CONNECTIONS) {
                        drawLine(
                            color = BalochCyan,
                            start = px(a),
                            end = px(b),
                            strokeWidth = 4f,
                        )
                    }
                    // Joints.
                    for (i in 0 until 21) {
                        drawCircle(
                            color = BalochCyanBright,
                            radius = 7f,
                            center = px(i),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ── Readout ──
        LabReadoutRow("STATUS", state.statusText, BalochCyan)
        LabReadoutRow("DETECTED", state.lastGestureText, BalochCyanBright)
        LabReadoutRow("PIPELINE", state.fpsText, BalochTextDim)
        Spacer(Modifier.height(12.dp))

        // ── Honest physics ──
        Text(
            text = "Works in normal indoor light at arm's length, sir. A pitch-dark " +
                "room or an extreme hand angle is physics, not a bug — the lighting " +
                "watchdog will tell you when it can't see. 💡",
            fontFamily = mono,
            fontSize = 11.sp,
            color = BalochTextDim,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(BalochPanel)
                .padding(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Try: open palm hold 1s → \"wake baloch\" · fist → pause · " +
                "thumb up/down → yes/no · quick flick → next/previous track.",
            fontFamily = mono,
            fontSize = 11.sp,
            color = BalochTextDim,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun LabReadoutRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    val mono = FontFamily.Monospace
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = mono,
            fontSize = 11.sp,
            color = BalochTextDim,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            text = value,
            fontFamily = mono,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = valueColor,
            modifier = Modifier.weight(0.65f),
        )
    }
}
