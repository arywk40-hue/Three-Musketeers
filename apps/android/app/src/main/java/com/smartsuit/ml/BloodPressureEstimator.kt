package com.smartsuit.ml

object BloodPressureEstimator {
    private const val HR_BASELINE = 70
    private const val TEMP_BASELINE_C = 33.0f
    private const val HR_GAIN_SBP = 0.6f
    private const val HR_GAIN_DBP = 0.3f
    private const val TEMP_GAIN_SBP = 4.0f
    private const val TEMP_GAIN_DBP = 2.0f
    private const val SBP_BASE = 115
    private const val DBP_BASE = 75
    private const val SBP_MIN = 85
    private const val SBP_MAX = 180
    private const val DBP_MIN = 50
    private const val DBP_MAX = 110

    data class BloodPressureEstimate(
        val systolicMmHg: Int,
        val diastolicMmHg: Int,
        val isEstimated: Boolean = true,
    )

    fun estimate(heartRateBpm: Int, skinTempC: Float): BloodPressureEstimate {
        val hrDeviation = (heartRateBpm - HR_BASELINE).toFloat()
        val tempDeviation = skinTempC - TEMP_BASELINE_C
        val sbp = (SBP_BASE + HR_GAIN_SBP * hrDeviation + TEMP_GAIN_SBP * tempDeviation)
            .toInt()
            .coerceIn(SBP_MIN, SBP_MAX)
        val dbp = (DBP_BASE + HR_GAIN_DBP * hrDeviation + TEMP_GAIN_DBP * tempDeviation)
            .toInt()
            .coerceIn(DBP_MIN, DBP_MAX)
        return BloodPressureEstimate(sbp, dbp, isEstimated = true)
    }
}
