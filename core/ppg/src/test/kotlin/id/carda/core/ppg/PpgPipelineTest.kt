package id.carda.core.ppg

import id.carda.core.model.MetricResult
import id.carda.core.model.MetricUnavailableReason
import id.carda.core.model.QualityIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PpgPipelineTest {
    private val pipeline = PpgPipeline()

    @Test fun regularSyntheticPulseProducesEngineeringEstimates() {
        val result = pipeline.analyze(regularPulse(seconds = 45))

        assertTrue(result.quality.passed)
        val hr = result.heartRateBpm as MetricResult.Available<Double>
        val rmssd = result.rmssdMs as MetricResult.Available<Double>
        val sdnn = result.sdnnMs as MetricResult.Available<Double>
        assertEquals(72.0, hr.value, 3.0)
        assertTrue(rmssd.value < 30.0)
        assertTrue(sdnn.value < 20.0)
        assertEquals("ppg-0.3", result.quality.pipelineVersion)
    }

    @Test fun shortWindowCanProduceHrButNotPrv() {
        val result = pipeline.analyze(regularPulse(seconds = 12))

        assertTrue(result.quality.passed)
        assertTrue(result.heartRateBpm is MetricResult.Available<*>)
        assertEquals(
            MetricResult.Unavailable(MetricUnavailableReason.INSUFFICIENT_DURATION),
            result.rmssdMs,
        )
        assertEquals(result.rmssdMs, result.sdnnMs)
    }

    @Test fun earlyPulseFollowedByFlatSignalCannotBecomeWholeWindowHeartRate() {
        val partialPulse = regularPulse(seconds = 45).map { sample ->
            if (sample.timestampNanos >= 12_000_000_000L) sample.copy(red = 120.0) else sample
        }

        val result = pipeline.analyze(partialPulse)

        // The global RMS gate alone can pass because the first 12 seconds are pulsatile.
        assertTrue(result.quality.passed)
        assertEquals(MetricResult.Unavailable(MetricUnavailableReason.UNCERTAIN_BEAT_INTERVALS),
            result.heartRateBpm)
        assertEquals(result.heartRateBpm, result.rmssdMs)
        assertEquals(result.heartRateBpm, result.sdnnMs)
    }

    @Test fun flatSignalFollowedByLatePulseCannotBecomeWholeWindowHeartRate() {
        val partialPulse = regularPulse(seconds = 45).map { sample ->
            if (sample.timestampNanos < 33_000_000_000L) sample.copy(red = 120.0) else sample
        }

        val result = pipeline.analyze(partialPulse)

        assertTrue(result.quality.passed)
        assertEquals(MetricResult.Unavailable(MetricUnavailableReason.UNCERTAIN_BEAT_INTERVALS),
            result.heartRateBpm)
        assertEquals(result.heartRateBpm, result.rmssdMs)
        assertEquals(result.heartRateBpm, result.sdnnMs)
    }

    @Test fun everyQualityRejectionBlocksEveryMetric() {
        val normal = regularPulse(seconds = 45)
        val scenarios = listOf(
            QualityIssue.TOO_DARK to normal.map { it.copy(red = 10.0) },
            QualityIssue.SATURATED to normal.map { it.copy(red = 250.0, saturatedFraction = 0.8) },
            QualityIssue.CLIPPED to normal.map { it.copy(clippedFraction = 0.8) },
            QualityIssue.POOR_CONTACT to normal.map { it.copy(coverageFraction = 0.3) },
            QualityIssue.EXCESSIVE_MOTION to normal.map { it.copy(motionScore = 0.8) },
            QualityIssue.TORCH_OFF to normal.map { it.copy(torchOn = false) },
            QualityIssue.LOW_PULSATILE_SIGNAL to normal.map { it.copy(red = 120.0) },
            QualityIssue.UNSTABLE_FRAME_CADENCE to irregularCadencePulse(),
            QualityIssue.INSUFFICIENT_SAMPLES to normal.take(30),
        )

        scenarios.forEach { (expected, input) ->
            val result = pipeline.analyze(input)
            assertFalse("$expected should fail", result.quality.passed)
            assertTrue("$expected missing", expected in result.quality.issues)
            val blocked = MetricResult.Unavailable(MetricUnavailableReason.QUALITY_REJECTED)
            assertEquals(blocked, result.heartRateBpm)
            assertEquals(blocked, result.rmssdMs)
            assertEquals(blocked, result.sdnnMs)
        }
    }

    @Test fun repeatedTimestampsAreRejectedWithoutCalculatingMetrics() {
        val samples = regularPulse(seconds = 45).toMutableList()
        samples[30] = samples[30].copy(timestampNanos = samples[29].timestampNanos)

        val result = pipeline.analyze(samples)

        assertTrue(QualityIssue.INVALID_TIMESTAMPS in result.quality.issues)
        assertTrue(result.heartRateBpm is MetricResult.Unavailable)
    }

    @Test fun stricterPrvCadenceGateDoesNotDiscardValidHr() {
        var timestamp = 0L
        val jittered = regularPulse(seconds = 45).mapIndexed { index, sample ->
            timestamp += if (index % 2 == 0) 29_000_000L else 37_666_666L
            sample.copy(timestampNanos = timestamp)
        }

        val result = pipeline.analyze(jittered)

        assertTrue(result.quality.passed)
        assertTrue(result.heartRateBpm is MetricResult.Available<*>)
        assertEquals(
            MetricResult.Unavailable(MetricUnavailableReason.UNCERTAIN_BEAT_INTERVALS),
            result.rmssdMs,
        )
        assertEquals(result.rmssdMs, result.sdnnMs)
    }

    private fun regularPulse(seconds: Int, bpm: Double = 72.0): List<PpgSample> {
        val frameCount = seconds * 30
        return List(frameCount) { index ->
            val t = index / 30.0
            PpgSample(
                timestampNanos = (t * 1_000_000_000).toLong(),
                red = 120.0 + 12.0 * sin(2.0 * PI * bpm / 60.0 * t),
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

    private fun irregularCadencePulse(): List<PpgSample> {
        var timestamp = 0L
        return regularPulse(seconds = 45).mapIndexed { index, sample ->
            timestamp += if (index == 100) 1_000_000_000L else 33_333_333L
            sample.copy(timestampNanos = timestamp)
        }
    }
}
