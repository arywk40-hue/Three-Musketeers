package com.smartsuit.ml

import com.smartsuit.data.RiskStatus

object DehydrationRiskModel {
    private const val HIGH_SWEAT_RATE = 1.5f
    private const val MED_SWEAT_RATE = 0.8f
    private const val HIGH_TEMP = 37.5f
    private const val MED_TEMP = 36.8f
    private const val HIGH_HR = 100
    private const val MED_HR = 90

    data class DehydrationAssessment(
        val risk: RiskStatus,
        val score: Float,
    )

    fun assess(
        sweatRatePercentPerMin: Float,
        skinTempC: Float,
        heartRateBpm: Int,
    ): DehydrationAssessment {
        val sweatContribution = when {
            sweatRatePercentPerMin >= HIGH_SWEAT_RATE -> 0.5f
            sweatRatePercentPerMin >= MED_SWEAT_RATE -> 0.25f
            else -> 0f
        }
        val tempContribution = when {
            skinTempC >= HIGH_TEMP -> 0.3f
            skinTempC >= MED_TEMP -> 0.15f
            else -> 0f
        }
        val hrContribution = when {
            heartRateBpm >= HIGH_HR -> 0.2f
            heartRateBpm >= MED_HR -> 0.10f
            else -> 0f
        }
        val score = sweatContribution + tempContribution + hrContribution
        val risk = when {
            score >= 0.7f -> RiskStatus.High
            score >= 0.4f -> RiskStatus.Medium
            else -> RiskStatus.Low
        }
        return DehydrationAssessment(risk, score)
    }
}
