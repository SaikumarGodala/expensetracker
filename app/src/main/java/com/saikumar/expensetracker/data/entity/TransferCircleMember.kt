package com.saikumar.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a member of the Transfer Circle
 * These are trusted contacts (friends/family) whose transfers are considered P2P, not expenses
 */
@Entity(tableName = "transfer_circle_members")
data class TransferCircleMember(
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    
    val recipientName: String,              // Name of the person (e.g., "Rajesh Kumar")
    
    val phoneNumber: String? = null,        // Optional phone number
    
    val totalTransfers: Int = 0,            // Total number of transfers to this person
    
    val totalAmountPaisa: Long = 0,         // Total amount transferred (in paisa)
    
    val addedDate: Long,                    // Timestamp when added to circle
    
    val isAutoDetected: Boolean = false,    // Was this auto-suggested by the system?
    
    val notes: String? = null,              // Optional user notes about this contact

    val isIgnored: Boolean = false          // If true, this person is ignored from suggestions and NOT in circle
)
