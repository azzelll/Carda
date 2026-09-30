package id.carda.core.ppg

import id.carda.core.model.MetricResult
import id.carda.core.model.MetricUnavailableReason
import id.carda.core.model.QualityIssue
import id.carda.core.model.QualityReport
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** One aggregate RGB observation from a frame ROI; never persist or log this type. */
data class PpgSample(
    val timestampNanos: Long,
    val red: Double,
    val green: Double,
    val blue: Double,
    val coverageFraction: Double,
    val saturatedFraction: Double,
    val clippedFraction: Double,
    val motionScore: Double,
    val torchOn: Boolean,
    /** Optical research only; red-channel HR/SQI clipping remains [clippedFraction]. */
    val greenClippedFraction: Double = 0.0,
) {
    init {
        require(timestampNanos >= 0)
        require(listOf(red, green, blue).all { it.isFinite() && it in 0.0..255.0 })
        require(listOf(coverageFraction, saturatedFraction, clippedFraction, motionScore,
            greenClippedFraction)
            .all { it.isFinite() && it in 0.0..1.0 })
    }
}

/**
 * Provisional engineering gates, version ppg-0.3. These are not calibrated physiological limits.
 * Revise only with a documented device/reference evaluation and version bump.
 */
data class QualityThresholds(
    val version: String = "ppg-0.3",
    val extraction: ExtractionThresholds = ExtractionThresholds(),
    val minimumSamples: Int = 200,
    val minimumWindowSeconds: Double = 10.0,
    val minimumFramesPerSecond: Double = 20.0,
    val maximumCadenceJitterFraction: Double = 0.35,
    val minimumMeanRed: Double = 25.0,
    val maximumMeanRed: Double = 230.0,
    val minimumCoverageFraction: Double = 0.75,
    val maximumSaturatedFraction: Double = 0.15,
    val maximumClippedFraction: Double = 0.15,
    val maximumMotionScore: Double = 0.35,
    val minimumPulsatileRms: Double = 1.5,
    val maximumNoiseToSignalRatio: Double = 0.8,
    val minimumScore: Double = 0.75,
    val minimumHeartRateBeats: Int = 8,
    val minimumPrvWindowSeconds: Double = 30.0,
    val minimumPrvIntervals: Int = 20,
    val maximumPrvCadenceJitterFraction: Double = 0.10,
    val minimumBeatIntervalMillis: Double = 333.0,
    val maximumBeatIntervalMillis: Double = 1500.0,
    val maximumPrvIntervalStepFraction: Double = 0.25,
) {
    init {
        require(version.isNotBlank())
        require(minimumSamples >= 3 && minimumWindowSeconds > 0 && minimumFramesPerSecond > 0)
        require(maximumCadenceJitterFraction in 0.0..1.0)
        require(minimumMeanRed in 0.0..255.0 && maximumMeanRed in minimumMeanRed..255.0)
        require(minimumCoverageFraction in 0.0..1.0)
        require(maximumSaturatedFraction in 0.0..1.0 && maximumClippedFraction in 0.0..1.0)
        require(maximumMotionScore in 0.0..1.0)
        require(minimumPulsatileRms >= 0 && maximumNoiseToSignalRatio >= 0)
        require(minimumScore in 0.0..1.0)
        require(minimumHeartRateBeats >= 2 && minimumPrvWindowSeconds >= minimumWindowSeconds)
        require(minimumPrvIntervals >= 2)
        require(maximumPrvCadenceJitterFraction in 0.0..maximumCadenceJitterFraction)
        require(minimumBeatIntervalMillis > 0 && maximumBeatIntervalMillis > minimumBeatIntervalMillis)
        require(maximumPrvIntervalStepFraction in 0.0..1.0)
    }
}

data class PpgAnalysis(
    val quality: QualityReport,
    val heartRateBpm: MetricResult<Double>,
    val rmssdMs: MetricResult<Double>,
    val sdnnMs: MetricResult<Double>,
)

