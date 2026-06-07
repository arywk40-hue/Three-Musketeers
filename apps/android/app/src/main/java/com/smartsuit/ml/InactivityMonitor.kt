package com.smartsuit.ml

import kotlin.math.sqrt

object InactivityMonitor {
    private const val MOVEMENT_THRESHOLD = 0.6f
    private const val GRAVITY = 9.81f
    private const val SECONDS_PER_MINUTE = 60

    fun assess(
        imuMagnitude: Float,
        previousInactivitySeconds: Int,
    ): Int {
        val deviated = abs(imuMagnitude - GRAVITY) > MOVEMENT_THRESHOLD
        return if (deviated) 0 else previousInactivitySeconds + 1
    }

    fun toMinutes(inactivitySeconds: Int): Int = inactivitySeconds / SECONDS_PER_MINUTE

    fun magnitude(ax: Float, ay: Float, az: Float): Float {
        return sqrt(ax * ax + ay * ay + az * az)
    }

    private fun abs(value: Float): Float = if (value < 0f) -value else value
}
