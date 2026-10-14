package com.baloch.ai.gesture

import android.content.Context
import android.os.SystemClock
import com.baloch.ai.brain.L3
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * One camera frame's worth of hand data, as produced by
 * [GestureControlService] and consumed by the calibrator.
 *
 * @param landmarks 21 normalized landmarks, or null when no hand is visible.
 * @param avgLuma average Y-plane brightness 0..1 (for the lighting watchdog).
 */
data class HandSample(
    val landmarks: List<NormalizedLandmark>?,
    val avgLuma: Float,
)

/**
 * Per-user calibration profile.
 *
 * Generic thresholds ("fingers extended if tip is far from wrist") work
 * okay for average hands and badly for everyone else. Calibration records
 * KAMRAN'S hand — his palm size and his open-vs-closed joint ranges — and
 * [GestureControlService] compares live frames against THESE numbers, not
 * generic ones.
 */
data class CalibrationData(
    /** Wrist→middle-MCP distance (normalized units) with palm open. */
    val palmSizeOpen: Float,
    /** Per-finger tip→wrist distance with fingers EXTENDED (index..pinky). */
    val extendedTipDist: List<Float>,
    /** Per-finger tip→wrist distance with fingers CURLED (fist, index..pinky). */
    val curledTipDist: List<Float>,
    /** Thumb tip→wrist distance with thumb extended. */
    val thumbExtendedDist: Float,
    /** Thumb tip→wrist distance with thumb folded (fist). */
    val thumbFoldedDist: Float,
    /** Average scene brightness during calibration (lighting reference). */
    val lumaRef: Float,
    val calibratedAtMs: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("palmSizeOpen", palmSizeOpen)
        .put("extended", extendedTipDist.joinToString(","))
        .put("curled", curledTipDist.joinToString(","))
        .put("thumbExt", thumbExtendedDist)
        .put("thumbFold", thumbFoldedDist)
        .put("lumaRef", lumaRef)
        .put("at", calibratedAtMs)

    companion object {
        fun fromJson(o: JSONObject): CalibrationData = CalibrationData(
            palmSizeOpen = o.getDouble("palmSizeOpen").toFloat(),
            extendedTipDist = o.getString("extended").split(",").map { it.toFloat() },
            curledTipDist = o.getString("curled").split(",").map { it.toFloat() },
            thumbExtendedDist = o.getDouble("thumbExt").toFloat(),
            thumbFoldedDist = o.getDouble("thumbFold").toFloat(),
            lumaRef = o.getDouble("lumaRef").toFloat(),
            calibratedAtMs = o.getLong("at"),
        )
    }
}

/** Landmark index helpers shared by the calibrator and the classifier. */
internal object HandMath {
    const val WRIST = 0
    const val THUMB_IP = 3
    const val THUMB_TIP = 4
    // (mcp, pip, tip) per finger, index → pinky.
    val FINGERS = listOf(
        Triple(5, 6, 8),    // index
        Triple(9, 10, 12),  // middle
        Triple(13, 14, 16), // ring
        Triple(17, 18, 20), // pinky
    )
    const val MIDDLE_MCP = 9

    fun dist(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        return kotlin.math.sqrt(dx * dx + dy * dy).toFloat()
    }

    /** Palm size: wrist → middle-finger MCP. */
    fun palmSize(lm: List<NormalizedLandmark>): Float =
        dist(lm[WRIST], lm[MIDDLE_MCP])

    /** Tip→wrist distance per finger (index..pinky). */
    fun tipDistances(lm: List<NormalizedLandmark>): List<Float> {
        val wrist = lm[WRIST]
        return FINGERS.map { (_, _, tip) -> dist(lm[tip], wrist) }
    }
}

/**
 * Mandatory first-run calibration: open palm 5s, then fist 5s.
 *
 * The UI shows the prompt; this helper only samples. [sampleProvider] is
 * wired by the parent to [GestureControlService.latestSample] (works even
 * when the master toggle is off — calibration runs the camera itself via
 * the service's lab path).
 *
 * Honest failure: if Kamran's hand isn't visible in at least 60% of samples
 * for EITHER pose, [calibrate] throws [CalibrationFailedException] with a
 * plain-language reason — the UI shows it and offers retry. No fake profile
 * is ever stored.
 */
class GestureCalibrator(private val context: Context) {

    class CalibrationFailedException(message: String) : Exception(message)

