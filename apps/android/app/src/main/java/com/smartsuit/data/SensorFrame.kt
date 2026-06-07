package com.smartsuit.data

data class SensorFrame(
    val timestampMillis: Long,
    val heartRateBpm: Int,
    val spo2Percent: Float,
    val systolicMmHg: Int,
    val diastolicMmHg: Int,
    val skinTempC: Float,
    val humidityPercent: Float,
    val respiratoryRate: Int,
    val posture: PostureStatus,
    val fatigue: FatigueStatus,
    val dehydration: RiskStatus,
    val fallRisk: RiskStatus,
    val caregiverAlert: CaregiverAlertStatus,
    val sosActive: Boolean,
    val inactivityMinutes: Int,
    val supercapPercent: Int,
    val ecgSamples: List<Float>,
    val ecgAnomaly: EcgAnomalyStatus = EcgAnomalyStatus.Unknown,
    val vitalsRisk: RiskStatus = RiskStatus.Low,
    val rrIntervalsMs: List<Int> = emptyList(),
    val imuMagnitude: Float = 9.81f,
    val sweatRatePercentPerMin: Float = 0f,
    val hrReservePercent: Int = 0,
    val bpEstimated: Boolean = true,
)

enum class PostureStatus {
    Good,
    Warning,
    Bad,
}

enum class FatigueStatus {
    Safe,
    Caution,
    Stop,
}

enum class RiskStatus {
    Low,
    Medium,
    High,
}

enum class CaregiverAlertStatus {
    Normal,
    Check,
    Urgent,
}

enum class EcgAnomalyStatus {
    Unknown,
    Normal,
    AFib,
    Tachycardia,
    Bradycardia,
}
