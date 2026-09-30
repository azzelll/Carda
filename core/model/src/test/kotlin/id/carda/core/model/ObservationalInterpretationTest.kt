package id.carda.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationalInterpretationTest {
    private val quality = QualityReport(true, 1.0, emptySet(), 900, 30_000, "fixture")
    private val device = DeviceProfile("Fixture", "Phone", 36, "arm64-v8a", "test", "fixture",
        true, true, 1280, 720, 30.0, null, null, emptySet(), 1.0,
        SupportClassification.COMPATIBLE)

    private fun summary(metrics: Map<MetricKind, MetricResult<Double>>,
                        activity: MeasurementActivity = MeasurementActivity.RESTING) =
        MeasurementSummary(1L, 30_000, quality, device, metrics, activity = activity)

    @Test fun acceptedMetricsProduceOnlyObservationsAndNoRiskLevel() {
        val report = ObservationalInterpretationEngine().evaluate(summary(mapOf(
            MetricKind.HEART_RATE_BPM to MetricResult.Available(72.0),
            MetricKind.PRV_RMSSD_MS to MetricResult.Available(30.0),
            MetricKind.PRV_SDNN_MS to MetricResult.Available(40.0),
        )))
        assertEquals("observation-0.1", report.policyVersion)
        assertEquals(listOf("ACTIVITY_RESTING", "PPG_HR_SOURCE", "PPG_PRV_SOURCE"),
            report.observations.map { it.ruleId })
        assertTrue(report.observations.all { it.sourceReference.isNotBlank() })
        assertTrue(report.overallRisk is OverallRiskAssessment.Unavailable)
        assertFalse(report.observations.any { it.message.contains("normal", ignoreCase = true) })
    }

    @Test fun unavailablePrvCannotCreateARecoveryOrAutonomicObservation() {
        val report = ObservationalInterpretationEngine().evaluate(summary(mapOf(
            MetricKind.HEART_RATE_BPM to MetricResult.Available(72.0),
            MetricKind.PRV_RMSSD_MS to MetricResult.Unavailable(MetricUnavailableReason.INSUFFICIENT_DURATION),
            MetricKind.PRV_SDNN_MS to MetricResult.Unavailable(MetricUnavailableReason.INSUFFICIENT_DURATION),
        ), MeasurementActivity.RECENT_ACTIVITY))
        assertEquals(listOf("ACTIVITY_RECENT", "PPG_HR_SOURCE"),
            report.observations.map { it.ruleId })
        assertTrue(report.overallRisk is OverallRiskAssessment.Unavailable)
    }
}
