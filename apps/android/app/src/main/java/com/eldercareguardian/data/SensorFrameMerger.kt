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

        return buildFromTelemetry(ble, tfliteModel, patientAgeYears)!!
    }

    fun buildFromTelemetry(ble: SmartSuitBleTelemetry, tfliteModel: FallDetectionTfliteModel? = null, patientAgeYears: Int = 70): SensorFrame? {
        val heartRateBpm = ble.heartRateBpm ?: return null
        val spo2Percent = ble.spo2Percent ?: 0f
        val respiratoryRate = ble.respiratoryRate?.toInt() ?: 0
        val ecgSamples = ble.ecgSamples.takeIf { it.size == 256 } ?: emptyList()

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
            0f
        }
        val isFallActive = fall?.riskStatus == RiskStatus.High || fall?.riskStatus == RiskStatus.Medium
        inactivitySeconds = InactivityMonitor.assess(imuMagnitude, inactivitySeconds, isFallActive)

        val assessment = if (ecgSamples.size == 256) {
            HealthRiskPipeline.assess(
                ecgSamples = ecgSamples,
                heartRateBpm = heartRateBpm,
                spo2Percent = spo2Percent,
                respiratoryRate = respiratoryRate,
                skinTempC = 0f,
                sweatRatePercentPerMin = 0f,
                imuMagnitude = imuMagnitude,
                patientAgeYears = patientAgeYears,
            )
        } else {
            null
        }

        val posture = when (fall?.riskStatus) {
            RiskStatus.High -> PostureStatus.Bad
            RiskStatus.Medium -> PostureStatus.Warning
            else -> PostureStatus.Good
        }

        val spo2Quality = when {
            ble.spo2Percent == null -> Spo2Quality.NoSignal
            abs(imuMagnitude - 9.81f) > 3.0f -> Spo2Quality.Unreliable
            else -> Spo2Quality.Reliable
        }

        val frame = SensorFrame(
            timestampMillis = System.currentTimeMillis(),
            heartRateBpm = heartRateBpm,
            spo2Percent = spo2Percent,
            systolicMmHg = assessment?.bloodPressure?.systolicMmHg ?: 0,
            diastolicMmHg = assessment?.bloodPressure?.diastolicMmHg ?: 0,
            skinTempC = 0f,
            humidityPercent = ble.humidityPercent ?: 0f,
            respiratoryRate = respiratoryRate,
            posture = posture,
            fatigue = assessment?.overexertion?.status ?: FatigueStatus.Safe,
            dehydration = assessment?.dehydration?.risk ?: RiskStatus.Low,
            fallRisk = confirmedFallRisk ?: RiskStatus.Low,
            caregiverAlert = CaregiverAlertStatus.Normal,
            sosActive = ble.sosState,
            inactivityMinutes = InactivityMonitor.toMinutes(inactivitySeconds),
            supercapPercent = ble.batteryPercent ?: 0,
            ecgSamples = ecgSamples,
            ecgAnomaly = assessment?.ecg?.status ?: EcgAnomalyStatus.Unknown,
            vitalsRisk = assessment?.vitals?.risk ?: RiskStatus.Low,
            rrIntervalsMs = assessment?.ecg?.rrIntervalsMs ?: emptyList(),
            imuMagnitude = imuMagnitude,
            spo2Quality = spo2Quality,
            sweatRatePercentPerMin = 0f,
            hrReservePercent = assessment?.overexertion?.hrReservePercent ?: 0,
            bpEstimated = assessment?.bloodPressure?.isEstimated ?: true,
            batteryPercent = ble.batteryPercent,
        )

        val alert = CaregiverAlertPolicy.evaluate(frame)
        return frame.copy(caregiverAlert = alert)
    }
}
