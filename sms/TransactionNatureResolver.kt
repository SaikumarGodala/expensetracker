package com.saikumar.expensetracker.sms

import android.util.Log
import java.util.regex.Pattern

/**
 * TransactionNatureResolver - Deterministically resolves transaction nature BEFORE categorization.
 *
 * CRITICAL: This layer enforces hard accounting invariants that CANNOT be violated.
 * Every transaction MUST resolve to exactly one nature:
 * - PENDING: Future debits, requests, standing instructions
 * - CREDIT_CARD_PAYMENT: Bill payment to credit card
 * - CREDIT_CARD_SPEND: Purchase using credit card
 * - SELF_TRANSFER: Transfer between own accounts
 * - INCOME: Money coming in from external source
 * - EXPENSE: Money going out to external party
 *
 * Evaluation order is STRICT. Once a rule matches, evaluation STOPS.
 */
object TransactionNatureResolver {
    private const val TAG = "TransactionNatureResolver"

    data class NatureResolution(
        val nature: TransactionNature,
        val matchedRule: String,
        val confidence: Double,
        val reasoning: String,
        val skippedRules: List<String> = emptyList(),
        val ruleTrace: List<String> = emptyList(),
        val detectedDirection: String = "UNKNOWN",  // DEBIT, CREDIT, or UNKNOWN
        val isDebit: Boolean = false,
        val isCredit: Boolean = false,
        val matchedIncomeRule: String? = null,  // For INCOME: SALARY | INTEREST | REFUND | NEFT | BONUS | CASHBACK | null
        val incomeConfidence: Double = 0.80,  // HIGH for explicit, LOW for unidentified credit
        
        // REGRESSION PROTECTION FIELDS
        val matchedLevel: String = "",  // Which level matched: LEVEL_1, LEVEL_2, etc.
        val stopReason: String = "",  // Why evaluation stopped
        val invariantViolations: List<String> = emptyList(),  // Any invariant violations detected
        val evaluatedLevels: List<String> = emptyList()  // All levels evaluated in order
    )

    enum class TransactionNature {
        PENDING,                 // Future debit, request, standing instruction
        CREDIT_CARD_PAYMENT,     // Bill payment to credit card
        CREDIT_CARD_SPEND,       // Purchase using credit card
        SELF_TRANSFER,           // Transfer between own accounts
        INCOME,                  // Money coming in from external source
        EXPENSE                  // Money going out to external party
    }

    /**
     * FROZEN DECISION TREE v1.0 - DO NOT MODIFY
     * Evaluation order: PENDING → CC_PAYMENT → CC_SPEND → SELF_TRANSFER → INCOME → EXPENSE
     * Hard invariants: Debit ≠ INCOME, Stop on first match, Single classification per transaction
     */

    // ===== HARD INVARIANT ENFORCEMENT =====
    /**
     * Enforce hard accounting invariants that cannot be violated.
     * 
     * @param nature The classified transaction nature
     * @param isDebit True if money left the account
     * @param isCredit True if money entered the account
     * @return List of invariant violations (empty if all pass)
     * 
     * VIOLATIONS CHECKED:
     * 1. INVARIANT_1: Debit ≠ INCOME
     *    - Money leaving account cannot be income
     *    - Indicates SMS parsing error or misclassification
     * 
     * 2. INVARIANT_2: CC_PAYMENT ≠ INCOME and ≠ EXPENSE
     *    - Credit card payments are liability settlements
     *    - The expense was counted when item was purchased, not when bill paid
     * 
     * 3. INVARIANT_3: STOP on first match
     *    - Enforced by return statements (not checked here)
     *    - Prevents multiple classifications per transaction
     * 
     * DEBUG MODE: Violations logged with 🚨 emoji for visibility
     */
    private fun enforceInvariants(nature: TransactionNature, isDebit: Boolean, isCredit: Boolean): List<String> {
        val violations = mutableListOf<String>()
        
        // INVARIANT 1: Debit cannot be INCOME
        if (isDebit && nature == TransactionNature.INCOME) {
            violations.add("INVARIANT_1_VIOLATION: Debit transaction classified as INCOME")
            Log.e(TAG, "🚨 CRITICAL: INVARIANT_1 VIOLATED - Debit cannot be INCOME")
        }
        
        // INVARIANT 2: Credit-card payment cannot be INCOME or general EXPENSE
        if (nature == TransactionNature.CREDIT_CARD_PAYMENT && 
            (nature == TransactionNature.INCOME || nature == TransactionNature.EXPENSE)) {
            violations.add("INVARIANT_2_VIOLATION: CC payment misclassified")
            Log.e(TAG, "🚨 CRITICAL: INVARIANT_2 VIOLATED - CC payment cannot be INCOME or EXPENSE")
        }
        
        // INVARIANT 3: Stop on first match (no multiple matches per transaction)
        // This is enforced by return statements in resolveNature
        
        return violations
    }

