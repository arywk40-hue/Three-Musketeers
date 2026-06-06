package com.smartsuit.samsung

import com.smartsuit.data.SensorFrame

interface SamsungHealthBridge {
    val state: SamsungHealthState

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
    override val state: SamsungHealthState = SamsungHealthState.NeedsSdkAar

    override suspend fun connect() = Unit

    override suspend fun requestPermissions() = Unit

    override suspend fun writeVitals(frame: SensorFrame) = Unit
}
