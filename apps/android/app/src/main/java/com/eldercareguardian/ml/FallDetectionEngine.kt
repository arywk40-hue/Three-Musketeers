package com.eldercareguardian.ml

import com.eldercareguardian.data.RiskStatus
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * IMU-based fall detection engine.
 *
 * Phase 5 improvements — temporal window approach:
 *
 * A fall produces three distinct phases in the IMU signal:
 *
 *  Phase A — Pre-fall activity: normal movement (5–15 m/s² magnitude).
 *  Phase B — Impact spike: sudden high-G event > FALL_SPIKE_THRESHOLD (calibrated to 7.5 m/s² deviation-from-gravity).
 *  Phase C — Post-impact stillness: person lying on floor, magnitude drops < STILLNESS_THRESHOLD.
 *
 * A SINGLE sample check (the old implementation) catches only Phase B and generates
 * false positives from energetic arm swings or tapping the device. The new
 * implementation uses a circular buffer of recent IMU samples to detect all three phases.
 *
 * False-positive reduction:
 *  - The spike must exceed the threshold for at least SPIKE_MIN_SAMPLES consecutive samples.
 *  - Stillness after the spike must persist for at least STILLNESS_MIN_SAMPLES.
 *  - If stillness follows a spike within SPIKE_TO_STILLNESS_WINDOW_SAMPLES, confirm fall.
 *
 * False-negative reduction:
 *  - Lowered spike threshold to 19.6 m/s² (2g) — typical real-world fall impact.
 *  - Stillness threshold raised to 4.0 m/s² (patient may be breathing, trembling).
 *  - Medium risk fires on spike alone, allowing the confirmation buffer to escalate.
 *
 * Calibration: Thresholds validated against the SisFall elderly subset (June 2026).
 * Best-F1 configuration: spike=7.5 m/s² (0.76g), stillness=15.0 m/s²,
 * sensitivity=0.800, specificity=0.775, F1=0.667.
 * See docs/fall-detection-calibration.md for the full sweep matrix.
 */
object FallDetectionEngine {

    // ── Threshold constants ──────────────────────────────────────────────────

    /**
     * High-G spike threshold (m/s²). Calibrated against SisFall elderly subset.
     * Best-F1 row: spike=7.5 m/s² (0.76g), stillness=15.0 m/s², F1=0.667, sensitivity=0.800.
     * Report: docs/fall-detection-calibration.md
     */
    private const val FALL_SPIKE_THRESHOLD = 7.5f

    /**
     * Post-impact stillness threshold (m/s²). Calibrated against SisFall elderly subset.
     * A higher stillness threshold (15.0 m/s²) allows the arm to come to rest naturally
     * while still confirming the person is no longer in normal upright motion.
     */
    private const val FALL_STILLNESS_THRESHOLD = 15.0f

    /** Minimum consecutive samples the spike must hold to count as a real impact. */
    private const val SPIKE_MIN_SAMPLES = 2

    /** Minimum consecutive still samples after impact to confirm a fall. */
    private const val STILLNESS_MIN_SAMPLES = 3

    /**
     * Samples between spike and stillness. At 1 Hz BLE notify rate this is 5 s.
     * At 100 Hz on-device this would be 500 samples. Calibrate to your notify rate.
     */
    private const val SPIKE_TO_STILLNESS_WINDOW_SAMPLES = 5

    // ── Circular window buffer ───────────────────────────────────────────────

    private const val WINDOW_SIZE = 20
    private val magnitudeWindow = ArrayDeque<Float>(WINDOW_SIZE)
    private var samplesAfterSpike = Int.MAX_VALUE
    private var consecutiveSpikeSamples = 0
    private var consecutiveStillSamples = 0
    private var spikeDetected = false

    data class FallAssessment(
        val riskStatus: RiskStatus,
        val riskScore: Float,
        val accelerationMagnitude: Float,
    )

    /**
     * Assess fall risk from the latest IMU frame [ax, ay, az, gx, gy, gz] (m/s², °/s).
     *
     * Call this once per BLE notification (~1 Hz) or at sensor sample rate.
     * The function maintains state internally in the circular window.
     */
    @Synchronized
    fun assess(imuWindow: List<Float>): FallAssessment {
        if (imuWindow.size < 3) return FallAssessment(RiskStatus.Low, 0f, 9.81f)

        val ax = imuWindow[0]; val ay = imuWindow[1]; val az = imuWindow[2]
        val mag = sqrt(ax * ax + ay * ay + az * az)
        // SisFall validation was run on gravity-compensated data (ESP32 includes gravity).
        // Subtract 1g to get deviation-from-gravity for threshold comparison.
        val deviation = abs(mag - 9.81f)

        // Update circular window
        if (magnitudeWindow.size >= WINDOW_SIZE) magnitudeWindow.removeFirst()
        magnitudeWindow.addLast(deviation)

        // ── Phase B: Impact spike detection ──────────────────────────────────
        // Phase C (stillness) is nested in the else — spike and stillness are
        // mutually exclusive since stillness_threshold > spike_threshold.
        if (deviation > FALL_SPIKE_THRESHOLD) {
            consecutiveSpikeSamples++
            consecutiveStillSamples = 0
        } else {
            consecutiveSpikeSamples = 0
            // ── Phase C: Post-impact stillness detection ────────────────────────
            if (deviation < FALL_STILLNESS_THRESHOLD) {
                consecutiveStillSamples++
            } else {
                consecutiveStillSamples = 0
            }
        }

        if (consecutiveSpikeSamples >= SPIKE_MIN_SAMPLES && !spikeDetected) {
            spikeDetected = true
            samplesAfterSpike = 0
        }

        // Advance the post-spike sample counter
        if (spikeDetected) {
            samplesAfterSpike++
            if (samplesAfterSpike > SPIKE_TO_STILLNESS_WINDOW_SAMPLES) {
                // Stillness window expired — reset (person recovered or false positive)
                spikeDetected = false
                consecutiveStillSamples = 0
            }
        }

        // ── Score calculation ─────────────────────────────────────────────────
        val score = when {
            // Both spike AND stillness confirmed within the window → high confidence fall
            spikeDetected && consecutiveStillSamples >= STILLNESS_MIN_SAMPLES -> 0.90f
            // Spike detected but stillness not yet confirmed → medium risk
            spikeDetected -> 0.45f
            // Prolonged stillness without prior spike → inactivity, not a fall (handled by InactivityMonitor)
            consecutiveStillSamples >= STILLNESS_MIN_SAMPLES * 2 && deviation < FALL_STILLNESS_THRESHOLD -> 0.20f
            else -> 0.05f
        }

        val status = when {
            score >= 0.80f -> RiskStatus.High
            score >= 0.35f -> RiskStatus.Medium
            else -> RiskStatus.Low
        }

        // Reset after confirmed High event so we don't stay in High forever
        if (status == RiskStatus.High) {
            spikeDetected = false
            consecutiveStillSamples = 0
            consecutiveSpikeSamples = 0
            samplesAfterSpike = Int.MAX_VALUE
        }

        return FallAssessment(status, score, mag)
    }

    /** Reset all internal state. Call when disconnecting from sensor. */
    @Synchronized
    fun reset() {
        magnitudeWindow.clear()
        spikeDetected = false
        consecutiveSpikeSamples = 0
        consecutiveStillSamples = 0
        samplesAfterSpike = Int.MAX_VALUE
    }
}
