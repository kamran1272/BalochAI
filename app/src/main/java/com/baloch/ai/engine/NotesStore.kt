package com.baloch.ai.engine

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * Baloch's notebook. Plain JSON file in app-private storage —
 * no database, no dependencies, survives app restarts.
 */
class NotesStore(context: Context) {

    private val file = File(context.filesDir, "baloch_notes.json")

    @Synchronized
    fun add(text: String) {
        val all = all().toMutableList()
        all.add(0, text.trim())
        save(all.take(200))
    }

    @Synchronized
    fun all(): List<String> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            List(arr.length()) { i -> arr.getString(i) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(items: List<String>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        file.writeText(arr.toString())
    }
}
