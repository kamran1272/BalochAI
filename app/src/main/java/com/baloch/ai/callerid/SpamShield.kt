package com.baloch.ai.callerid

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * Offline spam shield: starter heuristics + Kamran's personal learnable blocklist.
 *
 * HONEST BASIS (read before trusting a "spam" verdict):
 * [STARTER_SPAM] is a small set of *heuristics*, NOT a live spam database.
 * There is no Truecaller-style crowdsourced cloud DB on this device — Baloch
 * never uploads numbers anywhere (see PRIVACY.md), so it cannot know what
 * millions of strangers reported. The starter patterns below are compiled
 * from widely-reported Pakistani complaint-board themes:
 *   - satellite one-ring "Wangiri" callbacks (+881 / +882 ranges),
 *   - 0900 premium-rate "prize winner" / missed-call toll traps.
 * Most real-world Pakistani phone scams (fake BISP/JazzCash calls, "jeeto
 * Pakistan" prize SMS) come from ordinary-looking mobile numbers that NO
 * offline pattern can catch. Baloch therefore says "reported spam *pattern*"
 * — never "this is definitely a scammer". The personal blocklist (learned
 * from Kamran's own "block this number" commands) is the reliable layer;
 * the starter list is a courtesy tripwire.
 */
object SpamShield {

    /** One starter heuristic: international-digit prefix -> why it is flagged. */
    data class SpamPattern(val intlPrefix: String, val reason: String)

    val STARTER_SPAM: List<SpamPattern> = listOf(
        SpamPattern(
            "881",
            "Satellite range — classic one-ring (Wangiri) callback scam",
        ),
        SpamPattern(
            "882",
            "Satellite range — classic one-ring (Wangiri) callback scam",
        ),
        SpamPattern(
            "92900",
            "Pakistani 0900 premium-rate range — prize/toll-fraud calls",
        ),
    )

    /**
     * Canonical international digits: 03001234567 -> 923001234567,
     * +923001234567 -> 923001234567. Used for prefix matching.
     */
    fun intlDigits(raw: String): String {
        val d = PrefixTables.normalize(raw)
        return when {
            d.startsWith("0") -> "92" + d.drop(1)
            d.startsWith("92") -> d
            d.startsWith("0092") -> d.drop(2)
            else -> d
        }
    }

    /** True if the number matches a starter spam heuristic. */
    fun isSpam(raw: String): Boolean {
        val intl = intlDigits(raw)
        if (intl.isBlank()) return false
        return STARTER_SPAM.any { intl.startsWith(it.intlPrefix) }
    }

    // ------------------------------------------------------------------
    // Personal blocklist — JSON in the app's private filesDir.
    // ------------------------------------------------------------------

    private const val BLOCKLIST_FILE = "callerid-blocklist.json"
    private val lock = Any()

    private fun file(context: Context): File =
        File(context.filesDir, BLOCKLIST_FILE)

    private fun loadLocked(context: Context): MutableSet<String> {
        val f = file(context)
        if (!f.exists()) return mutableSetOf()
        return try {
            val arr = JSONArray(f.readText())
            val s = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                s.add(arr.optString(i))
            }
            s
        } catch (_: Exception) {
            mutableSetOf()
        }
    }

    private fun saveLocked(context: Context, set: Set<String>) {
        try {
            file(context).writeText(JSONArray(set.toList()).toString())
        } catch (_: Exception) {
            // Blocklist write failure must never break the call flow.
        }
    }

    /** Block a number (stored normalised). */
    fun block(context: Context, raw: String) = synchronized(lock) {
        val set = loadLocked(context)
        val n = PrefixTables.normalize(raw)
        if (n.isNotBlank()) {
            set.add(n)
            saveLocked(context, set)
        }
    }

    /** Remove a number from the blocklist. */
    fun unblock(context: Context, raw: String) = synchronized(lock) {
        val set = loadLocked(context)
        if (set.remove(PrefixTables.normalize(raw))) saveLocked(context, set)
    }

    /** True if the number is on Kamran's personal blocklist. */
    fun isBlocked(context: Context, raw: String): Boolean = synchronized(lock) {
        loadLocked(context).contains(PrefixTables.normalize(raw))
    }

    /** All blocked numbers (normalised form), newest last. */
    fun all(context: Context): List<String> = synchronized(lock) {
        loadLocked(context).toList()
    }

    // ------------------------------------------------------------------
    // Teachable voice patterns: "is number ko block kar de" etc.
    // ------------------------------------------------------------------

    /**
     * Voice/typed block command parsed from free text (trilingual, incl.
     * Roman Urdu). [BlockCmd.target] is a normalised number, or [LAST_NUMBER]
     * when the command names no explicit number ("is number ko block kar de"
     * -> block the last announced caller; the brain resolves LAST_NUMBER).
     */
    sealed interface BlockCmd {
        data class Block(val target: String) : BlockCmd
        data class Unblock(val target: String) : BlockCmd
    }

    const val LAST_NUMBER = "LAST"

    private val NUMBER_IN_TEXT = Regex("\\+?\\d[\\d\\s\\-]{8,15}")

    fun matchBlockCommand(input: String): BlockCmd? {
        val t = input.lowercase().trim()
        if (!t.contains("block")) return null
        // Check "unblock" FIRST — it contains "block".
        val unblock = t.contains("unblock") ||
            t.contains("un block") ||
            t.contains("block hata") ||
            t.contains("block khatam")
        val explicit = NUMBER_IN_TEXT.find(t)?.value?.let { PrefixTables.normalize(it) }
            ?.takeIf { it.length >= 10 }
        val target = explicit ?: LAST_NUMBER
        return if (unblock) BlockCmd.Unblock(target) else BlockCmd.Block(target)
    }
}
