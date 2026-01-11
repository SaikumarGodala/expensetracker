package com.saikumar.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Log of batch operations for undo capability.
 * Stores previous states to allow rollback of categorization changes.
 */
@Entity(tableName = "undo_log")
data class UndoLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    
    /** Type of action: BATCH_CATEGORIZE, RULE_CREATE, RULE_DELETE */
    val actionType: String,
    
    /** JSON data containing affected transaction IDs and old categoryIds */
    val undoData: String,
    
    /** When this action was performed */
    val performedAt: Long = System.currentTimeMillis(),
    
    /** Has this been undone? */
    val isUndone: Boolean = false,
    
    /** Human-readable description for UI */
    val description: String,
    
    /** Number of transactions affected */
    val affectedCount: Int = 0
) {
    companion object {
        const val ACTION_BATCH_CATEGORIZE = "BATCH_CATEGORIZE"
        const val ACTION_RULE_CREATE = "RULE_CREATE"
        const val ACTION_RULE_DELETE = "RULE_DELETE"
    }
}
