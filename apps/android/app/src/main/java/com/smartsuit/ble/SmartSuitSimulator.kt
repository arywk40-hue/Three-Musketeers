package com.smartsuit.ble

import com.smartsuit.data.FatigueStatus
import com.smartsuit.data.CaregiverAlertStatus
import com.smartsuit.data.PostureStatus
import com.smartsuit.data.RiskStatus
import com.smartsuit.data.SensorFrame
import com.smartsuit.data.SmartSuitDataSource
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SmartSuitSimulator : SmartSuitDataSource {
    override val frames: Flow<SensorFrame> = flow {
        var tick = 0

        while (true) {
            tick += 1

            val dailyWave = sin(tick / 6.0)
            val fallRisk = when {
                tick % 37 in 32..36 -> RiskStatus.High
                tick % 19 in 15..18 -> RiskStatus.Medium
                else -> RiskStatus.Low
            }
            val sosActive = tick % 41 in 38..40
            val caregiverAlert = when {
                sosActive || fallRisk == RiskStatus.High -> CaregiverAlertStatus.Urgent
                fallRisk == RiskStatus.Medium || tick % 23 in 19..22 -> CaregiverAlertStatus.Check
                else -> CaregiverAlertStatus.Normal
            }
            val ecg = List(ECG_SAMPLE_COUNT) { index ->
                val x = index / ECG_SAMPLE_COUNT.toDouble()
                val phase = (index + tick * 6) % ECG_BEAT_PERIOD_SAMPLES
                val qrs = if (phase in 3..8) 0.85f else 0f
                (0.08 * sin(2.0 * PI * x * 5.0) + qrs + Random.nextDouble(-0.03, 0.03)).toFloat()
            }

            emit(
                SensorFrame(
                    timestampMillis = System.currentTimeMillis(),
                    heartRateBpm = 76 + (dailyWave * 8).toInt(),
                    spo2Percent = 97.4f + Random.nextDouble(-0.3, 0.2).toFloat(),
                    systolicMmHg = 124 + (dailyWave * 5).toInt(),
                    diastolicMmHg = 78 + (dailyWave * 2).toInt(),
                    skinTempC = 33.1f + Random.nextDouble(-0.2, 0.4).toFloat(),
                    humidityPercent = 49f + (tick % 18) * 0.4f,
                    respiratoryRate = 15 + (dailyWave * 2).toInt(),
                    posture = if (fallRisk == RiskStatus.High) PostureStatus.Bad else if (fallRisk == RiskStatus.Medium) PostureStatus.Warning else PostureStatus.Good,
                    fatigue = if (caregiverAlert == CaregiverAlertStatus.Urgent) FatigueStatus.Stop else if (caregiverAlert == CaregiverAlertStatus.Check) FatigueStatus.Caution else FatigueStatus.Safe,
                    dehydration = if (tick > 42) RiskStatus.Medium else RiskStatus.Low,
                    fallRisk = fallRisk,
                    caregiverAlert = caregiverAlert,
                    sosActive = sosActive,
                    inactivityMinutes = if (tick % 31 > 24) tick % 31 else 0,
                    supercapPercent = (72 + (sin(tick / 8.0) * 8).toInt()).coerceIn(0, 100),
                    ecgSamples = ecg,
                )
            )

            delay(900)
        }
    }

    private companion object {
        const val ECG_SAMPLE_COUNT = 256
        const val ECG_BEAT_PERIOD_SAMPLES = 205
    }
}
