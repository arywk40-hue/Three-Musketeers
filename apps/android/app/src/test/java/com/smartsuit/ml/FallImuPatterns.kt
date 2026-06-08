package com.smartsuit.ml

import kotlin.math.sqrt

/**
 * Test-only synthetic IMU pattern library. Produces 6-element wrist-IMU
 * frames (ax, ay, az in m/s², gx, gy, gz in °/s) for known fall and
 * near-fall scenarios. Used by [FallConfirmationBufferTest] to verify the
 * spike + stillness confirmation pattern in deterministic conditions
 * before Ariyan's controlled-drop dataset is available.
 *
 * Each pattern returns a sequence of [FallDetectionEngine.assess]-shaped
 * frames at the cadence the firmware produces (≈900 ms apart), so tests
 * can push them through the [FallConfirmationBuffer] one-by-one and
 * assert the sequence of confirmed [com.smartsuit.data.RiskStatus]
 * values.
 */
internal object FallImuPatterns {

    private const val G_MS2 = 9.81f
    private const val FALL_SPIKE_THRESHOLD = 24.5f
    private const val FALL_STILLNESS_THRESHOLD = 3.0f

    private fun magnitude(ax: Float, ay: Float, az: Float) =
        sqrt(ax * ax + ay * ay + az * az)

    private fun normalUpright(): FloatArray = floatArrayOf(0.05f, 0.04f, G_MS2, 0.5f, 0.4f, 0.3f)

    private fun fallSpike(): FloatArray = floatArrayOf(23f, 2f, 10f, 90f, 12f, 5f)

    private fun postFallStillness(): FloatArray = floatArrayOf(0.5f, 0.5f, 0.5f, 0f, 0f, 0f)

    private fun vigorousStep(): FloatArray {
        val s = FALL_SPIKE_THRESHOLD * 0.95f
        return floatArrayOf(s, 1f, 4f, 70f, 10f, 4f)
    }

    private fun briefJolt(): FloatArray = floatArrayOf(25f, 2f, 8f, 60f, 10f, 5f)

    private fun toList(arr: FloatArray): List<Float> = arr.toList()

    /**
     * Confirmed fall: normal upright → impact spike → 3 frames of stillness.
     * Buffer should report Low → Medium (spike alone) → High (spike +
     * stillness present) → High → High.
     */
    fun confirmedFallSequence(): List<List<Float>> = listOf(
        toList(normalUpright()),
        toList(fallSpike()),
        toList(postFallStillness()),
        toList(postFallStillness()),
        toList(postFallStillness()),
    )

    /**
     * Walking artifact: normal upright → vigorous step spike → another
     * spike → another spike → normal. No stillness phase, so the
     * buffer must NOT escalate to High — each spike is independently
     * downgraded to Medium because the next frame is another spike,
     * not a stillness.
     */
    fun walkingArtifactSequence(): List<List<Float>> = listOf(
        toList(normalUpright()),
        toList(vigorousStep()),
        toList(vigorousStep()),
        toList(vigorousStep()),
        toList(normalUpright()),
    )

    /**
     * Brief jolt: a single sub-spike-threshold mag-22 jolt (e.g., the
     * wearer sits down hard on a chair) followed by normal frames. The
     * jolt exceeds the FallDetectionEngine spike threshold because the
     * accelerometer picks up the impact for a single frame, but the
     * person then sits normally — no stillness. Buffer should report
     * Low → Medium → Low (jolt ages out without any Medium in the
     * confirmation window) → Low → Low.
     *
     * Note: this is the most likely real-world false-positive in the
     * single-frame FallDetectionEngine — exactly the scenario the
     * [FallConfirmationBuffer] is designed to filter out.
     */
    fun briefJoltSequence(): List<List<Float>> = listOf(
        toList(normalUpright()),
        toList(briefJolt()),
        toList(normalUpright()),
        toList(normalUpright()),
        toList(normalUpright()),
    )

    /**
     * Sanity helper: returns the [com.smartsuit.data.RiskStatus] the
     * single-frame [FallDetectionEngine] would emit for a given IMU
     * frame. Mirrors the engine's threshold table.
     */
    fun expectedEngineStatus(imu: List<Float>): com.smartsuit.data.RiskStatus {
        if (imu.size < 6) return com.smartsuit.data.RiskStatus.Low
        val mag = magnitude(imu[0], imu[1], imu[2])
        return when {
            mag > FALL_SPIKE_THRESHOLD -> com.smartsuit.data.RiskStatus.High
            mag < FALL_STILLNESS_THRESHOLD -> com.smartsuit.data.RiskStatus.Medium
            mag > FALL_SPIKE_THRESHOLD * 0.7f -> com.smartsuit.data.RiskStatus.Medium
            else -> com.smartsuit.data.RiskStatus.Low
        }
    }
}
