package id.carda.core.export

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.carda.core.model.DeviceProfile
import id.carda.core.model.MeasurementSummary
import id.carda.core.model.MetricKind
import id.carda.core.model.MetricResult
import id.carda.core.model.QualityReport
import id.carda.core.model.SupportClassification
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfExporterInstrumentedTest {
    @Test fun writesReadableSinglePagePdf() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = File(context.cacheDir, "summary-test.pdf")
        file.outputStream().use { PdfSummaryExporter().write(it, summary()) }
        assertTrue(file.inputStream().use { it.readNBytes(4).contentEquals("%PDF".toByteArray()) })
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertEquals(1, renderer.pageCount)
        }
        file.delete()
    }

    private fun summary(): MeasurementSummary {
        val quality = QualityReport(true, 0.9, emptySet(), 900, 30_000, "ppg-0.1")
        val profile = DeviceProfile("Example", "Phone", 36, "arm64-v8a", "0.1.0", "ppg-0.1",
            true, true, 1280, 720, 30.0, null, "torch on", emptySet(), 0.9,
            SupportClassification.COMPATIBLE)
        return MeasurementSummary(1_790_000_000_000, 30_000, quality, profile,
            mapOf(MetricKind.HEART_RATE_BPM to MetricResult.Available(72.0)))
    }
}
