package com.saikumar.expensetracker.data.db

import androidx.room.*
import com.saikumar.expensetracker.data.entity.LinkType
import com.saikumar.expensetracker.data.entity.TransactionLink

/**
 * DAO for TransactionLink entity
 * 
 * Provides CRUD operations for transaction relationships (self-transfers, refunds, CC payments)
 */
@Dao
interface TransactionLinkDao {
    
    /**
     * Insert a new transaction link
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: TransactionLink): Long
    
    /**
     * Insert multiple links in a single transaction
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<TransactionLink>)
    
    /**
     * Delete a link (for manual unlinking)
     */
    @Delete
    suspend fun deleteLink(link: TransactionLink)
    
    /**
     * Get all links involving a specific transaction
     * Used to check if transaction is already paired
     */
    @Query("""
        SELECT * FROM transaction_links 
        WHERE primary_txn_id = :transactionId OR secondary_txn_id = :transactionId
    """)
    suspend fun getLinksForTransaction(transactionId: Long): List<TransactionLink>
    
    /**
     * Get all links of a specific type
     * Used for reporting and UI display
     */
    @Query("SELECT * FROM transaction_links WHERE link_type = :linkType")
    suspend fun getLinksByType(linkType: LinkType): List<TransactionLink>
    


    /**
     * Get links with full transaction details (limited to last 500 for performance)
     * Used for Link Manager UI
     */
    @Transaction
    @Query("SELECT * FROM transaction_links ORDER BY created_at DESC LIMIT 500")
    fun getAllLinksWithDetails(): kotlinx.coroutines.flow.Flow<List<com.saikumar.expensetracker.data.entity.LinkWithDetails>>

    @Query("DELETE FROM transaction_links WHERE id = :linkId")
    suspend fun deleteLinkById(linkId: Long)

    /**
     * Get all transaction links, limited to last 1000 to prevent unbounded growth
     * Performance optimization: Most UIs only need recent links
     */
    @Query("SELECT * FROM transaction_links ORDER BY created_at DESC LIMIT 1000")
    fun getAllLinks(): kotlinx.coroutines.flow.Flow<List<TransactionLink>>

    /**
     * Get transaction links within a time period (by joining with transactions)
     * Useful for period-specific views like Dashboard
     */
    @Query("""
        SELECT DISTINCT tl.* FROM transaction_links tl
        INNER JOIN transactions t1 ON tl.primary_txn_id = t1.id
        WHERE t1.timestamp >= :startTimestamp AND t1.timestamp <= :endTimestamp
        ORDER BY tl.created_at DESC
    """)
    fun getLinksInPeriod(startTimestamp: Long, endTimestamp: Long): kotlinx.coroutines.flow.Flow<List<TransactionLink>>
    
    /**
     * Check if a transaction is already linked
     * Returns true if transaction is part of any link
     */
    @Query("""
        SELECT COUNT(*) > 0 FROM transaction_links 
        WHERE primary_txn_id = :transactionId OR secondary_txn_id = :transactionId
    """)
    suspend fun isTransactionLinked(transactionId: Long): Boolean
    
    /**
     * Delete all links involving a specific transaction
     * Used when deleting a transaction
     */
    @Query("""
        DELETE FROM transaction_links 
        WHERE primary_txn_id = :transactionId OR secondary_txn_id = :transactionId
    """)
    suspend fun deleteLinksForTransaction(transactionId: Long)
}
