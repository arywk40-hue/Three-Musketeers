package com.eldercareguardian.ml

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the Phase 4/5 fixed InactivityMonitor.
 *
 * Critical fix verified here: the old `assess()` function accumulated inactivity
 * seconds indefinitely without resetting on movement. The new implementation
 * correctly resets to 0 when `deviated` is true.
 */
class InactivityMonitorPhase5Test {

    private val gravity = 9.81f
    private val movingMag = gravity + 2.0f  // deviation > MOVEMENT_THRESHOLD (0.6)
    private val stillMag = gravity + 0.1f   // deviation < MOVEMENT_THRESHOLD

    // ── Basic accumulation ───────────────────────────────────────────────────

    @Test
    fun `still magnitude increments counter`() {
        val result = InactivityMonitor.assess(stillMag, previousInactivitySeconds = 0)
        assertEquals(1, result)
    }

    @Test
    fun `still magnitude accumulates over multiple calls`() {
        var count = 0
        repeat(10) { count = InactivityMonitor.assess(stillMag, count) }
        assertEquals(10, count)
    }

    // ── Reset on movement (critical fix) ─────────────────────────────────────

    @Test
    fun `movement resets counter to zero`() {
        // Accumulate 100 seconds of inactivity
        var count = 100
        // One frame of movement
        count = InactivityMonitor.assess(movingMag, count)
        assertEquals(0, count)
    }

    @Test
    fun `movement resets even after long inactivity`() {
        var count = InactivityMonitor.DAY_INACTIVITY_THRESHOLD_MINUTES * 60 + 100
        count = InactivityMonitor.assess(movingMag, count)
        assertEquals(0, count)
    }

    @Test
    fun `counter resumes accumulating after movement`() {
        var count = 50
        count = InactivityMonitor.assess(movingMag, count)  // Reset
        assertEquals(0, count)
        count = InactivityMonitor.assess(stillMag, count)   // Start again
        assertEquals(1, count)
        count = InactivityMonitor.assess(stillMag, count)
        assertEquals(2, count)
    }

    // ── Fall active suppression ──────────────────────────────────────────────

    @Test
    fun `isFallActive suppresses accumulation`() {
        val result = InactivityMonitor.assess(stillMag, previousInactivitySeconds = 0, isFallActive = true)
        assertEquals(0, result)
    }

    @Test
    fun `isFallActive resets existing inactivity to zero`() {
        val result = InactivityMonitor.assess(stillMag, previousInactivitySeconds = 100, isFallActive = true)
        assertEquals(0, result)
    }

    // ── Magnitude computation ────────────────────────────────────────────────

    @Test
    fun `magnitude of pure Z gravity is 9_81`() {
        val mag = InactivityMonitor.magnitude(0f, 0f, 9.81f)
        assertEquals(9.81f, mag, 0.001f)
    }

    @Test
    fun `magnitude of zero vector is zero`() {
        val mag = InactivityMonitor.magnitude(0f, 0f, 0f)
        assertEquals(0f, mag, 0.001f)
    }

    // ── toMinutes conversion ─────────────────────────────────────────────────

    @Test
    fun `toMinutes converts correctly`() {
        assertEquals(0, InactivityMonitor.toMinutes(59))
        assertEquals(1, InactivityMonitor.toMinutes(60))
        assertEquals(20, InactivityMonitor.toMinutes(1200))
        assertEquals(20, InactivityMonitor.toMinutes(1259))
        assertEquals(21, InactivityMonitor.toMinutes(1260))
    }

    // ── Night-time threshold ─────────────────────────────────────────────────

    @Test
    fun `night hours 22-23 return night threshold`() {
        for (hour in listOf(22, 23)) {
            assertEquals(
                InactivityMonitor.NIGHT_INACTIVITY_THRESHOLD_MINUTES,
                InactivityMonitor.thresholdMinutesForHour(hour),
            )
        }
    }

    @Test
    fun `early morning hours 0-5 return night threshold`() {
        for (hour in 0..5) {
            assertEquals(
                InactivityMonitor.NIGHT_INACTIVITY_THRESHOLD_MINUTES,
                InactivityMonitor.thresholdMinutesForHour(hour),
            )
        }
    }

    @Test
    fun `daytime hours 6-21 return day threshold`() {
        for (hour in 6..21) {
            assertEquals(
                InactivityMonitor.DAY_INACTIVITY_THRESHOLD_MINUTES,
                InactivityMonitor.thresholdMinutesForHour(hour),
            )
        }
    }
}
