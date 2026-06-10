package com.eldercareguardian.ml

import com.eldercareguardian.data.RiskStatus
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Regression tests for FallDetectionEngine.
 *
 * Phase 5 rewrite uses a temporal window — single frames no longer map
 * 1:1 to risk levels the way the old single-threshold implementation did.
 * These tests reflect the new temporal engine's contracts:
 *
 *  - Normal gravity movement → Low
 *  - Insufficient IMU data → Low
 *  - Two consecutive high-G spikes → Medium (spike confirmed, stillness pending)
 *  - Spike + stillness sequence → High
 *  - accelerationMagnitude is always computed correctly from the IMU vector
 *
 * For comprehensive temporal-window tests, see FallDetectionEnginePhase5Test.
 */
class FallDetectionEngineTest {

    @Before
    fun reset() {
        FallDetectionEngine.reset()
    }

    @Test
    fun `returns Low risk on normal upright posture`() {
        // Az ~ 9.81 m/s² upright. Magnitude ~ 9.81. No spike, no stillness.
        val normal = listOf(0.1f, 0.1f, 9.81f, 0f, 0f, 0f)
        val result = FallDetectionEngine.assess(normal)
        assertEquals(RiskStatus.Low, result.riskStatus)
    }

    @Test
    fun `returns Low with zero score on insufficient samples`() {
        // Fewer than 3 values → returns Low immediately
        val result = FallDetectionEngine.assess(listOf(0f, 0f))
        assertEquals(RiskStatus.Low, result.riskStatus)
        assertEquals(0f, result.riskScore, 0f)
    }

    @Test
    fun `magnitude calculation is correct`() {
        val imu = listOf(3f, 4f, 0f, 0f, 0f, 0f) // sqrt(9+16) = 5
        val result = FallDetectionEngine.assess(imu)
        assertEquals(5f, result.accelerationMagnitude, 0.01f)
    }

    @Test
    fun `magnitude calculation is correct with gravity`() {
        val imu = listOf(0f, 0f, 9.81f, 0f, 0f, 0f)
        val result = FallDetectionEngine.assess(imu)
        assertEquals(9.81f, result.accelerationMagnitude, 0.01f)
    }

    @Test
    fun `consecutive high-G spike frames reach at least Medium risk`() {
        // Two frames at 22 m/s² should confirm the spike phase (SPIKE_MIN_SAMPLES = 2)
        // and return Medium (spike confirmed, stillness not yet observed).
        FallDetectionEngine.assess(listOf(22f, 0f, 0f, 0f, 0f, 0f))
        val result = FallDetectionEngine.assess(listOf(22f, 0f, 0f, 0f, 0f, 0f))
        assert(result.riskStatus >= RiskStatus.Medium) {
            "Expected at least Medium after 2 consecutive spike frames, got ${result.riskStatus}"
        }
    }

    @Test
    fun `spike followed by stillness reaches High risk`() {
        // Phase B: spike frames to trigger detection
        FallDetectionEngine.assess(listOf(22f, 0f, 0f, 0f, 0f, 0f))
        FallDetectionEngine.assess(listOf(22f, 0f, 0f, 0f, 0f, 0f))
        // Phase C: stillness frames (< 4.0 m/s²)
        FallDetectionEngine.assess(listOf(0f, 0f, 1.5f, 0f, 0f, 0f))
        FallDetectionEngine.assess(listOf(0f, 0f, 1.5f, 0f, 0f, 0f))
        val result = FallDetectionEngine.assess(listOf(0f, 0f, 1.5f, 0f, 0f, 0f))
        assertEquals(RiskStatus.High, result.riskStatus)
    }

    @Test
    fun `sustained normal movement never reaches High or Medium`() {
        // 20 frames of normal gravity — no spike, no stillness anomaly
        var lastResult = FallDetectionEngine.assess(listOf(0.1f, 0.1f, 9.81f, 0f, 0f, 0f))
        repeat(19) {
            lastResult = FallDetectionEngine.assess(listOf(0.1f, 0.1f, 9.81f, 0f, 0f, 0f))
        }
        assertEquals(RiskStatus.Low, lastResult.riskStatus)
    }
}
