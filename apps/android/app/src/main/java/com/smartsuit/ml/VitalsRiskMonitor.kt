package com.smartsuit.ml

import com.smartsuit.data.RiskStatus

object VitalsRiskMonitor {
    private const val HR_HIGH = 130
    private const val HR_LOW = 40
    private const val HR_MOD_HIGH = 110
    private const val HR_MOD_LOW = 50

    private const val SPO2_LOW = 90f
    private const val SPO2_MOD = 94f

    private const val RR_HIGH = 24
    private const val RR_LOW = 8
    private const val RR_MOD_HIGH = 20
    private const val RR_MOD_LOW = 10

    private const val TEMP_HIGH = 38.5f
    private const val TEMP_LOW = 35.0f
    private const val TEMP_MOD_HIGH = 37.8f
    private const val TEMP_MOD_LOW = 35.5f

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
            spo2Percent < SPO2_LOW -> 3
            spo2Percent < SPO2_MOD -> 1
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
            score >= 4 -> RiskStatus.High
            score >= 2 -> RiskStatus.Medium
            else -> RiskStatus.Low
        }
        return VitalsRiskAssessment(risk, score)
    }
}
