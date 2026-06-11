package com.eldercareguardian.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.eldercareguardian.data.SensorFrame
import com.eldercareguardian.data.SmartSuitDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.util.UUID

/**
 * BLE GATT client for ElderCare Guardian wearable.
 *
 * Phase 3 improvements:
 *  - Auto-reconnect with exponential back-off on unexpected disconnect.
 *  - Scan timeout (30 s) + switch to BALANCED mode after first device found.
 *  - Scan timeout handler posts a stop if no device connects within 30 seconds.
 *  - Max reconnect attempts (10) before surfacing a permanent Error state.
 *  - BLE state machine is explicit: Idle → Scanning → Connecting → Bonding →
 *    Bonded → Connected → Disconnected → Reconnecting → Error.
 *
 * Threading note: All GATT callbacks arrive on the BLE thread. MutableStateFlow
 * updates are thread-safe. Do NOT call gatt.close() from a GATT callback — post
 * it on the main handler to avoid deadlocks in the Android BLE stack.
 */
class SmartSuitBleDataSource(
    context: Context,
) : SmartSuitDataSource {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter = bluetoothManager?.adapter

    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private var notificationQueue: List<BluetoothGattCharacteristic> = emptyList()
    private var notificationIndex = 0
    private val devicesByAddress = mutableMapOf<String, BluetoothDevice>()
    private var bondReceiverRegistered = false
    private var pendingBond = false

    // Auto-reconnect state
    private var targetAddress: String? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 10

    // Scan mode switching
    private var hasSwitchedToBalanced = false

    // Scan timeout: stop scanning after 30 s if nothing connects
    private val scanTimeoutMs = 30_000L
    private val scanTimeoutRunnable = Runnable { stopScanOnly() }

    private val _connectionState = MutableStateFlow(BleConnectionState.Idle)
    val connectionState: StateFlow<BleConnectionState> = _connectionState

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = _discoveredDevices

    private val _telemetry = MutableStateFlow(SmartSuitBleTelemetry())
    val telemetry: StateFlow<SmartSuitBleTelemetry> = _telemetry

    private val _isLiveData = MutableStateFlow(false)
    val isLiveData: StateFlow<Boolean> = _isLiveData

    override val frames: Flow<SensorFrame> = emptyFlow()

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun startScan() {
        try {
            val adapter = bluetoothAdapter
            if (adapter == null || !adapter.isEnabled) {
                _connectionState.value = BleConnectionState.Unsupported
                return
            }

            val scanner = adapter.bluetoothLeScanner ?: run {
                _connectionState.value = BleConnectionState.Unsupported
                return
            }

            stopScanOnly()
            _discoveredDevices.value = emptyList()
            _connectionState.value = BleConnectionState.Scanning

            val filter = ScanFilter.Builder()
                .setDeviceName(SmartSuitBleContract.DEVICE_NAME)
                .build()

            // Start with LOW_LATENCY for fast initial discovery, the scan-result
            // callback switches to BALANCED after the first hit to save battery.
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device ?: return
                    val name = result.scanRecord?.deviceName ?: device.name
                        ?: SmartSuitBleContract.DEVICE_NAME
                    val discovered = DiscoveredBleDevice(
                        name = name,
                        address = device.address,
                        rssi = result.rssi,
                    )
                    devicesByAddress[device.address] = device
                    _discoveredDevices.value = (_discoveredDevices.value
                        .filterNot { it.address == discovered.address } + discovered)
                        .sortedByDescending { it.rssi }

                    // Switch to BALANCED mode after first discovery to save battery
                    if (!hasSwitchedToBalanced) {
                        hasSwitchedToBalanced = true
                        switchToBalancedScanMode(scanner)
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    _connectionState.value = BleConnectionState.Error
                }
            }

            scanner.startScan(listOf(filter), settings, scanCallback)

            // Auto-stop scan after 30 s
            mainHandler.removeCallbacks(scanTimeoutRunnable)
            mainHandler.postDelayed(scanTimeoutRunnable, scanTimeoutMs)
        } catch (_: SecurityException) {
            _connectionState.value = BleConnectionState.PermissionMissing
        }
    }

    @SuppressLint("MissingPermission")
    private fun switchToBalancedScanMode(scanner: android.bluetooth.le.BluetoothLeScanner) {
        val callback = scanCallback ?: return
        try {
            scanner.stopScan(callback)
            val balancedSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build()
            scanner.startScan(listOf(
                ScanFilter.Builder()
                    .setDeviceName(SmartSuitBleContract.DEVICE_NAME)
                    .build()
            ), balancedSettings, callback)
        } catch (_: SecurityException) {
            _connectionState.value = BleConnectionState.PermissionMissing
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        try {
            val device = devicesByAddress[address] ?: run {
                // Address known but device object not cached — try to get from adapter.
                val remote = bluetoothAdapter?.getRemoteDevice(address) ?: return
                devicesByAddress[address] = remote
                remote
            }
            targetAddress = address
            reconnectAttempts = 0
            connectInternal(device)
        } catch (_: SecurityException) {
            _connectionState.value = BleConnectionState.PermissionMissing
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try {
            targetAddress = null
            reconnectAttempts = 0
            mainHandler.removeCallbacks(scanTimeoutRunnable)
            mainHandler.removeCallbacks(reconnectRunnable)
            stopScanOnly()
            unregisterBondReceiver()
            pendingBond = false
            closeGatt()
            _connectionState.value = BleConnectionState.Idle
            _isLiveData.value = false
        } catch (_: SecurityException) {
            _connectionState.value = BleConnectionState.PermissionMissing
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal connection helpers
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun connectInternal(device: BluetoothDevice) {
        stopScanOnly()
        registerBondReceiver()
        pendingBond = false
        _connectionState.value = BleConnectionState.Connecting
        // autoConnect = false for faster initial connection; set true for background reconnect.
        val autoConnect = reconnectAttempts > 0
        gatt = device.connectGatt(appContext, autoConnect, gattCallback)
    }

    private val reconnectRunnable = Runnable {
        val address = targetAddress ?: return@Runnable
        val device = devicesByAddress[address]
            ?: bluetoothAdapter?.getRemoteDevice(address)
            ?: run {
                _connectionState.value = BleConnectionState.Error
                return@Runnable
            }
        reconnectAttempts++
        _connectionState.value = BleConnectionState.Reconnecting
        @Suppress("DEPRECATION")
        try {
            connectInternal(device)
        } catch (_: SecurityException) {
            _connectionState.value = BleConnectionState.PermissionMissing
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            _connectionState.value = BleConnectionState.Error
            return
        }
        // Exponential back-off: 2s, 4s, 8s … capped at 60s
        val delayMs = minOf(2_000L shl reconnectAttempts, 60_000L)
        _connectionState.value = BleConnectionState.Reconnecting
        mainHandler.postDelayed(reconnectRunnable, delayMs)
    }

    private fun closeGatt() {
        mainHandler.post {
            @Suppress("DEPRECATION")
            try {
                gatt?.close()
            } catch (_: Exception) { }
            gatt = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanOnly() {
        mainHandler.removeCallbacks(scanTimeoutRunnable)
        hasSwitchedToBalanced = false
        val callback = scanCallback ?: return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (_: SecurityException) { }
        scanCallback = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bond receiver
    // ─────────────────────────────────────────────────────────────────────────

    private val bondReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                ?: return
            val bondState = intent.getIntExtra(
                BluetoothDevice.EXTRA_BOND_STATE,
                BluetoothDevice.BOND_NONE,
            )
            when (bondState) {
                BluetoothDevice.BOND_BONDING -> {
                    _connectionState.value = BleConnectionState.Bonding
                }
                BluetoothDevice.BOND_BONDED -> {
                    pendingBond = false
                    _connectionState.value = BleConnectionState.Bonded
                    reconnectGatt(device)
                }
                BluetoothDevice.BOND_NONE -> {
                    // Pairing failed
                    if (pendingBond) {
                        pendingBond = false
                        scheduleReconnect()
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun reconnectGatt(device: BluetoothDevice) {
        closeGatt()
        mainHandler.postDelayed({
            try {
                gatt = device.connectGatt(appContext, false, gattCallback)
            } catch (_: SecurityException) {
                _connectionState.value = BleConnectionState.PermissionMissing
            }
        }, 500L)
    }

    private fun registerBondReceiver() {
        if (bondReceiverRegistered) return
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        appContext.registerReceiver(bondReceiver, filter)
        bondReceiverRegistered = true
    }

    private fun unregisterBondReceiver() {
        if (!bondReceiverRegistered) return
        try { appContext.unregisterReceiver(bondReceiver) } catch (_: IllegalArgumentException) { }
        bondReceiverRegistered = false
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GATT callbacks
    // ─────────────────────────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != GATT_SUCCESS) {
                if (!pendingBond) {
                    // Unexpected disconnect or connection failure — schedule reconnect
                    closeGatt()
                    scheduleReconnect()
                }
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    reconnectAttempts = 0  // Reset back-off on successful connection
                    _connectionState.value = BleConnectionState.Connected
                    if (gatt.device.bondState == BluetoothDevice.BOND_BONDED) {
                        _connectionState.value = BleConnectionState.Bonded
                        gatt.requestMtu(517)
                    } else {
                        pendingBond = true
                        _connectionState.value = BleConnectionState.Bonding
                        gatt.device.createBond()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (!pendingBond) {
                        _connectionState.value = BleConnectionState.Disconnected
                        _isLiveData.value = false
                        closeGatt()
                        // Auto-reconnect if we have a target address
                        if (targetAddress != null) {
                            scheduleReconnect()
                        }
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == GATT_SUCCESS) {
                gatt.discoverServices()
            } else {
                // MTU negotiation failed; proceed with default MTU but warn about ECG payload.
                // At ATT_MTU 23, 1024-byte ECG will require ~45 packets and may be truncated.
                gatt.discoverServices()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != GATT_SUCCESS) {
                _connectionState.value = BleConnectionState.Error
                return
            }
            notificationQueue = collectNotifyCharacteristics(gatt)
            notificationIndex = 0
            subscribeNext(gatt)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            handleNotification(characteristic.uuid, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleNotification(characteristic.uuid, value)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status != GATT_SUCCESS) {
                // Descriptor write failed — skip and try next (don't abort all subscriptions)
                subscribeNext(gatt)
                return
            }
            subscribeNext(gatt)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification subscription queue
    // ─────────────────────────────────────────────────────────────────────────

    private fun collectNotifyCharacteristics(gatt: BluetoothGatt): List<BluetoothGattCharacteristic> {
        return listOfNotNull(
            gatt.findCharacteristic(SmartSuitBleContract.BATTERY_SERVICE, SmartSuitBleContract.BATTERY_LEVEL),
            gatt.findCharacteristic(SmartSuitBleContract.HEART_RATE_SERVICE, SmartSuitBleContract.HEART_RATE_MEASUREMENT),
            gatt.findCharacteristic(SmartSuitBleContract.PLX_SERVICE, SmartSuitBleContract.PLX_CONTINUOUS_MEASUREMENT),
            gatt.findCharacteristic(SmartSuitBleContract.CUSTOM_SERVICE, SmartSuitBleContract.ECG_RAW),
            gatt.findCharacteristic(SmartSuitBleContract.CUSTOM_SERVICE, SmartSuitBleContract.IMU_WRIST),
            gatt.findCharacteristic(SmartSuitBleContract.CUSTOM_SERVICE, SmartSuitBleContract.SOS_STATE),
            gatt.findCharacteristic(SmartSuitBleContract.CUSTOM_SERVICE, SmartSuitBleContract.FALL_RISK),
            gatt.findCharacteristic(SmartSuitBleContract.CUSTOM_SERVICE, SmartSuitBleContract.HUMIDITY),
            gatt.findCharacteristic(SmartSuitBleContract.CUSTOM_SERVICE, SmartSuitBleContract.RESP_RATE),
            gatt.findCharacteristic(SmartSuitBleContract.CUSTOM_SERVICE, SmartSuitBleContract.DEVICE_STATE),
        ).filter { characteristic ->
            (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscribeNext(gatt: BluetoothGatt) {
        if (notificationIndex >= notificationQueue.size) {
            // All notifications subscribed — we're now receiving live data
            _isLiveData.value = true
            return
        }

        val characteristic = notificationQueue[notificationIndex++]
        val cccd = characteristic.getDescriptor(SmartSuitBleContract.CLIENT_CHARACTERISTIC_CONFIG)
        if (cccd == null) {
            subscribeNext(gatt)
            return
        }

        try {
            gatt.setCharacteristicNotification(characteristic, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
            }
        } catch (_: SecurityException) {
            _connectionState.value = BleConnectionState.PermissionMissing
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification parsing
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleNotification(uuid: UUID, payload: ByteArray) {
        _telemetry.value = when (uuid) {
            SmartSuitBleContract.BATTERY_LEVEL -> {
                _telemetry.value.copy(
                    batteryPercent = SensorFrameValidation.batteryPercent(
                        SmartSuitBleParser.parseBatteryLevel(payload),
                    ),
                )
            }
            SmartSuitBleContract.HEART_RATE_MEASUREMENT -> {
                _telemetry.value.copy(
                    heartRateBpm = SensorFrameValidation.heartRate(
                        SmartSuitBleParser.parseHeartRateMeasurement(payload),
                    ),
                )
            }
            SmartSuitBleContract.PLX_CONTINUOUS_MEASUREMENT -> {
                _telemetry.value.copy(
                    spo2Percent = SensorFrameValidation.spo2(
                        SmartSuitBleParser.parsePlxContinuousMeasurement(payload),
                    ),
                )
            }
            SmartSuitBleContract.ECG_RAW -> {
                _telemetry.value.copy(
                    ecgSamples = SensorFrameValidation.ecgSamples(
                        SmartSuitBleParser.parseFloat32Array(payload, expectedCount = 256),
                    ),
                )
            }
            SmartSuitBleContract.IMU_WRIST -> {
                _telemetry.value.copy(
                    wristImu = SensorFrameValidation.imuSamples(
                        SmartSuitBleParser.parseFloat32Array(payload, expectedCount = 6),
                    ),
                )
            }
            SmartSuitBleContract.SOS_STATE -> {
                _telemetry.value.copy(
                    sosState = (SmartSuitBleParser.parseUint8(payload) ?: 0) != 0,
                )
            }
            SmartSuitBleContract.FALL_RISK -> {
                _telemetry.value.copy(
                    fallRisk = SensorFrameValidation.fallRisk(
                        SmartSuitBleParser.parseFloat32(payload),
                    ),
                )
            }
            SmartSuitBleContract.HUMIDITY -> {
                val raw = SmartSuitBleParser.parseFloat32Array(payload, expectedCount = 2)
                _telemetry.value.copy(
                    humidityPercent = SensorFrameValidation.humidityPercent(raw.getOrNull(0)),
                    humidityTempC = SensorFrameValidation.temperatureC(raw.getOrNull(1)),
                )
            }
            SmartSuitBleContract.RESP_RATE -> {
                _telemetry.value.copy(
                    respiratoryRate = SensorFrameValidation.respiratoryRate(
                        SmartSuitBleParser.parseFloat32(payload),
                    ),
                )
            }
            SmartSuitBleContract.DEVICE_STATE -> {
                _telemetry.value.copy(
                    deviceState = SensorFrameValidation.deviceState(
                        SmartSuitBleParser.parseUint8(payload),
                    ),
                )
            }
            else -> _telemetry.value
        }
    }
}

private fun BluetoothGatt.findCharacteristic(
    serviceUuid: UUID,
    characteristicUuid: UUID,
): BluetoothGattCharacteristic? {
    return getService(serviceUuid)?.getCharacteristic(characteristicUuid)
}
