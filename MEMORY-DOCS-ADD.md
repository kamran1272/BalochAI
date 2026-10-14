# TOTAL MEMORY — Baloch's on-device brain (two-tier)

Kamran's order: *"har baat, mera data uski memory main rahy"* — Baloch remembers
EVERYTHING about him, forever, on-device. Zero cloud. Refinement: *"zaroori
hamesha, bekar har month khatam"* — important things stay forever, trivial
things expire after 30 days.

## Memory model (two tiers)

```
┌──────────────────────────── TOTAL MEMORY (Room, on-device) ────────────────────────────┐
│                                                                                       │
│  USER INPUT ──► AutoExtract ──► tier? ──┬── PERMANENT ──► facts.tier=PERMANENT          │
│      (EN / Roman /                        │                 expiresAt=NULL (never)       │
│       اردو / हिन्दी)                      │                                              │
│                                           │   becomes PERMANENT when:                    │
│                                           │   • explicit pin: "yaad rakhna", "keep in    │
│                                           │     mind", "save in your memories",          │
│                                           │     "یاد رکھنا", "याद रखना"                 │
│                                           │   • auto-important: people/clients,          │
│                                           │     deadlines, preferences, personal         │
│                                           │     identity, routines, scheduled tasks      │
│                                           │                                              │
│                                           └── TEMPORARY ──► facts.tier=TEMPORARY         │
│                                                             expiresAt=created+30d        │
│                                                             (chit-chat, jokes, small    │
│                                                              talk, "I'm bored")         │
│                                                                                       │
│  CONVERSATIONS inherit tier: an exchange that produces a PERMANENT fact is marked      │
│  producedPermanentFact=true and is NEVER pruned. Plain chit-chat expires in 30 days.   │
│                                                                                       │
│  MemoryJanitor (WorkManager) ──► monthly sweep + light daily check                      │
│      deletes expired TEMPORARY facts & conversations                                    │
│      NEVER touches PERMANENT rows or entities (people)                                  │
│      returns cleanup count ──► JARVIS briefing:                                        │
│      "Sir, maine 14 bekaar baatein saaf kar di — important sab mehfooz hai 🧹"         │
│                                                                                       │
│  MANUAL OVERRIDES (voice, via MemoryTierIntents — parent wires into brain):            │
│      "ye hamesha yaad rakhna" / "make this permanent" → pin latest temporary           │
│      "pin this: ..." → pin arbitrary content                                           │
│      "forget this" / "ye bhool jao" / "یہ بھول جاؤ" → delete latest / matching          │
│  UI: Memory screen (browse/search/delete, 📌/🕐 + "expires in X days", PIN-gated       │
│      "Forget everything"), one-tap encrypted backup export/import.                     │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

## Room tables

| Table | Key columns |
|---|---|
| `facts` | id, key, value, category (personal\|preference\|people\|work\|health\|other), confidence, createdAt, sourceQuote, **tier**, **expiresAt** |
| `conversations` | id, timestamp, userText, balochReply, language, mode, **tier**, **expiresAt**, **producedPermanentFact** |
| `entities` | id, name, type (person\|place\|org), notes, createdAt, **tier** (defaults PERMANENT — people are auto-important; janitor never auto-deletes) |

Files:
- `memory/room/MemoryEntities.kt` — entities + `Tier` enum + `TierConverters` + `TEMP_TTL_MS`
- `memory/room/MemoryDao.kt` — `FactDao`, `ConversationDao`, `EntityDao` (insert/all/search/expired/upgrade/delete)
- `memory/room/BalochDatabase.kt` — `@Database(version=1)`, SQLCipher singleton (see encryption below)
- `memory/MemoryRepository.kt` — suspend API, `get(context)` singleton
- `memory/AutoExtract.kt` — `extractCandidates()` (pure) + `autoExtractAndSave()` / `extractAndSaveDetailed()`
- `memory/Recall.kt` — `matchRecall()` + `answerRecall()` (trilingual)
- `memory/MemoryJanitor.kt` — `janitorSweep()`, `MemoryJanitorWorker`, `scheduleMemoryJanitor()`, `janitorReportLine()`
- `memory/MemoryTierIntents.kt` — `matchMemoryCommand()` → `MemoryCmd` + `executeMemoryCommand()`
- `memory/MemoryBackup.kt` — `exportToFile()` / `importFromFile()` (EncryptedFile)
- `memory/MemoryStore.kt` — legacy facade (CompanionAgent compat): `facts()`, `saveFact()`, `logExchange()` + `repo(context)`
- `ui/MemoryScreen.kt` — holographic-cyan screen + `PinGate` seam

## Auto-extract

Watches every input; trilingual regexes (EN, Roman Urdu, Urdu script, Hindi).
High precision only: skips questions, inputs >300 chars, command lines.
Never asks "should I remember?" — saves + confirms warmly:

- pinned/auto-important → *"Hamesha yaad rahega, sir 📌"* (trilingual)
- ordinary → *"Noted, sir — aapko cricket pasand hai. Yaad rakhunga. ✅"* (trilingual)

Near-duplicates skipped via `findByKey` + value comparison.

## Recall

- `matchRecall(input)` — "what do you remember about me", "tumhe kya yaad hai", "تمہیں کیا یاد ہے", "तुम्हें क्या याद है", "mera favourite X kya hai", "do you remember X"
- `answerRecall(input, repo, lang)` — specific fact lookup first, else a warm summary: permanent facts first (📌), then temporary (🕐), plus known people.

## Permissions / privacy

**Your memory never leaves your phone.**

- Encrypted at rest: SQLCipher (AES-256) encrypts the Room database file where
  available. The 256-bit passphrase is generated once and kept in
  `EncryptedSharedPreferences` ("baloch_crypto"), itself sealed by an Android
  Keystore MasterKey. If SQLCipher can't load on a device, Baloch falls back
  to plain Room and sets `BalochDatabase.isEncrypted = false` — the app keeps
  working and reports the truth; nothing silently downgrades.
- Keys: never in plain text, never in logs, never in backups of the DB file
  itself (the passphrase is separate from the encrypted bytes).
- Backups: one-tap export/import via `EncryptedFile` (AES-256-GCM) — the backup
  file is unreadable without this install's Keystore key.
- Reinstall limitation (honest): the Keystore key is wiped on uninstall, so a
  backup can only be restored on the SAME app install that created it. Export
  fresh after a reinstall; the old file cannot be decrypted. This is the price
  of zero-cloud privacy.
- No network calls anywhere in the memory stack. No analytics. No cloud sync.

No new Android permissions are required by the memory stack (Room, SQLCipher,
security-crypto and WorkManager need none beyond what the app already has).

## Wiring snippets (parent)

```kotlin
// BalochApp.onCreate — start the janitor (monthly sweep + daily check):
import com.baloch.ai.memory.scheduleMemoryJanitor
scheduleMemoryJanitor(this)

