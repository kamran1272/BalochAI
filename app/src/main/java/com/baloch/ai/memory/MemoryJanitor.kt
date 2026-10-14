package com.baloch.ai.memory

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import java.util.concurrent.TimeUnit

/**
 * MemoryJanitor — the cleanup crew for the two-tier memory.
 *
 * What it deletes:
 * 1. TEMPORARY facts whose expiresAt has passed (30 days after creation).
 * 2. TEMPORARY conversations past their expiry — EXCEPT exchanges with
 *    producedPermanentFact = true (those inherit permanent status because
 *    they produced something worth keeping forever).
 *
 * What it NEVER touches: PERMANENT facts/conversations, entities
 * (people/places are auto-important — the user deletes them manually).
 *
 * Two rhythms (Kamran's order: monthly sweep + a light daily check):
 * - "baloch-memory-janitor-monthly": every 30 days.
 * - "baloch-memory-janitor-daily": every 24 hours (cheap: indexed
 *   expiry queries, a few ms when nothing is due).
 *
 * WIRING (parent): call [scheduleMemoryJanitor] once from BalochApp.onCreate.
 * The sweep count is returned in the worker's output Data ("cleaned") and by
 * [janitorSweep] directly, so the JARVIS-mode briefing can report it, e.g.
 * via [janitorReportLine].
 */
suspend fun janitorSweep(context: Context): Int {
    val repo = MemoryRepository.get(context.applicationContext)
    val now = System.currentTimeMillis()
    var cleaned = 0
    cleaned += repo.deleteExpiredTemporary(now)
    cleaned += repo.pruneOldConversations(now)
    return cleaned
}

/** Worker wrapper so WorkManager can run the sweep in the background. */
class MemoryJanitorWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val cleaned = janitorSweep(applicationContext)
            Result.success(workDataOf(KEY_CLEANED to cleaned))
        } catch (e: Exception) {
            // Transient failure (DB locked etc.) — retry with backoff.
            Result.retry()
        }
    }

    companion object {
        const val KEY_CLEANED = "cleaned"
    }
}

/**
 * Schedules both janitor rhythms. Safe to call on every app start —
 * ExistingPeriodicWorkPolicy.KEEP means an existing schedule is left alone.
 *
 * NOTE: the parent must also make sure BalochApp is the Application class
 * that calls this (work-runtime auto-initialises via its manifest provider).
 */
fun scheduleMemoryJanitor(context: Context) {
    val wm = WorkManager.getInstance(context.applicationContext)

    val monthly = PeriodicWorkRequestBuilder<MemoryJanitorWorker>(30, TimeUnit.DAYS)
        .addTag("memory-janitor-monthly")
        .build()
    wm.enqueueUniquePeriodicWork(
        "baloch-memory-janitor-monthly",
        ExistingPeriodicWorkPolicy.KEEP,
        monthly,
    )

    val daily = PeriodicWorkRequestBuilder<MemoryJanitorWorker>(1, TimeUnit.DAYS)
        .addTag("memory-janitor-daily")
        .build()
    wm.enqueueUniquePeriodicWork(
        "baloch-memory-janitor-daily",
        ExistingPeriodicWorkPolicy.KEEP,
        daily,
    )
}

/**
 * One-off immediate sweep (e.g. from a settings toggle or a debug screen).
 * Returns the number of rows cleaned.
 */
suspend fun runJanitorNow(context: Context): Int = janitorSweep(context)

private val JANITOR_REPORT = L3(
    en = "Sir, I cleaned up {n} trivial things from memory — everything important is safe 🧹",
    ur = "سر، میں نے میموری سے {n} بیکار باتیں صاف کر دیں — اہم سب کچھ محفوظ ہے 🧹",
    hi = "सर, मैंने मेमोरी से {n} बेकार बातें साफ़ कर दीं — ज़रूरी सब कुछ सुरक्षित है 🧹",
)

/** Briefing line for JARVIS mode after a sweep. Count 0 → silent (no noise). */
fun janitorReportLine(count: Int, lang: BalochLanguage): String? {
    if (count <= 0) return null
    return JANITOR_REPORT.format(lang, "n" to count.toString())
}
