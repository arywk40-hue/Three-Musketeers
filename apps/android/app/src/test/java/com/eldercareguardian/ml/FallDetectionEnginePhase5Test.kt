package com.eldercareguardian.ml

import com.eldercareguardian.data.RiskStatus
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FallDetectionEnginePhase5Test {
    private lateinit var engine: FallDetectionEngine

    @Before
    fun setUp() {
        engine = FallDetectionEngine()
    }

    private fun normalFrame(mag: Float = 9.81f): List<Float> =
        listOf(0f, 0f, mag, 0f, 0f, 0f)

    private fun spikeFrame(mag: Float = 22f): List<Float> =
        listOf(mag / 1.732f, mag / 1.732f, mag / 1.732f, 0f, 0f, 0f)

    private fun stillFrame(mag: Float = 1.5f): List<Float> =
        listOf(0f, 0f, mag, 0f, 0f, 0f)

    @Test
    fun `normal resting movement returns Low risk`() {
        repeat(10) {
            val result = engine.assess(normalFrame())
            assertEquals(RiskStatus.Low, result.riskStatus)
        }
    }

    @Test
    fun `empty IMU list returns Low risk`() {
        val result = engine.assess(emptyList())
        assertEquals(RiskStatus.Low, result.riskStatus)
    }

    @Test
    fun `only 2 IMU values returns Low risk`() {
        val result = engine.assess(listOf(5f, 5f))
        assertEquals(RiskStatus.Low, result.riskStatus)
    }

    @Test
    fun `single spike frame alone returns Medium risk not High`() {
        engine = FallDetectionEngine()
        val result = engine.assess(spikeFrame(22f))
        assert(result.riskStatus in listOf(RiskStatus.Low, RiskStatus.Medium))
    }

    @Test
    fun `consecutive spike frames with no stillness returns Medium`() {
        engine = FallDetectionEngine()
        var lastResult = engine.assess(spikeFrame(22f))
        lastResult = engine.assess(spikeFrame(22f))
        assertEquals(RiskStatus.Medium, lastResult.riskStatus)
    }

    @Test
    fun `spike followed by stillness returns High risk`() {
        engine = FallDetectionEngine()
        engine.assess(spikeFrame(22f))
        engine.assess(spikeFrame(22f))
        engine.assess(stillFrame(1.5f))
        engine.assess(stillFrame(1.5f))
        val result = engine.assess(stillFrame(1.5f))
        assertEquals(RiskStatus.High, result.riskStatus)
    }

    @Test
    fun `High risk resets engine for next event`() {
        engine = FallDetectionEngine()
        engine.assess(spikeFrame(22f))
        engine.assess(spikeFrame(22f))
        engine.assess(stillFrame(1.5f))
        engine.assess(stillFrame(1.5f))
        engine.assess(stillFrame(1.5f))
        val afterReset = engine.assess(normalFrame())
        assertEquals(RiskStatus.Low, afterReset.riskStatus)
    }

    @Test
    fun `sustained stillness without prior spike does not return High`() {
        engine = FallDetectionEngine()
        repeat(10) {
            val result = engine.assess(stillFrame(1.5f))
            assert(result.riskStatus != RiskStatus.High) {
                "Expected non-High risk on stillness alone but got High at iteration $it"
            }
        }
    }

    @Test
    fun `spike window expires after multiple normal frames`() {
        engine = FallDetectionEngine()
        engine.assess(spikeFrame(22f))
        engine.assess(spikeFrame(22f))
        repeat(10) { engine.assess(normalFrame()) }
        engine.assess(stillFrame(1.5f))
        engine.assess(stillFrame(1.5f))
        val result = engine.assess(stillFrame(1.5f))
        assert(result.riskStatus != RiskStatus.High) {
            "Expected non-High after window expiry but got ${result.riskStatus}"
        }
    }

    @Test
    fun `reset clears all state`() {
        engine.assess(spikeFrame(22f))
        engine.assess(spikeFrame(22f))
        engine.reset()
        val result = engine.assess(normalFrame())
        assertEquals(RiskStatus.Low, result.riskStatus)
    }

    @Test
    fun `acceleration magnitude is computed correctly`() {
        engine = FallDetectionEngine()
        val result = engine.assess(listOf(0f, 0f, 9.81f, 0f, 0f, 0f))
        assertEquals(9.81f, result.accelerationMagnitude, 0.01f)
    }
}
