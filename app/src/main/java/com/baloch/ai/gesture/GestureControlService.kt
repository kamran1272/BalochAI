package com.baloch.ai.gesture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.baloch.ai.MainActivity
import com.baloch.ai.brain.L3
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker.HandLandmarkerOptions
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.channels.FileChannel
import java.security.MessageDigest

/**
 * Camera hand-gesture control — the Iron Man part.
 *
 * Foreground service (camera type) running MediaPipe's HandLandmarker
 * (on-device TFLite, GPU when available, CPU fallback) on the FRONT camera
 * at 12fps. Recognised gestures fire through [onGesture]; the parent maps
 * each [GestureKind] via [GestureMap] and routes the command text through
 * the Orchestrator — so voice lock, risk tiers and confirmations all still
 * apply. Gestures are just another way to say the words.
 *
 * RELIABILITY (Kamran's quality order — "achy sy work krain, ALWAYS"):
 *  1. CALIBRATION GATE — nothing fires until [GestureCalibrator] has stored
 *     Kamran's palm+fist profile. Recognition compares against HIS hand.
 *  2. ANTI-FLICKER — a static pose must classify identically (≥70% of
 *     frames, hand continuously present) for 800ms before firing.
 *  3. COOLDOWN — 1.5s after every trigger. No double-fires.
 *  4. LIGHTING WATCHDOG — hand visible in <25% of frames for 5s →
 *     [onSpeakRequest] fires with a trilingual "need more light" line;
 *     detection keeps running and resumes silently when light recovers.
 *  5. 12fps throttle — enough for gestures, kind to battery.
 *  6. CAMERA RESILIENCE — disconnect/conflict → status CAMERA_BUSY, quiet
 *     backoff, auto-resume when the camera is free again.
 *  7. Screen OFF → camera pauses (privacy + battery). Sensor gestures are
 *     the ones that work with the screen off — by design, see
 *     [SensorGestureService].
 *
 * HONEST PHYSICS LIMITS (also shown in the UI): works in normal indoor
 * light at arm's length. A pitch-dark room or an extreme hand angle is a
 * physics limit, not a bug — the watchdog says so out loud instead of
 * silently failing.
 *
 * PRIVACY: every frame is processed on-device and discarded immediately.
 * No video is stored, no frame ever leaves the phone. The persistent
 * "👆 Gesture control active" notification is the camera-active indicator.
 */
class GestureControlService : android.app.Service() {

    companion object {
        private const val TAG = "GestureControl"
        private const val PREFS = "baloch_gesture"

        /** Master toggle — default OFF. This is Kamran's choice, always. */
        const val KEY_ENABLED = "gesture_control_enabled"

        /** Official MediaPipe model bundle (float16, ~7.8MB). Downloaded once. */
        const val MODEL_URL =
            "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
        const val MODEL_FILE = "hand_landmarker.task"

        /** SHA-256 of the pinned model bundle (verified July 2026 provenance). */
        private const val MODEL_SHA256 =
            "fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1"

        const val ACTION_GESTURE = "com.baloch.ai.gesture.GESTURE_DETECTED"
        const val EXTRA_GESTURE = "gesture"
        const val EXTRA_COMMAND = "command"

        private const val CHANNEL_ID = "baloch_gesture"
        private const val NOTIF_ID = 4101
        private const val FRAME_W = 640
        private const val FRAME_H = 480
        private const val FRAME_INTERVAL_MS = 83L // ~12fps
        private const val HOLD_WINDOW = 10        // frames ≈ 800ms at 12fps
        private const val HOLD_AGREEMENT = 0.7f
        private const val COOLDOWN_MS = 1500L
        private const val SWIPE_PALM_WIDTHS = 1.2f
        private const val MAX_SWIPE_MS = 600L

        /** Shown in Settings → Gestures (camera tab). */
        const val BATTERY_NOTE =
            "Camera gestures watch the front camera continuously (~5–8%/hr). " +
                "Baloch auto-pauses the camera when the screen is off. " +
                "Sensor gestures are far cheaper — see the Sensor tab."

        /** Trilingual low-light line — the parent speaks it via VoiceOutput. */
        val LOW_LIGHT_SPEECH = L3(
            en = "Sir, thori roshni chahiye — haath saaf nazar nahi aa raha.",
            ur = "سر، تھوڑی روشنی چاہیے — ہاتھ صاف نظر نہیں آ رہا۔",
            hi = "सर, थोड़ी रोशनी चाहिए — हाथ साफ़ नज़र नहीं आ रहा।",
        )

        fun isMasterEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)