    // ===== HELPER FUNCTION FOR FROZEN TREE PROTECTION =====
    private fun createResolution(
        nature: TransactionNature,
        matchedRule: String,
        confidence: Double,
        reasoning: String,
        matchedLevel: String,  // e.g., "LEVEL_1_PENDING"
        trace: List<String>,
        detectedDirection: String,
        isDebit: Boolean,
        isCredit: Boolean,
        skippedRules: List<String> = emptyList(),
        matchedIncomeRule: String? = null,
        incomeConfidence: Double = 0.80
    ): NatureResolution {
        val violations = enforceInvariants(nature, isDebit, isCredit)
        val evaluatedLevels = trace.filter { it.contains("LEVEL_") }.map { it.split(":")[0].trim() }
        
        return NatureResolution(
            nature = nature,
            matchedRule = matchedRule,
            confidence = confidence,
            reasoning = reasoning,
            skippedRules = skippedRules,
            ruleTrace = trace,
            detectedDirection = detectedDirection,
            isDebit = isDebit,
            isCredit = isCredit,
            matchedIncomeRule = matchedIncomeRule,
            incomeConfidence = incomeConfidence,
            matchedLevel = matchedLevel,
            stopReason = "MATCHED: $matchedRule",
            invariantViolations = violations,
            evaluatedLevels = evaluatedLevels
        )
    }

