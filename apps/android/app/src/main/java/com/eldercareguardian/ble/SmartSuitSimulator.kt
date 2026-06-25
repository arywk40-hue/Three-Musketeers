package com.eldercareguardian.ble

import com.eldercareguardian.data.CaregiverAlertStatus
import com.eldercareguardian.data.FatigueStatus
import com.eldercareguardian.data.PostureStatus
import com.eldercareguardian.data.RiskStatus
import com.eldercareguardian.data.SensorFrame
import com.eldercareguardian.data.SmartSuitDataSource
import com.eldercareguardian.ml.CaregiverAlertPolicy
import com.eldercareguardian.ml.FallConfirmationBuffer
import com.eldercareguardian.ml.FallDetectionEngine
import com.eldercareguardian.ml.HealthRiskPipeline
import com.eldercareguardian.ml.InactivityMonitor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SmartSuitSimulator : SmartSuitDataSource {
    private val fallBuffer = FallConfirmationBuffer()
    private val fallDetectionEngine = FallDetectionEngine

    override val frames: Flow<SensorFrame> = flow {
        var tick = 0
        var inactivitySeconds = 0

        while (true) {
            tick += 1

            val dailyWave = sin(tick / 6.0)
            val baselineHr = 72.0 + dailyWave * 6.0
            val heartRateBpm = (baselineHr + Random.nextDouble(-2.0, 2.0)).toInt()
            val spo2Percent = (97.0f + Random.nextDouble(-0.4, 0.3).toFloat()).coerceIn(85f, 100f)
            val skinTempC = (33.0f + (dailyWave * 0.4).toFloat() + Random.nextDouble(-0.2, 0.4).toFloat())
                .coerceIn(31f, 39f)
            val respiratoryRate = (15 + (dailyWave * 2).toInt() + Random.nextInt(-1, 2))
                .coerceIn(6, 30)
            val humidityPercent = (45f + (tick % 18) * 0.5f).coerceIn(20f, 90f)
            val sweatRatePercentPerMin = (0.2f + (dailyWave * 0.4).toFloat() + Random.nextDouble(-0.1, 0.2).toFloat())
                .coerceIn(0f, 3f)

            val ax = (0.18f * sin(tick * 0.27)).toFloat()
            val ay = (0.15f * cos(tick * 0.21)).toFloat()
            val az = (9.81f + 0.20f * sin(tick * 0.13)).toFloat()
            val gx = (1.2f * sin(tick * 0.11)).toFloat()
            val gy = (1.0f * cos(tick * 0.09)).toFloat()
            val gz = (0.8f * sin(tick * 0.07)).toFloat()
            val imuMagnitude = InactivityMonitor.magnitude(ax, ay, az)

            val fallHighWindow = tick % 47 in 41..46
            val fallMediumWindow = tick % 19 in 15..18
            val simulatedWristImu = if (fallHighWindow) {
                listOf(22f, 1.2f, 5.1f, 90f, 12f, 5f)
            } else if (fallMediumWindow) {
                listOf(15f, 1.0f, 7.8f, 30f, 8f, 4f)
            } else {
                listOf(ax, ay, az, gx, gy, gz)
            }
            val fall = fallDetectionEngine.assess(simulatedWristImu)
            val sosActive = tick % 53 in 49..52
            val confirmedFallRisk = fallBuffer.assess(fall, sosActive)
            inactivitySeconds = InactivityMonitor.assess(imuMagnitude, inactivitySeconds)

            val ecgSamples = buildEcgWindow(tick, heartRateBpm)
            val assessment = HealthRiskPipeline.assess(
                ecgSamples = ecgSamples,
                heartRateBpm = heartRateBpm,
                spo2Percent = spo2Percent,
                respiratoryRate = respiratoryRate,
                skinTempC = skinTempC,
                sweatRatePercentPerMin = sweatRatePercentPerMin,
                imuMagnitude = imuMagnitude,
                patientAgeYears = 70,
            )

            val posture = when (fall.riskStatus) {
                RiskStatus.High -> PostureStatus.Bad
                RiskStatus.Medium -> PostureStatus.Warning
                RiskStatus.Low -> PostureStatus.Good
            }

            val frame = SensorFrame(
                timestampMillis = System.currentTimeMillis(),
                heartRateBpm = heartRateBpm,
                spo2Percent = spo2Percent,
                systolicMmHg = assessment.bloodPressure.systolicMmHg,
                diastolicMmHg = assessment.bloodPressure.diastolicMmHg,
                skinTempC = skinTempC,
                humidityPercent = humidityPercent,
                respiratoryRate = respiratoryRate,
                posture = posture,
                fatigue = assessment.overexertion.status,
                dehydration = assessment.dehydration.risk,
                fallRisk = confirmedFallRisk,
                caregiverAlert = CaregiverAlertStatus.Normal,
                sosActive = sosActive,
                inactivityMinutes = InactivityMonitor.toMinutes(inactivitySeconds),
                supercapPercent = (72 + (sin(tick / 8.0) * 8).toInt()).coerceIn(0, 100),
                ecgSamples = ecgSamples,
                ecgAnomaly = assessment.ecg.status,
                vitalsRisk = assessment.vitals.risk,
                rrIntervalsMs = assessment.ecg.rrIntervalsMs,
                imuMagnitude = imuMagnitude,
                sweatRatePercentPerMin = sweatRatePercentPerMin,
                hrReservePercent = assessment.overexertion.hrReservePercent,
                bpEstimated = assessment.bloodPressure.isEstimated,
                batteryPercent = (90 - tick % 95).coerceIn(0, 100),
            )
            val caregiverAlerted = frame.copy(caregiverAlert = CaregiverAlertPolicy.evaluate(frame))
            emit(caregiverAlerted)
            delay(900)
        }
    }

    private fun buildEcgWindow(tick: Int, heartRateBpm: Int): List<Float> {
        val beatPeriod = (60.0 / heartRateBpm.coerceAtLeast(30)) * ECG_SAMPLE_RATE
        return List(ECG_SAMPLE_COUNT) { index ->
            val phase = (index + tick * 4) % beatPeriod.toInt()
            val qrs = if (phase in 3..8) 0.85f else 0f
            (0.10 * sin(2.0 * PI * index / ECG_SAMPLE_COUNT * 5.0) +
                qrs +
                Random.nextDouble(-0.03, 0.03)).toFloat()
        }
    }

    private companion object {
        const val ECG_SAMPLE_COUNT = 256
        const val ECG_SAMPLE_RATE = 256
    }
}
