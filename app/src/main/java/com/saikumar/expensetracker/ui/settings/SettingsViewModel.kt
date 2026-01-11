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
import kotlinx.coroutines.launch

class SettingsViewModel(private val preferencesManager: PreferencesManager) : ViewModel() {

    val salaryDay: StateFlow<Int> = preferencesManager.salaryDay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val smsAutoRead: StateFlow<Boolean> = preferencesManager.smsAutoRead
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val debugMode: StateFlow<Boolean> = preferencesManager.debugMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    class Factory(private val preferencesManager: PreferencesManager) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(preferencesManager) as T
        }
    }
}
