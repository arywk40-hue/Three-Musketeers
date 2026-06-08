package com.smartsuit.ml

import com.smartsuit.data.RiskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DehydrationRiskModelTest {

    @Test
    fun `low sweat rate normal temp normal HR returns Low`() {
        val r = DehydrationRiskModel.assess(0.3f, 33.0f, 70)
        assertEquals(RiskStatus.Low, r.risk)
    }

    @Test
    fun `high sweat rate returns at least Medium`() {
        val r = DehydrationRiskModel.assess(1.6f, 33.0f, 70)
        assert(r.risk != RiskStatus.Low) { "Expected Medium or High, got ${r.risk}" }
    }

    @Test
    fun `all three contributors at high threshold returns High`() {
        val r = DehydrationRiskModel.assess(1.6f, 37.6f, 105)
        assertEquals(RiskStatus.High, r.risk)
    }

    @Test
    fun `score is in 0 to 1 range`() {
        val r = DehydrationRiskModel.assess(2.0f, 38.0f, 120)
        assert(r.score in 0f..1f) { "Score out of range: ${r.score}" }
    }

    @Test
    fun `sweat rate just below medium threshold stays Low`() {
        val r = DehydrationRiskModel.assess(0.79f, 33.0f, 70)
        assertEquals(RiskStatus.Low, r.risk)
    }
}
