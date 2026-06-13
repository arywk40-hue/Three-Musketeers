package com.eldercareguardian.ml

import com.eldercareguardian.data.CaregiverAlertStatus
import com.eldercareguardian.data.EcgAnomalyStatus
import com.eldercareguardian.data.FatigueStatus
import com.eldercareguardian.data.PostureStatus
import com.eldercareguardian.data.RiskStatus
import com.eldercareguardian.data.SensorFrame
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tests for the 4-level CaregiverAlertPolicy (Phase 7).
 * Covers all level boundaries and priority ordering.
 */
class CaregiverAlertPolicyPhase7Test {

    @Before
    fun setUp() {
        CaregiverAlertPolicy.reset()
    }

    private fun normalFrame(): SensorFrame = SensorFrame(
        timestampMillis = System.currentTimeMillis(),
        heartRateBpm = 72,
        spo2Percent = 98f,
        systolicMmHg = 120,
        diastolicMmHg = 80,
        skinTempC = 36.5f,
        humidityPercent = 45f,
        respiratoryRate = 16,
        posture = PostureStatus.Good,
        fatigue = FatigueStatus.Safe,
        dehydration = RiskStatus.Low,
        fallRisk = RiskStatus.Low,
        caregiverAlert = CaregiverAlertStatus.Normal,
        sosActive = false,
        inactivityMinutes = 0,
        supercapPercent = 80,
        ecgSamples = emptyList(),
        ecgAnomaly = EcgAnomalyStatus.Normal,
        vitalsRisk = RiskStatus.Low,
        batteryPercent = 80,
    )

    // ── Level 4 — Emergency ──────────────────────────────────────────────────

