package com.smartsuit.ml

import com.smartsuit.data.CaregiverAlertStatus
import com.smartsuit.data.RiskStatus
import com.smartsuit.data.SensorFrame

object CaregiverAlertPolicy {
    fun evaluate(frame: SensorFrame): CaregiverAlertStatus {
        if (frame.sosActive) return CaregiverAlertStatus.Urgent
        if (frame.heartRateBpm > 130 || frame.heartRateBpm < 40) return CaregiverAlertStatus.Urgent
        if (frame.spo2Percent < 90f) return CaregiverAlertStatus.Urgent
        if (frame.fallRisk == RiskStatus.High) return CaregiverAlertStatus.Urgent
        if (frame.fallRisk == RiskStatus.Medium) return CaregiverAlertStatus.Check
        if (frame.inactivityMinutes > 20) return CaregiverAlertStatus.Check
        if (frame.heartRateBpm > 110 || frame.heartRateBpm < 50) return CaregiverAlertStatus.Check
        return CaregiverAlertStatus.Normal
    }
}
