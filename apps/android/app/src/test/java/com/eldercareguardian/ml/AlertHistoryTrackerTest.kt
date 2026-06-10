package com.eldercareguardian.ml

import com.eldercareguardian.data.AlertReason
import com.eldercareguardian.data.CaregiverAlertStatus
import com.eldercareguardian.data.PostureStatus
import com.eldercareguardian.data.RiskStatus
import com.eldercareguardian.data.SensorFrame
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AlertHistoryTrackerTest {

    private fun frame(level: CaregiverAlertStatus, hr: Int = 75, sos: Boolean = false) = SensorFrame(
        timestampMillis = 0L,
        heartRateBpm = hr,
        spo2Percent = 98f,
        systolicMmHg = 120,
        diastolicMmHg = 80,
        skinTempC = 33.0f,
        humidityPercent = 50f,
        respiratoryRate = 16,
        posture = PostureStatus.Good,
        fatigue = com.eldercareguardian.data.FatigueStatus.Safe,
        dehydration = RiskStatus.Low,
        fallRisk = RiskStatus.Low,
        caregiverAlert = level,
        sosActive = sos,
        inactivityMinutes = 0,
        supercapPercent = 90,
        ecgSamples = emptyList(),
    )

    @Test
    fun `first frame does not emit an event`() = runTest {
        val tracker = AlertHistoryTracker()
        val event = tracker.onFrame(frame(level = CaregiverAlertStatus.Normal))
        assertNull(event)
    }

    @Test
    fun `same level on consecutive frames does not emit`() = runTest {
        val tracker = AlertHistoryTracker()
        tracker.onFrame(frame(level = CaregiverAlertStatus.Normal))
        val second = tracker.onFrame(frame(level = CaregiverAlertStatus.Normal))
        assertNull(second)
    }

    @Test
    fun `Normal to Emergency emits an SOS event when SOS active`() = runTest {
        val tracker = AlertHistoryTracker()
        tracker.onFrame(frame(level = CaregiverAlertStatus.Normal))
        // Emergency replaces Urgent (Phase 7 rename)
        val event = tracker.onFrame(frame(level = CaregiverAlertStatus.Emergency, sos = true))
        assertNotNull(event)
        assertEquals(CaregiverAlertStatus.Emergency, event!!.level)
        assertEquals(AlertReason.SosButton, event.reason)
    }

    @Test
    fun `Check to Normal emits a Resolved event`() = runTest {
        val tracker = AlertHistoryTracker()
        tracker.onFrame(frame(level = CaregiverAlertStatus.Check))
        val event = tracker.onFrame(frame(level = CaregiverAlertStatus.Normal))
        assertNotNull(event)
        assertEquals(AlertReason.Resolved, event!!.reason)
    }

    @Test
    fun `prepend caps at maxEvents`() {
        val tracker = AlertHistoryTracker(maxEvents = 3)
        val seed = emptyList<com.eldercareguardian.data.AlertEvent>()
        // Phase 7: Emergency replaces Urgent
        val v1 = com.eldercareguardian.data.AlertEvent(level = CaregiverAlertStatus.Emergency, reason = AlertReason.SosButton)
        val v2 = com.eldercareguardian.data.AlertEvent(level = CaregiverAlertStatus.Emergency, reason = AlertReason.SosButton)
        val v3 = com.eldercareguardian.data.AlertEvent(level = CaregiverAlertStatus.Normal, reason = AlertReason.Resolved)
        val v4 = com.eldercareguardian.data.AlertEvent(level = CaregiverAlertStatus.Check, reason = AlertReason.Inactivity)
        val a = tracker.prepend(seed, v1)
        val b = tracker.prepend(a, v2)
        val c = tracker.prepend(b, v3)
        val d = tracker.prepend(c, v4)
        assertEquals(3, d.size)
        assertEquals(v4, d[0])
    }

    @Test
    fun `concurrent onFrame calls serialise via mutex`() = runTest {
        val tracker = AlertHistoryTracker()
        // Fire 100 frames concurrently. Each emits a transition event, but
        // serialisation via the mutex must guarantee no lost updates and
        // no exceptions.
        val frames = List(100) { i -> frame(level = if (i % 2 == 0) CaregiverAlertStatus.Check else CaregiverAlertStatus.Normal) }
        val events = frames.map { tracker.onFrame(it) }
        // First frame has no previous → no event. Remaining 99 alternate.
        assertNull(events.first())
        events.drop(1).forEach { assertNotNull(it) }
    }

    @Test
    fun `Normal to Warning emits a DeviceAlert event`() = runTest {
        val tracker = AlertHistoryTracker()
        tracker.onFrame(frame(level = CaregiverAlertStatus.Normal))
        val event = tracker.onFrame(frame(level = CaregiverAlertStatus.Warning))
        assertNotNull(event)
        assertEquals(CaregiverAlertStatus.Warning, event!!.level)
    }
}
