package com.saikumar.expensetracker.data.db

import androidx.room.*
import com.saikumar.expensetracker.data.entity.MerchantMemory
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Merchant Dictionary Memory operations.
 * 
 * Supports:
 * - Looking up learned merchant→category mappings
 * - Incrementing occurrence counts
 * - Locking mappings after threshold
 * - User-confirmed overrides
 */
@Dao
interface MerchantMemoryDao {
    
    /**
     * Get a merchant memory entry by normalized name
     */
    @Query("SELECT * FROM merchant_memory WHERE normalizedMerchant = :normalizedName LIMIT 1")
    suspend fun getByMerchant(normalizedName: String): MerchantMemory?
    
    /**
     * Get all locked merchant mappings (for category suggestions)
     */
    @Query("SELECT * FROM merchant_memory WHERE isLocked = 1")
    fun getAllLocked(): Flow<List<MerchantMemory>>
    
    /**
     * Get all merchant memory entries
     */
    @Query("SELECT * FROM merchant_memory ORDER BY occurrenceCount DESC")
    fun getAll(): Flow<List<MerchantMemory>>
    
    /**
     * Insert a new merchant memory entry
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(memory: MerchantMemory): Long
    
    /**
     * Update an existing merchant memory entry
     */
    @Update
    suspend fun update(memory: MerchantMemory)
    
    /**
     * Upsert: Insert or update merchant memory
     */
    @Transaction
    suspend fun upsert(memory: MerchantMemory) {
        val existing = getByMerchant(memory.normalizedMerchant)
        if (existing == null) {
            insert(memory)
        } else {
            update(memory.copy(id = existing.id))
        }
    }
    
    /**
     * Increment occurrence count and check for auto-lock
     */
    @Query("""
        UPDATE merchant_memory 
        SET occurrenceCount = occurrenceCount + 1,
            lastSeenTimestamp = :timestamp,
            isLocked = CASE WHEN occurrenceCount + 1 >= :threshold THEN 1 ELSE isLocked END
        WHERE normalizedMerchant = :normalizedName
    """)
    suspend fun incrementOccurrence(normalizedName: String, timestamp: Long, threshold: Int = MerchantMemory.AUTO_LOCK_THRESHOLD)
    
    /**
     * User confirms/overrides a merchant mapping
     */
    @Query("""
        UPDATE merchant_memory 
        SET categoryId = :categoryId,
            isLocked = 1,
            userConfirmed = 1,
            lastSeenTimestamp = :timestamp,
            transactionType = :transactionType
        WHERE normalizedMerchant = :normalizedName
    """)
    suspend fun userConfirmMapping(
        normalizedName: String, 
        categoryId: Long, 
        timestamp: Long,
        transactionType: String?
    )
    
    /**
     * Delete a merchant memory entry
     */
    @Query("DELETE FROM merchant_memory WHERE normalizedMerchant = :normalizedName")
    suspend fun deleteByMerchant(normalizedName: String)
    
    /**
     * Delete all merchant memory entries
     */
    @Query("DELETE FROM merchant_memory")
    suspend fun deleteAll()
    
    /**
     * Get count of learned merchants
     */
    @Query("SELECT COUNT(*) FROM merchant_memory WHERE isLocked = 1")
    suspend fun getLockedCount(): Int
    /**
     * Get all confirmed/locked memories for batch processing
     */
    @Query("SELECT * FROM merchant_memory WHERE isLocked = 1 OR userConfirmed = 1")
    suspend fun getAllConfirmedMemories(): List<MerchantMemory>
}
