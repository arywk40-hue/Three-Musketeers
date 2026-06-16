package com.eldercareguardian.ml

import com.eldercareguardian.data.RiskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class VitalsRiskMonitorTest {

    @Test
    fun `stable vitals return Low risk`() {
        val r = VitalsRiskMonitor.assess(75, 97f, 16, 35.5f)
        assertEquals(RiskStatus.Low, r.risk)
    }

    @Test
    fun `HR above hard limit scores 3 returns High`() {
        val r = VitalsRiskMonitor.assess(135, 97f, 16, 35.5f)
        assertEquals(RiskStatus.High, r.risk)
    }

    @Test
    fun `SpO2 below 90 scores 3 returns High`() {
        val r = VitalsRiskMonitor.assess(75, 88f, 16, 35.5f)
        assertEquals(RiskStatus.High, r.risk)
    }

    @Test
    fun `two moderate signals accumulate to Medium`() {
        val r = VitalsRiskMonitor.assess(115, 97f, 21, 35.5f)
        assertEquals(RiskStatus.Medium, r.risk)
    }

    @Test
    fun `score is non-negative`() {
        val r = VitalsRiskMonitor.assess(75, 97f, 16, 35.5f)
        assert(r.score >= 0) { "Score was negative: ${r.score}" }
    }
}
