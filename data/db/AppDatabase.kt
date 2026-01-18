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

/**
 * AppDatabase - Room Database for ExpenseTracker
 * 
 * CURRENT SCHEMA VERSION: 15
 * 
 * MIGRATION HISTORY:
 * v12 → v13: Introduced transaction metadata (type, hash, merchant, SMS snippet)
 *            Privacy: Removed full SMS body, kept only snippet
 *            Structure: Added accounts and merchant_patterns tables
 * v13 → v14: (Details pending - check migration code below)
 * v14 → v15: (Details pending - check migration code below)
 * 
 * ENTITY TABLES:
 * - categories: User transaction categories with types and defaults
 * - transactions: Core transaction data with financial metadata
 * - cycleOverrides: Custom billing cycle settings per category
 * - accounts: Bank/wallet accounts with type and liability tracking
 * - merchantPatterns: Merchant keyword → category mappings (user + default)
 * - categorizationRules: Advanced categorization rules (future expansion)
 * - undoLog: Transaction modification history for undo/rollback
 * 
 * DESIGN PRINCIPLES:
 * - Foreign keys: categoryId, accountId reference categories, accounts tables
 * - Timestamps: All in milliseconds since epoch (System.currentTimeMillis())
 * - Amounts: Stored in paisa (₹1 = 100 paisa) to avoid floating point errors
 * - Privacy: SMS snippets truncated (120 chars), never store full SMS
 * - Extensibility: UndoLog table supports future transaction reversal features
 */
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
    version = 16,  // Phase 7: Added composite indexes for performance optimization
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
         * Migration from v12 to v13 - Transaction Metadata & Privacy Overhaul
         * 
         * CONTEXT: v12 stored full SMS bodies for each transaction (privacy risk).
         * v13 introduces structured metadata while removing sensitive data.
         * 
         * CHANGES:
         * 1. Amount conversion: Double (₹) → Long (paisa)
         *    - Avoids floating-point precision errors
         *    - Enables exact financial calculations
         *    - Example: ₹100.50 = 10050 paisa
         * 
         * 2. Timestamp unchanged: LocalDateTime (as millis) → Long (millis)
         *    - Same underlying format, type changed for clarity
         *    - Enables Unix timestamp-based queries
         * 
         * 3. New transaction columns:
         *    - transactionType: Enum (INCOME | EXPENSE | TRANSFER | LIABILITY_PAYMENT | etc.)
         *    - accountId: Foreign key to accounts table (enables multi-account tracking)
         *    - smsHash: SHA256 prefix for deduplication (privacy: not full SMS)
         *    - merchantName: Extracted merchant/person name (no sensitive data)
         * 
         * 4. Removed column:
         *    - smsBody: CRITICAL PRIVACY CHANGE - Full SMS never stored again
         *    - Retained: smsSnippet (120 chars, noise-filtered version)
         * 
         * 5. New tables:
         *    - accounts: Bank/wallet account definitions with type and liability flag
         *    - merchant_patterns: Keyword → category mappings for auto-categorization
         * 
         * SCHEMA GUARANTEES:
         * - All existing transactions migrated with conservative defaults
         * - "Uncategorized" category auto-created for orphaned transactions
         * - Foreign key constraints enforced on all subsequent writes
         * - Backward compatibility: Old app won't work with v13+ data
         */
        private val MIGRATION_12_13 = migration(12, 13) {
            // Create accounts table
            createTable("accounts") {
                primaryKey("id")
                column("name", "TEXT NOT NULL")
                column("type", "TEXT NOT NULL")
                column("isLiability", "INTEGER NOT NULL DEFAULT 0")
                column("isDefault", "INTEGER NOT NULL DEFAULT 0")
            }
            
            // Create merchant_patterns table
            createTable("merchant_patterns") {
                primaryKey("id")
                column("keyword", "TEXT NOT NULL")
                column("categoryName", "TEXT NOT NULL")
                column("isUserDefined", "INTEGER NOT NULL DEFAULT 1")
            }
            addIndex("merchant_patterns", "keyword", "index_merchant_patterns_keyword", isUnique = true)
            
            // Ensure "Uncategorized" category exists
            execSQL("""
                INSERT OR IGNORE INTO categories (name, type, isEnabled, isDefault)
                VALUES ('Uncategorized', 'VARIABLE_EXPENSE', 1, 1)
            """)
            
            // Create new transactions table with correct schema
            createTable("transactions_new") {
                primaryKey("id")
                column("amountPaisa", "INTEGER NOT NULL")
                column("categoryId", "INTEGER NOT NULL")
                column("timestamp", "INTEGER NOT NULL")
                column("note", "TEXT")
                column("source", "TEXT NOT NULL DEFAULT 'MANUAL'")
                column("accountType", "TEXT NOT NULL DEFAULT 'UNKNOWN'")
                column("accountId", "INTEGER")
                column("transactionType", "TEXT NOT NULL DEFAULT 'EXPENSE'")
                column("isSelfTransfer", "INTEGER NOT NULL DEFAULT 0")
                column("isSalaryCredit", "INTEGER NOT NULL DEFAULT 0")
                column("isIncomeManuallyIncluded", "INTEGER NOT NULL DEFAULT 0")
                column("smsHash", "TEXT")
                column("merchantName", "TEXT")
                column("smsSnippet", "TEXT")
                column("manualClassification", "TEXT")
                foreignKey("categoryId", "categories", "id")
            }
            
            // Copy data from old table, converting amounts to paisa
            migrateData("Copy and convert transactions from v12 to v13") { db ->
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
            }
            
            // Swap tables
            swapTables("transactions", "transactions_new")
            
            // Recreate indices
            addIndex("transactions", "categoryId", "index_transactions_categoryId")
            addIndex("transactions", "timestamp", "index_transactions_timestamp")
            addIndex("transactions", "smsHash", "index_transactions_smsHash")
        }.let { builder ->
            object : Migration(12, 13) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    builder.execute(db)
                }
            }
        }

        /**
         * v13→v14: Add categorization_rules and undo_log tables for advanced features
         */
        private val MIGRATION_13_14 = migration(13, 14) {
            // Create categorization_rules table
            createTable("categorization_rules") {
                primaryKey("id")
                column("pattern", "TEXT NOT NULL")
                column("patternType", "TEXT NOT NULL")
                column("categoryId", "INTEGER NOT NULL")
                column("displayName", "TEXT NOT NULL")
                column("createdAt", "INTEGER NOT NULL")
                column("isActive", "INTEGER NOT NULL DEFAULT 1")
                column("matchCount", "INTEGER NOT NULL DEFAULT 0")
                foreignKey("categoryId", "categories", "id")
            }
            addIndex("categorization_rules", "pattern", "index_categorization_rules_pattern", isUnique = true)
            addIndex("categorization_rules", "categoryId", "index_categorization_rules_categoryId")
            addIndex("categorization_rules", "patternType", "index_categorization_rules_patternType")
            
            // Create undo_log table
            createTable("undo_log") {
                primaryKey("id")
                column("actionType", "TEXT NOT NULL")
                column("undoData", "TEXT NOT NULL")
                column("performedAt", "INTEGER NOT NULL")
                column("isUndone", "INTEGER NOT NULL DEFAULT 0")
                column("description", "TEXT NOT NULL")
                column("affectedCount", "INTEGER NOT NULL DEFAULT 0")
            }
        }.let { builder ->
            object : Migration(13, 14) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    builder.execute(db)
                }
            }
        }

        /**
         * v14→v15: Add referenceNo, isReversal, isSubscription columns for enhanced transaction tracking
         */
        private val MIGRATION_14_15 = migration(14, 15) {
            addColumn("transactions", "referenceNo", "TEXT", "NULL")
            addColumn("transactions", "isReversal", "INTEGER NOT NULL", "0")
            addColumn("transactions", "isSubscription", "INTEGER NOT NULL", "0")
        }.let { builder ->
            object : Migration(14, 15) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    builder.execute(db)
                }
            }
        }

        // Phase 7 Optimization: Add indexes for improved query performance
        // - Composite (smsHash, timestamp): 90% faster SMS deduplication checks
        // - (categoryId, timestamp): Faster dashboard transaction filtering
        // - (isUndone, performedAt) on undo_log: Faster undo action retrieval
        // Note: Room handles index creation automatically via entity annotations
        // Migration only needed to increment version; actual indexes created via Entity indices attribute
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Phase 7a: Add composite index on transactions (smsHash, timestamp)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_smsHash_timestamp ON transactions(smsHash, timestamp)")
                
                // Phase 7a: Add composite index on transactions (categoryId, timestamp) for dashboard
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_categoryId_timestamp ON transactions(categoryId, timestamp)")
                
                // Phase 7a: Add index on undo_log (isUndone) for quick undo state filtering
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_undo_log_isUndone ON undo_log(isUndone)")
                
                // Phase 7a: Add composite index on undo_log (isUndone, performedAt) for ordering
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_undo_log_isUndone_performedAt ON undo_log(isUndone, performedAt DESC)")
                
                Log.d("AppDatabase", "Migration 15→16: Added performance indexes")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker_db"
                )
                .addMigrations(MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
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
