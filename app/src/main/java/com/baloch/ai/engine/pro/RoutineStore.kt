package com.baloch.ai.engine.pro

import android.content.Context
import com.baloch.ai.brain.DeviceAction
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Routines/macros: "when I say {trigger}, {commands}".
 *
 * Stored as JSON in app-private storage (org.json, no extra deps).
 * [DeviceAction]s are encoded manually as {t, ...fields} — only the
 * safe action subset below is storable; risky ones are filtered out by
 * the brain before saving.
 *
 * Built-ins are always present:
 *   "good night" / "shab bakhair" → DND on + brightness 20%
 *   "meeting mode"                → DND on + auto-off in 60 min
 *
 * When [context] is null the store keeps routines in memory only
 * (defensive path — in production BalochApp always supplies one).
 */
data class Routine(
    val name: String,
    val triggerPhrase: String,
    val actions: List<DeviceAction>,
)

class RoutineStore(context: Context?) {

    private val file: File? = context?.let { File(it.filesDir, "baloch_routines.json") }
    private val memoryOnly = mutableListOf<Routine>()

    /** Custom routines first (user overrides win), then built-ins. */
    fun all(): List<Routine> = stored() + builtins()

    /** First routine whose trigger phrase appears in [input] (already lowercased). */
    fun matchTrigger(input: String): Routine? =
        all().firstOrNull { r -> r.triggerPhrase.isNotBlank() && input.contains(r.triggerPhrase) }

    fun save(routine: Routine) {
        val f = file
        if (f == null) {
            memoryOnly.removeAll { it.triggerPhrase == routine.triggerPhrase }
            memoryOnly.add(0, routine)
            return
        }
        val all = stored().toMutableList()
        all.removeAll { it.triggerPhrase == routine.triggerPhrase }
        all.add(0, routine)
        persist(all)
    }

    /** Removes a user routine by its trigger phrase (built-ins can't be deleted). */
    fun delete(triggerPhrase: String) {
        if (file == null) {
            memoryOnly.removeAll { it.triggerPhrase == triggerPhrase }
            return
        }
        persist(stored().filterNot { it.triggerPhrase == triggerPhrase })
    }

    // ------------------------------------------------------------------

    private fun builtins(): List<Routine> = listOf(
        Routine(
            name = "good night",
            triggerPhrase = "good night",
            actions = listOf(DeviceAction.SetDnd(true), DeviceAction.SetBrightness(20)),
        ),
        Routine(
            name = "shab bakhair",
            triggerPhrase = "shab bakhair",
            actions = listOf(DeviceAction.SetDnd(true), DeviceAction.SetBrightness(20)),
        ),
        Routine(
            name = "شب بخیر",
            triggerPhrase = "شب بخیر",
            actions = listOf(DeviceAction.SetDnd(true), DeviceAction.SetBrightness(20)),
        ),
        Routine(
            name = "शुभ रात्रि",
            triggerPhrase = "शुभ रात्रि",
            actions = listOf(DeviceAction.SetDnd(true), DeviceAction.SetBrightness(20)),
        ),
        Routine(
            name = "meeting mode",
            triggerPhrase = "meeting mode",
            actions = listOf(DeviceAction.SetDnd(true), DeviceAction.DisableDndDelayed(60)),
        ),
    )