    /**
     * ENTRY POINT: Resolve transaction nature from SMS text.
     * 
     * This is the ONLY place to determine what a transaction is.
     * Categorization happens AFTER this, never before.
     *
     * @param smsBody The full SMS text
     * @param isDebit True if money is going OUT (debited)
     * @param isCredit True if money is coming IN (credited)
     * @param detectedAccountType The account type (CREDIT_CARD, BANK, etc.)
     * @return NatureResolution with matched rule and confidence
     */
    fun resolveNature(
        smsBody: String,
        isDebit: Boolean,
        isCredit: Boolean,
        detectedAccountType: String = "UNKNOWN"
    ): NatureResolution {
        val trace = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        // ===== EXPLICIT DIRECTION DETECTION =====
        // Determine and log the direction for every transaction
        val detectedDirection = when {
            isDebit && !isCredit -> "DEBIT"
            isCredit && !isDebit -> "CREDIT"
            else -> "UNKNOWN"
        }
        trace.add("DIRECTION_DETECTED: $detectedDirection (isDebit=$isDebit, isCredit=$isCredit)")

        // ===== HARD INVARIANT CHECK =====
        // These rules override all heuristics
        if (isDebit && smsBody.contains("debited", ignoreCase = true)) {
            // Rule 1: "debited" keyword → can NEVER be INCOME
            trace.add("INVARIANT_CHECK: Message contains 'debited' keyword - INCOME is forbidden")
        }

        // ===== STRICT EVALUATION ORDER =====
        // Level 1: PENDING DETECTION (highest priority)
        val pendingResult = detectPending(smsBody)
        if (pendingResult != null) {
            trace.add("LEVEL_1_PENDING: MATCHED - $pendingResult")
            return createResolution(
                nature = TransactionNature.PENDING,
                matchedRule = "PENDING",
                confidence = 1.0,
                reasoning = pendingResult,
                matchedLevel = "LEVEL_1_PENDING",
                trace = trace,
                detectedDirection = detectedDirection,
                isDebit = isDebit,
                isCredit = isCredit
            )
        }
        trace.add("LEVEL_1_PENDING: FAILED - No pending indicators found")

        // Level 1a: CASHBACK/REWARD DETECTION (must be BEFORE CC_PAYMENT to prevent false match)
        // "Cashback credited to your credit card" is NOT a payment, it's income
        val cashbackResult = detectCashback(smsBody)
        if (cashbackResult != null) {
            trace.add("LEVEL_1a_CASHBACK: MATCHED - $cashbackResult")
            return NatureResolution(
                nature = TransactionNature.INCOME,  // Cashback is a form of income
                matchedRule = "CASHBACK",
                confidence = 0.90,
                reasoning = cashbackResult,
                skippedRules = emptyList(),
                ruleTrace = trace,
                detectedDirection = detectedDirection,
                isDebit = isDebit,
                isCredit = isCredit
            )
        }
        trace.add("LEVEL_1a_CASHBACK: FAILED - Not cashback or reward")

        // Level 2: CREDIT CARD PAYMENT DETECTION (critical for invariant enforcement)
        val ccPaymentResult = detectCreditCardPayment(smsBody, isDebit, isCredit)
        if (ccPaymentResult != null) {
            trace.add("LEVEL_2_CC_PAYMENT: MATCHED - $ccPaymentResult")
            return NatureResolution(
                nature = TransactionNature.CREDIT_CARD_PAYMENT,
                matchedRule = "CREDIT_CARD_PAYMENT",
                confidence = 0.95,
                reasoning = ccPaymentResult,
                skippedRules = emptyList(),
                ruleTrace = trace,
                detectedDirection = detectedDirection,
                isDebit = isDebit,
                isCredit = isCredit
            )
        }
        trace.add("LEVEL_2_CC_PAYMENT: FAILED - Not a credit card payment")

        // Level 3: CREDIT CARD SPEND DETECTION
        val ccSpendResult = detectCreditCardSpend(smsBody, isDebit)
        if (ccSpendResult != null) {
            trace.add("LEVEL_3_CC_SPEND: MATCHED - $ccSpendResult")
            return NatureResolution(
                nature = TransactionNature.CREDIT_CARD_SPEND,
                matchedRule = "CREDIT_CARD_SPEND",
                confidence = 0.90,
                reasoning = ccSpendResult,
                skippedRules = emptyList(),
                ruleTrace = trace,
                detectedDirection = detectedDirection,
                isDebit = isDebit,
                isCredit = isCredit
            )
        }
        trace.add("LEVEL_3_CC_SPEND: FAILED - Not a credit card spend")

        // Level 4: SELF TRANSFER DETECTION
        val selfTransferResult = detectSelfTransfer(smsBody, isDebit, isCredit)
        if (selfTransferResult != null) {
            trace.add("LEVEL_4_SELF_TRANSFER: MATCHED - $selfTransferResult")
            return NatureResolution(
                nature = TransactionNature.SELF_TRANSFER,
                matchedRule = "SELF_TRANSFER",
                confidence = 0.85,
                reasoning = selfTransferResult,
                skippedRules = emptyList(),
                ruleTrace = trace,
                detectedDirection = detectedDirection,
                isDebit = isDebit,
                isCredit = isCredit
            )
        }
        trace.add("LEVEL_4_SELF_TRANSFER: FAILED - Not a self-transfer")

        // Level 5: INCOME DETECTION (before expense)
        if (isCredit && !isDebit) {
            val incomeResult = detectIncome(smsBody)
            if (incomeResult != null) {
                val (matchedRule, description) = incomeResult
                trace.add("LEVEL_5_INCOME: MATCHED - $description (rule: $matchedRule)")
                return NatureResolution(
                    nature = TransactionNature.INCOME,
                    matchedRule = "INCOME",
                    confidence = 0.95,  // HIGH confidence for explicit match
                    reasoning = description,
                    skippedRules = emptyList(),
                    ruleTrace = trace,
                    detectedDirection = detectedDirection,
                    isDebit = isDebit,
                    isCredit = isCredit,
                    matchedIncomeRule = matchedRule,
                    incomeConfidence = 0.95
                )
            }
            // No explicit income rule matched, but direction is CREDIT
            // Still assign as INCOME but with LOW confidence
            trace.add("LEVEL_5_INCOME: MATCHED (unidentified) - Credit with no explicit income rule")
            return NatureResolution(
                nature = TransactionNature.INCOME,
                matchedRule = "INCOME_UNIDENTIFIED",
                confidence = 0.40,  // LOW confidence for unidentified credit
                reasoning = "Unidentified credit transaction; requires user confirmation",
                skippedRules = emptyList(),
                ruleTrace = trace,
                detectedDirection = detectedDirection,
                isDebit = isDebit,
                isCredit = isCredit,
                matchedIncomeRule = null,  // No explicit rule matched
                incomeConfidence = 0.40
            )
        } else {
            trace.add("LEVEL_5_INCOME: SKIPPED - Not a credit transaction (direction=$detectedDirection)")
            skipped.add("INCOME")
        }

        // Level 6: EXPENSE DETECTION (fallback for debits)
        // BUG #4 FIX: Fallback confidence should be LOW (0.40), not HIGH
        // Fallback means "we don't know exactly what this is" - high confidence is a contradiction
        if (isDebit && !isCredit) {
            trace.add("LEVEL_6_EXPENSE_FALLBACK: MATCHED - Default for debit transactions")
            return NatureResolution(
                nature = TransactionNature.EXPENSE,
                matchedRule = "EXPENSE",
                confidence = 0.40,  // LOW confidence - this is a fallback, indicates uncertainty
                reasoning = "Default classification for debit transaction; requires user confirmation",
                skippedRules = emptyList(),
                ruleTrace = trace,
                detectedDirection = detectedDirection,
                isDebit = isDebit,
                isCredit = isCredit
            )
        }

        // Fallback (should not reach here in normal operation)
        // NOTE: This should ONLY reach here for CREDIT transactions with NO income rule matched
        // (LEVEL-5 should have already returned INCOME_UNIDENTIFIED for those)
        // If we get here with CREDIT, log error but do NOT assign INCOME silently
        trace.add("FALLBACK: No rule matched - unable to classify")
        return NatureResolution(
            nature = TransactionNature.EXPENSE,  // Default to EXPENSE, NOT INCOME
            matchedRule = "FALLBACK_UNCLASSIFIED",
            confidence = 0.30,  // Very low confidence - needs investigation
            reasoning = "Unable to classify transaction; defaulting to EXPENSE for safety",
            skippedRules = skipped,
            ruleTrace = trace,
            detectedDirection = detectedDirection,
            isDebit = isDebit,
            isCredit = isCredit
        )
    }

