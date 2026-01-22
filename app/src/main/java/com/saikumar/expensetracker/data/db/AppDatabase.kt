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
import com.saikumar.expensetracker.data.dao.BudgetBreachDao
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
        NeftSource::class,
        TransferCircleMember::class,
        TransferCircleAlias::class,
        BudgetBreach::class
    ],
    version = 40, // Added BudgetBreach
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
    abstract fun transferCircleDao(): com.saikumar.expensetracker.data.dao.TransferCircleDao
    abstract fun budgetBreachDao(): BudgetBreachDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun seedDefaultCategories(db: SupportSQLiteDatabase) {
            val categories = listOf(
                // Income Categories
                "Salary" to "INCOME",
                "Freelance / Other" to "INCOME",
                "Refund" to "INCOME",
                "Cashback" to "INCOME",
                "Interest" to "INCOME",
                "Dividend" to "INCOME",
                "Rental Income" to "INCOME",
                "Bonus" to "INCOME",
                "Other Income" to "INCOME",
                "Investment Redemption" to "INCOME",
                "Unverified Income" to "INCOME",
                
                // Fixed Expenses
                "Rent" to "FIXED_EXPENSE",
                "Housing" to "FIXED_EXPENSE",
                "Utilities" to "FIXED_EXPENSE",
                "Insurance" to "FIXED_EXPENSE",
                "Subscriptions" to "FIXED_EXPENSE",
                "Mobile + WiFi" to "FIXED_EXPENSE",
                "Loan EMI" to "FIXED_EXPENSE",
                "Education / Fees" to "FIXED_EXPENSE",
                
                // Liability
                "Credit Bill Payments" to "LIABILITY",
                
                // Variable Expenses
                "Groceries" to "VARIABLE_EXPENSE",
                "Dining Out" to "VARIABLE_EXPENSE",
                "Food Delivery" to "VARIABLE_EXPENSE",
                "Entertainment" to "VARIABLE_EXPENSE",
                "Transportation" to "VARIABLE_EXPENSE",
                "Medical" to "VARIABLE_EXPENSE",
                "Shopping" to "VARIABLE_EXPENSE",
                "Clothing" to "VARIABLE_EXPENSE",
                "Furniture" to "VARIABLE_EXPENSE",
                "Electronics" to "VARIABLE_EXPENSE",
                "Personal Care" to "VARIABLE_EXPENSE",
                "Gym & Fitness" to "VARIABLE_EXPENSE",
                "Gifts & Donations" to "VARIABLE_EXPENSE",
                "Books & Learning" to "VARIABLE_EXPENSE",
                "Pet Care" to "VARIABLE_EXPENSE",
                "ATM Withdrawal" to "VARIABLE_EXPENSE",
                "Offline Merchant" to "VARIABLE_EXPENSE",
                "Miscellaneous" to "VARIABLE_EXPENSE",
                "Unknown Expense" to "VARIABLE_EXPENSE",
                "Uncategorized" to "VARIABLE_EXPENSE",
                "P2P Transfers" to "VARIABLE_EXPENSE",
                "Self Transfer" to "VARIABLE_EXPENSE",
                
                // Investment
                "Mutual Funds" to "INVESTMENT",
                "Stocks" to "INVESTMENT",
                "Fixed Deposits" to "INVESTMENT",
                "Recurring Deposits" to "INVESTMENT",
                "PPF / EPF" to "INVESTMENT",
                "Gold" to "INVESTMENT",
                "Chits" to "INVESTMENT",
                
                // Vehicle
                "Fuel" to "VEHICLE",
                "Service" to "VEHICLE",
                "Parking & Tolls" to "VEHICLE",
                
                // Ignore
                "Invalid" to "IGNORE"
            )
            for ((name, type) in categories) {
                db.execSQL("INSERT OR IGNORE INTO categories (name, type, isEnabled, isDefault, icon) VALUES ('$name', '$type', 1, 1, '$name')")
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
                // Migrations removed as requested. Using destructive migration fallback.
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


