# NETWORK AUDIT — every outbound call Baloch is capable of

> Audited Oct 14 2026 against all `*.kt` sources and `app/build.gradle`.
> Doctrine: **local-only**. This file lists the COMPLETE set of network
> traffic the app can produce. If it's not on this list, the app cannot do it.

## Allowed outbound calls (4 + 2 user-initiated browser intents)

| # | Call site | Destination | What is sent | When it fires |
|---|---|---|---|---|
| 1 | `engine/pro/CurrencyConverter.kt:79` | `GET https://open.er-api.com/v6/latest/{FROM}` | Only the 3-letter currency code the user named (e.g. `USD`, `PKR`). No location, no identifiers, no device info. | Only when Kamran asks for a currency conversion. |
| 2 | `engine/pro/BriefingHelper.kt:28` | `GET https://api.open-meteo.com/v1/forecast?latitude=31.52&longitude=74.36&current=temperature_2m,weather_code` | **Fixed** Lahore coordinates compiled into the app. No GPS, no location permission used, no user identifier. | Only when building the morning/evening briefing that includes weather. |
| 3 | `service/WakeWordEngine.kt:430-431` | `GET https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip` (fallback `...-0.22.zip`) | Nothing — a plain file download. No data leaves the phone. | **One time only**: first enable of always-on voice, to fetch the offline wake-word model (~50 MB). After that, wake-word detection is 100% offline. |
| 4 | `engine/DeviceActions.kt:296` | `Intent(ACTION_VIEW)` → `https://www.google.com/search?q=<encoded query>` | The search query text Kamran dictated/typed. | Only when he explicitly asks Baloch to web-search. This opens **his browser** — it is not an in-app API call and Baloch never sees the results. |

### User-initiated share intents (leave the app, go to the named service only)

| # | Call site | Destination | What is sent | When it fires |
|---|---|---|---|---|
| 5 | `engine/DeviceActions.kt:398` (`whatsAppWebFallback`) | `Intent(ACTION_VIEW)` → `https://api.whatsapp.com/send?phone=<digits>&text=<message>` | The recipient's digits and the draft message text. | Only as a **fallback when the WhatsApp app isn't installed** and Kamran asked to message someone. Goes to WhatsApp, nowhere else. |
| 6 | `memory/MemoryBackup.kt` (export/import) | User-chosen file via Storage Access Framework | An encrypted backup of the memory DB, only if the user taps Backup and picks a destination. | Never automatic. The file goes where **Kamran** points it. |

## Explicitly NOT present

Verified by grepping all sources and the full dependency list (`app/build.gradle`):

- ❌ **No analytics SDK** (no Firebase Analytics, no Mixpanel, no Amplitude, nothing)
- ❌ **No crash-reporting SDK** (no Crashlytics, no Sentry, no Bugsnag)
- ❌ **No ads SDK** (no AdMob, nothing ad-related)
- ❌ **No telemetry / "phone home"** — no periodic pings, no usage counters sent anywhere
- ❌ **No push-messaging service** (no FCM)
- ❌ **No remote config / A-B testing**

Full dependency list for the record: compose-bom 2024.06.00, core-ktx, lifecycle, activity-compose, material3, kotlinx-coroutines-android, work-runtime-ktx, datastore-preferences, biometric, vosk-android, room-runtime/ktx, security-crypto, android-database-sqlcipher, mediapipe tasks-vision. Every one of these is a local/UI/storage/ML-on-device library — none of them transmits data.

## What the network NEVER carries

Personal data — names, contacts, photos, files, notes, voiceprints, PINs, message contents, chat history — is **never** part of any request above. The two data-bearing calls (#1 currency codes, #2 fixed coordinates, #4 an explicit search query) carry only what that feature needs, only when Kamran triggers it, and #4 goes to his own browser.

## How to keep this true

Any future network call must be added to this table with its call site, destination, payload, and trigger — otherwise it doesn't ship. Reviewers: `grep -rn "http" --include="*.kt"` is the 10-second re-audit.
