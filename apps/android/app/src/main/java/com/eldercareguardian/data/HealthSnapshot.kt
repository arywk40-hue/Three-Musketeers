package com.eldercareguardian.data

data class HealthSnapshot(
    val id: Long = 0,
    val patientId: Long = 0,
    val timestampMillis: Long = System.currentTimeMillis(),
    val heartRateBpm: Int,
    val spo2Percent: Float,
    val respiratoryRate: Int,
    val skinTempC: Float,
    val posture: String,
    val fatigue: String,
    val fallRisk: String,
    val caregiverAlert: String,
    val batteryPercent: Int?,
)
