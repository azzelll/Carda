package id.carda.core.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchEcgTimingExtractorTest {
    private val extractor = ResearchEcgTimingExtractor()

    @Test fun regularPositiveAndInvertedSyntheticSpikesHaveMillisecondIntervals() {
        for (polarity in listOf(1f, -1f)) {
            val signal = FloatArray(512)
            for (peak in listOf(64, 192, 320, 448)) {
                signal[peak - 1] = polarity * 0.3f
                signal[peak] = polarity
                signal[peak + 1] = polarity * 0.3f
            }
            val result = extractor.estimate(signal, 128.0)!!
            assertEquals(listOf(64, 192, 320, 448), result.copyPeakSampleIndices())
            assertEquals(1_000.0, result.meanRrMillis, 0.001)
            assertEquals("ecg-timing-research-0.1", result.methodVersion)
            assertTrue(result.toString().contains("redacted"))
        }
    }

    @Test fun flatInvalidAndTooShortWaveformsWithholdTiming() {
        assertNull(extractor.estimate(FloatArray(512), 128.0))
        assertNull(extractor.estimate(FloatArray(127), 128.0))
        assertNull(extractor.estimate(FloatArray(512).also { it[30] = Float.NaN }, 128.0))
        assertNull(extractor.estimate(FloatArray(512), Double.NaN))
    }

    @Test fun closelySpacedArtifactsCannotBecomeAdditionalPeaks() {
        val signal = FloatArray(512)
        for (peak in listOf(64, 192, 320, 448)) signal[peak] = 1f
        signal[78] = 0.8f
        signal[206] = 0.8f
        val result = extractor.estimate(signal, 128.0)!!
        assertEquals(listOf(64, 192, 320, 448), result.copyPeakSampleIndices())
    }

    @Test fun unsupportedIntervalsOrTooFewPeaksWithholdTiming() {
        val tooFew = FloatArray(512)
        tooFew[64] = 1f
        tooFew[192] = 1f
        assertNull(extractor.estimate(tooFew, 128.0))
        val tooClose = FloatArray(512)
        for (peak in listOf(64, 99, 192, 320)) tooClose[peak] = 1f
        assertNull(extractor.estimate(tooClose, 128.0))
    }
}
