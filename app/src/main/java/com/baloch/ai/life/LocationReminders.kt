package com.baloch.ai.life

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.baloch.ai.brain.BalochLanguage
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

/**
 * Location reminders — "jab market jaun to yaad dilana: doodh lena".
 *
 * Geofences (100 m radius) are registered with the GeofencingClient and
 * re-registered on BOOT_COMPLETED by [BootReceiver]. Reminders survive
 * in an atomic JSON store.
 *
 * DEPENDENCY NOTE: this file needs `com.google.android.gms:play-services-location`
 * in app/build.gradle (the parent adds it alongside the ML Kit dep —
 * see LIFE-DOCS-ADD.md).
 *
 * Voice: "jab market jaun to yaad dilana: doodh lena",
 * "yaad dilana jab ghar jaun: ...".
 */

data class GeoReminder(
    val id: String,
    val label: String,
    val message: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 100f,
    /** Epoch millis; 0 = one-shot already fired values are deleted instead. */
    val createdAt: Long = System.currentTimeMillis(),
)

class GeoReminderStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun add(reminder: GeoReminder) {
        val all = all().toMutableList()
        all.removeAll { it.id == reminder.id }
        all.add(reminder)
        save(all)
    }

    @Synchronized
    fun all(): List<GeoReminder> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            List(arr.length()) { i -> fromJson(arr.getJSONObject(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun remove(id: String) {
        save(all().filter { it.id != id })
    }

    private fun save(reminders: List<GeoReminder>) {
        val arr = JSONArray()
        reminders.forEach { arr.put(toJson(it)) }
        val tmp = File(file.parent, "$FILE_NAME.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) file.writeText(arr.toString())
    }

    private fun toJson(r: GeoReminder): JSONObject = JSONObject()
        .put("id", r.id)
        .put("label", r.label)
        .put("message", r.message)
        .put("lat", r.latitude)
        .put("lon", r.longitude)
        .put("radius", r.radiusMeters.toDouble())
        .put("createdAt", r.createdAt)

    private fun fromJson(o: JSONObject): GeoReminder = GeoReminder(
        id = o.optString("id", java.util.UUID.randomUUID().toString()),
        label = o.optString("label", ""),
        message = o.optString("message", ""),
        latitude = o.optDouble("lat", 0.0),
        longitude = o.optDouble("lon", 0.0),
        radiusMeters = o.optDouble("radius", 100.0).toFloat(),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    )

    companion object {
        private const val FILE_NAME = "geo_reminders.json"
    }
}

// ------------------------------------------------------------------
// Geofence plumbing
// ------------------------------------------------------------------

object GeofenceHelper {

    private const val ACTION_GEOFENCE = "com.baloch.ai.life.GEOFENCE_TRANSITION"

    private fun client(context: Context): GeofencingClient =
        LocationServices.getGeofencingClient(context)

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceTransitionReceiver::class.java).apply {
            action = ACTION_GEOFENCE
        }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    /**
     * Registers [reminder] with the system. Suspends until the Tasks API
     * completes — no extra coroutines-play-services dependency needed.
     * Returns null on success, or the failure message.
     */
    suspend fun addGeofence(context: Context, reminder: GeoReminder): String? {
        val geofence = Geofence.Builder()
            .setRequestId(reminder.id)
            .setCircularRegion(
                reminder.latitude, reminder.longitude, reminder.radiusMeters
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL
            )
            .setLoiteringDelay(60_000)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()
        return try {
            awaitTask {
                @Suppress("MissingPermission")
                client(context).addGeofences(request, pendingIntent(context))
            }
            GeoReminderStore(context).add(reminder)
            null
        } catch (e: Exception) {
            e.message ?: "geofence registration failed"
        }
    }

    suspend fun removeGeofence(context: Context, id: String): String? {
        return try {
            awaitTask { client(context).removeGeofences(listOf(id)) }
            GeoReminderStore(context).remove(id)
            null
        } catch (e: Exception) {
            e.message ?: "geofence removal failed"
        }
    }

    /** Re-registers every stored reminder — called on BOOT_COMPLETED. */
    suspend fun reregisterAll(context: Context) {
        GeoReminderStore(context).all().forEach { reminder ->
            try {
                addGeofence(context, reminder)
            } catch (_: Exception) {
                // One bad geofence must not block the rest.
            }
        }
    }

    private suspend fun awaitTask(
        block: () -> com.google.android.gms.tasks.Task<*>,
    ) = suspendCancellableCoroutine<Unit> { cont ->
        try {
            block()
                .addOnSuccessListener { if (!cont.isCompleted) cont.resume(Unit) }
                .addOnFailureListener { e ->
                    if (!cont.isCompleted) cont.cancel(e as? Exception ?: RuntimeException(e))
                }
        } catch (e: Exception) {
            if (!cont.isCompleted) cont.cancel(e)
        }
    }
}

/**
 * Fires when a geofence is entered: shows the reminder notification and
 * deletes the one-shot reminder so it doesn't nag twice.
 * Declared in manifest-snippets-life.txt.
 */
class GeofenceTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = try {
            com.google.android.gms.location.GeofenceEvent.fromIntent(intent)
        } catch (_: Exception) {
            null
        } ?: return
        if (event.hasError()) return
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER &&
            event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_DWELL
        ) return

        val lang = activeLanguage(context)
        val store = GeoReminderStore(context)
        event.triggeringGeofences?.forEach { geofence ->
            val reminder = store.all().firstOrNull { it.id == geofence.requestId }
                ?: return@forEach
            val title = when (lang) {
                BalochLanguage.ENGLISH -> "You're at ${reminder.label}, sir 📍"
                BalochLanguage.URDU -> "سر، آپ ${reminder.label} پہنچ گئے ہیں 📍"
                BalochLanguage.HINDI -> "सर, आप ${reminder.label} पहुँच गए हैं 📍"
            }
            LifeNotify.send(
                context, LifeNotify.CH_GEO, "Location reminders",
                5000 + reminder.id.hashCode() % 1000, title, reminder.message,
            )
            store.remove(reminder.id) // one-shot: nag once, then it's done
        }
    }
}