// Brain reply pipeline (per exchange):
import com.baloch.ai.memory.*

// 1. Manual memory commands first:
matchMemoryCommand(userText)?.let { cmd ->
    val reply = executeMemoryCommand(cmd, userText, repo, lang)
    speak(reply); return
}
// 2. Auto-extract (no asking, just remember):
val extraction = extractAndSaveDetailed(userText, repo, lang)
// 3. Log the exchange, linking permanent facts so the janitor keeps it:
repo.logConversation(
    userText, balochReply, lang.code, modeManager.currentMode.name,
    tier = if (extraction.savedPermanent) Tier.PERMANENT else Tier.TEMPORARY,
    producedPermanentFact = extraction.savedPermanent,
)
extraction.confirmation?.let { speak(it) }
// 4. Recall questions:
if (matchRecall(userText)) { speak(answerRecall(userText, repo, lang)); return }

// JARVIS briefing — janitor report:
janitorReportLine(lastSweepCount, lang)?.let { briefing.add(it) }

// Memory screen (parent collects flows in its ViewModel):
val facts by repo.factsFlow().collectAsState(initial = emptyList())
val entities = remember { mutableStateOf(emptyList<EntityRecord>()) }
MemoryScreen(
    facts = facts, entities = entities.value,
    searchQuery = q, onSearchChange = { q = it },
    onDeleteFact = { id -> scope.launch { repo.deleteFact(id) } },
    onDeleteEntity = { id -> scope.launch { repo.deleteEntity(id) } },
    onForgetAll = { scope.launch { repo.forgetAll() } },
    onExport = { createDocument.launch("baloch-memory-backup.enc") },
    onImport = { openDocument.launch(arrayOf("*/*")) },
    pinGate = object : PinGate {
        override suspend fun verifyPin(): Boolean = pinManager.verifyPin()
    },
)
// ActivityResultContracts handlers:
createDocument = registerForActivityResult(CreateDocument("application/octet-stream")) { uri ->
    uri ?: return@registerForActivityResult
    scope.launch { exportToFile(this@MainActivity, uri) }
}
openDocument = registerForActivityResult(OpenDocument()) { uri ->
    uri ?: return@registerForActivityResult
    scope.launch { importFromFile(this@MainActivity, uri) } // -1 = failed
}
```
