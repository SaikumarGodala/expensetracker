package com.saikumar.expensetracker.data.entity

import androidx.room.Embedded
import androidx.room.Relation

data class LinkWithDetails(
    @Embedded val link: TransactionLink,
    
    @Relation(
        parentColumn = "primary_txn_id",
        entityColumn = "id"
    )
    val primaryTransaction: Transaction,
    
    @Relation(
        parentColumn = "secondary_txn_id",
        entityColumn = "id"
    )
    val secondaryTransaction: Transaction
)
