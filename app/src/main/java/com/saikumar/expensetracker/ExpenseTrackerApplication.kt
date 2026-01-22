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

    fun forceDatabaseReload() {
        synchronized(this) {
            _database = null
            _repository = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize notification channel
        com.saikumar.expensetracker.util.TransactionNotificationHelper.createNotificationChannel(this)
        
        // Initialize ML Classifier
        com.saikumar.expensetracker.ml.NaiveBayesClassifier.load(this)
        
        // Pre-initialize database on a background thread
        applicationScope.launch(Dispatchers.IO) {
            try {
                database.query("SELECT 1", null).close()
                Log.d("ExpenseTrackerApp", "Database pre-initialized successfully")
            } catch (e: Exception) {
                Log.e("ExpenseTrackerApp", "Failed to pre-initialize database", e)
            }
        }
    }
}
