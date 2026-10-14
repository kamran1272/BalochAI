# FORTRESS PRIVACY — learn-vs-act doctrine

Material for PRIVACY.md (three paragraphs Kamran can trust):

**1. The doctrine.** Baloch learns freely and acts only with your permission. Reading your notifications, remembering your facts, recalling your notes — that is learning, and it always works. But nothing personal ever leaves the phone without you saying so first: no message sent, no call placed, no photo, video, document or contact shared, nothing deleted — until you confirm that exact action, with the exact recipient and the exact words shown to you.

**2. The fortress.** "Ask me first" is not a setting you can switch off by accident. You can teach Baloch standing allowances in plain words — "always send WhatsApp to Ami without asking" — but only specific ones: an allowance for messages never covers calls, and a blanket "don't ask me about anything" is ignored by design. Even if you delete a default safety rule, the code-level guard still asks. Manual mode and JARVIS mode both obey it.

**3. Voice safety.** If Baloch heard you over voice and isn't sure it heard right, it reads the exact message, name and number back to you before anything goes out. A misheard voice can never send a message or place a call on its own. Your tap or your "yes" is the key — always.

---

## Wiring checklist for the parent (Orchestrator.kt)

1. Before executing ANY `DeviceAction`, call `GuidanceRuleStore.effectiveVerdict(action)`. `Verdict.Forbid` → block with the rule's own reason text; nothing else runs.
2. Call `PersonalDataGuard.requiresExplicitConfirm(action)`. If false → proceed to step 6.
3. Call `PersonalDataGuard.standingAllowanceFor(action, store)`. Non-null → proceed to step 6 (log which rule allowed it).
4. Otherwise → build a MANDATORY `PendingAction` (confirm dialog + spoken text from `PersonalDataGuard.guardConfirmText(action, lang)`). This gate runs in BOTH Manual and JARVIS mode; relaxed-scopes and ALL-scope ALLOW rules must NOT bypass it.
5. If the action came from voice, call `PersonalDataGuard.needsPayloadReconfirm(action, voiceConfidence)`. True → re-speak the exact payload (name, number, message) and require an explicit yes/no before the step-4 confirm counts.
6. Execute. Direct user commands that pass the above always run after HE confirms — his tap/yes is the permission. For `EmergencyCall`, the guard confirm IS the existing emergency confirmation — reuse it, don't stack a second dialog.

Implementation: `app/src/main/java/com/baloch/ai/rules/PersonalDataGuard.kt` (`object PersonalDataGuard`).
