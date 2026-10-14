package com.baloch.ai.schedule

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baloch.ai.connectivity.ConnectivityGate
import java.util.concurrent.TimeUnit

/**
 * Fires recurring (DAILY/WEEKLY) scheduled tasks via WorkManager.
 * Shares the exact execution path with the alarm receiver through
 * [TaskExecutor.fire] — nothing is duplicated here.
 *
 * QUEUE-AND-SYNC: a web-dependent command (web search, weather, live
 * currency, deep lookup) must not FAIL when the phone is offline — a
 * failure would silently drop the day's reminder/briefing. Instead this
 * worker enqueues a one-time retry constrained to CONNECTED network, so
 * the task fires the moment data returns. The normal periodic schedule
 * is untouched; the retry carries the task id and runs the same
 * TaskExecutor.fire path once the network is back. AlarmManager-fired
 * one-shot tasks never reach this path: they are local by construction
 * and always run (see TaskFireReceiver).
 */
class TaskWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(TaskScheduler.KEY_TASK_ID, -1L)
        if (id < 0L) return Result.failure()
        val task = TaskStore(applicationContext).get(id)
        if (task != null &&
            needsNetwork(task.commandText) &&
            !ConnectivityGate.isOnline(applicationContext)
        ) {
            // Offline + web-dependent: don't fail, don't drop — queue a
            // network-constrained retry. Success here means "handled".
            enqueueNetworkRetry(id)
            return Result.success()
        }
        return try {
            // fire() skips disabled/missing tasks internally.
            TaskExecutor.fire(applicationContext, id)
            Result.success()
        } catch (_: Exception) {
            // No blind retries: a failing command would spam Kamran daily.
            Result.failure()
        }
    }

    /**
     * One-time retry of this worker for [id], gated on CONNECTED network
     * (fires when data returns). Replaces any earlier pending retry for
     * the same task so offline stretches can't stack duplicates.
     */
    private fun enqueueNetworkRetry(id: Long) {
        val request = OneTimeWorkRequestBuilder<TaskWorker>()
            .setInputData(Data.Builder().putLong(TaskScheduler.KEY_TASK_ID, id).build())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .addTag(RETRY_TAG + id)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            RETRY_WORK + id,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        private const val RETRY_WORK = "baloch_task_netretry_"
        private const val RETRY_TAG = "baloch_netretry_"

        /**
         * Online-only markers checked (case-insensitively) against the
         * scheduled command text. Deliberately narrow: commands that
         * degrade gracefully offline (e.g. the smart briefing, which skips
         * weather honestly) are NOT gated here.
         */
        private val ONLINE_MARKERS = listOf(
            // web search
            "search", "google", "web ",
            // weather
            "weather", "mausam", "موسم", "मौसम",
            // currency / live rates
            "currency", "convert", "exchange", "dollar", "euro", "کرنسی",
            // number deep-lookup
            "lookup", "who is", "find out",
        )

        private fun needsNetwork(commandText: String): Boolean {
            val lower = commandText.lowercase()
            return ONLINE_MARKERS.any { lower.contains(it) }
        }
    }
}
