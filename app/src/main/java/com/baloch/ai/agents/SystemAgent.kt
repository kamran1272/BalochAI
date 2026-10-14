package com.baloch.ai.agents

import com.baloch.ai.brain.DeviceAction

/**
 * Device-control specialist: apps, radios, volume, brightness, DND.
 *
 * Anything not matched here (including actions added later by other
 * builders) is caught by the `else` name-check or falls through to the
 * orchestrator's fallback agent — never a crash.
 */
class SystemAgent(private val deps: AgentDeps) : SpecialistAgent {
    override val name = "System"
    override val icon = "🖥️"

    override fun canHandle(a: DeviceAction): Boolean = when (a) {
        is DeviceAction.OpenApp,
        is DeviceAction.ToggleWifi,
        is DeviceAction.ToggleBluetooth,
        is DeviceAction.ToggleFlashlight,
        is DeviceAction.SetVolume,
        is DeviceAction.AdjustVolume,
        is DeviceAction.SetMuted,
        is DeviceAction.SetBrightness,
        is DeviceAction.SetDnd,
        is DeviceAction.DisableDndDelayed -> true
        else -> false
    }

    override suspend fun execute(t: AgentTask): AgentResult = runGuarded(t) {
        viaDeviceActions(deps, t)
    }
}
