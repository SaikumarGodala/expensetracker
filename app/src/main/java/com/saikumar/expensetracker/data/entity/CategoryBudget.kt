package com.saikumar.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "category_budgets",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["categoryId", "month", "year"], unique = true) // One budget per category per month
    ]
)
data class CategoryBudget(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val categoryId: Long,
    val amountPaisa: Long,
    
    // Month stored as string "YYYY-MM" for easy sorting/querying, or separate columsn?
    // User plan suggested month/year separately for flexibility.
    val month: Int, // 1-12
    val year: Int,  // 2024, etc.
    
    val isSoftCap: Boolean = true // Default to soft cap (Flex Budget)
)
