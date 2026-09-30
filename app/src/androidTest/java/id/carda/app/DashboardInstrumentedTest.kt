package id.carda.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import id.carda.core.model.DeviceProfile
import id.carda.core.model.MeasurementSummary
import id.carda.core.model.MetricKind
import id.carda.core.model.MetricResult
import id.carda.core.model.MetricUnavailableReason
import id.carda.core.model.QualityReport
import id.carda.core.model.StoredMeasurement
import id.carda.core.model.SupportClassification
import id.carda.feature.dashboard.DashboardSummary
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

/** Synthetic accepted summaries test copy and calendar logic, not physiological validity. */
class DashboardInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 9, 29)
    private val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun comparisonRequiresThreePriorDaysAndDescribesNumbersOnly() {
        val history = listOf(
            stored(1, today.minusDays(3), 68.0),
            stored(2, today.minusDays(1), 70.0),
            stored(3, today.minusDays(2), 72.0),
            stored(4, today, 75.0),
        )
        compose.setContent { MaterialTheme { DashboardSummary(history, now) } }

        compose.onNodeWithText("Denyut terbaru lebih tinggi 5,0", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Ini perubahan angka, bukan penilaian kesehatan.", substring = true)
            .assertIsDisplayed()
    }

    @Test fun oneResultHasInsufficientBaselineMessage() {
        compose.setContent { MaterialTheme { DashboardSummary(listOf(stored(1, today, 75.0)), now) } }

        compose.onNodeWithText("Perbandingan denyut dengan tujuh hari sebelumnya belum cukup data.")
            .assertIsDisplayed()
    }

    @Test fun latestResultShowsAvailablePrvWithPpgProvenance() {
        compose.setContent { MaterialTheme { DashboardSummary(listOf(stored(1, today, 75.0, 28.0)), now) } }
        compose.onNodeWithText("PRV RMSSD: 28,0 ms").assertIsDisplayed()
        compose.onNodeWithText("PRV SDNN: tidak dapat ditentukan").assertIsDisplayed()
        compose.onNodeWithText("PRV berasal dari PPG dan bukan HRV ECG", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Perangkat: Fixture Synthetic; pipeline fixture-ppg.").assertIsDisplayed()
    }

    private fun stored(id: Long, date: LocalDate, bpm: Double, rmssd: Double? = null): StoredMeasurement {
        val timestamp = date.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        val quality = QualityReport(true, 0.9, emptySet(), 900, 30_000L, "fixture-ppg")
        val device = DeviceProfile(
            manufacturer = "Fixture", model = "Synthetic", androidApiLevel = 36,
            abi = "arm64-v8a", appVersion = "0.1.0", pipelineVersion = "fixture-ppg",
            hasRearCamera = true, hasTorch = true, analysisWidth = 720,
            analysisHeight = 1280, observedFramesPerSecond = 30.0,
            exposureDescription = null, flashDescription = null,
            rejectionReasons = emptySet(), lastQualityScore = 0.9,
            supportClassification = SupportClassification.COMPATIBLE,
        )
        return StoredMeasurement(id, MeasurementSummary(
            measuredAtEpochMillis = timestamp, validDurationMillis = 30_000L,
            quality = quality, deviceProfile = device,
            metrics = mapOf(
                MetricKind.HEART_RATE_BPM to MetricResult.Available(bpm),
                MetricKind.PRV_RMSSD_MS to (rmssd?.let { MetricResult.Available(it) }
                    ?: MetricResult.Unavailable(MetricUnavailableReason.INSUFFICIENT_DURATION)),
            ),
        ))
    }
}
