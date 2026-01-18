package com.saikumar.expensetracker.util

import android.util.Log
import com.saikumar.expensetracker.ExpenseTrackerApplication
import com.saikumar.expensetracker.data.entity.TransactionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest

object MigrationHelper {
    private const val TAG = "MigrationHelper"

    /**
     * Repairs data corruption by:
     * 1. Back-filling missing hashes for SMS transactions
     * 2. Soft-deleting duplicates that violate uniqueness
     */
    suspend fun repairData(app: ExpenseTrackerApplication) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting data repair...")
        
        val transactionDao = app.database.transactionDao()
        
        // 1. Fetch all active transactions
        // Note: getTransactionsInPeriod returns DESC order (newest first). 
        // We probably want to process oldest first to establish "original" items.
        val allTxnsWithCategory = transactionDao.getTransactionsInPeriod(0, Long.MAX_VALUE).first()
        val allTxns = allTxnsWithCategory.map { it.transaction }.reversed() // Oldest first
        
        Log.d(TAG, "Fetched ${allTxns.size} transactions for repair analysis")
        
        var updatedCount = 0
        var deletedCount = 0
        
        // Map to track unique hashes to find duplicates
        val hashRegistry = mutableMapOf<String, Long>() // hash -> transactionId (first seen / original)

        for (txn in allTxns) {
            var currentHash = txn.smsHash
            var needsUpdate = false
            
            // 1. Backfill Hash if missing and source is SMS
            if (currentHash.isNullOrBlank() && txn.source == TransactionSource.SMS) {
                if (!txn.fullSmsBody.isNullOrBlank()) {
                    currentHash = generateSmsHash(txn.fullSmsBody)
                    needsUpdate = true
                } else if (!txn.smsSnippet.isNullOrBlank()) {
                    // Fallback to snippet if full body missing (legacy data?)
                    currentHash = generateSmsHash(txn.smsSnippet)
                    needsUpdate = true
                }
            }
            
            // 2. Dedup Logic
            if (!currentHash.isNullOrBlank()) {
                if (hashRegistry.containsKey(currentHash!!)) {
                    // Duplicate found!
                    val originalId = hashRegistry[currentHash]
                    Log.d(TAG, "Duplicate detected: Txn ${txn.id} is duplicate of $originalId (Hash: $currentHash). Soft deleting.")
                    transactionDao.softDelete(txn.id, System.currentTimeMillis())
                    deletedCount++
                    continue // Don't process further, it's deleted
                } else {
                    hashRegistry[currentHash!!] = txn.id
                }
            }
            
            // Apply update if we calculated a new hash
            if (needsUpdate && !currentHash.isNullOrBlank()) {
                try {
                    val updatedTxn = txn.copy(smsHash = currentHash)
                    transactionDao.updateTransaction(updatedTxn)
                    updatedCount++
                    // Registry already updated above
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update hash for txn ${txn.id} (Collision with deleted row?). Soft deleting.", e)
                    // Collision implies it's a duplicate of something (maybe deleted). 
                    // To be safe and compliant with UNIQUE constraint, we soft delete.
                    transactionDao.softDelete(txn.id, System.currentTimeMillis())
                    deletedCount++
                }
            }
        }
        
        Log.d(TAG, "Data Repair Complete: Updated $updatedCount hashes, Soft-deleted $deletedCount duplicates")
    }
    
    // Consistent hashing logic
    private fun generateSmsHash(body: String): String {
        val bytes = body.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }.take(16)
    }
}