        /**
         * Flip the master toggle. The service starts/stops itself to match.
         * Turning it ON with no calibration stored starts the pipeline in
         * NOT_CALIBRATED status — the UI must offer calibration; nothing
         * fires until the profile exists.
         */
        fun setMasterEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, enabled).apply()
            if (enabled) {
                ContextCompat.startForegroundService(
                    context, Intent(context, GestureControlService::class.java)
                )
            } else {
                context.stopService(Intent(context, GestureControlService::class.java))
            }
        }
    }

    /** What the pipeline is doing right now (drives the Settings status row). */
    enum class GestureStatus {
        STARTING, ACTIVE, PAUSED_SCREEN_OFF, CAMERA_BUSY,
        LOW_LIGHT, NOT_CALIBRATED, MODEL_ERROR, DISABLED,
    }

    /** One debug frame for the Gesture Lab overlay (built only when attached). */
    data class DebugFrame(
        /** Oriented + un-mirrored bitmap — exactly what the classifier sees. */
        val bitmap: Bitmap,
        /** Landmarks in the bitmap's normalized coordinates (null = no hand). */
        val landmarks: List<NormalizedLandmark>?,
        /** Current window-majority gesture vote (null = undecided). */
        val gestureVote: GestureKind?,
        val status: GestureStatus,
        val fps: Float,
    )

    // ── Wiring seams (parent sets these) ──────────────────────────
    /**
     * Fired on every recognised gesture (de-bounced, calibrated).
     * Called on a MediaPipe background thread — parent must hop to main
     * for UI work.
     */
    var onGesture: ((GestureKind) -> Unit)? = null

    /**
     * Convenience: same moment as [onGesture], but with the mapped command
     * text already resolved via [GestureMap]. Same shape as
     * [SensorGestureService.onCommand] so the parent can share one handler.
     * Background thread — same hop rule as [onGesture].
     */
    var onCommand: ((String) -> Unit)? = null

    /** Parent speaks this (VoiceOutput) when the lighting watchdog trips. */
    var onSpeakRequest: ((L3) -> Unit)? = null

    /**
     * Status changes for the Settings "camera gestures" status row.
     * May arrive on a background thread.
     */
    var onStatusChange: ((GestureStatus) -> Unit)? = null

    /** Gesture Lab listener. Called on a MediaPipe background thread. */
    var onDebugFrame: ((DebugFrame) -> Unit)? = null

    inner class GestureBinder : Binder() {
        fun service(): GestureControlService = this@GestureControlService
        fun attachLab(listener: (DebugFrame) -> Unit) {
            onDebugFrame = listener
            labActive = true
            updateRunState()
        }
        fun detachLab() {
            onDebugFrame = null
            labActive = false
            updateRunState()
        }
        fun latestSample(): HandSample? = lastSample
        fun status(): GestureStatus = currentStatus

        /**
         * Call after [GestureCalibrator.calibrate] succeeds: reloads the
         * profile and lifts the NOT_CALIBRATED gate without restarting.
         */
        fun notifyCalibrated() {
            calibration = GestureCalibrator.load(this@GestureControlService)
            if (calibration != null && currentStatus == GestureStatus.NOT_CALIBRATED) {
                setStatus(GestureStatus.ACTIVE)
            }
        }
    }

    private val binder = GestureBinder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var cameraThread: HandlerThread
    private lateinit var cameraHandler: Handler
    private lateinit var cameraManager: CameraManager
    private lateinit var gestureMap: GestureMap

    @Volatile private var running = false
    @Volatile private var labActive = false
    @Volatile private var detectionInFlight = false
    @Volatile private var lastSample: HandSample? = null
    @Volatile private var currentStatus: GestureStatus = GestureStatus.DISABLED
    /** Cached calibration profile — reloaded via binder.notifyCalibrated(). */
    @Volatile private var calibration: CalibrationData? = null

    private var landmarker: HandLandmarker? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var frontCameraId: String? = null
    private var sensorOrientation = 270
    private var screenOff = false
    private var cameraConflict = false
    private var lowLight = false
    private var lastSubmitMs = 0L
    private var lastTriggerMs = 0L
    private var fpsEma = 12f
    private var lastResultMs = 0L

    // Anti-flicker window: last HOLD_WINDOW votes (null = no hand/uncertain).
    private val voteWindow = ArrayDeque<GestureKind?>()
    // Swipe centroid trail (motion signature, not a static hold).
    private val trail = ArrayDeque<TrailPoint>()
    private data class TrailPoint(val x: Float, val y: Float, val pw: Float, val t: Long)
    // Lighting watchdog: hand-present ratio over rolling 5s.
    private val lightWindow = ArrayDeque<Boolean>()
    private var lastLightCheckMs = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOff = true
                    pauseCamera()
                    setStatus(GestureStatus.PAUSED_SCREEN_OFF)
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOff = false
                    if (isMasterEnabled(this@GestureControlService) && !cameraConflict) {
                        openCamera()
                    }
                }
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        gestureMap = GestureMap(this)
        cameraThread = HandlerThread("GestureCamera").also { it.start() }
        cameraHandler = Handler(cameraThread.looper)
        createChannel()
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        // API 33+ requires an explicit export flag for context receivers.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, screenFilter)
        }
        setStatus(GestureStatus.STARTING)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithType()
        updateRunState()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) { }
        stopPipeline()
        cameraThread.quitSafely()
        scope.cancel()
        super.onDestroy()
    }

    /** Recompute whether the pipeline should run, then match it. */
    private fun updateRunState() {
        val wantRun = isMasterEnabled(this) || labActive
        if (wantRun && !running) {
            running = true
            startForegroundWithType()
            scope.launch { startPipeline() }
        } else if (!wantRun && running) {
            stopPipeline()
            stopSelf()
        }
    }

    private fun setStatus(s: GestureStatus) {
        if (currentStatus == s) return
        currentStatus = s
        updateNotification()
        try { onStatusChange?.invoke(s) } catch (_: Exception) { }
    }

    // ── Pipeline startup ──────────────────────────────────────────
    private suspend fun startPipeline() {
        try {
            // 1. Model (download once, SHA-verified).
            val modelFile = ensureModelFile()
            // 2. Landmarker (GPU → CPU fallback), LIVE_STREAM.
            landmarker = HandLandmarkerHelper.createHandLandmarker(
                this, modelFile, resultListener, errorListener
            )
            // 3. Calibration gate (cached; refreshed via binder.notifyCalibrated()).
            calibration = GestureCalibrator.load(this)
            if (calibration == null) {
                setStatus(GestureStatus.NOT_CALIBRATED)
            }
            // 4. Camera.
            findFrontCamera()
            if (!screenOff) openCamera()
            if (currentStatus == GestureStatus.STARTING) {
                setStatus(if (calibration != null)
                    GestureStatus.ACTIVE else GestureStatus.NOT_CALIBRATED)
            }
        } catch (e: Exception) {
            Log.e(TAG, "pipeline start failed", e)
            setStatus(GestureStatus.MODEL_ERROR)
            notifyOnce(
                "Gesture control couldn't start",
                honestError(e),
            )
            // Revert the toggle so the UI reflects reality.
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, false).apply()
            stopSelf()
        }
    }

    private fun honestError(e: Exception): String = when (e) {
        is java.io.IOException ->
            "Model download failed — check internet and retry. Nothing was changed."
        is IllegalStateException ->
            "No front camera found on this device — camera gestures unavailable."
        else ->
            "Startup failed (${e.message ?: "unknown error"}). Camera gestures are off."
    }

    private fun stopPipeline() {
        running = false
        try { captureSession?.close() } catch (_: Exception) { }
        try { cameraDevice?.close() } catch (_: Exception) { }
        try { imageReader?.close() } catch (_: Exception) { }
        try { landmarker?.close() } catch (_: Exception) { }
        captureSession = null
        cameraDevice = null
        imageReader = null
        landmarker = null
        voteWindow.clear()
        trail.clear()
        setStatus(GestureStatus.DISABLED)
    }

    // ── Model ─────────────────────────────────────────────────────
    private fun ensureModelFile(): File {
        val out = File(filesDir, MODEL_FILE)
        if (out.exists() && out.length() > 5_000_000L && sha256(out) == MODEL_SHA256) {
            return out
        }
        Log.i(TAG, "downloading hand_landmarker.task (one-time, ~7.8MB)")
        val tmp = File(filesDir, "$MODEL_FILE.tmp")
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 90_000
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) {
                throw java.io.IOException("HTTP ${conn.responseCode} fetching model")
            }
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            }
            if (tmp.length() < 5_000_000L || sha256(tmp) != MODEL_SHA256) {
                tmp.delete()
                throw java.io.IOException("model failed integrity check")
            }
            if (!tmp.renameTo(out)) throw java.io.IOException("model install failed")
            return out
        } finally {
            conn?.disconnect()
        }
    }

    private fun sha256(f: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(f).use { input ->
            val buf = ByteArray(8192)
            var n: Int
            while (input.read(buf).also { n = it } > 0) digest.update(buf, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // ── Camera ────────────────────────────────────────────────────
    private fun findFrontCamera() {
        for (id in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_FRONT
            ) {
                frontCameraId = id
                sensorOrientation =
                    chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
                return
            }
        }
        throw IllegalStateException("no front camera")
    }

    private fun openCamera() {
        val id = frontCameraId ?: return
        if (cameraDevice != null || !running) return
        try {
            @Suppress("MissingPermission") // CAMERA declared; parent requests at runtime.
            cameraManager.openCamera(id, deviceCallback, cameraHandler)
        } catch (e: CameraAccessException) {
            onCameraConflict("open failed: ${e.message}")
        } catch (e: SecurityException) {
            setStatus(GestureStatus.MODEL_ERROR)
            notifyOnce("Camera permission missing",
                "Grant the Camera permission in Settings → Privacy & Permissions.")
            Log.e(TAG, "camera permission", e)
        }
    }

    private fun pauseCamera() {
        try { captureSession?.close() } catch (_: Exception) { }
        try { cameraDevice?.close() } catch (_: Exception) { }
        captureSession = null
        cameraDevice = null
    }

    private val deviceCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(device: CameraDevice) {
            cameraDevice = device
            cameraConflict = false
            createSession()
        }

        override fun onDisconnected(device: CameraDevice) {
            device.close()
            if (cameraDevice == device) cameraDevice = null
            onCameraConflict("disconnected")
        }

        override fun onError(device: CameraDevice, error: Int) {
            device.close()
            if (cameraDevice == device) cameraDevice = null
            onCameraConflict("error $error")
        }
    }

    /**
     * Another app took the camera (or it errored): back off quietly, show
     * "camera busy" in the status row, retry in 5s. Notifies once — never
     * spams.
     */
    private fun onCameraConflict(reason: String) {
        Log.w(TAG, "camera conflict: $reason")
        pauseCamera()
        cameraConflict = true
        setStatus(GestureStatus.CAMERA_BUSY)
        cameraHandler.postDelayed({
            if (!running || !isMasterEnabled(this@GestureControlService) || screenOff) return@postDelayed
            cameraConflict = false
            openCamera()
            // If it still fails, onError/onCameraConflict re-arms the backoff.
        }, 5000)
    }

    private fun createSession() {
        val device = cameraDevice ?: return
        try {
            imageReader?.close()
            imageReader = ImageReader.newInstance(FRAME_W, FRAME_H, ImageFormat.YUV_420_888, 2)
            imageReader?.setOnImageAvailableListener(imageListener, cameraHandler)
            val targets = mutableListOf<Surface>()
            imageReader?.surface?.let { targets.add(it) }
            @Suppress("DEPRECATION") // SessionConfiguration needs API 28+; this works on 26+.
            device.createCaptureSession(targets,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            val req = device.createCaptureRequest(
                                CameraDevice.TEMPLATE_PREVIEW)
                            targets.forEach { req.addTarget(it) }
                            req.set(CaptureRequest.CONTROL_MODE,
                                CaptureRequest.CONTROL_MODE_AUTO)
                            session.setRepeatingRequest(req.build(), null, cameraHandler)
                            if (currentStatus == GestureStatus.CAMERA_BUSY ||
                                currentStatus == GestureStatus.STARTING
                            ) {
                                setStatus(if (calibration != null)
                                    GestureStatus.ACTIVE
                                else GestureStatus.NOT_CALIBRATED)
                            }
                        } catch (e: CameraAccessException) {
                            onCameraConflict("request failed")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        onCameraConflict("configure failed")
                    }
                }, cameraHandler)
        } catch (e: CameraAccessException) {
            onCameraConflict("session failed")
        }
    }

    // ── Frames → MediaPipe ────────────────────────────────────────
    private val imageListener = ImageReader.OnImageAvailableListener { reader ->
        val image = try { reader.acquireLatestImage() } catch (_: Exception) { null }
            ?: return@OnImageAvailableListener
        try {
            val now = SystemClock.uptimeMillis()
            if (detectionInFlight || now - lastSubmitMs < FRAME_INTERVAL_MS) return@OnImageAvailableListener
            val lm = landmarker ?: return@OnImageAvailableListener
            lastSubmitMs = now
            val oriented = yuvToOrientedBitmap(image) ?: return@OnImageAvailableListener
            val luma = avgLuma(image)
            val mpImage: MPImage = try {
                BitmapImageBuilder(oriented).build()
            } catch (e: Exception) {
                Log.w(TAG, "mpimage build failed", e)
                return@OnImageAvailableListener
            }
            detectionInFlight = true
            try {
                lm.detectAsync(mpImage, now)
            } catch (e: Exception) {
                detectionInFlight = false
                Log.w(TAG, "detectAsync failed", e)
            } finally {
                try { mpImage.close() } catch (_: Exception) { }
            }
            lastOrientedBitmap = oriented
            lastLuma = luma
        } finally {
            try { image.close() } catch (_: Exception) { }
        }
    }

    @Volatile private var lastOrientedBitmap: Bitmap? = null
    @Volatile private var lastLuma = 0.5f

    private val resultListener: (HandLandmarkerResult, MPImage) -> Unit =
        { result: HandLandmarkerResult, _: MPImage ->
            detectionInFlight = false
            val now = SystemClock.uptimeMillis()
            if (lastResultMs > 0) {
                val dt = (now - lastResultMs).coerceAtLeast(1)
                fpsEma = 0.9f * fpsEma + 0.1f * (1000f / dt)
            }
            lastResultMs = now
            try {
                val landmarks = result.landmarks().firstOrNull()
                lastSample = HandSample(landmarks, lastLuma)
                onHandResult(landmarks, now)
                emitDebugFrame(landmarks)
            } catch (e: Exception) {
                Log.w(TAG, "result handling failed", e)
            }
        }

    private val errorListener: (RuntimeException) -> Unit = { e: RuntimeException ->
        detectionInFlight = false
        Log.e(TAG, "landmarker error", e)
    }

    // ── Classification + anti-flicker + watchdog ──────────────────
    private fun onHandResult(
        landmarks: List<NormalizedLandmark>?,
        now: Long,
    ) {
        val profile = calibration
        val calibrated = profile != null

        // Lighting watchdog: hand-present ratio over rolling 5s.
        lightWindow.addLast(landmarks != null)
        while (lightWindow.size > 60) lightWindow.removeFirst() // ~5s at 12fps
        if (now - lastLightCheckMs > 1000) {
            lastLightCheckMs = now
            val ratio = if (lightWindow.isEmpty()) 1f
            else lightWindow.count { it }.toFloat() / lightWindow.size
            if (!lowLight && lightWindow.size >= 30 && ratio < 0.25f) {
                lowLight = true
                setStatus(GestureStatus.LOW_LIGHT)
                speakOrNotifyLowLight()
            } else if (lowLight && ratio > 0.5f) {
                // Auto-resume: detection never stopped, just the warning.
                lowLight = false
                setStatus(if (calibrated) GestureStatus.ACTIVE
                else GestureStatus.NOT_CALIBRATED)
            }
        }

        // Calibration gate: watch, but never fire.
        if (!calibrated) {
            voteWindow.clear()
            trail.clear()
            return
        }

        val vote = GestureClassifier.classify(landmarks, profile)
        voteWindow.addLast(vote)
        while (voteWindow.size > HOLD_WINDOW) voteWindow.removeFirst()

        // Swipe trail (motion signature, not a static hold).
        if (landmarks != null) {
            val cx = landmarks.map { it.x() }.average().toFloat()
            val cy = landmarks.map { it.y() }.average().toFloat()
            val pw = HandMath.dist(landmarks[5], landmarks[17]).coerceAtLeast(0.01f)
            trail.addLast(TrailPoint(cx, cy, pw, now))
            while (trail.isNotEmpty() && now - trail.first().t > MAX_SWIPE_MS) {
                trail.removeFirst()
            }
            checkSwipe(now)
        } else {
            voteWindow.clear()
            trail.clear()
        }

        // Anti-flicker: full window, no gaps, ≥70% agreement, then fire.
        if (voteWindow.size == HOLD_WINDOW &&
            voteWindow.none { it == null } &&
            now - lastTriggerMs > COOLDOWN_MS
        ) {
            val top = voteWindow.filterNotNull()
                .groupingBy { it }.eachCount()
                .maxByOrNull { it.value }
            if (top != null && top.value.toFloat() / HOLD_WINDOW >= HOLD_AGREEMENT) {
                fire(top.key, now)
            }
        }
    }

    private fun checkSwipe(now: Long) {
        if (trail.size < 3 || now - lastTriggerMs <= COOLDOWN_MS) return
        val first = trail.first()
        val last = trail.last()
        val dt = last.t - first.t
        if (dt <= 0 || dt > MAX_SWIPE_MS) return
        val pw = (first.pw + last.pw) / 2f
        val dx = last.x - first.x
        val dy = last.y - first.y
        // Bitmap was un-mirrored: +dx = the user's real right.
        if (kotlin.math.abs(dx) >= SWIPE_PALM_WIDTHS * pw &&
            kotlin.math.abs(dy) < 0.8f * pw
        ) {
            fire(if (dx > 0) GestureKind.SWIPE_RIGHT else GestureKind.SWIPE_LEFT, now)
        }
    }

    private fun fire(kind: GestureKind, now: Long) {
        lastTriggerMs = now
        voteWindow.clear()
        trail.clear()
        val command = gestureMap.commandFor(kind)
        Log.i(TAG, "gesture fired: $kind → \"$command\"")
        try { onGesture?.invoke(kind) } catch (e: Exception) { Log.w(TAG, "onGesture", e) }
        try { onCommand?.invoke(command) } catch (e: Exception) { Log.w(TAG, "onCommand", e) }
        sendBroadcast(Intent(ACTION_GESTURE).apply {
            setPackage(packageName)
            putExtra(EXTRA_GESTURE, kind.name)
            putExtra(EXTRA_COMMAND, command)
        })
    }

    private fun speakOrNotifyLowLight() {
        val speech = LOW_LIGHT_SPEECH
        val cb = onSpeakRequest
        if (cb != null) {
            try { cb(speech) } catch (_: Exception) { }
        } else {
            notifyOnce("Low light for gestures",
                "Baloch can't see your hand clearly — a little more light, sir 💡")
        }
    }

    private fun emitDebugFrame(landmarks: List<NormalizedLandmark>?) {
        val listener = onDebugFrame ?: return
        val bmp = lastOrientedBitmap ?: return
        // Downscale for the Lab — skeleton only needs the shape.
        val w = 480
        val h = (bmp.height * (w.toFloat() / bmp.width)).toInt().coerceAtLeast(1)
        val small = try {
            Bitmap.createScaledBitmap(bmp, w, h, true)
        } catch (_: Exception) { return }
        val vote = voteWindow.filterNotNull().groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key
        try {
            listener(DebugFrame(small, landmarks, vote, currentStatus, fpsEma))
        } catch (e: Exception) {
            Log.w(TAG, "debug frame listener", e)
        }
    }

    // ── Image conversion ──────────────────────────────────────────
    /**
     * YUV_420_888 → upright, un-mirrored ARGB bitmap.
     *
     * Rotation is computed per frame from the current display rotation, so
     * portrait AND landscape both work. The front camera is mirrored
     * horizontally so coordinates match the user's own view (swipe right =
     * the hand really moved to the user's right) — the Gesture Lab shows
     * exactly this image, so Kamran can verify the mapping visually.
     */
    private fun yuvToOrientedBitmap(image: Image): Bitmap? {
        val flat = yuvToBitmap(image) ?: return null
        return try {
            val rotation = (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.rotation
            val degrees = when (rotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            // Front camera: (sensor - device + 360) % 360, then un-mirror.
            val total = (sensorOrientation - degrees + 360) % 360
            val m = Matrix()
            m.postScale(-1f, 1f, flat.width / 2f, flat.height / 2f)
            m.postRotate(total.toFloat())
            Bitmap.createBitmap(flat, 0, 0, flat.width, flat.height, m, true)
        } catch (e: Exception) {
            Log.w(TAG, "orientation failed", e)
            flat
        }
    }

    /** Stride-aware YUV_420_888 → NV21 → JPEG → Bitmap. */
    private fun yuvToBitmap(image: Image): Bitmap? {
        return try {
            val w = image.width
            val h = image.height
            val planes = image.planes
            val yBuf = planes[0].buffer
            val uBuf = planes[1].buffer
            val vBuf = planes[2].buffer
            val yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixStride = planes[1].pixelStride
            val nv21 = ByteArray(w * h + w * h / 2)
            var pos = 0
            for (row in 0 until h) {
                yBuf.position(row * yRowStride)
                yBuf.get(nv21, pos, w)
                pos += w
            }
            val uvH = h / 2
            for (row in 0 until uvH) {
                for (col in 0 until w / 2) {
                    nv21[pos++] = vBuf.get(row * uvRowStride + col * uvPixStride)
                    nv21[pos++] = uBuf.get(row * uvRowStride + col * uvPixStride)
                }
            }
            val yuv = YuvImage(nv21, ImageFormat.NV21, w, h, null)
            val out = ByteArrayOutputStream()
            yuv.compressToJpeg(Rect(0, 0, w, h), 80, out)
            val bytes = out.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.w(TAG, "yuv conversion failed", e)
            null
        }
    }

    /** Average scene brightness 0..1, sampled from the Y plane. */
    private fun avgLuma(image: Image): Float {
        return try {
            val buf = image.planes[0].buffer
            var sum = 0L
            var n = 0L
            var i = 0
            val limit = buf.remaining()
            while (i < limit) {
                // Sample every 64th byte, respecting row stride implicitly
                // (absolute gets; stride only shifts which rows we hit).
                sum += (buf.get(i).toInt() and 0xFF)
                n++
                i += 64
            }
            if (n == 0L) 0.5f else sum.toFloat() / n / 255f
        } catch (_: Exception) {
            0.5f
        }
    }

    // ── Foreground notification (camera-active indicator) ─────────
    private fun createChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Gesture control", NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Camera-active indicator for hand gestures" }
            )
        }
    }

    private fun startForegroundWithType() {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun buildNotification(): Notification {
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = when (currentStatus) {
            GestureStatus.CAMERA_BUSY -> "Paused — camera is busy"
            GestureStatus.PAUSED_SCREEN_OFF -> "Paused — screen off"
            GestureStatus.LOW_LIGHT -> "Needs more light to see your hand"
            GestureStatus.NOT_CALIBRATED -> "Calibrate once in Settings → Gestures"
            else -> "Watching for your hand gestures"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("👆 Gesture control active")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(tap)
            .build()
    }

    private fun updateNotification() {
        if (!running) return
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIF_ID, buildNotification())
    }

    private fun notifyOnce(title: String, text: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(4202, NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build())
    }
}

