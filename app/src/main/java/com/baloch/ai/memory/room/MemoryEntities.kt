package com.baloch.ai.memory.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/**
 * Two-tier memory model (Kamran's order: "zaroori hamesha, bekar har month khatam").
 *
 * PERMANENT — stays forever. Set when the user pins something ("yaad rakhna",
 * "keep in mind") or when the fact is auto-important (people/clients, deadlines,
 * preferences, personal identity, routines, scheduled tasks).
 *
 * TEMPORARY — casual chit-chat, jokes, small talk, transient states ("I'm bored").
 * Auto-expires 30 days after creation; MemoryJanitor deletes the rows.
 */
enum class Tier {
    PERMANENT,
    TEMPORARY,
}

/** Room stores the tier as a plain String column. */
class TierConverters {
    @TypeConverter
    fun fromTier(tier: Tier): String = tier.name

    @TypeConverter
    fun toTier(value: String): Tier =
        runCatching { Tier.valueOf(value) }.getOrDefault(Tier.TEMPORARY)
}

/** Time-to-live for TEMPORARY rows: 30 days, in milliseconds. */
const val TEMP_TTL_MS: Long = 30L * 24 * 60 * 60 * 1000

@Entity(tableName = "facts")
data class FactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val value: String,
    /** personal|preference|people|work|health|other */
    val category: String,
    val confidence: Float,
    val createdAt: Long,
    /** The exact user quote the fact was extracted from (provenance). */
    val sourceQuote: String,
    val tier: Tier = Tier.TEMPORARY,
    /** TEMPORARY: createdAt + 30 days. PERMANENT: null (never expires). */
    val expiresAt: Long? = null,
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val userText: String,
    val balochReply: String,
    /** en|ur|hi */
    val language: String,
    val mode: String,
    val tier: Tier = Tier.TEMPORARY,
    /** TEMPORARY: timestamp + 30 days. PERMANENT: null (never expires). */
    val expiresAt: Long? = null,
    /**
     * True when this exchange produced a PERMANENT fact. A conversation inherits
     * its tier from whether it produced something worth keeping: the janitor
     * never prunes a conversation with producedPermanentFact = true.
     */
    val producedPermanentFact: Boolean = false,
)

@Entity(tableName = "entities")
data class EntityRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** person|place|org */
    val type: String,
    val notes: String,
    val createdAt: Long,
    /**
     * Entities are people/places/orgs — auto-important by nature, so they
     * default to PERMANENT. The janitor never auto-deletes entities;
     * the user removes them from the Memory screen.
     */
    val tier: Tier = Tier.PERMANENT,
)
