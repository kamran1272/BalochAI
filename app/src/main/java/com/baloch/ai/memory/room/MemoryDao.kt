package com.baloch.ai.memory.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FactDao {

    @Insert
    suspend fun insert(fact: FactEntity): Long

    @Query("SELECT * FROM facts ORDER BY createdAt DESC")
    suspend fun all(): List<FactEntity>

    @Query("SELECT * FROM facts ORDER BY createdAt DESC")
    fun allFlow(): Flow<List<FactEntity>>

    @Query("SELECT * FROM facts WHERE category = :category ORDER BY createdAt DESC")
    suspend fun byCategory(category: String): List<FactEntity>

    @Query(
        """SELECT * FROM facts
           WHERE key LIKE '%' || :query || '%'
              OR value LIKE '%' || :query || '%'
              OR category LIKE '%' || :query || '%'
           ORDER BY createdAt DESC"""
    )
    suspend fun search(query: String): List<FactEntity>

    @Query("SELECT * FROM facts WHERE key = :key LIMIT 1")
    suspend fun findByKey(key: String): FactEntity?

    @Query("SELECT * FROM facts ORDER BY id DESC LIMIT 1")
    suspend fun latest(): FactEntity?

    @Query("SELECT * FROM facts WHERE tier = :tier ORDER BY id DESC LIMIT 1")
    suspend fun latestByTier(tier: Tier): FactEntity?

    @Query("SELECT COUNT(*) FROM facts")
    suspend fun count(): Int

    @Query(
        """SELECT * FROM facts
           WHERE tier = :tier AND expiresAt IS NOT NULL AND expiresAt < :now"""
    )
    suspend fun expiredTemporary(tier: Tier, now: Long): List<FactEntity>

    /** Promote a fact to PERMANENT — clears its expiry forever. */
    @Query("UPDATE facts SET tier = 'PERMANENT', expiresAt = NULL WHERE id = :id")
    suspend fun upgradeToPermanent(id: Long)

    /** Janitor delete: returns the number of rows removed. */
    @Query(
        """DELETE FROM facts
           WHERE tier = :tier AND expiresAt IS NOT NULL AND expiresAt < :now"""
    )
    suspend fun deleteExpired(tier: Tier, now: Long): Int

    @Query("DELETE FROM facts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM facts")
    suspend fun deleteAll()
}

@Dao
interface ConversationDao {

    @Insert
    suspend fun insert(conversation: ConversationEntity): Long

    @Query("SELECT * FROM conversations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ConversationEntity>

    @Query(
        """SELECT * FROM conversations
           WHERE userText LIKE '%' || :query || '%'
              OR balochReply LIKE '%' || :query || '%'
           ORDER BY timestamp DESC"""
    )
    suspend fun search(query: String): List<ConversationEntity>

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun count(): Int

    @Query("SELECT * FROM conversations ORDER BY id DESC LIMIT 1")
    suspend fun latest(): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE tier = :tier ORDER BY id DESC LIMIT 1")
    suspend fun latestByTier(tier: Tier): ConversationEntity?

    /**
     * Mark the most recent temporary conversation as permanent (used by the
     * "make this permanent" voice command — the pinned exchange is kept).
     */
    @Query(
        """UPDATE conversations
           SET tier = 'PERMANENT', expiresAt = NULL, producedPermanentFact = 1
           WHERE id = (SELECT id FROM conversations WHERE tier = 'TEMPORARY'
                       ORDER BY id DESC LIMIT 1)"""
    )
    suspend fun upgradeLatestTemporary()

    /**
     * Janitor prune: temporary conversations past their expiry — but never a
     * conversation that produced a permanent fact. Returns rows removed.
     */
    @Query(
        """DELETE FROM conversations
           WHERE producedPermanentFact = 0
             AND tier = :tier
             AND expiresAt IS NOT NULL
             AND expiresAt < :now"""
    )
    suspend fun deleteExpired(tier: Tier, now: Long): Int

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}

@Dao
interface EntityDao {

    @Insert
    suspend fun insert(entity: EntityRecord): Long

    @Query("SELECT * FROM entities ORDER BY createdAt DESC")
    suspend fun all(): List<EntityRecord>

    @Query(
        """SELECT * FROM entities
           WHERE name LIKE '%' || :query || '%'
              OR notes LIKE '%' || :query || '%'
           ORDER BY createdAt DESC"""
    )
    suspend fun search(query: String): List<EntityRecord>

    @Query("SELECT * FROM entities WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): EntityRecord?

    @Query("SELECT COUNT(*) FROM entities")
    suspend fun count(): Int

    @Query("DELETE FROM entities WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM entities")
    suspend fun deleteAll()
}
