package id.carda.core.ml

import id.carda.core.model.DeviceProfile
import id.carda.core.model.SupportClassification

data class InsightModelSpec(
    val version: String,
    val sha256: String,
    val bytes: Long,
    val samples: Int,
    val sampleRateHz: Double,
    val preprocessingVersion: String,
) {
    init {
        require(version.isNotBlank() && sha256.matches(Regex("[0-9a-f]{64}")))
        require(bytes in 16..268_435_456 && samples in 1..32_768)
        require(sampleRateHz.isFinite() && sampleRateHz > 0 && preprocessingVersion.isNotBlank())
    }
}

/** Exact evaluated configuration. No wildcard or all-device validation assumption. */
data class EvaluatedInsightConditions(
    val manufacturer: String,
    val model: String,
    val androidApiLevel: Int,
    val appVersion: String,
    val analysisWidth: Int,
    val analysisHeight: Int,
    val conditionId: String,
    val abi: String,
    val minimumObservedFps: Double,
    val maximumObservedFps: Double,
    val supportClassifications: Set<SupportClassification>,
) {
    init {
        require(listOf(manufacturer, model, appVersion, conditionId, abi).all { it.isNotBlank() })
        require(androidApiLevel > 0 && analysisWidth > 0 && analysisHeight > 0)
        require(minimumObservedFps.isFinite() && minimumObservedFps > 0)
        require(maximumObservedFps.isFinite() && maximumObservedFps >= minimumObservedFps)
        require(supportClassifications.isNotEmpty() && SupportClassification.NOT_SUPPORTED !in supportClassifications)
    }
    fun matches(device: DeviceProfile, condition: String): Boolean =
        manufacturer == device.manufacturer && model == device.model && androidApiLevel == device.androidApiLevel &&
            appVersion == device.appVersion && analysisWidth == device.analysisWidth &&
            analysisHeight == device.analysisHeight && conditionId == condition && abi == device.abi &&
            device.hasRearCamera && device.hasTorch && device.rejectionReasons.isEmpty() &&
            device.supportClassification in supportClassifications &&
            device.observedFramesPerSecond?.let { it in minimumObservedFps..maximumObservedFps } == true
}

/** References must point to reviewed evidence, not a build or a sensor-only dataset smoke test. */
data class InsightReleaseEvidence(
    val modelSha256: String,
    val pipelineVersion: String,
    val physiologyReportReference: String,
    val cameraDomainReportReference: String,
    val confidenceCalibrationReference: String,
    val wordingReviewReference: String,
    val distributionApprovalReference: String,
    val minimumConfidence: Double,
    val evaluatedConditions: Set<EvaluatedInsightConditions>,
) {
    init {
        require(modelSha256.matches(Regex("[0-9a-f]{64}")))
        require(listOf(pipelineVersion, physiologyReportReference, cameraDomainReportReference,
            confidenceCalibrationReference, wordingReviewReference, distributionApprovalReference).all { it.isNotBlank() })
        require(minimumConfidence.isFinite() && minimumConfidence in 0.0..1.0)
        require(evaluatedConditions.isNotEmpty())
    }
}

/** Current waveform-only research checkpoint deliberately has no confidence value. */
class InsightPrediction(waveform: FloatArray, val calibratedConfidence: Double?) {
    private val waveform = waveform.copyOf()
    fun copyWaveform(): FloatArray = waveform.copyOf()
    override fun toString(): String = "InsightPrediction(waveform=redacted)"
}

interface InsightBackend : AutoCloseable {
    fun infer(samples: FloatArray): InsightPrediction
}

fun interface InsightBackendFactory {
    fun open(spec: InsightModelSpec): InsightBackend
}
