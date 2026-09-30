package id.carda.core.ppg

import id.carda.core.model.MetricResult
import id.carda.core.model.QualityReport
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Research-only optical feature. Red/green camera responses are broadband and
 * do not encode absolute SpO2 without device-specific calibration and evaluation.
 * None of the thresholds below are physiological acceptance limits.
 */
internal data class OximetryFeatureThresholds(
    val version: String = "spo2-feature-research-0.2",
    val minimumDurationSeconds: Double = 30.0,
    val maximumSamples: Int = 7_200,
    val maximumCadenceJitterFraction: Double = 0.10,
    val minimumMeanRed: Double = 25.0,
    val minimumMeanGreen: Double = 15.0,
    val maximumMeanChannel: Double = 230.0,
    val maximumGreenClippedFraction: Double = 0.05,
    val minimumPulsatileFraction: Double = 0.005,
    val maximumHalfRatioDifferenceFraction: Double = 0.25,
) {
    init {
        require(version.isNotBlank() && minimumDurationSeconds >= 10.0)
        require(maximumSamples in 2..7_200)
        require(maximumCadenceJitterFraction in 0.0..1.0)
        require(minimumMeanRed in 0.0..255.0 && minimumMeanGreen in 0.0..255.0)
        require(maximumMeanChannel in maxOf(minimumMeanRed, minimumMeanGreen)..255.0)
        require(maximumGreenClippedFraction in 0.0..1.0)
        require(minimumPulsatileFraction in 0.0..1.0)
        require(maximumHalfRatioDifferenceFraction in 0.0..1.0)
    }
}

internal enum class OximetryFeatureUnavailableReason {
    SQI_REJECTED,
    SAMPLE_BUDGET_EXCEEDED,
    QUALITY_REPORT_MISMATCH,
    INSUFFICIENT_DURATION,
    UNSTABLE_CADENCE,
    HEART_RATE_UNAVAILABLE,
    CHANNEL_UNUSABLE,
    UNSTABLE_CHANNEL_RATIO,
}

internal sealed interface ResearchOximetryFeatureResult {
    /** Unitless feature only. It is not a percent saturation or a MetricResult. */
    data class Features(
        val redGreenRatioOfRatios: Double,
        val pulseFrequencyHz: Double,
        val methodVersion: String,
        val evaluatedDurationMillis: Long,
    ) : ResearchOximetryFeatureResult

    data class Unavailable(val reason: OximetryFeatureUnavailableReason) : ResearchOximetryFeatureResult
}