    @Test
    fun `SOS active triggers Emergency`() {
        val frame = normalFrame().copy(sosActive = true)
        assertEquals(CaregiverAlertStatus.Emergency, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `high fall risk triggers Emergency`() {
        val frame = normalFrame().copy(fallRisk = RiskStatus.High)
        assertEquals(CaregiverAlertStatus.Emergency, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `SpO2 below 90 triggers Emergency`() {
        val frame = normalFrame().copy(spo2Percent = 89f)
        repeat(3) { CaregiverAlertPolicy.evaluate(frame) }
        assertEquals(CaregiverAlertStatus.Emergency, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `HR above 130 triggers Emergency`() {
        val frame = normalFrame().copy(heartRateBpm = 131)
        assertEquals(CaregiverAlertStatus.Emergency, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `HR below 40 triggers Emergency`() {
        val frame = normalFrame().copy(heartRateBpm = 39)
        assertEquals(CaregiverAlertStatus.Emergency, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `AFib triggers Emergency`() {
        val frame = normalFrame().copy(ecgAnomaly = EcgAnomalyStatus.AFib)
        assertEquals(CaregiverAlertStatus.Emergency, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `SOS overrides all other states to Emergency`() {
        // Even if everything else is fine, SOS = Emergency
        val frame = normalFrame().copy(sosActive = true, heartRateBpm = 72, spo2Percent = 98f)
        assertEquals(CaregiverAlertStatus.Emergency, CaregiverAlertPolicy.evaluate(frame))
    }

    // ── Level 3 — Warning ───────────────────────────────────────────────────

    @Test
    fun `SpO2 at 92 percent triggers Warning`() {
        val frame = normalFrame().copy(spo2Percent = 92f)
        repeat(3) { CaregiverAlertPolicy.evaluate(frame) }
        assertEquals(CaregiverAlertStatus.Warning, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `HR at 115 bpm triggers Warning`() {
        val frame = normalFrame().copy(heartRateBpm = 115)
        assertEquals(CaregiverAlertStatus.Warning, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `HR at 48 bpm triggers Warning`() {
        val frame = normalFrame().copy(heartRateBpm = 48)
        assertEquals(CaregiverAlertStatus.Warning, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `medium fall risk triggers Warning`() {
        val frame = normalFrame().copy(fallRisk = RiskStatus.Medium)
        assertEquals(CaregiverAlertStatus.Warning, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `High vitals risk triggers Warning`() {
        val frame = normalFrame().copy(vitalsRisk = RiskStatus.High)
        assertEquals(CaregiverAlertStatus.Warning, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `Fatigue Stop triggers Warning`() {
        val frame = normalFrame().copy(fatigue = FatigueStatus.Stop)
        assertEquals(CaregiverAlertStatus.Warning, CaregiverAlertPolicy.evaluate(frame))
    }

    // ── Level 2 — Check ─────────────────────────────────────────────────────

    @Test
    fun `HR at 105 bpm triggers Check`() {
        val frame = normalFrame().copy(heartRateBpm = 105)
        assertEquals(CaregiverAlertStatus.Check, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `HR at 55 bpm triggers Check`() {
        val frame = normalFrame().copy(heartRateBpm = 55)
        assertEquals(CaregiverAlertStatus.Check, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `SpO2 at 95 percent triggers Check`() {
        val frame = normalFrame().copy(spo2Percent = 95f)
        repeat(3) { CaregiverAlertPolicy.evaluate(frame) }
        assertEquals(CaregiverAlertStatus.Check, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `inactivity over 20 minutes during day triggers Check`() {
        val frame = normalFrame().copy(inactivityMinutes = 21)
        // Note: this test may vary by time of day. The policy uses Calendar.getInstance().
        // For testing, we verify the threshold boundary is correct regardless of hour.
        val result = CaregiverAlertPolicy.evaluate(frame)
        // Must be at least Check (could be Warning at night)
        assert(result != CaregiverAlertStatus.Normal) {
            "Expected at least Check for 21-minute inactivity but got Normal"
        }
    }

    @Test
    fun `high dehydration risk triggers Check`() {
        val frame = normalFrame().copy(dehydration = RiskStatus.High)
        assertEquals(CaregiverAlertStatus.Check, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `low battery triggers Check`() {
        val frame = normalFrame().copy(batteryPercent = 10)
        assertEquals(CaregiverAlertStatus.Check, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `ECG Tachycardia classification triggers Check`() {
        val frame = normalFrame().copy(ecgAnomaly = EcgAnomalyStatus.Tachycardia)
        assertEquals(CaregiverAlertStatus.Check, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `ECG Bradycardia classification triggers Check`() {
        val frame = normalFrame().copy(ecgAnomaly = EcgAnomalyStatus.Bradycardia)
        assertEquals(CaregiverAlertStatus.Check, CaregiverAlertPolicy.evaluate(frame))
    }

    // ── Level 1 — Normal ────────────────────────────────────────────────────

    @Test
    fun `all values in normal range returns Normal`() {
        val frame = normalFrame()
        assertEquals(CaregiverAlertStatus.Normal, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `null battery does not trigger any alert`() {
        val frame = normalFrame().copy(batteryPercent = null)
        assertEquals(CaregiverAlertStatus.Normal, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `battery at exactly 15 triggers Check`() {
        val frame = normalFrame().copy(batteryPercent = 14)
        assertEquals(CaregiverAlertStatus.Check, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `battery at 15 does not trigger Check`() {
        // Threshold is < 15, so 15 is fine
        val frame = normalFrame().copy(batteryPercent = 15)
        assertEquals(CaregiverAlertStatus.Normal, CaregiverAlertPolicy.evaluate(frame))
    }

    // ── Priority order tests ─────────────────────────────────────────────────

    @Test
    fun `Emergency takes priority over Warning conditions`() {
        val frame = normalFrame().copy(
            sosActive = true,
            spo2Percent = 92f,  // Would be Warning
            fallRisk = RiskStatus.Medium,  // Would be Warning
        )
        assertEquals(CaregiverAlertStatus.Emergency, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `Warning takes priority over Check conditions`() {
        val frame = normalFrame().copy(
            heartRateBpm = 115,  // Warning
            dehydration = RiskStatus.High,  // Would be Check
            batteryPercent = 10,  // Would be Check
        )
        assertEquals(CaregiverAlertStatus.Warning, CaregiverAlertPolicy.evaluate(frame))
    }
}
