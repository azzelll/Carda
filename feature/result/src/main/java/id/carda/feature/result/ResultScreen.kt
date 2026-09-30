package id.carda.feature.result

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.carda.core.model.ExperimentalInsightResult
import id.carda.core.model.MeasurementSummary
import id.carda.core.model.MetricKind
import id.carda.core.model.ObservationalInterpretationEngine
import id.carda.core.model.OverallRiskAssessment
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Results only receive an accepted summary; unavailable metrics remain explicit. */
@Composable
fun ResultScreen(
    summary: MeasurementSummary,
    storageMessage: String,
    exportMessage: String,
    canShareExport: Boolean,
    researchNote: String? = null,
    experimentalInsight: ExperimentalInsightResult? = null,
    onExport: (MeasurementSummary) -> Unit,
    onShareExport: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onRetrySave: (() -> Unit)? = null,
) {
    val recordedAt = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))
        .format(Instant.ofEpochMilli(summary.measuredAtEpochMillis).atZone(ZoneId.systemDefault()))
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Hasil pengukuran", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() })
        Text("$recordedAt waktu perangkat · ${summary.validDurationMillis / 1000} detik sinyal valid")
        Text("Kondisi yang dipilih: ${summary.activity.label}. Ketepatan untuk kondisi ini belum dibandingkan dengan alat referensi.")
        Text("Kualitas sinyal ${(summary.quality.score * 100).toInt()}% (skor teknis, bukan akurasi kesehatan).")
        if (storageMessage.isNotBlank()) Text(storageMessage)
        if (onRetrySave != null) Button(onClick = onRetrySave) { Text("Coba simpan kembali") }
        if (exportMessage.isNotBlank()) Text(exportMessage)
        MetricKind.entries.forEach { kind ->
            val row = presentMetric(kind, summary.metrics[kind])
            Text(row.title, style = MaterialTheme.typography.titleMedium)
            Text(row.value)
        }
        val interpretation = ObservationalInterpretationEngine().evaluate(summary)
        Text("Ringkasan konteks", style = MaterialTheme.typography.titleMedium)
        interpretation.observations.forEach { Text(it.message) }
        (interpretation.overallRisk as? OverallRiskAssessment.Unavailable)?.let {
            Text(it.reason)
        }
        Text("PRV berasal dari interval denyut PPG dan tidak otomatis setara dengan HRV ECG.")
        val insightText = presentInsight(experimentalInsight)
        Text(insightText.description)
        if (experimentalInsight is ExperimentalInsightResult.Available) {
            EstimatedWaveform(experimentalInsight)
        }
        Text(insightText.timing)
        if (researchNote != null) {
            Text(researchNote, style = MaterialTheme.typography.bodySmall)
        }
        Text("Perangkat: ${summary.deviceProfile.manufacturer} ${summary.deviceProfile.model}; pipeline ${summary.quality.pipelineVersion}.")
        Text("Informasi wellness saja. Carda bukan alat diagnosis, layanan darurat, atau dasar mengubah pengobatan.")
        Button(onClick = { onExport(summary) }) { Text("Unduh ringkasan PDF") }
        if (canShareExport) Button(onClick = onShareExport) { Text("Bagikan PDF yang baru disimpan") }
        Button(onClick = onDone) { Text("Kembali ke beranda") }
    }
}

/** Horizontal navigation over an estimated trace only; never the camera capture PPG graph. */
@Composable
private fun EstimatedWaveform(insight: ExperimentalInsightResult.Available) {
    val values = insight.copyWaveform()
    val peaks = insight.estimatedTiming?.copyPeakSampleIndices().orEmpty()
    val minimum = values.min()
    val range = (values.max() - minimum).takeIf { it > 0f } ?: 1f
    val traceColor = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.tertiary
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics {
        contentDescription = "Waveform ECG estimasi dari PPG; ${peaks.size} kandidat puncak R. " +
            "Geser mendatar untuk melihat seluruh grafik. Bukan rekaman ECG."
    }) {
        Canvas(Modifier.width((values.size * 2).coerceIn(512, 4096).dp).height(160.dp)) {
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = index.toFloat() / (values.size - 1).coerceAtLeast(1) * size.width
                val y = (1f - (value - minimum) / range) * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, traceColor, style = Stroke(width = 2.dp.toPx()))
            peaks.forEach { index ->
                val x = index.toFloat() / (values.size - 1).coerceAtLeast(1) * size.width
                drawLine(markerColor, androidx.compose.ui.geometry.Offset(x, 0f),
                    androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1.dp.toPx())
            }
        }
    }
}
