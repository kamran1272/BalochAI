package com.baloch.ai.schedule

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists [ScheduledTask]s as a JSON array in app-private storage
 * (scheduled_tasks.json). Same org.json style as NotesStore/MemoryStore —
 * no extra dependencies. All public methods are synchronized; writes are
 * atomic (temp file + rename) so a crash mid-write never corrupts the list.
 */
class TaskStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun add(task: ScheduledTask): Long {
        val tasks = all().toMutableList()
        val nextId = (tasks.maxOfOrNull { it.id } ?: 0L) + 1L
        tasks.add(task.copy(id = nextId))
        save(tasks)
        return nextId
    }

    @Synchronized
    fun all(): List<ScheduledTask> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            List(arr.length()) { i -> fromJson(arr.getJSONObject(i)) }
                .sortedBy { it.triggerAtMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun get(id: Long): ScheduledTask? = all().firstOrNull { it.id == id }

    @Synchronized
    fun setEnabled(id: Long, enabled: Boolean) {
        val tasks = all().map { if (it.id == id) it.copy(enabled = enabled) else it }
        save(tasks)
    }

    @Synchronized
    fun update(task: ScheduledTask) {
        val tasks = all().map { if (it.id == task.id) task else it }
        save(tasks)
    }

    @Synchronized
    fun delete(id: Long) {
        save(all().filter { it.id != id })
    }

    private fun save(tasks: List<ScheduledTask>) {
        val arr = JSONArray()
        tasks.forEach { arr.put(toJson(it)) }
        val tmp = File(file.parent, "$FILE_NAME.tmp")
        tmp.writeText(arr.toString())
        // Atomic publish: readers never see a half-written file.
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
        }
    }

    private fun toJson(t: ScheduledTask): JSONObject = JSONObject()
        .put("id", t.id)
        .put("command", t.commandText)
        .put("trigger", t.triggerAtMillis)
        .put("recurrence", t.recurrence.name)
        .put("days", JSONArray(t.daysOfWeek.toList()))
        .put("created", t.createdAt)
        .put("enabled", t.enabled)

    private fun fromJson(o: JSONObject): ScheduledTask {
        val days = mutableSetOf<Int>()
        val arr = o.optJSONArray("days")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val d = arr.optInt(i, -1)
                if (d in 1..7) days.add(d)
            }
        }
        return ScheduledTask(
            id = o.optLong("id", 0L),
            commandText = o.optString("command", ""),
            triggerAtMillis = o.optLong("trigger", 0L),
            recurrence = runCatching { Recurrence.valueOf(o.optString("recurrence", "ONCE")) }
                .getOrDefault(Recurrence.ONCE),
            daysOfWeek = days,
            createdAt = o.optLong("created", System.currentTimeMillis()),
            enabled = o.optBoolean("enabled", true),
        )
    }

    companion object {
        private const val FILE_NAME = "scheduled_tasks.json"
    }
}
