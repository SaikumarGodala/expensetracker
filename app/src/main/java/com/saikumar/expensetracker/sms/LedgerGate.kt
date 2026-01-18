package com.saikumar.expensetracker.sms

import com.saikumar.expensetracker.data.entity.AccountType
import com.saikumar.expensetracker.data.entity.TransactionSource
import com.saikumar.expensetracker.data.entity.Transaction
import com.saikumar.expensetracker.sms.TransactionExtractor.TransactionType

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
    private val EXCLUDED_PHRASES = listOf(
        "otp is", "otp for", "mandate", "login", "requested block", 
        "minimum limit", "min bal", "balance below", "statement",
        // Standing Instruction confirmations (not actual transactions)
        "standing instruction", "successfully processed the payment", 
        "recurring charges", "manage standing instructions"
    )

    // 2. FUTURE/REMINDER EXCLUSIONS (Drop immediately)
    private val FUTURE_PHRASES = listOf(
        "amount due", "total due", "min due", "minimum due", 
        "due by", "will be debited", "to be debited", "is due"
    )

    // 3. POSITIVE CONFIRMATION VERBS (One MUST be present)
    private val CONFIRMATION_VERBS = listOf(
        "debited", "credited", "received", "paid", "sent", 
        "processed", "spent", "withdrawn", "deposited", "txn"
    )

    fun evaluate(body: String, parsedType: TransactionType?, rawSender: String): LedgerDecision {
        val lowerBody = body.lowercase()

        // RULE 1: Exclusion Filters
        if (EXCLUDED_PHRASES.any { lowerBody.contains(it) }) {
            return LedgerDecision.Drop("Informational/OTP Message", "FILTER_INFO")
        }

        // RULE 2: Future/Reminder Filters
        if (FUTURE_PHRASES.any { lowerBody.contains(it) }) {
            return LedgerDecision.Drop("Future/Reminder Event", "FILTER_FUTURE")
        }

        // RULE 3: "Avl Bal" only check
        if ((lowerBody.contains("available bal") || lowerBody.contains("avl bal")) && 
            CONFIRMATION_VERBS.none { lowerBody.contains(it) } && 
            !lowerBody.contains("payment")) {
            return LedgerDecision.Drop("Balance Info Only", "FILTER_AVL_BAL")
        }

        // RULE 4: Positive Confirmation Invariant
        val hasConfirmation = CONFIRMATION_VERBS.any { lowerBody.contains(it) }
        if (!hasConfirmation) {
            return LedgerDecision.Drop("Missing Confirmation Verb", "FILTER_NO_CONFIRMATION")
        }

        // RULE 5: Economic Reality Check (Infer Type and Eligibility)
        // If we got this far, it IS a valid ledger entry. Now determine its nature.
        
        // 5a. Card Spend Invariant (Expanded)
        // Matches: "Txn ... On ... Card", "Spent ... on Credit Card", "Sent ... using Card"
        // User Rule: On <Bank> Card + Txn/Spent/Sent -> FORCE EXPENSE
        if (lowerBody.contains("card") && 
           (lowerBody.contains("spent") || lowerBody.contains("txn") || lowerBody.contains("transaction") || lowerBody.contains("sent") || lowerBody.contains("used")) &&
           !lowerBody.contains("payment received") && !lowerBody.contains("credited")) {
            
            val isCredit = lowerBody.contains("credit card")
            val accType = if (isCredit) AccountType.CREDIT_CARD else AccountType.UNKNOWN // Could be Debit, but safe to default unknown if not explicit
            
            return LedgerDecision.Insert(
                transactionType = TransactionType.EXPENSE,
                isExpenseEligible = true,
                accountType = accType,
                reasoning = "INVARIANT_CARD_SPEND"
            )
        }

        // 5b. P2P Transfer Invariant
        // User Rule: "Credit from friend" / "Debit to friend" is TRANSFER (Neutral)
        if (lowerBody.contains("friend") || lowerBody.contains("family")) {
             return LedgerDecision.Insert(
                transactionType = TransactionType.TRANSFER,
                isExpenseEligible = false,
                accountType = AccountType.UNKNOWN,
                reasoning = "INVARIANT_P2P_TRANSFER"
            )
        }
        
        // 5c. Default based on Parsed Type (if available)
        if (parsedType != null) {
            // Determine Expense Eligibility based on Type
            // EXPENSE -> True
            // INCOME, TRANSFER, LIABILITY, PENSION -> False
            val isEligible = parsedType == TransactionType.EXPENSE
            
            // Map generic account type
            val accountType = if (parsedType == TransactionType.LIABILITY) AccountType.CREDIT_CARD else AccountType.UNKNOWN

            return LedgerDecision.Insert(
                transactionType = parsedType,
                isExpenseEligible = isEligible,
                accountType = accountType,
                reasoning = "PARSED_DEFAULT"
            )
        }

        // Fallback (shouldn't happen if parser works, but if parser fails but passed confirmation?)
        return LedgerDecision.Drop("Parser yielded null type", "FILTER_PARSER_NULL")
    }
}
