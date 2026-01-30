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
        BudgetBreach::class,
        CategoryBudget::class
    ],
    version = 44, // Added performance indices on upiId, status, transactionType
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
    abstract fun budgetDao(): com.saikumar.expensetracker.data.dao.BudgetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun seedDefaultCategories(db: SupportSQLiteDatabase) {
            val categories = com.saikumar.expensetracker.data.common.DefaultCategories.ALL_CATEGORIES

            // Use parameterized statements to prevent SQL injection
            val categoryStmt = db.compileStatement(
                "INSERT OR IGNORE INTO categories (name, type, isEnabled, isDefault, icon) VALUES (?, ?, 1, ?, ?)"
            )
            for (category in categories) {
                categoryStmt.clearBindings()
                categoryStmt.bindString(1, category.name)
                categoryStmt.bindString(2, category.type.name)
                categoryStmt.bindLong(3, if (category.isDefault) 1L else 0L)
                categoryStmt.bindString(4, category.name)
                categoryStmt.executeInsert()
            }

            // Seed default accounts with parameterized statements
            val accountStmt = db.compileStatement(
                "INSERT OR IGNORE INTO accounts (name, type, isLiability, isDefault) VALUES (?, ?, ?, ?)"
            )
            listOf(
                arrayOf("Primary Bank", "SAVINGS", 0L, 1L),
                arrayOf("Credit Card", "CREDIT_CARD", 1L, 0L),
                arrayOf("Wallet", "WALLET", 0L, 0L)
            ).forEach { (name, type, isLiability, isDefault) ->
                accountStmt.clearBindings()
                accountStmt.bindString(1, name as String)
                accountStmt.bindString(2, type as String)
                accountStmt.bindLong(3, isLiability as Long)
                accountStmt.bindLong(4, isDefault as Long)
                accountStmt.executeInsert()
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker_db"
                )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        Log.i("AppDatabase", "Database created, seeding default categories...")
                        seedDefaultCategories(db)
                    }
                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        super.onDestructiveMigration(db)
                        // NOTE: Do NOT seed here - tables don't exist yet during this callback.
                        // Seeding is handled in onOpen after tables are recreated.
                        Log.e("AppDatabase", "DESTRUCTIVE MIGRATION TRIGGERED - User data was lost!")
                    }
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        Log.i("AppDatabase", "Database opened, version: ${db.version}")

                        // Seed default data if tables are empty (handles destructive migration case)
                        try {
                            val cursor = db.query("SELECT COUNT(*) FROM categories")
                            cursor.moveToFirst()
                            val count = cursor.getInt(0)
                            cursor.close()

                            if (count == 0) {
                                Log.i("AppDatabase", "Categories table empty, seeding defaults...")
                                seedDefaultCategories(db)
                            }
                        } catch (e: Exception) {
                            Log.e("AppDatabase", "Error checking/seeding categories", e)
                        }
                    }
                })
                // Apply all defined migrations
                .addMigrations(*Migrations.ALL_MIGRATIONS)
                // Fallback only for truly unknown version jumps (e.g., very old app versions)
                // This should rarely trigger if migrations are properly maintained
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun clearInstance() {
            synchronized(this) { INSTANCE = null }
        }
    }
}


