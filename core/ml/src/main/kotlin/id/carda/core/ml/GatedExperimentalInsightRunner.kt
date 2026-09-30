package id.carda.core.ml

import id.carda.core.model.ExperimentalInsightResult
import id.carda.core.model.ExperimentalInsightRunner
import id.carda.core.model.InsightSignalWindow
import id.carda.core.model.InsightUnavailableReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive

/** All eligibility checks precede loading. Failure never changes an independently valid HR. */
class GatedExperimentalInsightRunner(
    private val spec: InsightModelSpec?,
    evidence: InsightReleaseEvidence?,
    private val backendFactory: InsightBackendFactory,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val timingExtractor: ResearchEcgTimingExtractor = ResearchEcgTimingExtractor(),
) : ExperimentalInsightRunner {
    private val evidence = evidence?.copy(evaluatedConditions = evidence.evaluatedConditions.map {
        it.copy(supportClassifications = it.supportClassifications.toSet())
    }.toSet())
    override suspend fun run(window: InsightSignalWindow): ExperimentalInsightResult {
        fun unavailable(reason: InsightUnavailableReason) = ExperimentalInsightResult.Unavailable(reason)
        if (!window.quality.passed) return unavailable(InsightUnavailableReason.QUALITY_REJECTED)
        val model = spec ?: return unavailable(InsightUnavailableReason.MODEL_UNAVAILABLE)
        if (window.size != model.samples || window.sampleRateHz != model.sampleRateHz ||
            window.preprocessingVersion != model.preprocessingVersion ||
            window.quality.pipelineVersion != window.device.pipelineVersion ||
            window.quality.validDurationMillis < ((model.samples - 1) * 1_000 / model.sampleRateHz).toLong()
        ) return unavailable(InsightUnavailableReason.INPUT_INCOMPATIBLE)
        val release = evidence ?: return unavailable(InsightUnavailableReason.METHOD_NOT_VALIDATED)
        if (release.modelSha256 != model.sha256 || release.pipelineVersion != window.quality.pipelineVersion) {
            return unavailable(InsightUnavailableReason.METHOD_NOT_VALIDATED)
        }
        if (release.evaluatedConditions.none { it.matches(window.device, window.conditionId) }) {
            return unavailable(InsightUnavailableReason.UNSUPPORTED_CONDITIONS)
        }
        return withContext(dispatcher) {
            try {
                coroutineContext.ensureActive()
                backendFactory.open(model).use { backend ->
                    coroutineContext.ensureActive()
                    val prediction = backend.infer(window.copySamples())
                    coroutineContext.ensureActive()
                    val waveform = prediction.copyWaveform()
                    if (waveform.size != model.samples || waveform.any { !it.isFinite() }) {
                        return@withContext unavailable(InsightUnavailableReason.INFERENCE_FAILED)
                    }
                    val confidence = prediction.calibratedConfidence
                        ?: return@withContext unavailable(InsightUnavailableReason.CONFIDENCE_UNAVAILABLE)
                    if (!confidence.isFinite() || confidence !in 0.0..1.0) {
                        return@withContext unavailable(InsightUnavailableReason.INFERENCE_FAILED)
                    }
                    if (confidence < release.minimumConfidence) {
                        return@withContext unavailable(InsightUnavailableReason.CONFIDENCE_BELOW_THRESHOLD)
                    }
                    ExperimentalInsightResult.Available(
                        waveform, model.version, confidence, model.sampleRateHz,
                        timingExtractor.estimate(waveform, model.sampleRateHz),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                unavailable(InsightUnavailableReason.INFERENCE_FAILED)
            } catch (_: LinkageError) {
                unavailable(InsightUnavailableReason.INFERENCE_FAILED)
            }
        }
    }
}
