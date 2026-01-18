package com.saikumar.expensetracker.data.db

import androidx.room.*
import com.saikumar.expensetracker.data.entity.UndoLog
import kotlinx.coroutines.flow.Flow

/**
 * DAO for undo log operations
 */
@Dao
interface UndoLogDao {
    
    @Query("SELECT * FROM undo_log WHERE isUndone = 0 ORDER BY performedAt DESC LIMIT 10")
    fun getRecentUndoableActions(): Flow<List<UndoLog>>
    
    @Query("SELECT * FROM undo_log WHERE id = :id")
    suspend fun getById(id: Long): UndoLog?
    
    @Query("SELECT * FROM undo_log WHERE isUndone = 0 ORDER BY performedAt DESC LIMIT 1")
    suspend fun getLatestUndoable(): UndoLog?
    
    @Insert
    suspend fun insert(log: UndoLog): Long
    
    @Query("UPDATE undo_log SET isUndone = 1 WHERE id = :id")
    suspend fun markAsUndone(id: Long)
    
    @Query("DELETE FROM undo_log WHERE performedAt < :timestampBefore")
    suspend fun deleteOlderThan(timestampBefore: Long)
    
    /** Clean up undo logs older than 7 days */
    suspend fun cleanupOldLogs() {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        deleteOlderThan(sevenDaysAgo)
    }
}
