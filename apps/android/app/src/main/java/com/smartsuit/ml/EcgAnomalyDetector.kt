package com.smartsuit.ml

import com.smartsuit.data.EcgAnomalyStatus
import kotlin.math.abs

object EcgAnomalyDetector {
    private const val TACHY_THRESHOLD_BPM = 100
    private const val BRADY_THRESHOLD_BPM = 50
    private const val AFIB_RMSSD_THRESHOLD_MS = 25
    private const val AFIB_IRREGULARITY_THRESHOLD = 0.30f
    private const val MIN_RR_INTERVALS = 4

    data class EcgAssessment(
        val status: EcgAnomalyStatus,
        val rmssdMs: Int?,
        val meanHrBpm: Int?,
    )

    fun assess(ecgSamples: List<Float>, reportedHrBpm: Int? = null): EcgAssessment {
        val rr = HeartRateExtractor.extractRrIntervals(ecgSamples)
        val meanHr = rr.meanHrBpm ?: reportedHrBpm
        if (meanHr == null) return EcgAssessment(EcgAnomalyStatus.Unknown, null, null)
        if (rr.rrIntervalsMs.size < MIN_RR_INTERVALS) {
            return when {
                meanHr >= TACHY_THRESHOLD_BPM -> EcgAssessment(EcgAnomalyStatus.Tachycardia, null, meanHr)
                meanHr <= BRADY_THRESHOLD_BPM -> EcgAssessment(EcgAnomalyStatus.Bradycardia, null, meanHr)
                else -> EcgAssessment(EcgAnomalyStatus.Normal, null, meanHr)
            }
        }

        // Past the early return we are guaranteed rr.rrIntervalsMs.size >= MIN_RR_INTERVALS >= 2,
        // so rmssd() will not return null. No need to re-check.
        val rmssd = HeartRateExtractor.rmssd(rr.rrIntervalsMs)!!
        val irregularity = rrIrregularity(rr.rrIntervalsMs)
        val status = when {
            meanHr >= TACHY_THRESHOLD_BPM -> EcgAnomalyStatus.Tachycardia
            meanHr <= BRADY_THRESHOLD_BPM -> EcgAnomalyStatus.Bradycardia
            rmssd < AFIB_RMSSD_THRESHOLD_MS || irregularity > AFIB_IRREGULARITY_THRESHOLD ->
                EcgAnomalyStatus.AFib
            else -> EcgAnomalyStatus.Normal
        }
        return EcgAssessment(status, rmssd, meanHr)
    }

    private fun rrIrregularity(rrIntervalsMs: List<Int>): Float {
        if (rrIntervalsMs.size < 2) return 0f
        val mean = rrIntervalsMs.average()
        if (mean <= 0.0) return 0f
        var sum = 0.0
        for (v in rrIntervalsMs) sum += abs(v - mean)
        val mad = sum / rrIntervalsMs.size
        return (mad / mean).toFloat()
    }
}
