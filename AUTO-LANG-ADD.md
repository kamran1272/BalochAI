# AUTO-LANG-ADD.md — Auto Language Detection (per-utterance)

Kamran's order (Oct 14 2026 ~08:55 PKT): *"khud detect kare main kaunsi
language bol raha hun"* — Baloch detects the language of EACH utterance
himself and replies in that language, including mid-conversation switching.
"whatsapp kholo" → Urdu reply, then "set an alarm" → English reply, no manual
toggle needed.

Status: **NEW** — detector implemented (`brain/LanguageDetector.kt`);
integration snippets below are for the parent to apply (Orchestrator.kt and
BalochViewModel.kt were deliberately NOT edited).

## Algorithm (3 stages)

```
input: "व्हाट्सऐप खोलो"
  Stage 1 SCRIPT: Devanagari chars found (U+0900–U+097F) → HINDI, conf 0.95
input: "whatsapp kholo"
  Stage 1 SCRIPT: no script chars → fall through
  Stage 2 ROMAN: urduScore=0.5 (kholo), engScore=0.0 → margin 0.5 ≥ 0.15
                 winner URDU, conf 0.5 ≥ 0.5 → URDU ("roman-markers")
input: "ok"
  Stage 2 ROMAN: no marker hits → margin 0 < 0.15 → Stage 3
  Stage 3 FALLBACK: silent → user's manual setting (default ENGLISH)
```

**Stage 1 — SCRIPT:** any char in `'\u0600'..'\u06FF'` → URDU (conf 0.95);
any in `'\u0900'..'\u097F'` → HINDI (conf 0.95). Mixed script → majority
count wins. An exact tie falls through to Stage 2 (effectively fallback).

**Stage 2 — ROMAN scoring:** Latin input is lowercased, tokenised on
`[^a-z]+`, and scored against two marker sets. Score = marker hits ÷ word
count, per side. `margin = |urduScore − engScore|`; the winner needs
`margin ≥ 0.15` to count as a decision. Confidence = the margin.

**Stage 3 — FALLBACK:** confidence < 0.5 (or blank/wordless input) returns
the user's manual setting, silently. Baloch NEVER asks "which language?" —
a wrong guess costs more than staying on his default.

## Marker lists

`ROMAN_URDU` (59): hai, hain, ho, kya, ka, ki, ke, ko, mein, me, ne, se,
kaise, kaisa, acha, nahi, nahin, mujhe, tumhe, apko, bhai, sir, kar, karna,
karo, kare, dena, lena, batao, sunao, kholo, band, lagao, utha, rakhna,
bhool, yaad, dilao, kitni, kitna, kaun, kahan, kab, abhi, roz, subah, shaam,
raat, aaj, kal, wala, wali, theek, bas, phir, aur, lekin, magar, ya.

`ENGLISH_MARKERS` (39): the, a, an, is, are, was, were, will, would, can,
could, should, what, when, where, which, how, do, does, did, have, has, had,
my, your, his, her, their, this, that, these, those, for, from, with, about,
please, thanks, sir.

