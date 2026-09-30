package id.carda.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import id.carda.core.model.LocalProfile
import id.carda.core.model.DeviceProfile
import id.carda.core.model.MeasurementSummary
import id.carda.core.model.MetricKind
import id.carda.core.model.MetricResult
import id.carda.core.model.QualityReport
import id.carda.core.model.SupportClassification
import id.carda.feature.history.HistoryScreen
import id.carda.feature.profile.ProfileScreen
import id.carda.feature.result.ResultScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Emulator UI semantics/layout checks; not a human TalkBack or usability study. */
class AccessibilityInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun historyMetricAndPeriodExposeSelectedState() {
        compose.setContent {
            MaterialTheme {
                HistoryScreen(emptyList(), "", {}, false, {}, {}, {})
            }
        }

        compose.onNodeWithText("✓ Denyut").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onNodeWithText("PRV RMSSD").performScrollTo().performClick()
        compose.onNodeWithText("✓ PRV RMSSD").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onNodeWithText("Denyut").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        compose.onNodeWithText("✓ 7 hari").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onNodeWithText("30 hari").performScrollTo().performClick()
        compose.onNodeWithText("✓ 30 hari").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    @Test fun reminderChoicesFitNarrowLargeTextViewport() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp).height(600.dp)) {
                        ProfileScreen(LocalProfile("fixture-account"), null, null, true, "", {}, {}, {}, {}, {})
                    }
                }
            }
        }

        listOf("08.00", "12.00", "18.00").forEach { label ->
            val choice = compose.onNodeWithText(label, substring = false).performScrollTo().assertIsDisplayed()
            val bounds = choice.getUnclippedBoundsInRoot()
            assertTrue("$label clipped horizontally: $bounds", bounds.left >= 0.dp && bounds.right <= 320.dp)
            choice.performClick()
        }
    }

    @Test fun historyPeriodChoicesFitNarrowLargeTextViewport() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp).height(600.dp)) {
                        HistoryScreen(emptyList(), "", {}, false, {}, {}, {})
                    }
                }
            }
        }

        listOf("Hari ini", "✓ 7 hari", "30 hari", "3 bulan").forEach { label ->
            val choice = compose.onNodeWithText(label, substring = false).performScrollTo().assertIsDisplayed()
            val bounds = choice.getUnclippedBoundsInRoot()
            assertTrue("$label clipped horizontally: $bounds", bounds.left >= 0.dp && bounds.right <= 320.dp)
        }
    }

    @Test fun resultDisclaimerAndExitRemainReachableAtLargeText() {
        val quality = QualityReport(true, 0.9, emptySet(), 900, 30_000, "fixture-ppg")
        val device = DeviceProfile("Fixture", "Phone", 36, "arm64-v8a", "test", "fixture-ppg",
            true, true, 1280, 720, 30.0, null, null, emptySet(), 0.9,
            SupportClassification.COMPATIBLE)
        val summary = MeasurementSummary(1_790_000_000_000L, 30_000, quality, device,
            mapOf(MetricKind.HEART_RATE_BPM to MetricResult.Available(72.0)))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp).height(600.dp)) {
                        ResultScreen(summary, "", "", false, onExport = {}, onShareExport = {}, onDone = {})
                    }
                }
            }
        }
        compose.onNodeWithText("Hasil pengukuran").assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText("Carda bukan alat diagnosis", substring = true)
            .performScrollTo().assertIsDisplayed()
        val exit = compose.onNodeWithText("Kembali ke beranda").performScrollTo().assertIsDisplayed()
        val bounds = exit.getUnclippedBoundsInRoot()
        assertTrue("Result exit clipped horizontally: $bounds", bounds.left >= 0.dp && bounds.right <= 320.dp)
    }
}
