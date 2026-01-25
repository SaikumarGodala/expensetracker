package com.saikumar.expensetracker.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class ScanState {
    object Idle : ScanState()
    data class Scanning(val current: Int, val total: Int) : ScanState() {
        val progress: Float
            get() = if (total > 0) current.toFloat() / total else 0f
    }
}

object ScanProgressManager {
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    fun start(total: Int) {
        _scanState.value = ScanState.Scanning(0, total)
    }

    fun update(current: Int) {
        val currentState = _scanState.value
        if (currentState is ScanState.Scanning) {
            _scanState.value = currentState.copy(current = current)
        }
    }

    fun finish() {
        _scanState.value = ScanState.Idle
    }
}
