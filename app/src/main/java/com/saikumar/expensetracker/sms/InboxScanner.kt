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

    // Anything older than this is almost certainly a missing/zero "date" column value rather
    // than a real message timestamp - the SMS content provider's date column can be null or 0
    // on some OEM ROMs/restored backups. Silently keeping it would sort the transaction to
    // 1970 and drop it out of every "recent" query and analytics window that filters by date.
    private const val MIN_PLAUSIBLE_TIMESTAMP_MS = 946684800000L // 2000-01-01T00:00:00Z

    fun readInbox(context: Context): InboxData {
        val cursor = context.contentResolver.query(
            "content://sms/inbox".toUri(),
            arrayOf("address", "body", "date"),
            null, null, "date DESC"
        ) ?: return InboxData(0, emptySequence())

        val totalCount = cursor.count

        // Stream rows lazily instead of materializing the whole inbox into a List up front.
        // On a device with years of SMS history this can be tens of thousands of rows, most of
        // which get dropped immediately (OTP/spam/non-bank senders) - reading lazily lets
        // already-processed rows be garbage collected as the scan progresses instead of holding
        // the entire inbox in memory for the duration.
        //
        // IMPORTANT: this sequence owns the cursor and closes it once fully drained (or on
        // failure). It must always be consumed to completion (e.g. via forEach) - if a caller
        // stops iterating early, the cursor will not be closed.
        val messages = sequence {
            cursor.use { c ->
                while (c.moveToNext()) {
                    val sender = c.getString(0) ?: continue
                    val body = c.getString(1) ?: continue
                    if (body.isBlank()) continue

                    val rawTimestamp = c.getLong(2)
                    val now = System.currentTimeMillis()
                    val timestamp = if (rawTimestamp < MIN_PLAUSIBLE_TIMESTAMP_MS || rawTimestamp > now + 86_400_000L) {
                        now
                    } else {
                        rawTimestamp
                    }

                    yield(RawSms(sender, body, timestamp))
                }
            }
        }

        return InboxData(totalCount, messages)
    }
}
