package id.carda.core.ppg

import id.carda.core.model.QualityReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ConcurrentModificationException
import kotlin.math.PI
import kotlin.math.sin

class ResearchRespirationEstimatorTest {
    private val pipeline = PpgPipeline()
    private val estimator = ResearchRespirationEstimator(globalPipeline = pipeline)

    @Test fun steadySyntheticModulationProducesOnlyAResearchCandidate() {
        for (breathsPerMinute in listOf(12.0, 18.0, 30.0)) {
            val samples = syntheticPulse(respiratoryBpm = breathsPerMinute)
            val quality = passedQuality(samples)
            val result = estimator.estimate(samples, quality)

            assertTrue("$breathsPerMinute: $result", result is ResearchRespirationResult.Candidate)
            val candidate = result as ResearchRespirationResult.Candidate
            assertEquals(breathsPerMinute, candidate.breathsPerMinute, 1.0)
            assertEquals("rr-research-0.2-baseline", candidate.methodVersion)
            assertTrue(candidate.evaluatedDurationMillis >= 58_000)
        }
    }

    @Test fun failedSqiTakesPriorityOverAnyCandidate() {
        val samples = syntheticPulse().map { it.copy(torchOn = false) }
        val quality = pipeline.evaluateQuality(samples)
        assertTrue(!quality.passed)
        assertEquals(
            ResearchRespirationResult.Unavailable(ResearchRespirationUnavailableReason.SQI_REJECTED),
            estimator.estimate(samples, quality),
        )
    }

    @Test fun shortWindowIsRejectedEvenWhenGlobalPpgSqiPasses() {
        val samples = syntheticPulse(seconds = 30)
        assertEquals(
            ResearchRespirationResult.Unavailable(ResearchRespirationUnavailableReason.INSUFFICIENT_DURATION),
            estimator.estimate(samples, passedQuality(samples)),
        )
    }

    @Test fun stricterRespiratoryCadenceRejectsSqiPassingJitter() {
        var timestamp = 0L
        val samples = syntheticPulse().mapIndexed { index, sample ->
            timestamp += if (index % 2 == 0) 29_000_000L else 37_666_666L
            sample.copy(timestampNanos = timestamp)
        }
        assertEquals(
            ResearchRespirationResult.Unavailable(ResearchRespirationUnavailableReason.UNSTABLE_CADENCE),
            estimator.estimate(samples, passedQuality(samples)),
        )
    }

    @Test fun briefMotionAndContactArtifactsCanFailRespiratoryGateIndependently() {
        val normal = syntheticPulse()
        val samples = normal.mapIndexed { index, sample ->
            if (index in 500..560) sample.copy(motionScore = 0.30, coverageFraction = 0.80) else sample
        }
        assertEquals(
            ResearchRespirationResult.Unavailable(ResearchRespirationUnavailableReason.ARTIFACTS),
            estimator.estimate(samples, passedQuality(samples)),
        )
    }

    @Test fun pulseAndItsHarmonicWithoutBreathingAreNotMistakenForRespiration() {
        val samples = syntheticPulse(respiratoryAmplitude = 0.0, includeHeartHarmonic = true)
        assertEquals(
            ResearchRespirationResult.Unavailable(ResearchRespirationUnavailableReason.WEAK_MODULATION),
            estimator.estimate(samples, passedQuality(samples)),
        )
    }

    @Test fun changingRateAcrossHalvesIsWithheld() {
        val samples = syntheticPulse(irregularRespiration = true)
        assertEquals(
            ResearchRespirationResult.Unavailable(ResearchRespirationUnavailableReason.INCONSISTENT_SUBWINDOWS),
            estimator.estimate(samples, passedQuality(samples)),
        )
    }

    @Test fun qualityFromAnotherCaptureCannotAuthorizeThisWindow() {
        val samples = syntheticPulse()
        val quality = passedQuality(samples)
        assertEquals(
            ResearchRespirationResult.Unavailable(ResearchRespirationUnavailableReason.QUALITY_REPORT_MISMATCH),
            estimator.estimate(samples.dropLast(1), quality),
        )
        assertEquals(
            ResearchRespirationResult.Unavailable(ResearchRespirationUnavailableReason.QUALITY_REPORT_MISMATCH),
            estimator.estimate(samples, quality.copy(validDurationMillis = quality.validDurationMillis + 100)),
        )
        val darkWithSameTimestamps = samples.map { it.copy(red = it.red * 0.1) }
        assertEquals(
            ResearchRespirationResult.Unavailable(ResearchRespirationUnavailableReason.SQI_REJECTED),
            estimator.estimate(darkWithSameTimestamps, quality),
        )
    }

