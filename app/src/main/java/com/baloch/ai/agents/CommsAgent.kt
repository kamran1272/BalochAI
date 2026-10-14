package com.baloch.ai.agents

import com.baloch.ai.brain.DeviceAction
import com.baloch.ai.brain.L3
import com.baloch.ai.brain.Replies

/**
 * Communication specialist: calls, SMS, WhatsApp drafts, emergency calls.
 *
 * Risk is decided by [DeviceAction.needsConfirmation], not by the agent:
 * - RISKY (CallContact, SendSms, EmergencyCall): this agent NEVER executes.
 *   It returns the trilingual confirmation question with
 *   [AgentResult.needsConfirmation]; the ViewModel shows the dialog and
 *   only [confirmPending] runs the action.
 * - SAFE (WhatsAppDraft): only opens WhatsApp with a pre-filled message —
 *   never auto-sends — so it executes straight through [DeviceActions].
 */
class CommsAgent(private val deps: AgentDeps) : SpecialistAgent {
    override val name = "Comms"
    override val icon = "📞"

    override fun canHandle(a: DeviceAction): Boolean = when (a) {
        is DeviceAction.CallContact,
        is DeviceAction.SendSms,
        is DeviceAction.EmergencyCall,
        is DeviceAction.WhatsAppDraft -> true
        else -> false
    }

    override suspend fun execute(t: AgentTask): AgentResult = runGuarded(t) {
        val a = t.action
        if (a.needsConfirmation) {
            val question: String = when (a) {
                is DeviceAction.CallContact ->
                    Replies.CONFIRM_CALL.format(t.lang, "name" to a.name)
                is DeviceAction.SendSms ->
                    Replies.CONFIRM_SMS.format(t.lang, "name" to a.name)
                is DeviceAction.EmergencyCall ->
                    L3(
                        en = "This will call your emergency contact {name} — shall I go ahead, sir?",
                        ur = "یہ آپ کے ایمرجنسی رابطے {name} کو کال کرے گا — کیا میں آگے بڑھوں، سر؟",
                        hi = "यह आपके आपातकालीन संपर्क {name} को कॉल करेगा — क्या मैं आगे बढ़ूँ, सर?",
                    ).format(t.lang, "name" to a.name)
                else -> L3(
                    en = "Shall I go ahead with this, sir?",
                    ur = "کیا میں اسے جاری رکھوں، سر؟",
                    hi = "क्या मैं इसे जारी रखूँ, सर?",
                ).pick(t.lang)
            }
            AgentResult(
                agentName = name,
                ok = true,
                line = question,
                needsConfirmation = true,
                pendingAction = a,
            )
        } else {
            viaDeviceActions(deps, t)
        }
    }
}
