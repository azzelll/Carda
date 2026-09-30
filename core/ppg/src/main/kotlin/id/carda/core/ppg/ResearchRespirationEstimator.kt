package id.carda.core.ppg

import id.carda.core.model.QualityReport
import id.carda.core.model.MetricResult
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Exploratory camera-PPG respiratory modulation parameters, version rr-research-0.2.
 * The 0.1–0.6 Hz band and nominal 60-second window follow the offline BIDMC
 * development baseline. All rejection thresholds below are provisional software
 * gates, not physiological acceptance limits or validated camera settings.
 */
internal data class ResearchRespirationThresholds(
    val version: String = "rr-research-0.2",
    val minimumDurationSeconds: Double = 58.0,
    val maximumDurationSeconds: Double = 70.0,
    val minimumFramesPerSecond: Double = 20.0,
    val maximumSamples: Int = 7_200,
    val maximumCadenceJitterFraction: Double = 0.10,
    val maximumArtifactFraction: Double = 0.02,
    val highMotionScore: Double = 0.20,
    val poorCoverageFraction: Double = 0.85,
    val highClippedFraction: Double = 0.05,
    val highSaturatedFraction: Double = 0.05,
    val minimumBandHz: Double = 0.10,
    val maximumBandHz: Double = 0.60,
    val minimumComponentAmplitude: Double = 0.15,
    val minimumComponentToSignalRmsRatio: Double = 0.03,
    val minimumPeakToCompetitorRatio: Double = 1.5,
    val competitorExclusionHz: Double = 0.04,
    val maximumSubwindowDifferenceBpm: Double = 4.0,
    val minimumSubwindowAmplitudeFraction: Double = 0.35,
) {
    init {
        require(version.isNotBlank())
        require(listOf(
            minimumDurationSeconds, maximumDurationSeconds, minimumFramesPerSecond,
            maximumCadenceJitterFraction, maximumArtifactFraction, highMotionScore,
            poorCoverageFraction, highClippedFraction, highSaturatedFraction,
            minimumBandHz, maximumBandHz, minimumComponentAmplitude,
            minimumComponentToSignalRmsRatio, minimumPeakToCompetitorRatio,
            competitorExclusionHz, maximumSubwindowDifferenceBpm,
            minimumSubwindowAmplitudeFraction,
        ).all { it.isFinite() })
        require(minimumDurationSeconds >= 50.0 && maximumDurationSeconds in minimumDurationSeconds..90.0)
        require(minimumFramesPerSecond > 0.0 && maximumSamples in 2..7_200)
        require(maximumCadenceJitterFraction in 0.0..1.0)
        require(maximumArtifactFraction in 0.0..1.0)
        require(highMotionScore in 0.0..1.0 && poorCoverageFraction in 0.0..1.0)
        require(highClippedFraction in 0.0..1.0 && highSaturatedFraction in 0.0..1.0)
        require(minimumBandHz > 0.0 && maximumBandHz in minimumBandHz..1.0 &&
            maximumBandHz > minimumBandHz)
        require(minimumComponentAmplitude >= 0.0 && minimumComponentToSignalRmsRatio >= 0.0)
        require(minimumPeakToCompetitorRatio > 1.0 && competitorExclusionHz > 0.0)
        require(maximumSubwindowDifferenceBpm > 0.0)
        require(minimumSubwindowAmplitudeFraction in 0.0..1.0)
    }
}

internal enum class ResearchRespirationUnavailableReason {
    SQI_REJECTED,
    SAMPLE_BUDGET_EXCEEDED,
    QUALITY_REPORT_MISMATCH,
    INSUFFICIENT_DURATION,
    EXCESSIVE_DURATION,
    UNSTABLE_CADENCE,
    ARTIFACTS,
    WEAK_MODULATION,
    AMBIGUOUS_MODULATION,
    INCONSISTENT_SUBWINDOWS,
}

/** Research-only candidate for transient debug diagnostics, never a production metric or history value. */
internal sealed interface ResearchRespirationResult {
    data class Candidate(
        val breathsPerMinute: Double,
        val methodVersion: String,
        val evaluatedDurationMillis: Long,
    ) : ResearchRespirationResult

    data class Unavailable(val reason: ResearchRespirationUnavailableReason) : ResearchRespirationResult
}

/**
 * Pure, bounded estimator over one current capture; never stores or logs its samples.
 * Pass the very same configured PpgPipeline that produced the supplied quality report.
 */