    // ===== INDIVIDUAL DETECTORS (return non-null if rule matches) =====

    /**
     * Level 1: PENDING - Future debits, requests, standing instructions.
     * Keywords: "will be debited", "has requested money", "due by", "standing instruction"
     */
    /**
     * Level 1: PENDING - Future debits, requests, standing instructions
     */
    private fun detectPending(smsBody: String): String? {
        val upper = smsBody.uppercase()

        val pendingKeywords = listOf(
            "WILL BE DEBITED",
            "HAS REQUESTED MONEY",
            "PAYMENT REQUEST",
            "DUE BY",
            "STANDING INSTRUCTION",
            "RECURRING CHARGE",
            "SUBSCRIPTION",
            "DEBIT ALERT FOR",
            "FUTURE PAYMENT"
        )

        val matched = pendingKeywords.find { upper.contains(it) }
        return if (matched != null) "Detected pending indicator: '$matched'" else null
    }

    /**
     * Level 1a: CASHBACK/REWARD - Positive adjustment to credit card.
     * 
     * CRITICAL: Must detect BEFORE CC_PAYMENT because both patterns match
     * "credited to your credit card" but they are fundamentally different:
     * - Cashback = Income (positive reward)
     * - CC Payment = Liability settlement (paying off bill)
     * 
     * Keywords: "cashback", "reward", "bonus", "credit score bonus"
     */
    private fun detectCashback(smsBody: String): String? {
        val upper = smsBody.uppercase()

        val cashbackKeywords = listOf(
            "CASHBACK",
            "REWARD",
            "BONUS",
            "CREDIT SCORE BONUS",
            "REWARD POINTS",
            "REDEEMED"
        )

        val matched = cashbackKeywords.find { upper.contains(it) }
        return if (matched != null) "Detected cashback/reward: '$matched'" else null
    }

