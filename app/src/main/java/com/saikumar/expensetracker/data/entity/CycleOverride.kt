package com.saikumar.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cycle_overrides")
data class CycleOverride(
    @PrimaryKey val yearMonth: String, // Format: "YYYY-MM"
    val startDate: Long,
    val endDate: Long
)
