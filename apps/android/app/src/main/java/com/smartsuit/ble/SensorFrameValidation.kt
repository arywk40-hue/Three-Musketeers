package com.smartsuit.ble

object SensorFrameValidation {
    const val HR_MIN = 30
    const val HR_MAX = 240
    const val SPO2_MIN = 50f
    const val SPO2_MAX = 100f
    const val BATT_MIN = 0
    const val BATT_MAX = 100
    const val RESP_MIN = 5f
    const val RESP_MAX = 60f
    const val HUMIDITY_MIN = 0f
    const val HUMIDITY_MAX = 100f
    const val TEMP_C_MIN = 30f
    const val TEMP_C_MAX = 45f
    const val FALL_RISK_MIN = 0f
    const val FALL_RISK_MAX = 1f
    const val DEVICE_STATE_MIN = 0
    const val DEVICE_STATE_MAX = 2
    const val ACCEL_MIN = -40f
    const val ACCEL_MAX = 40f
    const val GYRO_MIN = -500f
    const val GYRO_MAX = 500f
    const val ECG_MIN = -5f
    const val ECG_MAX = 5f
    const val BATTERY_PCT_MIN = 0
    const val BATTERY_PCT_MAX = 100

    fun heartRate(value: Int?): Int? {
        if (value == null) return null
        return value.coerceIn(HR_MIN, HR_MAX)
    }

    fun spo2(value: Float?): Float? {
        if (value == null) return null
        return value.coerceIn(SPO2_MIN, SPO2_MAX)
    }

    fun batteryPercent(value: Int?): Int? {
        if (value == null) return null
        return value.coerceIn(BATT_MIN, BATT_MAX)
    }

    fun respiratoryRate(value: Float?): Float? {
        if (value == null) return null
        return value.coerceIn(RESP_MIN, RESP_MAX)
    }

    fun humidityPercent(value: Float?): Float? {
        if (value == null) return null
        return value.coerceIn(HUMIDITY_MIN, HUMIDITY_MAX)
    }

    fun temperatureC(value: Float?): Float? {
        if (value == null) return null
        return value.coerceIn(TEMP_C_MIN, TEMP_C_MAX)
    }

    fun fallRisk(value: Float?): Float? {
        if (value == null) return null
        return value.coerceIn(FALL_RISK_MIN, FALL_RISK_MAX)
    }

    fun deviceState(value: Int?): Int? {
        if (value == null) return null
        return value.coerceIn(DEVICE_STATE_MIN, DEVICE_STATE_MAX)
    }

    fun ecgSamples(values: List<Float>): List<Float> {
        if (values.isEmpty()) return values
        return values.map { it.coerceIn(ECG_MIN, ECG_MAX) }
    }

    fun imuSamples(values: List<Float>): List<Float> {
        if (values.isEmpty()) return values
        return values.mapIndexed { i, v ->
            when (i) {
                in 0..2 -> v.coerceIn(ACCEL_MIN, ACCEL_MAX)
                in 3..5 -> v.coerceIn(GYRO_MIN, GYRO_MAX)
                else -> v
            }
        }
    }
}
