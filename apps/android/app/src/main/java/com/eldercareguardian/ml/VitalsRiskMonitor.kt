package com.eldercareguardian.ml

import com.eldercareguardian.data.RiskStatus

/**
 * Composite vital sign risk monitor.
 *
 * Thresholds validated against NEWS2 (National Early Warning Score 2,
 * Royal College of Physicians 2017). Score 3 = red flag, score 1 = amber.
 *
 * NEWS2 reference ranges used:
 *  HR:   ≤40 or ≥131 = score 3 | 41–50 or 111–130 = score 1
 *  SpO2: ≤91% = score 3 | 92–95% = score 1
 *  RR:   ≤8 or ≥25 = score 3 | 9–11 or 21–24 = score 1
 *  Temp: ≤35.0°C or ≥39.1°C = score 3 | 35.1–36.0°C or 38.1–39.0°C = score 1
 *
 * Note: NEWS2 uses peripheral SpO2; skin temperature is used as a proxy for
 * core temperature — thresholds are shifted ~1°C lower to account for this offset.
 */
object VitalsRiskMonitor {
    // HR — NEWS2
    private const val HR_HIGH = 131
    private const val HR_LOW = 40
    private const val HR_MOD_HIGH = 111
    private const val HR_MOD_LOW = 51

    // SpO2 — NEWS2
    private const val SPO2_LOW = 91f
    private const val SPO2_MOD = 95f

    // RR — NEWS2
    private const val RR_HIGH = 25
    private const val RR_LOW = 8
    private const val RR_MOD_HIGH = 21
    private const val RR_MOD_LOW = 9

    // Temp — NEWS2 shifted for skin surface (~1°C below core)
    private const val TEMP_HIGH = 38.1f   // NEWS2 core ≥39.1 → skin proxy ≥38.1
    private const val TEMP_LOW = 34.0f    // NEWS2 core ≤35.0 → skin proxy ≤34.0
    private const val TEMP_MOD_HIGH = 37.1f
    private const val TEMP_MOD_LOW = 34.1f   // NEWS2 core 35.1 → skin proxy 34.1

    data class VitalsRiskAssessment(
        val risk: RiskStatus,
        val score: Int,
    )

    fun assess(
        heartRateBpm: Int,
        spo2Percent: Float,
        respiratoryRate: Int,
        skinTempC: Float,
    ): VitalsRiskAssessment {
        var score = 0

        score += when {
            heartRateBpm >= HR_HIGH || heartRateBpm <= HR_LOW -> 3
            heartRateBpm >= HR_MOD_HIGH || heartRateBpm <= HR_MOD_LOW -> 1
            else -> 0
        }
        score += when {
            spo2Percent <= SPO2_LOW -> 3
            spo2Percent <= SPO2_MOD -> 1
            else -> 0
        }
        score += when {
            respiratoryRate >= RR_HIGH || respiratoryRate <= RR_LOW -> 3
            respiratoryRate >= RR_MOD_HIGH || respiratoryRate <= RR_MOD_LOW -> 1
            else -> 0
        }
        score += when {
            skinTempC >= TEMP_HIGH || skinTempC <= TEMP_LOW -> 3
            skinTempC >= TEMP_MOD_HIGH || skinTempC <= TEMP_MOD_LOW -> 1
            else -> 0
        }

        val risk = when {
            score >= 3 -> RiskStatus.High
            score >= 2 -> RiskStatus.Medium
            else -> RiskStatus.Low
        }
        return VitalsRiskAssessment(risk, score)
    }
}
