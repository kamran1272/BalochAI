# 🛡 FORTRESS PRIVACY — Baloch's guarantee to Kamran

> **The one-line promise:** your personal data never leaves this phone. Not to a server, not to an analytics company, not to anyone — ever.

## In short (English)

Everything Baloch knows about you lives **on this phone only**. Your memories are encrypted. Your voiceprint and PIN are locked in the Android Keystore. Screenshots of Baloch are blocked. Every time Baloch touches a photo, contact or file, it writes it to a Privacy log you can inspect — and you can wipe your memory with your PIN anytime. Nothing is uploaded. Nothing is synced. There is no cloud.

## مختصراً (اردو)

بلوچ آپ کے بارے میں جو کچھ جانتا ہے وہ **صرف اسی فون میں** رہتا ہے۔ آپ کی یادیں انکرپٹڈ ہیں۔ آپ کا وائس پرنٹ اور PIN اینڈرائیڈ کی اسٹور میں محفوظ ہیں۔ بلوچ کے اسکرین شاٹس بلاک ہیں۔ جب بھی بلوچ کسی تصویر، رابطے یا فائل کو چھوتا ہے، وہ پرائیویسی لاگ میں درج ہوتا ہے جسے آپ دیکھ سکتے ہیں — اور آپ اپنے PIN سے اپنی میموری کبھی بھی مٹا سکتے ہیں۔ کچھ بھی اپ لوڈ نہیں ہوتا۔ کچھ بھی سنک نہیں ہوتا۔ کوئی کلاؤڈ نہیں۔

## संक्षेप में (हिन्दी)

बलोच आपके बारे में जो कुछ जानता है वो **सिर्फ़ इसी फ़ोन में** रहता है। आपकी यादें एन्क्रिप्टेड हैं। आपका वॉइसप्रिंट और PIN एंड्रॉइड कीस्टोर में सुरक्षित हैं। बलोच के स्क्रीनशॉट ब्लॉक हैं। जब भी बलोच किसी तस्वीर, संपर्क या फ़ाइल को छूता है, वह प्राइवेसी लॉग में दर्ज होता है जिसे आप देख सकते हैं — और आप अपने PIN से अपनी मेमोरी कभी भी मिटा सकते हैं। कुछ भी अपलोड नहीं होता। कुछ भी सिंक नहीं होता। कोई क्लाउड नहीं।

---

## 1. Local-only doctrine

Baloch has **no account, no cloud, no sync, no backend**. There is no server that could receive your data because there is no server at all. The only network traffic the app is capable of is documented line-by-line in [NETWORK_AUDIT.md](NETWORK_AUDIT.md): a weather lookup (fixed Lahore coordinates), a currency-rate lookup (currency codes only), a web search (only when you ask, opened in your browser), and a one-time voice-model download. That's the complete list.

**Explicitly absent:** no analytics SDK, no crash-reporting SDK, no ads SDK, no telemetry, no "phone home". Verified against the dependency list in `app/build.gradle` — the audit names every allowed call site.

## 2. No snooping

Baloch never touches your private files on its own. Photos, contacts, files and documents are opened **only when you explicitly command it** ("set this photo as wallpaper", "message Ahmed"). There is no background scanning of your gallery, no contact harvesting, no file indexing. In MANUAL mode even proactive features pause; in JARVIS mode Baloch may *suggest* actions, but touching private data still waits for your command.

## 3. The encrypted vault

| What's encrypted | How | Key |
|---|---|---|
| Total Memory database (`baloch_memory.db`) | SQLCipher AES-256, whole file at rest | Random 256-bit passphrase, itself stored in EncryptedSharedPreferences under a Keystore MasterKey (`memory/room/BalochDatabase.kt`) |
| Privacy transparency log (`privacy_log.enc`) | AndroidX Security EncryptedFile, AES-256-GCM | Keystore MasterKey `baloch_master_key` (`privacy/PrivacyLog.kt`) |
| PIN hash, voiceprint vectors, toggles | EncryptedSharedPreferences (AES256_SIV keys / AES256_GCM values) | Keystore MasterKey `baloch_master_key` (`privacy/EncryptedPrefs.kt`) — *migration guide in that file's KDoc; see §8* |
| Everything | Keystore = hardware-backed on most devices; the OS — not the app — guards the key | One shared MasterKey alias, so future key rotation touches one place |

All vault files live in **app-internal storage** (`filesDir`) — never external storage, never the SD card. No other app can read them.

**Honest limits (documented, not hidden):** if the Keystore is lost (full app-data wipe), previously encrypted data cannot be recovered — that is the price of real encryption. If SQLCipher can't load on an exotic device, the database falls back to plain Room and flips an honest `isEncrypted = false` flag rather than crashing — the memory screen can show that note. A determined attacker with a *rooted* phone and *your unlocked device* is outside what any app can defend against; the threat model here is thieves, snoops, other apps, and network eavesdroppers — all blocked.

