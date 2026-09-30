package id.carda.core.ppg

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchSignalDiagnosticsTest {
    private val pipeline = PpgPipeline()

    @Test fun researchCandidatesRemainSeparateWhenRespirationWindowIsShort() {
        val samples = pulse(40)
        val diagnostics = evaluateResearchSignalDiagnostics(samples,
            pipeline.evaluateQuality(samples), pipeline)

        assertTrue(diagnostics.respiratoryBreathsPerMinute is ResearchDiagnosticValue.Unavailable)
        assertTrue(diagnostics.redGreenOpticalRatio is ResearchDiagnosticValue.Candidate)
    }

    @Test fun rejectedGlobalQualityWithholdsBothResearchCandidates() {
        val samples = pulse(40).map { it.copy(motionScore = 0.8) }
        val diagnostics = evaluateResearchSignalDiagnostics(samples,
            pipeline.evaluateQuality(samples), pipeline)

        assertTrue(diagnostics.respiratoryBreathsPerMinute is ResearchDiagnosticValue.Unavailable)
        assertTrue(diagnostics.redGreenOpticalRatio is ResearchDiagnosticValue.Unavailable)
    }

    private fun pulse(seconds: Int): List<PpgSample> = List(seconds * 30) { index ->
        val time = index / 30.0
        val wave = sin(2.0 * PI * 1.2 * time)
        PpgSample(
            timestampNanos = (time * 1_000_000_000).toLong(),
            red = 120.0 + 12.0 * wave,
            green = 75.0 + 6.0 * wave,
            blue = 40.0,
            coverageFraction = 0.95,
            saturatedFraction = 0.0,
            clippedFraction = 0.0,
            motionScore = 0.02,
            torchOn = true,
        )
    }
}
