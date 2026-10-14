package com.baloch.ai.engine.pro

import android.content.ClipboardManager
import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * Clipboard history — the last 20 copied texts.
 *
 * The listener is registered once in [BalochApp.onCreate] so it lives
 * for the whole app-process lifetime. Honest Android 10+ limitation:
 * the system only delivers clipboard changes to the foreground app (or
 * the default IME), so clips copied while Baloch wasn't active are NOT
 * captured — there is no API that lets a background app read them.
 * Everything captured is stored app-privately, never leaves the device.
 */
object ClipboardWatcher {

    private const val MAX = 20
    private const val FILE_NAME = "baloch_clipboard.json"

    private val clips = ArrayDeque<String>()
    private var loadedFor: File? = null

    fun init(context: Context) {
        load(context)
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener {
            val text = try {
                cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
            } catch (_: Exception) {
                null
            }
            if (!text.isNullOrBlank()) push(context, text)
        }
    }

    @Synchronized
    fun push(context: Context, text: String) {
        ensureLoaded(context)
        clips.remove(text)
        clips.addFirst(text)
        while (clips.size > MAX) clips.removeLast()
        save(context)
    }

    /** Most recent first. */
    @Synchronized
    fun recent(context: Context, n: Int = 5): List<String> {
        ensureLoaded(context)
        return clips.take(n.coerceIn(1, MAX))
    }

    /** Case-insensitive substring search, most recent first. */
    @Synchronized
    fun search(context: Context, query: String): List<String> {
        ensureLoaded(context)
        val q = query.lowercase()
        return clips.filter { it.lowercase().contains(q) }.take(5)
    }

    // ------------------------------------------------------------------

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun ensureLoaded(context: Context) {
        val f = file(context)
        if (loadedFor?.absolutePath == f.absolutePath) return
        load(context)
    }

    private fun load(context: Context) {
        val f = file(context)
        clips.clear()
        try {
            if (f.exists()) {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let { clips.addLast(it) }
                }
                while (clips.size > MAX) clips.removeLast()
            }
        } catch (_: Exception) {
            clips.clear()
        }
        loadedFor = f
    }

    private fun save(context: Context) {
        try {
            val arr = JSONArray()
            clips.forEach { arr.put(it) }
            file(context).writeText(arr.toString())
        } catch (_: Exception) {
            // best-effort; the in-memory list is the source of truth
        }
    }
}
