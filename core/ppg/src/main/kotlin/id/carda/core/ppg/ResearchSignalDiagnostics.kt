package id.carda.core.ppg

import id.carda.core.model.QualityReport

/** Transient debug/research readout only. Never place these values in MetricResult or history. */
sealed interface ResearchDiagnosticValue {
    data class Candidate(val value: Double, val methodVersion: String) : ResearchDiagnosticValue
    data class Unavailable(val reasonCode: String) : ResearchDiagnosticValue
}

data class ResearchSignalDiagnostics(
    val respiratoryBreathsPerMinute: ResearchDiagnosticValue,
    /** Unitless red/green optical feature, never a saturation percentage. */
    val redGreenOpticalRatio: ResearchDiagnosticValue,
)

/** Caller must own an immutable current capture; both candidates apply independent gates. */
fun evaluateResearchSignalDiagnostics(
    samples: List<PpgSample>,
    quality: QualityReport,
    pipeline: PpgPipeline,
): ResearchSignalDiagnostics {
    val respiration = when (val result = ResearchRespirationEstimator(globalPipeline = pipeline)
        .estimate(samples, quality)) {
        is ResearchRespirationResult.Candidate -> ResearchDiagnosticValue.Candidate(
            result.breathsPerMinute, result.methodVersion)
        is ResearchRespirationResult.Unavailable -> ResearchDiagnosticValue.Unavailable(result.reason.name)
    }
    val oximetry = when (val result = ResearchOximetryFeatureExtractor(pipeline)
        .extract(samples, quality)) {
        is ResearchOximetryFeatureResult.Features -> ResearchDiagnosticValue.Candidate(
            result.redGreenRatioOfRatios, result.methodVersion)
        is ResearchOximetryFeatureResult.Unavailable -> ResearchDiagnosticValue.Unavailable(result.reason.name)
    }
    return ResearchSignalDiagnostics(respiration, oximetry)
}
