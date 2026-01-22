package com.saikumar.expensetracker.data.entity

/**
 * Complete debug log for a transaction classification decision.
 */
data class ClassificationDebugLog(
    val transactionId: String,
    val rawInput: RawInputCapture,
    val parsedFields: ParsedFields,
    val ruleTrace: List<RuleExecution>,
    val conflictResolution: ConflictResolution? = null,
    val finalDecision: FinalDecision,
    val userOverride: UserOverride? = null,
    val error: ErrorDetails? = null
)

data class ErrorDetails(
    val message: String,
    val exceptionType: String,
    val stackTrace: String
)

data class RawInputCapture(
    val fullMessageText: String,
    val source: String,
    val receivedTimestamp: Long,
    val sender: String?,
    val amount: Long
)

data class ParsedFields(
    val merchantName: String?,
    val upiId: String?,
    val neftReference: String?,
    val detectedKeywords: List<String>,
    val accountTypeDetected: String,
    val senderInferred: String?,
    val receiverInferred: String?,
    val counterpartyExtraction: CounterpartyExtraction? = null,
    val merchantSanitization: MerchantSanitization? = null,
    val merchantResolution: MerchantResolution? = null
)

data class CounterpartyExtraction(
    val rawTrace: String,
    val extractedName: String?,
    val method: String
)

data class MerchantSanitization(
    val original: String,
    val sanitized: String,
    val strategy: String,
    val steps: List<String> = emptyList()
)

data class MerchantResolution(
    val source: String,
    val finalName: String,
    val aliasUsed: Boolean
)

data class RuleExecution(
    val ruleId: String,
    val ruleName: String,
    val ruleType: String,
    val result: String,
    val confidence: Double,
    val reason: String? = null
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
    val finalConfidence: Double,
    val requiresUserConfirmation: Boolean,
    val reasoning: String? = null,
    val status: String = "COMPLETED",
    val entityType: String = "UNKNOWN",
    val isExpenseEligible: Boolean = true,
    // NEW FIELDS
    val matchedPatternId: String? = null,
    val whyNotOtherCategories: List<String> = emptyList(),
    val confidenceBreakdown: Map<String, Double> = emptyMap()
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

