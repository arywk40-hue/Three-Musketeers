package com.smartsuit.ble

data class SmartSuitBleTelemetry(
    val heartRateBpm: Int? = null,
    val batteryPercent: Int? = null,
    val ecgSamples: List<Float> = emptyList(),
    val leftElbowImu: List<Float> = emptyList(),
    val rightElbowImu: List<Float> = emptyList(),
    val lumbarImu: List<Float> = emptyList(),
    val humidityPercent: Float? = null,
    val humidityTempC: Float? = null,
    val respiratoryRate: Float? = null,
    val powerMw: Float? = null,
)
