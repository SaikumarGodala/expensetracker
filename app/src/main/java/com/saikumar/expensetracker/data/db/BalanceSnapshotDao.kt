package com.saikumar.expensetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saikumar.expensetracker.data.entity.BalanceSnapshot

@Dao
interface BalanceSnapshotDao {

    /** IGNORE on conflict: the unique smsHash index makes re-scans idempotent */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(snapshot: BalanceSnapshot): Long

    @Query("SELECT * FROM balance_snapshots WHERE accountLast4 = :accountLast4 ORDER BY timestamp ASC")
    suspend fun getForAccount(accountLast4: String): List<BalanceSnapshot>

    @Query("SELECT DISTINCT accountLast4 FROM balance_snapshots")
    suspend fun getAccounts(): List<String>

    @Query("SELECT COUNT(*) FROM balance_snapshots")
    suspend fun count(): Int
}
