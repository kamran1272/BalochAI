package com.baloch.ai.gesture

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * The six gestures Baloch recognises, and the command text each one fires.
 *
 * The command text is routed through the SAME path as a typed/voice command
 * (the parent feeds it to the Orchestrator), so every existing gate still
 * applies — voice lock, risk tiers, confirmations. A gesture is just another
 * way to say the words.
 */
enum class GestureKind {
    /** Open palm held still ~1s — "talk to me now". */
    PALM_HOLD,

    /** Palm flicked to the user's right. */
    SWIPE_RIGHT,

    /** Palm flicked to the user's left. */
    SWIPE_LEFT,

    /** Closed fist. */
    FIST,

    /** Thumb up — confirms a pending risky action. */
    THUMB_UP,

    /** Thumb down — cancels a pending risky action. */
    THUMB_DOWN,
}

/** One gesture → one command-text mapping (user-remappable). */
data class GestureBinding(val gesture: GestureKind, val commandText: String)

/**
 * Persists the gesture→command map as JSON in filesDir (user-remappable).
 *
 * DEFAULT_MAP (Kamran's defaults):
 * - PALM_HOLD   → "wake baloch"     (starts voice input)
 * - SWIPE_RIGHT → "next track"      (media key)
 * - SWIPE_LEFT  → "previous track"  (media key)
 * - FIST        → "pause media"
 * - THUMB_UP    → "yes"             (confirm pending)
 * - THUMB_DOWN  → "no"              (cancel)
 *
 * All reads/writes are synchronized; the file is written atomically
 * (temp + rename) so a crash mid-write never corrupts the map.
 */
class GestureMap(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Any()

    companion object {
        const val FILE_NAME = "gesture_map.json"

        val DEFAULT_MAP: Map<GestureKind, String> = mapOf(
            GestureKind.PALM_HOLD to "wake baloch",
            GestureKind.SWIPE_RIGHT to "next track",
            GestureKind.SWIPE_LEFT to "previous track",
            GestureKind.FIST to "pause media",
            GestureKind.THUMB_UP to "yes",
            GestureKind.THUMB_DOWN to "no",
        )
    }

    /** Current map; unknown/missing entries fall back to [DEFAULT_MAP]. */
    fun getMap(): Map<GestureKind, String> = synchronized(lock) {
        if (!file.exists()) return DEFAULT_MAP
        return try {
            val json = JSONObject(file.readText())
            val out = DEFAULT_MAP.toMutableMap()
            for (kind in GestureKind.entries) {
                val v = json.optString(kind.name, "").trim()
                if (v.isNotEmpty()) out[kind] = v
            }
            out
        } catch (_: Exception) {
            // Corrupt file → defaults, never a crash. Parent can offer reset.
            DEFAULT_MAP
        }
    }

    /** Remap one gesture to new command text. Blank text is rejected (no-op). */
    fun remap(kind: GestureKind, commandText: String) = synchronized(lock) {
        val clean = commandText.trim()
        if (clean.isEmpty()) return
        val current = getMap().toMutableMap()
        current[kind] = clean
        write(current)
    }

    /** Restore Kamran's defaults. */
    fun resetDefaults() = synchronized(lock) {
        write(DEFAULT_MAP)
    }

    /** Command text for one gesture (default when unmapped). */
    fun commandFor(kind: GestureKind): String =
        getMap()[kind] ?: DEFAULT_MAP.getValue(kind)

    private fun write(map: Map<GestureKind, String>) {
        val json = JSONObject()
        for ((kind, cmd) in map) json.put(kind.name, cmd)
        val tmp = File(file.parent, "$FILE_NAME.tmp")
        tmp.writeText(json.toString())
        // Atomic replace — a crash here leaves the old file intact.
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