    companion object {
        private const val PREFS = "baloch_gesture"
        private const val KEY_PROFILE = "calibration_profile"
        private const val POSE_SECONDS = 5L
        private const val SAMPLE_MS = 100L
        private const val MIN_COVERAGE = 0.6f

        /** UI prompt shown during the palm phase (trilingual). */
        val PROMPT_PALM = L3(
            en = "Show your open palm to the camera, sir — hold it steady ✋",
            ur = "اپنا کھلا ہاتھ کیمرے کو دکھائیں، سر — ساکت رکھیں ✋",
            hi = "अपनी खुली हथेली कैमरे को दिखाइए, सर — स्थिर रखिए ✋",
        )

        /** UI prompt shown during the fist phase (trilingual). */
        val PROMPT_FIST = L3(
            en = "Now make a tight fist — hold it steady ✊",
            ur = "اب مٹھی بند کریں — ساکت رکھیں ✊",
            hi = "अब मुट्ठी भींचिए — स्थिर रखिए ✊",
        )

        /** "Works best in decent light" hint (trilingual). */
        val LIGHT_HINT = L3(
            en = "Works best in decent light, sir 💡",
            ur = "اچھی روشنی میں بہترین کام کرتا ہے، سر 💡",
            hi = "अच्छी रोशनी में सबसे अच्छा काम करता है, सर 💡",
        )

        fun isCalibrated(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .contains(KEY_PROFILE)

        fun load(context: Context): CalibrationData? {
            return try {
                val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_PROFILE, null) ?: return null
                CalibrationData.fromJson(JSONObject(raw))
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Run the full 5s palm + 5s fist flow.
     *
     * @param sampleProvider latest hand sample, or null when the camera
     *        isn't producing frames yet.
     * @param onPhase called with 0 (palm) / 1 (fist) so the UI can swap prompts.
     * @param onProgress 0..1 within the current phase.
     */
    suspend fun calibrate(
        sampleProvider: () -> HandSample?,
        onPhase: (Int) -> Unit = {},
        onProgress: (Float) -> Unit = {},
    ): CalibrationData {
        onPhase(0)
        val palmSamples = collectPose(sampleProvider, onProgress)
        val palmCoverage = coverage(palmSamples)
        if (palmCoverage < MIN_COVERAGE) {
            throw CalibrationFailedException(
                "Palm not visible enough (${(palmCoverage * 100).toInt()}% of samples). " +
                    "Hold your open palm closer to the front camera, in good light, and retry."
            )
        }

        onPhase(1)
        val fistSamples = collectPose(sampleProvider, onProgress)
        val fistCoverage = coverage(fistSamples)
        if (fistCoverage < MIN_COVERAGE) {
            throw CalibrationFailedException(
                "Fist not visible enough (${(fistCoverage * 100).toInt()}% of samples). " +
                    "Hold your fist up to the front camera, in good light, and retry."
            )
        }

        // Pair each landmark set with its own frame's brightness.
        val palmPairs = palmSamples.mapNotNull { s ->
            s.landmarks?.let { lm -> lm to s.avgLuma }
        }
        val fistPairs = fistSamples.mapNotNull { s ->
            s.landmarks?.let { lm -> lm to s.avgLuma }
        }

        val profile = CalibrationData(
            palmSizeOpen = palmPairs.map { HandMath.palmSize(it.first) }.average().toFloat(),
            extendedTipDist = averageTipDists(palmPairs.map { it.first }),
            curledTipDist = averageTipDists(fistPairs.map { it.first }),
            thumbExtendedDist = palmPairs.map {
                HandMath.dist(it.first[HandMath.THUMB_TIP], it.first[HandMath.WRIST])
            }.average().toFloat(),
            thumbFoldedDist = fistPairs.map {
                HandMath.dist(it.first[HandMath.THUMB_TIP], it.first[HandMath.WRIST])
            }.average().toFloat(),
            lumaRef = (palmPairs.map { it.second } + fistPairs.map { it.second })
                .average().toFloat(),
            calibratedAtMs = System.currentTimeMillis(),
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PROFILE, profile.toJson().toString())
            .apply()
        return profile
    }

    /** Clear the stored profile (forces re-calibration). */
    fun clear() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_PROFILE).apply()
    }

    private suspend fun collectPose(
        sampleProvider: () -> HandSample?,
        onProgress: (Float) -> Unit,
    ): List<HandSample> {
        val out = mutableListOf<HandSample>()
        val start = SystemClock.elapsedRealtime()
        val totalMs = POSE_SECONDS * 1000
        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - start
            if (elapsed >= totalMs) break
            sampleProvider()?.let { out.add(it) }
            onProgress((elapsed.toFloat() / totalMs).coerceIn(0f, 1f))
            delay(SAMPLE_MS)
        }
        onProgress(1f)
        return out
    }

    private fun coverage(samples: List<HandSample>): Float {
        if (samples.isEmpty()) return 0f
        return samples.count { it.landmarks != null }.toFloat() / samples.size
    }

    private fun averageTipDists(
        landmarkSets: List<List<NormalizedLandmark>>,
    ): List<Float> {
        val perFinger = Array(4) { mutableListOf<Float>() }
        for (lm in landmarkSets) {
            HandMath.tipDistances(lm).forEachIndexed { i, d -> perFinger[i].add(d) }
        }
        return perFinger.map { it.average().toFloat() }
    }
}

