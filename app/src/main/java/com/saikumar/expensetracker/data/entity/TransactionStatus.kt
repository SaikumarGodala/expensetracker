package com.saikumar.expensetracker.data.entity

/**
 * Represents the lifecycle state of a transaction.
 * Critical for separating "intent/requests" from actual "money movement".
 */
enum class TransactionStatus {
    /**
     * An OTP, authorization request, or payment mandate request.
     * Financial Impact: NONE. (Money has not moved yet)
     */
    INTENT,

    /**
     * Transaction initiated or authorized but not yet fully settled.
     * Financial Impact: NONE/BLOCKED. (Money is on hold or transit, not technically gone)
     */
    PENDING,

    /**
     * Transaction successfully settled. Account balance explicitly debited/credited.
     * Financial Impact: REAL. (This is the only state that counts for totals)
     */
    COMPLETED,

    /**
     * Transaction failed, declined, or was explicitly cancelled.
     * Financial Impact: NONE.
     */
    CANCELLED
}
