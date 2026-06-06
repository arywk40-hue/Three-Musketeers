package com.smartsuit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartsuit.ble.SmartSuitSimulator
import com.smartsuit.data.SensorFrame
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SmartSuitViewModel : ViewModel() {
    private val simulator = SmartSuitSimulator()

    val frames: StateFlow<SensorFrame?> = simulator.frames.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )
}
