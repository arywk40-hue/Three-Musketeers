package com.smartsuit.ml

import com.smartsuit.data.RiskStatus
import kotlin.math.sqrt

object FallDetectionEngine {
    private const val FALL_SPIKE_THRESHOLD = 24.5f
    private const val FALL_STILLNESS_THRESHOLD = 3.0f

    data class FallAssessment(val riskStatus: RiskStatus, val riskScore: Float, val accelerationMagnitude: Float)

    fun assess(imuWindow: List<Float>): FallAssessment {
        if (imuWindow.size < 6) return FallAssessment(RiskStatus.Low, 0f, 9.81f)
        val ax = imuWindow[0]; val ay = imuWindow[1]; val az = imuWindow[2]
        val mag = sqrt(ax * ax + ay * ay + az * az)
        val score = when {
            mag > FALL_SPIKE_THRESHOLD -> 0.9f
            mag < FALL_STILLNESS_THRESHOLD -> 0.6f
            mag > FALL_SPIKE_THRESHOLD * 0.7f -> 0.4f
            else -> 0.1f
        }
        val status = when {
            score >= 0.8f -> RiskStatus.High
            score >= 0.4f -> RiskStatus.Medium
            else -> RiskStatus.Low
        }
        return FallAssessment(status, score, mag)
    }
}
