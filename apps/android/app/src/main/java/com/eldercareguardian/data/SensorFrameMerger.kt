package com.eldercareguardian.data

import com.eldercareguardian.ble.SmartSuitBleTelemetry
import com.eldercareguardian.ml.CaregiverAlertPolicy
import com.eldercareguardian.ml.FallConfirmationBuffer
import com.eldercareguardian.ml.FallDetectionEngine
import com.eldercareguardian.ml.FallDetectionTfliteModel
import com.eldercareguardian.ml.HealthRiskPipeline
import com.eldercareguardian.ml.InactivityMonitor
import kotlin.math.abs
import kotlin.math.sqrt

object SensorFrameMerger {
    private val fallBuffer = FallConfirmationBuffer()
    private val fallDetectionEngine = FallDetectionEngine
    private var inactivitySeconds = 0

    fun reset() {
        inactivitySeconds = 0
        fallBuffer.reset()
    }

    fun merge(base: SensorFrame, ble: SmartSuitBleTelemetry, tfliteModel: FallDetectionTfliteModel? = null, patientAgeYears: Int = 70): SensorFrame {
        val hasBle = ble.heartRateBpm != null
        if (!hasBle) return base

        val heartRateBpm = ble.heartRateBpm ?: base.heartRateBpm
        val spo2Percent = ble.spo2Percent ?: base.spo2Percent
        val humidityPercent = ble.humidityPercent ?: base.humidityPercent
        val respiratoryRate = ble.respiratoryRate?.toInt() ?: base.respiratoryRate
        val ecgSamples = ble.ecgSamples.takeIf { it.size == 256 } ?: base.ecgSamples

        val fall = if (ble.wristImu.size >= 6) {
            tfliteModel?.assess(ble.wristImu)?.takeIf { it.riskScore >= 0.5f }
                ?: fallDetectionEngine.assess(ble.wristImu)
        } else {
            null
        }
        val confirmedFallRisk = fall?.let { fallBuffer.assess(it, ble.sosState) }
        val imuMagnitude = if (ble.wristImu.size >= 6) {
            val a = ble.wristImu
            sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
        } else {
            base.imuMagnitude
        }
        val isFallActive = fall?.riskStatus == RiskStatus.High || fall?.riskStatus == RiskStatus.Medium
        inactivitySeconds = InactivityMonitor.assess(imuMagnitude, inactivitySeconds, isFallActive)

        val assessment = if (ecgSamples.size == 256) {
            HealthRiskPipeline.assess(
                ecgSamples = ecgSamples,
                heartRateBpm = heartRateBpm,
                spo2Percent = spo2Percent,
                respiratoryRate = respiratoryRate,
                skinTempC = base.skinTempC,
                sweatRatePercentPerMin = base.sweatRatePercentPerMin,
                imuMagnitude = imuMagnitude,
                patientAgeYears = patientAgeYears,
            )
        } else {
            null
        }

        val posture = when (fall?.riskStatus) {
            RiskStatus.High -> PostureStatus.Bad
            RiskStatus.Medium -> PostureStatus.Warning
            null -> base.posture
            RiskStatus.Low -> base.posture
        }

        val spo2Quality = when {
            ble.spo2Percent == null -> Spo2Quality.NoSignal
            abs(imuMagnitude - 9.81f) > 3.0f -> Spo2Quality.Unreliable
            else -> Spo2Quality.Reliable
        }

        val merged = base.copy(
            heartRateBpm = heartRateBpm,
            spo2Percent = spo2Percent,
            humidityPercent = humidityPercent,
            respiratoryRate = respiratoryRate,
            ecgSamples = ecgSamples,
            sosActive = ble.sosState,
            inactivityMinutes = InactivityMonitor.toMinutes(inactivitySeconds),
            fallRisk = confirmedFallRisk ?: base.fallRisk,
            posture = posture,
            fatigue = assessment?.overexertion.status ?: base.fatigue,
            dehydration = assessment?.dehydration.risk ?: base.dehydration,
            vitalsRisk = assessment?.vitals.risk ?: base.vitalsRisk,
            ecgAnomaly = assessment?.ecg.status ?: base.ecgAnomaly,
            rrIntervalsMs = assessment?.ecg.rrIntervalsMs ?: base.rrIntervalsMs,
            imuMagnitude = imuMagnitude,
            hrReservePercent = assessment?.overexertion.hrReservePercent ?: base.hrReservePercent,
            batteryPercent = ble.batteryPercent ?: base.batteryPercent,
            spo2Quality = spo2Quality,
        )

        val alert = CaregiverAlertPolicy.evaluate(merged)
        return merged.copy(caregiverAlert = alert)
    }
}
