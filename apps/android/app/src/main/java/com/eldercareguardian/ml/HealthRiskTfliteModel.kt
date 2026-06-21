package com.eldercareguardian.ml

import android.content.Context
import android.util.Log
import com.eldercareguardian.data.FatigueStatus
import com.eldercareguardian.data.RiskStatus
import com.eldercareguardian.data.SensorFrame
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Unified health risk TFLite model.
 *
 * Input:  float32[1][7]
 *   [heart_rate_bpm, spo2_percent, respiratory_rate, skin_temp_c,
 *    sweat_rate_pct_per_min, imu_magnitude, hr_reserve_pct]
 *   — each feature normalised with mean/std from training (StandardScaler).
 *
 * Output: float32[1][9]  — concat of 3 softmax heads, each 3 classes:
 *   [0..2]  vitals_risk    : Low / Medium / High
 *   [3..5]  dehydration    : Low / Medium / High
 *   [6..8]  overexertion   : Safe / Caution / Stop
 *
 * Falls back to [VitalsRiskMonitor], [DehydrationRiskModel], [OverexertionModel]
 * rule engines when model is not loaded.
 */
class HealthRiskTfliteModel(context: Context, modelName: String = "health_risk.tflite") {

    companion object {
        private const val TAG = "HealthRiskTFLite"

        // StandardScaler means/stds from synthetic training data (update after real training)
        // Order: hr, spo2, rr, temp, sweat, imu, hrr
        private val MEANS = floatArrayOf(97.5f, 91.0f, 17.5f, 36.5f, 1.5f, 12.5f, 50.0f)
        private val STDS  = floatArrayOf(36.0f,  5.2f,  7.2f,  2.0f, 0.9f,  7.2f, 29.0f)
    }

    private var interpreter: Interpreter? = null
    val isReady: Boolean get() = interpreter != null

    init {
        try {
            val fd = context.assets.openFd(modelName)
            val buf = fd.createInputStream().use { it.readBytes() }
            val bb = ByteBuffer.allocateDirect(buf.size).also { it.put(buf); it.rewind() }
            interpreter = Interpreter(bb)
            Log.i(TAG, "Loaded $modelName")
        } catch (e: Exception) {
            Log.w(TAG, "health_risk.tflite not found — using rule engines. ${e.message}")
        }
    }

    data class HealthRiskResult(
        val vitalsRisk: RiskStatus,
        val dehydration: RiskStatus,
        val overexertion: FatigueStatus,
        val fromTflite: Boolean,
    )

    fun assess(frame: SensorFrame, patientAgeYears: Int = 70): HealthRiskResult {
        val interp = interpreter
        if (interp == null) return fallback(frame, patientAgeYears)

        return try {
            val input = buildInput(frame)
            val output = Array(1) { FloatArray(9) }
            interp.run(input, output)
            val logits = output[0]

            HealthRiskResult(
                vitalsRisk   = argmax3toRisk(logits, 0),
                dehydration  = argmax3toRisk(logits, 3),
                overexertion = argmax3toFatigue(logits, 6),
                fromTflite   = true,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}")
            fallback(frame, patientAgeYears)
        }
    }

    private fun buildInput(frame: SensorFrame): Array<FloatArray> {
        val raw = floatArrayOf(
            frame.heartRateBpm.toFloat(),
            frame.spo2Percent,
            frame.respiratoryRate.toFloat(),
            frame.skinTempC,
            frame.sweatRatePercentPerMin,
            frame.imuMagnitude,
            frame.hrReservePercent.toFloat(),
        )
        val norm = FloatArray(raw.size) { i -> (raw[i] - MEANS[i]) / STDS[i].coerceAtLeast(1e-6f) }
        val buf = ByteBuffer.allocateDirect(norm.size * 4).order(ByteOrder.nativeOrder())
        norm.forEach { buf.putFloat(it) }
        buf.rewind()
        return Array(1) { norm }
    }

    private fun argmax3toRisk(logits: FloatArray, offset: Int): RiskStatus {
        val idx = (offset until offset + 3).maxByOrNull { logits[it] }!! - offset
        return when (idx) { 2 -> RiskStatus.High; 1 -> RiskStatus.Medium; else -> RiskStatus.Low }
    }

    private fun argmax3toFatigue(logits: FloatArray, offset: Int): FatigueStatus {
        val idx = (offset until offset + 3).maxByOrNull { logits[it] }!! - offset
        return when (idx) { 2 -> FatigueStatus.Stop; 1 -> FatigueStatus.Caution; else -> FatigueStatus.Safe }
    }

    private fun fallback(frame: SensorFrame, ageYears: Int): HealthRiskResult {
        val v = VitalsRiskMonitor.assess(frame.heartRateBpm, frame.spo2Percent, frame.respiratoryRate, frame.skinTempC)
        val d = DehydrationRiskModel.assess(frame.sweatRatePercentPerMin, frame.skinTempC, frame.heartRateBpm)
        val o = OverexertionModel.assess(frame.heartRateBpm, frame.spo2Percent, frame.respiratoryRate, frame.imuMagnitude, ageYears)
        return HealthRiskResult(v.risk, d.risk, o.status, fromTflite = false)
    }
}
