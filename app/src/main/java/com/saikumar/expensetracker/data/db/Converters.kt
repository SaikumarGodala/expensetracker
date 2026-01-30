package com.saikumar.expensetracker.data.db

import androidx.room.TypeConverter
import com.saikumar.expensetracker.data.entity.AccountType
import com.saikumar.expensetracker.data.entity.CategoryType
import com.saikumar.expensetracker.data.entity.PatternType
import com.saikumar.expensetracker.data.entity.TransactionType

/**
 * Room type converters for enum types.
 * 
 * NOTE: Dates are now stored as Long (UTC epoch millis) directly,
 * no conversion needed. This ensures timezone-independent storage.
 */
class Converters {
    @TypeConverter
    fun fromCategoryType(value: CategoryType): String {
        return value.name
    }

    @TypeConverter
    fun toCategoryType(value: String): CategoryType {
        return CategoryType.valueOf(value)
    }

    @TypeConverter
    fun fromAccountType(value: AccountType): String {
        return value.name
    }

    @TypeConverter
    fun toAccountType(value: String): AccountType {
        return try {
            AccountType.valueOf(value)
        } catch (e: Exception) {
            AccountType.UNKNOWN
        }
    }

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String {
        return value.name
    }

    @TypeConverter
    fun toTransactionType(value: String): TransactionType {
        return try {
            TransactionType.valueOf(value)
        } catch (e: Exception) {
            TransactionType.EXPENSE
        }
    }

    @TypeConverter
    fun fromPatternType(value: PatternType): String {
        return value.name
    }

    @TypeConverter
    fun toPatternType(value: String): PatternType {
        return try {
            PatternType.valueOf(value)
        } catch (e: Exception) {
            PatternType.MERCHANT_NAME
        }
    }

    @TypeConverter
    fun fromTransactionStatus(value: com.saikumar.expensetracker.data.entity.TransactionStatus): String {
        return value.name
    }

    @TypeConverter
    fun toTransactionStatus(value: String): com.saikumar.expensetracker.data.entity.TransactionStatus {
        return try {
            com.saikumar.expensetracker.data.entity.TransactionStatus.valueOf(value)
        } catch (e: Exception) {
            com.saikumar.expensetracker.data.entity.TransactionStatus.COMPLETED
        }
    }
}
