package com.saikumar.expensetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saikumar.expensetracker.data.entity.SmsRaw

@Dao
interface SmsRawDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(smsRaw: SmsRaw): Long

    @Query("SELECT * FROM sms_raw WHERE sender = :sender AND receivedAt = :timestamp AND rawText = :body LIMIT 1")
    suspend fun findExisting(sender: String, timestamp: Long, body: String): SmsRaw?
    
    @Query("SELECT * FROM sms_raw WHERE rawSmsId = :id")
    suspend fun getById(id: Long): SmsRaw?
}