internal class ResearchRespirationEstimator(
    private val thresholds: ResearchRespirationThresholds = ResearchRespirationThresholds(),
    private val globalPipeline: PpgPipeline,
) {
    fun estimate(samples: List<PpgSample>, quality: QualityReport): ResearchRespirationResult {
        fun unavailable(reason: ResearchRespirationUnavailableReason) = ResearchRespirationResult.Unavailable(reason)

        if (!quality.passed) return unavailable(ResearchRespirationUnavailableReason.SQI_REJECTED)
        val sourceCount = try {
            samples.size
        } catch (_: RuntimeException) {
            return unavailable(ResearchRespirationUnavailableReason.QUALITY_REPORT_MISMATCH)
        }
        if (sourceCount > thresholds.maximumSamples) {
            return unavailable(ResearchRespirationUnavailableReason.SAMPLE_BUDGET_EXCEEDED)
        }
        if (sourceCount < 2) {
            return unavailable(ResearchRespirationUnavailableReason.QUALITY_REPORT_MISMATCH)
        }
        // The caller owns the capture list until this copy finishes. Subsequent SQI,
        // artifact and spectral checks use only this private, bounded snapshot.
        val window = try {
            List(sourceCount) { index -> samples[index] }
        } catch (_: RuntimeException) {
            return unavailable(ResearchRespirationUnavailableReason.QUALITY_REPORT_MISMATCH)
        }
        val finalSourceCount = try {
            samples.size
        } catch (_: RuntimeException) {
            return unavailable(ResearchRespirationUnavailableReason.QUALITY_REPORT_MISMATCH)
        }
        if (finalSourceCount != sourceCount || quality.evaluatedSamples != window.size) {
            return unavailable(ResearchRespirationUnavailableReason.QUALITY_REPORT_MISMATCH)
        }
        val deltas = window.zipWithNext { a, b -> b.timestampNanos - a.timestampNanos }
        if (deltas.any { it <= 0 }) return unavailable(ResearchRespirationUnavailableReason.UNSTABLE_CADENCE)
        val durationNanos = window.last().timestampNanos - window.first().timestampNanos
        val durationMillis = durationNanos / 1_000_000L
        if (abs(quality.validDurationMillis - durationMillis) > 1L) {
            return unavailable(ResearchRespirationUnavailableReason.QUALITY_REPORT_MISMATCH)
        }
        // Recompute global SQI on the current bounded input. A passed report alone
        // is insufficient even if its sample count and duration happen to match.
        val currentQuality = globalPipeline.evaluateQuality(window)
        if (currentQuality.pipelineVersion != quality.pipelineVersion) {
            return unavailable(ResearchRespirationUnavailableReason.QUALITY_REPORT_MISMATCH)
        }
        if (!currentQuality.passed) return unavailable(ResearchRespirationUnavailableReason.SQI_REJECTED)
        if (currentQuality != quality) {
            return unavailable(ResearchRespirationUnavailableReason.QUALITY_REPORT_MISMATCH)
        }
        val durationSeconds = durationNanos / 1_000_000_000.0
        if (durationSeconds < thresholds.minimumDurationSeconds) {
            return unavailable(ResearchRespirationUnavailableReason.INSUFFICIENT_DURATION)
        }
        if (durationSeconds > thresholds.maximumDurationSeconds) {
            return unavailable(ResearchRespirationUnavailableReason.EXCESSIVE_DURATION)
        }
        val meanDelta = durationNanos.toDouble() / deltas.size
        val cadenceJitter = deltas.maxOf { abs(it - meanDelta) } / meanDelta
        if (deltas.size / durationSeconds < thresholds.minimumFramesPerSecond ||
            cadenceJitter > thresholds.maximumCadenceJitterFraction
        ) return unavailable(ResearchRespirationUnavailableReason.UNSTABLE_CADENCE)

        val artifactFraction = window.count {
            it.motionScore > thresholds.highMotionScore ||
                it.coverageFraction < thresholds.poorCoverageFraction ||
                it.clippedFraction > thresholds.highClippedFraction ||
                it.saturatedFraction > thresholds.highSaturatedFraction || !it.torchOn
        }.toDouble() / window.size
        if (artifactFraction > thresholds.maximumArtifactFraction) {
            return unavailable(ResearchRespirationUnavailableReason.ARTIFACTS)
        }

        val full = spectrum(window, 0, window.size)
        val dominant = full.maxBy { it.amplitude }
        if (!hasAdequateComponent(dominant, window)) {
            val amplitudeRate = estimateAmplitudeModulation(window)
            return if (amplitudeRate == null) unavailable(ResearchRespirationUnavailableReason.WEAK_MODULATION)
            else ResearchRespirationResult.Candidate(amplitudeRate * 60.0,
                "${thresholds.version}-amplitude", durationMillis)
        }
        val middle = window.size / 2
        val first = spectrum(window, 0, middle).maxBy { it.amplitude }
        val second = spectrum(window, middle, window.size).maxBy { it.amplitude }
        val stable = abs(first.frequencyHz - second.frequencyHz) * 60.0 <= thresholds.maximumSubwindowDifferenceBpm &&
            abs(first.frequencyHz - dominant.frequencyHz) * 60.0 <= thresholds.maximumSubwindowDifferenceBpm &&
            abs(second.frequencyHz - dominant.frequencyHz) * 60.0 <= thresholds.maximumSubwindowDifferenceBpm &&
            minOf(first.amplitude, second.amplitude) >=
            dominant.amplitude * thresholds.minimumSubwindowAmplitudeFraction
        if (!stable) return unavailable(ResearchRespirationUnavailableReason.INCONSISTENT_SUBWINDOWS)

        val competing = full.filter { abs(it.frequencyHz - dominant.frequencyHz) >= thresholds.competitorExclusionHz }
            .maxOfOrNull { it.amplitude } ?: 0.0
        if (competing > 0.0 && dominant.amplitude / competing < thresholds.minimumPeakToCompetitorRatio) {
            return unavailable(ResearchRespirationUnavailableReason.AMBIGUOUS_MODULATION)
        }

        return ResearchRespirationResult.Candidate(
            breathsPerMinute = dominant.frequencyHz * 60.0,
            methodVersion = "${thresholds.version}-baseline",
            evaluatedDurationMillis = durationMillis,
        )
    }

    private data class Peak(val frequencyHz: Double, val amplitude: Double)

    private data class BeatAmplitude(val timestampNanos: Long, val value: Double)

    /** A separate research branch; it cannot rescue other baseline artefact/ambiguity rejections. */
    private fun estimateAmplitudeModulation(samples: List<PpgSample>): Double? {
        val pulseBpm = (globalPipeline.analyze(samples).heartRateBpm as? MetricResult.Available)?.value
            ?: return null
        val beatNanos = (60_000_000_000.0 / pulseBpm).toLong()
        if (beatNanos <= 0L) return null
        val firstNanos = samples.first().timestampNanos
        val completeBins = ((samples.last().timestampNanos - firstNanos) / beatNanos).toInt()
        if (completeBins < 30) return null
        val minimum = DoubleArray(completeBins) { Double.POSITIVE_INFINITY }
        val maximum = DoubleArray(completeBins) { Double.NEGATIVE_INFINITY }
        val counts = IntArray(completeBins)
        for (sample in samples) {
            val index = ((sample.timestampNanos - firstNanos) / beatNanos).toInt()
            if (index !in 0 until completeBins) continue
            minimum[index] = minOf(minimum[index], sample.red)
            maximum[index] = maxOf(maximum[index], sample.red)
            counts[index]++
        }
        if (counts.any { it < 3 }) return null
        val envelope = List(completeBins) { index ->
            BeatAmplitude(firstNanos + index * beatNanos + beatNanos / 2,
                maximum[index] - minimum[index])
        }
        val full = amplitudeSpectrum(envelope, 0, envelope.size)
        val dominant = full.maxBy { it.amplitude }
        val mean = envelope.sumOf { it.value } / envelope.size
        val rms = sqrt(envelope.sumOf { (it.value - mean) * (it.value - mean) } / envelope.size)
        if (dominant.amplitude < thresholds.minimumComponentAmplitude || rms <= 0.0 ||
            dominant.amplitude / rms < thresholds.minimumComponentToSignalRmsRatio) return null
        val middle = envelope.size / 2
        val first = amplitudeSpectrum(envelope, 0, middle).maxBy { it.amplitude }
        val second = amplitudeSpectrum(envelope, middle, envelope.size).maxBy { it.amplitude }
        val stable = abs(first.frequencyHz - second.frequencyHz) * 60.0 <= thresholds.maximumSubwindowDifferenceBpm &&
            abs(first.frequencyHz - dominant.frequencyHz) * 60.0 <= thresholds.maximumSubwindowDifferenceBpm &&
            abs(second.frequencyHz - dominant.frequencyHz) * 60.0 <= thresholds.maximumSubwindowDifferenceBpm &&
            minOf(first.amplitude, second.amplitude) >=
            dominant.amplitude * thresholds.minimumSubwindowAmplitudeFraction
        if (!stable) return null
        val competing = full.filter { abs(it.frequencyHz - dominant.frequencyHz) >= thresholds.competitorExclusionHz }
            .maxOfOrNull { it.amplitude } ?: 0.0
        if (competing > 0.0 && dominant.amplitude / competing < thresholds.minimumPeakToCompetitorRatio) return null
        return dominant.frequencyHz
    }

    private fun amplitudeSpectrum(samples: List<BeatAmplitude>, from: Int, until: Int): List<Peak> {
        val count = until - from
        val firstNanos = samples[from].timestampNanos
        val durationSeconds = (samples[until - 1].timestampNanos - firstNanos) / 1_000_000_000.0
        val times = DoubleArray(count) { i -> (samples[from + i].timestampNanos - firstNanos) / 1_000_000_000.0 }
        val values = DoubleArray(count) { i -> samples[from + i].value }
        val meanTime = times.average()
        val meanValue = values.average()
        var covariance = 0.0
        var timeVariance = 0.0
        for (i in 0 until count) {
            covariance += (times[i] - meanTime) * (values[i] - meanValue)
            timeVariance += (times[i] - meanTime) * (times[i] - meanTime)
        }
        val slope = if (timeVariance > 0.0) covariance / timeVariance else 0.0
        val centered = DoubleArray(count) { i -> values[i] - meanValue - slope * (times[i] - meanTime) }
        val weights = DoubleArray(count) { i -> 0.5 - 0.5 * cos(2.0 * PI * i / (count - 1)) }
        val weightSum = weights.sum()
        val stepHz = 1.0 / (4.0 * durationSeconds)
        val numberOfSteps = ((thresholds.maximumBandHz - thresholds.minimumBandHz) / stepHz).toInt()
        return (0..numberOfSteps).map { step ->
            val frequency = thresholds.minimumBandHz + step * stepHz
            var real = 0.0
            var imaginary = 0.0
            for (i in 0 until count) {
                val angle = 2.0 * PI * frequency * times[i]
                real += centered[i] * weights[i] * cos(angle)
                imaginary += centered[i] * weights[i] * sin(angle)
            }
            Peak(frequency, 2.0 * hypot(real, imaginary) / weightSum)
        }
    }

    private fun spectrum(samples: List<PpgSample>, from: Int, until: Int): List<Peak> {
        val count = until - from
        val firstNanos = samples[from].timestampNanos
        val durationSeconds = (samples[until - 1].timestampNanos - firstNanos) / 1_000_000_000.0
        val times = DoubleArray(count) { i -> (samples[from + i].timestampNanos - firstNanos) / 1_000_000_000.0 }
        val values = DoubleArray(count) { i -> samples[from + i].red }
        val meanTime = times.average()
        val meanValue = values.average()
        var covariance = 0.0
        var timeVariance = 0.0
        for (i in 0 until count) {
            covariance += (times[i] - meanTime) * (values[i] - meanValue)
            timeVariance += (times[i] - meanTime) * (times[i] - meanTime)
        }
        val slope = if (timeVariance > 0.0) covariance / timeVariance else 0.0
        val centered = DoubleArray(count) { i -> values[i] - meanValue - slope * (times[i] - meanTime) }
        val weights = DoubleArray(count) { i -> 0.5 - 0.5 * cos(2.0 * PI * i / (count - 1)) }
        val weightSum = weights.sum()
        val stepHz = 1.0 / (4.0 * durationSeconds)
        val numberOfSteps = ((thresholds.maximumBandHz - thresholds.minimumBandHz) / stepHz).toInt()
        return (0..numberOfSteps).map { step ->
            val frequency = thresholds.minimumBandHz + step * stepHz
            var real = 0.0
            var imaginary = 0.0
            for (i in 0 until count) {
                val angle = 2.0 * PI * frequency * times[i]
                real += centered[i] * weights[i] * cos(angle)
                imaginary += centered[i] * weights[i] * sin(angle)
            }
            Peak(frequency, 2.0 * hypot(real, imaginary) / weightSum)
        }
    }

    private fun hasAdequateComponent(peak: Peak, samples: List<PpgSample>): Boolean {
        if (peak.amplitude < thresholds.minimumComponentAmplitude) return false
        val mean = samples.sumOf { it.red } / samples.size
        val rms = sqrt(samples.sumOf { sample ->
            val centered = sample.red - mean
            centered * centered
        } / samples.size)
        return rms > 0.0 && peak.amplitude / rms >= thresholds.minimumComponentToSignalRmsRatio
    }
}
