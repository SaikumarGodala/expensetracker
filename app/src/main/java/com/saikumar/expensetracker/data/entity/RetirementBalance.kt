package com.saikumar.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RetirementType { EPF, NPS }

@Entity(
    tableName = "retirement_balances",
    indices = [
        Index(value = ["identifier"], unique = true)
    ]
)
data class RetirementBalance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: RetirementType,
    val balancePaisa: Long,
    val contributionPaisa: Long,
    val month: String,           // "Jan-24", "Feb-24"
    val timestamp: Long,
    val identifier: String,       // PRAN for NPS, UAN for EPF
    val smsBody: String? = null,
    val sender: String? = null
)
