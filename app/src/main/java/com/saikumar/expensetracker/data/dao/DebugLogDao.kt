package com.saikumar.expensetracker.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saikumar.expensetracker.data.entity.DebugLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DebugLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DebugLogEntity)

    @Query("SELECT * FROM debug_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 50): Flow<List<DebugLogEntity>>

    @Query("SELECT * FROM debug_logs WHERE transactionId = :transactionId")
    suspend fun getLogByTransactionId(transactionId: String): DebugLogEntity?

    @Query("DELETE FROM debug_logs WHERE timestamp < :timestamp")
    suspend fun deleteLogsOlderThan(timestamp: Long)

    @Query("DELETE FROM debug_logs")
    suspend fun clearAllLogs()
    
    @Query("SELECT COUNT(*) FROM debug_logs")
    suspend fun getLogCount(): Int
}