/** Requires the same configured pipeline that produced [quality]. Never persists samples. */
internal class ResearchOximetryFeatureExtractor(
    private val pipeline: PpgPipeline,
    private val thresholds: OximetryFeatureThresholds = OximetryFeatureThresholds(),
) {
    fun extract(samples: List<PpgSample>, quality: QualityReport): ResearchOximetryFeatureResult {
        fun unavailable(reason: OximetryFeatureUnavailableReason) = ResearchOximetryFeatureResult.Unavailable(reason)
        if (!quality.passed) return unavailable(OximetryFeatureUnavailableReason.SQI_REJECTED)
        val sourceSize = try { samples.size } catch (_: RuntimeException) {
            return unavailable(OximetryFeatureUnavailableReason.QUALITY_REPORT_MISMATCH)
        }
        if (sourceSize > thresholds.maximumSamples) return unavailable(OximetryFeatureUnavailableReason.SAMPLE_BUDGET_EXCEEDED)
        if (sourceSize < 2) return unavailable(OximetryFeatureUnavailableReason.QUALITY_REPORT_MISMATCH)
        val window = try { List(sourceSize) { samples[it] } } catch (_: RuntimeException) {
            return unavailable(OximetryFeatureUnavailableReason.QUALITY_REPORT_MISMATCH)
        }
        val finalSize = try { samples.size } catch (_: RuntimeException) {
            return unavailable(OximetryFeatureUnavailableReason.QUALITY_REPORT_MISMATCH)
        }
        if (sourceSize != finalSize || quality.evaluatedSamples != sourceSize) {
            return unavailable(OximetryFeatureUnavailableReason.QUALITY_REPORT_MISMATCH)
        }
        val deltas = window.zipWithNext { a, b -> b.timestampNanos - a.timestampNanos }
        if (deltas.any { it <= 0 }) return unavailable(OximetryFeatureUnavailableReason.UNSTABLE_CADENCE)
        val durationNanos = window.last().timestampNanos - window.first().timestampNanos
        val durationMillis = durationNanos / 1_000_000L
        if (abs(durationMillis - quality.validDurationMillis) > 1L ||
            pipeline.evaluateQuality(window) != quality
        ) return unavailable(OximetryFeatureUnavailableReason.QUALITY_REPORT_MISMATCH)
        val durationSeconds = durationNanos / 1_000_000_000.0
        if (durationSeconds < thresholds.minimumDurationSeconds) {
            return unavailable(OximetryFeatureUnavailableReason.INSUFFICIENT_DURATION)
        }
        // HR uses red; a usable red window cannot certify the green optical channel.
        if (window.any { it.greenClippedFraction > thresholds.maximumGreenClippedFraction }) {
            return unavailable(OximetryFeatureUnavailableReason.CHANNEL_UNUSABLE)
        }
        val meanDelta = durationNanos.toDouble() / deltas.size
        if (deltas.maxOf { abs(it - meanDelta) } / meanDelta > thresholds.maximumCadenceJitterFraction) {
            return unavailable(OximetryFeatureUnavailableReason.UNSTABLE_CADENCE)
        }
        val pulseBpm = (pipeline.analyze(window).heartRateBpm as? MetricResult.Available)?.value
            ?: return unavailable(OximetryFeatureUnavailableReason.HEART_RATE_UNAVAILABLE)
        val pulseHz = pulseBpm / 60.0
        val middle = window.size / 2
        val full = ratio(window, 0, window.size, pulseHz)
            ?: return unavailable(OximetryFeatureUnavailableReason.CHANNEL_UNUSABLE)
        val first = ratio(window, 0, middle, pulseHz)
            ?: return unavailable(OximetryFeatureUnavailableReason.CHANNEL_UNUSABLE)
        val second = ratio(window, middle, window.size, pulseHz)
            ?: return unavailable(OximetryFeatureUnavailableReason.CHANNEL_UNUSABLE)
        if (abs(first - second) / maxOf(first, second) > thresholds.maximumHalfRatioDifferenceFraction) {
            return unavailable(OximetryFeatureUnavailableReason.UNSTABLE_CHANNEL_RATIO)
        }
        return ResearchOximetryFeatureResult.Features(full, pulseHz, thresholds.version, durationMillis)
    }

    private fun ratio(samples: List<PpgSample>, start: Int, end: Int, pulseHz: Double): Double? {
        val red = channel(samples, start, end, pulseHz, thresholds.minimumMeanRed) { it.red } ?: return null
        val green = channel(samples, start, end, pulseHz, thresholds.minimumMeanGreen) { it.green } ?: return null
        val value = red / green
        return value.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun channel(
        samples: List<PpgSample>, start: Int, end: Int, pulseHz: Double, minimumMean: Double,
        select: (PpgSample) -> Double,
    ): Double? {
        val count = end - start
        if (count < 3) return null
        val mean = (start until end).sumOf { select(samples[it]) } / count
        if (mean < minimumMean || mean > thresholds.maximumMeanChannel) return null
        val times = DoubleArray(count) { i ->
            (samples[start + i].timestampNanos - samples[start].timestampNanos) / 1_000_000_000.0
        }
        val meanTime = times.average()
        var covariance = 0.0
        var timeVariance = 0.0
        for (i in 0 until count) {
            covariance += (times[i] - meanTime) * (select(samples[start + i]) - mean)
            timeVariance += (times[i] - meanTime) * (times[i] - meanTime)
        }
        val slope = if (timeVariance > 0.0) covariance / timeVariance else 0.0
        var real = 0.0
        var imaginary = 0.0
        var weightSum = 0.0
        for (i in 0 until count) {
            val centered = select(samples[start + i]) - mean - slope * (times[i] - meanTime)
            val weight = 0.5 - 0.5 * cos(2.0 * PI * i / (count - 1))
            val angle = 2.0 * PI * pulseHz * times[i]
            real += centered * weight * cos(angle)
            imaginary += centered * weight * sin(angle)
            weightSum += weight
        }
        val amplitudeFraction = 2.0 * hypot(real, imaginary) / weightSum / mean
        return amplitudeFraction.takeIf {
            it.isFinite() && it >= thresholds.minimumPulsatileFraction
        }
    }
}
