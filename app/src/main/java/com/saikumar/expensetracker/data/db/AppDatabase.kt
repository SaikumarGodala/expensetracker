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
            val categories = com.saikumar.expensetracker.data.common.DefaultCategories.ALL_CATEGORIES
            
            for (category in categories) {
                val isDefaultInt = if (category.isDefault) 1 else 0
                db.execSQL("INSERT OR IGNORE INTO categories (name, type, isEnabled, isDefault, icon) VALUES ('${category.name}', '${category.type.name}', 1, $isDefaultInt, '${category.name}')")
            }
            db.execSQL("INSERT OR IGNORE INTO accounts (name, type, isLiability, isDefault) VALUES ('Primary Bank', 'SAVINGS', 0, 1)")
            db.execSQL("INSERT OR IGNORE INTO accounts (name, type, isLiability, isDefault) VALUES ('Credit Card', 'CREDIT_CARD', 1, 0)")
            db.execSQL("INSERT OR IGNORE INTO accounts (name, type, isLiability, isDefault) VALUES ('Wallet', 'WALLET', 0, 0)")
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
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
                INSTANCE = instance
                instance
            }
        }

        fun clearInstance() {
            synchronized(this) { INSTANCE = null }
        }
    }
}


