package com.eldercareguardian.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eldercareguardian.MainActivity
import com.eldercareguardian.ble.BleConnectionState
import com.eldercareguardian.ble.DiscoveredBleDevice
import com.eldercareguardian.ble.SmartSuitBleDataSource
import com.eldercareguardian.ble.SmartSuitBleTelemetry
import com.eldercareguardian.ble.SmartSuitSimulator
import com.eldercareguardian.data.AlertEvent
import com.eldercareguardian.data.CaregiverAlertStatus
import com.eldercareguardian.data.SensorFrame
import com.eldercareguardian.data.SensorFrameMerger
import com.eldercareguardian.database.ElderCareDatabase
import com.eldercareguardian.database.toAlertEvent
import com.eldercareguardian.database.toEntity
import com.eldercareguardian.ml.AlertHistoryTracker
import com.eldercareguardian.notifications.NotificationHelper
import com.eldercareguardian.samsung.NoOpSamsungHealthBridge
import com.eldercareguardian.samsung.SamsungHealthBridge
import com.eldercareguardian.samsung.SamsungHealthState
import com.eldercareguardian.service.ElderCareMonitorService
import com.eldercareguardian.settings.CaregiverPreferences
import com.eldercareguardian.settings.isValidPhone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmartSuitViewModel(application: Application) : AndroidViewModel(application) {
    private val simulator = SmartSuitSimulator()
    private val bleDataSource = SmartSuitBleDataSource(application.applicationContext)
    private val samsungBridge: SamsungHealthBridge = NoOpSamsungHealthBridge()
    private val db = ElderCareDatabase.getInstance(application.applicationContext)
    private val alertHistoryTracker = AlertHistoryTracker()
    private val prefs = CaregiverPreferences.getInstance(application)

    private val _sosOverride = MutableStateFlow(false)
    val sosOverride: StateFlow<Boolean> = _sosOverride.asStateFlow()

    private val _acknowledgedEmergency = MutableStateFlow(false)
    val acknowledgedEmergency: StateFlow<Boolean> = _acknowledgedEmergency.asStateFlow()

    /** Back-compat alias for acknowledgedEmergency (renamed from acknowledgedUrgent). */
    val acknowledgedUrgent: StateFlow<Boolean> get() = _acknowledgedEmergency

    private val _alertHistory = MutableStateFlow<List<AlertEvent>>(emptyList())
    val alertHistory: StateFlow<List<AlertEvent>> = _alertHistory.asStateFlow()

    private val _hrTrend = MutableStateFlow<List<Float>>(emptyList())
    val hrTrend: StateFlow<List<Float>> = _hrTrend.asStateFlow()

    private val _spo2Trend = MutableStateFlow<List<Float>>(emptyList())
    val spo2Trend: StateFlow<List<Float>> = _spo2Trend.asStateFlow()

    // Caregiver contact — collected from DataStore-backed flows. Initial value
    // is the DataStore default (empty phone, "Caregiver" name) so the UI has
    // something to render before the first read completes.
    val caregiverPhoneNumber: StateFlow<String> = prefs.caregiverPhone.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = CaregiverPreferences.DEFAULT_PHONE,
    )
    val caregiverDisplayName: StateFlow<String> = prefs.caregiverName.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = CaregiverPreferences.DEFAULT_NAME,
    )

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
            baseFrame.copy(sosActive = true, caregiverAlert = CaregiverAlertStatus.Emergency)
        } else {
            baseFrame
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )

    init {
        // One-shot seed of in-memory history from persisted Room data on startup.
        viewModelScope.launch {
            val persisted = withContext(Dispatchers.IO) {
                db.alertEventDao().getRecent().first()
            }.map { it.toAlertEvent() }
            if (_alertHistory.value.isEmpty() && persisted.isNotEmpty()) {
                _alertHistory.value = persisted
            }
        }

        // Watch live frames, update trends and append to alert history on transitions.
        viewModelScope.launch {
            frames.filterNotNull().collect { frame ->
                _hrTrend.value = (_hrTrend.value + frame.heartRateBpm.toFloat()).takeLast(60)
                _spo2Trend.value = (_spo2Trend.value + frame.spo2Percent).takeLast(60)

                val event = alertHistoryTracker.onFrame(frame)
                if (event != null) {
                    _alertHistory.value = alertHistoryTracker.prepend(_alertHistory.value, event)
                    when (event.level) {
                        CaregiverAlertStatus.Emergency -> {
                            _acknowledgedEmergency.value = false
                            if (!_acknowledgedEmergency.value) {
                                postEmergencyNotification(event)
                            }
                        }
                        CaregiverAlertStatus.Warning -> {
                            NotificationHelper.cancelEmergency(getApplication())
                            postWarningNotification(event)
                        }
                        else -> {
                            // Check or Normal — cancel any outstanding emergency/warning notifications
                            NotificationHelper.cancelEmergency(getApplication())
                            NotificationHelper.cancelWarning(getApplication())
                        }
                    }
                    viewModelScope.launch(Dispatchers.IO) {
                        db.alertEventDao().insert(event.toEntity())
                        db.alertEventDao().deleteOlderThan(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L)
                    }
                }
            }
        }

        // Samsung Health write cadence — fires every 5 s when bridge is connected.
        // NoOp until the real AAR is installed (isAvailable returns false); zero
        // performance cost when dormant.
        viewModelScope.launch {
            while (true) {
                delay(5_000L)
                val frame = frames.value ?: continue
                if (samsungBridge.isAvailable()) {
                    samsungBridge.writeVitals(frame)
                }
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
    fun acknowledgeUrgent() { _acknowledgedEmergency.value = true }
    fun acknowledgeEmergency() { _acknowledgedEmergency.value = true }

    /**
     * Persist the caregiver contact. Both fields must be non-blank; the
     * phone must contain at least 7 digits. Returns true on success.
     */
    suspend fun updateCaregiverContact(name: String, phone: String): Boolean {
        val trimmedName = name.trim()
        val trimmedPhone = phone.trim()
        if (trimmedName.isBlank() || !isValidPhone(trimmedPhone)) return false
        withContext(Dispatchers.IO) {
            prefs.setBoth(trimmedName, trimmedPhone)
        }
        return true
    }

    fun buildCaregiverDialIntent(): Intent? {
        val phone = caregiverPhoneNumber.value
        if (!isValidPhone(phone)) return null
        return com.eldercareguardian.notifications.CaregiverContact.dialIntent(phone)
    }

    private fun postEmergencyNotification(event: AlertEvent) {
        val context = getApplication<Application>()
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        NotificationHelper.postEmergencyAlert(context, event, openApp)
    }

    private fun postWarningNotification(event: AlertEvent) {
        val context = getApplication<Application>()
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        NotificationHelper.postWarningAlert(context, event, openApp)
    }

    override fun onCleared() {
        bleDataSource.stop()
        super.onCleared()
    }
}
