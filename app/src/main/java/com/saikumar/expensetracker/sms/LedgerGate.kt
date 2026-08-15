package com.saikumar.expensetracker.sms

import com.saikumar.expensetracker.data.entity.AccountType
import com.saikumar.expensetracker.data.entity.TransactionSource
import com.saikumar.expensetracker.data.entity.Transaction
import com.saikumar.expensetracker.data.entity.TransactionType

sealed class LedgerDecision {
    data class Insert(
        val transactionType: TransactionType,
        val isExpenseEligible: Boolean,
        val accountType: AccountType,
        val reasoning: String
    ) : LedgerDecision()

    data class Drop(val reason: String, val ruleId: String) : LedgerDecision()
}

object LedgerGate {

    // 1. NON-TRANSACTIONAL EXCLUSIONS (Drop immediately)
    // NOTE: Credit card statements are NOT excluded - they are processed as STATEMENT type
    private val EXCLUDED_PHRASES = listOf(
        "otp is", "otp for", "login", "requested block",
        "minimum limit", "min bal", "balance below",
        // Standing Instruction confirmations (not actual transactions)
        "standing instruction", "recurring charges", "manage standing instructions",
        // EMI-conversion notice: the original card spend was already recorded; the
        // conversion is a restructuring, not new money movement
        "converted to emi",
        // Marketing / Spam
        "presenting", "credit line", "congratulations", "pre-approved"
    )

    // "Mandate" alone must NOT be excluded: an executed SIP autopay reads "UPI Mandate:
    // Sent Rs.12000.00 from HDFC Bank A/c To Indian Clearing Corpor..." — real money moved
    // (a mutual-fund investment). Only mandate LIFECYCLE notices (created/revoked/upcoming)
    // are non-transactional; the upcoming-debit ones are dropped by FUTURE_PHRASES anyway.
    private val MANDATE_SETTLED_PHRASES = listOf("sent rs", "debited", "deducted")

    // 2. FUTURE/REMINDER EXCLUSIONS (Drop immediately)
    private val FUTURE_PHRASES = listOf(
        "amount due", "total due", "min due", "minimum due",
        "due by", "will be debited", "to be debited", "is due",
        // E-Mandate pre-debit notice ("E-Mandate! Rs.12000 will be deducted on 02/07/26") -
        // the actual execution SMS arrives separately and is captured instead.
        "will be deducted", "to be deducted",
        // Pending reversal/refund promises - nothing has settled yet, so there's nothing
        // to record. The eventual settlement SMS (if the bank sends one) will be caught by
        // the SETTLED_REVERSAL_KEYWORDS check below instead.
        "will be reversed", "will be refunded", "will be credited back",
        "to be refunded", "to be reversed"
    )

    // 3. POSITIVE CONFIRMATION VERBS (One MUST be present)
    private val CONFIRMATION_VERBS = listOf(
        "debited", "credited", "received", "paid", "sent",
        "processed", "spent", "withdrawn", "deposited", "txn",
        // Settled reversals/refunds ("Transaction Reversed! ... Amt: Rs.X", "is refunded to
        // your Card") carry no other verb. FUTURE_PHRASES already dropped "will be reversed/
        // refunded" above, so only completed ones reach here.
        "reversed", "refunded",
        // "Amt Deducted! Rs.49900 from your HDFC Bank A/c ... for Money Transfer" - a settled
        // debit. Safe: FUTURE_PHRASES drops "will be/to be deducted" notices before this check.
        "deducted"
    )

    // 4. FAILURE / DECLINE KEYWORDS - a notice about a transaction that never actually
    // settled. Dropped unless the SMS also confirms the money has already been credited
    // back (a completed reversal), in which case it's let through to be recorded as a
    // REFUND instead of silently discarded.
    private val FAILURE_KEYWORDS = listOf(
        "declined", "txn failed", "transaction failed", "payment failed",
        "could not be processed", "was not successful", "unsuccessful transaction",
        "not been processed"
    )

    private val SETTLED_REVERSAL_KEYWORDS = listOf(
        "reversed", "reversal", "refunded", "refund of", "refund for", "credited back", "chargeback"
    )

    /**
     * STAGE 1 CHECKS: Fast, body-only filters.
     * Run this BEFORE extraction to save CPU on spam regex.
     */
    fun quickFilter(body: String): LedgerDecision.Drop? {
        val lowerBody = body.lowercase()

        // RULE 1: Exclusion Filters (OTP, Login, etc.)
        if (EXCLUDED_PHRASES.any { lowerBody.contains(it) }) {
            return LedgerDecision.Drop("Informational/OTP Message", "FILTER_INFO")
        }

        // RULE 1.5: Mandate lifecycle notices (created/revoked/registered) are informational.
        // An EXECUTED mandate ("UPI Mandate: Sent Rs.X ... To Indian Clearing Corpor") is a
        // real settled debit - an SIP investment - and must pass. Upcoming-debit mandate
        // notices are handled by FUTURE_PHRASES in evaluate().
        if (lowerBody.contains("mandate") &&
            MANDATE_SETTLED_PHRASES.none { lowerBody.contains(it) }) {
            return LedgerDecision.Drop("Mandate Lifecycle Notice", "FILTER_MANDATE_INFO")
        }

        // RULE 3: "Avl Bal" only check (Balance Enquiry)
        if ((lowerBody.contains("available bal") || lowerBody.contains("avl bal")) && 
            CONFIRMATION_VERBS.none { lowerBody.contains(it) } && 
            !lowerBody.contains("payment")) {
            return LedgerDecision.Drop("Balance Info Only", "FILTER_AVL_BAL")
        }

        // RULE 3.5: Loan Spam Filter
        if (lowerBody.contains("loan")) {
            val hasStrongLoanVerb = listOf("debited", "credited", "disbursed", "paid", "repayment", "emi").any { lowerBody.contains(it) }
            if (!hasStrongLoanVerb) {
                 return LedgerDecision.Drop("Loan Marketing/Info", "FILTER_LOAN_SPAM")
            }
        }
        
        return null
    }

