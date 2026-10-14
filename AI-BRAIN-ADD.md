# AI BRAIN 🧠 — on-device LLM (v1.0, REAL — no stub)

Kamran's order (14 Oct 2026 ~08:47 PKT): *"model bhi sath kar do abhi"* —
the LLM brain is REAL in v1.0. `LlmBrain` is no longer a stub: it runs
Gemma 2B instruct fully on-device via MediaPipe's LLM Inference API.

## 1. Architecture — hybrid fast-path

```
                        ┌─────────────────────┐
  Kamran's input ──────►│     HybridBrain     │  (the default BrainEngine)
                        └─────────┬───────────┘
                                  │ 1. RuleBasedBrain.respond()
                    ┌─────────────┴──────────────┐
                    │ match?  (instant, offline) │
                    └──────┬──────────────┬──────┘
                  YES │                  │ NO (pure fallback:
                      ▼                  │  NOT_UNDERSTOOD + no actions)
          reply + DeviceActions          ▼
          (rules own ALL            BrainMode?
          device effects)           ┌──────┴──────┐
                               RULES_ONLY    HYBRID
                                    │            │
                                    ▼            ▼
                              rule fallback  LlmBrain.respond()
                              (unchanged)         │
                                                  ├─ model missing → "download the AI brain"
                                                  │   (Settings → AI Brain 🧠 hint)
                                                  ├─ OOM / load fail / timeout →
                                                  │   warm companion-style fallback
                                                  └─ success → text-only reply
```

Design rules:
- **Rules own every device effect.** The LLM never emits `DeviceAction`
  (see §5 Safety). HybridBrain only consults the LLM on a *pure* rule
  miss — `Replies.NOT_UNDERSTOOD` with an empty action list. Real rule
  answers (contact-not-found, permission-needed, …) are never overridden.
- **Offline-safe degrade.** Model missing, engine OOM, load failure, or a
  120 s generation timeout all land on a warm persona line — never a crash,
  never an error dump.
- **Fast by default.** The LLM's cold start is ~10–15 s and CPU generation
  is seconds-per-reply; it only spins up for inputs the rule engine
  genuinely doesn't understand.

Files:
- `brain/LlmModelManager.kt` — model file lifecycle (download / resume /
  import / delete / RAM guard).
- `brain/LlmBrain.kt` — real MediaPipe implementation (replaces the stub).
- `brain/HybridBrain.kt` — the default brain (rules → LLM fallback).
- `settings/AiBrainPrefs.kt` — `BrainMode` enum + persisted prefs.
- `ui/SettingsScreen.kt` — **additive only**: new `SettingsState` fields +
  new `SettingsActions` callbacks (see §6). NOTE: the task brief named
  `settings/SettingsActions.kt`, but that file never existed — the
  `SettingsActions` interface actually lives in `ui/SettingsScreen.kt`,
  so the additive contract went there.

## 2. Model details

| | |
|---|---|
| Model | **Gemma 2B instruct, INT4, CPU** — `gemma-2b-it-cpu-int4.bin` |
| Size | **~1.35 GB** (1,354,301,440 bytes extracted) |
| Format | MediaPipe `.bin` — chat template applied **manually in code** (`LlmBrain.buildPrompt` wraps in Gemma `<start_of_turn>` markers; `.task` files would auto-template, `.bin` does not) |
| Source | Kaggle: `https://www.kaggle.com/models/google/gemma/tfLite/gemma-2b-it-cpu-int4` (direct API URL in `LlmModelManager.MODEL_URL`) |
| Runtime | `com.google.mediapipe:tasks-genai:0.10.27` (`LlmInference.createFromOptions` → `generateResponse`) |
| Backend | CPU default (deliberate: GPU builds of this model hit "out of host memory" init failures on tasks-genai 0.10.13/0.10.14) |
| Config | maxTokens 2048 · topK 40 · temperature 0.7 · time-based random seed |
| minSdk | tasks-genai requires API 26+ — the app is exactly minSdk 26 ✓ |

