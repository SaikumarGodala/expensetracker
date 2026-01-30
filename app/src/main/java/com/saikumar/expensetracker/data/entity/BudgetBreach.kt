package com.saikumar.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

import androidx.room.Index

@Entity(
    tableName = "budget_breaches",
    indices = [
        Index(value = ["month", "stage"], unique = true)
    ]
)
data class BudgetBreach(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val month: String,         // "2024-01"
    val stage: Int,            // 1 = Initial Breach, 2 = Month-End Review
    val limitAmount: Long,     // The budget limit at that time (paisa)
    val breachedAmount: Long,  // The actual expense amount (paisa)
    val reason: String,        // User's explanation
    val timestamp: Long        // When it was recorded
)
