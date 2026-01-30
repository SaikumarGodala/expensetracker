package com.saikumar.expensetracker

import android.app.Application
import android.util.Log
import com.saikumar.expensetracker.data.db.AppDatabase
import com.saikumar.expensetracker.data.repository.ExpenseRepository
import com.saikumar.expensetracker.sms.SmsProcessor
import com.saikumar.expensetracker.util.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ExpenseTrackerApplication : Application() {
    companion object {
        lateinit var instance: ExpenseTrackerApplication
            private set
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var _database: AppDatabase? = null
    val database: AppDatabase
        get() {
            synchronized(this) {
                if (_database == null || _database?.isOpen == false) {
                    _database = AppDatabase.getDatabase(this, applicationScope)
                }
                return _database!!
            }
        }

    private var _repository: ExpenseRepository? = null
    val repository: ExpenseRepository
        get() {
            synchronized(this) {
                if (_repository == null || _database?.isOpen == false) { // Recreate repo if DB was closed/recreated
                     _repository = ExpenseRepository(
                        database.categoryDao(), 
                        database.transactionDao(),
                        database.accountDao(),
                        database.merchantPatternDao(),
                        database.merchantMemoryDao(),
                        database.transactionLinkDao(),  // P1 Fix #4
                        database.pendingTransactionDao() // CRITICAL FIX 3
                    ) 
                }
                return _repository!!
            }
        }
    
    val preferencesManager by lazy { PreferencesManager(this) }

    val budgetManager by lazy {
        com.saikumar.expensetracker.util.BudgetManager(
            transactionDao = database.transactionDao(),
            budgetBreachDao = database.budgetBreachDao(),
            categoryDao = database.categoryDao(),
            preferencesManager = preferencesManager
        )
    }

    val merchantBackupManager by lazy {
        com.saikumar.expensetracker.util.MerchantMemoryBackupManager(
            this,
            database.merchantMemoryDao()
        )
    }

    fun forceDatabaseReload() {
        synchronized(this) {
            _database = null
            _repository = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize notification channel
        com.saikumar.expensetracker.util.TransactionNotificationHelper.createNotificationChannel(this)
        
        // Pre-initialize database on a background thread
        applicationScope.launch(Dispatchers.IO) {
            try {
                database.query("SELECT 1", null).close()
                Log.d("ExpenseTrackerApp", "Database pre-initialized successfully")

                // Seed default categories (moved from ViewModel init for better performance)
                repository.seedCategories()
                Log.d("ExpenseTrackerApp", "Categories seeded")

                // Restore merchant memory from backup if needed (handles destructive migration)
                val restoredCount = merchantBackupManager.restoreMerchantMemoryIfNeeded()
                if (restoredCount > 0) {
                    Log.i("ExpenseTrackerApp", "Restored $restoredCount merchant memories from backup")
                }
            } catch (e: Exception) {
                Log.e("ExpenseTrackerApp", "Failed to pre-initialize database", e)
            }
        }
    }
}
