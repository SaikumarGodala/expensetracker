package com.saikumar.expensetracker.data.entity

import androidx.room.*

/**
 * Entity representing an alias/variation of a Transfer Circle Member's name
 * Example: Member "Rajesh Kumar" might have aliases "Rajesh K", "Rajesh", etc.
 */
@Entity(
    tableName = "transfer_circle_aliases",
    foreignKeys = [
        ForeignKey(
            entity = TransferCircleMember::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("memberId"), 
        Index("aliasName", unique = true)
    ]
)
data class TransferCircleAlias(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val memberId: Long,
    
    val aliasName: String
)
