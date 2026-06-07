package com.smartsuit.ml

import kotlin.math.abs

object HeartRateExtractor {
    private const val DEFAULT_SAMPLE_RATE_HZ = 256
    private const val DEFAULT_REFRACTORY_MS = 200
    private const val DEFAULT_THRESHOLD = 0.55f

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
        val rrIntervals = mutableListOf<Int>()
        var lastPeakIndex = -refractorySamples
        var lastValidValue = 0f

        for (i in ecgSamples.indices) {
            val value = ecgSamples[i]
            val isRising = value > threshold && value >= lastValidValue
            val isPeak = isRising && (i + 1 >= ecgSamples.size || ecgSamples[i + 1] <= value)
            val pastRefractory = (i - lastPeakIndex) >= refractorySamples

            if (isPeak && pastRefractory) {
                if (lastPeakIndex >= 0) {
                    val intervalMs = ((i - lastPeakIndex) * sampleStepMs).toInt()
                    if (intervalMs in 250..2000) rrIntervals.add(intervalMs)
                }
                lastPeakIndex = i
            }
            lastValidValue = value
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
}
