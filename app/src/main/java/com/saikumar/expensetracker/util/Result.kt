package com.saikumar.expensetracker.util

/**
 * Sealed class representing the result of an operation.
 * Replaces nullable returns and silent failures with explicit states.
 */
sealed class Result<out T> {
    /**
     * Operation completed successfully with data.
     */
    data class Success<T>(val data: T) : Result<T>()
    
    /**
     * Operation failed with an error.
     * @param exception The original exception
     * @param message User-friendly error message
     * @param isRetryable Whether the operation can be retried
     */
    data class Error(
        val exception: Throwable,
        val message: String,
        val isRetryable: Boolean = false
    ) : Result<Nothing>()
    
    /**
     * Operation is in progress.
     */
    object Loading : Result<Nothing>()
}

/**
 * Extension to get data or null.
 */
fun <T> Result<T>.getOrNull(): T? = when (this) {
    is Result.Success -> data
    else -> null
}

/**
 * Extension to check if result is success.
 */
fun <T> Result<T>.isSuccess(): Boolean = this is Result.Success

/**
 * Extension to check if result is error.
 */
fun <T> Result<T>.isError(): Boolean = this is Result.Error

/**
 * Map success data to another type.
 */
inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Error -> this
    is Result.Loading -> this
}
