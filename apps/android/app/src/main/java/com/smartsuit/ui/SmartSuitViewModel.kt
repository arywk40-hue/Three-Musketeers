package com.smartsuit.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartsuit.ble.BleConnectionState
import com.smartsuit.ble.DiscoveredBleDevice
import com.smartsuit.ble.SmartSuitBleDataSource
import com.smartsuit.ble.SmartSuitBleTelemetry
import com.smartsuit.ble.SmartSuitSimulator
import com.smartsuit.data.SensorFrame
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SmartSuitViewModel(application: Application) : AndroidViewModel(application) {
    private val simulator = SmartSuitSimulator()
    private val bleDataSource = SmartSuitBleDataSource(application.applicationContext)

    val frames: StateFlow<SensorFrame?> = simulator.frames.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )

    val bleConnectionState: StateFlow<BleConnectionState> = bleDataSource.connectionState
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = bleDataSource.discoveredDevices
    val bleTelemetry: StateFlow<SmartSuitBleTelemetry> = bleDataSource.telemetry

    fun startBleScan() {
        bleDataSource.startScan()
    }

    fun stopBle() {
        bleDataSource.stop()
    }

    fun connectToFirstDiscoveredDevice() {
        val firstDevice = discoveredDevices.value.firstOrNull() ?: return
        bleDataSource.connect(firstDevice.address)
    }

    override fun onCleared() {
        bleDataSource.stop()
        super.onCleared()
    }
}
