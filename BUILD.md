# BUILD.md — Build & install Baloch

## Option A: Android Studio (recommended)

1. Install Android Studio (any recent version) with Android SDK Platform 34 + Build-Tools 34.
2. Open `~/workspace/baloch-build/mobile/` as a project.
3. Let Gradle sync (downloads dependencies — needs internet once).
4. Run ▶ on a connected phone, or Build → Build APK → `app-debug.apk`.

## Option B: Command line

```bash
export JAVA_HOME=~/android-tooling/jdk17
export ANDROID_HOME=~/android-tooling/sdk
cd ~/workspace/baloch-build/mobile
./gradlew assembleDebug   # or: gradle assembleDebug
# APK lands at: app/build/outputs/apk/debug/app-debug.apk
```

(Copy it to `~/workspace/baloch-build/mobile/app-debug.apk` for Kamran.)

## Install on Kamran's phone

1. Copy `app-debug.apk` to the phone (USB / Drive / WhatsApp to himself).
2. Tap it → allow "Install unknown apps" → Install.
3. Open Baloch → grant permissions as asked (see PERMISSIONS.md):
   - Microphone (voice)
   - Notifications (alerts, reminders)
   - Usage access (app insights) — special access screen
   - Notification access (read notifications) — special access screen
   - Modify system settings (brightness)
   - Alarms & reminders (exact alarms, Android 12+)
4. First-run checklist inside the app:
   - Pick language (English / اردو / हिन्दी)
   - Enrol voice lock (3× "Hey Baloch, it is me") + set PIN
   - Set emergency (ICE) contact
   - Download wake-word model (~50 MB, one time) for always-on
   - Battery: set to "Unrestricted" so the listener survives

## Notes

- `minSdk 26` (Android 8.0+) · `targetSdk 34`.
- First Gradle sync downloads ~1 GB of dependencies — one-time cost.
- Debug APK is signed with the debug key — fine for personal use.
