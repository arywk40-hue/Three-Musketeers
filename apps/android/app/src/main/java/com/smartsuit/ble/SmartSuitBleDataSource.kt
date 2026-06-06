package com.smartsuit.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.smartsuit.data.SensorFrame
import com.smartsuit.data.SmartSuitDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

class SmartSuitBleDataSource(
    context: Context,
) : SmartSuitDataSource {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter = bluetoothManager?.adapter
    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private val devicesByAddress = mutableMapOf<String, android.bluetooth.BluetoothDevice>()

    private val _connectionState = MutableStateFlow(BleConnectionState.Idle)
    val connectionState: StateFlow<BleConnectionState> = _connectionState

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = _discoveredDevices

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
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = BleConnectionState.Disconnected
                    gatt.close()
                }
            }
        }
    }
}
