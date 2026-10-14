package com.baloch.ai.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires one-shot scheduled tasks armed via
 * AlarmManager.setExactAndAllowWhileIdle (see [TaskScheduler]).
 * Declared in the manifest with exported=false.
 */
class TaskFireReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(TaskScheduler.EXTRA_TASK_ID, -1L)
        if (id < 0L) return
        // onReceive runs on the main thread. The rule-based brain is fast and
        // runBlocking keeps the whole fire atomic inside the broadcast window
        // (well under the ~10s ANR budget for these small commands).
        TaskExecutor.fire(context.applicationContext, id)
    }
}