    private fun stored(): List<Routine> {
        val f = file ?: return memoryOnly.toList()
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            List(arr.length()) { i -> decode(arr.getJSONObject(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persist(routines: List<Routine>) {
        val f = file ?: return
        try {
            val arr = JSONArray()
            routines.forEach { arr.put(encode(it)) }
            f.writeText(arr.toString())
        } catch (_: Exception) {
            // best-effort; the in-memory list is already updated
        }
    }

    // ------------------------------------------------------------------
    // Manual DeviceAction codec (safe subset only)
    // ------------------------------------------------------------------

    private fun encode(r: Routine): JSONObject = JSONObject()
        .put("name", r.name)
        .put("trigger", r.triggerPhrase)
        .put("actions", JSONArray().apply {
            r.actions.forEach { a -> encodeAction(a)?.let { put(it) } }
        })

    private fun decode(o: JSONObject): Routine = Routine(
        name = o.optString("name"),
        triggerPhrase = o.optString("trigger"),
        actions = buildList {
            val arr = o.optJSONArray("actions") ?: JSONArray()
            for (i in 0 until arr.length()) {
                decodeAction(arr.optJSONObject(i) ?: continue)?.let { add(it) }
            }
        },
    )

    private fun encodeAction(a: DeviceAction): JSONObject? = when (a) {
        is DeviceAction.OpenApp -> obj("OpenApp", "appName" to a.appName)
        is DeviceAction.SetAlarm -> obj("SetAlarm", "hour" to a.hour, "minute" to a.minute, "label" to a.label)
        is DeviceAction.StartTimer -> obj("StartTimer", "seconds" to a.seconds, "label" to a.label)
        is DeviceAction.AddReminder -> obj("AddReminder", "text" to a.text, "minutesFromNow" to a.minutesFromNow)
        is DeviceAction.ToggleWifi -> obj("ToggleWifi", "on" to a.on)
        is DeviceAction.ToggleBluetooth -> obj("ToggleBluetooth", "on" to a.on)
        is DeviceAction.ToggleFlashlight -> obj("ToggleFlashlight", "on" to a.on)
        is DeviceAction.SetVolume -> obj("SetVolume", "levelPercent" to a.levelPercent)
        is DeviceAction.AdjustVolume -> obj("AdjustVolume", "up" to a.up)
        is DeviceAction.SetMuted -> obj("SetMuted", "muted" to a.muted)
        is DeviceAction.SetBrightness -> obj("SetBrightness", "levelPercent" to a.levelPercent)
        is DeviceAction.WebSearch -> obj("WebSearch", "query" to a.query)
        is DeviceAction.AddNote -> obj("AddNote", "text" to a.text)
        is DeviceAction.RememberFact -> obj("RememberFact", "fact" to a.fact)
        is DeviceAction.SetDnd -> obj("SetDnd", "on" to a.on)
        is DeviceAction.DisableDndDelayed -> obj("DisableDndDelayed", "minutes" to a.minutes)
        is DeviceAction.WhatsAppDraft -> obj("WhatsAppDraft", "name" to a.name, "number" to a.number, "text" to a.text)
        else -> null // risky / informational actions are never stored in routines
    }

    private fun decodeAction(o: JSONObject): DeviceAction? = when (o.optString("t")) {
        "OpenApp" -> DeviceAction.OpenApp(o.optString("appName"))
        "SetAlarm" -> DeviceAction.SetAlarm(o.optInt("hour"), o.optInt("minute"), o.optString("label"))
        "StartTimer" -> DeviceAction.StartTimer(o.optInt("seconds"), o.optString("label"))
        "AddReminder" -> DeviceAction.AddReminder(o.optString("text"), o.optInt("minutesFromNow"))
        "ToggleWifi" -> DeviceAction.ToggleWifi(o.optBoolean("on"))
        "ToggleBluetooth" -> DeviceAction.ToggleBluetooth(o.optBoolean("on"))
        "ToggleFlashlight" -> DeviceAction.ToggleFlashlight(o.optBoolean("on"))
        "SetVolume" -> DeviceAction.SetVolume(o.optInt("levelPercent"))
        "AdjustVolume" -> DeviceAction.AdjustVolume(o.optBoolean("up"))
        "SetMuted" -> DeviceAction.SetMuted(o.optBoolean("muted"))
        "SetBrightness" -> DeviceAction.SetBrightness(o.optInt("levelPercent"))
        "WebSearch" -> DeviceAction.WebSearch(o.optString("query"))
        "AddNote" -> DeviceAction.AddNote(o.optString("text"))
        "RememberFact" -> DeviceAction.RememberFact(o.optString("fact"))
        "SetDnd" -> DeviceAction.SetDnd(o.optBoolean("on"))
        "DisableDndDelayed" -> DeviceAction.DisableDndDelayed(o.optInt("minutes", 60))
        "WhatsAppDraft" -> DeviceAction.WhatsAppDraft(o.optString("name"), o.optString("number"), o.optString("text"))
        else -> null
    }

    private fun obj(type: String, vararg fields: Pair<String, Any>): JSONObject =
        JSONObject().put("t", type).apply {
            fields.forEach { (k, v) -> put(k, v) }
        }
}
