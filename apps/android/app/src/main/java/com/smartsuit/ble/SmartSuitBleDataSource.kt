package com.smartsuit.ble

import android.annotation.SuppressLint
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
import android.content.Context
import android.os.Build
import com.smartsuit.data.SensorFrame
import com.smartsuit.data.SmartSuitDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.util.UUID

class SmartSuitBleDataSource(
    context: Context,
) : SmartSuitDataSource {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter = bluetoothManager?.adapter
    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private var notificationQueue: List<BluetoothGattCharacteristic> = emptyList()
    private var notificationIndex = 0
    private val devicesByAddress = mutableMapOf<String, android.bluetooth.BluetoothDevice>()

    private val _connectionState = MutableStateFlow(BleConnectionState.Idle)
    val connectionState: StateFlow<BleConnectionState> = _connectionState

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = _discoveredDevices

    private val _telemetry = MutableStateFlow(SmartSuitBleTelemetry())
    val telemetry: StateFlow<SmartSuitBleTelemetry> = _telemetry

    override val frames: Flow<SensorFrame> = emptyFlow()

    @SuppressLint("MissingPermission")
    fun startScan() {
        try {
            val adapter = bluetoothAdapter
            if (adapter == null || !adapter.isEnabled) {
                _connectionState.value = BleConnectionState.Unsupported
                return
            }

            val scanner = adapter.bluetoothLeScanner
            if (scanner == null) {
                _connectionState.value = BleConnectionState.Unsupported
                return
            }

            stopScanOnly()
            _discoveredDevices.value = emptyList()
            _connectionState.value = BleConnectionState.Scanning

            val filter = ScanFilter.Builder()
                .setDeviceName(SmartSuitBleContract.DEVICE_NAME)
                .build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device ?: return
                    val name = result.scanRecord?.deviceName ?: device.name ?: SmartSuitBleContract.DEVICE_NAME
                    val discovered = DiscoveredBleDevice(
                        name = name,
                        address = device.address,
                        rssi = result.rssi,
                    )
                    devicesByAddress[device.address] = device
                    _discoveredDevices.value = (_discoveredDevices.value
                        .filterNot { it.address == discovered.address } + discovered)
                        .sortedByDescending { it.rssi }
                }

                override fun onScanFailed(errorCode: Int) {
                    _connectionState.value = BleConnectionState.Error
                }
            }

            scanner.startScan(listOf(filter), settings, scanCallback)
        } catch (_: SecurityException) {
            _connectionState.value = BleConnectionState.PermissionMissing
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        try {
            val device = devicesByAddress[address] ?: return
            stopScanOnly()
            _connectionState.value = BleConnectionState.Connecting
            gatt = device.connectGatt(appContext, false, gattCallback)
        } catch (_: SecurityException) {
            _connectionState.value = BleConnectionState.PermissionMissing
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try {
            stopScanOnly()
            gatt?.close()
            gatt = null
            _connectionState.value = BleConnectionState.Idle
        } catch (_: SecurityException) {
            _connectionState.value = BleConnectionState.PermissionMissing
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanOnly() {
        val callback = scanCallback ?: return
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback)
        scanCallback = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != GATT_SUCCESS) {
                _connectionState.value = BleConnectionState.Error
                gatt.close()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = BleConnectionState.Connected
                    gatt.requestMtu(517)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = BleConnectionState.Disconnected
                    gatt.close()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
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
                _connectionState.value = BleConnectionState.Error
                return
            }

            subscribeNext(gatt)
        }
    }

    private fun collectNotifyCharacteristics(gatt: BluetoothGatt): List<BluetoothGattCharacteristic> {
        return listOfNotNull(
            gatt.findCharacteristic(SmartSuitBleContract.BATTERY_SERVICE, SmartSuitBleContract.BATTERY_LEVEL),
            gatt.findCharacteristic(SmartSuitBleContract.HEART_RATE_SERVICE, SmartSuitBleContract.HEART_RATE_MEASUREMENT),
            gatt.findCharacteristic(SmartSuitBleContract.CUSTOM_SERVICE, SmartSuitBleContract.ECG_RAW),
            gatt.findCharacteristic(SmartSuitBleContract.CUSTOM_SERVICE, SmartSuitBleContract.IMU_ELBOW_LEFT),
            gatt.findCharacteristic(SmartSuitBleContract.CUSTOM_SERVICE, SmartSuitBleContract.IMU_ELBOW_RIGHT),
            gatt.findCharacteristic(SmartSuitBleContract.CUSTOM_SERVICE, SmartSuitBleContract.IMU_LUMBAR),
            gatt.findCharacteristic(SmartSuitBleContract.CUSTOM_SERVICE, SmartSuitBleContract.HUMIDITY),
            gatt.findCharacteristic(SmartSuitBleContract.CUSTOM_SERVICE, SmartSuitBleContract.RESP_RATE),
            gatt.findCharacteristic(SmartSuitBleContract.CUSTOM_SERVICE, SmartSuitBleContract.POWER_MW),
        ).filter { characteristic ->
            (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscribeNext(gatt: BluetoothGatt) {
        if (notificationIndex >= notificationQueue.size) return

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

    private fun handleNotification(uuid: UUID, payload: ByteArray) {
        _telemetry.value = when (uuid) {
            SmartSuitBleContract.BATTERY_LEVEL -> {
                _telemetry.value.copy(batteryPercent = SmartSuitBleParser.parseBatteryLevel(payload))
            }
            SmartSuitBleContract.HEART_RATE_MEASUREMENT -> {
                _telemetry.value.copy(heartRateBpm = SmartSuitBleParser.parseHeartRateMeasurement(payload))
            }
            SmartSuitBleContract.ECG_RAW -> {
                _telemetry.value.copy(ecgSamples = SmartSuitBleParser.parseFloat32Array(payload, expectedCount = 256))
            }
            SmartSuitBleContract.IMU_ELBOW_LEFT -> {
                _telemetry.value.copy(leftElbowImu = SmartSuitBleParser.parseFloat32Array(payload, expectedCount = 6))
            }
            SmartSuitBleContract.IMU_ELBOW_RIGHT -> {
                _telemetry.value.copy(rightElbowImu = SmartSuitBleParser.parseFloat32Array(payload, expectedCount = 6))
            }
            SmartSuitBleContract.IMU_LUMBAR -> {
                _telemetry.value.copy(lumbarImu = SmartSuitBleParser.parseFloat32Array(payload, expectedCount = 6))
            }
            SmartSuitBleContract.HUMIDITY -> {
                val values = SmartSuitBleParser.parseFloat32Array(payload, expectedCount = 2)
                _telemetry.value.copy(
                    humidityPercent = values.getOrNull(0),
                    humidityTempC = values.getOrNull(1),
                )
            }
            SmartSuitBleContract.RESP_RATE -> {
                _telemetry.value.copy(respiratoryRate = SmartSuitBleParser.parseFloat32(payload))
            }
            SmartSuitBleContract.POWER_MW -> {
                _telemetry.value.copy(powerMw = SmartSuitBleParser.parseFloat32(payload))
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
