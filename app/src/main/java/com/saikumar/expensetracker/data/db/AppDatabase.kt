package com.saikumar.expensetracker.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.saikumar.expensetracker.data.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Database(
    entities = [
        Category::class,
        Transaction::class,
        CycleOverride::class,
        Account::class,
        MerchantPattern::class,
        CategorizationRule::class,
        UndoLog::class
    ],
    version = 15,
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration from v12 to v13:
         * - Converts amount (Double) to amountPaisa (Long)
         * - Converts date (LocalDateTime as millis) to timestamp (Long, same format)
         * - Adds new columns: transactionType, accountId, smsHash, merchantName
         * - Removes smsBody column (privacy improvement)
         * - Creates accounts and merchant_patterns tables
         * - Ensures "Uncategorized" category exists for orphaned transactions
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d("AppDatabase", "Running migration 12 -> 13")
                
                // Step 1: Create new tables
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS accounts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        isLiability INTEGER NOT NULL DEFAULT 0,
                        isDefault INTEGER NOT NULL DEFAULT 0
                    )
                """)
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS merchant_patterns (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        keyword TEXT NOT NULL,
                        categoryName TEXT NOT NULL,
                        isUserDefined INTEGER NOT NULL DEFAULT 1
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_merchant_patterns_keyword ON merchant_patterns (keyword)")
                
                // Step 2: Ensure "Uncategorized" category exists (ID will be used as default)
                db.execSQL("""
                    INSERT OR IGNORE INTO categories (name, type, isEnabled, isDefault)
                    VALUES ('Uncategorized', 'VARIABLE_EXPENSE', 1, 1)
                """)
                
                // Step 3: Create new transactions table with correct schema
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS transactions_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        amountPaisa INTEGER NOT NULL,
                        categoryId INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        note TEXT,
                        source TEXT NOT NULL DEFAULT 'MANUAL',
                        accountType TEXT NOT NULL DEFAULT 'UNKNOWN',
                        accountId INTEGER,
                        transactionType TEXT NOT NULL DEFAULT 'EXPENSE',
                        isSelfTransfer INTEGER NOT NULL DEFAULT 0,
                        isSalaryCredit INTEGER NOT NULL DEFAULT 0,
                        isIncomeManuallyIncluded INTEGER NOT NULL DEFAULT 0,
                        smsHash TEXT,
                        merchantName TEXT,
                        smsSnippet TEXT,
                        manualClassification TEXT,
                        FOREIGN KEY (categoryId) REFERENCES categories(id) ON DELETE SET DEFAULT
                    )
                """)
                
                // Step 4: Copy data from old table, converting amounts to paisa
                // The old 'date' column was stored as epoch millis, same as new 'timestamp'
                db.execSQL("""
                    INSERT INTO transactions_new (
                        id, amountPaisa, categoryId, timestamp, note, source,
                        accountType, isSelfTransfer, isSalaryCredit, isIncomeManuallyIncluded,
                        manualClassification, transactionType
                    )
                    SELECT 
                        id,
                        CAST(amount * 100 AS INTEGER),
                        categoryId,
                        date,
                        note,
                        source,
                        accountType,
                        isSelfTransfer,
                        isSalaryCredit,
                        isIncomeManuallyIncluded,
                        manualClassification,
                        CASE 
                            WHEN isSelfTransfer = 1 THEN 'TRANSFER'
                            WHEN isSalaryCredit = 1 OR isIncomeManuallyIncluded = 1 THEN 'INCOME'
                            ELSE 'EXPENSE'
                        END
                    FROM transactions
                """)
                
                // Step 5: Drop old table and rename new one
                db.execSQL("DROP TABLE transactions")
                db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
                
                // Step 6: Recreate indices
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_categoryId ON transactions (categoryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_timestamp ON transactions (timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_smsHash ON transactions (smsHash)")
                
                Log.d("AppDatabase", "Migration 12 -> 13 completed")
            }
        }

        /**
         * Migration from v13 to v14:
         * - Adds categorization_rules table for user-defined categorization patterns
         * - Adds undo_log table for batch operation undo capability
         */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d("AppDatabase", "Running migration 13 -> 14")
                
                // Create categorization_rules table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS categorization_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        pattern TEXT NOT NULL,
                        patternType TEXT NOT NULL,
                        categoryId INTEGER NOT NULL,
                        displayName TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        matchCount INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (categoryId) REFERENCES categories(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categorization_rules_pattern ON categorization_rules (pattern)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_categorization_rules_categoryId ON categorization_rules (categoryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_categorization_rules_patternType ON categorization_rules (patternType)")
                
                // Create undo_log table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS undo_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        actionType TEXT NOT NULL,
                        undoData TEXT NOT NULL,
                        performedAt INTEGER NOT NULL,
                        isUndone INTEGER NOT NULL DEFAULT 0,
                        description TEXT NOT NULL,
                        affectedCount INTEGER NOT NULL DEFAULT 0
                    )
                """)
                
                Log.d("AppDatabase", "Migration 13 -> 14 completed")
            }
        }

        /**
         * Migration from v14 to v15:
         * - Adds referenceNo column for transaction reference numbers (UPI ref, NEFT ref, RRN)
         * - Adds isReversal column to flag refunded/reversed transactions
         * - Adds isSubscription column to flag recurring payments
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d("AppDatabase", "Running migration 14 -> 15")
                
                // Add new columns to transactions table
                db.execSQL("ALTER TABLE transactions ADD COLUMN referenceNo TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE transactions ADD COLUMN isReversal INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN isSubscription INTEGER NOT NULL DEFAULT 0")
                
                Log.d("AppDatabase", "Migration 14 -> 15 completed")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker_db"
                )
                .addMigrations(MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                // NO fallbackToDestructiveMigration() - we want safe migrations only
                .addCallback(AppDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            Log.d("AppDatabase", "Database onCreate: Seeding default categories")
            try {
                val categories = listOf(
                    // Income categories
                    "Salary" to "INCOME",
                    "Freelance / Other" to "INCOME",
                    "Refund" to "INCOME",
                    "Cashback" to "INCOME",
                    "Interest" to "INCOME",
                    "Other Income" to "INCOME",
                    "Unknown Income" to "INCOME",
                    // Fixed expenses
                    "Home Rent / EMI" to "FIXED_EXPENSE",
                    "Home Expenses" to "FIXED_EXPENSE",
                    "Insurance (Life + Health + Term)" to "FIXED_EXPENSE",
                    "Subscriptions" to "FIXED_EXPENSE",
                    "Mobile + WiFi" to "FIXED_EXPENSE",
                    "Car EMI" to "FIXED_EXPENSE",
                    "Credit Bill Payments" to "FIXED_EXPENSE",
                    // Variable expenses
                    "Groceries" to "VARIABLE_EXPENSE",
                    "Food Outside" to "VARIABLE_EXPENSE",
                    "Entertainment" to "VARIABLE_EXPENSE",
                    "Travel" to "VARIABLE_EXPENSE",
                    "Gifts" to "VARIABLE_EXPENSE",
                    "Medical" to "VARIABLE_EXPENSE",
                    "Apparel / Shopping" to "VARIABLE_EXPENSE",
                    "Electronics" to "VARIABLE_EXPENSE",
                    "Grooming" to "VARIABLE_EXPENSE",
                    "Miscellaneous" to "VARIABLE_EXPENSE",
                    "Unknown Expense" to "VARIABLE_EXPENSE",
                    "Uncategorized" to "VARIABLE_EXPENSE",  // Default fallback
                    // Investments
                    "Recurring Deposits" to "INVESTMENT",
                    "Mutual Funds" to "INVESTMENT",
                    "Gold / Silver" to "INVESTMENT",
                    "Additional Lump-sum" to "INVESTMENT",
                    "Investments / Dividends" to "INVESTMENT",
                    // Vehicle
                    "Fuel" to "VEHICLE",
                    "Service" to "VEHICLE",
                    "Repair" to "VEHICLE"
                )

                for ((name, type) in categories) {
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type, isEnabled, isDefault) VALUES ('$name', '$type', 1, 1)")
                }
                
                // Seed default accounts
                db.execSQL("INSERT OR IGNORE INTO accounts (name, type, isLiability, isDefault) VALUES ('Primary Bank', 'SAVINGS', 0, 1)")
                db.execSQL("INSERT OR IGNORE INTO accounts (name, type, isLiability, isDefault) VALUES ('Credit Card', 'CREDIT_CARD', 1, 0)")
                db.execSQL("INSERT OR IGNORE INTO accounts (name, type, isLiability, isDefault) VALUES ('Wallet', 'WALLET', 0, 0)")
                
                Log.d("AppDatabase", "Database seeding completed")
            } catch (e: Exception) {
                Log.e("AppDatabase", "Error seeding categories", e)
            }
        }
    }
}
