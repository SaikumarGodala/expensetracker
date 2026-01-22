package com.saikumar.expensetracker.util

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.saikumar.expensetracker.ExpenseTrackerApplication
import com.saikumar.expensetracker.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Debug logger for transaction classification.
 * Provides full traceability of why each classification decision was made.
 */
object ClassificationDebugLogger {
    private const val TAG = "ClassificationDebugLogger"
    
    // In-memory logs being built (keyed by logId)
    private val activeLog = java.util.concurrent.ConcurrentHashMap<String, LogBuilder>()
    
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    
    // Compact Gson for JSONL
    private val jsonlGson: Gson = GsonBuilder().create()
        
    // ... (LogBuilder class omitted for brevity in diff, assume it exists)

    // ... (Other methods omitted)

    /**
     * End the current batch session and write all logs to a single file.
     */
    suspend fun endBatchSession(context: Context) {
        if (!isBatchSessionActive) return 
        
        // Copy list to avoid concurrent modification during write
        val logsToWrite = synchronized(batchLogs) { batchLogs.toList() }

        if (logsToWrite.isEmpty()) {
            // Use runCatching to avoid coroutine cancellation crashes
            runCatching {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "No transactions to log (0 processed)", android.widget.Toast.LENGTH_LONG).show()
                }
            }.onFailure { Log.e(TAG, "Toast failed (scope cancelled?)", it) }
            isBatchSessionActive = false
            return
        }

        try {
            // Save batch to single file - Use JSONL (New Line Delimited JSON) for efficiency
            val timestamp = System.currentTimeMillis()
            val fileName = "log_batch_${timestamp}_${batchSessionId?.take(8)}.jsonl"
            
            // Serialize as JSONL
            val sb = StringBuilder()
            for (log in logsToWrite) {
                sb.append(jsonlGson.toJson(log)).append("\n")
            }
            val jsonContent = sb.toString()

            writeContentToFile(context, fileName, jsonContent)
            
            Log.d(TAG, "Batch session ended. Saved ${logsToWrite.size} logs to $fileName")
            
            // Show summary toast - use runCatching to avoid coroutine cancellation crash
            runCatching {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Batch Log Saved (.jsonl): $fileName\n(${logsToWrite.size} txns)", android.widget.Toast.LENGTH_LONG).show()
                }
            }.onFailure { Log.e(TAG, "Toast failed (scope cancelled?)", it) }

        } catch (e: Exception) {
            Log.e(TAG, "Error saving batch logs", e)
            runCatching {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Batch Save Failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        } finally {
            isBatchSessionActive = false
            synchronized(batchLogs) {
                batchLogs.clear()
            }
            batchSessionId = null
        }
    }
    private class LogBuilder {
        var transactionId: String = UUID.randomUUID().toString()
        var rawInput: RawInputCapture? = null
        var parsedFields: ParsedFields? = null
        val ruleTrace = mutableListOf<RuleExecution>()
        var conflictResolution: ConflictResolution? = null
        var finalDecision: FinalDecision? = null
        var userOverride: UserOverride? = null
        var errorDetails: ErrorDetails? = null
        
        fun build(): ClassificationDebugLog? {
            val raw = rawInput ?: return null
            // If error occurred early, parsedFields might be null. Provide dummy.
            val parsed = parsedFields ?: ParsedFields(null, null, null, emptyList(), "UNKNOWN", null, null)
            
            // If error occurred, create a dummy failed decision if one doesn't exist
            val decision = finalDecision ?: if (errorDetails != null) {
                FinalDecision(
                    transactionType = "ERROR",
                    categoryId = 0,
                    categoryName = "ERROR",
                    finalConfidence = 0.0,
                    requiresUserConfirmation = true,
                    reasoning = "Processing failed: ${errorDetails?.message}",
                    status = "FAILED"
                )
            } else {
                 return null
            }
            
            return ClassificationDebugLog(
                transactionId = transactionId,
                rawInput = raw,
                parsedFields = parsed,
                ruleTrace = ruleTrace.toList(),
                conflictResolution = conflictResolution,
                finalDecision = decision,
                userOverride = userOverride,
                error = errorDetails
            )
        }
    }
    
    /**
     * Start a new debug log. Returns a unique logId.
     */
    fun startLog(rawInput: RawInputCapture): String {
        val logId = UUID.randomUUID().toString()
        val builder = LogBuilder()
        builder.rawInput = rawInput
        activeLog[logId] = builder
        
        Log.d(TAG, "Started debug log: $logId")
        return logId
    }
    
    /**
     * Update RawInputCapture with parsed amount.
     * Called after parsing to populate values that weren't available at startLog time.
     */
    fun updateLogAmount(logId: String, amountPaisa: Long) {
        val builder = activeLog[logId] ?: return
        val current = builder.rawInput ?: return
        
        builder.rawInput = current.copy(
            amount = amountPaisa
        )
        Log.d(TAG, "[$logId] Updated raw input amount: $amountPaisa")
    }
    
    /**
     * Log the parsed fields extracted from the message
     */
    fun logParsedFields(logId: String, fields: ParsedFields) {
        activeLog[logId]?.parsedFields = fields
        Log.d(TAG, "[$logId] Parsed fields: merchant=${fields.merchantName}, upi=${fields.upiId}")
    }
    
    /**
     * Log execution of a classification rule
     */
    fun logRuleExecution(logId: String, rule: RuleExecution) {
        // Only store MATCHED and PASSED rules in the trace (reduces JSON size by ~80%)
        val shouldStore = rule.result == "MATCHED" || rule.result == "PASSED"
        if (shouldStore) {
            activeLog[logId]?.ruleTrace?.add(rule)
        }
        // Always log to Logcat for debugging (can filter in logcat viewer)
        Log.d(TAG, "[$logId] Rule ${rule.ruleName}: ${rule.result} (${rule.reason})")
    }
    
    /**
     * Log how conflicts between multiple matching rules were resolved
     */
    fun logConflictResolution(logId: String, conflict: ConflictResolution) {
        activeLog[logId]?.conflictResolution = conflict
        Log.d(TAG, "[$logId] Conflict resolved: winner=${conflict.winningRule}")
    }
    
    /**
     * Log post-fallback reconciliation: soft merchant override after fallback decision
     */
    fun logPostFallbackReconciliation(logId: String, reconciliationLog: Map<String, Any?>) {
        val applied = reconciliationLog["applied"] as? Boolean ?: false
        val reason = reconciliationLog["reason"] as? String ?: "Unknown"
        
        if (applied) {
            val previous = reconciliationLog["previousMerchant"] as? String ?: "null"
            val newMerchant = reconciliationLog["newMerchant"] as? String ?: "null"
            Log.d(TAG, "[$logId] Post-fallback reconciliation: APPLIED - '$previous' → '$newMerchant' ($reason)")
        } else {
            Log.d(TAG, "[$logId] Post-fallback reconciliation: NOT APPLIED - $reason")
        }
    }
    
    fun logP2PTransferInvariant(logId: String, invariantLog: P2PTransferInvariantLog) {
        if (invariantLog.applied) {
            Log.d(TAG, "[$logId] P2P Invariant APPLIED: ${invariantLog.invariantName} -> ${invariantLog.correctedTransactionType}")
        } else {
            Log.d(TAG, "[$logId] P2P Invariant SKIPPED: ${invariantLog.reason}")
        }
        
        logRuleExecution(logId, createRuleExecution(
            ruleId = "FINANCIAL_INVARIANT",
            ruleName = invariantLog.invariantName,
            ruleType = "POST_DECISION_TREE_INVARIANT",
            result = if (invariantLog.applied) "APPLIED" else "NOT_APPLIED",
            confidence = if (invariantLog.applied) 0.7 else 0.0,
            reason = invariantLog.reason
        ))
    }
    
    /**
     * Finalize the log with the final classification decision
     */
    fun finalizeLog(logId: String, decision: FinalDecision): String? {
        val builder = activeLog[logId] ?: return null
        builder.finalDecision = decision
        
        val log = builder.build()
        if (log != null) {
            Log.d(TAG, "[$logId] Finalized: ${decision.transactionType} -> ${decision.categoryName}")
            return logId
        }
        
        activeLog.remove(logId)
        return null
    }
    
    /**
     * Log a user override/correction
     */
    fun logUserOverride(logId: String, override: UserOverride) {
        activeLog[logId]?.userOverride = override
        Log.d(TAG, "[$logId] User override: ${override.originalType} -> ${override.userSelectedType}")
    }

    /**
     * Log a processing error
     */
    fun logError(logId: String, message: String, exception: Throwable?) {
        val builder = activeLog[logId] ?: return
        val stackTrace = exception?.stackTraceToString() ?: ""
        val error = ErrorDetails(message, exception?.javaClass?.simpleName ?: "Unknown", stackTrace)
        builder.errorDetails = error
        Log.e(TAG, "[$logId] ERROR logged: $message", exception)
    }
    
    /**
     * Persist a completed log to database (async) AND write to local file
     */
    // Batch logging state
    private var isBatchSessionActive = false
    private val batchLogs = mutableListOf<ClassificationDebugLog>()
    private var batchSessionId: String? = null

    /**
     * Start a batch logging session (e.g. for Scan Inbox).
     * Logs will be accumulated in memory and saved as a single file when endBatchSession is called.
     */
    fun startBatchSession() {
        isBatchSessionActive = true
        batchLogs.clear()
        batchSessionId = UUID.randomUUID().toString()
        Log.d(TAG, "Started batch logging session: $batchSessionId")
    }



    /**
     * Persist a completed log to database (async) AND write to local file
     */
    suspend fun persistLog(context: Context, logId: String) {
        val builder = activeLog.remove(logId) ?: return
        val log = builder.build() ?: return
        
        try {
            val app = context.applicationContext as ExpenseTrackerApplication
            
            // Check if debug mode is enabled - use runCatching for safety
            val debugEnabled = runCatching {
                app.preferencesManager.debugMode.first()
            }.getOrDefault(false)
            
            if (!debugEnabled) {
                return
            }
            
            // If batch session is active, add to list instead of writing file immediately
            if (isBatchSessionActive) {
                synchronized(batchLogs) {
                    batchLogs.add(log)
                }
            } else {
                // Write to local file (User Request) - Individual mode
                writeLogToFile(context, log)
            }
            
            Log.d(TAG, "Persisted debug log: ${log.transactionId}")
        } catch (e: Exception) {
            // Don't propagate errors - logging should never break main processing
            Log.e(TAG, "Error persisting debug log (non-fatal)", e)
        }
    }
    
    /**
     * Write log to Downloads folder using MediaStore for easier access
     */
    private suspend fun writeLogToFile(context: Context, log: ClassificationDebugLog) {
        val fileName = "log_${log.rawInput.receivedTimestamp}_${log.transactionId.take(8)}.json"
        val jsonContent = gson.toJson(log)
        writeContentToFile(context, fileName, jsonContent)
    }

    private suspend fun writeContentToFile(context: Context, fileName: String, content: String) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+ (Scoped Storage)
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/ExpenseTrackerLogs")
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(content.toByteArray())
                    }
                    Log.d(TAG, "Log saved to Downloads: $fileName")
                    
                    // Show success toast ONLY for individual files (batch handles its own toast)
                    if (!fileName.startsWith("log_batch")) {
                         withContext(Dispatchers.Main) {
                            try {
                                android.widget.Toast.makeText(context, "Log saved to Downloads:\n$fileName", android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) { Log.e(TAG, "Toast failed", e) }
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to create MediaStore entry")
                    if (!fileName.startsWith("log_batch")) {
                        withContext(Dispatchers.Main) {
                             android.widget.Toast.makeText(context, "Error: Could not create log file", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                // Legacy approach for Android 9 and below
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val logDir = java.io.File(downloadsDir, "ExpenseTrackerLogs")
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }
                val file = java.io.File(logDir, fileName)
                file.writeText(content)
                Log.d(TAG, "Log saved to Downloads (Legacy): ${file.absolutePath}")
                
                 // Show success toast
                if (!fileName.startsWith("log_batch")) {
                    withContext(Dispatchers.Main) {
                         try {
                            android.widget.Toast.makeText(context, "Log saved:\n${file.absolutePath}", android.widget.Toast.LENGTH_SHORT).show()
                         } catch (e: Exception) { Log.e(TAG, "Toast failed", e) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing log to file", e)
            val errorMsg = e.message ?: "Unknown error"
             withContext(Dispatchers.Main) {
                 android.widget.Toast.makeText(context, "Log Save Failed:\n$errorMsg", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Export logs as JSON string for analysis
     */
    fun exportLog(logId: String): String? {
        val builder = activeLog[logId] ?: return null
        val log = builder.build() ?: return null
        return gson.toJson(log)
    }
    
    fun createRuleExecution(
        ruleId: String,
        ruleName: String,
        ruleType: String,
        result: String,
        confidence: Double = 0.0,
        reason: String? = null
    ): RuleExecution {
        return RuleExecution(
            ruleId = ruleId,
            ruleName = ruleName,
            ruleType = ruleType,
            result = result,
            confidence = confidence,
            reason = reason
            // Removed executionTimestamp
        )
    }
}
