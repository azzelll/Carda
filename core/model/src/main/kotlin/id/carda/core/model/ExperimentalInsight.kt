package id.carda.core.model

/** Transient preprocessed signal only. No frames, repository serialization or sample toString. */
class InsightSignalWindow(
    samples: FloatArray,
    val sampleRateHz: Double,
    val preprocessingVersion: String,
    val quality: QualityReport,
    val device: DeviceProfile,
    val conditionId: String,
) {
    private val samples = samples.copyOf()
    val size: Int get() = samples.size
    fun copySamples(): FloatArray = samples.copyOf()

    init {
        require(samples.size in 1..32_768 && samples.all { it.isFinite() })
        require(sampleRateHz.isFinite() && sampleRateHz > 0)
        require(preprocessingVersion.isNotBlank() && conditionId.isNotBlank())
    }

    override fun toString(): String = "InsightSignalWindow(samples=redacted)"
}

enum class InsightUnavailableReason {
    QUALITY_REJECTED, MODEL_UNAVAILABLE, INPUT_INCOMPATIBLE, METHOD_NOT_VALIDATED,
    UNSUPPORTED_CONDITIONS, CONFIDENCE_UNAVAILABLE, CONFIDENCE_BELOW_THRESHOLD, INFERENCE_FAILED,
}

sealed interface ExperimentalInsightResult {
    data class Unavailable(val reason: InsightUnavailableReason) : ExperimentalInsightResult

    /** Research timing from an estimated waveform, not measured ECG beats. Transient only. */
    class EstimatedTiming(
        peakSampleIndices: List<Int>,
        val meanRrMillis: Double,
        val methodVersion: String,
    ) {
        private val indices = peakSampleIndices.toList()
        fun copyPeakSampleIndices(): List<Int> = indices.toList()
        init {
            require(indices.size >= 3 && indices.first() >= 0)
            require(indices.zipWithNext().all { (a, b) -> b > a })
            require(meanRrMillis.isFinite() && meanRrMillis > 0 && methodVersion.isNotBlank())
        }
        override fun toString(): String = "EstimatedTiming(peaks=redacted)"
    }

    /** Estimated waveform, not measured ECG. Never persist a trace or expose a diagnosis here. */
    class Available(
        waveform: FloatArray,
        val modelVersion: String,
        val confidence: Double,
        val sampleRateHz: Double,
        val estimatedTiming: EstimatedTiming? = null,
    ) : ExperimentalInsightResult {
        private val waveform = waveform.copyOf()
        fun copyWaveform(): FloatArray = waveform.copyOf()
        init {
            require(waveform.isNotEmpty() && waveform.all { it.isFinite() })
            require(modelVersion.isNotBlank() && confidence.isFinite() && confidence in 0.0..1.0)
            require(sampleRateHz.isFinite() && sampleRateHz > 0.0)
            require(estimatedTiming?.copyPeakSampleIndices()?.lastOrNull()?.let { it < waveform.size } ?: true)
        }
        override fun toString(): String = "ExperimentalInsightResult.Available(waveform=redacted)"
    }
}

interface ExperimentalInsightRunner {
    suspend fun run(window: InsightSignalWindow): ExperimentalInsightResult
}
