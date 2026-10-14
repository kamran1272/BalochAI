package com.baloch.ai.gesture

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.baloch.ai.notify.BalochNotifications
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Sensor gestures — touchless control WITHOUT the camera.
 *
 * Kamran's catch: "sensor sy bhi gestures — zaroori nai always camera."
 * The proximity sensor works in COMPLETE DARKNESS (it's IR) and with the
 * screen off, which is exactly where camera gestures can't go.
 *
 * Gestures (all debounced, all remappable via [SensorGestureMap]):
 * - WAVE: proximity near→far→near within 1.5s. Context-aware:
 *     ringing (parent-flagged) → "silence ringing" (fixed, safety);
 *     music playing            → WAVE_MEDIA map ("toggle media");
 *     otherwise                → WAVE map ("wake baloch").
 * - FLIP: face-down (accel z flips +→−, held 500ms) → "silence ringing"
 *     + optional "dnd on" (configurable); flip back up → "dnd off".
 * - SHAKE: 2 sharp shakes within 2s → SHAKE map ("toggle flashlight").
 *     3s cooldown.
 * - PICK_UP (default OFF): significant-motion trigger while locked →
 *     [onReadyForCommand]; the parent chimes "Ji, sir?".
 *
 * SENSOR FUSION ANTI-MISFIRE: proximity "near" (phone in pocket / against
 * ear) suppresses shake + flip. A phone in a pocket cannot misfire.
 *
 * SCREEN-OFF BEHAVIOUR — DELIBERATE, unlike camera gestures: this service
 * does NOT pause when the screen turns off. The proximity wave with a dark
 * screen is the killer feature (silence the alarm without looking).
 * Accelerometer gestures need the device awake; on most phones they simply
 * don't fire with the screen off — documented, not a bug.
 *
 * PERMISSIONS: none. Only normal sensor APIs (proximity, accelerometer,
 * significant-motion) + AudioManager.isMusicActive(). No new manifest
 * permissions — see the PERMISSIONS note in GESTURE-SETTINGS-DOCS-ADD.md.
 *
 * BATTERY: SENSOR_DELAY_NORMAL throughout; the proximity sensor sips power.
 * Roughly an order of magnitude cheaper than the camera pipeline.
 */
class SensorGestureService : android.app.Service() {

    companion object {
        private const val TAG = "SensorGesture"
        private const val PREFS = "baloch_sensor_gesture"

        const val KEY_ENABLED = "sensor_gestures_enabled"
        const val KEY_WAVE = "sensor_wave"
        const val KEY_FLIP = "sensor_flip"
        const val KEY_SHAKE = "sensor_shake"
        const val KEY_PICK_UP = "sensor_pick_up"
        const val KEY_FLIP_DND = "sensor_flip_dnd"

        const val ACTION_SENSOR_GESTURE = "com.baloch.ai.sensor.GESTURE_DETECTED"
        const val EXTRA_KIND = "kind"
        const val EXTRA_COMMAND = "command"
        const val ACTION_SET_RINGING = "com.baloch.ai.sensor.SET_RINGING"
        const val EXTRA_RINGING = "ringing"

        private const val NOTIF_ID = 4105

        // Wave: near→far→near within this window.
        private const val WAVE_WINDOW_MS = 1500L
        private const val WAVE_COOLDOWN_MS = 2000L
        // Shake: |Δ|magnitude| peaks above this (m/s²) count; 2 within 2s.
        private const val SHAKE_THRESHOLD = 12f
        private const val SHAKE_WINDOW_MS = 2000L
        private const val SHAKE_COOLDOWN_MS = 3000L
        // Flip: |z| beyond this, held this long.
        private const val FLIP_Z = 7.0f
        private const val FLIP_HOLD_MS = 500L

        /** Shown in Settings → Gestures (sensor tab). */
        const val BATTERY_NOTE =
            "Sensor gestures sip battery (proximity + accelerometer at low rate); " +
                "camera gestures drink it. 🥤 Proximity wave even works in the dark, screen off."

        fun isEnabled(context: Context): Boolean =
            prefs(context).getBoolean(KEY_ENABLED, false)

        fun setEnabled(context: Context, enabled: Boolean) {
            prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
            if (enabled) {
                ContextCompat.startForegroundService(
                    context, Intent(context, SensorGestureService::class.java)
                )
            } else {
                context.stopService(Intent(context, SensorGestureService::class.java))
            }
        }

        fun isGestureOn(context: Context, key: String, default: Boolean): Boolean =
            prefs(context).getBoolean(key, default)

        fun setGestureOn(context: Context, key: String, on: Boolean) {
            prefs(context).edit().putBoolean(key, on).apply()
        }

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /**
     * Command text for a recognised sensor gesture — same shape as
     * [GestureControlService.onCommand]. The parent routes it through the
     * Orchestrator like any typed/voice command.
     */
    var onCommand: ((String) -> Unit)? = null

    /**
     * Pick-up-to-wake fired: the phone was significantly moved while locked.
     * Parent contract: chime "Ji, sir?" and start listening — do NOT execute
     * anything yet.
     */
    var onReadyForCommand: (() -> Unit)? = null

    inner class SensorBinder : Binder() {
        fun service(): SensorGestureService = this@SensorGestureService
        /** Parent calls this when an alarm rings / a call comes in / it stops. */
        fun setRinging(ringing: Boolean) {
            ringingNow = ringing
        }
    }

    private val binder = SensorBinder()

    private lateinit var sensorManager: SensorManager
    private lateinit var sensorMap: SensorGestureMap
    private lateinit var audioManager: AudioManager

    @Volatile private var ringingNow = false
    @Volatile private var proximityNear = false

    // Wave state machine: FAR --near--> NEAR --far--> FAR --near--> WAVE.
    private var waveStage = 0 // 0 idle/far, 1 near-seen, 2 far-again-seen
    private var waveT0 = 0L
    private var lastWaveMs = 0L

    // Shake: low-passed accel → magnitude deltas → peak counting.
    private val smooth = FloatArray(3)
    private var smoothInit = false
    private var lastMag = 0f
    private var shakePeaks = 0
    private var shakeWindowStart = 0L
    private var lastShakeMs = 0L

    // Flip: face-down / face-up with hold time.
    private var faceDown = false
    private var flipCandidateSince = 0L
    private var dndTurnedOn = false

    private var accelRegistered = false
    private var proxRegistered = false
    private var sigMotionRegistered = false

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_PROXIMITY -> onProximity(event.values[0], event.sensor.maximumRange)
                Sensor.TYPE_ACCELEROMETER -> onAccelerometer(event.values)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val sigMotionListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            // Re-arm: trigger sensors fire once.
            registerSigMotion()
            if (!isGestureOn(this@SensorGestureService, KEY_PICK_UP, false)) return
            Log.i(TAG, "pick-up detected → ready for command")
            // Readiness signal ONLY — the parent chimes "Ji, sir?" and starts
            // listening. Nothing executes; pick-up is not command-mapped.
            try { onReadyForCommand?.invoke() } catch (e: Exception) { Log.w(TAG, "pickup cb", e) }
            sendBroadcast(Intent(ACTION_SENSOR_GESTURE).apply {
                setPackage(packageName)
                putExtra(EXTRA_KIND, SensorGestureKind.PICK_UP.name)
                putExtra(EXTRA_COMMAND, "")
            })
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorMap = SensorGestureMap(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        startForeground(NOTIF_ID, buildNotification())
        reregister()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SET_RINGING) {
            ringingNow = intent.getBooleanExtra(EXTRA_RINGING, false)
        }
        if (!isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        reregister()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        unregisterAll()
        super.onDestroy()
    }

    /** (Re)register listeners for the gestures Kamran has switched on. */
    private fun reregister() {
        unregisterAll()
        if (!isEnabled(this)) return

        val prox = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        // Proximity is registered when ANY of wave/shake/flip is on: the wave
        // needs it as its sensor, and shake/flip need it for pocket
        // suppression (sensor fusion anti-misfire).
        val needProx = isGestureOn(this, KEY_WAVE, true) ||
            isGestureOn(this, KEY_SHAKE, true) ||
            isGestureOn(this, KEY_FLIP, true)
        if (prox != null && needProx) {
            proxRegistered = sensorManager.registerListener(
                sensorListener, prox, SensorManager.SENSOR_DELAY_NORMAL)
        }

        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val needAccel = isGestureOn(this, KEY_FLIP, true) ||
            isGestureOn(this, KEY_SHAKE, true)
        if (accel != null && needAccel) {
            accelRegistered = sensorManager.registerListener(
                sensorListener, accel, SensorManager.SENSOR_DELAY_NORMAL)
        }

        if (isGestureOn(this, KEY_PICK_UP, false)) registerSigMotion()
    }

    private fun registerSigMotion() {
        val sm = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
        if (sm != null) {
            // Trigger sensors fire once and auto-unregister; re-request each time.
            sigMotionRegistered = sensorManager.requestTriggerSensor(sigMotionListener, sm)
        }
    }

    private fun unregisterAll() {
        try { sensorManager.unregisterListener(sensorListener) } catch (_: Exception) { }
        try {
            val sm = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
            if (sm != null) sensorManager.cancelTriggerSensor(sigMotionListener, sm)
        } catch (_: Exception) { }
        accelRegistered = false
        proxRegistered = false
        sigMotionRegistered = false
    }

    // ── Proximity wave ────────────────────────────────────────────
    private fun onProximity(value: Float, maxRange: Float) {
        val near = value < maxRange
        proximityNear = near
        if (!isGestureOn(this, KEY_WAVE, true)) return
        val now = SystemClock.elapsedRealtime()

        when (waveStage) {
            0 -> if (near) { waveStage = 1; waveT0 = now }
            1 -> {
                if (!near) waveStage = 2 // far again
                else if (now - waveT0 > WAVE_WINDOW_MS) { waveStage = 0 }
            }
            2 -> {
                if (near) {
                    if (now - waveT0 <= WAVE_WINDOW_MS &&
                        now - lastWaveMs > WAVE_COOLDOWN_MS
                    ) {
                        lastWaveMs = now
                        onWave()
                    }
                    waveStage = 0
                } else if (now - waveT0 > WAVE_WINDOW_MS) {
                    waveStage = 0
                }
            }
        }
    }

    private fun onWave() {
        // Context-aware, in priority order:
        // 1. Ringing → ALWAYS silence (safety: never remappable).
        // 2. Music playing → WAVE_MEDIA mapping.
        // 3. Otherwise → WAVE mapping.
        val command = when {
            ringingNow -> SensorGestureMap.RINGING_COMMAND
            isMusicActive() -> sensorMap.commandFor(SensorGestureKind.WAVE_MEDIA)
            else -> sensorMap.commandFor(SensorGestureKind.WAVE)
        }
        Log.i(TAG, "wave → \"$command\" (ringing=$ringingNow)")
        fire(SensorGestureKind.WAVE, command)
    }

    private fun isMusicActive(): Boolean =
        try { audioManager.isMusicActive } catch (_: Exception) { false }

    // ── Accelerometer: flip + shake ───────────────────────────────
    private fun onAccelerometer(v: FloatArray) {
        val now = SystemClock.elapsedRealtime()

        // --- Low-pass per axis to kill sensor noise (alpha 0.8). ---
        if (!smoothInit) {
            v.copyInto(smooth)
            smoothInit = true
            lastMag = magnitude(smooth)
            return
        }
        for (i in 0..2) smooth[i] = 0.8f * smooth[i] + 0.2f * v[i]

        // --- FLIP: face-down = z strongly negative, held 500ms. ---
        if (isGestureOn(this, KEY_FLIP, true) && !proximityNear) {
            val z = smooth[2]
            if (!faceDown && z < -FLIP_Z) {
                if (flipCandidateSince == 0L) flipCandidateSince = now
                if (now - flipCandidateSince >= FLIP_HOLD_MS) {
                    faceDown = true
                    flipCandidateSince = 0L
                    onFlipDown()
                }
            } else if (faceDown && z > FLIP_Z) {
                faceDown = false
                onFlipUp()
            } else if (abs(z) < FLIP_Z) {
                flipCandidateSince = 0L // left the zone — restart the hold
            }
        }

        // --- SHAKE: the math, plainly commented. ---
        // A shake is a sudden REVERSAL of motion. Gravity-inclusive magnitude
        // m = sqrt(x²+y²+z²) sits near 9.8 at rest; during a shake it swings
        // wildly. We watch |Δm| between consecutive (smoothed) samples:
        // a "peak" is |Δm| > 12 m/s² (≈1.2g change — far above walking noise,
        // far below what breaks the phone). Two peaks inside 2s = a shake.
        // Proximity-near (pocket) suppresses: walking must never shake-fire.
        if (isGestureOn(this, KEY_SHAKE, true) && !proximityNear) {
            val mag = magnitude(smooth)
            val delta = abs(mag - lastMag)
            lastMag = mag
            if (delta > SHAKE_THRESHOLD) {
                if (shakePeaks == 0 || now - shakeWindowStart > SHAKE_WINDOW_MS) {
                    shakePeaks = 1
                    shakeWindowStart = now
                } else {
                    shakePeaks++
                }
                if (shakePeaks >= 2 && now - lastShakeMs > SHAKE_COOLDOWN_MS) {
                    lastShakeMs = now
                    shakePeaks = 0
                    Log.i(TAG, "shake detected")
                    fire(SensorGestureKind.SHAKE)
                }
            }
        }
    }

    private fun magnitude(v: FloatArray): Float =
        sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])

    private fun onFlipDown() {
        Log.i(TAG, "flip face-down")
        // Silence first — always.
        fire(SensorGestureKind.FLIP, SensorGestureMap.RINGING_COMMAND)
        // Optional DND: routed as a command so the parent's normal
        // confirmation/policy path handles it (this service touches no
        // notification policy itself).
        if (isGestureOn(this, KEY_FLIP_DND, false)) {
            dndTurnedOn = true
            emitCommand("dnd on")
        }
    }

    private fun onFlipUp() {
        Log.i(TAG, "flip face-up")
        if (dndTurnedOn) {
            dndTurnedOn = false
            emitCommand("dnd off")
        }
    }

    // ── Fire ──────────────────────────────────────────────────────
    private fun fire(kind: SensorGestureKind, command: String = sensorMap.commandFor(kind)) {
        emitCommand(command)
        sendBroadcast(Intent(ACTION_SENSOR_GESTURE).apply {
            setPackage(packageName)
            putExtra(EXTRA_KIND, kind.name)
            putExtra(EXTRA_COMMAND, command)
        })
    }

    private fun emitCommand(command: String) {
        try { onCommand?.invoke(command) } catch (e: Exception) { Log.w(TAG, "onCommand", e) }
    }

    // ── Branded foreground notification ───────────────────────────
    private fun buildNotification(): android.app.Notification {
        return BalochNotifications.serviceNotification(
            this,
            "Sensor gestures active",
            "Wave, flip or shake — Baloch is listening to your sensors",
        )
    }
}
