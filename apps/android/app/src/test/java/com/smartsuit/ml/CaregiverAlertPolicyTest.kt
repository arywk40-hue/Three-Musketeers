package com.smartsuit.ml

import com.smartsuit.data.CaregiverAlertStatus
import com.smartsuit.data.EcgAnomalyStatus
import com.smartsuit.data.FatigueStatus
import com.smartsuit.data.PostureStatus
import com.smartsuit.data.RiskStatus
import com.smartsuit.data.SensorFrame
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Legacy CaregiverAlertPolicyTest — updated for Phase 7 4-level alert system.
 *
 * Migration notes:
 *  - `CaregiverAlertStatus.Urgent` → renamed to `CaregiverAlertStatus.Emergency`
 *  - HR 110–130 bpm → moved from Check to Warning
 *  - HR 40–50 bpm → moved from Check to Warning
 *  - SpO2 90–94% → moved from Check to Warning
 *  - Fatigue Stop → moved from Check to Warning
 *  - Fall risk Medium → moved from Check to Warning
 *  - Vitals risk High → moved from Check to Warning (was Urgent, now Warning)
 *  - HR caution band 100–110 bpm → remains Check
 *  - HR caution band 50–60 bpm → remains Check
 *  - SpO2 94–96% → added as Check
 *
 * For the new comprehensive Phase 7 tests, see CaregiverAlertPolicyPhase7Test.kt.
 */
class CaregiverAlertPolicyTest {

    private fun baseFrame(
        heartRateBpm: Int = 75,
        spo2Percent: Float = 98f,
        fallRisk: RiskStatus = RiskStatus.Low,
        dehydration: RiskStatus = RiskStatus.Low,
        fatigue: FatigueStatus = FatigueStatus.Safe,
        inactivityMinutes: Int = 0,
        ecgAnomaly: EcgAnomalyStatus = EcgAnomalyStatus.Normal,
        vitalsRisk: RiskStatus = RiskStatus.Low,
        sosActive: Boolean = false,
        batteryPercent: Int? = 75,
    ): SensorFrame = SensorFrame(
        timestampMillis = 0L,
        heartRateBpm = heartRateBpm,
        spo2Percent = spo2Percent,
        systolicMmHg = 120,
        diastolicMmHg = 80,
        skinTempC = 33.0f,
        humidityPercent = 50f,
        respiratoryRate = 16,
        posture = PostureStatus.Good,
        fatigue = fatigue,
        dehydration = dehydration,
        fallRisk = fallRisk,
        caregiverAlert = CaregiverAlertStatus.Normal,
        sosActive = sosActive,
        inactivityMinutes = inactivityMinutes,
        supercapPercent = 90,
        ecgSamples = emptyList(),
        ecgAnomaly = ecgAnomaly,
        vitalsRisk = vitalsRisk,
        batteryPercent = batteryPercent,
    )

    // ── Emergency cases (was: Urgent) ──

