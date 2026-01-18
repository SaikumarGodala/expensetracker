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
    @Query("SELECT * FROM transactions WHERE timestamp >= :startTimestamp AND timestamp <= :endTimestamp ORDER BY timestamp DESC")
    fun getTransactionsInPeriod(startTimestamp: Long, endTimestamp: Long): Flow<List<TransactionWithCategory>>

    @Insert
    suspend fun insertTransaction(transaction: Transaction)

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    /**
     * Check if a transaction with this SMS hash already exists.
     * Used to prevent duplicate SMS imports.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE smsHash = :hash LIMIT 1)")
    suspend fun existsBySmsHash(hash: String): Boolean

    /**
     * Get all transactions that need category reassignment
     * (e.g., after a category is deleted and transactions fallback to default)
     */
    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId")
    suspend fun getTransactionsByCategoryId(categoryId: Long): List<Transaction>
}
