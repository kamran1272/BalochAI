# Caller ID — README section

## How it works

When an unknown number calls, Baloch announces who it is — **100% offline,
nothing ever uploaded anywhere** (see PRIVACY.md). Package
`com.baloch.ai.callerid`:

1. **BalochCallScreeningService** (`CallerIdService.kt`, Path A) — a
   `CallScreeningService` (API 24+). The system hands it every incoming call
   via `onScreenCall(Call.Details)` with the real caller number. Needs the
   `ROLE_CALL_SCREENING` role, granted once by Kamran from the Call Log
   screen (RoleManager, API 29+).
2. **CallStateWatcher** (Path B, fallback — no role needed) — a
   `PhoneStateListener` (API 26–30) / `TelephonyCallback` (API 31+) watching
   `CALL_STATE_RINGING` with only `READ_PHONE_STATE`. Works without being the
   screening app. Honest limit: on API 31+ the framework withholds the
   incoming number from non-dialer apps, so Path B degrades to ring-timing
   only (generic "unknown number" line); on API 26–30 the number is still
   delivered when the permission is granted (OEM-dependent).
3. **CallerIdAnnouncer** (shared hot path, target <200ms to speech):
   - saved contact → *"Sir, Ahmed is calling"* (reverse lookup via
     `PhoneLookup`, same guarded pattern as `DeviceActions.resolveContact`);
   - personal blocklist hit → ringer **silenced** via AudioManager, no
     announcement, previous ringer mode restored on call end;
   - starter spam pattern → *"Sir, this is a reported spam number — pick up
     or cut it?"* and the question is exposed via `pendingSpamQuestion()` for
     the parent to wire yes/no;
   - otherwise → `PrefixTables.describe()` → *"Sir, unknown number — it's a
     Jazz mobile"* / *"a Lahore landline"* (trilingual via L3).
   - Announcements use a one-shot `TextToSpeech` on the service context
     (`OnInitListener`, `STREAM_MUSIC`, locked deeper pitch 0.87, shutdown on
     utterance completion — nothing kept alive between calls).
4. **SpamShield** — offline spam shield:
   - `STARTER_SPAM`: a *small heuristic set*, not a database (satellite
     one-ring Wangiri ranges +881/+882, Pakistani 0900 premium-rate range).
     Documented honestly in code: most real PK scams use ordinary-looking
     mobiles that no offline pattern can catch.
   - Personal blocklist in `filesDir/callerid-blocklist.json`
     (`block` / `unblock` / `isBlocked` / `all`), taught by voice:
     *"is number ko block kar de" / "block this number" / "unblock"*
     (trilingual incl. Roman Urdu) via `matchBlockCommand()`.
5. **Deep lookup** (`"is number ka pata lagao"`) — opens a browser search for
   the number (`CallerIdAnnouncer.deepLookupIntent()`), framed honestly:
   *"Internet par is number ke baare mein ye mila — certain nahi hai, sir."*
   Online-only; the offline-first rule applies (parent checks
   ConnectivityGate first).
6. **CallLogScreen** (`ui/`) — recent unknown incoming callers (last 20,
   `READ_CALL_LOG`), each with network/city/spam badge, Block / Unblock /
   Report-spam / Look-up actions, and the "set as call screener" row. Pure
   props + `CallLogCallbacks` interface — the parent loads
   `loadRecentUnknownCallers()` off the main thread and wires the role
   request (snippet in the interface KDoc).

## Honest Truecaller comparison

| | Truecaller | Baloch Caller ID |
|---|---|---|
| Unknown-caller **name** | Yes — crowdsourced cloud DB | **No.** That DB is closed/paid; Baloch has no cloud directory and will not fake one |
| Saved contacts | Yes | Yes — announced by name |
| Network / city | Sometimes | Yes — offline PK prefix tables |
| Spam warning | Live community reports | Starter heuristics + **your own** learned blocklist |
| Privacy | Numbers uploaded to their cloud | **Nothing uploaded, ever** — 100% on-device |

## Permissions — grant steps

1. **Phone** (`READ_PHONE_STATE`) — Settings → Apps → Baloch → Permissions →
   Phone → Allow. Enables the no-role ring watcher.
2. **Contacts** (`READ_CONTACTS`) — same screen → Contacts → Allow. Enables
   *"Sir, Ahmed is calling"* announcements.
3. **Call log** (`READ_CALL_LOG`) — same screen → Call log → Allow. Enables
   the recent-unknown-callers list.
4. **Call screener role** (API 29+) — open the Caller ID screen in the app →
   "Call screener" row → ENABLE → choose Baloch in the system dialog. This
   unlocks full number detection (Path A). On Android 8.x the role doesn't
   exist; Baloch automatically uses the Path B watcher instead — nothing to
   grant.

Manifest pieces: `manifest-snippets-callerid.txt` (permissions + the
`BIND_SCREENING_SERVICE` service declaration — merge, don't replace).
