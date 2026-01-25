package com.saikumar.expensetracker.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Represents a message to be shown in a Snackbar.
 */
data class SnackbarMessage(
    val id: String = UUID.randomUUID().toString(),
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val duration: SnackbarDuration = SnackbarDuration.Short
)

enum class SnackbarDuration {
    Short,
    Long,
    Indefinite
}

/**
 * Controller for managing Snackbar messages across the app.
 * Use this to show errors, success messages, and other user feedback.
 */
class SnackbarController {
    private val _messages = MutableStateFlow<List<SnackbarMessage>>(emptyList())
    val messages: StateFlow<List<SnackbarMessage>> = _messages.asStateFlow()
    
    /**
     * Show an error message.
     */
    fun showError(
        message: String,
        actionLabel: String? = "Retry",
        onAction: (() -> Unit)? = null
    ) {
        val snackbarMessage = SnackbarMessage(
            message = message,
            actionLabel = actionLabel,
            onAction = onAction,
            duration = SnackbarDuration.Long
        )
        _messages.value = _messages.value + snackbarMessage
    }
    
    /**
     * Show a success message.
     */
    fun showSuccess(message: String) {
        val snackbarMessage = SnackbarMessage(
            message = message,
            duration = SnackbarDuration.Short
        )
        _messages.value = _messages.value + snackbarMessage
    }
    
    /**
     * Show an info message.
     */
    fun showInfo(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        val snackbarMessage = SnackbarMessage(
            message = message,
            actionLabel = actionLabel,
            onAction = onAction,
            duration = SnackbarDuration.Short
        )
        _messages.value = _messages.value + snackbarMessage
    }
    
    /**
     * Dismiss a specific message by ID.
     */
    fun dismiss(id: String) {
        _messages.value = _messages.value.filterNot { it.id == id }
    }
    
    /**
     * Clear all messages.
     */
    fun clearAll() {
        _messages.value = emptyList()
    }
}
