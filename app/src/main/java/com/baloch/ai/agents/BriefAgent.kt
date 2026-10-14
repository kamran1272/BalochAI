package com.baloch.ai.agents

import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.DeviceAction
import com.baloch.ai.brain.L3

/**
 * Live answers for the info actions ([DeviceAction.GetTime],
 * [DeviceAction.GetBattery], [DeviceAction.GetDeviceStats],
 * [DeviceAction.ReadNotifications], [DeviceAction.TellThought]).
 *
 * Answers come from injected [BriefProviders] lambdas (wired by the
 * ViewModel to [com.baloch.ai.engine.DeviceActions.currentContext] and
 * [com.baloch.ai.engine.NotificationReader]). Any provider left null
 * falls back to a graceful trilingual line instead of crashing.
 */
data class BriefProviders(
    val timeLine: ((BalochLanguage) -> String)? = null,
    val batteryLine: ((BalochLanguage) -> String)? = null,
    val statsLine: ((BalochLanguage) -> String)? = null,
    val notificationsLine: ((BalochLanguage) -> String)? = null,
    val thoughtLine: ((BalochLanguage) -> String)? = null,
)

class BriefAgent(
    deps: AgentDeps,
    private val providers: BriefProviders = BriefProviders(),
) : SpecialistAgent {
    override val name = "Brief"
    override val icon = "📊"

    override fun canHandle(a: DeviceAction): Boolean = when (a) {
        is DeviceAction.GetTime,
        is DeviceAction.GetBattery,
        is DeviceAction.GetDeviceStats,
        is DeviceAction.ReadNotifications,
        is DeviceAction.TellThought -> true
        else -> false
    }

    override suspend fun execute(t: AgentTask): AgentResult = runGuarded(t) {
        val provider: ((BalochLanguage) -> String)? = when (t.action) {
            is DeviceAction.GetTime -> providers.timeLine
            is DeviceAction.GetBattery -> providers.batteryLine
            is DeviceAction.GetDeviceStats -> providers.statsLine
            is DeviceAction.ReadNotifications -> providers.notificationsLine
            is DeviceAction.TellThought -> providers.thoughtLine
            else -> null
        }
        val line = provider?.invoke(t.lang) ?: L3(
            en = "I can't reach that right now, sir.",
            ur = "میں ابھی اس تک رسائی نہیں کر سکتا، سر۔",
            hi = "मैं अभी उस तक नहीं पहुँच सकता, सर।",
        ).pick(t.lang)
        AgentResult(name, true, line)
    }
}