    /**
     * Level 2: CREDIT CARD PAYMENT - Payment TO a credit card account.
     * 
     * HARD INVARIANT: If destination is credit card AND money is being paid,
     * this MUST be CREDIT_CARD_PAYMENT, NEVER INCOME.
     *
     * Keywords: "received", "credited", "towards your credit card", "BBPS", "payment received"
     * Context: Usually a credit transaction TO the credit card limit
     * 
     * NEGATIVE SIGNALS: Exclude if message is about cashback/rewards (detected earlier)
     */
    /**
     * Level 2: CREDIT CARD PAYMENT - Bill payment to credit card.
     * Must evaluate after cashback (both mention "credited to card")
     */
    private fun detectCreditCardPayment(smsBody: String, isDebit: Boolean, isCredit: Boolean): String? {
        val upper = smsBody.uppercase()

        // NEGATIVE SIGNALS: These are NOT CC payments, even if they mention "credit card"
        if (upper.contains("CASHBACK") || 
            upper.contains("REWARD") || 
            upper.contains("BONUS") ||
            upper.contains("INTEREST CREDITED") ||
            upper.contains("CREDIT SCORE")) {
            return null  // Block: These are adjustments/benefits, not bill payments
        }

        // Check for explicit credit card payment patterns
        // IMPORTANT: These patterns detect CC PAYMENT RECEIPTS (money paid TO user's CC)
        // Example: "HDFC Bank Cardmember, Online Payment of Rs.2970 was credited to your card ending 3838"
        val ccPaymentKeywords = listOf(
            "PAYMENT.*RECEIVED.*CREDIT CARD",
            "PAYMENT.*CREDITED.*CREDIT CARD",
            "RECEIVED.*TOWARDS YOUR CREDIT CARD",
            "CREDITED TO.*CREDIT CARD",
            "RECEIVED PAYMENT",
            "BBPS",
            "BILL PAYMENT SYSTEM",
            // HDFC-style patterns (Fix for Issue #2/#5)
            "CARDMEMBER.*PAYMENT.*CREDITED",
            "PAYMENT OF RS.*WAS CREDITED TO YOUR CARD",
            "ONLINE PAYMENT.*CREDITED TO YOUR CARD",
            "CREDITED TO YOUR CARD ENDING"
        )

        for (pattern in ccPaymentKeywords) {
            try {
                if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(upper).find()) {
                    return "Matched CC payment receipt pattern: '$pattern'"
                }
            } catch (e: Exception) {
                if (upper.contains(pattern)) {
                    return "Matched CC payment receipt keyword: '$pattern'"
                }
            }
        }

        // Check for flexible patterns: "payment ... received ... credit card"
        if (upper.contains("PAYMENT") && 
            (upper.contains("RECEIVED") || upper.contains("CREDITED")) && 
            upper.contains("CREDIT CARD")) {
            return "Matched flexible CC payment pattern (payment + received/credited + credit card)"
        }
        
        // ===== DEBIT-SIDE CC PAYMENT DETECTION =====
        // These patterns detect when YOUR BANK ACCOUNT is debited to pay a credit card
        // Example: "ICICI Bank Acct XX294 debited for Rs 22678.00; CRED Club credited"
        // This is LIABILITY_PAYMENT even though it's a DEBIT because you're paying off CC debt
        
