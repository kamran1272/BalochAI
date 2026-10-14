package com.baloch.ai.memory

import android.content.Context
import com.baloch.ai.memory.room.Tier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Facade over the Room-backed Total Memory.
 *
 * The three legacy methods below are kept byte-for-byte signature-compatible
 * for the sibling CompanionAgent (agents/Agents.kt) and BalochViewModel —
 * they bridge into the repository with runBlocking on Dispatchers.IO.
 *
 * NOTE on runBlocking: these legacy sync methods predate the Room migration
 * (the old implementation did @Synchronized file I/O on the calling thread).
 * runBlocking(Dispatchers.IO) is used ONLY here, never on the main thread by
 * new code — everything new should call [repo] directly with suspend funs.
 * All file/DB I/O stays off the main thread.
 */
class MemoryStore(private val context: Context) {

    /**
     * Direct access to the full repository — the path all new call sites
     * should use (suspend API, Flow streams, tiers, backup, janitor hooks).
     * The parent's new brain call sites pass real language/mode values via
     * repo.logConversation(...) instead of the legacy logExchange.
     */
    fun repo(context: Context = this.context): MemoryRepository =
        MemoryRepository.get(context.applicationContext)

    /**
     * Legacy: all facts as "key: value" strings, newest first.
     * @deprecated Internal bridge for CompanionAgent — new code: repo().allFacts()
     */
    @Deprecated("Internal legacy bridge; use repo().allFacts() from a coroutine")
    fun facts(): List<String> =
        runBlocking(Dispatchers.IO) { repo().facts() }

    /**
     * Legacy: stores a free-text note as a temporary "other" fact.
     * @deprecated Internal legacy bridge — new code: repo().saveFact(...) with a tier
     */
    @Deprecated("Internal legacy bridge; use repo().saveFact(...) from a coroutine")
    fun saveFact(fact: String) {
        val text = fact.trim()
        if (text.isEmpty()) return
        runBlocking(Dispatchers.IO) {
            repo().saveFact(
                key = "note",
                value = text,
                category = "other",
                confidence = 0.8f,
                sourceQuote = text,
                tier = Tier.TEMPORARY,
            )
        }
    }

    /**
     * Legacy: logs an exchange with language/mode "unknown". New call sites
     * pass real values via repo().logConversation(user, reply, lang, mode).
     * @deprecated Internal legacy bridge — new code: repo().logConversation(...)
     */
    @Deprecated("Internal legacy bridge; use repo().logConversation(...) from a coroutine")
    fun logExchange(user: String, baloch: String) {
        runBlocking(Dispatchers.IO) {
            repo().logConversation(user, baloch, lang = "unknown", mode = "unknown")
        }
    }
}
