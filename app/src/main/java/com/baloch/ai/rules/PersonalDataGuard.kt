package com.baloch.ai.rules

import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.DeviceAction
import com.baloch.ai.brain.L3

/**
 * PERSONAL DATA GUARD — Kamran's order, his exact words:
 * "personal cheezein seekhna theek hai, lekin meri permission ke
 *  baghair wahan KUCH na kare"
 * ("Learning personal things is fine, but without my permission
 *  do NOTHING there.")
 *
 * This is the CODE-LEVEL layer of the learn-vs-act doctrine. It is NOT
 * teachable, NOT deletable from MY RULES, and NOT relaxable by the
 * relaxed-scope mechanism in [GuidanceRuleStore]. Deleting a default
 * safety rule only relaxes the *teachable rules* layer; this guard still
 * asks. That is deliberate: Kamran's permission to transmit must be
 * permission given *in the moment or by specific standing allowance*,
 * never an accident of a deleted rule.
 *
 * ------------------------------------------------------------------
 * WIRING CONTRACT — for the parent wiring this into Orchestrator.kt.
 * For every [DeviceAction], in THIS order:
 *
 *  (a) [GuidanceRuleStore.effectiveVerdict] — a FORBID verdict blocks the
 *      action outright and wins over everything below. Speak the rule's
 *      own text back; run nothing.
 *  (b) [PersonalDataGuard.requiresExplicitConfirm] — if true, call
 *      [PersonalDataGuard.standingAllowanceFor]. If it returns a rule,
 *      the action proceeds (log which rule allowed it). Otherwise the
 *      action becomes a MANDATORY PendingAction whose confirmation text
 *      comes from [PersonalDataGuard.guardConfirmText] — this gate runs
 *      in BOTH Manual and JARVIS mode, and blanket relax rules
 *      ("don't ask me about anything", ALL-scope ALLOW) can NEVER skip
 *      it. Only a scope-specific standing allowance skips it.
 *  (c) [PersonalDataGuard.needsPayloadReconfirm] — if the action came
 *      from voice and confidence is low/missing, re-speak the EXACT
 *      payload (name, number, message) and require an explicit yes/no
 *      before the step-(b) confirm counts. A misheard voice must never
 *      fire a transmit.
 *
 *  Direct user commands still execute after THEY confirm — his tap / "yes"
 *  IS the permission. The guard is not a wall; it is a locked door whose
 *  key is always his word.
 * ------------------------------------------------------------------
 */
object PersonalDataGuard {

    // ------------------------------------------------------------------
    // 1. Data-touch classification
    // ------------------------------------------------------------------

    /**
     * How deeply an action touches Kamran's personal data.
     *
     * - [NONE]: device-local only; no personal data leaves the phone and
     *   nothing is irreversibly changed. OpenApp, toggles, volume,
     *   brightness, alarms, timers, DND, mode, status reads, spoken
     *   thoughts.
     * - [READ]: learning. Baloch may READ and REMEMBER freely — the whole
     *   memory system depends on this. Reading is never transmission.
     * - [MUTATE_TRANSMIT]: personal data leaves the phone (message, voice,
     *   contact) or is irreversibly changed (delete). Always needs his
     *   explicit permission per action.
     */
    enum class DataTouch { NONE, READ, MUTATE_TRANSMIT }

