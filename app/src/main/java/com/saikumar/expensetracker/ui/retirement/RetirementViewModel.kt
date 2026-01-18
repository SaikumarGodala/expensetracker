package com.saikumar.expensetracker.ui.retirement

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saikumar.expensetracker.ExpenseTrackerApplication
import com.saikumar.expensetracker.data.entity.RetirementBalance
import com.saikumar.expensetracker.data.entity.RetirementType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RetirementUiState(
    val epfBalance: Long = 0L,
    val npsBalance: Long = 0L,
    val epfHistory: List<RetirementBalance> = emptyList(),
    val npsHistory: List<RetirementBalance> = emptyList()
)

class RetirementViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as ExpenseTrackerApplication).database.retirementDao()
    
    private val _uiState = MutableStateFlow(RetirementUiState())
    val uiState: StateFlow<RetirementUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            dao.getBalanceHistory(RetirementType.EPF).collect { epfList: List<RetirementBalance> ->
                _uiState.update { state ->
                    // EPF: Use the Latest Known Balance (first non-zero balance in history)
                    val latestBalance = epfList.firstOrNull { it.balancePaisa > 0 }?.balancePaisa ?: 0L
                    state.copy(
                        epfBalance = latestBalance,
                        epfHistory = epfList
                    )
                }
            }
        }
        viewModelScope.launch {
            dao.getBalanceHistory(RetirementType.NPS).collect { npsList: List<RetirementBalance> ->
                _uiState.update { state ->
                    // NPS: Sum of ALL contributions
                    val totalContribution = npsList.sumOf { it.contributionPaisa }
                    state.copy(
                        npsBalance = totalContribution,
                        npsHistory = npsList
                    )
                }
            }
        }
    }
}
