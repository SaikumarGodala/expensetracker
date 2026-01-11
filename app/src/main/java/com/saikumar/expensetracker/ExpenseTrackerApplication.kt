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

    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val repository by lazy { 
        ExpenseRepository(
            database.categoryDao(), 
            database.transactionDao(),
            database.accountDao(),
            database.merchantPatternDao()
        ) 
    }
    val preferencesManager by lazy { PreferencesManager(this) }

    override fun onCreate() {
        super.onCreate()
        // Pre-initialize database on a background thread to prevent launch JANK/Crashes
        applicationScope.launch(Dispatchers.IO) {
            try {
                database.query("SELECT 1", null).close()
                Log.d("ExpenseTrackerApp", "Database pre-initialized successfully")
                
                // Load user-defined merchant patterns
                SmsProcessor.loadUserPatterns(this@ExpenseTrackerApplication)
                Log.d("ExpenseTrackerApp", "Merchant patterns loaded")
                
                // NOTE: Removed auto-reclassification on startup for performance
                // Reclassification is now only triggered on user request
            } catch (e: Exception) {
                Log.e("ExpenseTrackerApp", "Failed to pre-initialize database", e)
            }
        }
    }
}