    /**
     * STAGE 2 CHECKS: Deep validation requiring Transaction Type.
     */
    /**
     * STAGE 2 CHECKS: Deep validation requiring Transaction Type.
     */

    fun evaluate(body: String, parsedType: TransactionType?, amountPaisa: Long?, rawSender: String): LedgerDecision {
        val lowerBody = body.lowercase()
        
        // Sanity Check: If quick filters weren't run, run them now? 
        quickFilter(body)?.let { return it }

        // RULE 0: Hard Requirement - Money must be involved
        if (amountPaisa == null || amountPaisa <= 0) {
            return LedgerDecision.Drop("No Amount Detected", "FILTER_NO_AMOUNT")
        }

        // RULE 2: Future/Reminder Filters (Depends on Type)
        // EXCEPTIONS: STATEMENT passes; so does a SETTLED refund-to-card - "IRCTC refund of
        // Rs X credited to your Card ... Revised total due Rs Y" mentions "total due" only
        // as the statement math, the refund itself already happened.
        val isStatement = parsedType == TransactionType.STATEMENT
        val isSettledRefund = parsedType == TransactionType.REFUND &&
            SETTLED_REVERSAL_KEYWORDS.any { lowerBody.contains(it) }
        if (!isStatement && !isSettledRefund && FUTURE_PHRASES.any { lowerBody.contains(it) }) {
            return LedgerDecision.Drop("Future/Reminder Event", "FILTER_FUTURE")
        }

        // RULE 3: Failed/Declined Transaction Filter
        // A decline/failure notice where no settled reversal is confirmed means no money
        // actually moved - inserting it would create a phantom expense (or, worse, a
        // phantom expense that later has no matching reversal SMS to cancel it out).
        val hasFailureKeyword = FAILURE_KEYWORDS.any { lowerBody.contains(it) }
        val hasSettledReversal = lowerBody.contains("credited") &&
            SETTLED_REVERSAL_KEYWORDS.any { lowerBody.contains(it) }
        if (hasFailureKeyword && !hasSettledReversal) {
            return LedgerDecision.Drop("Failed/Declined Transaction - No Settlement", "FILTER_DECLINED")
        }

        // RULE 4: Positive Confirmation Invariant
        val hasConfirmation = CONFIRMATION_VERBS.any { lowerBody.contains(it) }
        if (!isStatement && !hasConfirmation) {
            return LedgerDecision.Drop("Missing Confirmation Verb", "FILTER_NO_CONFIRMATION")
        }

        // RULE 5: Economic Reality Check (Infer Type and Eligibility)

        // 5a. Card Spend Invariant
        // FINANCIAL ACCURACY: must NOT fire for statements. A credit card statement SMS like
        // "ICICI Bank Credit Card XX0006 Statement is sent to xx@email. Total of Rs 39,114 due"
        // contains "card" + "sent", which previously forced it to EXPENSE - recording the whole
        // statement balance as a fresh expense and double-counting every card swipe in it.
        // A settled reversal/refund ("Transaction Reversed! ... Card ... Amt Rs.X") matches
        // "transaction"+"card" here, but it is money coming BACK, not a card spend. Respect the
        // extractor's REFUND type instead of forcing EXPENSE (which later flips to phantom INCOME).
        if (!isStatement && parsedType != TransactionType.REFUND && lowerBody.contains("card") &&
           (lowerBody.contains("spent") || lowerBody.contains("txn") || lowerBody.contains("transaction") || lowerBody.contains("sent") || lowerBody.contains("used")) &&
           !lowerBody.contains("payment received") && !lowerBody.contains("credited")) {
            
            val isCredit = lowerBody.contains("credit card")
            val accType = if (isCredit) AccountType.CREDIT_CARD else AccountType.UNKNOWN
            
            return LedgerDecision.Insert(
                transactionType = TransactionType.EXPENSE,
                isExpenseEligible = true,
                accountType = accType,
                reasoning = "INVARIANT_CARD_SPEND"
            )
        }
        
        // 5c. Default based on Parsed Type
        if (parsedType != null) {
            val isEligible = parsedType == TransactionType.EXPENSE
            val accountType = if (parsedType == TransactionType.LIABILITY_PAYMENT) AccountType.CREDIT_CARD else AccountType.UNKNOWN

            return LedgerDecision.Insert(
                transactionType = parsedType,
                isExpenseEligible = isEligible,
                accountType = accountType,
                reasoning = "PARSED_DEFAULT"
            )
        }

        return LedgerDecision.Drop("Parser yielded null type", "FILTER_PARSER_NULL")
    }
}
