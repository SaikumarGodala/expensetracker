package com.saikumar.expensetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.saikumar.expensetracker.data.entity.NeftSource

@Dao
interface NeftSourceDao {
    
    @Query("SELECT * FROM neft_sources WHERE ifscCode = :ifsc AND senderPattern = :sender LIMIT 1")
    suspend fun findByIfscAndSender(ifsc: String, sender: String): NeftSource?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(neftSource: NeftSource): Long
    
    @Update
    suspend fun update(neftSource: NeftSource)
    
    @Query("SELECT * FROM neft_sources WHERE isSalary = 1")
    suspend fun getSalarySources(): List<NeftSource>
    
    @Query("SELECT * FROM neft_sources")
    suspend fun getAll(): List<NeftSource>
    
    @Query("DELETE FROM neft_sources")
    suspend fun clearAll()
    
    /**
     * Check if a given IFSC + sender pattern is a known salary source.
     */
    @Query("SELECT isSalary FROM neft_sources WHERE ifscCode = :ifsc AND senderPattern = :sender LIMIT 1")
    suspend fun isSalarySource(ifsc: String, sender: String): Boolean?
}
