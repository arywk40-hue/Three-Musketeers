package com.eldercareguardian.ml

import com.eldercareguardian.data.FatigueStatus
import kotlin.math.abs

object OverexertionModel {
    private const val HR_REST_BPM = 70
    private const val HR_MAX_BPM = 180
    private const val SPO2_DROP_BASELINE = 97f
    private const val SPO2_DROP_THRESHOLD = 2.5f
    private const val RR_HIGH = 22
    private const val IMU_INTENSITY_HIGH = 6.0f
    private const val IMU_INTENSITY_MOD = 3.0f

    data class OverexertionAssessment(
        val status: FatigueStatus,
        val score: Float,
        val hrReservePercent: Int,
    )

    fun assess(
        heartRateBpm: Int,
        spo2Percent: Float,
        respiratoryRate: Int,
        imuMagnitude: Float,
    ): OverexertionAssessment {
        val hrReserveRange = (HR_MAX_BPM - HR_REST_BPM).coerceAtLeast(1)
        val hrReservePct = (((heartRateBpm - HR_REST_BPM).toFloat() / hrReserveRange) * 100f)
            .coerceIn(0f, 100f)
        val imuDeviation = abs(imuMagnitude - 9.81f)

        val hrScore = when {
            hrReservePct >= 75f -> 0.45f
            hrReservePct >= 50f -> 0.25f
            hrReservePct >= 25f -> 0.10f
            else -> 0f
        }
        val spo2Drop = SPO2_DROP_BASELINE - spo2Percent
        val spo2Score = when {
            spo2Drop >= SPO2_DROP_THRESHOLD + 1f -> 0.25f
            spo2Drop >= SPO2_DROP_THRESHOLD -> 0.12f
            else -> 0f
        }
        val rrScore = if (respiratoryRate >= RR_HIGH) 0.15f else 0f
        val imuScore = when {
            imuDeviation >= IMU_INTENSITY_HIGH -> 0.15f
            imuDeviation >= IMU_INTENSITY_MOD -> 0.08f
            else -> 0f
        }
        val total = hrScore + spo2Score + rrScore + imuScore

        val status = when {
            total >= 0.65f -> FatigueStatus.Stop
            total >= 0.30f -> FatigueStatus.Caution
            else -> FatigueStatus.Safe
        }
        return OverexertionAssessment(status, total, hrReservePct.toInt())
    }
}
