package com.saikumar.expensetracker.util

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TrainingDataLogger {
    private const val TAG = "TrainingDataLogger"
    private const val FILE_NAME = "ml_training_data.jsonl"

    // Log format version to handle schema evolution
    private const val VERSION = 1

    // While a batch is active, logSample() appends to this in-memory buffer instead of
    // opening/closing the training-data file on every call. Bulk inbox scans can process
    // thousands of messages in one pass; without batching each one paid the cost of a
    // separate file open+append+close.
    private val batchLock = Any()
    private var batchBuffer: StringBuilder? = null

    fun startBatch() {
        synchronized(batchLock) { batchBuffer = StringBuilder() }
    }

    /** Flush any buffered samples to disk in a single write and stop batching. */
    fun endBatch(context: Context) {
        val buffered = synchronized(batchLock) {
            val current = batchBuffer
            batchBuffer = null
            current
        }
        if (!buffered.isNullOrEmpty()) {
            try {
                appendToFile(context, buffered.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to flush batched training samples", e)
            }
        }
    }

    fun logSample(
        context: Context,
        smsBody: String,
        merchantName: String?,
        confidence: Float,
        isUserCorrection: Boolean,
        timestamp: Long = System.currentTimeMillis()
    ) {
        try {
            val entities = org.json.JSONArray()
            
            // Calculate Spans (Simple exact match for now)
            if (!merchantName.isNullOrBlank() && smsBody.contains(merchantName, ignoreCase = false)) {
                val start = smsBody.indexOf(merchantName, ignoreCase = false)
                val end = start + merchantName.length
                
                val entity = JSONObject().apply {
                    put("type", "MERCHANT")
                    put("start", start)
                    put("end", end)
                    put("text", merchantName)
                }
                entities.put(entity)
            }

            val json = JSONObject().apply {
                put("version", 2) // Version 2 for Span support
                put("timestamp", timestamp)
                put("date_readable", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp)))
                put("sms_body", smsBody)
                put("entities", entities)
                put("confidence", confidence)
                put("is_gold_label", isUserCorrection)
                // Keep legacy field for backward compat viewer
                put("label_merchant", merchantName) 
            }
            
            val line = json.toString() + "\n"
            val activeBuffer = synchronized(batchLock) { batchBuffer }
            if (activeBuffer != null) {
                synchronized(batchLock) { activeBuffer.append(line) }
            } else {
                appendToFile(context, line)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to log training sample", e)
        }
    }

    private fun appendToFile(context: Context, content: String) {
        // Use internal storage / files dir
        val file = File(context.filesDir, FILE_NAME)

        FileOutputStream(file, true).use { output ->
            output.write(content.toByteArray())
        }
    }
    
    fun getTrainingFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }
    
    fun clearLogs(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) file.delete()
    }
}
