# Mobile Baloch — UI/UX Zero-Mistake Audit Report

**Date:** 14 Oct 2026 ~08:30 PKT (Asia/Karachi)
**Auditor:** subagent (UI/UX audit lane)
**Scope:** every screen under `app/src/main/java/com/baloch/ai/ui/` **except** `MainScreen.kt` and `BalochViewModel.kt` (parent-owned, untouched).
**Identity:** Holographic Cyan preserved everywhere — no colour-scheme changes beyond contrast repairs.

## How this was done (honesty first)

- **Verified by reading code:** every layout structure, touch-target size (computed from padding + font metrics), contrast ratio (computed per WCAG formula), RTL directionality, string inventory, callback wiring, and draw-scope allocations.
- **NOT compiled:** this VM has no Android SDK/Gradle toolchain for the app module, so nothing was built. Syntax sanity-checked via brace/paren balance on all 15 touched files (all balanced); imports cross-checked for unused leftovers.
- **Eyeballed, not device-tested:** actual rendering at 320dp / 1.3x font scale / landscape, real Urdu & Hindi string lengths, and true 60fps frame timing. No emulator exists here. The parent should do one physical-device pass (small phone, Urdu active, 1.3x font, landscape) before v1.0 ships.

## Summary

| # | Fix | Files |
|---|-----|-------|
| 1 | New shared nav chrome: 48dp RTL-mirrored back buttons | `ui/components/NavChrome.kt` (new) |
| 2 | Back chevrons upgraded to 48dp + auto-mirror for Urdu RTL | Schedule, Rules, CallLog, Memory*, VoiceLockSetup* |
| 3 | "◀ BACK" text buttons upgraded to 48dp + auto-mirror | VoiceSettings, GestureLab, Settings |
| 4 | Delete "✕" hit areas raised to 48dp | Schedule, Rules |
| 5 | ENABLE pill (Caller ID) raised to 48dp height | CallLogScreen |
| 6 | Segmented options (language/mode/frequency/gesture tabs) raised to 48dp | SettingsScreen |
| 7 | ModeToggle track 76×38dp → 76×48dp (pill radius 19→24dp) | ChatComponents |
| 8 | Contrast repairs: 6 sub-4.5:1 text colours → BalochTextDim (7.7:1) | Schedule, VoiceSettings, CallLog, GestureLab, Settings |
| 9 | Tier-filter Row → LazyRow (overflowed at 320dp) | MemoryScreen |
| 10 | Rule chips Row → FlowRow (wrapped; "REQUIRE CONFIRM" clipped at 320dp) | RulesScreen |
| 11 | AgentChipRow → horizontally scrollable (6 chips overflowed everywhere) | ChatComponents |
| 12 | Header titles + date/command lines: maxLines/ellipsis guards | Schedule, Rules, CallLog, Memory, VoiceSettings, GestureLab, VoiceLockSetup |
| 13 | Voice-picker rows: name ellipsis + "● LOCKED" single-line | VoiceSettingsScreen |
| 14 | PrivacyOnboarding: Box+scroll+Center so landscape/1.3x scrolls instead of clipping | PrivacyOnboarding |
| 15 | SplashScreen: adaptive canvas via BoxWithConstraints (260dp clipped in landscape) | SplashScreen |
| 16 | `imePadding()` on every screen with a text field | Memory, VoiceLockSetup, Settings |
| 17 | Optional `onBack` (null-safe, no dead chrome) | MemoryScreen, VoiceLockSetupScreen |
| 18 | IceContactRow: `modifier` param kills 30dp double-inset in Settings | ProSettingsRows, SettingsScreen |
| 19 | Memory search made case-insensitive (was silently missing matches) | MemoryScreen |
| 20 | `bmp.asImageBitmap()` hoisted with `remember(bmp)` (was re-wrapping every camera frame) | GestureLabScreen |
| 21 | Splash draw-scope `listOf` allocations hoisted to top-level vals | SplashScreen |
| 22 | `// TODO(parent)` seam note on VoiceLockSetupActions no-op defaults | VoiceLockSetupScreen |
| 23 | SplashScreen `tagline` param — L3 wiring seam for trilingual splash | SplashScreen |
| 24 | NEW: `ui/FeatureTour.kt` — first-run tour | (new) |
| 25 | NEW: `ui/components/TipsCard.kt` — dismissible tip card | (new) |

