package id.carda.core.model

import org.junit.Assert.assertThrows
import org.junit.Test

class MeasurementModelsTest {
    @Test fun historySummaryRejectsFailedQuality() {
        val failed = quality(passed = false)
        assertThrows(IllegalArgumentException::class.java) {
            summary(failed, mapOf(MetricKind.HEART_RATE_BPM to MetricResult.Available(72.0)))
        }
    }

    @Test fun historySummaryRejectsOnlyUnavailableMetrics() {
        assertThrows(IllegalArgumentException::class.java) {
            summary(
                quality(passed = true),
                mapOf(MetricKind.HEART_RATE_BPM to
                    MetricResult.Unavailable(MetricUnavailableReason.INSUFFICIENT_BEATS)),
            )
        }
    }

    @Test fun historySummaryRejectsZeroOrNonfiniteHeartRate() {
        listOf(0.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                summary(
                    quality(passed = true),
                    mapOf(MetricKind.HEART_RATE_BPM to MetricResult.Available(value)),
                )
            }
        }
    }

    @Test fun historySummaryRejectsAnIncompleteSession() {
        assertThrows(IllegalArgumentException::class.java) {
            summary(
                quality(passed = true),
                mapOf(MetricKind.HEART_RATE_BPM to MetricResult.Available(72.0)),
                MeasurementState.RETRY,
            )
        }
    }

    private fun summary(
        quality: QualityReport,
        metrics: Map<MetricKind, MetricResult<Double>>,
        status: MeasurementState = MeasurementState.COMPLETE,
    ) = MeasurementSummary(
        measuredAtEpochMillis = 1L,
        validDurationMillis = 12_000L,
        quality = quality,
        deviceProfile = DeviceProfile(
            manufacturer = "Synthetic",
            model = "Fixture",
            androidApiLevel = 36,
            abi = "arm64-v8a",
            appVersion = "test",
            pipelineVersion = "ppg-0.1",
            hasRearCamera = true,
            hasTorch = true,
            analysisWidth = 1280,
            analysisHeight = 720,
            observedFramesPerSecond = 30.0,
            exposureDescription = null,
            flashDescription = "torch on",
            rejectionReasons = emptySet(),
            lastQualityScore = 1.0,
            supportClassification = SupportClassification.COMPATIBLE,
        ),
        metrics = metrics,
        status = status,
    )

    private fun quality(passed: Boolean) = QualityReport(
        passed = passed,
        score = if (passed) 1.0 else 0.2,
        issues = if (passed) emptySet() else setOf(QualityIssue.TOO_DARK),
        evaluatedSamples = 360,
        validDurationMillis = 12_000L,
        pipelineVersion = "ppg-0.1",
    )
}
