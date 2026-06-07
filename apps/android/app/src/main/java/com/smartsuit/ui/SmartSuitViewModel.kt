package com.smartsuit.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartsuit.ble.BleConnectionState
import com.smartsuit.ble.DiscoveredBleDevice
import com.smartsuit.ble.SmartSuitBleDataSource
import com.smartsuit.ble.SmartSuitBleTelemetry
import com.smartsuit.ble.SmartSuitSimulator
import com.smartsuit.data.AlertEvent
import com.smartsuit.data.AlertReason
import com.smartsuit.data.CaregiverAlertStatus
import com.smartsuit.data.RiskStatus
import com.smartsuit.data.SensorFrame
import com.smartsuit.data.SensorFrameMerger
import com.smartsuit.database.ElderCareDatabase
import com.smartsuit.database.toAlertEvent
import com.smartsuit.database.toEntity
import com.smartsuit.samsung.NoOpSamsungHealthBridge
import com.smartsuit.samsung.SamsungHealthBridge
import com.smartsuit.samsung.SamsungHealthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SmartSuitViewModel(application: Application) : AndroidViewModel(application) {
    private val simulator = SmartSuitSimulator()
    private val bleDataSource = SmartSuitBleDataSource(application.applicationContext)
    private val samsungBridge: SamsungHealthBridge = NoOpSamsungHealthBridge()
    private val db = ElderCareDatabase.getInstance(application.applicationContext)

    private val _sosOverride = MutableStateFlow(false)
    val sosOverride: StateFlow<Boolean> = _sosOverride.asStateFlow()

    private val _acknowledgedUrgent = MutableStateFlow(false)
    val acknowledgedUrgent: StateFlow<Boolean> = _acknowledgedUrgent.asStateFlow()

    private val _alertHistory = MutableStateFlow<List<AlertEvent>>(emptyList())
    val alertHistory: StateFlow<List<AlertEvent>> = _alertHistory.asStateFlow()

    private val _hrTrend = MutableStateFlow<List<Float>>(emptyList())
    val hrTrend: StateFlow<List<Float>> = _hrTrend.asStateFlow()

    private val _spo2Trend = MutableStateFlow<List<Float>>(emptyList())
    val spo2Trend: StateFlow<List<Float>> = _spo2Trend.asStateFlow()

    private var previousAlertLevel: CaregiverAlertStatus? = null

    val frames: StateFlow<SensorFrame?> = combine(
        simulator.frames,
        bleDataSource.telemetry,
        _sosOverride,
    ) { simFrame, bleTelemetry, sosActive ->
        val baseFrame = if (bleDataSource.connectionState.value == BleConnectionState.Connected && bleTelemetry.heartRateBpm != null) {
            SensorFrameMerger.merge(simFrame, bleTelemetry)
        } else {
            simFrame
        }
        if (sosActive) {
            baseFrame.copy(sosActive = true, caregiverAlert = CaregiverAlertStatus.Urgent)
        } else {
            baseFrame
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )

    init {
        // Seed in-memory history from persisted Room data on startup
        viewModelScope.launch {
            db.alertEventDao().getRecent().collect { entities ->
                val persisted = entities.map { it.toAlertEvent() }
                if (_alertHistory.value.isEmpty() && persisted.isNotEmpty()) {
                    _alertHistory.value = persisted
                }
            }
        }

        // Watch live frames, update trends and append to alert history on transitions
        viewModelScope.launch {
            frames.filterNotNull().collect { frame ->
                _hrTrend.value = (_hrTrend.value + frame.heartRateBpm.toFloat()).takeLast(60)
                _spo2Trend.value = (_spo2Trend.value + frame.spo2Percent).takeLast(60)

                val current = frame.caregiverAlert
                val previous = previousAlertLevel
                if (previous != null && current != previous) {
                    val reason = when {
                        frame.sosActive -> AlertReason.SosButton
                        frame.fallRisk == RiskStatus.High -> AlertReason.FallDetected
                        frame.heartRateBpm > 130 -> AlertReason.HeartRateHigh
                        frame.heartRateBpm < 40 -> AlertReason.HeartRateLow
                        frame.spo2Percent < 90f -> AlertReason.LowSpO2
                        frame.inactivityMinutes > 20 -> AlertReason.Inactivity
                        current == CaregiverAlertStatus.Normal -> AlertReason.Resolved
                        else -> AlertReason.DeviceAlert
                    }
                    val event = AlertEvent(level = current, reason = reason)
                    _alertHistory.value = (listOf(event) + _alertHistory.value).take(50)
                    if (current != CaregiverAlertStatus.Urgent) {
                        _acknowledgedUrgent.value = false
                    }
                    viewModelScope.launch(Dispatchers.IO) {
                        db.alertEventDao().insert(event.toEntity())
                        db.alertEventDao().deleteOlderThan(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L)
                    }
                }
                previousAlertLevel = current
            }
        }
    }

    val bleConnectionState: StateFlow<BleConnectionState> = bleDataSource.connectionState
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = bleDataSource.discoveredDevices
    val bleTelemetry: StateFlow<SmartSuitBleTelemetry> = bleDataSource.telemetry
    val samsungState: StateFlow<SamsungHealthState> = samsungBridge.state

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

    fun startSamsungBridge() {
        viewModelScope.launch(Dispatchers.IO) {
            samsungBridge.connect()
            samsungBridge.requestPermissions()
        }
    }

    fun triggerSosDemo() { _sosOverride.value = true }
    fun clearSosDemo() { _sosOverride.value = false }
    fun acknowledgeUrgent() { _acknowledgedUrgent.value = true }

    override fun onCleared() {
        bleDataSource.stop()
        super.onCleared()
    }
}
