# Baloch proguard rules (minify is off; kept for the release build type)

# ---------------------------------------------------------------------------
# FORTRESS PRIVACY — strip ALL android.util.Log calls in release builds.
# Logcat is readable by any app with READ_LOGS-adjacent access on some
# devices and by anyone pulling a bug report, so no release build may emit
# log lines at all. This is the safety net under the source-level rule
# (no personal data in Log.* calls, ever). It takes effect when the release
# build type enables minification (minifyEnabled true).
# ---------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** i(...);
    public static *** e(...);
    public static *** v(...);
    public static *** w(...);
    public static *** wtf(...);
}

# ---------------------------------------------------------------------------
# Keep rules for libraries that rely on reflection / native code.
# ---------------------------------------------------------------------------

# Room — keep entities, DAOs and the database class; Room generates the
# implementation at compile time but reflectively reads the annotations.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep class * {
    @androidx.room.TypeConverter *;
}

# SQLCipher (net.zetetic) — native SQLite; keep the whole surface so the
# SupportFactory path used by BalochDatabase survives shrinking.
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.**

# WorkManager — workers are instantiated by class name from the manifest.
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker
-keep class androidx.work.** { *; }

# Vosk (org.vosk:vosk-android) — speech models load through JNA; keep the
# Java bridge and the JNA plumbing.
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**

# MediaPipe tasks-vision — model/graph plumbing is partly reflective.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