/**
 * Builds the MediaPipe HandLandmarker in LIVE_STREAM mode.
 * GPU delegate first (fast, cool), CPU fallback when the GPU delegate
 * isn't available on the device. Everything is wrapped — a failure here
 * is honest, never a crash.
 *
 * NOTE: in tasks-vision 0.10.14 a LIVE_STREAM landmarker REQUIRES its
 * result/error listeners at creation time (createFromOptions throws
 * otherwise), so they are parameters here — the service passes its own.
 */
internal object HandLandmarkerHelper {

    fun createHandLandmarker(
        context: Context,
        modelFile: File,
        resultListener: (HandLandmarkerResult, MPImage) -> Unit,
        errorListener: (RuntimeException) -> Unit,
    ): HandLandmarker {
        val mapped = FileInputStream(modelFile).channel.use { ch ->
            ch.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length())
        }
        val baseOptions = try {
            BaseOptions.builder()
                .setModelAssetBuffer(mapped)
                .setDelegate(Delegate.GPU)
                .build()
        } catch (e: Exception) {
            android.util.Log.w("GestureControl", "GPU delegate unavailable, CPU", e)
            BaseOptions.builder()
                .setModelAssetBuffer(mapped)
                .setDelegate(Delegate.CPU)
                .build()
        }
        val options = HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result: HandLandmarkerResult, image: MPImage -> resultListener(result, image) }
            .setErrorListener { e: RuntimeException -> errorListener(e) }
            .build()
        return HandLandmarker.createFromOptions(context, options)
    }
}

