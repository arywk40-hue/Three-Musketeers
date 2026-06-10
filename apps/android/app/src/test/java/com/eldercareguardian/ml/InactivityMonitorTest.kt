package com.eldercareguardian.ml

import org.junit.Assert.assertEquals
import org.junit.Test

class InactivityMonitorTest {

    @Test
    fun `movement resets inactivity counter to zero`() {
        val result = InactivityMonitor.assess(imuMagnitude = 15.0f, previousInactivitySeconds = 120)
        assertEquals(0, result)
    }

    @Test
    fun `still patient increments counter by one`() {
        val result = InactivityMonitor.assess(imuMagnitude = 9.81f, previousInactivitySeconds = 30)
        assertEquals(31, result)
    }

    @Test
    fun `toMinutes converts correctly`() {
        assertEquals(0, InactivityMonitor.toMinutes(59))
        assertEquals(1, InactivityMonitor.toMinutes(60))
        assertEquals(2, InactivityMonitor.toMinutes(150))
    }

    @Test
    fun `magnitude is correct for known vector`() {
        val mag = InactivityMonitor.magnitude(3f, 4f, 0f)
        assertEquals(5f, mag, 0.01f)
    }

    @Test
    fun `magnitude for upright rest is approximately gravity`() {
        val mag = InactivityMonitor.magnitude(0f, 0f, 9.81f)
        assertEquals(9.81f, mag, 0.01f)
    }
}
