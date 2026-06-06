package com.smartsuit.ble

data class DiscoveredBleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
)
