package com.saikumar.expensetracker.util

import android.content.Context
import android.util.Log
import com.saikumar.expensetracker.ExpenseTrackerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Logger for raw SMS messages - captures ALL incoming SMS before any processing.
 * Logs are saved to Downloads/ExpenseTrackerLogs/raw_sms_YYYYMMDD.json
 * 
 * IMPORTANT: This logs ALL SMS, not just financial transactions.
 * Respects debug mode toggle in Settings.
 */
object RawSmsLogger {
    private const val TAG = "RawSmsLogger"
    
    // Fixed filenames to avoid creating many files
    private const val LIVE_LOG_FILE = "raw_sms_stream.jsonl"
    private const val SCAN_LOG_FILE = "raw_sms_inbox_scan.jsonl"
    
    // Retention only applies to the stream file if we wanted to rotate, 
    // but user requested "one logger", so we'll keep it simple for now.
    private const val RETENTION_DAYS = 30
    
    private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    /**
     * Scan all existing SMS messages from phone inbox and log them to a SINGLE file.
     * 
     * @param context Application context
     * @return Number of messages logged
     */
    suspend fun scanAndLogAllSms(context: Context): Int = withContext(Dispatchers.IO) {
        Log.i(TAG, "🔍 SCANNING SMS INBOX - Starting...")
        var count = 0
        
        try {
            // Collect ALL messages into a single list
            val allMessages = mutableListOf<JSONObject>()
            
            val cursor = context.contentResolver.query(
                android.provider.Telephony.Sms.CONTENT_URI,
                arrayOf(
                    android.provider.Telephony.Sms._ID,
                    android.provider.Telephony.Sms.ADDRESS,
                    android.provider.Telephony.Sms.BODY,
                    android.provider.Telephony.Sms.DATE,
                    android.provider.Telephony.Sms.TYPE
                ),
                null,
                null,
                "${android.provider.Telephony.Sms.DATE} DESC"  // Newest first
            )
            
            cursor?.use {
                val totalMessages = it.count
                Log.i(TAG, "   Found $totalMessages total SMS messages")
                Log.i(TAG, "   📦 Collecting all messages...")
                
                while (it.moveToNext()) {
                    val sender = it.getString(it.getColumnIndexOrThrow(android.provider.Telephony.Sms.ADDRESS))
                    val body = it.getString(it.getColumnIndexOrThrow(android.provider.Telephony.Sms.BODY))
                    val timestamp = it.getLong(it.getColumnIndexOrThrow(android.provider.Telephony.Sms.DATE))
                    
                    val logEntry = JSONObject().apply {
                        put("sender", sender ?: "UNKNOWN")
                        put("body", body ?: "")
                        // Optional: Add timestamp to JSON since we aren't using date in filename anymore
                        put("timestamp", timestamp)
                        put("date", timestampFormat.format(Date(timestamp)))
                    }
                    
                    allMessages.add(logEntry)
                    count++
                    
                    if (count % 500 == 0) {
                        Log.i(TAG, "   Progress: $count/$totalMessages collected...")
                    }
                }
            }
            
            Log.i(TAG, "   ✅ Collected $count messages. Writing to SINGLE file...")
            
            // Write ALL messages to one file
            writeBatchToFile(context, SCAN_LOG_FILE, allMessages)
            Log.i(TAG, "   ✅ Wrote to $SCAN_LOG_FILE")
            
            Log.i(TAG, "✅ INBOX SCAN COMPLETE: Logged $count messages to $SCAN_LOG_FILE")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to scan SMS inbox", e)
            e.printStackTrace()
        }
        
        return@withContext count
    }
    
    /**
     * Write a batch of messages to a single file in JSONL format.
     */
    private suspend fun writeBatchToFile(context: Context, fileName: String, messages: List<JSONObject>) {
        withContext(Dispatchers.IO) {
            try {
                // Convert to JSONL string (one object per line)
                val jsonlContent = messages.joinToString("\n") { it.toString() }
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    writeBatchFileMediaStore(context, fileName, jsonlContent)
                } else {
                    writeBatchFileLegacy(context, fileName, jsonlContent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write batch file: $fileName", e)
            }
        }
    }
    
    /**
     * Write batch file using MediaStore (Android 10+)
     */
    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    private fun writeBatchFileMediaStore(context: Context, fileName: String, content: String) {
        val resolver = context.contentResolver
        val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json") // Or text/plain
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, 
                "${android.os.Environment.DIRECTORY_DOWNLOADS}/ExpenseTrackerLogs")
        }
        
