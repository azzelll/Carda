package id.carda.feature.result

import id.carda.core.model.MetricKind
import id.carda.core.model.MetricResult
import id.carda.core.model.MetricUnavailableReason
import id.carda.core.model.ExperimentalInsightResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultPresentationTest {
    @Test fun unavailableMetricHasReasonAndNoFabricatedNumber() {
        val row = presentMetric(MetricKind.ESTIMATED_SPO2_PERCENT,
            MetricResult.Unavailable(MetricUnavailableReason.METHOD_NOT_VALIDATED))
        assertFalse(row.available)
        assertTrue(row.value.contains("Tidak dapat ditentukan"))
        assertFalse(row.value.contains("0%"))
    }

    @Test fun respiratoryRateHasBreathingUnitNotCardiacIntervalUnit() {
        val row = presentMetric(MetricKind.ESTIMATED_RESPIRATORY_RATE_BPM,
            MetricResult.Available(16.0))
        assertTrue(row.available)
        assertTrue(row.value.contains("napas/menit"))
        assertFalse(row.value.contains("ms"))
    }

    @Test fun experimentalWaveformAndCardiacRrStayDistinctFromRespiratoryRate() {
        val timing = ExperimentalInsightResult.EstimatedTiming(listOf(64, 192, 320),
            1_000.0, "research-fixture")
        val insight = presentInsight(ExperimentalInsightResult.Available(FloatArray(512),
            "fixture-model", 0.91, 128.0, timing))
        assertTrue(insight.available)
        assertTrue(insight.description.contains("bukan rekaman ECG"))
        assertTrue(insight.timing.contains("R–R jantung"))
        assertTrue(insight.timing.contains("ms"))
        assertFalse(insight.timing.contains("napas/menit"))
        val absent = presentInsight(null)
        assertFalse(absent.available)
        assertTrue(absent.timing.contains("tidak dapat ditentukan"))
    }
}
