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
 *
 * Phase 5 update: Thresholds updated to match the new engine:
 *  - FALL_SPIKE_THRESHOLD: 19.6 m/s² (was 24.5)
 *  - FALL_STILLNESS_THRESHOLD: 4.0 m/s² (was 3.0)
 * The [expectedEngineStatus] helper mirrors the new engine's single-sample
 * scoring for use by [FallConfirmationBufferTest], which tests the buffer
 * isolation logic via pre-fabricated FallAssessment objects rather than
 * the real engine's temporal state.
 */
internal object FallImuPatterns {

    private const val G_MS2 = 9.81f

    // Phase 5 updated thresholds to match FallDetectionEngine constants
    private const val FALL_SPIKE_THRESHOLD = 19.6f
    private const val FALL_STILLNESS_THRESHOLD = 4.0f

    private fun magnitude(ax: Float, ay: Float, az: Float) =
        sqrt(ax * ax + ay * ay + az * az)

    private fun normalUpright(): FloatArray = floatArrayOf(0.05f, 0.04f, G_MS2, 0.5f, 0.4f, 0.3f)

    // Spike at ~22 m/s² — above the new FALL_SPIKE_THRESHOLD (19.6)
    private fun fallSpike(): FloatArray = floatArrayOf(22f, 2f, 3f, 90f, 12f, 5f)

    private fun postFallStillness(): FloatArray = floatArrayOf(0.5f, 0.5f, 0.5f, 0f, 0f, 0f)

    // Vigorous step at ~0.95 × 19.6 = ~18.6 m/s² — below FALL_SPIKE_THRESHOLD
    private fun vigorousStep(): FloatArray {
        val s = FALL_SPIKE_THRESHOLD * 0.95f
        return floatArrayOf(s, 1f, 4f, 70f, 10f, 4f)
    }

    // Brief jolt at ~23 m/s² — above threshold for one frame only
    private fun briefJolt(): FloatArray = floatArrayOf(23f, 2f, 8f, 60f, 10f, 5f)

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
     * Walking artifact: normal upright → vigorous step spike (below threshold)
     * → more vigorous steps → normal. Since the step magnitude is below
     * FALL_SPIKE_THRESHOLD, none of these frames should ever produce High.
     */
    fun walkingArtifactSequence(): List<List<Float>> = listOf(
        toList(normalUpright()),
        toList(vigorousStep()),
        toList(vigorousStep()),
        toList(vigorousStep()),
        toList(normalUpright()),
    )

    /**
     * Brief jolt: a single above-threshold magnitude spike (e.g., the
     * wearer sits down hard on a chair) followed by normal frames. The
     * jolt exceeds the FallDetectionEngine spike threshold for a single frame,
     * but the person then resumes normal movement — no stillness.
     * Buffer should report Low → Medium (lone spike) → Low → Low → Low.
     *
     * Note: this is the most likely real-world false-positive that the
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
     * Sanity helper: returns the [com.smartsuit.data.RiskStatus] a single
     * IMU frame would produce at face value (for driving the buffer with
     * known inputs). This does NOT replicate the temporal state of the engine
     * — it is a simplified one-shot signal categoriser used only in buffer
     * tests where the buffer logic itself is under test.
     *
     * Phase 5: Updated to use new thresholds (19.6 spike, 4.0 stillness).
     */
    fun expectedEngineStatus(imu: List<Float>): com.smartsuit.data.RiskStatus {
        if (imu.size < 3) return com.smartsuit.data.RiskStatus.Low
        val mag = magnitude(imu[0], imu[1], imu[2])
        return when {
            mag > FALL_SPIKE_THRESHOLD -> com.smartsuit.data.RiskStatus.High
            mag < FALL_STILLNESS_THRESHOLD -> com.smartsuit.data.RiskStatus.Medium
            else -> com.smartsuit.data.RiskStatus.Low
        }
    }
}
