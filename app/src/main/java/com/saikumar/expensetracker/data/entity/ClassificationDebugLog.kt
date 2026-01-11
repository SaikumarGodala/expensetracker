package com.saikumar.expensetracker.data.entity

/**
 * Complete debug log for a transaction classification decision.
 */
data class ClassificationDebugLog(
    val transactionId: String,
    val timestamp: Long,
    val rawInput: RawInputCapture,
    val parsedFields: ParsedFields,
    val ruleTrace: List<RuleExecution>,
    val conflictResolution: ConflictResolution? = null,
    val finalDecision: FinalDecision,
    val userOverride: UserOverride? = null,
    val debugMode: Boolean = true
)

data class RawInputCapture(
    val fullMessageText: String,
    val source: String,
    val receivedTimestamp: Long,
    val sender: String?,
    val amount: Long,
    val direction: String,
    val accountType: String
)

data class ParsedFields(
    val merchantName: String?,
    val upiId: String?,
    val neftReference: String?,
    val detectedKeywords: List<String>,
    val accountTypeDetected: String,
    val senderInferred: String?,
    val receiverInferred: String?,
    val extractedSnippet: String,
    val counterpartyExtraction: CounterpartyExtraction? = null,
    val merchantSanitization: MerchantSanitization? = null,
    val merchantResolution: MerchantResolution? = null
)

data class CounterpartyExtraction(
    val extractedName: String? = null,
    val extractionRule: String? = null,
    val confidence: String = "MEDIUM",
    val found: Boolean = false,
    // Enhanced diagnostics: direction independence
    val directionAtExtraction: String? = null,  // "DEBIT", "CREDIT", or "UNKNOWN"
    val phrasesMatched: List<String> = emptyList(),  // e.g., ["credited", "from", "aishwarya rao"]
    val suppressedByDirection: Boolean = false  // true if extraction was blocked by direction bias (should never happen)
)

data class MerchantSanitization(
    val applied: Boolean = false,
    val deferred: Boolean = false,
    val originalMerchant: String? = null,
    val sanitizedTo: String? = null,
    val reason: String? = null
)

data class MerchantResolution(
    val originalMerchant: String? = null,
    val resolvedMerchant: String? = null,
    val resolutionRule: String? = null
)

data class RuleExecution(
    val ruleId: String,
    val ruleName: String,
    val ruleType: String,
    val inputEvaluated: String,
    val result: String,
    val confidence: Double,
    val reason: String,
    val executionTimestamp: Long
)

data class ConflictResolution(
    val matchedRules: List<String>,
    val priorityOrder: List<String>,
    val winningRule: String,
    val rejectedRules: Map<String, String>
)

data class FinalDecision(
    val transactionType: String,
    val categoryId: Long,
    val categoryName: String,
    val confidence: String,
    val finalConfidence: Double,
    val requiresUserConfirmation: Boolean,
    val reasoning: String
)

data class UserOverride(
    val originalType: String,
    val originalCategoryId: Long,
    val userSelectedType: String,
    val userSelectedCategoryId: Long,
    val overrideTimestamp: Long,
    val isTrainingSignal: Boolean = true
)

/**
 * Log entry for P2P Outgoing Transfer Financial Invariant.
 * 
 * FINANCIAL INVARIANT: Outgoing P2P payments are TRANSFER, not EXPENSE.
 * Expenses represent consumption. P2P payments represent movement of money.
 * Treating transfers as expenses breaks budgeting and spending analytics.
 * 
 * This invariant runs AFTER the decision tree as a post-hoc correction.
 */
data class P2PTransferInvariantLog(
    val applied: Boolean,
    val invariantName: String = "P2P_OUTGOING_TRANSFER",
    val originalTransactionType: String? = null,
    val correctedTransactionType: String? = null,
    val transferDirection: String? = null,  // "OUT" for outgoing P2P
    val counterparty: String? = null,
    val reason: String,
    val confidence: String? = null  // "MEDIUM" when applied
)

