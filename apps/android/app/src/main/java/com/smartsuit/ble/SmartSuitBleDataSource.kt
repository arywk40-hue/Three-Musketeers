package com.smartsuit.ble

import android.content.Context
import com.smartsuit.data.SensorFrame
import com.smartsuit.data.SmartSuitDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

class SmartSuitBleDataSource(
    private val context: Context,
) : SmartSuitDataSource {
    val connectionState = MutableStateFlow(BleConnectionState.Idle)

    override val frames: Flow<SensorFrame> = emptyFlow()

    fun startScan() {
        connectionState.value = BleConnectionState.Scanning
    }

    fun stop() {
        connectionState.value = BleConnectionState.Idle
    }
}
