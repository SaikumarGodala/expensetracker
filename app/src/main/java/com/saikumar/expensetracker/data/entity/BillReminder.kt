package com.saikumar.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A captured bill-reminder SMS ("Your Airtel DTH recharge of Rs.599 is due by 15-Jul").
 *
 * These messages are correctly rejected by LedgerGate as non-transactions (nothing has
 * been paid yet), but they carry exactly the information a later payment lacks: WHAT the
 * amount is for. Instead of discarding them, we store them here and link them to the
 * actual payment by amount + time window, which lets the payment inherit the reminder's
 * biller name and category (e.g. an "AIRTEL" debit of 599 becomes DTH/Subscriptions
 * rather than defaulting to Mobile + WiFi).
 */
@Entity(
    tableName = "bill_reminders",
    indices = [
        Index(value = ["smsHash"], unique = true),
        Index(value = ["amountPaisa"])
    ]
)
data class BillReminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Dedup key so re-scans don't capture the same reminder twice */
    val smsHash: String,
    /** Biller token found in the reminder ("AIRTEL", "BESCOM", "LIC"...), if any */
    val billerName: String?,
    val amountPaisa: Long,
    /** Parsed "due by" date, when the reminder states one */
    val dueDateMillis: Long?,
    /** Category name inferred from the reminder wording ("Utilities", "Subscriptions"...) */
    val categoryHint: String?,
    val smsBody: String,
    val receivedAt: Long,
    /** Set once linked to a real payment; NULL = still waiting for its payment */
    val matchedTxnId: Long? = null
)
