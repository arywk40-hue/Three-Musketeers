package com.eldercareguardian.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BloodPressureEstimatorTest {

    @Test
    fun `baseline HR and temp returns near-baseline BP`() {
        val r = BloodPressureEstimator.estimate(70, 33.0f)
        assertEquals(115, r.systolicMmHg)
        assertEquals(75, r.diastolicMmHg)
        assertTrue(r.isEstimated)
    }

    @Test
    fun `elevated HR increases systolic`() {
        val baseline = BloodPressureEstimator.estimate(70, 33.0f)
        val elevated = BloodPressureEstimator.estimate(120, 33.0f)
        assertTrue(elevated.systolicMmHg > baseline.systolicMmHg)
    }

    @Test
    fun `result is clamped within physiological range`() {
        val extreme = BloodPressureEstimator.estimate(220, 42.0f)
        assertTrue(extreme.systolicMmHg <= 180)
        assertTrue(extreme.diastolicMmHg <= 110)
        val low = BloodPressureEstimator.estimate(20, 28.0f)
        assertTrue(low.systolicMmHg >= 85)
        assertTrue(low.diastolicMmHg >= 50)
    }

    @Test
    fun `elevated skin temp increases systolic`() {
        val cool = BloodPressureEstimator.estimate(70, 33.0f)
        val warm = BloodPressureEstimator.estimate(70, 38.0f)
        assertTrue(warm.systolicMmHg > cool.systolicMmHg)
    }
}
