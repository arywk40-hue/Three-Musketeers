package com.smartsuit.ml

import kotlin.math.exp
import kotlin.math.pow

/**
 * Test-only synthetic ECG generator. Produces a QRS-spike train at a given
 * heart rate so unit tests can assert on [HeartRateExtractor] and
 * [EcgAnomalyDetector] against a known signal.
 */
internal object SyntheticEcg {
    fun regularSinusRhythm(bpm: Int, samples: Int, sampleRateHz: Int): List<Float> {
        val durationSec = samples.toFloat() / sampleRateHz.toFloat()
        return qrsTrain(hrBpm = bpm, durationSec = durationSec, sampleRateHz = sampleRateHz)
    }

    /**
     * Returns a 1-D ECG signal of [durationSec] seconds at [sampleRateHz]
     * carrying one positive QRS spike per beat at the given [hrBpm].
     */
    fun qrsTrain(
        hrBpm: Int,
        durationSec: Float = 4f,
        sampleRateHz: Int = 256,
        qrsAmplitude: Float = 0.9f,
        qrsWidth: Float = 3.0f,
        qrsCenterFrac: Float = 0.35f,
    ): List<Float> {
        val n = (durationSec * sampleRateHz).toInt()
        val samplesPerBeat = (60f / hrBpm) * sampleRateHz
        val qrsCenter = samplesPerBeat * qrsCenterFrac
        val inv2sigma2 = 1f / (2f * qrsWidth * qrsWidth)
        return List(n) { i ->
            val posInBeat = i % samplesPerBeat
            val diff = posInBeat - qrsCenter
            qrsAmplitude * exp(-(diff.pow(2)) * inv2sigma2)
        }
    }

    /**
     * Returns a QRS train whose RR intervals follow a repeating pattern of
     * [rrIntervalsMs]. The first beat is placed at the centre of the first
     * interval; subsequent beats are placed at the end of each preceding
     * interval. The signal length is [durationSec] seconds.
     *
     * Used to test the [EcgAnomalyDetector] irregularity threshold: a 4-value
     * cycle of [400, 600, 1000, 1200] ms has mean 800, MAD 300, MAD/mean
     * = 0.375, which is comfortably above the 0.30 AFib threshold.
     */
    fun patternedRrTrain(
        rrIntervalsMs: List<Int>,
        durationSec: Float = 8f,
        sampleRateHz: Int = 256,
    ): List<Float> {
        val totalSamples = (durationSec * sampleRateHz).toInt()
        val out = FloatArray(totalSamples)
        val qrsWidth = 3f
        val inv2sigma2 = 1f / (2f * qrsWidth * qrsWidth)
        val qrsAmplitude = 0.9f
        var beatStartSample = 0
        var patternIdx = 0
        while (beatStartSample < totalSamples) {
            val rrMs = rrIntervalsMs[patternIdx % rrIntervalsMs.size]
            val samplesThisBeat = (rrMs.toFloat() / 1000f) * sampleRateHz
            val qrsCenter = samplesThisBeat * 0.35f
            val endSample = (beatStartSample + samplesThisBeat).toInt().coerceAtMost(totalSamples)
            for (s in beatStartSample until endSample) {
                val posInBeat = s - beatStartSample
                val diff = posInBeat - qrsCenter
                out[s] = qrsAmplitude * exp(-(diff * diff) * inv2sigma2)
            }
            beatStartSample = endSample
            patternIdx++
        }
        return out.toList()
    }
}