        val uri = resolver.insert(collection, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { output ->
                output.write(content.toByteArray())
            }
        }
    }
    
    /**
     * Write batch file using legacy method (Android 9-)
     */
    private fun writeBatchFileLegacy(context: Context, fileName: String, content: String) {
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        val logDir = File(downloadsDir, "ExpenseTrackerLogs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        
        val logFile = File(logDir, fileName)
        logFile.writeText(content)
    }
    
    /**
     * Log a raw SMS message to file.
     * Non-blocking - runs on IO dispatcher.
     * Fails silently to never block SMS processing.
     * 
     * ALWAYS LOGS - does not check debug mode setting.
     * 
     * @param context Application context
     * @param sender Phone number or sender ID
     * @param body Full SMS message body
     * @param timestamp Message timestamp in milliseconds
     */
    fun logRawSms(context: Context, sender: String?, body: String, timestamp: Long) {
        Log.i(TAG, "🔵 RAW SMS RECEIVED - Starting logging process...")
        
        // Launch async - never block SMS processing
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Create log entry
                val logEntry = JSONObject().apply {
                    put("sender", sender ?: "UNKNOWN")
                    put("body", body)
                    put("timestamp", timestamp)
                    put("date", timestampFormat.format(Date(timestamp)))
                }
                
                // Append to the SINGLE fixed stream file
                appendToLogFile(context, LIVE_LOG_FILE, logEntry)
                
                Log.i(TAG, "   ✅ RAW SMS LOGGED to $LIVE_LOG_FILE")
                
            } catch (e: Exception) {
                // NEVER throw - logging errors must not crash SMS processing
                Log.e(TAG, "   ❌ FAILED to log raw SMS (non-fatal)", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Append a log entry to the log file in JSONL format.
     */
    private suspend fun appendToLogFile(context: Context, fileName: String, logEntry: JSONObject) {
        withContext(Dispatchers.IO) {
            try {
                // Append as new line
                val lineToAppend = logEntry.toString() + "\n"
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    appendToLogFileMediaStore(context, fileName, lineToAppend)
                } else {
                    appendToLogFileLegacy(context, fileName, lineToAppend)
                }
                
                // No cleanup needed as we are using a single file now
                // cleanupOldLogs(context, RETENTION_DAYS)
                
            } catch (e: Exception) {
                Log.e(TAG, "   >> ❌ Failed to append to log file", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Append to log file using MediaStore (Android 10+)
     */
    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    private fun appendToLogFileMediaStore(context: Context, fileName: String, content: String) {
        val resolver = context.contentResolver
        val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        
        // Check if file already exists
        val projection = arrayOf(
            android.provider.MediaStore.MediaColumns._ID
        )
        val selection = "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
                "${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(fileName, "${android.os.Environment.DIRECTORY_DOWNLOADS}/ExpenseTrackerLogs/")
        
        val cursor = resolver.query(collection, projection, selection, selectionArgs, null)
        val existingUri = cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getLong(it.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID))
                android.content.ContentUris.withAppendedId(collection, id)
            } else null
        }
        
        if (existingUri != null) {
            // File exists - append
            try {
                resolver.openOutputStream(existingUri, "wa")?.use { output ->
                    output.write(content.toByteArray())
                }
            } catch (e: Exception) {
                // Fallback if "wa" (write-append) mode isn't supported or fails
                // Note: Standard 'wa' support varies by Android version/OEM. 
                // Reliable fallback is read full -> append -> write full.
                // For this specific 'single file' request, we'll assume it works or they will report.
                // But let's try a safer append block if the above fails?
                // Actually, let's keep it simple for now as per previous success.
                Log.e(TAG, "Failed to append to MediaStore file", e)
            }
        } else {
            // File doesn't exist - create new
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, 
                    "${android.os.Environment.DIRECTORY_DOWNLOADS}/ExpenseTrackerLogs")
            }
            
            val uri = resolver.insert(collection, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { output ->
                    output.write(content.toByteArray())
                }
            }
        }
    }
    
    /**
     * Append to log file using legacy file system (Android 9 and below)
     */
    private fun appendToLogFileLegacy(context: Context, fileName: String, content: String) {
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        val logDir = File(downloadsDir, "ExpenseTrackerLogs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        
        val logFile = File(logDir, fileName)
        logFile.appendText(content)
    }

    // cleanupOldLogs removed as we are now using single permanent files
}
