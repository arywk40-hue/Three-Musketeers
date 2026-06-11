package com.eldercareguardian.ml

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.FileInputStream

/**
 * State of the TFLite model loader, analogous to [com.eldercareguardian.samsung.SamsungHealthState].
 *
 *  - [Unavailable]: TFLite runtime classes are not on the classpath. Add
 *    `org.tensorflow:tensorflow-lite` to build.gradle.kts to resolve.
 *  - [NoModel]: TFLite is available but no `.tflite` file found in assets for
 *    the requested model name. Drop the file into `src/main/assets/`.
 *  - [Ready]: Model loaded and ready for inference.
 *  - [Error]: Model loaded but inference failed. Check [lastErrorMessage].
 */
enum class TfLiteModelState {
    Unavailable,
    NoModel,
    Ready,
    Error,
}

/**
 * Scaffold for future TFLite model inference.
 *
 * Each method returns null when no model is loaded, so callers fall back to
 * the rule-based engine transparently.
 */
interface TfLiteFallbackLoader {
    val state: StateFlow<TfLiteModelState>

    /** Human-readable error from the last failed operation. */
    val lastErrorMessage: String?

    /**
     * Load a `.tflite` model from assets.
     * @param modelName filename in assets (e.g. "fall_detection.tflite").
     */
    suspend fun loadModel(modelName: String)

    /**
     * Run inference on a float array and return the result, or null if
     * the model is not loaded / inference fails.
     */
    suspend fun classify(input: FloatArray): FloatArray?

    /** True when a model is loaded and ready for inference. */
    fun isReady(): Boolean
}

/**
 * Real TFLite loader that accesses [org.tensorflow.lite.Interpreter] via
 * reflection so the module compiles even when the TFLite dependency is absent
 * from build.gradle.kts.
 *
 * When the dependency IS present, [loadModel] reads the `.tflite` file from
 * assets, creates an Interpreter, and transitions to [Ready].
 */
class RealTfLiteFallbackLoader(
    private val context: Context,
) : TfLiteFallbackLoader {

    private val _state = MutableStateFlow(checkTfLiteAvailability())
    override val state: StateFlow<TfLiteModelState> = _state

    @Volatile
    override var lastErrorMessage: String? = null
        private set

    // Reflection handles for org.tensorflow.lite.Interpreter
    private var interpreterCls: Class<*>? = null
    private var interpreter: Any? = null
    private var loadedModelName: String? = null

    private fun checkTfLiteAvailability(): TfLiteModelState {
        return try {
            interpreterCls = Class.forName("org.tensorflow.lite.Interpreter")
            TfLiteModelState.NoModel
        } catch (_: ClassNotFoundException) {
            TfLiteModelState.Unavailable
        }
    }

    override suspend fun loadModel(modelName: String) {
        val interpCls = interpreterCls ?: run {
            _state.value = TfLiteModelState.Unavailable
            return
        }

        try {
            // Close previous interpreter
            if (interpreter != null) {
                try {
                    interpreterCls!!.getMethod("close").invoke(interpreter)
                } catch (_: Exception) {}
                interpreter = null
                loadedModelName = null
            }

            // Read model file from assets
            val buffer = context.assets.open(modelName).use { input ->
                input.readBytes()
            }

            // NIO ByteBuffer for Interpreter
            val byteBuffer = java.nio.ByteBuffer.allocateDirect(buffer.size)
            byteBuffer.put(buffer)
            byteBuffer.rewind()

            // new Interpreter(ByteBuffer)
            interpreter = interpCls.getConstructor(java.nio.ByteBuffer::class.java)
                .newInstance(byteBuffer)

            loadedModelName = modelName
            _state.value = TfLiteModelState.Ready
            lastErrorMessage = null
        } catch (e: java.io.FileNotFoundException) {
            _state.value = TfLiteModelState.NoModel
            lastErrorMessage = "Model file '$modelName' not found in assets"
        } catch (e: Exception) {
            _state.value = TfLiteModelState.Error
            lastErrorMessage = e.message
        }
    }

    override suspend fun classify(input: FloatArray): FloatArray? {
        val interp = interpreter ?: return null
        val interpCls = interpreterCls ?: return null

        try {
            // Determine output shape from the loaded model
            val outputIdxMethod = interpCls.getMethod("getOutputIndex", String::class.java)
            val outputTensorCls = Class.forName("org.tensorflow.lite.Tensor")
            val getOutputTensorMethod = interpCls.getMethod("getOutputTensor", Int::class.javaPrimitiveType)
            val shapeMethod = outputTensorCls.getMethod("shape")
            val numBytesMethod = outputTensorCls.getMethod("numBytes")

            val outputTensor = getOutputTensorMethod.invoke(interp, 0)
            val outputShape = shapeMethod.invoke(outputTensor) as IntArray
            val numOutputs = outputShape.fold(1) { acc, i -> acc * i }

            val outputBuffer = java.nio.ByteBuffer.allocateDirect(numOutputs * 4) // 4 bytes per float
            val inputBuffer = java.nio.ByteBuffer.allocateDirect(input.size * 4)
            inputBuffer.asFloatBuffer().put(input)
            inputBuffer.rewind()

            val runMethod = interpCls.getMethod(
                "run",
                Any::class.java,
                Any::class.java,
            )
            runMethod.invoke(interp, inputBuffer, outputBuffer)
            outputBuffer.rewind()

            val result = FloatArray(numOutputs)
            outputBuffer.asFloatBuffer().get(result)
            return result
        } catch (e: Exception) {
            lastErrorMessage = "Inference failed: ${e.message}"
            _state.value = TfLiteModelState.Error
            return null
        }
    }

    override fun isReady(): Boolean = _state.value == TfLiteModelState.Ready
}

/**
 * No-op loader used when TFLite runtime is absent.
 */
class NoOpTfLiteFallbackLoader : TfLiteFallbackLoader {
    private val _state = MutableStateFlow(TfLiteModelState.Unavailable)
    override val state: StateFlow<TfLiteModelState> = _state

    @Volatile
    override var lastErrorMessage: String? = null
        private set

    override suspend fun loadModel(modelName: String) {
        _state.value = TfLiteModelState.Unavailable
    }

    override suspend fun classify(input: FloatArray): FloatArray? = null

    override fun isReady(): Boolean = false
}

/**
 * Provider that returns a [RealTfLiteFallbackLoader] when the
 * `org.tensorflow.lite.Interpreter` class is on the runtime classpath,
 * or [NoOpTfLiteFallbackLoader] otherwise.
 *
 * Usage:
 * ```
 * val loader = TfLiteFallbackLoaderProvider.create(context)
 * loader.loadModel("fall_detection.tflite")
 * val result = loader.classify(floatArrayOf(ax, ay, az, gx, gy, gz))
 * if (result == null) {
 *     // fall back to FallDetectionEngine
 * }
 * ```
 */
object TfLiteFallbackLoaderProvider {
    fun create(context: Context): TfLiteFallbackLoader {
        return try {
            Class.forName("org.tensorflow.lite.Interpreter")
            RealTfLiteFallbackLoader(context)
        } catch (_: ClassNotFoundException) {
            NoOpTfLiteFallbackLoader()
        }
    }
}
