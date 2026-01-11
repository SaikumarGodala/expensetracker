package com.saikumar.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores merchant keyword to category mappings for auto-categorization.
 * Moved from JSON preferences to database for better performance and querying.
 */
@Entity(
    tableName = "merchant_patterns",
    indices = [Index(value = ["keyword"], unique = true)]
)
data class MerchantPattern(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The keyword to match in SMS/transaction (stored uppercase) */
    val keyword: String,
    /** The category name to assign when matched */
    val categoryName: String,
    /** True if user-defined, false if system default */
    val isUserDefined: Boolean = true
)
