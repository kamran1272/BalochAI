# Teachable Guidance Rules

> Kamran's standing order (Oct 14 2026): *"koi restrictions abhi k nai lagani —
> main khud is ko apny treeqy sy guide kru ga"* — no hard-coded restrictions;
> he guides Baloch himself. Safety confirmations ship as **default rules he can
> delete or rephrase**, not code locks.

## The philosophy

Baloch is Kamran's. There is no corporate nanny list of forbidden actions
buried in code. Instead, boundaries are **taught in conversation, stored as
data, and enforced by the Orchestrator**:

- Kamran says it in his own words (English, Roman Urdu, or Hindi/Urdu).
- Baloch parses it into a `GuidanceRule` and stores it in
  `guidance_rules.json` (app-private, atomic writes).
- Every proposed `DeviceAction` goes through `GuidanceRuleStore.effectiveVerdict()`
  before execution.
- He reviews, rephrases (delete + re-teach), or deletes rules in the
  **◈ MY RULES** screen. **Deleting a rule genuinely removes its effect.**

The two shipped safety starters ("ask before calls", "ask before messages")
are flagged `isDefault` — but that flag only buys them a confirmation dialog
on deletion. Delete one and its scope is recorded in `relaxed_scopes`
(SharedPreferences), which is what makes confirmations truly teachable:
deleting the call-confirm rule means calls no longer ask. No code lock
survives the deletion.

## How to teach

Say it in chat like you'd tell a person — the parser runs before the normal
intent pipeline:

1. `"Baloch, call karne se pehle hamesha poochna"`
   → CALLS / REQUIRE_CONFIRM — Baloch asks before every call from now on.
2. `"raat 12 ke baad mujhe disturb mat karna"`
   → DISTURB / QUIET_HOURS (00:00 → 07:00 PKT) — proactive outputs
   (check-ins, battery alerts) pause overnight. Assumption: "raat 12" =
   midnight; morning end defaults to 7. Re-teach to change either.
3. `"call karne se pehle mat poochna"`
   → CALLS / ALLOW — explicit relax; Baloch stops asking before calls.

More: `"meri permission ke baghair kabhi call mat karna"` → REQUIRE_CONFIRM
(permission qualifiers beat the "kabhi … mat karna" phrasing);
`"kabhi bhi bluetooth on mat karna"` → FORBID, stored ALL-scope with the
action named in the text; `"hamesha pehle mujhse poochna before sending any
message"` → MESSAGES / REQUIRE_CONFIRM.

Rule queries: `"show my rules"` / `"mere rules dikhao"` opens MY RULES;
`"delete rule 2"` / `"rule 2 hata do"` deletes row 2 (1-based, as displayed).

If an utterance isn't rule-teaching, the parser returns null and it flows to
the normal command pipeline untouched.

## MY RULES screen (`ui/RulesScreen.kt`)

Holographic-cyan list: each row shows the rule text (Kamran's own cleaned-up
words), scope + constraint chips, and a DEFAULT badge for the safety starters.
✕ per row; deleting a default asks once —
*"This is a safety default, sir — delete anyway?"* — then it goes. Quiet-hour
rules show their window (e.g. `Quiet 0:00 → 7:00 PKT`). Empty state teaches by
example: *"No rules yet, sir — teach me like: 'call karne se pehle hamesha
poochna'."*

## Verdict logic (`effectiveVerdict`)

1. **FORBID wins over everything** — his explicit "never do X"; the verdict
   carries his own rule text as the spoken reason.
2. Scope-specific rules beat ALL-scope rules; latest-taught wins within a
   scope. REQUIRE_CONFIRM → ask first, ALLOW → run.
3. ALL-scope REQUIRE_CONFIRM, then ALL-scope ALLOW.
4. Fallback: scope in `relaxedScopes` → Allow (he deleted the default, so the
   training wheels are off); otherwise the built-in risk tier —
   `action.needsConfirmation` (calls, SMS) → RequireConfirm; everything
   else → Allow.

Scope mapping (`DeviceAction.ruleScope()`): `CallContact`/`EmergencyCall` →
CALLS; `SendSms`/`WhatsAppDraft` → MESSAGES; everything else → ALL via an
`else` arm, so new safe actions never need this file edited.

## Quiet-hours semantics

`isQuietNow()` reads QUIET_HOURS rules against the current hour in
**Asia/Karachi** (wrap-around aware, e.g. 22→7). It is consulted **only by the
proactive scheduler** before unasked-for outputs (check-ins, battery alerts,
briefing nudges) — direct commands always run, even at 3am.

## Ownership principle

**Baloch never refuses or lectures on legitimate personal commands.** The
only guardrails are the ones Kamran set: his taught rules, his defaults, his
relaxations. A direct "call Ami now" runs — quietly if he taught it, with a
confirm if he kept the default, never with a lecture. The Orchestrator applies
`QuietDefer` to proactive outputs only; a direct order is always executed per
its verdict.

## Wiring checklist (final pass)

1. Construct `GuidanceRuleStore(appContext)` once (application scope).
2. Call `seedDefaultsIfNeeded()` once at app start.
3. In the Orchestrator, before executing ANY `DeviceAction`: `val v =
   store.effectiveVerdict(action)` → Allow: run; RequireConfirm: confirm UI;
   Forbid: speak `v.reason`, don't run.
4. In the input pipeline BEFORE intent parsing: `parseRuleTeach(input)` →
   non-null: `store.add(rule)`, confirm to Kamran, skip intents.
5. `matchRuleQuery(input)` → ListRules: navigate to `RulesScreen(state =
   RulesScreenState(store.all()), onDelete = { store.delete(it); refresh },
   onBack = …)`; DeleteRule(n): resolve `store.all()[n-1].id` → `store.delete`.
6. In the proactive scheduler (JARVIS mode): `if (store.isQuietNow())`
   skip/defer the unasked-for output.
