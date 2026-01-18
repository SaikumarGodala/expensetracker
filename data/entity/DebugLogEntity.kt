package com.saikumar.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Entity for persisting debug logs to the database.
 * We store the complex log structure as a JSON string for simplicity and flexibility.
 */
@Entity(
    tableName = "debug_logs",
    indices = [
        Index(value = ["transactionId"], unique = true),
        Index(value = ["timestamp"])
    ]
)
data class DebugLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val transactionId: String,
    
    val timestamp: Long,
    
    /** 
     * The complete ClassificationDebugLog object serialized as JSON.
     * This allows us to store the complex nested structure without multiple tables.
     */
    val logJson: String,
    
    /**
     * Whether this log was created in debug mode
     */
    val isDebug: Boolean = true
)
