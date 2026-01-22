package com.saikumar.expensetracker.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.saikumar.expensetracker.sms.SmsProcessor
import com.saikumar.expensetracker.util.PreferencesManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.saikumar.expensetracker.data.dao.BudgetBreachDao
import com.saikumar.expensetracker.data.entity.BudgetBreach

class SettingsViewModel(
    private val preferencesManager: PreferencesManager,
    private val budgetBreachDao: BudgetBreachDao
) : ViewModel() {

    val salaryDay: StateFlow<Int> = preferencesManager.salaryDay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val smsAutoRead: StateFlow<Boolean> = preferencesManager.smsAutoRead
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val debugMode: StateFlow<Boolean> = preferencesManager.debugMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val themeMode: StateFlow<Int> = preferencesManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val colorPalette: StateFlow<String> = preferencesManager.colorPalette
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DYNAMIC")

    fun setSalaryDay(day: Int) {
        viewModelScope.launch {
            preferencesManager.setSalaryDay(day)
        }
    }

    fun setDebugMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDebugMode(enabled)
        }
    }

    fun setSmsAutoRead(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setSmsAutoRead(enabled)
        }
    }
    
    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            preferencesManager.setThemeMode(mode)
        }
    }
    
    fun setColorPalette(palette: String) {
        viewModelScope.launch {
            preferencesManager.setColorPalette(palette)
        }
    }

    fun scanInbox(context: Context) {
        viewModelScope.launch {
            SmsProcessor.scanInbox(context)
        }
    }
    
    // Salary company names for salary detection
    val salaryCompanyNames: StateFlow<Set<String>> = preferencesManager.salaryCompanyNames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    fun addSalaryCompanyName(name: String): Boolean {
        var result = false
        viewModelScope.launch {
            result = preferencesManager.addSalaryCompanyName(name)
        }
        return result
    }
    
    fun removeSalaryCompanyName(name: String) {
        viewModelScope.launch {
            preferencesManager.removeSalaryCompanyName(name)
        }
    }
    
    // Small P2P threshold - below this amount, P2P is treated as merchant expense
    val smallP2pThresholdPaise: StateFlow<Long> = preferencesManager.smallP2pThresholdPaise
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 50000L)
    
    fun setSmallP2pThresholdRupees(rupees: Int) {
        viewModelScope.launch {
            preferencesManager.setSmallP2pThresholdPaise(rupees.toLong() * 100)
        }
    }
    
    // Budget
    val budgetLimitPaise: StateFlow<Long> = preferencesManager.budgetLimitPaise
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    
    val isAutoBudgetEnabled: StateFlow<Boolean> = preferencesManager.isAutoBudgetEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
        
    val isManualBudgetOverride: StateFlow<Boolean> = preferencesManager.isManualBudgetOverride
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setBudgetLimit(limitRupees: Long) {
        viewModelScope.launch {
            preferencesManager.setBudgetLimit(limitRupees * 100L, isManual = true)
        }
    }
    
    fun setAutoBudget(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setIsAutoBudgetEnabled(enabled)
            // If enabling auto, we might want to clear manual flag effectively on next recalc?
            // For now, just toggling preference. Logic in Manager handles precedence (Manual > Auto).
            // If user wants to revert to Auto, they toggle this ON. 
            // But if ManualOverride is true, Manager uses Manual limit.
            // So we strictly need to clear Manual flag if they toggle Auto ON.
            if (enabled) {
                // Reset manual override flag while keeping current limit temporarily until recalc
                // We'll use the existing limit but mark it as NOT manual.
                val current = budgetLimitPaise.value
                preferencesManager.setBudgetLimit(current, isManual = false)
            }
        }
    }

    // Total Interest Earned
    private val _totalInterest = kotlinx.coroutines.flow.MutableStateFlow(0.0)
    val totalInterest: StateFlow<Double> = _totalInterest.asStateFlow()

    fun loadTotalInterest(context: Context) {
        viewModelScope.launch {
            try {
                // Use IO dispatcher for DB ops
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val db = com.saikumar.expensetracker.data.db.AppDatabase.getDatabase(context, viewModelScope)
                    
                    // Use optimized SQL query with Join
                    val interestPaisa = db.transactionDao().getTotalInterestPaisa() ?: 0L
                    _totalInterest.value = interestPaisa / 100.0
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Breach History
    val breachHistory: StateFlow<List<BudgetBreach>> = budgetBreachDao.getAllBreaches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    class Factory(
        private val preferencesManager: PreferencesManager,
        private val budgetBreachDao: BudgetBreachDao
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(preferencesManager, budgetBreachDao) as T
        }
    }
}
