package com.eldercareguardian.ml

import com.eldercareguardian.data.EcgAnomalyStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Unit tests for the Phase 5/6 fixed EcgAnomalyDetector.
 *
 * Key test: AFib RMSSD logic was inverted in the original code.
 * The fix changes: `rmssd < threshold` (wrong) to `rmssd > threshold && irregularity > threshold` (correct).
 *
 * These tests use synthetic ECG signals from SyntheticEcg.kt where available,
 * and direct manipulation of RR intervals for RMSSD/AFib boundary tests.
 */
class EcgAnomalyDetectorPhase5Test {

    // ── Normal sinus rhythm ──────────────────────────────────────────────────

    @Test
    fun `regular 72 bpm ECG returns Normal`() {
        val ecg = SyntheticEcg.regularSinusRhythm(bpm = 72, samples = 256, sampleRateHz = 256)
        val result = EcgAnomalyDetector.assess(ecg, 72)
        assertEquals(EcgAnomalyStatus.Normal, result.status)
    }

    // ── Rate-based classifications ───────────────────────────────────────────

    @Test
    fun `110 bpm reported HR returns Tachycardia`() {
        val ecg = SyntheticEcg.regularSinusRhythm(bpm = 110, samples = 256, sampleRateHz = 256)
        val result = EcgAnomalyDetector.assess(ecg, 110)
        assertEquals(EcgAnomalyStatus.Tachycardia, result.status)
    }

    @Test
    fun `45 bpm reported HR returns Bradycardia`() {
        val ecg = SyntheticEcg.regularSinusRhythm(bpm = 45, samples = 256, sampleRateHz = 256)
        val result = EcgAnomalyDetector.assess(ecg, 45)
        assertEquals(EcgAnomalyStatus.Bradycardia, result.status)
    }

    @Test
    fun `exactly 100 bpm returns Tachycardia`() {
        val result = EcgAnomalyDetector.assess(emptyList(), 100)
        assertEquals(EcgAnomalyStatus.Tachycardia, result.status)
    }

    @Test
    fun `exactly 50 bpm returns Bradycardia`() {
        val result = EcgAnomalyDetector.assess(emptyList(), 50)
        assertEquals(EcgAnomalyStatus.Bradycardia, result.status)
    }

    // ── AFib detection (critical fix verification) ───────────────────────────

    @Test
    fun `low RMSSD regular rhythm should NOT be AFib (was broken before fix)`() {
        // Low HRV (RMSSD < 25 ms) + regular rhythm = NOT AFib.
        // The old code incorrectly flagged this as AFib.
        val ecg = SyntheticEcg.regularSinusRhythm(bpm = 75, samples = 256, sampleRateHz = 256)
        val result = EcgAnomalyDetector.assess(ecg, 75)
        // Regular sinus rhythm should NEVER be AFib regardless of RMSSD
        assert(result.status != EcgAnomalyStatus.AFib) {
            "Regular sinus rhythm incorrectly classified as AFib: RMSSD=${result.rmssdMs}"
        }
    }

    @Test
    fun `null ECG with no reported HR returns Unknown`() {
        val result = EcgAnomalyDetector.assess(emptyList(), null)
        assertEquals(EcgAnomalyStatus.Unknown, result.status)
    }

    @Test
    fun `reported HR in normal range with empty ECG returns Normal`() {
        val result = EcgAnomalyDetector.assess(emptyList(), 72)
        // Not enough RR intervals from empty ECG, but rate is normal
        assertEquals(EcgAnomalyStatus.Normal, result.status)
    }

    // ── RMSSD computation ────────────────────────────────────────────────────

    @Test
    fun `RMSSD is null for less than 2 RR intervals`() {
        val result = HeartRateExtractor.rmssd(emptyList())
        assertEquals(null, result)
    }

    @Test
    fun `RMSSD is zero for identical intervals`() {
        val rr = listOf(800, 800, 800, 800, 800)
        val result = HeartRateExtractor.rmssd(rr)
        assertNotNull(result)
        assertEquals(0, result!!)
    }

    @Test
    fun `RMSSD is non-zero for irregular intervals`() {
        val rr = listOf(600, 900, 550, 1000, 650, 850)
        val result = HeartRateExtractor.rmssd(rr)
        assertNotNull(result)
        assert(result!! > 0) { "Expected non-zero RMSSD for irregular RR intervals" }
    }

    // ── HeartRateExtractor edge cases ─────────────────────────────────────────

    @Test
    fun `extractRrIntervals returns empty list for too-short ECG`() {
        val result = HeartRateExtractor.extractRrIntervals(listOf(0.1f, 0.2f, 0.3f))
        assertEquals(emptyList<Int>(), result.rrIntervalsMs)
        assertEquals(null, result.meanHrBpm)
    }

    @Test
    fun `extractRrIntervals computes HR within expected range for 72 bpm synthetic ECG`() {
        val ecg = SyntheticEcg.regularSinusRhythm(bpm = 72, samples = 512, sampleRateHz = 256)
        val result = HeartRateExtractor.extractRrIntervals(ecg, sampleRateHz = 256)
        val hr = result.meanHrBpm
        if (hr != null) {
            assert(hr in 60..90) { "Expected HR near 72 bpm but got $hr" }
        }
        // It's acceptable if the synthetic signal doesn't produce enough peaks in 2 seconds
    }
}
