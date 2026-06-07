package com.smartsuit.ml

import com.smartsuit.data.RiskStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class FallDetectionEngineTest {

    @Test
    fun `flags High risk on a large acceleration spike`() {
        // Magnitude > 24.5 m/s² — a real fall spike.
        val spike = listOf(15f, 0f, 20f, 0f, 0f, 0f) // sqrt(225+400) = ~25
        val result = FallDetectionEngine.assess(spike)
        assertEquals(RiskStatus.High, result.riskStatus)
        assert(result.riskScore >= 0.8f)
    }

    @Test
    fun `flags Medium risk on near-zero stillness`() {
        // Magnitude < 3.0 m/s² — patient lying motionless after a fall.
        val still = listOf(0.5f, 0.5f, 0.5f, 0f, 0f, 0f) // sqrt(0.75) ~ 0.87
        val result = FallDetectionEngine.assess(still)
        assertEquals(RiskStatus.Medium, result.riskStatus)
    }

    @Test
    fun `flags Low risk on normal upright posture`() {
        // Az ~ 9.81 m/s² upright. Magnitude ~ 9.81.
        val normal = listOf(0.1f, 0.1f, 9.81f, 0f, 0f, 0f)
        val result = FallDetectionEngine.assess(normal)
        assertEquals(RiskStatus.Low, result.riskStatus)
    }

    @Test
    fun `returns Low with zero score on insufficient samples`() {
        val result = FallDetectionEngine.assess(listOf(0f, 0f, 0f))
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
}
