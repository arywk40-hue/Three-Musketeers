package com.eldercareguardian.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateExtractorTest {

    @Test
    fun `returns empty result for too-short signal`() {
        val result = HeartRateExtractor.extractRrIntervals(List(32) { 0f })
        assertNull(result.meanHrBpm)
        assertTrue(result.rrIntervalsMs.isEmpty())
    }

    @Test
    fun `extracts 60 bpm from QRS-spike train`() {
        val ecg = SyntheticEcg.qrsTrain(hrBpm = 60, durationSec = 6f)
        val result = HeartRateExtractor.extractRrIntervals(ecg)
        assertNotNull(result.meanHrBpm)
        // Allow ±3 bpm tolerance for the per-beat jitter from windowing.
        val hr = result.meanHrBpm!!
        assertTrue("HR was $hr, expected ~60", hr in 57..63)
    }

    @Test
    fun `extracts 75 bpm from QRS-spike train`() {
        val ecg = SyntheticEcg.qrsTrain(hrBpm = 75, durationSec = 8f)
        val result = HeartRateExtractor.extractRrIntervals(ecg)
        assertNotNull(result.meanHrBpm)
        val hr = result.meanHrBpm!!
        assertTrue("HR was $hr, expected ~75", hr in 72..78)
    }

    @Test
    fun `extracts 120 bpm from QRS-spike train`() {
        val ecg = SyntheticEcg.qrsTrain(hrBpm = 120, durationSec = 8f)
        val result = HeartRateExtractor.extractRrIntervals(ecg)
        assertNotNull(result.meanHrBpm)
        val hr = result.meanHrBpm!!
        assertTrue("HR was $hr, expected ~120", hr in 116..124)
    }

    @Test
    fun `rmssd is zero for perfectly regular intervals`() {
        val regular = listOf(800, 800, 800, 800, 800)
        val rmssd = HeartRateExtractor.rmssd(regular)
        assertEquals(0, rmssd)
    }

    @Test
    fun `rmssd is positive for irregular intervals`() {
        val irregular = listOf(700, 900, 700, 900, 700)
        val rmssd = HeartRateExtractor.rmssd(irregular)
        assertNotNull(rmssd)
        assertTrue("rmssd was $rmssd, expected > 100", rmssd!! > 100)
    }

    @Test
    fun `rmssd returns null for fewer than two intervals`() {
        assertNull(HeartRateExtractor.rmssd(emptyList()))
        assertNull(HeartRateExtractor.rmssd(listOf(800)))
    }

    @Test
    fun `no phantom peak is registered at the trailing edge of the window`() {
        // A signal that rises monotonically and ends at the very last sample
        // at its maximum. The old detector's `i + 1 >= size` branch would
        // register a peak here and produce a phantom RR interval against
        // lastPeakIndex. The fixed detector should refuse.
        val ecg = List(1024) { it / 1024f }   // 0.0, 0.001, 0.002, ... 0.999
        val result = HeartRateExtractor.extractRrIntervals(ecg)
        // No genuine QRS spikes are present, so the detector must not invent
        // any RR intervals.
        assertTrue(result.rrIntervalsMs.isEmpty())
    }
}
