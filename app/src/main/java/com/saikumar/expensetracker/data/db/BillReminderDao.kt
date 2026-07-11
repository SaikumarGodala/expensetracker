package com.saikumar.expensetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saikumar.expensetracker.data.entity.BillReminder

@Dao
interface BillReminderDao {

    /** IGNORE on conflict: the unique smsHash index makes re-scans idempotent */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(reminder: BillReminder): Long

    /** Unmatched reminders with this exact amount received inside the window */
    @Query(
        """SELECT * FROM bill_reminders
           WHERE matchedTxnId IS NULL
           AND amountPaisa = :amountPaisa
           AND receivedAt BETWEEN :fromTs AND :toTs"""
    )
    suspend fun findCandidates(amountPaisa: Long, fromTs: Long, toTs: Long): List<BillReminder>

    @Query("SELECT * FROM bill_reminders WHERE matchedTxnId IS NULL")
    suspend fun getUnmatched(): List<BillReminder>

    @Query("UPDATE bill_reminders SET matchedTxnId = :txnId WHERE id = :id")
    suspend fun markMatched(id: Long, txnId: Long)

    /** Drop reminders that never found a payment (bill was paid elsewhere / ignored) */
    @Query("DELETE FROM bill_reminders WHERE matchedTxnId IS NULL AND receivedAt < :cutoff")
    suspend fun purgeStale(cutoff: Long)

    /**
     * Bills still unpaid (no linked payment) with a known due date inside the window.
     * [graceStart] lets just-overdue bills (a few days past due) stay visible.
     */
    @Query(
        """SELECT * FROM bill_reminders
           WHERE matchedTxnId IS NULL
           AND dueDateMillis IS NOT NULL
           AND dueDateMillis BETWEEN :graceStart AND :horizon
           ORDER BY dueDateMillis ASC"""
    )
    suspend fun getUpcoming(graceStart: Long, horizon: Long): List<BillReminder>
}