    /**
     * Classifies a [DeviceAction]. Every subclass is named explicitly;
     * the `else` arm only catches FUTURE subclasses (see [futureTouch]).
     *
     * Honest nuances, documented rather than hidden:
     * - WebSearch -> NONE: the search query IS his typed words and it DOES
     *   travel to the search engine as an outbound network request — but
     *   it is his own words, not stored personal data. Per NETWORK_AUDIT.md
     *   the query text itself is the only outbound content of a search.
     * - WhatsAppDraft -> MUTATE_TRANSMIT (deliberate divergence from the
     *   old "SAFE" comment in BrainEngine.kt): nothing auto-sends — he
     *   still taps send inside WhatsApp — but the pre-filled message text
     *   already leaves Baloch's process boundary into another app, outside
     *   his permission gate. So the guard treats it as transmit.
     * - CallContact / EmergencyCall -> MUTATE_TRANSMIT: live voice to a
     *   person leaves the phone. EmergencyCall keeps its existing
     *   confirmation — the guard's mandatory confirm IS that confirmation;
     *   the orchestrator must reuse it, not stack a second dialog.
     * - Internal contact resolution (DeviceActions.resolveContact) and the
     *   memory commands "forget this/that" / "Forget everything"
     *   (MemoryCmd.Forget, MemoryRepository.forgetAll) are NOT DeviceAction
     *   subclasses, so they never pass through here. By doctrine they are
     *   READ-equivalent: his explicit commands to his own AI about his own
     *   data, never transmission. "Forget everything" is additionally
     *   PIN-gated elsewhere.
     */
    fun DeviceAction.dataTouch(): DataTouch = when (this) {
        // ---------- READ: learning — always allowed ----------
        is DeviceAction.RememberFact -> DataTouch.READ
        is DeviceAction.AddNote -> DataTouch.READ
        is DeviceAction.ListNotes -> DataTouch.READ
        is DeviceAction.ReadNotifications -> DataTouch.READ

        // ---------- MUTATE_TRANSMIT: his explicit permission required ----------
        is DeviceAction.CallContact -> DataTouch.MUTATE_TRANSMIT
        is DeviceAction.SendSms -> DataTouch.MUTATE_TRANSMIT
        is DeviceAction.WhatsAppDraft -> DataTouch.MUTATE_TRANSMIT
        is DeviceAction.EmergencyCall -> DataTouch.MUTATE_TRANSMIT

        // ---------- NONE: device-local, nothing leaves ----------
        is DeviceAction.OpenApp -> DataTouch.NONE
        is DeviceAction.SetAlarm -> DataTouch.NONE
        is DeviceAction.StartTimer -> DataTouch.NONE
        is DeviceAction.AddReminder -> DataTouch.NONE
        is DeviceAction.ToggleWifi -> DataTouch.NONE
        is DeviceAction.ToggleBluetooth -> DataTouch.NONE
        is DeviceAction.ToggleFlashlight -> DataTouch.NONE
        is DeviceAction.SetVolume -> DataTouch.NONE
        is DeviceAction.AdjustVolume -> DataTouch.NONE
        is DeviceAction.SetMuted -> DataTouch.NONE
        is DeviceAction.SetBrightness -> DataTouch.NONE
        is DeviceAction.WebSearch -> DataTouch.NONE
        is DeviceAction.SetDnd -> DataTouch.NONE
        is DeviceAction.DisableDndDelayed -> DataTouch.NONE
        is DeviceAction.GetTime -> DataTouch.NONE
        is DeviceAction.GetBattery -> DataTouch.NONE
        is DeviceAction.GetDeviceStats -> DataTouch.NONE
        is DeviceAction.TellThought -> DataTouch.NONE
        is DeviceAction.SetMode -> DataTouch.NONE

        // ---------- FUTURE subclasses: the named rule below ----------
        else -> futureTouch()
    }

    /**
     * FUTURE-PROOF RULE, by name: an action added later defaults to READ
     * (learning is free) UNLESS its class name smells like transmission or
     * irreversible change — contains Send / Share / Upload / Post / Delete
     * — in which case it defaults to MUTATE_TRANSMIT (permission required).
     *
     * This arm only ever sees subclasses that do not exist yet; every
     * current subclass is named explicitly above. (Caveat: name matching
     * assumes non-obfuscated class names; with R8 full obfuscation a future
     * action would land on READ. New transmit actions must still be added
     * to the explicit arms above — this arm is a safety net, not a
     * substitute.)
     */
    private fun DeviceAction.futureTouch(): DataTouch {
        val smell = javaClass.simpleName.uppercase()
        return if (TRANSMIT_NAME_SMELLS.any { smell.contains(it) })
            DataTouch.MUTATE_TRANSMIT
        else
            DataTouch.READ
    }

