package id.carda.feature.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import id.carda.core.model.DeviceProfile
import id.carda.core.model.MeasurementSummary
import id.carda.core.model.MetricKind
import id.carda.core.model.MetricResult
import id.carda.core.model.QualityReport
import id.carda.core.model.StoredMeasurement
import id.carda.core.model.SupportClassification
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

class HistoryScreenInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun numericBaselineAndUnavailableMetricStayDistinct() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val history = listOf(
            record(1, today.minusDays(3), 70.0, zone),
            record(2, today.minusDays(2), 80.0, zone),
            record(3, today.minusDays(1), 90.0, zone),
            record(4, today, 100.0, zone),
        )
        compose.setContent {
            HistoryScreen(history, "", {}, false, {}, {}, {})
        }

        compose.onNodeWithText("Hasil terbaru hari ini lebih tinggi", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Rentang nilai pada grafik:", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sumbu waktu:", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sumbu nilai:", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("SpO₂").performScrollTo().performClick()
        compose.onNodeWithText("Belum ada data valid untuk metrik dan rentang ini.")
            .performScrollTo().assertIsDisplayed()
    }

    private fun record(id: Long, date: LocalDate, bpm: Double, zone: ZoneId): StoredMeasurement {
        val at = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val quality = QualityReport(true, 1.0, emptySet(), 900, 30_000, "synthetic-fixture")
        val device = DeviceProfile("Synthetic", "Fixture", 36, "arm64-v8a", "test",
            "synthetic-fixture", true, true, 1280, 720, 30.0, null, "test",
            emptySet(), 1.0, SupportClassification.COMPATIBLE)
        return StoredMeasurement(id, MeasurementSummary(at, 30_000, quality, device,
            mapOf(MetricKind.HEART_RATE_BPM to MetricResult.Available(bpm))))
    }
}
