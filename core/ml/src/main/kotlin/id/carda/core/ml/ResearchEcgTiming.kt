package id.carda.core.ml

import id.carda.core.model.ExperimentalInsightResult
import kotlin.math.sqrt

/** Provisional waveform-timing gates. This is not an ECG or rhythm validation method. */
data class ResearchEcgTimingThresholds(
    val version: String = "ecg-timing-research-0.1",
    val minimumSamples: Int = 128,
    val minimumPeakCount: Int = 3,
    val minimumAmplitudeRange: Double = 0.000001,
    val minimumProminenceFraction: Double = 0.45,
    val minimumPeakToRmsRatio: Double = 1.5,
    val refractoryMillis: Double = 280.0,
    val minimumIntervalMillis: Double = 280.0,
    val maximumIntervalMillis: Double = 2_000.0,
) {
    init {
        require(version.isNotBlank() && minimumSamples >= 3 && minimumPeakCount >= 3)
        require(minimumAmplitudeRange.isFinite() && minimumAmplitudeRange > 0.0)
        require(minimumProminenceFraction.isFinite() && minimumProminenceFraction in 0.0..1.0)
        require(minimumPeakToRmsRatio.isFinite() && minimumPeakToRmsRatio > 1.0)
        require(refractoryMillis.isFinite() && refractoryMillis > 0.0)
        require(minimumIntervalMillis.isFinite() && minimumIntervalMillis >= refractoryMillis)
        require(maximumIntervalMillis.isFinite() && maximumIntervalMillis > minimumIntervalMillis)
    }
}

/**
 * Extracts a timing candidate only from an already quality/confidence-gated estimated waveform.
 * The model's polarity/scale and peak timing still require paired phone-camera/ECG evaluation.
 */
class ResearchEcgTimingExtractor(
    private val thresholds: ResearchEcgTimingThresholds = ResearchEcgTimingThresholds(),
) {
    fun estimate(waveform: FloatArray, sampleRateHz: Double): ExperimentalInsightResult.EstimatedTiming? {
        if (waveform.size < thresholds.minimumSamples || waveform.any { !it.isFinite() } ||
            !sampleRateHz.isFinite() || sampleRateHz <= 0.0) return null
        val values = waveform.copyOf()
        val sorted = values.sorted()
        val median = sorted[sorted.size / 2].toDouble()
        val upward = values.max().toDouble() - median
        val downward = median - values.min().toDouble()
        val polarity = if (upward >= downward) 1.0 else -1.0
        val excursion = maxOf(upward, downward)
        if (excursion < thresholds.minimumAmplitudeRange) return null
        val centered = DoubleArray(values.size) { index -> polarity * (values[index] - median) }
        val rms = sqrt(centered.sumOf { it * it } / centered.size)
        if (rms <= 0.0) return null
        val floor = excursion * thresholds.minimumProminenceFraction
        val refractorySamples = (thresholds.refractoryMillis * sampleRateHz / 1_000.0).toInt()
            .coerceAtLeast(1)
        val peaks = mutableListOf<Int>()
        for (index in 1 until centered.lastIndex) {
            val height = centered[index]
            if (height < floor || height / rms < thresholds.minimumPeakToRmsRatio ||
                height <= centered[index - 1] || height < centered[index + 1]) continue
            val previous = peaks.lastOrNull()
            if (previous == null || index - previous >= refractorySamples) {
                peaks += index
            } else if (height > centered[previous]) {
                peaks[peaks.lastIndex] = index
            }
        }
        if (peaks.size < thresholds.minimumPeakCount) return null
        val intervals = peaks.zipWithNext { a, b -> (b - a) * 1_000.0 / sampleRateHz }
        if (intervals.any { it !in thresholds.minimumIntervalMillis..thresholds.maximumIntervalMillis }) {
            return null
        }
        val meanRrMillis = intervals.average()
        if (!meanRrMillis.isFinite()) return null
        return ExperimentalInsightResult.EstimatedTiming(peaks, meanRrMillis, thresholds.version)
    }
}
