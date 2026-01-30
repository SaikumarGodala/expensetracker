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
        
        return cursor?.use { c ->
            val messages = mutableListOf<RawSms>()
            while (c.moveToNext()) {
                val sender = c.getString(0) ?: continue
                val body = c.getString(1) ?: continue
                val timestamp = c.getLong(2)
                messages.add(RawSms(sender, body, timestamp))
            }
            InboxData(c.count, messages.asSequence())
        } ?: InboxData(0, emptySequence())
    }
}