---

## Per-file findings

### ui/components/NavChrome.kt (NEW)
Shared back-navigation chrome so the five screens stop hand-rolling under-sized, LTR-only back buttons.
- `BackChevron(onBack)` — 48×48dp hit box, chevron auto-mirrors (`‹` LTR / `›` RTL via `LocalLayoutDirection`).
- `BackLabel(onBack)` — "◀ BACK" / "BACK ▶" text variant, `minimumInteractiveComponentSize()` (48dp floor) + RTL mirroring.

### ui/ScheduleScreen.kt
1. **Touch target** (`:69`): back "‹" was ~38×46dp → replaced with `BackChevron` (48dp).
2. **RTL** (`:69`): chevron was hardcoded LTR `‹` → now mirrors for Urdu.
3. **Overflow** (`:72-80`): "◈ SCHEDULED TASKS" had no truncation; at 320dp + 1.3x scale it clipped → `maxLines=1` + `Ellipsis`.
4. **Overflow** (`:144`): date line (`whenText`) could wrap and push the recurrence badge → `maxLines=1` + `Ellipsis`.
5. **Overflow** (`:173`): weekly-days line could wrap → single-line + ellipsis.
6. **Contrast** (`:170`): weekly-days used `BalochCyanDim` (3.56:1, fails 4.5:1) → `BalochTextDim` (7.7:1). Unused `BalochCyanDim` import removed.
7. **Touch target** (`:221`): delete "✕" was ~35×35dp → `defaultMinSize(48.dp, 48.dp)`.