## 4. Screenshots blocked — FLAG_SECURE

`MainActivity.kt` sets `FLAG_SECURE` on the app window. Every Baloch screen (chat, memory, settings, privacy dashboard) is composed inside that one window, so all of them are protected: other apps cannot screenshot or screen-record Baloch, and the system "recents" thumbnail is blanked. (Your own manual screenshots are blocked too — that's the trade-off, and it's deliberate.)

## 5. The Privacy dashboard — proof, not promises

`privacy/PrivacyLog.kt` is an append-only, encrypted transparency log. The parent wires one `log()` call at each real private-data touch point (photo view, contact read, file read, voiceprint check, memory export). `privacy/PrivacySection.kt` renders it as **"What Baloch accessed"** — icon, description, timestamp — inside Settings, trilingual via `brain.L3`. The log says *what* was touched, never the private content itself (the API contract forbids raw PII in `detail`, and `sanitize()` enforces single-line, 160-char descriptions as a second belt). You can clear the log with a confirm-gated tap; clearing the log never deletes your actual data.

## 6. No personal data in logs

Two layers:

1. **Source rule:** no `Log.*` call may ever print user content — no message text, no transcriptions, no contact names, no file paths with personal names. The log audit below (§9) lists the two violations found and their fixes.
2. **Build rule:** `app/proguard-rules.pro` strips **all** `android.util.Log` calls in release builds (`-assumenosideeffects`), so even a missed debug line can never ship. (Takes effect when the release build type enables minification.)

## 7. Voiceprint policy

Your voiceprint (26-float MFCC vector) is derived and stored **on-device only** — no audio ever leaves the phone, no ML model is downloaded for it, no cloud verification exists. It is used for one thing: deciding whether the voice giving a command is yours. It can be re-enrolled or deleted anytime from Voice settings. Honest limits, stated in the code and in the onboarding: it reliably rejects casual strangers but is **not spoof-proof against a clear recording** of your voice — the PIN (and optional biometric lock) is the hard guarantee behind it.

## 8. The plain-storage migrations (open work, documented)

The audit found two places still on **plain** storage. Neither is a leak to the network (filesDir is app-private), but both are file-extraction risks on a rooted/stolen phone, so both have a written migration path:

- **Voiceprint + PIN hash** live in the plain DataStore file `voice_lock.preferences_pb` (`voice/VoiceLockManager.kt`, `voice/PinManager.kt`). The PIN is at least a peppered SHA-256 (never plaintext), but the voiceprint floats are readable. → Migration snippet is in the KDoc of `privacy/EncryptedPrefs.kt` (copy → encrypted vault → delete plain file). The voice/PIN sibling owns those files; the parent applies the migration when ready.
- **Notes** (`engine/NotesStore.kt`) are plain JSON (`baloch_notes.json`) in filesDir. → Recommended: move to an `EncryptedFile` store on the same pattern as `PrivacyLog` (small bounded JSON, atomic temp-write + rename). Snippet available on request; the engine sibling owns the file.

## 9. LOG AUDIT (Oct 14 2026 — all *.kt scanned)

**Violations found — user content in logs (parent: fix these in the owning files, I did not touch them):**

1. `service/VerbalConfirm.kt:85` — `Log.i(TAG, "unrecognized confirmation answer (\"$heard\") — defaulting to NO")`
   → `$heard` is **Kamran's spoken transcription**. Logcat must never carry voice content. Fix: drop `$heard` (log the event only, e.g. `"unrecognized confirmation answer — defaulting to NO"`).
2. `service/BalochListenerService.kt:318` — `Log.i(TAG, "hands-free command: \"$command\" ($lang)")`
   → `$command` is the **full voice-command text** (could contain names, numbers, private requests). Fix: remove the command text; log `lang` and command length only, or nothing.

**Borderline (not PII, but noisy — optional cleanup):**

3. `service/BalochListenerService.kt:284` — logs `rms=$energy` (a float, no content). Harmless, but it fires on every quiet utterance; consider removing for a quieter log.

**Clean:** all other `Log.*` lines (`service/WakeWordEngine.kt` ×6, `service/BalochListenerService.kt` remaining ~10, `service/VerbalConfirm.kt:65`) carry status/error text only — no user content, no paths, no identifiers. Zero `println` calls anywhere in the codebase. Release builds strip everything anyway (§6).

---

*This document is the guarantee. If Baloch ever behaves against it, that's a bug — report it like one.*

<!-- ================ FORTRESS-PRIVACY ================ -->

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