    private val TRANSMIT_NAME_SMELLS = setOf("SEND", "SHARE", "UPLOAD", "POST", "DELETE")

    // ------------------------------------------------------------------
    // 2. The gate
    // ------------------------------------------------------------------

    /** True only for [DataTouch.MUTATE_TRANSMIT] actions. */
    fun requiresExplicitConfirm(action: DeviceAction): Boolean =
        action.dataTouch() == DataTouch.MUTATE_TRANSMIT

    // ------------------------------------------------------------------
    // 3. The teachable escape hatch — standing allowances
    // ------------------------------------------------------------------

    /**
     * Kamran's teachable escape hatch: a STORED [GuidanceRule] with
     * constraint ALLOW whose scope EXACTLY equals the action's
     * [ruleScope] (CALLS for calls, MESSAGES for messages) counts as a
     * standing allowance — e.g. "always send WhatsApp to Ami without
     * asking" stored with scope MESSAGES lets WhatsAppDraft through.
     *
     * ### THE FORTRESS RULE — read twice
     * A blanket ALL-scope ALLOW ("don't ask me about anything") NEVER
     * satisfies this guard. Only a scope-SPECIFIC allowance does. His
     * permission to SEND must be permission to send — specific, taught for
     * that kind of act — never a blanket pass he may have taught for
     * something harmless. An ALL-scope allowance is deliberately ignored
     * here even though [GuidanceRuleStore.effectiveVerdict] honours it;
     * the guard is the stricter layer.
     *
     * Latest-taught wins (mirrors effectiveVerdict): if he later taught
     * REQUIRE_CONFIRM (or FORBID) for the same scope, the allowance is
     * gone — he changed his mind. QUIET_HOURS rules never count as
     * allowances; they govern proactive outputs only.
     *
     * The orchestrator MUST run the (a)-step FORBID check via
     * effectiveVerdict BEFORE calling this: a stored FORBID already
     * blocked the action there.
     *
     * @return the standing ALLOW rule, or null if the guard must ask.
     */
    fun standingAllowanceFor(
        action: DeviceAction,
        store: GuidanceRuleStore,
    ): GuidanceRule? {
        if (!requiresExplicitConfirm(action)) return null
        val scope = action.ruleScope()
        val latest = store.all()
            .filter {
                it.scope == scope && (
                    it.constraint == Constraint.ALLOW ||
                        it.constraint == Constraint.REQUIRE_CONFIRM ||
                        it.constraint == Constraint.FORBID
                    )
            }
            .maxByOrNull { it.createdAt }
        return if (latest?.constraint == Constraint.ALLOW) latest else null
    }

    // ------------------------------------------------------------------
    // 4. Trilingual confirmation text — names the EXACT payload
    // ------------------------------------------------------------------

    /** Max payload characters echoed in a confirm; longer text is cut with …. */
    private const val PAYLOAD_ECHO_LIMIT = 280

    /** Minimum voice confidence below which a transmit must be re-confirmed. */
    private const val VOICE_CONFIDENCE_FLOOR = 0.6f

