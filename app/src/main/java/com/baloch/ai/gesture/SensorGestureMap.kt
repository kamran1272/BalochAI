package com.baloch.ai.gesture

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * The sensor gestures Baloch recognises — touchless, dark-friendly, and
 * ~10x cheaper on battery than camera gestures.
 */
enum class SensorGestureKind {
    /** Proximity wave: near→far→near within 1.5s. */
    WAVE,

    /** Phone flipped face-down. */
    FLIP,

    /** Two sharp shakes within 2s. */
    SHAKE,

    /** Picked up while locked (significant motion). Default OFF. */
    PICK_UP,

    /**
     * Context override, not a physical gesture: what a WAVE means while
     * music is playing. Kept as a separate remappable key so Kamran can
     * change it without touching the base wave mapping.
     */
    WAVE_MEDIA,
}

/**
 * Persists the sensor-gesture → command map as JSON in filesDir.
 *
 * DEFAULTS:
 * - WAVE       → "wake baloch"      (voice input)
 * - WAVE_MEDIA → "toggle media"     (wave while music plays)
 * - FLIP       → "silence ringing"  (flip face-down)
 * - SHAKE      → "toggle flashlight"
 *
 * PICK_UP is intentionally NOT command-mapped: it is a readiness signal
 * (parent chimes "Ji, sir?"), so there is nothing to remap.
 *
 * Ringing override: a wave while the parent reports "ringing" always means
 * "silence ringing" (safety behaviour, not remappable — silencing a ringing
 * phone must never surprise him).
 */
class SensorGestureMap(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Any()

    companion object {
        const val FILE_NAME = "sensor_gesture_map.json"

        const val RINGING_COMMAND = "silence ringing"

        val DEFAULT_MAP: Map<SensorGestureKind, String> = mapOf(
            SensorGestureKind.WAVE to "wake baloch",
            SensorGestureKind.WAVE_MEDIA to "toggle media",
            SensorGestureKind.FLIP to "silence ringing",
            SensorGestureKind.SHAKE to "toggle flashlight",
        )

        /** Gestures shown in the remap UI (PICK_UP is a readiness signal). */
        val REMAPPABLE = listOf(
            SensorGestureKind.WAVE,
            SensorGestureKind.WAVE_MEDIA,
            SensorGestureKind.FLIP,
            SensorGestureKind.SHAKE,
        )
    }

    fun getMap(): Map<SensorGestureKind, String> = synchronized(lock) {
        if (!file.exists()) return DEFAULT_MAP
        return try {
            val json = JSONObject(file.readText())
            val out = DEFAULT_MAP.toMutableMap()
            for (kind in SensorGestureKind.entries) {
                val v = json.optString(kind.name, "").trim()
                if (v.isNotEmpty()) out[kind] = v
            }
            out
        } catch (_: Exception) {
            DEFAULT_MAP
        }
    }

    fun remap(kind: SensorGestureKind, commandText: String) = synchronized(lock) {
        val clean = commandText.trim()
        if (clean.isEmpty()) return
        val current = getMap().toMutableMap()
        current[kind] = clean
        val json = JSONObject()
        for ((k, cmd) in current) json.put(k.name, cmd)
        val tmp = File(file.parent, "$FILE_NAME.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    fun resetDefaults() = synchronized(lock) {
        if (file.exists()) file.delete()
    }

    fun commandFor(kind: SensorGestureKind): String =
        getMap()[kind] ?: DEFAULT_MAP.getValue(kind)
}
