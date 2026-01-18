package com.saikumar.expensetracker.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saikumar.expensetracker.ExpenseTrackerApplication
import com.saikumar.expensetracker.data.entity.LinkType
import com.saikumar.expensetracker.data.entity.LinkWithDetails
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LinkManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as ExpenseTrackerApplication).repository
    
    // All links from DB
    val allLinks: StateFlow<List<LinkWithDetails>> = repository.allLinksWithDetails
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
    // Links grouped by type for UI sections
    val selfTransferLinks = allLinks.map { list ->
        list.filter { it.link.linkType == LinkType.SELF_TRANSFER }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val refundLinks = allLinks.map { list ->
        list.filter { it.link.linkType == LinkType.REFUND }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val ccLinks = allLinks.map { list ->
        list.filter { it.link.linkType == LinkType.CC_PAYMENT }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteLink(linkId: Long) {
        viewModelScope.launch {
            repository.deleteLink(linkId)
        }
    }
}
