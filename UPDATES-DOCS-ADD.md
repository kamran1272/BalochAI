# PROFESSIONAL VERSIONED UPDATES — README section

## Versioning scheme

Baloch is a living product: every feature ships as a versioned release.
`update/VersionInfo.kt` is the single source of truth the UI reads
(splash footer, Settings → About):

- `APP_VERSION_NAME` — semver `MAJOR.MINOR.PATCH` ("1.0.0").
- `APP_VERSION_CODE` — integer, **MUST increment every release**, even a
  hotfix. Android decides "newer" by code, not name.
- Both are mirrored into `app/build.gradle` (`versionCode` /
  `versionName`) before the factory builds — the manifest value is what
  the OS sees; they must agree.
- `CHANGELOG.md` (user-facing, Keep-a-Changelog) and `VERSIONS.md`
  (factory dev ledger, not shipped in-app) are updated with every
  release.

## The update-check flow

Settings → About → **Check for updates**. All strings are trilingual
(`update/UpdateStrings`, en-GB English) and travel through the normal
reply pipeline, so Kamran hears them in his active language.

1. **Check.** `UpdateChecker.checkForUpdates(ctx)` (Dispatchers.IO,
   8 s timeouts) reads the latest GitHub release. No internet →
   `UpdateResult.Offline`, and the app shows the Roman-Urdu line:
   *"Internet chahiye, sir — baad mein check kar lena."*
2. **Up to date.** `UpToDate` → "You're on the latest version, sir —
   v1.0.0. Nothing to do." The updater never suggests a downgrade.
3. **Available.** `Available(release)` → "A new Baloch is ready, sir —
   v{version}. Shall I download it?" — asked, never assumed. On his
   yes, `downloadAndPromptInstall(ctx, release)` enqueues the APK in
   `DownloadManager` under `Downloads/BalochAI/` with a visible
   notification.
4. **Install.** A one-shot completion receiver verifies the download
   succeeded, then fires the **system install prompt** via FileProvider.
   Kamran taps Install himself — **nothing ever auto-installs**. On
   Android 8+, if the "Install unknown apps" grant is missing, the app
   deep-links to the exact settings page instead of a dead installer.
5. **Errors** (bad feed, timeout, unreadable release) → `Error(msg)`,
   surfaced as one honest spoken line, never a crash.

Permissions involved: `ACCESS_NETWORK_STATE` (offline detection),
`REQUEST_INSTALL_PACKAGES` (API 26+ install gate). See
`manifest-snippets-updates.txt` for the exact blocks plus the
`res/xml/file_paths.xml` FileProvider paths.

## The migration guarantee

> Updates never wipe his memory — facts, conversations, voiceprint,
> PIN, rules, routines, scheduled tasks survive every version.

The guarantee is enforced as code in `update/MigrationContract.kt`:

- **Room** (`baloch_memory.db`, currently v1): additive schema changes
  use `@AutoMigration`; destructive ones need a hand-written `Migration`
  that carries every row forward. Dropping user data in a migration is
  forbidden — if a change can't preserve it, the release doesn't ship.
- **Keys** (DataStore `"voice_lock"` / `"baloch_mode"`, SharedPreferences
  `"baloch_prefs"` / `"guidance_rules_prefs"` / `"baloch_pro"`, JSON
  files for tasks and routines): a changed key shape gets a new
  `<key>_v2` name with a migrate-read from `<key>_v1`; old keys are
  never deleted until migration is confirmed.
- After an update install, scheduled tasks are re-armed through the
  same path as the boot re-arm, and the always-on listener restarts if
  its toggle was on.

## Release-repo convention (factory)

The updater reads `https://api.github.com/repos/kamran1272/BalochAI/releases/latest`.
Each factory release must follow this shape:

- **Release name:** `v1.0.0 (code 3)` — semver + the integer versionCode
  in parentheses (parsed by regex; `tag_name` is the fallback).
- **APK:** attached as the first `.apk` release asset (fallback: an
  `.apk` URL in the body).
- **Body:** the changelog (shown to Kamran before he confirms).
- The release's versionCode must be greater than the installed
  `APP_VERSION_CODE`, or the updater reports "up to date".

## Snippets for the parent (final wiring)

**Splash version footer** — in `ui/SplashScreen.kt`, beside the existing
`"MAGSI DIGITAL · BALOCH OS"` footer:

```kotlin
Text(
    text = "v${com.baloch.ai.update.APP_VERSION_NAME}",
    color = BalochTextDim.copy(alpha = 0.6f),
    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 28.dp),
)
```

**Settings → About row** — in the About section:

```kotlin
// Row 1: version (read-only)
Text("Baloch v${com.baloch.ai.update.APP_VERSION_NAME} (code ${com.baloch.ai.update.APP_VERSION_CODE})")
// Row 2: "Check for updates" → onClick:
//   scope.launch {
//       updateState = UpdateCheckState.Checking
//       updateState = when (val r = UpdateChecker.checkForUpdates(ctx)) {
//           is UpdateResult.Available -> UpdateCheckState.Available(r.release)
//           UpdateResult.UpToDate -> UpdateCheckState.UpToDate
//           UpdateResult.Offline -> UpdateCheckState.Offline
//           is UpdateResult.Error -> UpdateCheckState.Failed(r.msg)
//       }
//   }
// Available → confirm dialog showing release.changelog → on confirm:
//   UpdateChecker.downloadAndPromptInstall(ctx, release)
```

`UpdateCheckState` is a small UI-state sealed interface the parent
defines in the Settings/ViewModel layer; the strings come from
`UpdateStrings` via `brain.L3.pick(languageManager.current())`
(`AVAILABLE` carries a `{version}` placeholder — resolve with
`.format(lang, "version" to release.versionName)`).
