package com.eldercareguardian.ml

import com.eldercareguardian.data.RiskStatus
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FallDetectionEngineTest {
    private lateinit var engine: FallDetectionEngine

    @Before
    fun reset() {
        engine = FallDetectionEngine()
    }

    @Test
    fun `returns Low risk on normal upright posture`() {
        val normal = listOf(0.1f, 0.1f, 9.81f, 0f, 0f, 0f)
        val result = engine.assess(normal)
        assertEquals(RiskStatus.Low, result.riskStatus)
    }

    @Test
    fun `returns Low with zero score on insufficient samples`() {
        val result = engine.assess(listOf(0f, 0f))
        assertEquals(RiskStatus.Low, result.riskStatus)
        assertEquals(0f, result.riskScore, 0f)
    }

    @Test
    fun `magnitude calculation is correct`() {
        val imu = listOf(3f, 4f, 0f, 0f, 0f, 0f)
        val result = engine.assess(imu)
        assertEquals(5f, result.accelerationMagnitude, 0.01f)
    }

    @Test
    fun `magnitude calculation is correct with gravity`() {
        val imu = listOf(0f, 0f, 9.81f, 0f, 0f, 0f)
        val result = engine.assess(imu)
        assertEquals(9.81f, result.accelerationMagnitude, 0.01f)
    }

    @Test
    fun `consecutive high-G spike frames reach at least Medium risk`() {
        engine.assess(listOf(22f, 0f, 0f, 0f, 0f, 0f))
        val result = engine.assess(listOf(22f, 0f, 0f, 0f, 0f, 0f))
        assert(result.riskStatus >= RiskStatus.Medium) {
            "Expected at least Medium after 2 consecutive spike frames, got ${result.riskStatus}"
        }
    }

    @Test
    fun `spike followed by stillness reaches High risk`() {
        engine.assess(listOf(22f, 0f, 0f, 0f, 0f, 0f))
        engine.assess(listOf(22f, 0f, 0f, 0f, 0f, 0f))
        engine.assess(listOf(0f, 0f, 1.5f, 0f, 0f, 0f))
        engine.assess(listOf(0f, 0f, 1.5f, 0f, 0f, 0f))
        val result = engine.assess(listOf(0f, 0f, 1.5f, 0f, 0f, 0f))
        assertEquals(RiskStatus.High, result.riskStatus)
    }

    @Test
    fun `sustained normal movement never reaches High or Medium`() {
        var lastResult = engine.assess(listOf(0.1f, 0.1f, 9.81f, 0f, 0f, 0f))
        repeat(19) {
            lastResult = engine.assess(listOf(0.1f, 0.1f, 9.81f, 0f, 0f, 0f))
        }
        assertEquals(RiskStatus.Low, lastResult.riskStatus)
    }
}
