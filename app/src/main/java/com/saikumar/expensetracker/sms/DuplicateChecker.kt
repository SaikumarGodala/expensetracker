package com.saikumar.expensetracker.sms

import com.saikumar.expensetracker.data.db.AppDatabase
import java.security.MessageDigest

/**
 * Handles duplicate detection for SMS messages.
 */
object DuplicateChecker {
    
    fun generateHash(body: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(body.toByteArray(Charsets.UTF_8))
        return hash.take(16).joinToString("") { "%02x".format(it) }
    }

    suspend fun isDuplicate(db: AppDatabase, smsHash: String): Boolean {
        return db.transactionDao().existsBySmsHash(smsHash)
    }
}