### ui/MemoryScreen.kt
1. **Dead-end UX** (`:107`): the screen had **no back affordance at all** — if the parent navigates to it, Kamran is trapped (system-back only). Added optional `onBack: (() -> Unit)? = null`; back chrome renders only when the parent supplies it (never a dead button).
2. **Overflow** (`:207`): tier-filter `Row` ("All tiers / 📌 Pinned / 🕐 Temporary") measured ~330dp — overflowed a 320dp screen with no scroll → converted to `LazyRow` (categories row already was one).
3. **Functional bug** (`:122-123`): search lowercased the query but compared against raw `f.key`/`f.value`/`f.category` — "favourite" never matched "Favourite". Now lowercases both sides.
4. **Keyboard** (`:133`): search field had no IME awareness → added `.imePadding()` (needs the manifest `adjustResize` — see TODO(parent) #1).
5. **Overflow** (`:138-160`): new header title gets `maxLines=1` + ellipsis.

### ui/RulesScreen.kt
1. **Touch target + RTL** (`:84`): back chevron → `BackChevron` (48dp, mirrored).
2. **Overflow** (`:93`): "◈ MY RULES" → single-line + ellipsis.
3. **Touch target** (`:216`): delete "✕" was ~35×31dp → `defaultMinSize(48.dp, 48.dp)`.
4. **Overflow** (`:224-232`): scope/constraint/DEFAULT chips in a fixed `Row` clipped at 320dp ("REQUIRE CONFIRM" ≈ 130dp + scope ≈ 70dp + DEFAULT ≈ 80dp + spacing > 288dp available) → `FlowRow` with 8dp spacing (wraps to two lines; `@OptIn(ExperimentalLayoutApi::class)` on `RuleRow`).

### ui/VoiceSettingsScreen.kt
1. **Touch target + RTL** (`:92`): "◀ BACK" text was ~90×18dp hit area → `BackLabel` (48dp floor, mirrored).
2. **Overflow** (`:99`): "◈ BALOCH VOICE" (20sp) at 1.3x overflowed 320dp next to the back label → `weight(1f)` + `maxLines=1` + ellipsis.
3. **Overflow** (`:223, :234`): TTS voice names (long, incl. Urdu-script names) could push the "● LOCKED" badge off-screen → name `maxLines=1` + ellipsis, badge `maxLines=1`.
4. **Contrast** (`:141`): "Language: …" used `BalochCyanDim` (3.56:1) → `BalochTextDim`. Unused import removed.

### ui/VoiceLockSetupScreen.kt
1. **Dead-end UX** (`:110-113`): no back affordance → optional `onBack`, same null-safe contract as MemoryScreen.
2. **Keyboard** (`:119`): PIN fields mid-scroll → `.imePadding()`.
3. **Dead-button guard** (`:87`): `VoiceLockSetupActions` ships no-op defaults (`= {}`) — correct for previews, but a dead tap if the parent forgets to wire. Added an explicit `// TODO(parent)` block on the data class. Graceful states already exist (RECORD only when recordable, SAVE PIN disabled until 4 digits, Voice Lock toggle disabled until enrolled) — left intact.
4. **Overflow** (`:127`): new header title single-line + ellipsis.

### ui/ProSettingsRows.kt
1. **Double-inset** (`:74`): `IceContactRow` hardcoded `padding(horizontal=16.dp)` inside its Card; embedded in SettingsScreen's already-padded (14dp) section it sat at a 30dp inset vs 14dp for sibling rows. Added `modifier: Modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)` — standalone callers keep the old look, SettingsScreen now passes bare `Modifier`. Placement KDoc updated.

### ui/SettingsScreen.kt
1. **Touch target + RTL** (`:311`): "◀ BACK" → `BackLabel`.
2. **Touch targets** (`:763`): `SegmentedRow` options were 36dp tall (12sp + 20dp padding) → `vertical = 16.dp` padding = 48dp. Applies to language, mode, check-in frequency, and gesture-tab segments.
3. **Contrast** (`:390`): MANUAL/JARVIS description used `BalochCyanDim` (3.56:1) → `BalochTextDim`. (`BalochCyanDim` import kept — still used by the GRANT button at `:845`.)
4. **Double-inset** (`:478`): EMERGENCY section passes bare `Modifier` to `IceContactRow` (see ProSettingsRows #1).
5. **Keyboard** (`:304`): root scroll column → `.imePadding()` (covers the embedded ICE phone field).

### ui/CallLogScreen.kt
1. **Touch target + RTL** (`:175`): back chevron → `BackChevron`.
2. **Overflow** (`:182`): "◈ CALLER ID" → single-line + ellipsis.
3. **Touch target** (`:268`): ENABLE pill was ~78×34dp → `defaultMinSize(minHeight = 48.dp)` (pill grows to 48dp tall; still holographic).
4. **Contrast** (`:282`, `:256`): detection line ("Could not place this number") and the Android 8.x screener note used `BalochCyanDim` (3.56:1) → `BalochTextDim`. Unused import removed.
5. **Overflow** (`:335`): detection line → `maxLines=2` + ellipsis (long "network · city" strings).

### ui/GestureLabScreen.kt
1. **Touch target + RTL** (`:131`): "◀ BACK" → `BackLabel`.
2. **Overflow** (`:138`): "◈ GESTURE LAB" → `weight(1f)` + ellipsis.
3. **Contrast** (`:262`): the "Try: open palm hold 1s → …" hint used **`BalochPanelBorder` — 1.46:1, effectively invisible** → `BalochTextDim` (7.7:1). Unused `BalochPanelBorder` + `clickable` imports removed.
4. **60fps** (`:197`): `bmp.asImageBitmap()` re-wrapped the bitmap on every camera-frame delivery (~30fps) → hoisted with `remember(bmp)`. `HAND_CONNECTIONS` was already a top-level val; the skeleton Canvas allocates nothing per frame (Offset/Size are value classes).

### ui/PrivacyOnboarding.kt
1. **Landscape / 1.3x** (`:59-70`): the centered `Column` was not scrollable — in landscape (~360dp tall) the shield + title + promise + 3-bullet card + button + footer (~700dp+) clipped with no way to reach the accept button. Restructured to `Box(verticalScroll, contentAlignment=Center)` + inner `Column`: scrolls when it overflows, stays optically centered when it fits. (Strings were already fully L3 — no copy work needed.)

### ui/SplashScreen.kt
1. **Landscape** (`:118`): the 260dp wireframe canvas + BALOCH + tagline + footer (~420dp) clipped in landscape. Now `BoxWithConstraints` with `canvasSize = minOf(260.dp, maxWidth*0.7f, maxHeight*0.42f)` — portrait keeps ~252dp (≈ original), landscape shrinks proportionally.
2. **60fps** (`:52-54`): `listOf(0.92f, 0.72f, 0.54f)` was allocated inside the draw scope every frame, plus jitter lists in composition → hoisted to top-level `RING_FRACS`, `JITTER_X`, `JITTER_ALPHA`. (`Color.copy(alpha=…)` in the draw scope is allocation-free — Color is an inline value class — so the rings/ticks are fine.)
3. **Trilingual seam** (`:68`): added optional `tagline: String = TAGLINE` param; the parent can pass the L3-picked line. Default keeps current behaviour. Reduced-motion path, tap-to-skip, and the double-`onDone` guard untouched.

### ui/components/ChatComponents.kt
1. **Overflow** (`:276`): `AgentChipRow` was a fixed `Row` — six agent chips (~600dp) overflowed on every phone → `modifier.horizontalScroll(rememberScrollState())` (parent-supplied modifier preserved).
2. **Touch target** (`:391`): `ModeToggle` track was 76×38dp → 76×48dp, thumb 30→38dp, pad 4→5dp, pill radius 19→24dp. Visual identity kept (same glow/border/spring), now meets the 48dp floor.
3. No per-frame allocations found: the chat edge-sweep `Brush` and `glowPress` radial brush are both hoisted per size (pre-existing comments confirmed accurate). The `translate(left=…)` sweep is canvas-absolute (LTR sweep in RTL locales) — cosmetic shimmer, left as-is.

### ui/components/TipsCard.kt (NEW)
`@Composable fun TipsCard(tip: String, onDismiss: () -> Unit, modifier: Modifier = Modifier, lang: BalochLanguage = BalochLanguage.ENGLISH)` — holographic card (BalochPanel, 45%-alpha cyan border, "💡" + kicker + wrapping tip text), 48dp circular dismiss target, kicker trilingual via L3. Parent passes the already-localised tip string and owns dismissed state.

### ui/FeatureTour.kt (NEW)
First-run tour: 4 swipeable `HorizontalPager` cards (🎙️ Voice + wake word / 🧠 Memory / 👆 Gestures / 🛡️ Privacy fortress), all copy fully L3 (en/ur/hi, en-GB), holographic card styling, Skip (48dp `TextButton`) + Next/Get-started bottom button, page dots + "n / 4" counter. `fun FeatureTour(lang: BalochLanguage, onFinish: () -> Unit)` — shown once; **the parent gates on a prefs flag** (e.g. `feature_tour_seen`) and handles the write. Cards use wrapping text (no truncation — tour copy is meant to be read); pager handles RTL page direction automatically.

---

## TODO(parent) — things I could not / should not do from this lane

1. **Manifest: `android:windowSoftInputMode="adjustResize"`** on `MainActivity` (`app/src/main/AndroidManifest.xml:62-64`). Currently unset (defaults to `adjustPan`), which means the `.imePadding()` I added to Memory/VoiceLockSetup/Settings is inert until this is set. One-line change, outside my ui/ scope — needs the parent.
2. **VoiceLockSetupActions wiring** (`ui/VoiceLockSetupScreen.kt:87`): the no-op defaults are marked `// TODO(parent)`. The parent must wire `VoiceLockManager` / `PinManager` / `BiometricLock`; otherwise enrollment taps silently do nothing in production.
3. **Trilingual string pass (item 10):** every user-visible string outside `PrivacyOnboarding.kt`, `FeatureTour.kt`, `TipsCard.kt` and the brain `Replies` is hardcoded English. Full inventory below. The systematic fix is threading `lang: BalochLanguage` (or a `UiStrings` holder) through the screen signatures — a parent-level API decision I did not want to make unilaterally across 12 parent-wired screens. Flagged, not rewritten.
4. **Device pass:** one physical-device check (320dp-class phone, Urdu active, 1.3x font scale, landscape) before v1.0 — I have no emulator/SDK here.
5. **Compile:** not compiled here (no Android SDK in this VM). Brace/paren balance verified on all 15 files; imports audited. Please build before merging.

---

## Item 10 — hardcoded English-only strings (flagged per checklist)

en-GB spelling verified clean: no US spellings ("customize", "optimize", "behavior", …) in any user-visible string; "BEHAVIOUR" is correctly en-GB. Trilingual gaps:

- **ScheduleScreen.kt** — "◈ SCHEDULED TASKS", "No scheduled tasks, sir.", "ENABLED"/"PAUSED", recurrence names (`Recurrence.name`: ONCE/DAILY/WEEKLY), `shortDay()` Mon–Sun.
- **MemoryScreen.kt** — "TOTAL MEMORY", "📌 pinned · 🕐 temporary · N people", "Search memory…", category names, "All tiers"/"📌 Pinned"/"🕐 Temporary", "PEOPLE & PLACES", "Nothing here yet, sir. …", "⬆ Backup"/"⬇ Restore", "🗑 Forget everything", all three dialog copy blocks.
- **RulesScreen.kt** — "◈ MY RULES", subtitle, empty-state example, "Delete safety default?" dialog, "DELETE"/"KEEP", `scope.name` / `constraint.name` chip text, quiet-hours line.
- **VoiceSettingsScreen.kt** — "◈ BALOCH VOICE", subtitle, "CURRENT VOICE", "— not set yet —", "Language: …", "▶ PREVIEW — "I am Baloch, sir."", re-match note, "CHOOSE VOICE (…)", "No voices found…", "● LOCKED", "VOICE VOLUME", "Loud by default — …", "Default 100% (FULL)…", lock footnote.
- **VoiceLockSetupScreen.kt** — "VOICE LOCK", subtitle, ""Hey Baloch, it is me"", "ENROLLMENT SAMPLES (n/3)", "Sample n", "Captured"/"Recording…"/"Waiting", "Listening… say the phrase now.", "✓ Voiceprint stored on this device only.", "FALLBACK PIN", "✓ PIN is set…", "4-digit PIN"/"Confirm PIN", "UPDATE PIN"/"SAVE PIN", Voice Lock + biometric titles/subtitles, "Finish enrollment to arm the lock.", "HONEST LIMITS — …", "RECORD".
- **ProSettingsRows.kt** — "🚨 Emergency contact", description, "+92 3XX XXXXXXX" placeholder, "Save"/"Saved ✓".
- **SettingsScreen.kt** — all section titles/descriptions, toggle titles/subtitles, nav-row titles/subtitles, segmented labels (Off/Subtle/Normal, MANUAL/JARVIS, 📷 Camera/📳 Sensor), language display names (BalochLanguage.displayName — Urdu/Hindi names already native script ✓), permission titles + why-texts, "GRANT"/"✓ GRANTED", status strings ("ACTIVE"/"LOW LIGHT"/…), "Baloch v0.1 — Built by the Baloch Factory for Kamran.", "Holographic Cyan edition. Your data stays yours."
- **CallLogScreen.kt** — "◈ CALLER ID", subtitle, "Call screener" + status lines, "ENABLE", Android 8.x note, permission prompt, empty-state line, "SPAM"/"BLOCKED", "Could not place this number", "Unblock"/"Block"/"Report spam"/"Look up".
- **GestureLabScreen.kt** — "◈ GESTURE LAB", subtitle, calibration banner + "CALIBRATE NOW — PALM 5s + FIST 5s", "Starting camera…", "Camera view for gesture detection" (content description), "STATUS"/"DETECTED"/"PIPELINE", physics note, "Try: …" line.
- **SplashScreen.kt** — "BALOCH", "Systems online, sir." (now an overridable `tagline` param — parent can pass L3), "MAGSI DIGITAL · BALOCH OS".
- **ChatComponents.kt** — "YOU"/"◈ BALOCH", "Confirm, sir?", "CONFIRM"/"Cancel", "MANUAL"/"JARVIS".
- **NavChrome.kt** (new) — "◈ BACK" handled; literal is "◀ BACK"/"BACK ▶" (English word "BACK" — needs the same lang-threading decision as everything above).
- **Clean:** PrivacyOnboarding.kt, FeatureTour.kt (new), TipsCard.kt (new), brain/Replies — fully L3.

## Dead buttons — verdict

No dead buttons remain in the audited scope. Every `onClick` resolves to a parent-wired callback (all documented in KDoc contracts), except the two intentional seams:
- `VoiceLockSetupActions` no-op defaults → marked `// TODO(parent)` (`VoiceLockSetupScreen.kt:87`); graceful disabled states already in place (RECORD only when recordable, SAVE PIN gated on 4 digits, Voice Lock toggle disabled until enrolled).
- `MemoryScreen`/`VoiceLockSetupScreen` `onBack: (() -> Unit)? = null` → back chrome only renders when the parent supplies it; null shows nothing (never a dead tap).

## 60fps notes

- **OrbView.kt** (out of scope — report only): per-frame `Color.copy(alpha=…)` calls are allocation-free (Color is an inline value class); motes list is `remember`-hoisted. Nothing alarming spotted on read.
- **SplashScreen:** hoisted the per-frame `listOf` allocations (done, `:52-54`); ring/tick drawing is allocation-free.
- **GestureLabScreen:** `asImageBitmap()` hoisted with `remember(bmp)` (done, `:197`).
- **ChatComponents:** edge-sweep and `glowPress` brushes already hoisted per size — confirmed, no change needed.

## Contrast audit (computed, WCAG)

| Colour on BalochBlack (#010507) | Ratio | Verdict |
|---|---|---|
| BalochText #E4FBFF | ~15:1 | ✓ body |
| BalochTextDim #7FA3AC | ~7.7:1 | ✓ body — the standard dim colour |
| BalochCyan #00E5FF | ~13.6:1 | ✓ |
| BalochCyanBright #80F5FF | ~15:1 | ✓ |
| BalochAmber #FFB300 | ~11.7:1 | ✓ |
| BalochRed #FF5252 | ~6.6:1 | ✓ |
| BalochCyanDim #0E6E7E | **~3.56:1** | ✗ was used for 11sp/13sp body text in 5 screens → replaced with BalochTextDim |
| BalochPanelBorder #0F2E36 | **~1.46:1** | ✗ was used for the GestureLab "Try:" hint → replaced with BalochTextDim |

`BalochCyanDim` remains in the theme for borders/status dots (non-text use is fine). One design note left untouched: inactive `ModeLabel`s render at 38% alpha (~2.9:1) — acceptable as an inactive-state label, not body text.

## RTL audit

- `supportsRtl="true"` is set in the manifest ✓.
- No absolute `padding(left/right)`, `TextAlign.Left/Right`, or `Arrangement.Absolute` anywhere in ui/ — all directional APIs (`start/end`, `horizontal`) were already correct.
- Back navigation was the RTL gap: hardcoded `‹` / `◀ BACK` everywhere → now mirrored via `LocalLayoutDirection` in `NavChrome.kt`, adopted by all 7 screens that have back chrome.
- Urdu "اردو" / Hindi "हिन्दी" labels (language segmented row) shape correctly as RTL runs inside their Text composables; no bidi isolation issues in the inspected rows.

## Files changed / added

Changed: `ui/ScheduleScreen.kt`, `ui/MemoryScreen.kt`, `ui/RulesScreen.kt`, `ui/VoiceSettingsScreen.kt`, `ui/VoiceLockSetupScreen.kt`, `ui/ProSettingsRows.kt`, `ui/SettingsScreen.kt`, `ui/CallLogScreen.kt`, `ui/GestureLabScreen.kt`, `ui/PrivacyOnboarding.kt`, `ui/SplashScreen.kt`, `ui/components/ChatComponents.kt`.
Added: `ui/components/NavChrome.kt`, `ui/FeatureTour.kt`, `ui/components/TipsCard.kt`.
Untouched per instructions: `ui/MainScreen.kt`, `ui/BalochViewModel.kt`, `ui/components/OrbView.kt`, `ui/components/ScanlineOverlay.kt`, `ui/theme/BalochTheme.kt`. (`ui/LifeSections.kt` / `ui/PowerSections.kt` do not exist — skipped.)
