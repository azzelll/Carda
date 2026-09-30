package id.carda.core.export

import id.carda.core.model.DeviceProfile
import id.carda.core.model.MeasurementSummary
import id.carda.core.model.MeasurementActivity
import id.carda.core.model.MetricKind
import id.carda.core.model.MetricResult
import id.carda.core.model.MetricUnavailableReason
import id.carda.core.model.QualityReport
import id.carda.core.model.SupportClassification
import java.time.ZoneId
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSummaryContentTest {
    @Test fun exportIncludesQualityVersionsDisclaimerAndUnavailableStatus() {
        val lines = PdfSummaryContent.lines(
            summary().copy(activity = MeasurementActivity.RECENT_ACTIVITY),
            ZoneId.of("Asia/Jakarta"),
        ).joinToString("\n")
        assertTrue(lines.contains("Kualitas sinyal: 90%"))
        assertTrue(lines.contains("Pipeline: ppg-0.1"))
        assertTrue(lines.contains("SpO2 perkiraan: tidak dapat ditentukan"))
        assertTrue(lines.contains("bukan diagnosis"))
        assertTrue(lines.contains("PRV berasal"))
        assertTrue(lines.contains("Kondisi sesi (pilihan pengguna): Baru beraktivitas"))
    }
}

internal fun summary(): MeasurementSummary {
    val quality = QualityReport(true, 0.9, emptySet(), 900, 30_000, "ppg-0.1")
    val profile = DeviceProfile("Example", "Phone", 36, "arm64-v8a", "0.1.0", "ppg-0.1",
        true, true, 1280, 720, 30.0, null, "torch on", emptySet(), 0.9,
        SupportClassification.COMPATIBLE)
    return MeasurementSummary(1_790_000_000_000, 30_000, quality, profile, mapOf(
        MetricKind.HEART_RATE_BPM to MetricResult.Available(72.0),
        MetricKind.ESTIMATED_SPO2_PERCENT to MetricResult.Unavailable(MetricUnavailableReason.METHOD_NOT_VALIDATED),
    ))
}