**License note (important):** Gemma is Google's model under the **Gemma
Terms of Use** — NOT Apache/MIT. Downloading from Kaggle requires a free
Kaggle account + accepting Google's terms (that's why the in-app download
can hit a sign-in gate — see §4). The model file on Kamran's phone is for
his personal use inside Baloch; it is never uploaded anywhere (all
inference is on-device).

## 3. RAM guard policy

`LlmModelManager.MIN_RAM_GB = 4.0`. `deviceRamGb()` reads the honest total
from `ActivityManager.MemoryInfo` (not an estimate).

- `< 4 GB` → `downloadModel()` / `importModelFile()` return
  `BlockedLowRam` **before any network or disk use**. The settings screen
  must hide/disable the Download button when `!aiModelRamOk` and show:
  *"Sir, 4GB+ RAM chahiye AI brain ke liye — rules mode perfect rahega."*
  (trilingual variants live in the parent's wiring; the manager only
  reports the numbers).
- `≥ 4 GB` → download allowed. Gemma-2B INT4 needs ~2.5–3 GB working RAM
  on top of the OS; 4 GB devices will be slow but functional, 6 GB+ is
  comfortable.

## 4. Download / storage behaviour

- **Never in the APK.** The model downloads on first run to app-private
  storage: `<filesDir>/llm/gemma-2b-it-cpu-int4.bin`. App-private = no
  storage permission needed, auto-removed on uninstall.
- **Resumable.** Downloads go to `<name>.part` with HTTP `Range` resume;
  a killed/interrupted download resumes next time. Coroutine cancellation
  aborts cleanly and keeps the `.part`.
- **Verified.** Success requires the final size within ±2% of
  `MODEL_SIZE_BYTES`; anything else is deleted with an honest message.
  `isDownloaded()` additionally requires ≥ 1 GB, so a corrupt half-file
  can never reach the inference engine. Publish is atomic (rename).
- **Sign-in gate honesty.** The Kaggle API URL needs Kaggle sign-in. If it
  answers 401/403 or HTML, `downloadModel()` returns `NeedsManualImport` —
  Kamran downloads the `.bin` once manually (page URL in
  `LlmModelManager.MODEL_PAGE_URL`), then **Settings → AI Brain →
  "Import model file"** copies it in via the Storage Access Framework
  (`importModelFile`), with a size sanity check that rejects wrong files.
- **Override.** `AiBrainPrefs.customModelUrl` lets Kamran point the
  downloader at his own mirror (Drive/R2/PC) — then no sign-in is needed.
- **Delete** removes the model (+ stale `.part`); the brain keeps working
  via rules + companion fallback.

## 5. Safety rules

1. **The LLM never emits `DeviceAction`.** `LlmBrain.respond()` always
   returns `BrainResponse(text)` with an empty action list. Rationale:
   an LLM turning free text into actions is a prompt-injection path to
   `CallContact` / `SendSms` / `SendSosAlert` — deterministic rules are
   the only action source. (Commented in `LlmBrain.kt` header.)
2. **The system prompt forbids action claims.** If Kamran asks the LLM
   side to call/message someone, it must NOT claim it did — it says warmly
   that he'll need to confirm, or to phrase it as a direct command.
3. **No transmit actions, ever, from LLM output.** Even a future tool-use
   upgrade must restrict itself to an explicit allowlist of
   read-only/informational actions, validated by exact enum match.
4. **The brain never throws.** Every failure path returns a persona-warm
   line (`BalochPersona.gentleError`), never raw exception text.
5. **No prompt leakage.** The system prompt instructs the model never to
   reveal instructions, tokens, or model details.

## 6. Settings contract — exact callbacks the UI must wire

`SettingsActions` (in `ui/SettingsScreen.kt`) gained — parent
(`BalochViewModel.settingsActions`) implements each against the real
manager; `SettingsState` carries the display fields:

