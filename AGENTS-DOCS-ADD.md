# Multi-Agent Parallel Execution — README section (merge into mobile README)

## Multi-agent parallel execution

Baloch doesn't run commands one by one. The `Orchestrator`
(`agents/Orchestrator.kt`) splits one utterance into parts on trilingual
conjunctions, asks the brain once **per part**, routes each resulting
`DeviceAction` to a specialist agent, runs all agents **concurrently**
(Kotlin `async` in a `supervisorScope`), and synthesises a single reply.

```
User: "subah 7 baje alarm lagao aur wifi band kar do aur aj ka mausam batao"
                          │
                    ┌──────┴──────┐
                    │ Orchestrator│  split on: and/aur/or/phir/then/اور/پھر/और/फिर
                    └──────┬──────┘
        ┌─────────────────┼─────────────────┐
   part 1            part 2             part 3
"subah 7 baje     "wifi band       "aj ka mausam
  alarm lagao"      kar do"           batao"
        │                 │                 │
   brain → SetAlarm   brain → ToggleWifi  brain → text only
   (7, 0)             (off)               (no device action)
        │                 │                 │
   ┌────┴────┐       ┌────┴────┐            │
   │ ⏰ Time │       │ 🖥️ System│     contributes brain
   │  Agent  │       │  Agent  │      text directly
   └────┬────┘       └────┬────┘            │
        └────────┬────────┘                 │
          unified reply (one message, one voice line)
```

The six specialists (each in its own file under `agents/`):

| Agent | Icon | Handles |
|---|---|---|
| SystemAgent | 🖥️ | OpenApp, ToggleWifi/Bluetooth/Flashlight, SetVolume/AdjustVolume/SetMuted, SetBrightness, SetDnd, DisableDndDelayed |
| InfoAgent | 🔍 | WebSearch (+ future calc/converter/briefing actions by name match) |
| CommsAgent | 📞 | CallContact, SendSms, EmergencyCall (all RISKY: never executes, returns the trilingual confirm question; the dialog + `confirmPending()` run it), WhatsAppDraft (SAFE: opens WhatsApp pre-filled, never auto-sends) |
| TimeAgent | ⏰ | SetAlarm, StartTimer, AddReminder |
| MemoryAgent | 🧠 | AddNote, RememberFact, ListNotes (writes straight to `NotesStore`/`MemoryStore`) |
| BriefAgent | 📊 | GetTime/GetBattery/GetDeviceStats/ReadNotifications/TellThought — answered from injected `BriefProviders` lambdas (wired to `DeviceActions.currentContext` + `NotificationReader`); a null provider yields a graceful fallback line, never a crash |

Anything no specialist claims goes to a private `FallbackAgent`
("General" ⚙️) that executes straight through `DeviceActions`.

### Key behaviours

- **Single command = fast path.** One part → brain text is the reply, no
  split overhead; the agent runs once and only a failure appends a note:
  `… — (SystemAgent hit a snag: …)`.
- **Multi command = one reply.** Trilingual intro ("Done, sir —" /
  "ہو گیا، سر —" / "हो गया, सर —") plus one `•` bullet per part, each
  bullet the part's agent line (or the brain's text when the part needed
  no action).
- **Failure isolation.** Every agent wraps execution in `runGuarded`
  (any exception → failed `AgentResult`), and the orchestrator adds
  `supervisorScope` + a second try/catch — one agent's crash can never
  cancel or block the others.
- **Live status chips.** Each running agent shows a chip under the orb
  (icon + name + `…`/`✓`/`✗`); WORKING chips pulse cyan. The ViewModel
  clears them 4 seconds after the run (cancellable).
- **SetMode never touches agents.** `DeviceAction.SetMode` is intercepted
  in the orchestrator and applied via `AgentDeps.onSetMode` → the
  ViewModel's existing `runAction(SetMode)` → `ModeManager` path.
- **Risky actions stay risky.** CommsAgent returns
  `needsConfirmation=true`; the orchestrator surfaces the first one as
  `PendingAction` and the existing confirm dialog flow is unchanged.
- **Forward compatibility.** Actions added later by other builders are
  matched by `javaClass.simpleName` where they don't exist yet — never by
  direct class references that would break compilation. (SetDnd,
  DisableDndDelayed, WhatsAppDraft and EmergencyCall already exist in
  `DeviceAction` today, so the agents reference them directly.)

### Worked example

Input (Roman Urdu): `subah 7 baje alarm lagao aur wifi band kar do aur aj ka mausam batao`

1. Split → 3 parts on `aur`.
2. Brain per part: `SetAlarm(7, 0)` · `ToggleWifi(false)` · text-only
   (no intent matches the weather question, so the brain's own text
   answers it directly — no agent needed).
3. Routing: ⏰ TimeAgent + 🖥️ SystemAgent launch in parallel; chips flip
   WORKING → DONE as each lands.
4. Reply (English): the multi-part synthesis, e.g.
   `Done, sir —` + `• Alarm set for 7:00, sir.` +
   `• Wi-Fi off, sir.` + `• I didn't quite catch that, sir. …`
   (spoken aloud via the normal `say()` path; the orb shows SPEAKING).