    private val SMS_CONFIRM = L3(
        en = "Sir, this message is for {name} ({number}): \"{message}\" — shall I send it?",
        ur = "سر، یہ پیغام {name} ({number}) کو بھیجنا ہے: \"{message}\" — بھیج دوں؟",
        hi = "सर, यह संदेश {name} ({number}) को भेजना है: \"{message}\" — भेज दूँ?",
    )
    private val WHATSAPP_CONFIRM = L3(
        en = "Sir, this goes to WhatsApp for {name} ({number}): \"{text}\" — shall I send it?",
        ur = "سر، یہ {name} ({number}) کے لیے WhatsApp پر بھیجنا ہے: \"{text}\" — بھیج دوں؟",
        hi = "सर, यह {name} ({number}) के लिए WhatsApp पर भेजना है: \"{text}\" — भेज दूँ?",
    )
    private val CALL_CONFIRM = L3(
        en = "Sir, shall I call {name} ({number})?",
        ur = "سر، {name} ({number}) کو کال کروں؟",
        hi = "सर, {name} ({number}) को कॉल करूँ?",
    )
    private val EMERGENCY_CALL_CONFIRM = L3(
        en = "Sir — this is an EMERGENCY call to {name} ({number}). Shall I place it right now?",
        ur = "سر — یہ {name} ({number}) کو ایمرجنسی کال ہے۔ کیا ابھی کال کروں؟",
        hi = "सर — यह {name} ({number}) को आपातकालीन कॉल है। क्या अभी कॉल करूँ?",
    )
    private val GENERIC_TRANSMIT_CONFIRM = L3(
        en = "Sir, this will send or change your personal data ({detail}) — shall I go ahead?",
        ur = "سر، اس سے آپ کا ذاتی ڈیٹا بھیجا یا تبدیل ہو جائے گا ({detail}) — آگے بڑھوں؟",
        hi = "सर, इससे आपका व्यक्तिगत डेटा भेजा या बदला जाएगा ({detail}) — आगे बढ़ूँ?",
    )

    /**
     * Warm, friendly confirmation that NAMES the exact payload — the
     * recipient's name and number plus the full message text — so Kamran
     * approves precisely what will go out, never a vague "send message?".
     * Trilingual via [L3] (Urdu in Urdu script, Hindi in Devanagari, per
     * the brain's TTS convention). Payloads longer than
     * [PAYLOAD_ECHO_LIMIT] chars are cut with "…" for speakability.
     *
     * For EmergencyCall this text doubles as the existing emergency
     * confirmation — reuse it, don't stack two dialogs.
     */
    fun guardConfirmText(action: DeviceAction, lang: BalochLanguage): String {
        val template = when (action) {
            is DeviceAction.SendSms -> SMS_CONFIRM
            is DeviceAction.WhatsAppDraft -> WHATSAPP_CONFIRM
            is DeviceAction.CallContact -> CALL_CONFIRM
            is DeviceAction.EmergencyCall -> EMERGENCY_CALL_CONFIRM
            else -> GENERIC_TRANSMIT_CONFIRM
        }
        val name = when (action) {
            is DeviceAction.SendSms -> action.name
            is DeviceAction.WhatsAppDraft -> action.name
            is DeviceAction.CallContact -> action.name
            is DeviceAction.EmergencyCall -> action.name
            else -> ""
        }.ifBlank { "—" }
        val number = when (action) {
            is DeviceAction.SendSms -> action.number
            is DeviceAction.WhatsAppDraft -> action.number
            is DeviceAction.CallContact -> action.number
            is DeviceAction.EmergencyCall -> action.number
            else -> ""
        }.ifBlank { "—" }
        val payload = when (action) {
            is DeviceAction.SendSms -> action.message
            is DeviceAction.WhatsAppDraft -> action.text
            else -> action.javaClass.simpleName
        }.let {
            if (it.length > PAYLOAD_ECHO_LIMIT) it.take(PAYLOAD_ECHO_LIMIT) + "…" else it
        }
        return template.format(
            lang,
            "name" to name,
            "number" to number,
            "message" to payload,
            "text" to payload,
            "detail" to payload,
        )
    }

    // ------------------------------------------------------------------
    // 5. Low-confidence voice must never fire a transmit
    // ------------------------------------------------------------------

    /**
     * True when the action is transmit-class AND the voice confidence is
     * missing or below [VOICE_CONFIDENCE_FLOOR] (0.6). The orchestrator must
     * then re-speak the exact payload — name, number, message — and require
     * an explicit yes/no before the step-(b) confirmation counts.
     *
     * Typed input carries no confidence value, so pass null only for
     * genuinely-unknown voice confidence; typed commands skip this gate
     * (the user literally typed the words).
     */
    fun needsPayloadReconfirm(action: DeviceAction, voiceConfidence: Float?): Boolean =
        requiresExplicitConfirm(action) &&
            (voiceConfidence == null || voiceConfidence < VOICE_CONFIDENCE_FLOOR)
}
