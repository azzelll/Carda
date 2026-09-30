package id.carda.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import id.carda.core.model.DeviceProfile
import id.carda.core.model.ExperimentalInsightResult
import id.carda.core.model.MeasurementSummary
import id.carda.core.model.MetricKind
import id.carda.core.model.MetricResult
import id.carda.core.model.MetricUnavailableReason
import id.carda.core.model.QualityReport
import id.carda.core.model.SupportClassification
import id.carda.feature.result.ResultScreen
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A synthetic UI fixture; never evidence of a physical camera measurement. */
class ResearchResultInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun debugResearchTextIsSeparateFromExportableSummary() {
        val summary = syntheticSummary()
        var exported: MeasurementSummary? = null
        compose.setContent {
            MaterialTheme {
                ResultScreen(
                    summary = summary, storageMessage = "", exportMessage = "",
                    canShareExport = false,
                    researchNote = "KANDIDAT RISET BUILD DEBUG: rasio optik tanpa satuan, bukan SpO₂.",
                    onExport = { exported = it }, onShareExport = {}, onDone = {},
                )
            }
        }

        compose.onNodeWithText("KANDIDAT RISET BUILD DEBUG", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Unduh ringkasan PDF").performScrollTo().performClick()
        assertSame(summary, exported)
        assertTrue(exported!!.metrics[MetricKind.ESTIMATED_SPO2_PERCENT] is MetricResult.Unavailable)
    }

    @Test fun gatedSyntheticInsightShowsEstimatedWaveformAndRrWithoutChangingExport() {
        val summary = syntheticSummary()
        val waveform = FloatArray(512)
        for (peak in listOf(64, 192, 320, 448)) waveform[peak] = 1f
        val insight = ExperimentalInsightResult.Available(waveform, "fixture-model", 0.91, 128.0,
            ExperimentalInsightResult.EstimatedTiming(listOf(64, 192, 320, 448),
                1_000.0, "fixture-timing"))
        var exported: MeasurementSummary? = null
        compose.setContent {
            MaterialTheme {
                ResultScreen(summary, "", "", false, experimentalInsight = insight,
                    onExport = { exported = it }, onShareExport = {}, onDone = {})
            }
        }
        compose.onNodeWithText("Waveform ECG estimasi eksperimental", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Interval R–R jantung estimasi", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Waveform ECG estimasi dari PPG", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Unduh ringkasan PDF").performScrollTo().performClick()
        assertSame(summary, exported)
        assertTrue(exported!!.metrics[MetricKind.ESTIMATED_SPO2_PERCENT] is MetricResult.Unavailable)
    }

    private fun syntheticSummary() = MeasurementSummary(
            measuredAtEpochMillis = 1_780_000_000_000L,
            validDurationMillis = 60_000L,
            quality = QualityReport(true, 0.9, emptySet(), 1_800, 60_000L, "fixture-ppg"),
            deviceProfile = DeviceProfile(
                manufacturer = "Fixture", model = "Synthetic", androidApiLevel = 36,
                abi = "arm64-v8a", appVersion = "0.1.0", pipelineVersion = "fixture-ppg",
                hasRearCamera = true, hasTorch = true, analysisWidth = 720,
                analysisHeight = 1280, observedFramesPerSecond = 30.0,
                exposureDescription = null, flashDescription = null,
                rejectionReasons = emptySet(), lastQualityScore = 0.9,
                supportClassification = SupportClassification.COMPATIBLE,
            ),
            metrics = mapOf(
                MetricKind.HEART_RATE_BPM to MetricResult.Available(72.0),
                MetricKind.ESTIMATED_SPO2_PERCENT to
                    MetricResult.Unavailable(MetricUnavailableReason.METHOD_NOT_VALIDATED),
            ),
        )
}
