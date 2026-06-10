package com.eldercareguardian.ble

data class SmartSuitBleTelemetry(
    val heartRateBpm: Int? = null,
    val batteryPercent: Int? = null,
    val ecgSamples: List<Float> = emptyList(),
    val wristImu: List<Float> = emptyList(),
    val sosState: Boolean = false,
    val fallRisk: Float? = null,
    val humidityPercent: Float? = null,
    val humidityTempC: Float? = null,
    val respiratoryRate: Float? = null,
    val spo2Percent: Float? = null,
    val deviceState: Int? = null,
)
