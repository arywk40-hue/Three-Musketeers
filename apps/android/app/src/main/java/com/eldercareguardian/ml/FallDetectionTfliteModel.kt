package com.eldercareguardian.ml

import android.content.Context
import android.util.Log
import com.eldercareguardian.data.RiskStatus
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * TFLite wrapper for the fall detection CNN model.
 *
 * The model expects a 2-second window of 3-axis accelerometer data at 200 Hz.
 * Input:  (1, 3, 400) float32 — 3 axes × 400 samples
 * Output: (1, 2) float32 — [P_NoFall, P_Fall]
 *
 * Maintains a circular buffer of IMU frames. Runs inference when the buffer
 * has at least WINDOW_SIZE samples.
 */
class FallDetectionTfliteModel(
    context: Context,
    modelName: String = "fall_detection.tflite",
) {
    companion object {
        private const val TAG = "FallDetectionTFLite"
        private const val WINDOW_SIZE = 400
        private const val NUM_AXES = 3
        private const val FALL_THRESHOLD = 0.5f
    }

    private var interpreter: Interpreter? = null
    private val imuBuffer = ArrayDeque<FloatArray>(WINDOW_SIZE)
    private val inputBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(1 * NUM_AXES * WINDOW_SIZE * 4)
        .order(ByteOrder.nativeOrder())
    private val outputArray = Array(1) { FloatArray(2) }

    /** True when the TFLite model is loaded and ready. */
    var isReady: Boolean = false
        private set

    init {
        try {
            val modelBuffer = context.assets.openFd(modelName).use { fd ->
                FileInputStream(fd.fileDescriptor).use { stream ->
                    stream.channel.map(
                        java.nio.channels.FileChannel.MapMode.READ_ONLY,
                        fd.startOffset,
                        fd.declaredLength,
                    )
                }
            }
            interpreter = Interpreter(modelBuffer)
            isReady = true
            Log.i(TAG, "Loaded model: $modelName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}")
        }
    }

    /**
     * Add an IMU frame. Once the buffer is full (WINDOW_SIZE samples),
     * run TFLite inference and return a FallAssessment.
     *
     * Returns null when:
     *  - Model is not loaded
     *  - Buffer is not yet full
     *  - Input has fewer than 3 elements
     *
     * @param imuFrame [ax, ay, az] or [ax, ay, az, gx, gy, gz]. Only first 3 axes used.
     */
    fun assess(imuFrame: List<Float>): FallDetectionEngine.FallAssessment? {
        if (!isReady || imuFrame.size < 3) return null

        imuBuffer.addLast(floatArrayOf(imuFrame[0], imuFrame[1], imuFrame[2]))

        // Sliding window: keep at most WINDOW_SIZE samples
        if (imuBuffer.size > WINDOW_SIZE) {
            imuBuffer.removeFirst()
        }

        if (imuBuffer.size < WINDOW_SIZE) return null

        return runInference()
    }

    private fun runInference(): FallDetectionEngine.FallAssessment {
        inputBuffer.rewind()
        val buf = inputBuffer.asFloatBuffer()

        for (axis in 0 until NUM_AXES) {
            for (sample in imuBuffer) {
                buf.put(sample[axis])
            }
        }

        outputArray[0].fill(0f)
        interpreter?.run(inputBuffer, outputArray)

        val probFall = outputArray[0][1]
        val riskScore = probFall
        val status = when {
            probFall >= FALL_THRESHOLD -> RiskStatus.High
            probFall >= FALL_THRESHOLD * 0.7f -> RiskStatus.Medium
            else -> RiskStatus.Low
        }

        val last = imuBuffer.last()
        val magnitude = kotlin.math.sqrt(
            (last[0] * last[0] + last[1] * last[1] + last[2] * last[2]).toDouble()
        ).toFloat()

        return FallDetectionEngine.FallAssessment(status, riskScore, magnitude)
    }

    fun reset() {
        imuBuffer.clear()
    }
}
