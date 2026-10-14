# COMPANION — README section

> Kamran's order (Oct 14 2026): Baloch is also a **real-human-like friend** —
> chit-chat when he's bored, asks about his wellbeing, remembers personal
> things. Warm but honest: never claims to be human, never fakes feelings.

## What it is

The companionship layer of Mobile Baloch. Three files, no UI, no engine,
no voice changes:

| File | Role |
|---|---|
| `companion/ContentBanks.kt` | Trilingual content: jokes, facts, stories, small talk, mood replies, fallback lines, check-in message. All `L3` (en-GB / Urdu script / Devanagari). |
| `agents/CompanionAgent.kt` | The `Companion` specialist agent: trigger matching, mood awareness, `chatFallback`, personal-memory weave. |
| `companion/CheckInWorker.kt` | Proactive check-ins via WorkManager — JARVIS mode only. |

## CompanionAgent

`class CompanionAgent(memory: MemoryStore, context: Context) : SpecialistAgent`
(`name = "Companion"`, `icon = "💬"`).

- **Does not execute device actions** — `canHandle()` always returns false.
  The parent routes conversational input here via `chat()` / `chatFallback()`
  directly, or through `execute()` with the raw text in `AgentTask.partText`
  (e.g. when the Orchestrator splits a multi-part command and one part is
  conversational).
- **Contract note:** `SpecialistAgent` / `AgentTask` / `AgentResult` are
  defined provisionally at the top of `CompanionAgent.kt` because the
  `agents/` package is being built by a sibling in parallel. If the sibling's
  names differ slightly, the final pass adapts this file — the companion
  logic is independent of those shapes.

### Trigger matching (trilingual, incl. Roman Urdu)

Input is lowercased and keyword-matched. Checked in this order (so
"koi joke sunao" wins over the "sunao" in "aur sunao"):

- **story** — `story`, `kahani`/`کہانی`/`कहानी`, `qissa`/`قصہ`/`किस्सा`
- **joke** — `joke`, `mazak`/`لطیفہ`/`मज़ाक`, `hansao`, `funny`
- **fact** — `fun fact`, `mazedar`/`مزیدار`/`मज़ेदार`, `interesting`, `معلومات`
- **how are you** — `how are you`, `aap kaise`/`آپ کیسے`/`आप कैसे`
- **tum kaise ho** — `tum kaise ho`/`تم کیسے ہو`/`तुम कैसे हो`
- **aur sunao** — `aur sunao`/`اور سناؤ`/`और सुनाओ`, `kya haal`/`کیا حال`/`क्या हाल`, `haal chaal`

### Mood awareness

When no content trigger matches, keyword sets detect the mood —
**bored / tired / happy / stressed / excited** — in English, Roman Urdu,
Urdu script and Devanagari. Each mood has 2 empathetic `L3` replies plus a
gentle offer ("Break le lo, sir? Koi joke sunau? 🎧"). Deliberately warm,
never clinical: no diagnoses, no advice beyond rest/water/a chat.

### Fallback role — `chatFallback(input, lang)`

When NO command intent matched anywhere, the parent routes Orchestrator
misses here instead of a dead "I don't understand": warm acknowledgement
("Hmm, that one went over my head, sir…") + concrete options. 6 variants,
no immediate repeats, and every ~4th fallback attaches a "taster" line that
teaches what Baloch can do conversationally ("try 'koi joke sunao'…").

### Personal-memory weave

~1 in 3 conversational replies, `CompanionAgent` reads `memory.facts()`
(read-only; the parent's brain owns writes) and naturally references one
fact ("Waise sir, cricket ka kya scene hai aj kal? 🏏"). Keyword hooks for
cricket / football / exam / gym / client-project, generic template
otherwise. Facts are quoted as things *he told Baloch* — never invented
shared experiences.

## Content banks

| Bank | Count | Notes |
|---|---|---|
| `COMPANION_JOKES` | 44 | Clean programmer/freelancer humour, 1–3 sentences, never mean |
| `COMPANION_FACTS` | 44 | Tech, space, human body, history mix |
| `COMPANION_STORIES` | 8 | ~1-minute warm, slightly motivational (5–7 sentences each) |
| `SMALLTALK_HOW_ARE_YOU` | 6 | English-prompt small talk |
| `SMALLTALK_TUM_KAISE_HO` | 6 | Roman-Urdu-prompt small talk, code-switched register |
| `SMALLTALK_AUR_SUNAO` | 6 | "What's up" small talk |
| `MOOD_REPLIES` | 5 moods × 2 | Empathy + gentle offer |
| `FALLBACK_LINES` | 6 | Miss-handler lines |

All picks go through `CompanionPicker`, which tracks the last index per
bank key so the same item never repeats twice in a row. Urdu is in Urdu
script (TTS-safe per the `L3` contract — the ur-PK voice mispronounces
Roman Urdu); Roman-Urdu flavour lives in the natural spoken register of
the replies.

## Check-in policy (CheckInWorker)

- **JARVIS mode only** — `doWork()` returns `Result.success()` immediately
  in MANUAL mode. Scheduled tasks and direct commands are unaffected (they
  are explicit orders, not proactivity).
- **Quota:** `normal` → max 2/day; `subtle` → 1 per 2 days; `off` → none.
  Tracked in `SharedPreferences("baloch_companion")`: `last_checkin_date`
  (yyyy-MM-dd) + `checkin_count`. Setting key: `checkin_level`
  (`off`/`subtle`/`normal`, default `normal`) — written by the settings UI.
- **Smart timing:** notifies only ~8pm–10pm device-local **or** when 4+
  hours passed since the last interaction.
- **Parent contract:** the chat/voice pipeline must write
  `System.currentTimeMillis()` to `last_interaction_at` on every user
  interaction. The worker only reads it.
- **Notification:** channel `baloch_checkins`, title "◈ Baloch", tap opens
  `MainActivity` (immutable `PendingIntent`).
- **Scheduling:** `CheckInWorker.schedule(context)` enqueues a unique
  4-hourly periodic work (KEEP policy) — call once at app start. The worker
  itself enforces quota + timing, so the interval is just a heartbeat.
  `CheckInWorker.poke(context)` fires one immediate (still policy-gated)
  evaluation.

## The honesty boundary

Standing rule, enforced in content and code comments: Baloch is a warm,
playful, loyal friend — but it **never claims to be human** and **never
fakes feelings**. One small-talk variant says it outright ("as an AI I
don't feel things the way you do — but I'm glad you're here"). Memory
weaves quote what Kamran *told* Baloch; they never invent shared memories.
