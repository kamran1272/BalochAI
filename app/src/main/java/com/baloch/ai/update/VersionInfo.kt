package com.baloch.ai.update

/**
 * Single source of truth for the app's version. The UI reads this —
 * the splash footer and Settings → About — never hard-codes a version
 * string anywhere else.
 *
 * RELEASE RULES (Kamran's order — this is a living product):
 * 1. Bump [APP_VERSION_NAME] every release (semver: MAJOR.MINOR.PATCH).
 * 2. [APP_VERSION_CODE] MUST increment by at least 1 EVERY release, even
 *    for a tiny hotfix. Android uses the code — not the name — to decide
 *    what is "newer"; a release with a non-incremented code is invisible
 *    to [UpdateChecker] and to the Play-style install flow.
 * 3. Mirror both values into app/build.gradle (versionCode/versionName)
 *    before the factory builds the APK — the manifest value is what the
 *    OS sees; this file is what the UI and the updater see. They must
 *    agree.
 * 4. After bumping, add the release notes to CHANGELOG.md and the dev
 *    notes to VERSIONS.md (both at the project root).
 */
object VersionInfo {
    const val APP_VERSION_NAME = "1.0.0"
    const val APP_VERSION_CODE = 1
}
