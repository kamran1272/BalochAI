package com.baloch.ai.schedule

import java.util.Calendar

/**
 * One user-ordered scheduled command. Stored in [TaskStore], fired by
 * [TaskScheduler] / [TaskFireReceiver] / [TaskWorker].
 *
 * Scheduled tasks are EXPLICIT user orders — they fire in BOTH Manual and
 * JARVIS mode. ModeManager is never consulted here.
 */
enum class Recurrence {
    ONCE,
    DAILY,
    WEEKLY,
}

data class ScheduledTask(
    val id: Long,
    val commandText: String,
    /** Next fire time, epoch millis. */
    val triggerAtMillis: Long,
    val recurrence: Recurrence,
    /** ISO weekdays 1=Monday..7=Sunday. Only meaningful for WEEKLY. */
    val daysOfWeek: Set<Int> = emptySet(),
    val createdAt: Long = System.currentTimeMillis(),
    val enabled: Boolean = true,
) {
    /**
     * The fire time AFTER [now]. For ONCE this is null (a one-shot has no
     * next fire once it has fired). For DAILY the same wall-clock time on
     * the next day strictly after [now]; for WEEKLY the next selected
     * weekday at the same wall-clock time, strictly after [now].
     *
     * The +24h / +7d stepping loop also self-heals drift: if a device was
     * off for days, the next computed trigger is still in the future.
     */
    fun nextTriggerAfter(now: Long): Long? {
        if (triggerAtMillis <= 0L) return null
        return when (recurrence) {
            Recurrence.ONCE -> null
            Recurrence.DAILY -> {
                var next = triggerAtMillis
                // Guard against pathological trigger values.
                var guard = 0
                while (next <= now && guard++ < 370) next += 24L * 60L * 60L * 1000L
                if (next <= now) null else next
            }
            Recurrence.WEEKLY -> {
                val days = daysOfWeek.filter { it in 1..7 }.toSortedSet()
                if (days.isEmpty()) return null
                val base = Calendar.getInstance().apply { timeInMillis = triggerAtMillis }
                val hour = base.get(Calendar.HOUR_OF_DAY)
                val minute = base.get(Calendar.MINUTE)
                // Check the coming 8 days: the first selected weekday whose
                // time-of-day is still ahead of `now` wins. Checking 8 (not 7)
                // covers "today is selected but its time already passed".
                for (offset in 0..8) {
                    val cand = Calendar.getInstance().apply {
                        timeInMillis = now
                        add(Calendar.DAY_OF_YEAR, offset)
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val isoDow = ((cand.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
                    if (isoDow in days && cand.timeInMillis > now) {
                        return cand.timeInMillis
                    }
                }
                null
            }
        }
    }
}
