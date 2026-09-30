package id.carda.core.measurement

import id.carda.core.model.MetricResult
import id.carda.core.model.MetricUnavailableReason
import id.carda.core.model.QualityIssue
import id.carda.core.model.QualityReport
import id.carda.core.ppg.PpgAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProvisionalMetricGateTest {
    private val unavailable = MetricResult.Unavailable(MetricUnavailableReason.INSUFFICIENT_DURATION)

    @Test fun rejectedQualityCannotShowEvenAnAccidentallyAvailableValue() {
        val rejected = PpgAnalysis(
            QualityReport(false, 0.6, setOf(QualityIssue.EXCESSIVE_MOTION), 300, 10_000, "ppg-0.1"),
            MetricResult.Available(72.0), unavailable, unavailable,
        )
        assertNull(provisionalHeartRate(rejected))
    }

    @Test fun onlyAvailableValueFromAcceptedQualityCanAppear() {
        val quality = QualityReport(true, 1.0, emptySet(), 300, 10_000, "ppg-0.1")
        assertNull(provisionalHeartRate(PpgAnalysis(quality, unavailable, unavailable, unavailable)))
        assertEquals(72.0, provisionalHeartRate(PpgAnalysis(quality,
            MetricResult.Available(72.0), unavailable, unavailable)))
    }
}