        if (isDebit) {
            val ccPaymentDebitPatterns = listOf(
                "CRED CLUB CREDITED",      // CRED payment - bank debited, CRED credited
                "CRED CREDITED",           // Generic CRED pattern
                "CRED.*CREDIT.*CARD",      // CRED + credit card mention
                "CC BILL.*PAYMENT",        // CC bill payment
                "CREDIT CARD.*BILL.*PAID", // Credit card bill paid
                "PAYING.*CREDIT CARD"      // Paying credit card
            )
            
            for (pattern in ccPaymentDebitPatterns) {
                try {
                    if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(upper).find()) {
                        return "Matched CC payment DEBIT pattern (source transaction): '$pattern'"
                    }
                } catch (e: Exception) {
                    if (upper.contains(pattern)) {
                        return "Matched CC payment DEBIT keyword: '$pattern'"
                    }
                }
            }
        }

        return null
    }

    /**
     * Level 3: CREDIT CARD SPEND - Purchase using a credit card.
     * Debit only (money leaves account via card).
     */
    private fun detectCreditCardSpend(smsBody: String, isDebit: Boolean): String? {
        if (!isDebit) return null  // CC spend requires debit

        val upper = smsBody.uppercase()

        val ccSpendKeywords = listOf(
            "SPENT",
            "PURCHASE",
            "CARD TRANSACTION",
            "SWIPE",
            "DEBIT CARD TRANSACTION",
            "CREDIT CARD TRANSACTION"
        )

        // Check for credit card in message
        val isCreditCard = upper.contains("CREDIT CARD") || 
                          upper.contains("CARD NO") ||
                          upper.contains("CARD ENDING")

        if (isCreditCard) {
            val matched = ccSpendKeywords.find { upper.contains(it) }
            if (matched != null) {
                return "Matched CC spend: '$matched' on credit card"
            }
        }

        return null
    }

    /**
     * Level 4: SELF TRANSFER - Transfer between user's OWN accounts ONLY.
     * 
     * CRITICAL FINANCIAL INVARIANT (Fix for Issue #1):
     * - SELF_TRANSFER = money moving between user's OWN accounts (net zero effect)
     * - P2P PAYMENTS to OTHER people = EXPENSE (money leaves user's wallet)
     * 
     * Detection methods:
     * 1. ONLY explicit keywords: "self", "own account", "transfer between your accounts"
     * 
     * REMOVED (was causing Issue #1):
     * - Generic UPI patterns (could be self OR other person)
     * - Person name patterns ("debited...NAME credited" is P2P payment, NOT self-transfer!)
     * - Generic account-to-account patterns (too broad)
     * 
     * WHY: Payment to "Saikumar Reddy" or "A MANOHARACHARI" is an EXPENSE, not a TRANSFER.
     *      The old logic incorrectly classified any P2P payment as SELF_TRANSFER.
     */
    private fun detectSelfTransfer(smsBody: String, isDebit: Boolean, isCredit: Boolean): String? {
        val upper = smsBody.uppercase()

        // ONLY explicit self-transfer keywords qualify
        // These are unambiguous indicators that BOTH accounts belong to the user
        val selfTransferKeywords = listOf(
            "SELF",
            "OWN ACCOUNT",
            "YOUR OWN ACCOUNT",
            "TRANSFER BETWEEN YOUR ACCOUNTS",
            "TRANSFER TO SELF",
            "SELF TRANSFER",
            "TO YOUR OWN"
        )

        // Check explicit keywords ONLY
        val matched = selfTransferKeywords.find { upper.contains(it) }
        if (matched != null) {
            return "Matched self-transfer keyword: '$matched'"
        }

        // NOTE: We intentionally DO NOT detect:
        // - Generic UPI patterns (9618809138@ybl could be self OR another person)
        // - Person name patterns ("Saikumar Reddy" is likely ANOTHER person, not self)
        // - Generic "A/C + TRANSFER" patterns (too ambiguous)
        // 
        // These will correctly fall through to LEVEL_6_EXPENSE_FALLBACK,
        // which is financially correct for P2P payments.

        return null
    }

    /**
     * Level 5: INCOME - Money coming in from external source.
     * Allowed types: SALARY, NEFT, INTEREST, BONUS, REFUND, DIVIDEND, CASHBACK
     */
    private fun detectIncome(smsBody: String): Pair<String, String>? {
        // Returns Pair<matchedRule, description> or null if no income detected
        // ALLOWED income rules (MANDATORY spec):
        // SALARY, INTEREST, CASHBACK, REFUND, DIVIDEND, BONUS, NEFT_CREDIT
        val upper = smsBody.uppercase()

        if (upper.contains("SALARY")) return "SALARY" to "Salary income matched"
        if (upper.contains("NEFT")) return "NEFT" to "NEFT credit transfer detected"
        if (upper.contains("INTEREST")) return "INTEREST" to "Interest income matched"
        if (upper.contains("BONUS")) return "BONUS" to "Bonus matched"
        if (upper.contains("REFUND")) return "REFUND" to "Refund detected"
        if (upper.contains("DIVIDEND")) return "DIVIDEND" to "Dividend income matched"
        if (upper.contains("CASHBACK")) return "CASHBACK" to "Cashback income matched"
        
        return null  // No explicit income rule matched
    }

    // ===== LOGGING HELPER =====
    fun logResolution(transactionId: String, resolution: NatureResolution) {
        Log.d(TAG, """
            ╔═══════════════════════════════════════════════════════════
            ║ TRANSACTION NATURE RESOLUTION
            ║ ID: $transactionId
            ║─────────────────────────────────────────────────────────────
            ║ Nature: ${resolution.nature}
            ║ Matched Rule: ${resolution.matchedRule}
            ║ Confidence: ${(resolution.confidence * 100).toInt()}%
            ║ Reasoning: ${resolution.reasoning}
            ║─────────────────────────────────────────────────────────────
            ║ Rule Trace:
            ${resolution.ruleTrace.joinToString("\n") { "║   $it" }}
            ║═════════════════════════════════════════════════════════════
        """.trimIndent())
    }
}
