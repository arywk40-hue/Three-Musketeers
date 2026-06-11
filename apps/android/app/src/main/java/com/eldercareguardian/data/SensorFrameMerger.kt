package com.eldercareguardian.data

import com.eldercareguardian.ble.SmartSuitBleTelemetry
import com.eldercareguardian.ml.CaregiverAlertPolicy
import com.eldercareguardian.ml.DehydrationRiskModel
import com.eldercareguardian.ml.EcgAnomalyDetector
import com.eldercareguardian.ml.FallConfirmationBuffer
import com.eldercareguardian.ml.FallDetectionEngine
import com.eldercareguardian.ml.HeartRateExtractor
import com.eldercareguardian.ml.InactivityMonitor
import com.eldercareguardian.ml.OverexertionModel
import com.eldercareguardian.ml.VitalsRiskMonitor
import kotlin.math.sqrt

object SensorFrameMerger {
    private val fallBuffer = FallConfirmationBuffer()
    private val fallDetectionEngine = FallDetectionEngine()
    private var inactivitySeconds = 0

    fun merge(base: SensorFrame, ble: SmartSuitBleTelemetry): SensorFrame {
        val hasBle = ble.heartRateBpm != null
        if (!hasBle) return base

        val heartRateBpm = ble.heartRateBpm ?: base.heartRateBpm
        val spo2Percent = ble.spo2Percent ?: base.spo2Percent
        val humidityPercent = ble.humidityPercent ?: base.humidityPercent
        val respiratoryRate = ble.respiratoryRate?.toInt() ?: base.respiratoryRate
        val ecgSamples = ble.ecgSamples.takeIf { it.size == 256 } ?: base.ecgSamples

        val fall = if (ble.wristImu.size >= 6) {
            fallDetectionEngine.assess(ble.wristImu)
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

        val heartRateFromEcg = if (ecgSamples.size == 256) {
            HeartRateExtractor.extractRrIntervals(ecgSamples)
        } else {
            null
        }
        val ecgAnomaly = if (ecgSamples.size == 256) {
            EcgAnomalyDetector.assess(ecgSamples, heartRateBpm)
        } else {
            null
        }
        val vitalsRisk = VitalsRiskMonitor.assess(heartRateBpm, spo2Percent, respiratoryRate, base.skinTempC)
        val dehydration = DehydrationRiskModel.assess(
            sweatRatePercentPerMin = base.sweatRatePercentPerMin,
            skinTempC = base.skinTempC,
            heartRateBpm = heartRateBpm,
        )
        val overexertion = OverexertionModel.assess(
            heartRateBpm = heartRateBpm,
            spo2Percent = spo2Percent,
            respiratoryRate = respiratoryRate,
            imuMagnitude = imuMagnitude,
        )

        val posture = when (fall?.riskStatus) {
            RiskStatus.High -> PostureStatus.Bad
            RiskStatus.Medium -> PostureStatus.Warning
            null -> base.posture
            RiskStatus.Low -> base.posture
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
            fatigue = overexertion.status,
            dehydration = dehydration.risk,
            vitalsRisk = vitalsRisk.risk,
            ecgAnomaly = ecgAnomaly?.status ?: base.ecgAnomaly,
            rrIntervalsMs = heartRateFromEcg?.rrIntervalsMs ?: base.rrIntervalsMs,
            imuMagnitude = imuMagnitude,
            hrReservePercent = overexertion.hrReservePercent,
            batteryPercent = ble.batteryPercent ?: base.batteryPercent,
        )

        val alert = CaregiverAlertPolicy.evaluate(merged)
        return merged.copy(caregiverAlert = alert)
    }
}
