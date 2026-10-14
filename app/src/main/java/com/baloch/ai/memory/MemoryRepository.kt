package com.baloch.ai.memory

import android.content.Context
import com.baloch.ai.memory.room.BalochDatabase
import com.baloch.ai.memory.room.ConversationEntity
import com.baloch.ai.memory.room.EntityRecord
import com.baloch.ai.memory.room.FactEntity
import com.baloch.ai.memory.room.TEMP_TTL_MS
import com.baloch.ai.memory.room.Tier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Clean suspend API over the memory DAOs — the single entry point the brain,
 * the janitor and the memory screen talk to. All I/O happens on
 * Dispatchers.IO callers' scopes; every function here is suspend (or a Flow)
 * so it can never block the main thread.
 *
 * Get it via [get] — one instance per process, backed by the encrypted
 * [BalochDatabase] singleton.
 */
class MemoryRepository private constructor(private val db: BalochDatabase) {

    companion object {
        @Volatile
        private var INSTANCE: MemoryRepository? = null

        fun get(context: Context): MemoryRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: MemoryRepository(BalochDatabase.get(context.applicationContext))
                    .also { INSTANCE = it }
            }
    }

    /** Mirrors BalochDatabase.isEncrypted — false only on the plain-Room fallback. */
    val isEncrypted: Boolean
        get() = BalochDatabase.isEncrypted

    // ------------------------------------------------------------------ facts

    /**
     * Saves a fact. Temporary facts automatically get expiresAt = now + 30 days;
     * permanent facts get expiresAt = null. Callers may pass an explicit
     * expiresAt to override.
     */
    suspend fun saveFact(
        key: String,
        value: String,
        category: String = "other",
        confidence: Float = 1f,
        sourceQuote: String = "",
        tier: Tier = Tier.TEMPORARY,
        expiresAt: Long? = null,
    ): Long {
        val now = System.currentTimeMillis()
        val effectiveExpiry = expiresAt
            ?: if (tier == Tier.TEMPORARY) now + TEMP_TTL_MS else null
        return db.factDao().insert(
            FactEntity(
                key = key.trim().lowercase().take(60),
                value = value.trim().take(300),
                category = category,
                confidence = confidence.coerceIn(0f, 1f),
                createdAt = now,
                sourceQuote = sourceQuote.trim().take(300),
                tier = tier,
                expiresAt = effectiveExpiry,
            )
        )
    }

    /** Reactive stream of all facts (newest first) for the Memory screen. */
    fun factsFlow(): Flow<List<FactEntity>> = db.factDao().allFlow()

    /** One-shot list of all facts (newest first). */
    suspend fun allFacts(): List<FactEntity> = db.factDao().all()

    /** Legacy shape for the old MemoryStore API: "key: value" strings. */
    suspend fun facts(): List<String> =
        db.factDao().all().map { "${it.key}: ${it.value}" }

    suspend fun factsByCategory(category: String): List<FactEntity> =
        db.factDao().byCategory(category)

    suspend fun findFactByKey(key: String): FactEntity? =
        db.factDao().findByKey(key.trim().lowercase())

    suspend fun searchFacts(query: String): List<FactEntity> =
        db.factDao().search(query.trim())

    suspend fun deleteFact(id: Long) = db.factDao().deleteById(id)

    /** Pin a fact forever: tier -> PERMANENT, expiry cleared. */
    suspend fun upgradeToPermanent(id: Long) = db.factDao().upgradeToPermanent(id)

    suspend fun latestTemporaryFact(): FactEntity? =
        db.factDao().latestByTier(Tier.TEMPORARY)

    suspend fun expiredTemporary(now: Long = System.currentTimeMillis()): List<FactEntity> =
        db.factDao().expiredTemporary(Tier.TEMPORARY, now)

    /** Deletes expired temporary facts. Returns the row count. */
    suspend fun deleteExpiredTemporary(now: Long = System.currentTimeMillis()): Int =
        db.factDao().deleteExpired(Tier.TEMPORARY, now)

    suspend fun factCount(): Int = db.factDao().count()

    // ---------------------------------------------------------- conversations

    /**
     * Logs one exchange. [producedPermanentFact] links the conversation to a
     * permanent fact — the janitor will never prune such conversations.
     */
    suspend fun logConversation(
        user: String,
        reply: String,
        lang: String,
        mode: String,
        tier: Tier = Tier.TEMPORARY,
        producedPermanentFact: Boolean = false,
    ): Long {
        val now = System.currentTimeMillis()
        return db.conversationDao().insert(
            ConversationEntity(
                timestamp = now,
                userText = user.trim().take(1000),
                balochReply = reply.trim().take(1000),
                language = lang,
                mode = mode,
                tier = tier,
                expiresAt = if (tier == Tier.TEMPORARY) now + TEMP_TTL_MS else null,
                producedPermanentFact = producedPermanentFact,
            )
        )
    }

    suspend fun recentConversations(limit: Int = 50): List<ConversationEntity> =
        db.conversationDao().recent(limit)

    suspend fun searchConversations(query: String): List<ConversationEntity> =
        db.conversationDao().search(query.trim())

    /** "make this permanent": keep the latest temporary exchange forever. */
    suspend fun upgradeLatestTemporaryConversation() =
        db.conversationDao().upgradeLatestTemporary()

    /** Prunes expired temporary conversations (never permanent-fact ones). */
    suspend fun pruneOldConversations(now: Long = System.currentTimeMillis()): Int =
        db.conversationDao().deleteExpired(Tier.TEMPORARY, now)

    suspend fun conversationCount(): Int = db.conversationDao().count()

    // ---------------------------------------------------------------- entities

    suspend fun saveEntity(
        name: String,
        type: String = "person",
        notes: String = "",
        tier: Tier = Tier.PERMANENT,
    ): Long = db.entityDao().insert(
        EntityRecord(
            name = name.trim().take(120),
            type = type,
            notes = notes.trim().take(500),
            createdAt = System.currentTimeMillis(),
            tier = tier,
        )
    )

    suspend fun entities(): List<EntityRecord> = db.entityDao().all()

    suspend fun findEntityByName(name: String): EntityRecord? =
        db.entityDao().findByName(name.trim())

    suspend fun searchEntities(query: String): List<EntityRecord> =
        db.entityDao().search(query.trim())

    suspend fun deleteEntity(id: Long) = db.entityDao().deleteById(id)

    // ------------------------------------------------------------------ search

    /** One query across all three tables: (facts, entities, conversations). */
    suspend fun searchAll(
        query: String,
    ): Triple<List<FactEntity>, List<EntityRecord>, List<ConversationEntity>> =
        Triple(
            db.factDao().search(query),
            db.entityDao().search(query),
            db.conversationDao().search(query),
        )

    // ------------------------------------------------------------------ danger

    /** Wipes ALL memory tables. PIN-gated in the UI; no undo. */
    suspend fun forgetAll() {
        db.factDao().deleteAll()
        db.conversationDao().deleteAll()
        db.entityDao().deleteAll()
    }

    // ------------------------------------------------------------- json backup

    /** Serialises the whole memory to JSON (used by MemoryBackup's export). */
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put(
            "facts",
            JSONArray().also { arr ->
                db.factDao().all().forEach { f ->
                    arr.put(
                        JSONObject()
                            .put("key", f.key)
                            .put("value", f.value)
                            .put("category", f.category)
                            .put("confidence", f.confidence.toDouble())
                            .put("createdAt", f.createdAt)
                            .put("sourceQuote", f.sourceQuote)
                            .put("tier", f.tier.name)
                            .put("expiresAt", f.expiresAt ?: JSONObject.NULL)
                    )
                }
            },
        )
        root.put(
            "conversations",
            JSONArray().also { arr ->
                db.conversationDao().recent(Int.MAX_VALUE).forEach { c ->
                    arr.put(
                        JSONObject()
                            .put("timestamp", c.timestamp)
                            .put("userText", c.userText)
                            .put("balochReply", c.balochReply)
                            .put("language", c.language)
                            .put("mode", c.mode)
                            .put("tier", c.tier.name)
                            .put("expiresAt", c.expiresAt ?: JSONObject.NULL)
                            .put("producedPermanentFact", c.producedPermanentFact)
                    )
                }
            },
        )
        root.put(
            "entities",
            JSONArray().also { arr ->
                db.entityDao().all().forEach { e ->
                    arr.put(
                        JSONObject()
                            .put("name", e.name)
                            .put("type", e.type)
                            .put("notes", e.notes)
                            .put("createdAt", e.createdAt)
                            .put("tier", e.tier.name)
                    )
                }
            },
        )
        root.put("exportedAt", System.currentTimeMillis())
        root.put("version", 1)
        root.toString()
    }

    /**
     * Restores memory from an export JSON. Rows are inserted fresh (new ids),
     * so importing twice duplicates — the caller (MemoryBackup) should
     * forgetAll() first if a clean restore is wanted. Returns rows imported.
     */
    suspend fun importJson(json: String): Int = withContext(Dispatchers.IO) {
        var count = 0
        val root = JSONObject(json)
        val now = System.currentTimeMillis()

        fun tierOf(o: JSONObject): Tier =
            runCatching { Tier.valueOf(o.optString("tier", "TEMPORARY")) }
                .getOrDefault(Tier.TEMPORARY)

        fun expiresOf(o: JSONObject): Long? =
            if (o.isNull("expiresAt")) null else o.optLong("expiresAt").takeIf { it > 0 }

        root.optJSONArray("facts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.factDao().insert(
                    FactEntity(
                        key = o.optString("key"),
                        value = o.optString("value"),
                        category = o.optString("category", "other"),
                        confidence = o.optDouble("confidence", 1.0).toFloat(),
                        createdAt = o.optLong("createdAt", now),
                        sourceQuote = o.optString("sourceQuote", ""),
                        tier = tierOf(o),
                        expiresAt = expiresOf(o),
                    )
                )
                count++
            }
        }
        root.optJSONArray("conversations")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.conversationDao().insert(
                    ConversationEntity(
                        timestamp = o.optLong("timestamp", now),
                        userText = o.optString("userText"),
                        balochReply = o.optString("balochReply"),
                        language = o.optString("language", "unknown"),
                        mode = o.optString("mode", "unknown"),
                        tier = tierOf(o),
                        expiresAt = expiresOf(o),
                        producedPermanentFact = o.optBoolean("producedPermanentFact", false),
                    )
                )
                count++
            }
        }
        root.optJSONArray("entities")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.entityDao().insert(
                    EntityRecord(
                        name = o.optString("name"),
                        type = o.optString("type", "person"),
                        notes = o.optString("notes", ""),
                        createdAt = o.optLong("createdAt", now),
                        tier = tierOf(o),
                    )
                )
                count++
            }
        }
        count
    }
}
