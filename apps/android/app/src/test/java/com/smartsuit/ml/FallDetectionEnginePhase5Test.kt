package com.smartsuit.ml

import com.smartsuit.data.RiskStatus
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the Phase 5 rewritten FallDetectionEngine.
 *
 * Tests verify the three-phase temporal window approach:
 *  Phase B — Impact spike detection
 *  Phase C — Post-impact stillness detection
 *  Spike + Stillness confirmation → High risk
 *
 * Coverage targets:
 *  - Single spike frame → Medium (not High, needs stillness confirmation)
 *  - Spike followed by stillness → High
 *  - Normal movement (gravity ≈ 9.81 m/s²) → Low
 *  - Complete stillness alone (no prior spike) → Low (inactivity, not fall)
 *  - Spike then recovery → resets after window expires
 *  - Reset() clears all state
 */
class FallDetectionEnginePhase5Test {

    @Before
    fun setUp() {
        FallDetectionEngine.reset()
    }

    // ── Helper factories ─────────────────────────────────────────────────────

    private fun normalFrame(mag: Float = 9.81f): List<Float> =
        listOf(0f, 0f, mag, 0f, 0f, 0f)

    private fun spikeFrame(mag: Float = 22f): List<Float> =
        listOf(mag / 1.732f, mag / 1.732f, mag / 1.732f, 0f, 0f, 0f)

    private fun stillFrame(mag: Float = 1.5f): List<Float> =
        listOf(0f, 0f, mag, 0f, 0f, 0f)

    // ── Normal movement tests ────────────────────────────────────────────────

    @Test
    fun `normal resting movement returns Low risk`() {
        repeat(10) {
            val result = FallDetectionEngine.assess(normalFrame())
            assertEquals(RiskStatus.Low, result.riskStatus)
        }
    }

    @Test
    fun `empty IMU list returns Low risk`() {
        val result = FallDetectionEngine.assess(emptyList())
        assertEquals(RiskStatus.Low, result.riskStatus)
    }

    @Test
    fun `only 2 IMU values returns Low risk`() {
        val result = FallDetectionEngine.assess(listOf(5f, 5f))
        assertEquals(RiskStatus.Low, result.riskStatus)
    }

    // ── Single spike tests ───────────────────────────────────────────────────

    @Test
    fun `single spike frame alone returns Medium risk not High`() {
        FallDetectionEngine.reset()
        // Need at least SPIKE_MIN_SAMPLES consecutive spike frames
        val result = FallDetectionEngine.assess(spikeFrame(22f))
        // First spike frame — not yet SPIKE_MIN_SAMPLES consecutive
        assert(result.riskStatus in listOf(RiskStatus.Low, RiskStatus.Medium))
    }

    @Test
    fun `consecutive spike frames with no stillness returns Medium`() {
        FallDetectionEngine.reset()
        var lastResult = FallDetectionEngine.assess(spikeFrame(22f))
        lastResult = FallDetectionEngine.assess(spikeFrame(22f))
        // Spike confirmed but no stillness yet → Medium
        assertEquals(RiskStatus.Medium, lastResult.riskStatus)
    }

    // ── Fall confirmation (spike + stillness) tests ──────────────────────────

    @Test
    fun `spike followed by stillness returns High risk`() {
        FallDetectionEngine.reset()
        // Feed spike frames to trigger spike detection
        FallDetectionEngine.assess(spikeFrame(22f))
        FallDetectionEngine.assess(spikeFrame(22f))
        // Feed stillness frames
        FallDetectionEngine.assess(stillFrame(1.5f))
        FallDetectionEngine.assess(stillFrame(1.5f))
        val result = FallDetectionEngine.assess(stillFrame(1.5f))
        assertEquals(RiskStatus.High, result.riskStatus)
    }

    @Test
    fun `High risk resets engine for next event`() {
        FallDetectionEngine.reset()
        // Trigger confirmed fall
        FallDetectionEngine.assess(spikeFrame(22f))
        FallDetectionEngine.assess(spikeFrame(22f))
        FallDetectionEngine.assess(stillFrame(1.5f))
        FallDetectionEngine.assess(stillFrame(1.5f))
        FallDetectionEngine.assess(stillFrame(1.5f))  // High risk fires and resets

        // Next normal frames should return Low
        val afterReset = FallDetectionEngine.assess(normalFrame())
        assertEquals(RiskStatus.Low, afterReset.riskStatus)
    }

    // ── Stillness-only (no spike) tests ─────────────────────────────────────

    @Test
    fun `sustained stillness without prior spike does not return High`() {
        FallDetectionEngine.reset()
        repeat(10) {
            val result = FallDetectionEngine.assess(stillFrame(1.5f))
            assert(result.riskStatus != RiskStatus.High) {
                "Expected non-High risk on stillness alone but got High at iteration $it"
            }
        }
    }

    // ── Spike window expiry ──────────────────────────────────────────────────

    @Test
    fun `spike window expires after multiple normal frames`() {
        FallDetectionEngine.reset()
        // Trigger spike detection
        FallDetectionEngine.assess(spikeFrame(22f))
        FallDetectionEngine.assess(spikeFrame(22f))
        // Feed normal frames to exhaust the window
        repeat(10) { FallDetectionEngine.assess(normalFrame()) }
        // Now feed stillness — should NOT confirm because window expired
        FallDetectionEngine.assess(stillFrame(1.5f))
        FallDetectionEngine.assess(stillFrame(1.5f))
        val result = FallDetectionEngine.assess(stillFrame(1.5f))
        // Should be Low (window expired) or at most Medium (new spike not detected)
        assert(result.riskStatus != RiskStatus.High) {
            "Expected non-High after window expiry but got ${result.riskStatus}"
        }
    }

    // ── Reset tests ──────────────────────────────────────────────────────────

    @Test
    fun `reset clears all state`() {
        FallDetectionEngine.assess(spikeFrame(22f))
        FallDetectionEngine.assess(spikeFrame(22f))
        FallDetectionEngine.reset()
        val result = FallDetectionEngine.assess(normalFrame())
        assertEquals(RiskStatus.Low, result.riskStatus)
    }

    // ── Acceleration magnitude tests ─────────────────────────────────────────

    @Test
    fun `acceleration magnitude is computed correctly`() {
        FallDetectionEngine.reset()
        val result = FallDetectionEngine.assess(listOf(0f, 0f, 9.81f, 0f, 0f, 0f))
        assertEquals(9.81f, result.accelerationMagnitude, 0.01f)
    }
}
