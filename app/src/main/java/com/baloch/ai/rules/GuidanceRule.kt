package com.baloch.ai.rules

import com.baloch.ai.brain.DeviceAction

/**
 * TEACHABLE GUIDANCE RULES — Kamran's order: "koi restrictions abhi k nai
 * lagani — main khud is ko apny treeqy sy guide kru ga."
 *
 * There are NO hard-coded restrictions here. These are Kamran's own words,
 * taught to Baloch in conversation, stored as data — editable, rephrasable
 * and deletable by him at any time from the MY RULES screen. Safety
 * confirmations ship as DEFAULT rules (starter suggestions), not code locks:
 * deleting the default "ask before calls" rule genuinely stops Baloch from
 * asking before calls — that is the whole point of teachability.
 *
 * The Orchestrator consults [GuidanceRuleStore.effectiveVerdict] before
 * executing any [DeviceAction]; the store is the single source of truth.
 * This file only holds the data model and the DeviceAction → scope mapping.
 */
enum class RuleScope { CALLS, MESSAGES, DISTURB, ALL }

enum class Constraint { REQUIRE_CONFIRM, FORBID, ALLOW, QUIET_HOURS }

data class GuidanceRule(
    val id: Long,
    /** Kamran's cleaned-up own words — shown verbatim in MY RULES. */
    val text: String,
    val scope: RuleScope,
    val constraint: Constraint,
    /** Only meaningful when constraint == QUIET_HOURS (0–23, local PKT hour). */
    val quietStartHour: Int = 0,
    val quietEndHour: Int = 7,
    /** True for the two seeded safety starters. Deletable like any rule. */
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Maps a [DeviceAction] to the rule scope it belongs to.
 *
 * READ-ONLY reference to brain.DeviceAction — verified against
 * BrainEngine.kt: CallContact, EmergencyCall, SendSms and WhatsAppDraft
 * all exist as subclasses. The `else` arm absorbs every current and future
 * safe action (OpenApp, ToggleWifi, …) without naming them, so this never
 * needs editing when a sibling adds a new action.
 *
 * DISTURB is never an action scope — it is handled separately by
 * [GuidanceRuleStore.isQuietNow], which the proactive scheduler consults
 * before unasked-for outputs (check-ins, battery alerts). Direct commands
 * always run: Kamran's word is law.
 */
fun DeviceAction.ruleScope(): RuleScope = when (this) {
    is DeviceAction.CallContact -> RuleScope.CALLS
    is DeviceAction.EmergencyCall -> RuleScope.CALLS
    is DeviceAction.SendSms -> RuleScope.MESSAGES
    is DeviceAction.WhatsAppDraft -> RuleScope.MESSAGES
    else -> RuleScope.ALL
}
