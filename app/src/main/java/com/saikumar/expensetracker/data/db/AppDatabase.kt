package com.saikumar.expensetracker.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.saikumar.expensetracker.data.entity.*
import com.saikumar.expensetracker.data.dao.RetirementDao
import kotlinx.coroutines.CoroutineScope

@Database(
    entities = [
        Category::class,
        Transaction::class,
        CycleOverride::class,
        Account::class,
        MerchantPattern::class,
        CategorizationRule::class,
        UndoLog::class,
        MerchantMemory::class,
        TransactionLink::class,
        PendingTransaction::class,
        SmsRaw::class,
        RetirementBalance::class,
        UserAccount::class,
        MerchantAlias::class,
        NeftSource::class
    ],
    version = 31,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun cycleOverrideDao(): CycleOverrideDao
    abstract fun accountDao(): AccountDao
    abstract fun merchantPatternDao(): MerchantPatternDao
    abstract fun categorizationRuleDao(): CategorizationRuleDao
    abstract fun undoLogDao(): UndoLogDao
    abstract fun merchantMemoryDao(): MerchantMemoryDao
    abstract fun transactionLinkDao(): TransactionLinkDao
    abstract fun pendingTransactionDao(): PendingTransactionDao
    abstract fun smsRawDao(): SmsRawDao
    abstract fun retirementDao(): RetirementDao
    abstract fun userAccountDao(): UserAccountDao
    abstract fun merchantAliasDao(): com.saikumar.expensetracker.data.dao.MerchantAliasDao
    abstract fun neftSourceDao(): NeftSourceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun seedDefaultCategories(db: SupportSQLiteDatabase) {
            val categories = listOf(
                "Salary" to "INCOME",
                "Freelance / Other" to "INCOME",
                "Refund" to "INCOME",
                "Cashback" to "INCOME",
                "Interest" to "INCOME",
                "Other Income" to "INCOME",
                "Housing" to "FIXED_EXPENSE",
                "Utilities" to "FIXED_EXPENSE",
                "Insurance" to "FIXED_EXPENSE",
                "Subscriptions" to "FIXED_EXPENSE",
                "Mobile + WiFi" to "FIXED_EXPENSE",
                "Loan EMI" to "FIXED_EXPENSE",
                "Credit Bill Payments" to "FIXED_EXPENSE",
                "Groceries" to "VARIABLE_EXPENSE",
                "Dining Out" to "VARIABLE_EXPENSE",
                "Entertainment" to "VARIABLE_EXPENSE",
                "Travel" to "VARIABLE_EXPENSE",
                "Cab & Taxi" to "VARIABLE_EXPENSE",
                "Food Delivery" to "VARIABLE_EXPENSE",
                "Medical" to "VARIABLE_EXPENSE",
                "Shopping" to "VARIABLE_EXPENSE",
                "Miscellaneous" to "VARIABLE_EXPENSE",
                "Unknown Expense" to "VARIABLE_EXPENSE",
                "Uncategorized" to "VARIABLE_EXPENSE",
                "Mutual Funds" to "INVESTMENT",
                "Recurring Deposits" to "INVESTMENT",
                "Fuel" to "VEHICLE",
                "Service" to "VEHICLE",
                "P2P Transfers" to "VARIABLE_EXPENSE"
            )
            for ((name, type) in categories) {
                db.execSQL("INSERT OR IGNORE INTO categories (name, type, isEnabled, isDefault) VALUES ('$name', '$type', 1, 1)")
            }
            db.execSQL("INSERT OR IGNORE INTO accounts (name, type, isLiability, isDefault) VALUES ('Primary Bank', 'SAVINGS', 0, 1)")
            db.execSQL("INSERT OR IGNORE INTO accounts (name, type, isLiability, isDefault) VALUES ('Credit Card', 'CREDIT_CARD', 1, 0)")
            db.execSQL("INSERT OR IGNORE INTO accounts (name, type, isLiability, isDefault) VALUES ('Wallet', 'WALLET', 0, 0)")
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker_db"
                )
                .fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        seedDefaultCategories(db)
                    }
                })
                .build()
                .also { INSTANCE = it }
            }
        }

        fun clearInstance() {
            synchronized(this) { INSTANCE = null }
        }
    }
}
