package com.baloch.ai.rules

import android.content.Context
import android.content.SharedPreferences
import com.baloch.ai.brain.DeviceAction
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Calendar
import java.util.TimeZone

/**
 * What the Orchestrator may do with a proposed [DeviceAction].
 *
 * - [Allow]          run it straight away.
 * - [RequireConfirm] show the confirm UI first (Kamran taps to proceed).
 * - [Forbid]         do NOT run; speak [Forbid.reason] (his own rule words).
 * - [QuietDefer]     NEVER returned by [GuidanceRuleStore.effectiveVerdict]
 *                    for direct commands — the Orchestrator only applies it
 *                    to *proactive* outputs, after consulting
 *                    [GuidanceRuleStore.isQuietNow]. Direct orders always run
 *                    (ownership principle: his word is law).
 */
sealed interface Verdict {
    data object Allow : Verdict
    data object RequireConfirm : Verdict
    data class Forbid(val reason: String) : Verdict
    data object QuietDefer : Verdict
}

private const val RULES_FILE = "guidance_rules.json"
private const val PREFS_NAME = "guidance_rules_prefs"
private const val KEY_SEEDED = "seeded"
private const val KEY_RELAXED = "relaxed_scopes"

/**
 * JSON store for [GuidanceRule]s in app-private filesDir.
 *
 * - Writes are ATOMIC: payload goes to `guidance_rules.json.tmp` in the same
 *   directory, then renameTo() — a crash mid-write can never leave a
 *   truncated rules file (and truncated rules = lost safety rules).
 * - DELETED-DEFAULT TRACKING: deleting a default rule records its scope in
 *   the "relaxed_scopes" SharedPreferences string set. That is what makes
 *   confirmations truly teachable — deleting the "ask before calls" default
 *   means calls no longer ask; no code lock survives the deletion.
 * - All public functions are @Synchronized (ViewModel + workers may call
 *   from different threads).
 *
 * ENTRY POINTS for the parent wiring pass:
 *   1. `GuidanceRuleStore(appContext)` — construct once, keep application-scoped.
 *   2. `seedDefaultsIfNeeded()` — call once at app start.
 *   3. `effectiveVerdict(action)` — call in the Orchestrator before executing
 *      ANY DeviceAction; honour the Verdict as documented above.
 *   4. `isQuietNow()` — call in the proactive scheduler before any unasked-for
 *      output (check-in, battery alert, briefing nudge); skip/defer when true.
 */
class GuidanceRuleStore(context: Context) {

    private val dir: File = context.filesDir
    private val file = File(dir, RULES_FILE)
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------- reads ----------

