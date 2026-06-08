package com.smartsuit.ml

import com.smartsuit.data.CaregiverAlertStatus
import com.smartsuit.data.EcgAnomalyStatus
import com.smartsuit.data.FatigueStatus
import com.smartsuit.data.RiskStatus
import com.smartsuit.data.SensorFrame

/**
 * Maps a SensorFrame to a caregiver alert level.
 *
 * Priority order:
 *  1. SOS active → Urgent (always wins)
 *  2. Hard physiological limits (HR, SpO2, fall spike) → Urgent
 *  3. ML engines that already classified a serious condition (AFib, High vitals risk) → Urgent
 *  4. Mid-tier risk (fall risk Medium, dehydration High, fatigue Stop, prolonged inactivity,
 *     HR in caution band) → Check
 *  5. Otherwise → Normal
 */
object CaregiverAlertPolicy {
    fun evaluate(frame: SensorFrame): CaregiverAlertStatus {
        if (isUrgent(frame)) return CaregiverAlertStatus.Urgent
        if (isCheck(frame)) return CaregiverAlertStatus.Check
        return CaregiverAlertStatus.Normal
    }

    private fun isUrgent(frame: SensorFrame): Boolean {
        if (frame.sosActive) return true
        if (frame.heartRateBpm > 130 || frame.heartRateBpm < 40) return true
        if (frame.spo2Percent < 90f) return true
        if (frame.fallRisk == RiskStatus.High) return true
        if (frame.ecgAnomaly == EcgAnomalyStatus.AFib) return true
        if (frame.vitalsRisk == RiskStatus.High) return true
        return false
    }

    private fun isCheck(frame: SensorFrame): Boolean {
        if (frame.fallRisk == RiskStatus.Medium) return true
        if (frame.dehydration == RiskStatus.High) return true
        if (frame.fatigue == FatigueStatus.Stop) return true
        if (frame.inactivityMinutes > 20) return true
        if (frame.heartRateBpm > 110 || frame.heartRateBpm < 50) return true
        if (frame.spo2Percent in 90f..94f) return true
        if (frame.batteryPercent != null && frame.batteryPercent < 15) return true
        return false
    }
}