Known marker caveat: "sir" is in BOTH sets, so it hits both sides and washes
out of the margin (documented in the KDoc — shared words can't decide).

## Confidence policy

| Stage | Result | Confidence | Method string |
|---|---|---|---|
| Script | URDU / HINDI | 0.95 | `"script"` |
| Roman, margin ≥ 0.5 | winner | = margin (0.5–1.0) | `"roman-markers"` |
| Roman, 0.15 ≤ margin < 0.5 | manual setting | = margin | `"fallback"` |
| Roman, margin < 0.15 | manual setting | = margin | `"fallback"` |
| Blank / no latin words | manual setting | 0.0 | `"fallback"` |

The 0.5 floor means weak winners ("mera naam batao" — only *batao* hits,
margin 0.33) fall back instead of committing on one word.

## Settings contract

- Toggle: **Auto language detect**, SharedPreferences `"baloch_settings"`,
  key `auto_lang_detect`, **default TRUE**.
- API (on `LanguageDetector`):
  - `fun isAutoDetectEnabled(context: Context): Boolean`
  - `fun setAutoDetectEnabled(context: Context, enabled: Boolean)`
- LanguageManager.kt is UNTOUCHED — manual selector API unchanged; the
  manual setting is the fallback language and still drives voice recognition
  locale, welcome message, and everything outside the per-turn reply.

## Integration (parent applies — Orchestrator.kt untouched)

`Orchestrator.run()` reads `val lang = ctx.language` (line ~101) — nothing
to change there; detection flows through `ctx`.

**Hook A — ViewModel.process()** (BalochViewModel.kt, ~line 355): replace
`val lang = languageManager.current()` with the 5-line snippet. `prefs` is
the ViewModel's existing `"baloch_settings"` SharedPreferences (line 127).
Add import `com.baloch.ai.brain.LanguageDetector`.

```kotlin
val manual = languageManager.current()
val lang = if (prefs.getBoolean("auto_lang_detect", true))
    LanguageDetector.detect(userText, manual).language else manual
val ctx = deviceActions.currentContext(lang)
```

**Hook B — TTS per-turn:** in the same `process()` coroutine, inside
`withContext(Dispatchers.Main)` BEFORE `say(result.reply)`, add
`voiceOutput.setLanguage(lang)` so the spoken voice follows the detected
language (the existing `collect { voiceOutput.setLanguage(...) }` only
tracks the manual selector).

**Voice input note:** voice already carries a locale —
`startVoice()` calls `voiceInput.startListening(lang = languageManager.current())`
(~line 316). Detection still runs on the transcript and can correct a wrong
locale (Kamran speaks Roman Urdu while recognition runs in en-US). If a
perceived clash ever appears, restrict detection to the typed path
(`sendText()`) — for now it runs for both.

**Hands-free note (optional, BalochApp.handsFreeContext ~line 150):** the
locked-screen pipeline builds `DeviceContext` straight from
`LanguageManager.current()`. Detection could be added there per incoming
command; left as a parent decision — the chat path is the requirement.

**Settings UI:** bind a toggle switch to `isAutoDetectEnabled(context)` /
`setAutoDetectEnabled(context, on)` in SettingsScreen (ProSettingsRows.kt
holds the pro toggles — natural home).

## Test examples

| Input | Expected | Via |
|---|---|---|
| `"whatsapp kholo"` | URDU, conf 0.5 | roman-markers (kholo) |
| `"open whatsapp"` | ENGLISH | fallback (no marker hits; manual default English). **Flag:** if the user sets the manual language to Urdu, this resolves Urdu. Candidate follow-up: add imperative verbs like "open" to the English set. |
| `"व्हाट्सऐप खोलो"` | HINDI, conf 0.95 | script (Devanagari) |
| `"واٹس ایپ کھولو"` | URDU, conf 0.95 | script (Arabic block) |
| `"ok"` | manual default | fallback (inherently ambiguous — by design) |
| `""` / whitespace | manual default, conf 0 | fallback |
| `"battery kitni hai"` | URDU | roman-markers (kitni + hai: margin 0.67) |
| `"what is the battery"` | ENGLISH | roman-markers (what/is/the: margin 0.75) |
| `"mera naam batao"` | manual default | fallback — margin 0.33 < 0.5 floor. Honest limit: one-marker sentences don't clear the bar. |

## Known limits (by design)

- Latin-script Hindi is scored as URDU — the marker sets can't separate
  Latin Hindustani, and project convention treats Roman Urdu as the Latin
  channel. Hindi is detected via Devanagari script.
- Compile-check NOT done: no kotlinc or Gradle wrapper exists in this
  environment (see builder report). Pure Kotlin + Android Context only —
  no new dependencies.
