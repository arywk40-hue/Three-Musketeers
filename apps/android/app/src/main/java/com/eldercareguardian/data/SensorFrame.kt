package com.eldercareguardian.data

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
    val spo2Quality: Spo2Quality = Spo2Quality.Reliable,
    val sweatRatePercentPerMin: Float = 0f,
    val hrReservePercent: Int = 0,
    val bpEstimated: Boolean = true,
    val batteryPercent: Int? = null,
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

/**
 * Four-level caregiver alert hierarchy (Phase 7).
 *
 *  Level 1 — Normal:    No action required. All vitals within acceptable range.
 *  Level 2 — Check:     Soft concern. Caregiver should check in within the hour.
 *                       Examples: mild tachycardia, 10-min inactivity, low battery.
 *  Level 3 — Warning:   Elevated concern. Caregiver should respond within minutes.
 *                       Examples: sustained tachycardia, SpO2 90–94%, prolonged inactivity,
 *                       confirmed medium fall risk.
 *  Level 4 — Emergency: Immediate response required.
 *                       Examples: SOS pressed, confirmed fall, SpO2 < 90%, HR > 130 / < 40.
 *
 * The old two-level (Normal/Check) and three-level (Normal/Check/Urgent) systems
 * collapsed Warning into either Check or Urgent, losing actionable granularity.
 */
enum class CaregiverAlertStatus {
    Normal,
    Check,
    Warning,
    Emergency,
}

enum class Spo2Quality {
    Reliable,
    Unreliable,
    NoSignal,
}

enum class EcgAnomalyStatus(val displayLabel: String) {
    Unknown("Unknown"),
    Normal("Normal"),
    AFib("Irregular rhythm"),
    Tachycardia("Elevated heart rate"),
    Bradycardia("Low heart rate"),
}
