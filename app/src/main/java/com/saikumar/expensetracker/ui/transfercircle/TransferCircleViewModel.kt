package com.saikumar.expensetracker.ui.transfercircle

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saikumar.expensetracker.ExpenseTrackerApplication
import com.saikumar.expensetracker.data.entity.TransferCircleMember
import com.saikumar.expensetracker.data.repository.TransferCircleRepository
import com.saikumar.expensetracker.util.RecipientSuggestion
import com.saikumar.expensetracker.util.TransferCircleDetector
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TransferCircleUiState(
    val members: List<TransferCircleMember> = emptyList(),
    val suggestions: List<RecipientSuggestion> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val candidateList: List<RecipientSuggestion> = emptyList(),
    val isCandidatesLoading: Boolean = false
)

class TransferCircleViewModel(application: Application) : AndroidViewModel(application) {
    
    private val app = application as ExpenseTrackerApplication
    private val repository = TransferCircleRepository(app.database.transferCircleDao())
    private val detector = TransferCircleDetector(
        app.repository,
        repository
    )
    
    private val _uiState = MutableStateFlow(TransferCircleUiState())
    val uiState: StateFlow<TransferCircleUiState> = _uiState.asStateFlow()
    
    init {
        loadData()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // Load members
                repository.getAllMembers().collect { members ->
                    _uiState.update { it.copy(members = members) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
        
        viewModelScope.launch {
            try {
                // Load suggestions
                val suggestions = detector.detectPotentialMembers()
                _uiState.update { it.copy(suggestions = suggestions, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }
    
    fun addMember(name: String, phoneNumber: String? = null, notes: String? = null) {
        viewModelScope.launch {
            repository.addMember(
                name = name,
                phoneNumber = phoneNumber,
                isAutoDetected = false,
                notes = notes
            )
            
            // Retroactively update existing transactions to P2P
            try {
                app.repository.updateTransactionsToP2P(listOf(name))
            } catch (e: Exception) {
                // Log error
            }
            
            // Refresh suggestions
            refreshSuggestions()
        }
    }
    
    fun addMemberFromSuggestion(suggestion: RecipientSuggestion) {
        viewModelScope.launch {
            repository.addMember(
                name = suggestion.name,
                isAutoDetected = true,
                aliases = suggestion.aliases
            )
            
            // Retroactively update existing transactions to P2P
            try {
                val allNames = (suggestion.aliases + suggestion.name).distinct()
                app.repository.updateTransactionsToP2P(allNames)
            } catch (e: Exception) {
                // Log error but don't crash
            }
            
            // Update stats
            repository.updateMemberStats(
                suggestion.name,
                suggestion.transferCount,
                suggestion.totalAmountPaisa
            )
            // Refresh suggestions
            refreshSuggestions()
        }
    }
    
    fun removeMember(id: Long) {
        viewModelScope.launch {
            repository.removeMember(id)
        }
    }
    
    fun ignoreSuggestion(name: String) {
        viewModelScope.launch {
            // Persist ignored status
            repository.ignoreMember(name)
            // Refresh suggestions to update UI
            refreshSuggestions()
        }
    }
    
    private suspend fun refreshSuggestions() {
        try {
            val newSuggestions = detector.detectPotentialMembers()
            _uiState.update { it.copy(suggestions = newSuggestions) }
        } catch (e: Exception) {
            // Silently fail
        }
    }
    
    fun refresh() {
        loadData()
    }

    fun loadAllCandidates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCandidatesLoading = true) }
            try {
                val candidates = detector.detectAllCandidates()
                _uiState.update { it.copy(candidateList = candidates, isCandidatesLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isCandidatesLoading = false) }
            }
        }
    }
}
