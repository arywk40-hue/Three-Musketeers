package com.smartsuit.ml

import com.smartsuit.data.CaregiverAlertStatus
import com.smartsuit.data.EcgAnomalyStatus
import com.smartsuit.data.FatigueStatus
import com.smartsuit.data.PostureStatus
import com.smartsuit.data.RiskStatus
import com.smartsuit.data.SensorFrame
import org.junit.Assert.assertEquals
import org.junit.Test

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

    // ── Urgent cases ──

    @Test
    fun `SOS active always returns Urgent`() {
        val frame = baseFrame(heartRateBpm = 75, sosActive = true)
        assertEquals(CaregiverAlertStatus.Urgent, CaregiverAlertPolicy.evaluate(frame))
    }

    @Test
    fun `HR above 130 returns Urgent`() {
        assertEquals(CaregiverAlertStatus.Urgent, CaregiverAlertPolicy.evaluate(baseFrame(heartRateBpm = 135)))
    }

    @Test
    fun `HR below 40 returns Urgent`() {
        assertEquals(CaregiverAlertStatus.Urgent, CaregiverAlertPolicy.evaluate(baseFrame(heartRateBpm = 38)))
    }

    @Test
    fun `SpO2 below 90 returns Urgent`() {
        assertEquals(CaregiverAlertStatus.Urgent, CaregiverAlertPolicy.evaluate(baseFrame(spo2Percent = 88f)))
    }

    @Test
    fun `fall risk High returns Urgent`() {
        assertEquals(CaregiverAlertStatus.Urgent, CaregiverAlertPolicy.evaluate(baseFrame(fallRisk = RiskStatus.High)))
    }

    @Test
    fun `ecg anomaly AFib returns Urgent`() {
        assertEquals(CaregiverAlertStatus.Urgent, CaregiverAlertPolicy.evaluate(baseFrame(ecgAnomaly = EcgAnomalyStatus.AFib)))
    }

    @Test
    fun `vitals risk High returns Urgent`() {
        assertEquals(CaregiverAlertStatus.Urgent, CaregiverAlertPolicy.evaluate(baseFrame(vitalsRisk = RiskStatus.High)))
    }

    @Test
    fun `Urgent takes priority over Check signals`() {
        // AFib would be Urgent on its own; combine with a Check signal — must
        // still resolve to Urgent.
        val frame = baseFrame(
            ecgAnomaly = EcgAnomalyStatus.AFib,
            inactivityMinutes = 60,
            heartRateBpm = 115,    // would be Check
        )
        assertEquals(CaregiverAlertStatus.Urgent, CaregiverAlertPolicy.evaluate(frame))
    }

    // ── Check cases ──

    @Test
    fun `fall risk Medium returns Check`() {
        assertEquals(CaregiverAlertStatus.Check, CaregiverAlertPolicy.evaluate(baseFrame(fallRisk = RiskStatus.Medium)))
    }

    @Test
    fun `dehydration High returns Check`() {
        assertEquals(CaregiverAlertStatus.Check, CaregiverAlertPolicy.evaluate(baseFrame(dehydration = RiskStatus.High)))
    }

    @Test
    fun `fatigue Stop returns Check`() {
        assertEquals(CaregiverAlertStatus.Check, CaregiverAlertPolicy.evaluate(baseFrame(fatigue = FatigueStatus.Stop)))
    }

    @Test
    fun `inactivity over 20 minutes returns Check`() {
        assertEquals(CaregiverAlertStatus.Check, CaregiverAlertPolicy.evaluate(baseFrame(inactivityMinutes = 25)))
    }

    @Test
    fun `HR caution band 110-130 returns Check`() {
        assertEquals(CaregiverAlertStatus.Check, CaregiverAlertPolicy.evaluate(baseFrame(heartRateBpm = 115)))
    }

    @Test
    fun `HR caution band 40-50 returns Check`() {
        assertEquals(CaregiverAlertStatus.Check, CaregiverAlertPolicy.evaluate(baseFrame(heartRateBpm = 45)))
    }

    @Test
    fun `SpO2 90 to 94 returns Check`() {
        assertEquals(CaregiverAlertStatus.Check, CaregiverAlertPolicy.evaluate(baseFrame(spo2Percent = 92f)))
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
