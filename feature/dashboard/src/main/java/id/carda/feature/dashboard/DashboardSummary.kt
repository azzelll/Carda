package id.carda.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.carda.core.model.MetricKind
import id.carda.core.model.MetricResult
import id.carda.core.model.StoredMeasurement
import id.carda.core.model.HistoryAnalytics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardSummary(history: List<StoredMeasurement>, nowEpochMillis: Long = System.currentTimeMillis()) {
    val latest = history.maxByOrNull { it.summary.measuredAtEpochMillis }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Hasil terakhir", style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() })
        if (latest == null) {
            Text("Belum ada pengukuran valid di perangkat ini.")
            Text("Hasil yang gagal kualitas sinyal tidak masuk dashboard.")
            return@Column
        }
        val summary = latest.summary
        val timestamp = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))
            .format(Instant.ofEpochMilli(summary.measuredAtEpochMillis).atZone(ZoneId.systemDefault()))
        Text(timestamp)
        Text("Kondisi sesi: ${summary.activity.label}")
        val heartRate = (summary.metrics[MetricKind.HEART_RATE_BPM] as? MetricResult.Available)?.value
        Text(if (heartRate == null) "Denyut: tidak dapat ditentukan" else
            String.format(Locale.forLanguageTag("id-ID"), "Denyut: %.1f kali/menit", heartRate))
        listOf(MetricKind.PRV_RMSSD_MS to "RMSSD", MetricKind.PRV_SDNN_MS to "SDNN").forEach { (kind, label) ->
            val value = (summary.metrics[kind] as? MetricResult.Available)?.value
            Text(if (value == null) "PRV $label: tidak dapat ditentukan" else
                String.format(Locale.forLanguageTag("id-ID"), "PRV $label: %.1f ms", value))
        }
        val comparison = HistoryAnalytics.currentVsPriorSevenDay(history, MetricKind.HEART_RATE_BPM,
            nowEpochMillis, ZoneId.systemDefault())?.takeIf {
            it.latestToday.measuredAtEpochMillis == summary.measuredAtEpochMillis
        }
        if (comparison == null) {
            Text("Perbandingan denyut dengan tujuh hari sebelumnya belum cukup data.")
        } else {
            val direction = when {
                comparison.difference > 0.0 -> "lebih tinggi"
                comparison.difference < 0.0 -> "lebih rendah"
                else -> "sama"
            }
            Text(String.format(Locale.forLanguageTag("id-ID"),
                "Denyut terbaru $direction %.1f kali/menit dari rata-rata tujuh hari sebelumnya (%d hasil pada %d hari). Ini perubahan angka, bukan penilaian kesehatan.",
                kotlin.math.abs(comparison.difference), comparison.baseline.results,
                comparison.baseline.distinctDays))
        }
        Text("Kualitas sinyal ${(summary.quality.score * 100).toInt()}% • ${summary.validDurationMillis / 1000} detik valid")
        Text("Perangkat: ${summary.deviceProfile.manufacturer} ${summary.deviceProfile.model}; pipeline ${summary.quality.pipelineVersion}.")
        Text("Informasi wellness, bukan diagnosis. PRV berasal dari PPG dan bukan HRV ECG; metrik lain memerlukan bukti terpisah.")
    }
}
