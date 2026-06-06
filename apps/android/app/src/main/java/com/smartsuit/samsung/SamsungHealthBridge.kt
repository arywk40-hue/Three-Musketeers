package com.smartsuit.samsung

import com.smartsuit.data.SensorFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface SamsungHealthBridge {
    val state: StateFlow<SamsungHealthState>

    suspend fun connect()
    suspend fun requestPermissions()
    suspend fun writeVitals(frame: SensorFrame)
}

enum class SamsungHealthState {
    Disabled,
    NeedsSdkAar,
    NeedsPermission,
    Ready,
    Error,
}

class NoOpSamsungHealthBridge : SamsungHealthBridge {
    private val _state = MutableStateFlow(SamsungHealthState.NeedsSdkAar)
    override val state: StateFlow<SamsungHealthState> = _state

    override suspend fun connect() = Unit

    override suspend fun requestPermissions() = Unit

    override suspend fun writeVitals(frame: SensorFrame) = Unit
}