| Callback | Wires to |
|---|---|
| `onBrainModeChange(mode: BrainMode)` | `AiBrainPrefs(appCtx).brainMode = mode`; refresh state. HybridBrain reads the same prefs via its constructor lambda → effective on the next message. |
| `onDownloadAiModel()` | `viewModelScope.launch { val job = …; modelManager.downloadModel { p -> update aiModelDownloadProgress } }` → handle `Success` / `BlockedLowRam` / `NeedsManualImport` / `NetworkError` / `Cancelled` with honest trilingual messages; keep a handle for cancel. |
| `onCancelAiModelDownload()` | Cancel the download Job; reset `aiModelDownloadProgress = null`. |
| `onDeleteAiModel()` | Confirm dialog first → `modelManager.deleteModel()` → refresh state. |
| `onImportAiModelFile()` | SAF picker → `modelManager.importModelFile(uri) { progress }` → surface `ImportResult` verbatim. |

`SettingsState` fields: `brainMode` (default HYBRID), `aiModelDownloaded`,
`aiModelDownloadProgress: Float?` (null = idle), `aiModelSizeBytes`
(display via `LlmModelManager.sizeLabel`), `aiModelRamGb`,
`aiModelRamOk` (hide Download when false).

### Required "AI Brain 🧠" screen elements (for the UI/UX builder)

1. **Brain mode selector** — two options: "Rules only" (instant, zero
   extra RAM) / "Hybrid — rules + AI brain" (default).
2. **Status card** — model state: Not downloaded / Downloading x% /
   Ready (~1.35 GB) / RAM line: "Device RAM: X.X GB" + either "✓ AI brain
   supported" or the honest 4GB message.
3. **Download button** (hidden/disabled when `!aiModelRamOk` or already
   downloaded) with a **progress bar + Cancel** while downloading.
4. **"Import model file"** row (the Kaggle manual path) with a one-line
   explainer.
5. **Storage row** — "AI model: 1.35 GB" (via `sizeLabel`) + **Delete
   model** (confirm dialog; brain falls back to rules).
6. Optional: **"Custom model URL"** field → `AiBrainPrefs.customModelUrl`.

### Parent wiring checklist (BalochViewModel — NOT done by this task)

- [ ] Construct `LlmModelManager(appCtx)`, `RuleBasedBrain(...)` (existing),
      `LlmBrain(appCtx, modelManager) { historyProvider }`,
      `HybridBrain(rules, llm) { AiBrainPrefs(appCtx).brainMode }` and pass
      the HybridBrain to the Orchestrator (replacing the direct
      RuleBasedBrain).
- [ ] Implement the five `SettingsActions` callbacks above.
- [ ] `refreshSettings()` fills the six new `SettingsState` fields.
- [ ] `LlmBrain.close()` on ViewModel clear (or leave for process death —
      the engine is lazily recreated).
- [ ] Optional: wire `history` provider from chat messages for
      multi-turn context (last 3 turns used).

## 7. Verification notes (honest)

- Every MediaPipe call matches the **current official LLM Inference guide
  for Android** (ai.google.dev, checked 14 Oct 2026):
  `LlmInference.LlmInferenceOptions.builder().setModelPath()/.setMaxTokens()/.setMaxTopK()/.setTemperature()/.setRandomSeed()`,
  `LlmInference.createFromOptions(context, options)`,
  `generateResponse(prompt): String`, `close()`. (The brief said
  `LlmInference.create` — the real API is `createFromOptions`; corrected
  in code + header.)
- `com.google.mediapipe:tasks-genai:0.10.27` added to `app/build.gradle`
  (current guide version; repo's tasks-vision stays 0.10.14).
- Every settings callback maps 1:1 to a real `LlmModelManager` /
  `AiBrainPrefs` function written in this change.
- **Not compile-checked**: no Android SDK/Gradle in this environment, so
  the Kotlin is carefully hand-verified but not built. Recommend
  `./gradlew assembleDebug` on the build machine before merging; watch
  for: tasks-genai/tasks-vision transitive version alignment, and the
  `LlmInferenceOptions.Builder` method set in 0.10.27.
- `RuleBasedBrain.kt`, `Orchestrator.kt`, `BalochViewModel.kt`,
  `MainScreen.kt` untouched. `SettingsScreen.kt` edited **additively
  only** (import + KDoc + 6 state fields + 5 interface methods).