/**
 * Pure classifier: 21 landmarks + Kamran's calibration profile → gesture.
 *
 * Compares against HIS calibrated hand, not generic thresholds:
 * - finger EXTENDED if tip→wrist ≥ 82% of his calibrated extended distance
 * - finger CURLED   if tip→wrist ≤ 118% of his calibrated curled distance
 * - between the two = uncertain → null (no vote; anti-flicker needs certainty)
 *
 * Static poses only — swipes are a motion signature handled in the service.
 */
internal object GestureClassifier {

    fun classify(
        landmarks: List<NormalizedLandmark>?,
        profile: CalibrationData?,
    ): GestureKind? {
        if (landmarks == null || landmarks.size < 21 || profile == null) return null
        val tips = HandMath.tipDistances(landmarks)
        val states = tips.mapIndexed { i, d ->
            when {
                d >= profile.extendedTipDist[i] * 0.82f -> FingerState.EXTENDED
                d <= profile.curledTipDist[i] * 1.18f -> FingerState.CURLED
                else -> FingerState.UNCERTAIN
            }
        }
        if (states.any { it == FingerState.UNCERTAIN }) return null

        val thumbTipD = HandMath.dist(
            landmarks[HandMath.THUMB_TIP], landmarks[HandMath.WRIST])
        val thumbExtended = thumbTipD >= profile.thumbExtendedDist * 0.85f
        val allExtended = states.all { it == FingerState.EXTENDED }
        val allCurled = states.all { it == FingerState.CURLED }

        // Thumb up/down: thumb out, others curled, thumb roughly vertical.
        if (thumbExtended && allCurled) {
            val tip = landmarks[HandMath.THUMB_TIP]
            val ip = landmarks[HandMath.THUMB_IP]
            val dy = tip.y() - ip.y() // image coords: y grows downward
            val dx = tip.x() - ip.x()
            if (kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.2f) {
                return when {
                    dy < -0.015f -> GestureKind.THUMB_UP
                    dy > 0.015f -> GestureKind.THUMB_DOWN
                    else -> null
                }
            }
            return null
        }
        if (allExtended && thumbExtended) return GestureKind.PALM_HOLD
        if (allCurled && !thumbExtended) return GestureKind.FIST
        return null
    }

    private enum class FingerState { EXTENDED, CURLED, UNCERTAIN }
}
