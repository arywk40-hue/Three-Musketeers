package com.smartsuit.ml

import com.smartsuit.data.RiskStatus
import kotlin.math.ceil

class FallConfirmationBuffer(
    private val spikeWindowSec: Int = 2,
    private val frameIntervalMs: Long = 900,
) {
    private val windowCapacity: Int = maxOf(1, ceil(spikeWindowSec * 1000.0 / frameIntervalMs).toInt())
    private val recent: ArrayDeque<RiskStatus> = ArrayDeque(windowCapacity)

    fun assess(latest: FallDetectionEngine.FallAssessment, sosActive: Boolean): RiskStatus {
        if (recent.size == windowCapacity) {
            recent.removeFirst()
        }
        recent.addLast(latest.riskStatus)

        if (sosActive) return RiskStatus.High

        val hasHigh = recent.any { it == RiskStatus.High }
        val hasMedium = recent.any { it == RiskStatus.Medium }
        if (hasHigh && hasMedium) return RiskStatus.High

        if (latest.riskStatus == RiskStatus.High) return RiskStatus.Medium

        return latest.riskStatus
    }
}
