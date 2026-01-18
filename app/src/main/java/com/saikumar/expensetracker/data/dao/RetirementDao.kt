package com.saikumar.expensetracker.data.dao

import androidx.room.*
import com.saikumar.expensetracker.data.entity.RetirementBalance
import com.saikumar.expensetracker.data.entity.RetirementType
import kotlinx.coroutines.flow.Flow

@Dao
interface RetirementDao {

    @Query("SELECT * FROM retirement_balances WHERE type = :type ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestBalance(type: RetirementType): RetirementBalance?

    @Query("SELECT * FROM retirement_balances WHERE type = :type ORDER BY timestamp DESC")
    fun getBalanceHistory(type: RetirementType): Flow<List<RetirementBalance>>

    @Query("SELECT * FROM retirement_balances ORDER BY timestamp DESC")
    fun getAllBalances(): Flow<List<RetirementBalance>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(balance: RetirementBalance)

    @Query("SELECT SUM(contributionPaisa) FROM retirement_balances WHERE type = :type")
    suspend fun getTotalContributions(type: RetirementType): Long?
}
