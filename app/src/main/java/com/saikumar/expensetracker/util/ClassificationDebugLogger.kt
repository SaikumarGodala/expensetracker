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
    private val activeLog = mutableMapOf<String, LogBuilder>()
    
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    
    /**
     * Builder for constructing a debug log incrementally
     */
    private class LogBuilder {
        var transactionId: String = UUID.randomUUID().toString()
        var timestamp: Long = System.currentTimeMillis()
        var rawInput: RawInputCapture? = null
        var parsedFields: ParsedFields? = null
        val ruleTrace = mutableListOf<RuleExecution>()
        var conflictResolution: ConflictResolution? = null
        var finalDecision: FinalDecision? = null
        var userOverride: UserOverride? = null
        var debugMode: Boolean = true
        
        fun build(): ClassificationDebugLog? {
            val raw = rawInput ?: return null
            val parsed = parsedFields ?: return null
            val decision = finalDecision ?: return null
            
            return ClassificationDebugLog(
                transactionId = transactionId,
                timestamp = timestamp,
                rawInput = raw,
                parsedFields = parsed,
                ruleTrace = ruleTrace.toList(),
                conflictResolution = conflictResolution,
                finalDecision = decision,
                userOverride = userOverride,
                debugMode = debugMode
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
        activeLog[logId]?.ruleTrace?.add(rule)
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
    
    /**
     * Log P2P Outgoing Transfer Financial Invariant application.
     * 
     * CRITICAL: This invariant runs AFTER the decision tree.
     * It corrects fallback EXPENSE classifications to TRANSFER for outgoing P2P payments.
     * Silent corrections are FORBIDDEN - every application MUST be logged.
     */
    fun logP2PTransferInvariant(logId: String, invariantLog: P2PTransferInvariantLog) {
        if (invariantLog.applied) {
            Log.d(TAG, """
                ╔═══════════════════════════════════════════════════════════
                ║ 🔄 FINANCIAL INVARIANT APPLIED: ${invariantLog.invariantName}
                ║─────────────────────────────────────────────────────────────
                ║ Original Type: ${invariantLog.originalTransactionType}
                ║ Corrected To: ${invariantLog.correctedTransactionType}
                ║ Transfer Direction: ${invariantLog.transferDirection}
                ║ Counterparty: ${invariantLog.counterparty}
                ║ Reason: ${invariantLog.reason}
                ║ Confidence: ${invariantLog.confidence}
                ╚═══════════════════════════════════════════════════════════
            """.trimIndent())
        } else {
            Log.d(TAG, "[$logId] P2P_OUTGOING_TRANSFER invariant: NOT APPLIED - ${invariantLog.reason}")
        }
        
        // Log as a rule execution for the trace
        logRuleExecution(logId, createRuleExecution(
            ruleId = "FINANCIAL_INVARIANT",
            ruleName = invariantLog.invariantName,
            ruleType = "POST_DECISION_TREE_INVARIANT",
            input = invariantLog.counterparty ?: "N/A",
            result = if (invariantLog.applied) "APPLIED" else "NOT_APPLIED",
            confidence = if (invariantLog.applied) 0.7 else 0.0,  // MEDIUM confidence
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
     * End the current batch session and write all logs to a single file.
     */
    suspend fun endBatchSession(context: Context) {
        if (!isBatchSessionActive || batchLogs.isEmpty()) {
            isBatchSessionActive = false
            return
        }

        withContext(Dispatchers.IO) {
            try {
                // Save batch to single file
                val timestamp = System.currentTimeMillis()
                val fileName = "log_batch_${timestamp}_${batchSessionId?.take(8)}.json"
                val jsonContent = gson.toJson(batchLogs)

                writeContentToFile(context, fileName, jsonContent)
                
                Log.d(TAG, "Batch session ended. Saved ${batchLogs.size} logs to $fileName")
                
                // Show summary toast
                withContext(Dispatchers.Main) {
                    try {
                        android.widget.Toast.makeText(context, "Batch Log Saved: $fileName\n(${batchLogs.size} transactions)", android.widget.Toast.LENGTH_LONG).show()
                    } catch (e: Exception) { Log.e(TAG, "Toast failed", e) }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error saving batch logs", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Batch Save Failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                isBatchSessionActive = false
                batchLogs.clear()
                batchSessionId = null
            }
        }
    }

    /**
     * Persist a completed log to database (async) AND write to local file
     */
    suspend fun persistLog(context: Context, logId: String) = withContext(Dispatchers.IO) {
        val builder = activeLog.remove(logId) ?: return@withContext
        val log = builder.build() ?: return@withContext
        
        try {
            val app = context.applicationContext as ExpenseTrackerApplication
            
            // Check if debug mode is enabled
            val debugEnabled = app.preferencesManager.debugMode.first()
            if (!debugEnabled) {
                // Log.d(TAG, "Debug mode disabled, skipping log persistence")
                return@withContext
            }
            
            // If batch session is active, add to list instead of writing file immediately
            if (isBatchSessionActive) {
                synchronized(batchLogs) {
                    batchLogs.add(log)
                }
            } else {
                // 1. Write to local file (User Request) - Individual mode
                writeLogToFile(context, log)
            }
            
            // 2. Database persistence (Coming soon with DAO)
            // val logJson = gson.toJson(log)
            // app.repository.insertDebugLog(logJson)
            
            Log.d(TAG, "Persisted debug log: ${log.transactionId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting debug log", e)
        }
    }
    
    /**
     * Write log to Downloads folder using MediaStore for easier access
     */
    private suspend fun writeLogToFile(context: Context, log: ClassificationDebugLog) {
        val fileName = "log_${log.timestamp}_${log.transactionId.take(8)}.json"
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
    
    /**
     * Create a simple rule execution log entry
     */
    fun createRuleExecution(
        ruleId: String,
        ruleName: String,
        ruleType: String,
        input: String,
        result: String,
        confidence: Double = 0.0,
        reason: String
    ): RuleExecution {
        return RuleExecution(
            ruleId = ruleId,
            ruleName = ruleName,
            ruleType = ruleType,
            inputEvaluated = input,
            result = result,
            confidence = confidence,
            reason = reason,
            executionTimestamp = System.currentTimeMillis()
        )
    }
}
