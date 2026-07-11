package com.saikumar.expensetracker.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.saikumar.expensetracker.util.PreferencesManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.saikumar.expensetracker.data.dao.BudgetBreachDao
import com.saikumar.expensetracker.data.entity.BudgetBreach
import com.saikumar.expensetracker.util.SnackbarController

class SettingsViewModel(
    private val preferencesManager: PreferencesManager,
    private val budgetBreachDao: BudgetBreachDao
) : ViewModel() {

    val snackbarController = SnackbarController()

    val debugMode: StateFlow<Boolean> = preferencesManager.debugMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val themeMode: StateFlow<Int> = preferencesManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val colorPalette: StateFlow<String> = preferencesManager.colorPalette
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DYNAMIC")

    // Security Settings
    val appLockEnabled: StateFlow<Boolean> = preferencesManager.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appLockPin: StateFlow<String> = preferencesManager.appLockPin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
        
    val biometricEnabled: StateFlow<Boolean> = preferencesManager.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAppLockEnabled(enabled)
        }
    }

    fun setAppLockPin(pin: String) {
        viewModelScope.launch {
            preferencesManager.setAppLockPin(pin)
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setBiometricEnabled(enabled)
        }
    }

    fun setDebugMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDebugMode(enabled)
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

    // Salary company names for salary detection
    val salaryCompanyNames: StateFlow<Set<String>> = preferencesManager.salaryCompanyNames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    fun addSalaryCompanyName(name: String) {
        viewModelScope.launch {
            try {
                val success = preferencesManager.addSalaryCompanyName(name)
                if (success) {
                    snackbarController.showSuccess("Company name added")
                } else {
                    snackbarController.showError("Name must be at least 3 characters")
                }
            } catch (e: Exception) {
                snackbarController.showError("Failed to add company name")
            }
        }
    }
    
    fun removeSalaryCompanyName(name: String) {
        viewModelScope.launch {
            try {
                preferencesManager.removeSalaryCompanyName(name)
                snackbarController.showSuccess("Company name removed")
            } catch (e: Exception) {
                snackbarController.showError("Failed to remove company name")
            }
        }
    }
    
    // P2P threshold: payments to people below this amount count as spending, not transfers
    val smallP2pThresholdPaise: StateFlow<Long> = preferencesManager.smallP2pThresholdPaise
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 50000L)

    fun setSmallP2pThresholdRupees(rupees: Long) {
        viewModelScope.launch {
            preferencesManager.setSmallP2pThresholdPaise(rupees * 100)
        }
    }

    // Budget
    val budgetLimitPaise: StateFlow<Long> = preferencesManager.budgetLimitPaise
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val isAutoBudgetEnabled: StateFlow<Boolean> = preferencesManager.isAutoBudgetEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

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

    // Breach History
    val breachHistory: StateFlow<List<BudgetBreach>> = budgetBreachDao.getAllBreaches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Merchant Memory Backup
    private val _backupInfo = kotlinx.coroutines.flow.MutableStateFlow<com.saikumar.expensetracker.util.MerchantMemoryBackupManager.BackupInfo?>(null)
    val backupInfo: StateFlow<com.saikumar.expensetracker.util.MerchantMemoryBackupManager.BackupInfo?> = _backupInfo.asStateFlow()

    fun loadBackupInfo(context: Context) {
        viewModelScope.launch {
            try {
                val app = context.applicationContext as? com.saikumar.expensetracker.ExpenseTrackerApplication
                    ?: run {
                        snackbarController.showError("Application context unavailable")
                        return@launch
                    }
                val info = app.merchantBackupManager.getBackupInfo()
                _backupInfo.value = info
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to load backup info", e)
            }
        }
    }

    fun backupMerchantMemory(context: Context) {
        viewModelScope.launch {
            try {
                val app = context.applicationContext as? com.saikumar.expensetracker.ExpenseTrackerApplication
                    ?: run {
                        snackbarController.showError("Application context unavailable")
                        return@launch
                    }
                val success = app.merchantBackupManager.backupMerchantMemory()
                if (success) {
                    snackbarController.showSuccess("Category preferences backed up")
                    loadBackupInfo(context) // Refresh backup info
                } else {
                    snackbarController.showError("Failed to backup category preferences")
                }
            } catch (e: Exception) {
                snackbarController.showError("Backup failed: ${e.message}")
            }
        }
    }

    class Factory(
        private val preferencesManager: PreferencesManager,
        private val budgetBreachDao: BudgetBreachDao
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(preferencesManager, budgetBreachDao) as T
        }
    }
}
