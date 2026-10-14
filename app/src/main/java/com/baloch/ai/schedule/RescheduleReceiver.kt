package com.baloch.ai.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-arms every enabled scheduled task after a device reboot.
 * AlarmManager alarms do not survive reboots; WorkManager periodic work is
 * re-enqueued here too so a reboot can never silently drop a task.
 * Declared in the manifest with the BOOT_COMPLETED intent-filter.
 */
class RescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext
                TaskStore(app).all()
                    .filter { it.enabled }
                    .forEach { TaskScheduler.schedule(app, it) }
            } catch (_: Exception) {
                // Best-effort: a failed re-arm must never crash the boot path.
            } finally {
                pending.finish()
            }
        }
    }
}
