package com.eldercareguardian.ml

import com.eldercareguardian.data.EcgAnomalyStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EcgAnomalyDetectorTest {

    @Test
    fun `classifies regular 75 bpm as Normal`() {
        val ecg = SyntheticEcg.qrsTrain(hrBpm = 75, durationSec = 6f)
        val assessment = EcgAnomalyDetector.assess(ecg)
        assertEquals(EcgAnomalyStatus.Normal, assessment.status)
        assertNotNull(assessment.meanHrBpm)
    }

    @Test
    fun `classifies 120 bpm as Tachycardia`() {
        val ecg = SyntheticEcg.qrsTrain(hrBpm = 120, durationSec = 6f)
        val assessment = EcgAnomalyDetector.assess(ecg)
        assertEquals(EcgAnomalyStatus.Tachycardia, assessment.status)
    }

    @Test
    fun `classifies 40 bpm as Bradycardia`() {
        val ecg = SyntheticEcg.qrsTrain(hrBpm = 40, durationSec = 8f)
        val assessment = EcgAnomalyDetector.assess(ecg)
        assertEquals(EcgAnomalyStatus.Bradycardia, assessment.status)
    }

    @Test
    fun `classifies irregular intervals as AFib`() {
        // 4-value cycle with mean=800, MAD=300, MAD/mean=0.375. The detector
        // flags irregularity > 0.30 as AFib.
        val ecg = SyntheticEcg.patternedRrTrain(
            rrIntervalsMs = listOf(400, 600, 1000, 1200),
            durationSec = 10f,
        )
        val assessment = EcgAnomalyDetector.assess(ecg)
        assertEquals(EcgAnomalyStatus.AFib, assessment.status)
    }

    @Test
    fun `reports Unknown when there is no detectable RR and no reported HR`() {
        // Empty signal → no RR, no reported HR.
        val assessment = EcgAnomalyDetector.assess(List(64) { 0f })
        assertEquals(EcgAnomalyStatus.Unknown, assessment.status)
    }

    @Test
    fun `uses reported HR when no RR intervals are extracted`() {
        // 32 samples of zero — too short for the detector to fire, but the
        // caller reports HR = 110. The detector should fall back to that.
        val assessment = EcgAnomalyDetector.assess(List(32) { 0f }, reportedHrBpm = 110)
        // HeartRateExtractor.extractRrIntervals returns empty for < sampleRateHz/4 samples
        // (sampleRateHz default 256, so < 64). The detector then uses reportedHrBpm.
        assertEquals(EcgAnomalyStatus.Tachycardia, assessment.status)
    }
}
