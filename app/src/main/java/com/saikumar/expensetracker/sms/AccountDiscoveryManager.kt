package com.saikumar.expensetracker.sms

import android.util.Log
import com.saikumar.expensetracker.data.db.UserAccountDao
import com.saikumar.expensetracker.data.entity.AccountType
import com.saikumar.expensetracker.data.entity.UserAccount
import java.util.regex.Pattern

object AccountDiscoveryManager {

    private const val TAG = "AccountDiscovery"

    // Matches "A/c *1234", "A/c XX1234", "Account XX1234", "Acct XX1234"
    private val ACCOUNT_PATTERN = Pattern.compile(
        "(?:A/c|Acct|Account)\\s+[*X]*(\\d{4})", 
        Pattern.CASE_INSENSITIVE
    )

    // Matches "Card XX1234", "Credit Card ending 1234"
    private val CARD_PATTERN = Pattern.compile(
        "(?:Card|Credit Card)\\s+(?:ending\\s+)?(?:[*X]*\\s*)?(\\d{4})",
        Pattern.CASE_INSENSITIVE
    )

    // Matches "From VPA <vpa>" or "VPA: <vpa>" or "UPI: <vpa>" for discovering user's own VPA
    private val VPA_FROM_PATTERN = Pattern.compile(
        "(?:From VPA|VPA|UPI)\\s*:?\\s*([a-zA-Z0-9._-]+@[a-zA-Z]+)",
        Pattern.CASE_INSENSITIVE
    )

    // Matches debit transactions - indicates this is user's own VPA
    private val DEBIT_KEYWORDS = listOf("debited", "sent", "paid", "spent", "withdrawn")

    suspend fun scanAndDiscover(message: String, sender: String, dao: UserAccountDao) {
        val accountMatch = ACCOUNT_PATTERN.matcher(message)
        val cardMatch = CARD_PATTERN.matcher(message)
        val vpaMatch = VPA_FROM_PATTERN.matcher(message)
        val bankName = inferBankName(sender)

        // Discover bank account
        if (accountMatch.find()) {
            val last4 = accountMatch.group(1) ?: return
            saveAccount(dao, last4, bankName, AccountType.SAVINGS, null)
        }

        // Discover credit card
        if (cardMatch.find()) {
            val last4 = cardMatch.group(1) ?: return
            saveAccount(dao, last4, bankName, AccountType.CREDIT_CARD, null)
        }

        // Discover UPI VPA (only if it's a debit transaction - indicating user's own VPA)
        if (vpaMatch.find()) {
            val vpa = vpaMatch.group(1) ?: return
            val isDebit = DEBIT_KEYWORDS.any { message.contains(it, ignoreCase = true) }

            if (isDebit) {
                // This is user's own VPA - save it
                // We'll associate it with the account if we can extract account number
                val accountNum = accountMatch.group(1)
                if (accountNum != null) {
                    updateUserVpa(dao, accountNum, vpa)
                } else {
                    // Create a pseudo-account entry for this VPA
                    val vpaLast4 = vpa.substringBefore("@").takeLast(4).padStart(4, '0')
                    saveAccount(dao, vpaLast4, bankName, AccountType.UPI, vpa)
                }
            }
        }
    }

    private suspend fun saveAccount(
        dao: UserAccountDao,
        last4: String,
        bankName: String,
        type: AccountType,
        upiVpa: String? = null
    ) {
        // Double check: If we already have this account, do nothing
        if (dao.isMyAccount(last4)) {
            // If we have new VPA info, update it
            if (upiVpa != null) {
                updateUserVpa(dao, last4, upiVpa)
            }
            return
        }

        val newAccount = UserAccount(
            accountNumberLast4 = last4,
            bankName = bankName,
            accountType = type,
            isMyAccount = true,
            alias = "$bankName $type ending $last4",
            upiVpa = upiVpa
        )

        Log.d(TAG, "Discovered NEW Account: ${newAccount.alias}" + (if (upiVpa != null) " with VPA: $upiVpa" else ""))
        dao.insert(newAccount)
    }

    suspend fun updateHolderName(dao: UserAccountDao, last4: String, holderName: String) {
        if (holderName.isBlank()) return

        // Log discovery
        Log.d(TAG, "Discovered Account Holder Name for $last4: $holderName")

        // Update DB
        dao.updateAccountHolderName(last4, holderName)
    }

    suspend fun updateUserVpa(dao: UserAccountDao, last4: String, vpa: String) {
        if (vpa.isBlank()) return

        // Log discovery
        Log.d(TAG, "Discovered UPI VPA for account $last4: $vpa")

        // Update DB
        dao.updateUpiVpa(last4, vpa)
    }

    private fun inferBankName(sender: String): String {
        return when {
            sender.contains("HDFC", ignoreCase = true) -> "HDFC Bank"
            sender.contains("ICICI", ignoreCase = true) -> "ICICI Bank"
            sender.contains("SBI", ignoreCase = true) -> "SBI"
            sender.contains("AXIS", ignoreCase = true) -> "Axis Bank"
            sender.contains("IDFC", ignoreCase = true) -> "IDFC FIRST Bank"
            else -> "Unknown Bank"
        }
    }
}
