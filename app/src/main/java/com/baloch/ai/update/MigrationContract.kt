package com.baloch.ai.update

/**
 * The migration-safety guarantee, as code and as documentation.
 *
 * Kamran's standing order: **updates never wipe his memory.** Facts,
 * conversations, voiceprint, PIN, rules, routines and scheduled tasks
 * must survive every version bump. This file is the contract the
 * factory checks against before shipping any release.
 *
 * ## Room database version history
 *
 * | DB v | App v | Change | Strategy |
 * |------|-------|--------|----------|
 * | 1 | 1.0.0 | Baseline: FactEntity, ConversationEntity, EntityRecord (SQLCipher-encrypted, plain-Room fallback) | — |
 * | 2 | _future_ | _example: new column or new table_ | `@AutoMigration(from = 1, to = 2)` |
 * | 3 | _future_ | _example: column type change_ | manual `Migration(2, 3)` — data carried forward, never dropped |
 *
 * ## The rule
 *
 * - **Additive** changes (new table, new column, new index) → use
 *   `@AutoMigration(from = X, to = Y)`. No hand-written SQL.
 * - **Destructive** changes (drop/rename column or table, type change) →
 *   hand-written `Migration` that carries every row forward. It is
 *   FORBIDDEN to drop facts, conversations, entities, voiceprints,
 *   rules, routines or scheduled tasks in any migration. If a schema
 *   change cannot preserve them, the release does not ship.
 * - `exportSchema` is currently `false` in [com.baloch.ai.memory.room.BalochDatabase].
 *   The factory MUST flip it to `true` and commit the schema JSON under
 *   `app/schemas/` before the first migration is written — auto-migrations
 *   are verified against those schemas at build time.
 *
 * ## Key versioning (DataStore / SharedPreferences / files)
 *
 * Storage today (v1.0) and the rule for each:
 *
 * - DataStore `"voice_lock"` — voiceprint vector, variance, enrolled flag,
 *   enabled toggle, PIN hash ([com.baloch.ai.voice.VoiceLockManager]).
 * - DataStore `"baloch_mode"` — MANUAL/JARVIS mode ([com.baloch.ai.settings.ModeManager]).
 * - SharedPreferences `"baloch_prefs"` — language choice ([com.baloch.ai.settings.LanguageManager]).
 * - SharedPreferences `"guidance_rules_prefs"` — standing safety rules ([com.baloch.ai.rules.GuidanceRuleStore]).
 * - SharedPreferences `"baloch_pro"` — emergency ICE contact ([com.baloch.ai.engine.pro.EmergencyStore]).
 * - File `filesDir/scheduled_tasks.json` — scheduled tasks ([com.baloch.ai.schedule.TaskStore]).
 * - File `filesDir/baloch_routines.json` — routines/macros ([com.baloch.ai.engine.pro.RoutineStore]).
 * - Room file `baloch_memory.db` — facts, conversations, entities.
 * - EncryptedSharedPreferences `"baloch_crypto"` — the SQLCipher passphrase.
 *   Losing this file (app-data wipe) orphans an encrypted DB — documented
 *   in BalochDatabase; the fallback path only applies when encryption was
 *   never set up.
 *
 * Convention: when a key's **shape** changes, do NOT reuse the key name.
 * Write `<key>_v2`, add a migrate-read that copies `<key>_v1` → `<key>_v2`
 * once, and only then stop reading the old key. Old keys are never deleted
 * until the migration is confirmed complete — a half-migrated update that
 * crashes must still find the old data on the next launch.
 *
 * ## Post-update re-arm
 *
 * An APK update does not reboot the phone, but AlarmManager exact alarms
 * and WorkManager workers can be affected by the reinstall. After any
 * update install, [com.baloch.ai.schedule.RescheduleReceiver]-style logic
 * must re-arm enabled scheduled tasks and restart the always-on listener
 * if its toggle was on — same path as the BOOT_COMPLETED re-arm, reused,
 * not duplicated.
 */
object MigrationContract {

    /**
     * The guarantee statement. The parent copies this verbatim into the
     * README's update section and it is read aloud if Kamran ever asks
     * "will an update delete my stuff?".
     */
    const val GUARANTEE =
        "Updates never wipe his memory — facts, conversations, voiceprint, " +
            "PIN, rules, routines, scheduled tasks survive every version."

    /** Current Room schema version — bump together with BalochDatabase. */
    const val ROOM_VERSION = 1

    /**
     * Everything an update is contractually forbidden from losing.
     * The factory's update QA walks this list on a real device:
     * install v1.0 → populate each store → install the new APK →
     * assert every item still reads back.
     */
    val MUST_SURVIVE: List<SurvivalItem> = listOf(
        SurvivalItem("Facts", "Room baloch_memory.db · FactEntity"),
        SurvivalItem("Conversations", "Room baloch_memory.db · ConversationEntity"),
        SurvivalItem("Entities", "Room baloch_memory.db · EntityRecord"),
        SurvivalItem("Voiceprint + variance", "DataStore \"voice_lock\" · keys voiceprint, voiceprint_variance"),
        SurvivalItem("Voice-lock toggles", "DataStore \"voice_lock\" · keys enrolled, enabled"),
        SurvivalItem("PIN hash", "DataStore \"voice_lock\" · PinManager keys"),
        SurvivalItem("Language choice", "SharedPreferences \"baloch_prefs\" · key language"),
        SurvivalItem("Manual/Jarvis mode", "DataStore \"baloch_mode\""),
        SurvivalItem("Safety rules", "SharedPreferences \"guidance_rules_prefs\""),
        SurvivalItem("Routines / macros", "File filesDir/baloch_routines.json"),
        SurvivalItem("Scheduled tasks", "File filesDir/scheduled_tasks.json"),
        SurvivalItem("Emergency ICE contact", "SharedPreferences \"baloch_pro\""),
        SurvivalItem("DB passphrase", "EncryptedSharedPreferences \"baloch_crypto\""),
    )
}

/** One thing an update must never lose: what it is and where it lives. */
data class SurvivalItem(
    val what: String,
    val where: String,
)