    @Test fun oversizedInputIsRejectedBeforeSpectralWork() {
        val normal = syntheticPulse()
        val oversized = List(7_201) { index ->
            normal[index % normal.size].copy(timestampNanos = index * 8_333_333L)
        }
        val report = passedQuality(normal).copy(evaluatedSamples = oversized.size)
        assertEquals(
            ResearchRespirationResult.Unavailable(ResearchRespirationUnavailableReason.SAMPLE_BUDGET_EXCEEDED),
            estimator.estimate(oversized, report),
        )
    }

    @Test fun callerOwnedInputIsReadOnceBeforeQualityAndSpectrum() {
        val stable = syntheticPulse()
        val quality = passedQuality(stable)
        var reads = 0
        val oneReadSource = object : AbstractList<PpgSample>() {
            override val size: Int get() = stable.size
            override fun get(index: Int): PpgSample {
                reads += 1
                check(reads <= stable.size) { "source was read after the initial snapshot" }
                return stable[index]
            }
        }

        assertTrue(estimator.estimate(oneReadSource, quality) is ResearchRespirationResult.Candidate)
        assertEquals(stable.size, reads)
    }

    @Test fun sourceThatChangesWhileCopyingFailsClosed() {
        val stable = syntheticPulse()
        val brokenSource = object : AbstractList<PpgSample>() {
            override val size: Int get() = stable.size
            override fun get(index: Int): PpgSample {
                if (index == 5) throw ConcurrentModificationException("capture changed during copy")
                return stable[index]
            }
        }

        assertEquals(
            ResearchRespirationResult.Unavailable(ResearchRespirationUnavailableReason.QUALITY_REPORT_MISMATCH),
            estimator.estimate(brokenSource, passedQuality(stable)),
        )
    }

    @Test fun amplitudeOnlyBreathingModulationUsesSeparateResearchBranch() {
        val samples = syntheticPulse(amplitudeOnlyRespiration = true)
        val result = estimator.estimate(samples, passedQuality(samples))
        assertTrue("$result", result is ResearchRespirationResult.Candidate)
        val candidate = result as ResearchRespirationResult.Candidate
        assertEquals(18.0, candidate.breathsPerMinute, 1.0)
        assertEquals("rr-research-0.2-amplitude", candidate.methodVersion)
    }

    @Test fun amplitudeRateChangingAcrossHalvesIsWithheld() {
        val samples = syntheticPulse(amplitudeOnlyRespiration = true, irregularRespiration = true)
        assertEquals(
            ResearchRespirationResult.Unavailable(ResearchRespirationUnavailableReason.WEAK_MODULATION),
            estimator.estimate(samples, passedQuality(samples)),
        )
    }

    @Test fun respiratoryEstimatorUsesTheSameConfiguredSqiEvaluatorAsItsReport() {
        val samples = syntheticPulse()
        val configuredPipeline = PpgPipeline(QualityThresholds(version = "ppg-research-config-1"))
        val configuredReport = configuredPipeline.evaluateQuality(samples)
        assertTrue(configuredReport.passed)

        assertTrue(
            ResearchRespirationEstimator(globalPipeline = configuredPipeline)
                .estimate(samples, configuredReport) is ResearchRespirationResult.Candidate,
        )
        assertEquals(
            ResearchRespirationResult.Unavailable(ResearchRespirationUnavailableReason.QUALITY_REPORT_MISMATCH),
            estimator.estimate(samples, configuredReport),
        )
    }

    private fun passedQuality(samples: List<PpgSample>): QualityReport =
        pipeline.evaluateQuality(samples).also { assertTrue("global SQI: ${it.issues}", it.passed) }

    private fun syntheticPulse(
        seconds: Int = 60,
        respiratoryBpm: Double = 18.0,
        respiratoryAmplitude: Double = 2.0,
        includeHeartHarmonic: Boolean = false,
        irregularRespiration: Boolean = false,
        amplitudeOnlyRespiration: Boolean = false,
    ): List<PpgSample> = List(seconds * 30) { index ->
        val time = index / 30.0
        val respiratoryPhase = if (irregularRespiration) {
            if (time < seconds / 2.0) 2.0 * PI * 0.2 * time
            else 2.0 * PI * (0.2 * seconds / 2.0 + 0.4 * (time - seconds / 2.0))
        } else 2.0 * PI * respiratoryBpm / 60.0 * time
        val respiratoryWave = respiratoryAmplitude * sin(respiratoryPhase)
        PpgSample(
            timestampNanos = (time * 1_000_000_000.0).toLong(),
            red = 120.0 + (12.0 + if (amplitudeOnlyRespiration) respiratoryWave else 0.0) *
                sin(2.0 * PI * 1.2 * time) +
                (if (amplitudeOnlyRespiration) 0.0 else respiratoryWave) +
                (if (includeHeartHarmonic) 3.0 * sin(2.0 * PI * 2.4 * time) else 0.0),
            green = 75.0,
            blue = 50.0,
            coverageFraction = 0.95,
            saturatedFraction = 0.0,
            clippedFraction = 0.0,
            motionScore = 0.02,
            torchOn = true,
        )
    }
}
