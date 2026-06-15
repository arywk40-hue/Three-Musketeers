package com.eldercareguardian.ml

import com.eldercareguardian.data.CaregiverAlertStatus
import com.eldercareguardian.data.EcgAnomalyStatus
import com.eldercareguardian.data.FatigueStatus
import com.eldercareguardian.data.RiskStatus
import com.eldercareguardian.data.SensorFrame
import java.util.Calendar

/**
 * Four-level caregiver alert triage engine (Phase 7).
 *
 * SpO2 debounce (Session 15): requires 3 consecutive low SpO2 readings
 * before triggering an alert. The counter is reset on any normal reading.
 *
 * Priority order (highest wins):
 *  Level 4 — Emergency:
 *   - SOS button pressed by patient
 *   - Confirmed fall (High fall risk)
 *   - SpO2 < 90% (severe hypoxia)
 *   - HR > 130 bpm (dangerous tachycardia)
 *   - HR < 40 bpm (dangerous bradycardia)
 *   - AFib confirmed
 *
 *  Level 3 — Warning:
 *   - SpO2 90–94% (moderate hypoxia — worrying but not yet critical)
 *   - HR 110–130 bpm (sustained tachycardia)
 *   - HR 40–50 bpm (mild bradycardia)
 *   - High vitals risk composite
 *   - Medium fall risk persisting (device fell or energetic movement)
 *   - Inactivity > night threshold (5 min at night, uses current hour)
 *   - Fatigue Stop class
 *
 *  Level 2 — Check:
 *   - HR mildly elevated (100–110 bpm)
 *   - HR mildly low (50–60 bpm)
 *   - SpO2 94–96% (trend toward low)
 *   - Inactivity > day threshold (20 min during day)
 *   - High dehydration risk
 *   - Low battery (< 15%)
 *   - ECG Tachycardia / Bradycardia classification (rate-based, less severe)
 *
 *  Level 1 — Normal:
 *   - Everything else
 *
 * Escalation rule: Once a frame arrives that drops the level, do NOT
 * immediately de-escalate. De-escalation is handled by [AlertHistoryTracker]
 * which requires N consecutive normal frames before clearing an alert.
 * This engine only computes the level for the current frame.
 */
object CaregiverAlertPolicy {
    private const val SPO2_DEBOUNCE_THRESHOLD = 3
    private var consecutiveLowSpo2 = 0

    @Synchronized
    fun evaluate(frame: SensorFrame): CaregiverAlertStatus {
        // SpO2 debounce: track consecutive low readings
        if (frame.spo2Percent < 96f) {
            consecutiveLowSpo2++
        } else {
            consecutiveLowSpo2 = 0
        }
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            isEmergency(frame) -> CaregiverAlertStatus.Emergency
            isWarning(frame, currentHour) -> CaregiverAlertStatus.Warning
            isCheck(frame, currentHour) -> CaregiverAlertStatus.Check
            else -> CaregiverAlertStatus.Normal
        }
    }

    @Synchronized
    fun reset() {
        consecutiveLowSpo2 = 0
    }

    // ── Level 4 — Emergency ──────────────────────────────────────────────────

    private fun isEmergency(frame: SensorFrame): Boolean {
        if (frame.sosActive) return true
        if (frame.fallRisk == RiskStatus.High) return true
        if (frame.spo2Percent < 90f && consecutiveLowSpo2 >= SPO2_DEBOUNCE_THRESHOLD) return true
        if (frame.heartRateBpm > 130) return true
        if (frame.heartRateBpm < 40) return true
        if (frame.ecgAnomaly == EcgAnomalyStatus.AFib) return true
        return false
    }

    // ── Level 3 — Warning ───────────────────────────────────────────────────

    private fun isWarning(frame: SensorFrame, currentHour: Int): Boolean {
        if (frame.spo2Percent in 90f..94f && consecutiveLowSpo2 >= SPO2_DEBOUNCE_THRESHOLD) return true
        if (frame.heartRateBpm > 110) return true
        if (frame.heartRateBpm < 50) return true
        if (frame.vitalsRisk == RiskStatus.High) return true
        if (frame.fallRisk == RiskStatus.Medium) return true
        if (frame.fatigue == FatigueStatus.Stop) return true
        // Night-time: 5-minute inactivity threshold
        val nightThreshold = InactivityMonitor.NIGHT_INACTIVITY_THRESHOLD_MINUTES
        val isDaytime = currentHour in 6..21
        if (!isDaytime && frame.inactivityMinutes >= nightThreshold) return true
        return false
    }

    // ── Level 2 — Check ─────────────────────────────────────────────────────

    private fun isCheck(frame: SensorFrame, currentHour: Int): Boolean {
        if (frame.heartRateBpm in 100..110) return true
        if (frame.heartRateBpm in 50..60) return true
        if (frame.spo2Percent in 94f..96f && consecutiveLowSpo2 >= SPO2_DEBOUNCE_THRESHOLD) return true
        // Day-time: 20-minute inactivity threshold
        val isDaytime = currentHour in 6..21
        if (isDaytime && frame.inactivityMinutes >= InactivityMonitor.DAY_INACTIVITY_THRESHOLD_MINUTES) return true
        if (frame.dehydration == RiskStatus.High) return true
        if (frame.batteryPercent != null && frame.batteryPercent < 15) return true
        if (frame.ecgAnomaly == EcgAnomalyStatus.Tachycardia) return true
        if (frame.ecgAnomaly == EcgAnomalyStatus.Bradycardia) return true
        if (frame.vitalsRisk == RiskStatus.Medium) return true
        return false
    }
}
