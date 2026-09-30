package id.carda.core.ppg

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchOximetryFeaturesTest {
    private val pipeline = PpgPipeline()
    private val extractor = ResearchOximetryFeatureExtractor(pipeline)

    @Test fun stableRedGreenPulseProducesOnlyUnitlessOpticalFeature() {
        val samples = pulse()
        val result = extractor.extract(samples, pipeline.evaluateQuality(samples))

        assertTrue(result is ResearchOximetryFeatureResult.Features)
        val feature = result as ResearchOximetryFeatureResult.Features
        assertEquals(1.25, feature.redGreenRatioOfRatios, 0.10)
        assertEquals(1.2, feature.pulseFrequencyHz, 0.05)
        assertEquals("spo2-feature-research-0.2", feature.methodVersion)
    }

    @Test fun globalQualityFailureBlocksFeature() {
        val bad = pulse().map { it.copy(coverageFraction = 0.2) }
        val result = extractor.extract(bad, pipeline.evaluateQuality(bad))
        assertEquals(ResearchOximetryFeatureResult.Unavailable(
            OximetryFeatureUnavailableReason.SQI_REJECTED), result)
    }

    @Test fun missingGreenPulseIsWithheldEvenWhenRedSqIAndHeartRatePass() {
        val samples = pulse().map { it.copy(green = 75.0) }
        assertTrue(pipeline.evaluateQuality(samples).passed)
        val result = extractor.extract(samples, pipeline.evaluateQuality(samples))
        assertEquals(ResearchOximetryFeatureResult.Unavailable(
            OximetryFeatureUnavailableReason.CHANNEL_UNUSABLE), result)
    }

    @Test fun differentHalfWindowChannelRatiosAreWithheld() {
        val samples = pulse().mapIndexed { i, sample ->
            if (i < 600) sample else sample.copy(green = 75.0 +
                2.0 * sin(2.0 * PI * 1.2 * i / 30.0))
        }
        val result = extractor.extract(samples, pipeline.evaluateQuality(samples))
        assertEquals(ResearchOximetryFeatureResult.Unavailable(
            OximetryFeatureUnavailableReason.UNSTABLE_CHANNEL_RATIO), result)
    }

    @Test fun changedRedWindowCannotReuseOldQualityReport() {
        val samples = pulse()
        val report = pipeline.evaluateQuality(samples)
        val altered = samples.map { it.copy(red = 10.0) }
        val result = extractor.extract(altered, report)
        assertEquals(ResearchOximetryFeatureResult.Unavailable(
            OximetryFeatureUnavailableReason.QUALITY_REPORT_MISMATCH), result)
    }

    @Test fun shortWindowCannotProduceOpticalFeature() {
        val samples = pulse(seconds = 12)
        val result = extractor.extract(samples, pipeline.evaluateQuality(samples))
        assertEquals(ResearchOximetryFeatureResult.Unavailable(
            OximetryFeatureUnavailableReason.INSUFFICIENT_DURATION), result)
    }

    @Test fun greenPixelClippingWithUsableRedWithholdsOpticalFeature() {
        val samples = pulse().map { it.copy(greenClippedFraction = 0.25) }
        assertTrue(pipeline.evaluateQuality(samples).passed)
        val result = extractor.extract(samples, pipeline.evaluateQuality(samples))
        assertEquals(ResearchOximetryFeatureResult.Unavailable(
            OximetryFeatureUnavailableReason.CHANNEL_UNUSABLE), result)
    }

    private fun pulse(seconds: Int = 40): List<PpgSample> = List(seconds * 30) { index ->
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
