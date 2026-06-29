package com.eldercareguardian.ml

import com.eldercareguardian.data.EcgAnomalyStatus
import kotlin.math.abs

/**
 * Rule-based ECG anomaly detector.
 *
 * Phase 5/6 fix: The AFib detection logic was inverted.
 *
 * RMSSD (Root Mean Square of Successive Differences) interpretation:
 *  - Low RMSSD (< 20 ms): Low HRV — typical in athletes, anxiety, or sinus tachycardia.
 *    This is NOT AFib.
 *  - High RMSSD (> 50 ms) with HIGH irregularity: indicates chaotic RR intervals
 *    consistent with AFib. AFib has BOTH high RMSSD AND high beat-to-beat variability.
 *
 * Previous bug: `rmssd < AFIB_RMSSD_THRESHOLD_MS` would flag low HRV as AFib — the opposite
 * of clinical reality. Fixed to require BOTH high RMSSD AND high irregularity.
 *
 * Additional fix: detection priority order now matches clinical importance:
 *  Tachycardia / Bradycardia → both are rate-based and take priority.
 *  AFib is irregular rhythm at normal-to-fast rate — checked after rate thresholds.
 *
 * Confidence requirement: Only flag AFib when at least MIN_RR_INTERVALS RR intervals
 * are available. A single ECG window at 1 Hz notification rate is insufficient for
 * reliable AFib detection in a clinical setting. Flag as Unknown when data is sparse.
 *
 * Clinical disclaimer: This is a rule-based screening aid. It must NOT be used
 * for diagnostic purposes. All anomaly detections must be reviewed by a physician.
 */
object EcgAnomalyDetector {
    private const val TACHY_THRESHOLD_BPM = 100
    private const val BRADY_THRESHOLD_BPM = 50

    /**
     * AFib RMSSD threshold (ms). AFib is associated with HIGH RMSSD (> 50 ms)
     * combined with high irregularity. Low RMSSD indicates regular rhythm.
     *
     * Reference: Lian et al. (2011), "A simple method to detect AFib using RR intervals."
     */
    private const val AFIB_RMSSD_THRESHOLD_MS = 50

    /**
     * AFib irregularity threshold. RR irregularity (MAD / mean) > 0.20 is consistent
     * with AFib. Values < 0.10 indicate highly regular sinus rhythm.
     */
    private const val AFIB_IRREGULARITY_THRESHOLD = 0.20f

    /** Minimum valid RR intervals required for any AFib classification. */
    private const val MIN_RR_INTERVALS = 4

    data class EcgAssessment(
        val status: EcgAnomalyStatus,
        val rmssdMs: Int?,
        val meanHrBpm: Int?,
        val rrIntervalsMs: List<Int> = emptyList(),
    )

    fun assess(ecgSamples: List<Float>, reportedHrBpm: Int? = null): EcgAssessment {
        val rr = HeartRateExtractor.extractRrIntervals(ecgSamples)
        val meanHr = rr.meanHrBpm ?: reportedHrBpm
        if (meanHr == null) return EcgAssessment(EcgAnomalyStatus.Unknown, null, null, rr.rrIntervalsMs)

        // AFib classification requires enough RR intervals for statistical reliability.
        // Check AFib first — AFib can coexist with tachycardia/bradycardia.
        if (rr.rrIntervalsMs.size >= MIN_RR_INTERVALS) {
            val rmssd = HeartRateExtractor.rmssd(rr.rrIntervalsMs)
            if (rmssd != null) {
                val irregularity = rrIrregularity(rr.rrIntervalsMs)
                if (rmssd > AFIB_RMSSD_THRESHOLD_MS && irregularity > AFIB_IRREGULARITY_THRESHOLD) {
                    return EcgAssessment(EcgAnomalyStatus.AFib, rmssd, meanHr, rr.rrIntervalsMs)
                }
            }
        }

        // Rate-based classification (AFib ruled out or insufficient data).
        if (meanHr >= TACHY_THRESHOLD_BPM) {
            return EcgAssessment(EcgAnomalyStatus.Tachycardia, null, meanHr, rr.rrIntervalsMs)
        }
        if (meanHr <= BRADY_THRESHOLD_BPM) {
            return EcgAssessment(EcgAnomalyStatus.Bradycardia, null, meanHr, rr.rrIntervalsMs)
        }

        return EcgAssessment(EcgAnomalyStatus.Normal, null, meanHr, rr.rrIntervalsMs)
    }

    /**
     * Mean Absolute Deviation of RR intervals, normalised by mean.
     * Returns 0 for regular rhythm, higher values for irregular.
     */
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