    @Synchronized
    fun all(): List<GuidanceRule> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            List(arr.length()) { i -> arr.getJSONObject(i).toRule() }
                .filterNotNull()
                .sortedBy { it.createdAt }
        } catch (_: Exception) {
            emptyList() // corrupt file → empty, never crash; next save() rewrites clean
        }
    }

    /** Scopes whose default confirm rule Kamran deleted ("relaxed by deletion"). */
    @Synchronized
    fun relaxedScopes(): Set<RuleScope> =
        prefs.getStringSet(KEY_RELAXED, emptySet()).orEmpty().mapNotNull {
            runCatching { RuleScope.valueOf(it) }.getOrNull()
        }.toSet()

    private fun relaxedScopesRaw(): Set<String> =
        prefs.getStringSet(KEY_RELAXED, emptySet()).orEmpty()

    /**
     * Quiet-hours check, wrap-around aware (e.g. 22→7 covers midnight).
     * Kamran's timezone is Asia/Karachi — hour is always read in PKT.
     * True only when at least one QUIET_HOURS rule exists AND now is inside
     * one of its windows. Direct commands ignore this; only the proactive
     * scheduler consults it.
     */
    fun isQuietNow(): Boolean {
        val windows = all().filter { it.constraint == Constraint.QUIET_HOURS }
        if (windows.isEmpty()) return false
        val hour = Calendar.getInstance(TimeZone.getTimeZone("Asia/Karachi"))
            .get(Calendar.HOUR_OF_DAY)
        return windows.any { inWindow(hour, it.quietStartHour, it.quietEndHour) }
    }

    private fun inWindow(hour: Int, start: Int, end: Int): Boolean {
        val s = start.coerceIn(0, 23)
        val e = end.coerceIn(0, 23)
        // s == e is treated as an empty window (documented; a "whole day"
        // quiet rule would be taught differently, e.g. a DISTURB FORBID).
        return if (s < e) hour in s until e else hour >= s || hour < e
    }

    // ---------- writes ----------

    @Synchronized
    fun add(rule: GuidanceRule): GuidanceRule {
        val rules = all().toMutableList()
        // Monotonic ids even if two adds land in the same millisecond.
        val freshId = maxOf(
            System.currentTimeMillis(),
            (rules.maxOfOrNull { it.id } ?: 0L) + 1L,
        )
        val stored = rule.copy(
            id = freshId,
            createdAt = System.currentTimeMillis(),
            text = rule.text.trim(),
        )
        rules.add(stored)
        save(rules)
        // An explicit rule for this scope supersedes "relaxed by deletion".
        clearRelaxed(rule.scope)
        return stored
    }

    @Synchronized
    fun delete(id: Long): Boolean {
        val rules = all().toMutableList()
        val target = rules.firstOrNull { it.id == id } ?: return false
        rules.remove(target)
        save(rules)
        if (target.isDefault) {
            val relaxed = relaxedScopesRaw().toMutableSet()
            relaxed.add(target.scope.name)
            prefs.edit().putStringSet(KEY_RELAXED, relaxed).apply()
        }
        return true
    }

    @Synchronized
    fun clearRelaxed(scope: RuleScope) {
        val relaxed = relaxedScopesRaw().toMutableSet()
        if (relaxed.remove(scope.name)) {
            prefs.edit().putStringSet(KEY_RELAXED, relaxed).apply()
        }
    }

    /**
     * Seeds Kamran's two starter safety rules on first run ONLY (guarded by a
     * persisted flag — never re-seeds after he deletes them). These are HIS
     * starter rules, deletable from MY RULES like any other rule.
     */
    @Synchronized
    fun seedDefaultsIfNeeded() {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        if (all().isEmpty()) {
            add(
                GuidanceRule(
                    id = 0,
                    text = "Always ask me before making any call, sir.",
                    scope = RuleScope.CALLS,
                    constraint = Constraint.REQUIRE_CONFIRM,
                    isDefault = true,
                ),
            )
            add(
                GuidanceRule(
                    id = 0,
                    text = "Always ask me before sending any message, sir.",
                    scope = RuleScope.MESSAGES,
                    constraint = Constraint.REQUIRE_CONFIRM,
                    isDefault = true,
                ),
            )
        }
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }

    // ---------- THE verdict — Orchestrator entry point ----------

    /**
     * Decides what may happen with a proposed [DeviceAction].
     *
     * Resolution order:
     *  1. FORBID wins over everything (his explicit "never do X").
     *  2. Scope-specific rules beat ALL-scope rules; latest-taught wins ties
     *     within a scope. REQUIRE_CONFIRM → RequireConfirm, ALLOW → Allow.
     *  3. ALL-scope REQUIRE_CONFIRM / ALLOW (in that priority).
     *  4. Fallback: scope in relaxedScopes → Allow (he deleted the default,
     *     so the training wheels are off); else the built-in risk tier —
     *     [DeviceAction.needsConfirmation] → RequireConfirm; everything
     *     else → Allow.
     *
     * QUIET_HOURS/DISTURB rules never affect this verdict: they are for
     * proactive outputs only (see [isQuietNow]). A direct "call Ami now at
     * 3am" runs — his word is law.
     */
    @Synchronized
    fun effectiveVerdict(action: DeviceAction): Verdict {
        val scope = action.ruleScope()
        val matching = all().filter { it.scope == scope || it.scope == RuleScope.ALL }

        // 1 — explicit FORBID beats everything; speak his own words back.
        matching.firstOrNull { it.constraint == Constraint.FORBID }
            ?.let { return Verdict.Forbid(it.text) }

        // 2 — scope-specific rule (latest taught wins).
        val specific = matching.filter { it.scope == scope }
        if (specific.isNotEmpty()) {
            val latest = specific.maxBy { it.createdAt }
            return when (latest.constraint) {
                Constraint.REQUIRE_CONFIRM -> Verdict.RequireConfirm
                Constraint.ALLOW -> Verdict.Allow
                Constraint.FORBID -> Verdict.Forbid(latest.text) // unreachable; step 1
                Constraint.QUIET_HOURS -> Verdict.Allow // DISTURB never matches an action scope
            }
        }

        // 3 — ALL-scope fallthrough.
        if (matching.any { it.constraint == Constraint.REQUIRE_CONFIRM }) return Verdict.RequireConfirm
        if (matching.any { it.constraint == Constraint.ALLOW }) return Verdict.Allow

        // 4 — built-in risk tier, relaxed by deletion of a default.
        if (relaxedScopesRaw().contains(scope.name)) return Verdict.Allow
        return if (action.needsConfirmation) Verdict.RequireConfirm else Verdict.Allow
    }

    // ---------- persistence ----------

    private fun save(rules: List<GuidanceRule>) {
        val arr = JSONArray()
        for (r in rules) {
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("text", r.text)
                    .put("scope", r.scope.name)
                    .put("constraint", r.constraint.name)
                    .put("quietStartHour", r.quietStartHour)
                    .put("quietEndHour", r.quietEndHour)
                    .put("isDefault", r.isDefault)
                    .put("createdAt", r.createdAt),
            )
        }
        // Atomic: temp file in the SAME directory, verify content, rename.
        val tmp = File(dir, "$RULES_FILE.tmp")
        tmp.writeText(arr.toString())
        if (file.exists() && !file.delete()) {
            throw IOException("GuidanceRuleStore: could not replace $RULES_FILE")
        }
        if (!tmp.renameTo(file)) {
            throw IOException("GuidanceRuleStore: atomic rename failed for $RULES_FILE")
        }
    }

    private fun JSONObject.toRule(): GuidanceRule? = runCatching {
        GuidanceRule(
            id = getLong("id"),
            text = getString("text"),
            scope = RuleScope.valueOf(getString("scope")),
            constraint = Constraint.valueOf(getString("constraint")),
            quietStartHour = optInt("quietStartHour", 0),
            quietEndHour = optInt("quietEndHour", 7),
            isDefault = optBoolean("isDefault", false),
            createdAt = optLong("createdAt", System.currentTimeMillis()),
        )
    }.getOrNull()
}