/** Pure deterministic processing. The caller owns a bounded, current capture window. */
class PpgPipeline(private val thresholds: QualityThresholds = QualityThresholds()) {
    fun analyze(samples: List<PpgSample>): PpgAnalysis {
        val quality = evaluateQuality(samples)
        if (!quality.passed) {
            val unavailable = MetricResult.Unavailable(MetricUnavailableReason.QUALITY_REJECTED)
            return PpgAnalysis(quality, unavailable, unavailable, unavailable)
        }

        val filtered = filteredRed(samples)
        val peaks = detectPeaks(samples, filtered)
        val intervalsMs = peaks.zipWithNext { a, b ->
            (samples[b].timestampNanos - samples[a].timestampNanos) / 1_000_000.0
        }
        val supportedIntervals = intervalsMs.all {
            it in thresholds.minimumBeatIntervalMillis..thresholds.maximumBeatIntervalMillis
        }
        // An otherwise regular burst at one end cannot represent the whole capture window.
        // Reuse the existing maximum supported beat interval; no new physiological cutoff.
        val edgeToleranceNanos = (thresholds.maximumBeatIntervalMillis * 1_000_000.0).toLong()
        val coversWindow = peaks.isNotEmpty() &&
            samples[peaks.first()].timestampNanos - samples.first().timestampNanos <= edgeToleranceNanos &&
            samples.last().timestampNanos - samples[peaks.last()].timestampNanos <= edgeToleranceNanos
        val heartRate = when {
            peaks.size < thresholds.minimumHeartRateBeats ->
                MetricResult.Unavailable(MetricUnavailableReason.INSUFFICIENT_BEATS)
            !supportedIntervals || !coversWindow ->
                MetricResult.Unavailable(MetricUnavailableReason.UNCERTAIN_BEAT_INTERVALS)
            else -> MetricResult.Available(60_000.0 / intervalsMs.median())
        }

        val durationSeconds = quality.validDurationMillis / 1_000.0
        val frameDeltas = samples.zipWithNext { a, b ->
            (b.timestampNanos - a.timestampNanos).toDouble()
        }
        val frameDeltaAverage = frameDeltas.average()
        val prvCadenceJitter = frameDeltas.maxOf { abs(it - frameDeltaAverage) } / frameDeltaAverage
        val prvReason = when {
            heartRate !is MetricResult.Available<*> -> MetricUnavailableReason.UNCERTAIN_BEAT_INTERVALS
            durationSeconds < thresholds.minimumPrvWindowSeconds -> MetricUnavailableReason.INSUFFICIENT_DURATION
            intervalsMs.size < thresholds.minimumPrvIntervals -> MetricUnavailableReason.INSUFFICIENT_BEATS
            prvCadenceJitter > thresholds.maximumPrvCadenceJitterFraction ->
                MetricUnavailableReason.UNCERTAIN_BEAT_INTERVALS
            !intervalsMs.hasStableSteps(thresholds.maximumPrvIntervalStepFraction) ->
                MetricUnavailableReason.UNCERTAIN_BEAT_INTERVALS
            else -> null
        }
        if (prvReason != null) {
            val unavailable = MetricResult.Unavailable(prvReason)
            return PpgAnalysis(quality, heartRate, unavailable, unavailable)
        }

        // Pulse rate variability (PRV) from PPG peak intervals; no ECG-HRV equivalence claim.
        val squaredDifferences = intervalsMs.zipWithNext { a, b -> (b - a).pow(2) }
        val rmssd = sqrt(squaredDifferences.average())
        val meanInterval = intervalsMs.average()
        val sdnn = sqrt(intervalsMs.sumOf { (it - meanInterval).pow(2) } / (intervalsMs.size - 1))
        return PpgAnalysis(quality, heartRate, MetricResult.Available(rmssd), MetricResult.Available(sdnn))
    }

