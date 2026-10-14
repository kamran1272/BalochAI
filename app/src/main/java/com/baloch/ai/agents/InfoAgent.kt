package com.baloch.ai.agents

import com.baloch.ai.brain.DeviceAction

/**
 * Knowledge specialist: web search today; calculator, currency
 * converter and briefing actions later. Future actions are matched by
 * class name so this file compiles before they exist — execution goes
 * through [com.baloch.ai.engine.DeviceActions], which answers `Ok`
 * for anything it doesn't explicitly implement yet.
 */
class InfoAgent(private val deps: AgentDeps) : SpecialistAgent {
    override val name = "Info"
    override val icon = "🔍"

    private val futureActionNames = setOf(
        "Calculate", "ConvertCurrency", "CurrencyConverter", "GetBriefing",
    )

    override fun canHandle(a: DeviceAction): Boolean =
        a is DeviceAction.WebSearch || a.javaClass.simpleName in futureActionNames

    override suspend fun execute(t: AgentTask): AgentResult = runGuarded(t) {
        viaDeviceActions(deps, t)
    }
}
