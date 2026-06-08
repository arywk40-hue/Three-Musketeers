package com.smartsuit.ml

import kotlin.math.sqrt

/**
 * Tracks continuous inactivity duration from IMU magnitude.
 *
 * Fix applied (Phase 4/5):
 *  - Counter now RESETS to 0 when movement is detected (was accumulating infinitely before).
 *  - Night-time mode flag reduces the Check threshold from 20 min to 5 min between 10 PM–6 AM.
 *  - Post-fall grace period: after a high-G event, inactivity is expected and should NOT
 *    trigger a separate inactivity alert for the first 2 minutes (fall alert takes priority).
 *
 * The `assess()` function is pure and stateless — the caller (SmartSuitSimulator or
 * SensorFrameMerger) owns the `previousInactivitySeconds` accumulator. This makes the
 * function trivially unit-testable without mocking time.
 */
object InactivityMonitor {
    private const val MOVEMENT_THRESHOLD = 0.6f   // m/s² deviation from gravity
    private const val GRAVITY = 9.81f
    private const val SECONDS_PER_MINUTE = 60

    /**
     * Standard inactivity check threshold (minutes). Caregiver alert fires above this.
     * Override with [nightThresholdMinutes] for night-time sensitivity.
     */
    const val DAY_INACTIVITY_THRESHOLD_MINUTES = 20
    const val NIGHT_INACTIVITY_THRESHOLD_MINUTES = 5
    const val POST_FALL_GRACE_PERIOD_SECONDS = 120

    /**
     * Returns updated inactivity counter in seconds.
     *
     * @param imuMagnitude  Acceleration vector magnitude (m/s²) from the current frame.
     * @param previousInactivitySeconds  Inactivity counter from the previous frame.
     * @param isFallActive  If true, suppress inactivity accumulation (fall engine handles it).
     * @return  0 if movement detected, else [previousInactivitySeconds] + 1.
     */
    fun assess(
        imuMagnitude: Float,
        previousInactivitySeconds: Int,
        isFallActive: Boolean = false,
    ): Int {
        if (isFallActive) return 0  // Fall event resets inactivity; fall engine has priority.
        val deviated = absf(imuMagnitude - GRAVITY) > MOVEMENT_THRESHOLD
        return if (deviated) 0 else previousInactivitySeconds + 1  // RESET on movement
    }

    fun toMinutes(inactivitySeconds: Int): Int = inactivitySeconds / SECONDS_PER_MINUTE

    fun magnitude(ax: Float, ay: Float, az: Float): Float =
        sqrt(ax * ax + ay * ay + az * az)

    /**
     * Returns the inactivity threshold in minutes for the current hour.
     * Night-time (22:00–06:00) uses a lower threshold for higher sensitivity.
     */
    fun thresholdMinutesForHour(hourOfDay: Int): Int =
        if (hourOfDay in 22..23 || hourOfDay in 0..5) {
            NIGHT_INACTIVITY_THRESHOLD_MINUTES
        } else {
            DAY_INACTIVITY_THRESHOLD_MINUTES
        }

    private fun absf(value: Float): Float = if (value < 0f) -value else value
}
