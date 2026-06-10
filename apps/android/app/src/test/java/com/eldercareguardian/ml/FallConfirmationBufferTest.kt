package com.eldercareguardian.ml

import com.eldercareguardian.data.RiskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class FallConfirmationBufferTest {

    private fun assess(status: RiskStatus) =
        FallDetectionEngine.FallAssessment(riskStatus = status, riskScore = 0f, accelerationMagnitude = 0f)

    @Test
    fun `single High frame with no Medium returns Medium`() {
        val buffer = FallConfirmationBuffer()
        val result = buffer.assess(assess(RiskStatus.High), sosActive = false)
        assertEquals(RiskStatus.Medium, result)
    }

    @Test
    fun `High followed by Medium within window returns High`() {
        val buffer = FallConfirmationBuffer()
        buffer.assess(assess(RiskStatus.High), sosActive = false)
        val result = buffer.assess(assess(RiskStatus.Medium), sosActive = false)
        assertEquals(RiskStatus.High, result)
    }

    @Test
    fun `sosActive true always returns High regardless of IMU`() {
        val buffer = FallConfirmationBuffer()
        val low = buffer.assess(assess(RiskStatus.Low), sosActive = true)
        val high = buffer.assess(assess(RiskStatus.High), sosActive = true)
        val medium = buffer.assess(assess(RiskStatus.Medium), sosActive = true)
        assertEquals(RiskStatus.High, low)
        assertEquals(RiskStatus.High, high)
        assertEquals(RiskStatus.High, medium)
    }

    @Test
    fun `normal frames only return Low`() {
        val buffer = FallConfirmationBuffer()
        val first = buffer.assess(assess(RiskStatus.Low), sosActive = false)
        val second = buffer.assess(assess(RiskStatus.Low), sosActive = false)
        val third = buffer.assess(assess(RiskStatus.Low), sosActive = false)
        assertEquals(RiskStatus.Low, first)
        assertEquals(RiskStatus.Low, second)
        assertEquals(RiskStatus.Low, third)
    }

    @Test
    fun `High frame ages out of window and no longer contributes to confirmation`() {
        val buffer = FallConfirmationBuffer()
        assertEquals(RiskStatus.Medium, buffer.assess(assess(RiskStatus.High), sosActive = false))
        assertEquals(RiskStatus.High, buffer.assess(assess(RiskStatus.Medium), sosActive = false))
        assertEquals(RiskStatus.High, buffer.assess(assess(RiskStatus.Low), sosActive = false))
        assertEquals(RiskStatus.Low, buffer.assess(assess(RiskStatus.Low), sosActive = false))
        val afterAged = buffer.assess(assess(RiskStatus.Medium), sosActive = false)
        assertEquals(RiskStatus.Medium, afterAged)
    }

    @Test
    fun `Medium followed by High also returns High when order is reversed`() {
        val buffer = FallConfirmationBuffer()
        val first = buffer.assess(assess(RiskStatus.Medium), sosActive = false)
        val second = buffer.assess(assess(RiskStatus.High), sosActive = false)
        assertEquals(RiskStatus.Medium, first)
        assertEquals(RiskStatus.High, second)
    }

    // ── Pattern-driven tests (FallImuPatterns) ──

    @Test
    fun `confirmedFall pattern ends in High after spike plus stillness`() {
        val buffer = FallConfirmationBuffer()
        val sequence = FallImuPatterns.confirmedFallSequence()
        val outputs = sequence.map { imu ->
            buffer.assess(assess(FallImuPatterns.expectedEngineStatus(imu)), sosActive = false)
        }
        assertEquals(RiskStatus.Low, outputs[0])
        assertEquals(RiskStatus.Medium, outputs[1])
        assertEquals(RiskStatus.High, outputs[2])
        assertEquals(RiskStatus.High, outputs[3])
        assertEquals(RiskStatus.Medium, outputs[4])
    }

    @Test
    fun `walking artifact pattern never escalates to High`() {
        val buffer = FallConfirmationBuffer()
        val sequence = FallImuPatterns.walkingArtifactSequence()
        val outputs = sequence.map { imu ->
            buffer.assess(assess(FallImuPatterns.expectedEngineStatus(imu)), sosActive = false)
        }
        outputs.forEach { assert(it != RiskStatus.High) { "Walking artifact escalated to High at output $it: $outputs" } }
    }

    @Test
    fun `brief jolt pattern is filtered out and never escalates to High`() {
        val buffer = FallConfirmationBuffer()
        val sequence = FallImuPatterns.briefJoltSequence()
        val outputs = sequence.map { imu ->
            buffer.assess(assess(FallImuPatterns.expectedEngineStatus(imu)), sosActive = false)
        }
        outputs.forEach { assert(it != RiskStatus.High) { "Brief jolt escalated to High at output $it: $outputs" } }
    }
}