/**
 * Re-registers geofences after a reboot (alarms/geofences don't survive
 * restarts). Declared in manifest-snippets-life.txt with BOOT_COMPLETED.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        // Work off the main thread; keep the receiver itself instant.
        val pending = goAsync()
        Thread {
            try {
                kotlinx.coroutines.runBlocking {
                    GeofenceHelper.reregisterAll(context.applicationContext)
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}

// ------------------------------------------------------------------
// Voice parsing — "jab market jaun to yaad dilana: doodh lena"
// ------------------------------------------------------------------

data class ParsedGeoReminder(val label: String, val message: String)

/**
 * Parses location-reminder voice input. The label is best-effort (taken
 * from the "jab X jaun" clause); the message is everything after
 * "yaad dilana". Returns null when the trigger phrase is absent.
 */
fun parseGeoReminderVoice(rawInput: String): ParsedGeoReminder? {
    val lower = rawInput.lowercase()
    val triggers = listOf("yaad dilana", "yaad dilaana", "یاد دلانا", "याद दिलाना", "remind me when")
    if (triggers.none { lower.contains(it) }) return null

    val trigger = triggers.first { lower.contains(it) }
    val after = rawInput.substring(lower.indexOf(trigger) + trigger.length)
        .trimStart(':', ' ', '—', '-', '۔')

    // Label from "jab <label> jaun/pahunchen/jayein" — best effort.
    val labelMatch = Regex(
        """jab\s+(.+?)\s+(jaun|jaaun|jaon|jao|pahunchun|pahunchen|jayein|جاؤں|پہنچوں)""",
        RegexOption.IGNORE_CASE,
    ).find(rawInput)
    val label = labelMatch?.groupValues?.getOrNull(1)?.trim().takeIf { !it.isNullOrBlank() }
        ?: "that place"

    return ParsedGeoReminder(
        label = label,
        message = after.ifBlank { "You asked to be reminded here, sir." },
    )
}

/** Where to get coordinates: the reminder needs a pinned location. */
val GEO_NEEDS_LOCATION = com.baloch.ai.brain.L3(
    en = "Got it, sir 📍 — \"{message}\". Now tap the location on the map (or say the place name) so I can pin the geofence for \"{label}\".",
    ur = "سمجھ گیا، سر 📍 — \"{message}\"۔ اب نقشے پر جگہ تھپتھپائیں (یا جگہ کا نام کہیں) تاکہ میں \"{label}\" کے لیے جیوفینس لگا سکوں۔",
    hi = "समझ गया, सर 📍 — \"{message}\"। अब नक्शे पर जगह टैप करें (या जगह का नाम कहें) ताकि मैं \"{label}\" के लिए जियोफेंस लगा सकूँ।",
)
