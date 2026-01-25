package com.saikumar.expensetracker.sms

import android.content.Context
import androidx.core.net.toUri

data class RawSms(
    val sender: String,
    val body: String,
    val timestamp: Long
)

/**
 * Handles reading SMS from device inbox.
 */
data class InboxData(
    val totalCount: Int, 
    val messages: Sequence<RawSms>
)

object InboxScanner {
    
    fun readInbox(context: Context): InboxData {
        val cursor = context.contentResolver.query(
            "content://sms/inbox".toUri(),
            arrayOf("address", "body", "date"),
            null, null, "date DESC"
        )
        
        val totalCount = cursor?.count ?: 0
        
        val messages = sequence {
            cursor?.use {
                while (it.moveToNext()) {
                    val sender = it.getString(0) ?: continue
                    val body = it.getString(1) ?: continue
                    val timestamp = it.getLong(2)
                    yield(RawSms(sender, body, timestamp))
                }
            }
        }
        
        return InboxData(totalCount, messages)
    }
}
