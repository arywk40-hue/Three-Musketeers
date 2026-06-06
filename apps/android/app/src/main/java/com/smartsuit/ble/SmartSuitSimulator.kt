package com.smartsuit.ble

import com.smartsuit.data.FatigueStatus
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
        var reps = 0

        while (true) {
            tick += 1
            if (tick % 4 == 0) reps += 1

            val workoutWave = sin(tick / 6.0)
            val ecg = List(ECG_SAMPLE_COUNT) { index ->
                val x = index / ECG_SAMPLE_COUNT.toDouble()
                val phase = (index + tick * 6) % ECG_BEAT_PERIOD_SAMPLES
                val qrs = if (phase in 3..8) 0.85f else 0f
                (0.08 * sin(2.0 * PI * x * 5.0) + qrs + Random.nextDouble(-0.03, 0.03)).toFloat()
            }

            emit(
                SensorFrame(
                    timestampMillis = System.currentTimeMillis(),
                    heartRateBpm = 76 + (workoutWave * 10).toInt(),
                    spo2Percent = 97.4f + Random.nextDouble(-0.3, 0.2).toFloat(),
                    systolicMmHg = 118 + (workoutWave * 4).toInt(),
                    diastolicMmHg = 76 + (workoutWave * 2).toInt(),
                    skinTempC = 33.1f + Random.nextDouble(-0.2, 0.4).toFloat(),
                    humidityPercent = 52f + (tick % 18) * 0.7f,
                    respiratoryRate = 15 + (workoutWave * 2).toInt(),
                    reps = reps,
                    formScore = (8.5f + Random.nextDouble(-0.7, 0.4).toFloat()).coerceIn(0f, 10f),
                    posture = if (tick % 17 == 0) PostureStatus.Warning else PostureStatus.Good,
                    fatigue = if (tick % 29 == 0) FatigueStatus.Caution else FatigueStatus.Safe,
                    dehydration = if (tick > 42) RiskStatus.Medium else RiskStatus.Low,
                    tegPowerMw = 4.2f + Random.nextDouble(-0.4, 0.6).toFloat(),
                    solarPowerMw = 18.0f + Random.nextDouble(-2.0, 2.4).toFloat(),
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
