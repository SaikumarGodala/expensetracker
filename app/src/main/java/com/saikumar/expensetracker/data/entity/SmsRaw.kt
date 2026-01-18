package com.saikumar.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sms_raw",
    indices = [
        Index(value = ["sender", "receivedAt", "rawText"], unique = true)
    ]
)
data class SmsRaw(
    @PrimaryKey(autoGenerate = true) val rawSmsId: Long = 0,
    val sender: String,
    val receivedAt: Long, // epoch milliseconds UTC
    val rawText: String,
    val normalizedText: String,
    val provider: String? = null
)
