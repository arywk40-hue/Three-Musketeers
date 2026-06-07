package com.smartsuit.ml

import kotlin.math.abs
import kotlin.math.max

/**
 * Forward-looking R-peak detector with baseline-wander removal and adaptive threshold.
 *
 * Designed for AD8232-class single-lead ECG at ~256 Hz. The detector:
 *  1. Subtracts a trailing moving-mean baseline so a wandering baseline does not
 *     push the whole signal above or below the threshold.
 *  2. Uses an adaptive threshold (max-of-centered × 0.4, floored at [DEFAULT_THRESHOLD])
 *     so the detector still fires on low-amplitude patients.
 *  3. Refuses to register a peak at the trailing edge of the window — that would
 *     be a truncation artifact with no falling edge to confirm it.
 *  4. Enforces a refractory period (default 200 ms) to reject T-wave double-counts.
 *
 * Assumes the input signal is oriented with positive R-peaks. AD8232 hardware
 * typically inverts R-peaks; the upstream pipeline is expected to flip the
 * signal before it reaches this extractor.
 */
object HeartRateExtractor {
    private const val DEFAULT_SAMPLE_RATE_HZ = 256
    private const val DEFAULT_REFRACTORY_MS = 200
    private const val DEFAULT_THRESHOLD = 0.55f
    private const val ADAPTIVE_FRACTION = 0.4f
    private const val BASELINE_WINDOW_SEC = 0.5f

    data class RrResult(
        val rrIntervalsMs: List<Int>,
        val meanHrBpm: Int?,
    )

    fun extractRrIntervals(
        ecgSamples: List<Float>,
        sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
        threshold: Float = DEFAULT_THRESHOLD,
        refractoryMs: Int = DEFAULT_REFRACTORY_MS,
    ): RrResult {
        if (ecgSamples.size < sampleRateHz / 4) return RrResult(emptyList(), null)

        val refractorySamples = (refractoryMs * sampleRateHz) / 1000
        val sampleStepMs = 1000.0 / sampleRateHz

        val baselineWindow = max(1, (BASELINE_WINDOW_SEC * sampleRateHz).toInt())
        val centered = subtractBaseline(ecgSamples, baselineWindow)
        val maxCentered = centered.max()
        val adaptiveThreshold = max(threshold, maxCentered * ADAPTIVE_FRACTION)

        val rrIntervals = mutableListOf<Int>()
        var lastPeakIndex = -refractorySamples
        var prevValue = Float.NEGATIVE_INFINITY

        for (i in centered.indices) {
            val value = centered[i]
            val isRising = value > adaptiveThreshold && value >= prevValue
            val isLast = i + 1 >= centered.size
            // Forward peak: current sample is above the adaptive threshold, rising
            // from the previous sample, and the next sample is strictly lower.
            // We refuse to fire on the last sample of the window — there is no
            // falling edge to confirm it is a peak and not a truncation artifact.
            val isPeak = isRising && !isLast && centered[i + 1] < value
            val pastRefractory = (i - lastPeakIndex) >= refractorySamples

            if (isPeak && pastRefractory) {
                if (lastPeakIndex >= 0) {
                    val intervalMs = ((i - lastPeakIndex) * sampleStepMs).toInt()
                    if (intervalMs in 250..2000) rrIntervals.add(intervalMs)
                }
                lastPeakIndex = i
            }
            prevValue = value
        }

        if (rrIntervals.isEmpty()) {
            return RrResult(emptyList(), null)
        }

        val meanInterval = rrIntervals.average()
        val meanHr = if (meanInterval > 0) (60_000.0 / meanInterval).toInt() else null
        return RrResult(rrIntervals, meanHr)
    }

    fun rmssd(rrIntervalsMs: List<Int>): Int? {
        if (rrIntervalsMs.size < 2) return null
        var sumSquaredDiffs = 0.0
        for (i in 1 until rrIntervalsMs.size) {
            val diff = rrIntervalsMs[i] - rrIntervalsMs[i - 1]
            sumSquaredDiffs += diff.toDouble() * diff.toDouble()
        }
        val mean = sumSquaredDiffs / (rrIntervalsMs.size - 1)
        return kotlin.math.sqrt(mean).toInt()
    }

    @Suppress("unused")
    fun detectBaselineWander(ecgSamples: List<Float>): Float {
        if (ecgSamples.isEmpty()) return 0f
        var sum = 0.0
        for (s in ecgSamples) sum += abs(s)
        return (sum / ecgSamples.size).toFloat()
    }

    private fun subtractBaseline(samples: List<Float>, window: Int): FloatArray {
        val out = FloatArray(samples.size)
        var sum = 0f
        for (i in samples.indices) {
            sum += samples[i]
            if (i >= window) sum -= samples[i - window]
            val w = if (i < window) i + 1 else window
            out[i] = samples[i] - (sum / w)
        }
        return out
    }
}
