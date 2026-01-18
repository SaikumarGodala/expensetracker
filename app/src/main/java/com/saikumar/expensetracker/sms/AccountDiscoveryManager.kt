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

    suspend fun scanAndDiscover(message: String, sender: String, dao: UserAccountDao) {
        val accountMatch = ACCOUNT_PATTERN.matcher(message)
        val cardMatch = CARD_PATTERN.matcher(message)
        val bankName = inferBankName(sender)

        if (accountMatch.find()) {
            val last4 = accountMatch.group(1) ?: return
            saveAccount(dao, last4, bankName, AccountType.SAVINGS)
        }

        if (cardMatch.find()) {
            val last4 = cardMatch.group(1) ?: return
            saveAccount(dao, last4, bankName, AccountType.CREDIT_CARD)
        }
    }

    private suspend fun saveAccount(
        dao: UserAccountDao, 
        last4: String, 
        bankName: String, 
        type: AccountType
    ) {
        // Double check: If we already have this account, do nothing
        if (dao.isMyAccount(last4)) return

        val newAccount = UserAccount(
            accountNumberLast4 = last4,
            bankName = bankName,
            accountType = type,
            isMyAccount = true,
            alias = "$bankName $type ending $last4"
        )
        
        Log.d(TAG, "Discovered NEW Account: ${newAccount.alias}")
        dao.insert(newAccount)
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
