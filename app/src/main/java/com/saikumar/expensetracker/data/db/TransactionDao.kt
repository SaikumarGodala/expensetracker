package com.saikumar.expensetracker.data.db

import androidx.room.*
import com.saikumar.expensetracker.data.entity.Category
import com.saikumar.expensetracker.data.entity.Transaction
import kotlinx.coroutines.flow.Flow

data class TransactionWithCategory(
    @Embedded val transaction: Transaction,
    @Relation(
        parentColumn = "categoryId",
        entityColumn = "id"
    )
    val category: Category
)

@Dao
interface TransactionDao {
    @androidx.room.Transaction
    @Query("SELECT * FROM transactions WHERE timestamp >= :startTimestamp AND timestamp <= :endTimestamp AND deletedAt IS NULL ORDER BY timestamp DESC")
    fun getTransactionsInPeriod(startTimestamp: Long, endTimestamp: Long): Flow<List<TransactionWithCategory>>

    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: Long): Transaction?


    /**
     * Insert a transaction with idempotent duplicate handling.
     * 
     * CRITICAL FIX 2: Uses OnConflictStrategy.IGNORE to prevent duplicate SMS
     * from creating duplicate transactions. Returns -1 if the insert was ignored
     * due to a conflict (e.g., duplicate smsHash).
     * 
     * @return Row ID of inserted transaction, or -1 if ignored due to conflict
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    /**
     * Soft delete a transaction by setting deletedAt timestamp
     */
    @Query("UPDATE transactions SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)
    
    /**
     * Restore a soft-deleted transaction by setting deletedAt to null
     */
    @Query("UPDATE transactions SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)
    
    // Kept for legacy compatibility if needed, but repository should use softDelete
    @Delete
    suspend fun hardDelete(transaction: Transaction)

    /**
     * Check if a transaction with this SMS hash already exists.
     * Used to prevent duplicate SMS imports.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE smsHash = :hash AND deletedAt IS NULL LIMIT 1)")
    suspend fun existsBySmsHash(hash: String): Boolean

    /**
     * BUG FIX 1: Enhanced deduplication - Check for duplicate by reference number + amount.
     * Used to catch duplicates even when SMS text differs slightly.
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM transactions 
            WHERE referenceNo = :refNo 
            AND amountPaisa = :amountPaisa 
            AND referenceNo IS NOT NULL
            AND deletedAt IS NULL
            LIMIT 1
        )
    """)
    suspend fun existsByReferenceAndAmount(refNo: String?, amountPaisa: Long): Boolean

    /**
     * BUG FIX 1: Find potential duplicates by amount and timestamp (within a time window).
     * Used to detect duplicates when reference numbers are missing.
     * 
     * @param amountPaisa Exact amount in paisa
     * @param timestampStart Start of time window (transaction time - 5 minutes)
     * @param timestampEnd End of time window (transaction time + 5 minutes)
     */
    @Query("""
        SELECT * FROM transactions 
        WHERE amountPaisa = :amountPaisa 
        AND timestamp BETWEEN :timestampStart AND :timestampEnd
        AND deletedAt IS NULL
    """)
    suspend fun findPotentialDuplicates(
        amountPaisa: Long, 
        timestampStart: Long, 
        timestampEnd: Long
    ): List<Transaction>

    /**
     * Get all transactions that need category reassignment
     * (e.g., after a category is deleted and transactions fallback to default)
     */
    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId AND deletedAt IS NULL")
    suspend fun getTransactionsByCategoryId(categoryId: Long): List<Transaction>

    // P1 Fix: Optimized similarity search queries
    @Query("SELECT * FROM transactions WHERE merchantName = :merchantName AND deletedAt IS NULL ORDER BY timestamp DESC")
    suspend fun getTransactionsByMerchant(merchantName: String): List<Transaction>

    @Query("SELECT * FROM transactions WHERE smsSnippet LIKE '%' || :pattern || '%' AND deletedAt IS NULL ORDER BY timestamp DESC")
    suspend fun getTransactionsBySnippetPattern(pattern: String): List<Transaction>

    @Query("SELECT * FROM transactions WHERE merchantName = :merchantName AND deletedAt IS NULL")
    suspend fun getByMerchantName(merchantName: String): List<Transaction>

    @Update
    suspend fun updateTransactions(transactions: List<Transaction>)
}