    @Test
    fun `SOS active always returns Emergency`() {
        val frame = baseFrame(heartRateBpm = 75, sosActive = true)
        assertEquals(CaregiverAlertStatus.Emergency, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `HR above 130 returns Emergency`() {
        assertEquals(CaregiverAlertStatus.Emergency, CaregiverAlertPolicy.evaluate(baseFrame(heartRateBpm = 135)))
    }

    @Test
    fun `HR below 40 returns Emergency`() {
        assertEquals(CaregiverAlertStatus.Emergency, CaregiverAlertPolicy.evaluate(baseFrame(heartRateBpm = 38)))
    }

    @Test
    fun `SpO2 below 90 returns Emergency`() {
        assertEquals(CaregiverAlertStatus.Emergency, CaregiverAlertPolicy.evaluate(baseFrame(spo2Percent = 88f)))
    }

    @Test
    fun `fall risk High returns Emergency`() {
        assertEquals(CaregiverAlertStatus.Emergency, CaregiverAlertPolicy.evaluate(baseFrame(fallRisk = RiskStatus.High)))
    }

    @Test
    fun `ecg anomaly AFib returns Emergency`() {
        assertEquals(CaregiverAlertStatus.Emergency, CaregiverAlertPolicy.evaluate(baseFrame(ecgAnomaly = EcgAnomalyStatus.AFib)))
    }

    @Test
    fun `Emergency takes priority over Warning signals`() {
        val frame = baseFrame(
            ecgAnomaly = EcgAnomalyStatus.AFib,   // Emergency
            inactivityMinutes = 60,                 // Warning at night, Check during day
            heartRateBpm = 115,                     // Warning
        )
        assertEquals(CaregiverAlertStatus.Emergency, CaregiverAlertPolicy.evaluate(frame))
    }

    // ── Warning cases (new level — between Check and Emergency) ──

    @Test
    fun `fall risk Medium returns Warning`() {
        assertEquals(CaregiverAlertStatus.Warning, CaregiverAlertPolicy.evaluate(baseFrame(fallRisk = RiskStatus.Medium)))
    }

    @Test
    fun `fatigue Stop returns Warning`() {
        assertEquals(CaregiverAlertStatus.Warning, CaregiverAlertPolicy.evaluate(baseFrame(fatigue = FatigueStatus.Stop)))
    }

    @Test
    fun `HR caution band 110-130 returns Warning`() {
        assertEquals(CaregiverAlertStatus.Warning, CaregiverAlertPolicy.evaluate(baseFrame(heartRateBpm = 115)))
    }

    @Test
    fun `HR caution band 40-50 returns Warning`() {
        assertEquals(CaregiverAlertStatus.Warning, CaregiverAlertPolicy.evaluate(baseFrame(heartRateBpm = 45)))
    }

    @Test
    fun `SpO2 90 to 94 returns Warning`() {
        assertEquals(CaregiverAlertStatus.Warning, CaregiverAlertPolicy.evaluate(baseFrame(spo2Percent = 92f)))
    }

    @Test
    fun `vitals risk High returns Warning`() {
        assertEquals(CaregiverAlertStatus.Warning, CaregiverAlertPolicy.evaluate(baseFrame(vitalsRisk = RiskStatus.High)))
    }

    // ── Check cases ──

    @Test
    fun `dehydration High returns Check`() {
        assertEquals(CaregiverAlertStatus.Check, CaregiverAlertPolicy.evaluate(baseFrame(dehydration = RiskStatus.High)))
    }

    @Test
    fun `inactivity over 20 minutes during day returns at least Check`() {
        val result = CaregiverAlertPolicy.evaluate(baseFrame(inactivityMinutes = 25))
        assert(result != CaregiverAlertStatus.Normal) { "Expected at least Check for 25-min inactivity" }
    }

    @Test
    fun `HR mild caution band 100-110 returns Check`() {
        assertEquals(CaregiverAlertStatus.Check, CaregiverAlertPolicy.evaluate(baseFrame(heartRateBpm = 105)))
    }

    @Test
    fun `HR mild caution band 50-60 returns Check`() {
        assertEquals(CaregiverAlertStatus.Check, CaregiverAlertPolicy.evaluate(baseFrame(heartRateBpm = 55)))
    }

    @Test
    fun `battery below 15 percent returns Check`() {
        assertEquals(CaregiverAlertStatus.Check, CaregiverAlertPolicy.evaluate(baseFrame(batteryPercent = 12)))
    }

    @Test
    fun `battery at or above 15 percent does not return Check`() {
        assertEquals(CaregiverAlertStatus.Normal, CaregiverAlertPolicy.evaluate(baseFrame(batteryPercent = 15)))
        assertEquals(CaregiverAlertStatus.Normal, CaregiverAlertPolicy.evaluate(baseFrame(batteryPercent = 80)))
    }

    @Test
    fun `battery null does not return Check`() {
        assertEquals(CaregiverAlertStatus.Normal, CaregiverAlertPolicy.evaluate(baseFrame(batteryPercent = null)))
    }

    // ── Normal ──

    @Test
    fun `stable patient returns Normal`() {
        val frame = baseFrame()
        assertEquals(CaregiverAlertStatus.Normal, CaregiverAlertPolicy.evaluate(frame))
    }
}
