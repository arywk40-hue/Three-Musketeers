package com.eldercareguardian.ml

import com.eldercareguardian.data.FatigueStatus
import com.eldercareguardian.data.RiskStatus
import com.eldercareguardian.data.SensorFrame

/**
 * Unified health risk assessment pipeline.
 * Single source of truth for all ML rule-engine evaluations.
 * Eliminates duplicate calls from Simulator, Merger, and TFLite fallback.
 */
object HealthRiskPipeline {

    data class AssessmentResult(
        val ecg: EcgAnomalyDetector.EcgAssessment,
        val vitals: VitalsRiskMonitor.VitalsRiskAssessment,
        val dehydration: DehydrationRiskModel.DehydrationAssessment,
        val overexertion: OverexertionModel.OverexertionAssessment,
        val bloodPressure: BloodPressureEstimator.BloodPressureEstimate,
    )

    /**
     * Runs the complete health risk assessment pipeline.
     * Call once per frame from either Simulator or SensorFrameMerger.
     */
    fun assess(
        ecgSamples: List<Float>,
        heartRateBpm: Int,
        spo2Percent: Float,
        respiratoryRate: Int,
        skinTempC: Float,
        sweatRatePercentPerMin: Float,
        imuMagnitude: Float,
        patientAgeYears: Int = 70,
    ): AssessmentResult {
        val ecg = EcgAnomalyDetector.assess(ecgSamples, heartRateBpm)
        val vitals = VitalsRiskMonitor.assess(heartRateBpm, spo2Percent, respiratoryRate, skinTempC)
        val dehydration = DehydrationRiskModel.assess(sweatRatePercentPerMin, skinTempC, heartRateBpm)
        val overexertion = OverexertionModel.assess(
            heartRateBpm = heartRateBpm,
            spo2Percent = spo2Percent,
            respiratoryRate = respiratoryRate,
            imuMagnitude = imuMagnitude,
            ageYears = patientAgeYears,
        )
        val bloodPressure = BloodPressureEstimator.estimate(heartRateBpm, skinTempC)
        return AssessmentResult(ecg, vitals, dehydration, overexertion, bloodPressure)
    }

    /**
     * Minimal assessment for TFLite fallback - only returns RiskStatus enums
     * to match HealthRiskTfliteModel.HealthRiskResult structure.
     */
    fun assessRiskOnly(
        frame: SensorFrame,
        patientAgeYears: Int = 70,
    ): Triple<RiskStatus, RiskStatus, FatigueStatus> {
        val vitals = VitalsRiskMonitor.assess(
            frame.heartRateBpm, frame.spo2Percent, frame.respiratoryRate, frame.skinTempC
        )
        val dehydration = DehydrationRiskModel.assess(
            frame.sweatRatePercentPerMin, frame.skinTempC, frame.heartRateBpm
        )
        val overexertion = OverexertionModel.assess(
            frame.heartRateBpm, frame.spo2Percent, frame.respiratoryRate,
            frame.imuMagnitude, patientAgeYears
        )
        return Triple(vitals.risk, dehydration.risk, overexertion.status)
    }
}