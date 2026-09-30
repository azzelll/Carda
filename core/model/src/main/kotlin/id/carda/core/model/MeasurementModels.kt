package id.carda.core.model

/** Stable, summary-only types. Camera frames and PPG samples must never enter persistence. */
enum class SupportClassification { COMPATIBLE, RESTRICTED, NOT_SUPPORTED }

enum class QualityIssue {
    NO_REAR_CAMERA,
    NO_TORCH,
    UNSUPPORTED_ANALYSIS_STREAM,
    SAMPLE_BUDGET_EXCEEDED,
    INSUFFICIENT_SAMPLES,
    INVALID_TIMESTAMPS,
    UNSTABLE_FRAME_CADENCE,
    TORCH_OFF,
    TOO_DARK,
    SATURATED,
    POOR_CONTACT,
    CLIPPED,
    EXCESSIVE_MOTION,
    EXCESSIVE_NOISE,
    LOW_PULSATILE_SIGNAL,
    SCORE_BELOW_THRESHOLD,
}

data class QualityReport(
    val passed: Boolean,
    val score: Double,
    val issues: Set<QualityIssue>,
    val evaluatedSamples: Int,
    val validDurationMillis: Long,
    val pipelineVersion: String,
) {
    init {
        require(score in 0.0..1.0)
        require(evaluatedSamples >= 0)
        require(validDurationMillis >= 0)
        require(pipelineVersion.isNotBlank())
        require(!passed || issues.isEmpty())
    }
}

data class DeviceProfile(
    val manufacturer: String,
    val model: String,
    val androidApiLevel: Int,
    val abi: String,
    val appVersion: String,
    val pipelineVersion: String,
    val hasRearCamera: Boolean,
    val hasTorch: Boolean,
    val analysisWidth: Int?,
    val analysisHeight: Int?,
    val observedFramesPerSecond: Double?,
    val exposureDescription: String?,
    val flashDescription: String?,
    val rejectionReasons: Set<QualityIssue>,
    val lastQualityScore: Double?,
    val supportClassification: SupportClassification,
) {
    init {
        require(androidApiLevel > 0)
        require(pipelineVersion.isNotBlank())
        require(analysisWidth == null || analysisWidth > 0)
        require(analysisHeight == null || analysisHeight > 0)
        require(observedFramesPerSecond == null ||
            (observedFramesPerSecond.isFinite() && observedFramesPerSecond > 0.0))
        require(lastQualityScore == null || lastQualityScore in 0.0..1.0)
    }
}

enum class MeasurementState {
    PREPARING, MEASURING, TOO_DARK, MOTION_DETECTED, POOR_CONTACT,
    PROCESSING, COMPLETE, RETRY, PERMISSION_DENIED, UNSUPPORTED_DEVICE, ERROR,
}

/** User-reported context only; it never changes the signal gate or implies a health condition. */
enum class MeasurementActivity(val label: String) {
    UNSPECIFIED("Tidak dinyatakan"),
    RESTING("Sedang istirahat"),
    RECENT_ACTIVITY("Baru beraktivitas"),
}

enum class CaptureDuration(val seconds: Int) { THIRTY_SECONDS(30), SIXTY_SECONDS(60) }

enum class MetricKind {
    HEART_RATE_BPM,
    PRV_RMSSD_MS,
    PRV_SDNN_MS,
    ESTIMATED_RESPIRATORY_RATE_BPM,
    ESTIMATED_SPO2_PERCENT,
    ESTIMATED_SYSTOLIC_MMHG,
    ESTIMATED_DIASTOLIC_MMHG,
}

enum class MetricUnavailableReason {
    QUALITY_REJECTED,
    INSUFFICIENT_DURATION,
    INSUFFICIENT_BEATS,
    UNCERTAIN_BEAT_INTERVALS,
    UNSUPPORTED_VALUE,
    METHOD_NOT_VALIDATED,
    MODEL_UNAVAILABLE,
    UNSUPPORTED_DEVICE,
}

sealed interface MetricResult<out T> {
    data class Available<T>(val value: T) : MetricResult<T>
    data class Unavailable(val reason: MetricUnavailableReason) : MetricResult<Nothing>
}

/** Only successful, quality-gated summaries may be persisted as history entries. */
data class MeasurementSummary(
    val measuredAtEpochMillis: Long,
    val validDurationMillis: Long,
    val quality: QualityReport,
    val deviceProfile: DeviceProfile,
    val metrics: Map<MetricKind, MetricResult<Double>>,
    val status: MeasurementState = MeasurementState.COMPLETE,
    val modelVersion: String? = null,
    val activity: MeasurementActivity = MeasurementActivity.UNSPECIFIED,
) {
    init {
        require(measuredAtEpochMillis > 0)
        require(validDurationMillis > 0)
        require(quality.passed)
        require(status == MeasurementState.COMPLETE)
        require(metrics.values.any { it is MetricResult.Available<*> })
        require(metrics.all { (kind, result) ->
            if (result !is MetricResult.Available<*>) true
            else {
                val value = result.value as Double
                value.isFinite() && value >= 0.0 &&
                    (kind != MetricKind.HEART_RATE_BPM || value > 0.0)
            }
        })
    }
}
