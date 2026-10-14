package com.baloch.ai.agents

import com.baloch.ai.brain.DeviceAction

/** Time specialist: alarms, timers, reminders. */
class TimeAgent(private val deps: AgentDeps) : SpecialistAgent {
    override val name = "Time"
    override val icon = "⏰"

    override fun canHandle(a: DeviceAction): Boolean = when (a) {
        is DeviceAction.SetAlarm,
        is DeviceAction.StartTimer,
        is DeviceAction.AddReminder -> true
        else -> false
    }

    override suspend fun execute(t: AgentTask): AgentResult = runGuarded(t) {
        viaDeviceActions(deps, t)
    }
}
