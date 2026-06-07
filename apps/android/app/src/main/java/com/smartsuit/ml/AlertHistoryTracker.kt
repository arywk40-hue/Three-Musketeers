package com.smartsuit.ml

import com.smartsuit.data.AlertEvent
import com.smartsuit.data.AlertReason
import com.smartsuit.data.CaregiverAlertStatus
import com.smartsuit.data.RiskStatus
import com.smartsuit.data.SensorFrame
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe stateful detector for alert-level transitions.
 *
 * Extracted from SmartSuitViewModel so the read/mutate of [previousLevel] is
 * serialised by an internal [Mutex] rather than relying on the single-collector
 * guarantee of a viewModelScope launch. This keeps the alert-history append
 * correct under any future `flowOn(Dispatchers.IO)` change.
 */
class AlertHistoryTracker(private val maxEvents: Int = 50) {
    private val mutex = Mutex()
    private var previousLevel: CaregiverAlertStatus? = null

    suspend fun onFrame(frame: SensorFrame): AlertEvent? = mutex.withLock {
        val current = frame.caregiverAlert
        val previous = previousLevel
        previousLevel = current
        if (previous == null || current == previous) {
            null
        } else {
            AlertEvent(level = current, reason = reasonFor(frame, current))
        }
    }

    fun prepend(history: List<AlertEvent>, event: AlertEvent): List<AlertEvent> =
        (listOf(event) + history).take(maxEvents)

    private fun reasonFor(frame: SensorFrame, current: CaregiverAlertStatus): AlertReason =
        when {
            frame.sosActive -> AlertReason.SosButton
            frame.fallRisk == RiskStatus.High -> AlertReason.FallDetected
            frame.heartRateBpm > 130 -> AlertReason.HeartRateHigh
            frame.heartRateBpm < 40 -> AlertReason.HeartRateLow
            frame.spo2Percent < 90f -> AlertReason.LowSpO2
            frame.inactivityMinutes > 20 -> AlertReason.Inactivity
            current == CaregiverAlertStatus.Normal -> AlertReason.Resolved
            else -> AlertReason.DeviceAlert
        }
}
