package com.saikumar.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks NEFT income sources for salary detection.
 * 
 * When the same NEFT source (IFSC + sender pattern) appears in 2+ consecutive months,
 * it's automatically classified as Salary.
 */
@Entity(tableName = "neft_sources")
data class NeftSource(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val ifscCode: String,           // e.g., "BOFA0CN6215"
    val senderPattern: String,       // e.g., "OPEN TEXT TECHNOLOGIES" or normalized identifier
    
    val firstSeenMonth: Int,         // YYYYMM format, e.g., 202512 for Dec 2025
    val lastSeenMonth: Int,          // YYYYMM format
    
    val occurrenceCount: Int = 1,    // Number of times this pattern has been seen
    val consecutiveMonths: Int = 1,  // Number of consecutive months seen
    
    val isSalary: Boolean = false,   // True when detected as recurring salary
    
    val lastSeenAt: Long = System.currentTimeMillis() // Timestamp of last occurrence
)
