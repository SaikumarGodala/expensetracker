package com.saikumar.expensetracker.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.saikumar.expensetracker.data.entity.*
import com.saikumar.expensetracker.data.repository.ExpenseRepository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.saikumar.expensetracker.domain.TransactionRuleEngine

/**
 * Represents the state of a save operation.
 */
sealed class SaveResult {
    object Idle : SaveResult()
    object Loading : SaveResult()
    object Success : SaveResult()
    data class Error(val message: String) : SaveResult()
}

class AddTransactionViewModel(private val repository: ExpenseRepository) : ViewModel() {

    private val _saveResult = MutableStateFlow<SaveResult>(SaveResult.Idle)
    val saveResult: StateFlow<SaveResult> = _saveResult.asStateFlow()

    val categories: StateFlow<List<Category>> = repository.allEnabledCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun resetSaveResult() {
        _saveResult.value = SaveResult.Idle
    }

    /**
     * Save a new transaction
     * @param amountPaisa Amount in paisa (smallest currency unit)
     * @param categoryId Category foreign key
     * @param timestamp UTC epoch milliseconds
     * @param note Optional user note
     * @param manualClassification Optional user override ("INCOME", "EXPENSE", "NEUTRAL", etc.)
     */


    fun saveTransaction(amountPaisa: Long, categoryId: Long, timestamp: Long, note: String, manualClassification: String? = null) {
        if (_saveResult.value == SaveResult.Loading) return // Prevent double-submit

        viewModelScope.launch {
            _saveResult.value = SaveResult.Loading

            try {
                val categories = repository.allEnabledCategories.stateIn(viewModelScope).value
                val category = categories.find { it.id == categoryId }

                if (category == null) {
                    _saveResult.value = SaveResult.Error("Invalid category selected")
                    return@launch
                }

                // Validate amount
                if (amountPaisa <= 0) {
                    _saveResult.value = SaveResult.Error("Amount must be greater than zero")
                    return@launch
                }

                // Use centralized TransactionRuleEngine
                val resolvedType = TransactionRuleEngine.resolveTransactionType(
                    manualClassification = manualClassification,
                    isSelfTransfer = false,
                    category = category
                )

                // Validate: Ensure type is compatible with category
                val finalType = if (TransactionRuleEngine.validateCategoryType(resolvedType, category) is TransactionRuleEngine.ValidationResult.Valid) {
                    resolvedType
                } else {
                    TransactionRuleEngine.getAllowedTypes(category).first()
                }

                val transaction = Transaction(
                    amountPaisa = amountPaisa,
                    categoryId = categoryId,
                    timestamp = timestamp,
                    note = note.ifBlank { null },
                    source = TransactionSource.MANUAL,
                    transactionType = finalType,
                    manualClassification = manualClassification,
                    confidenceScore = 100,
                    isExpenseEligible = (finalType == TransactionType.EXPENSE)
                )

                val rowId = repository.insertTransaction(transaction)
                if (rowId == -1L) {
                    _saveResult.value = SaveResult.Error("Transaction already exists (duplicate)")
                    android.util.Log.w("AddTransactionVM", "Insert failed - duplicate detected")
                } else {
                    _saveResult.value = SaveResult.Success
                    android.util.Log.d("AddTransactionVM", "Transaction saved with id: $rowId")
                }
            } catch (e: Exception) {
                android.util.Log.e("AddTransactionVM", "Save failed", e)
                _saveResult.value = SaveResult.Error(e.message ?: "Failed to save transaction")
            }
        }
    }

    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddTransactionViewModel(repository) as T
        }
    }
}