    fun evaluateQuality(samples: List<PpgSample>): QualityReport {
        val issues = mutableSetOf<QualityIssue>()
        if (samples.size < thresholds.minimumSamples) issues += QualityIssue.INSUFFICIENT_SAMPLES
        val deltas = samples.zipWithNext { a, b -> b.timestampNanos - a.timestampNanos }
        if (deltas.any { it <= 0 }) issues += QualityIssue.INVALID_TIMESTAMPS
        val durationNanos = if (samples.size >= 2) samples.last().timestampNanos - samples.first().timestampNanos else 0L
        val durationSeconds = (durationNanos.coerceAtLeast(0L)) / 1_000_000_000.0
        if (durationSeconds < thresholds.minimumWindowSeconds) issues += QualityIssue.INSUFFICIENT_SAMPLES

        if (deltas.isNotEmpty() && QualityIssue.INVALID_TIMESTAMPS !in issues && durationSeconds > 0) {
            val averageDelta = deltas.average()
            val frameRate = (deltas.size / durationSeconds)
            val jitter = deltas.maxOf { abs(it - averageDelta) } / averageDelta
            if (frameRate < thresholds.minimumFramesPerSecond || jitter > thresholds.maximumCadenceJitterFraction) {
                issues += QualityIssue.UNSTABLE_FRAME_CADENCE
            }
        }

        if (samples.isNotEmpty()) {
            if (samples.any { !it.torchOn }) issues += QualityIssue.TORCH_OFF
            val meanRed = samples.map { it.red }.average()
            if (meanRed < thresholds.minimumMeanRed) issues += QualityIssue.TOO_DARK
            if (meanRed > thresholds.maximumMeanRed ||
                samples.map { it.saturatedFraction }.average() > thresholds.maximumSaturatedFraction
            ) issues += QualityIssue.SATURATED
            if (samples.map { it.coverageFraction }.average() < thresholds.minimumCoverageFraction) {
                issues += QualityIssue.POOR_CONTACT
            }
            if (samples.map { it.clippedFraction }.average() > thresholds.maximumClippedFraction) {
                issues += QualityIssue.CLIPPED
            }
            if (samples.map { it.motionScore }.average() > thresholds.maximumMotionScore) {
                issues += QualityIssue.EXCESSIVE_MOTION
            }
        }

        if (samples.size >= 3 && QualityIssue.INVALID_TIMESTAMPS !in issues) {
            val filtered = filteredRed(samples)
            val pulsatileRms = filtered.rootMeanSquare()
            if (pulsatileRms < thresholds.minimumPulsatileRms) issues += QualityIssue.LOW_PULSATILE_SIGNAL
            if (pulsatileRms > 0) {
                val noiseRms = samples.indices.map { i ->
                    val previous = filtered[(i - 1).coerceAtLeast(0)]
                    val next = filtered[(i + 1).coerceAtMost(filtered.lastIndex)]
                    filtered[i] - (previous + next) / 2.0
                }.rootMeanSquare()
                if (noiseRms / pulsatileRms > thresholds.maximumNoiseToSignalRatio) {
                    issues += QualityIssue.EXCESSIVE_NOISE
                }
            }
        }

        // Score is a transparent aggregate engineering signal, never a percent of health accuracy.
        val score = (1.0 - issues.size * 0.2).coerceIn(0.0, 1.0)
        if (score < thresholds.minimumScore) issues += QualityIssue.SCORE_BELOW_THRESHOLD
        return QualityReport(
            passed = issues.isEmpty(),
            score = score,
            issues = issues,
            evaluatedSamples = samples.size,
            validDurationMillis = (durationNanos.coerceAtLeast(0L) / 1_000_000L),
            pipelineVersion = thresholds.version,
        )
    }

    private fun filteredRed(samples: List<PpgSample>): List<Double> {
        val averageFps = if (samples.size >= 2) {
            (samples.size - 1) * 1_000_000_000.0 /
                (samples.last().timestampNanos - samples.first().timestampNanos).coerceAtLeast(1)
        } else 30.0
        val baselineRadius = (averageFps * 0.75).toInt().coerceIn(2, 90)
        val smoothRadius = (averageFps * 0.07).toInt().coerceIn(1, 8)
        val values = samples.map { it.red }
        val centered = values.indices.map { i -> values[i] - values.windowAverage(i, baselineRadius) }
        return centered.indices.map { centered.windowAverage(it, smoothRadius) }
    }

    private fun detectPeaks(samples: List<PpgSample>, signal: List<Double>): List<Int> {
        if (signal.size < 3) return emptyList()
        val floor = signal.rootMeanSquare() * 0.4
        val refractoryNanos = (thresholds.minimumBeatIntervalMillis * 1_000_000.0).toLong()
        val peaks = mutableListOf<Int>()
        for (i in 1 until signal.lastIndex) {
            if (signal[i] <= floor || signal[i] < signal[i - 1] || signal[i] <= signal[i + 1]) continue
            val previous = peaks.lastOrNull()
            if (previous == null || samples[i].timestampNanos - samples[previous].timestampNanos >= refractoryNanos) {
                peaks += i
            } else if (signal[i] > signal[previous]) {
                peaks[peaks.lastIndex] = i
            }
        }
        return peaks
    }
}

private fun List<Double>.windowAverage(center: Int, radius: Int): Double {
    val from = (center - radius).coerceAtLeast(0)
    val endExclusive = (center + radius + 1).coerceAtMost(size)
    var total = 0.0
    for (i in from until endExclusive) total += this[i]
    return total / (endExclusive - from)
}

private fun List<Double>.rootMeanSquare(): Double =
    if (isEmpty()) 0.0 else sqrt(sumOf { it * it } / size)

private fun List<Double>.median(): Double {
    val sorted = sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
}

private fun List<Double>.hasStableSteps(maxFraction: Double): Boolean {
    if (size < 2) return false
    return zipWithNext().all { (a, b) -> abs(b - a) / a <= maxFraction }
}
