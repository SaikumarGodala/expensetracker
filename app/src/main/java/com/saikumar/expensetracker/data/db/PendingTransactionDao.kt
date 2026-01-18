package com.saikumar.expensetracker.data.db

import androidx.room.*
import com.saikumar.expensetracker.data.entity.PendingTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingTransactionDao {
    @Query("SELECT * FROM pending_transactions WHERE status = 'PENDING' ORDER BY dueDate ASC")
    fun getAllPending(): Flow<List<PendingTransaction>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(pending: PendingTransaction): Long

    @Update
    suspend fun update(pending: PendingTransaction)

    @Query("SELECT * FROM pending_transactions WHERE smsHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): PendingTransaction?
    
    @Query("UPDATE pending_transactions SET status = 'PROCESSED', realizedTransactionId = :realizedId WHERE id = :id")
    suspend fun markAsProcessed(id: Long, realizedId: Long)

    @Query("UPDATE pending_transactions SET status = 'CANCELLED' WHERE id = :id")
    suspend fun cancel(id: Long)
}
