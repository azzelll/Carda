package id.carda.feature.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import id.carda.core.model.HistoryAnalytics
import id.carda.core.model.TrendPlotScale
import id.carda.core.model.MetricKind
import id.carda.core.model.MetricResult
import id.carda.core.model.MeasurementSummary
import id.carda.core.model.StoredMeasurement
import id.carda.core.model.SupportClassification
import id.carda.core.model.TrendPeriod
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    history: List<StoredMeasurement>,
    exportMessage: String,
    onExport: (MeasurementSummary) -> Unit,
    canShareExport: Boolean,
    onShareExport: () -> Unit,
    onDeleteAll: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actionMessage: String = "",
    isDeleting: Boolean = false,
) {
    var metric by remember { mutableStateOf(MetricKind.HEART_RATE_BPM) }
    var period by remember { mutableStateOf(TrendPeriod.SEVEN_DAYS) }
    var search by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val points = HistoryAnalytics.points(history, metric, period, now, zone)
    val baseline = HistoryAnalytics.priorSevenDayBaseline(history, metric, now, zone)
    val comparison = HistoryAnalytics.currentVsPriorSevenDay(history, metric, now, zone)
    val formatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID")) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (actionMessage.isNotBlank()) Text(actionMessage)
        if (isDeleting) Text("Menghapus riwayat lokal…")
        Text("Riwayat dan tren lokal", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() })
        Text("Hanya pengukuran yang lolos kualitas sinyal masuk riwayat. Perubahan angka bukan diagnosis.")
        if (exportMessage.isNotBlank()) Text(exportMessage)
        if (canShareExport) Button(onClick = onShareExport) { Text("Bagikan PDF yang baru disimpan") }
        Text("Metrik", style = MaterialTheme.typography.titleMedium)
        MetricKind.entries.forEach { candidate ->
            OutlinedButton(onClick = { metric = candidate }, modifier = Modifier.semantics {
                role = Role.RadioButton
                selected = metric == candidate
                stateDescription = if (metric == candidate) "Dipilih" else "Tidak dipilih"
            }) {
                Text((if (metric == candidate) "✓ " else "") + candidate.label())
            }
        }
        Text("Rentang waktu", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendPeriod.entries.forEach { candidate ->
                PeriodButton(candidate, period, { period = it })
            }
        }
        if (points.isEmpty()) {
            Text("Belum ada data valid untuk metrik dan rentang ini.")
        } else {
            Text("${points.size} hasil valid • ${metric.unit()}")
            val scale = TrendPlotScale.from(points, period, now, zone)
            val axisFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.forLanguageTag("id-ID")) }
            Canvas(Modifier.fillMaxWidth().height(160.dp).semantics {
                contentDescription = "Grafik ${metric.label()} berisi ${points.size} hasil valid, " +
                    "satuan ${metric.unit()}, dari " +
                    axisFormatter.format(Instant.ofEpochMilli(scale.startEpochMillis).atZone(zone)) +
                    " sampai " + axisFormatter.format(Instant.ofEpochMilli(scale.endEpochMillis).atZone(zone))
            }) {
                val path = Path()
                points.forEachIndexed { index, point ->
                    val x = (scale.xFraction(point.measuredAtEpochMillis) * size.width).toFloat()
                    val y = (size.height - scale.yFraction(point.value) * size.height).toFloat()
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    drawCircle(Color.Blue, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                }
                if (points.size > 1) drawPath(path, Color.Blue, style = Stroke(width = 2.dp.toPx()))
            }
            Text("Sumbu waktu: " + axisFormatter.format(Instant.ofEpochMilli(scale.startEpochMillis).atZone(zone)) +
                " sampai " + axisFormatter.format(Instant.ofEpochMilli(scale.endEpochMillis).atZone(zone)))
            Text("Sumbu nilai: %.1f–%.1f %s".format(scale.minimumValue, scale.maximumValue, metric.unit()))
            val last = points.last()
            Text("Terakhir: %.1f %s pada %s".format(last.value, metric.unit(),
                formatter.format(Instant.ofEpochMilli(last.measuredAtEpochMillis).atZone(zone))))
            if (baseline == null) Text("Data tujuh hari sebelumnya belum cukup untuk baseline (perlu ≥3 hari berbeda).")
            else Text("Rata-rata tujuh hari sebelumnya: %.1f %s dari %d hasil/%d hari."
                .format(baseline.mean, metric.unit(), baseline.results, baseline.distinctDays))
            if (comparison != null) {
                val direction = when {
                    comparison.difference > 0 -> "lebih tinggi"
                    comparison.difference < 0 -> "lebih rendah"
                    else -> "sama"
                }
                Text("Hasil terbaru hari ini $direction %.1f %s dari rata-rata tujuh hari sebelumnya. Ini perubahan angka, bukan penilaian kesehatan."
                    .format(kotlin.math.abs(comparison.difference), metric.unit()))
            }
            Text("Awal: ${formatter.format(Instant.ofEpochMilli(points.first().measuredAtEpochMillis).atZone(zone))} · Akhir: ${formatter.format(Instant.ofEpochMilli(points.last().measuredAtEpochMillis).atZone(zone))}")
            Text("Rentang nilai pada grafik: %.1f–%.1f %s".format(
                points.minOf { it.value }, points.maxOf { it.value }, metric.unit()))
        }
        OutlinedTextField(search, { search = it }, label = { Text("Cari tanggal hasil") })
        val listed = history.filter { item ->
            val date = formatter.format(Instant.ofEpochMilli(item.summary.measuredAtEpochMillis).atZone(zone))
            date.contains(search, ignoreCase = true) &&
                item.summary.metrics[metric] is MetricResult.Available<*>
        }
        if (listed.isEmpty()) Text("Tidak ada hasil yang cocok.")
        listed.forEach { stored ->
            val date = formatter.format(Instant.ofEpochMilli(stored.summary.measuredAtEpochMillis).atZone(zone))
            val value = (stored.summary.metrics[metric] as MetricResult.Available).value
            OutlinedButton(onClick = { selectedId = if (selectedId == stored.id) null else stored.id }) {
                Text("$date • %.1f %s".format(value, metric.unit()))
            }
            if (selectedId == stored.id) {
                Text("Kondisi saat sesi: ${stored.summary.activity.label}")
                Text("Kualitas: ${(stored.summary.quality.score * 100).toInt()}% • Durasi valid: ${stored.summary.validDurationMillis / 1000} detik")
                Text("Perangkat: ${stored.summary.deviceProfile.manufacturer} ${stored.summary.deviceProfile.model}")
                val device = stored.summary.deviceProfile
                val stream = if (device.analysisWidth == null || device.analysisHeight == null) "belum tercatat"
                    else "${device.analysisWidth} × ${device.analysisHeight}"
                val cadence = device.observedFramesPerSecond?.let { "%.1f fps".format(it) } ?: "belum tercatat"
                Text("Dukungan saat sesi: ${device.supportClassification.label()}. Analisis: $stream; cadence: $cadence.")
                Text("Pipeline: ${stored.summary.quality.pipelineVersion}. Informasi wellness, bukan diagnosis.")
                Button(onClick = { onExport(stored.summary) }) { Text("Unduh ringkasan PDF") }
            }
        }
        if (history.isNotEmpty()) {
            if (confirmDelete) {
                Text("Hapus semua hasil lokal akun ini? Tindakan ini tidak menghapus akun server.")
                Button(enabled = !isDeleting, onClick = { confirmDelete = false; onDeleteAll() }) { Text("Ya, hapus semua riwayat") }
                Button(onClick = { confirmDelete = false }) { Text("Batal") }
            } else Button(enabled = !isDeleting, onClick = { confirmDelete = true }) { Text("Hapus semua riwayat lokal") }
        }
        Button(onClick = onBack) { Text("Kembali") }
    }
}

@Composable
private fun PeriodButton(period: TrendPeriod, selected: TrendPeriod, onSelect: (TrendPeriod) -> Unit) {
    OutlinedButton(onClick = { onSelect(period) }, modifier = Modifier.fillMaxWidth().semantics {
        role = Role.RadioButton
        this.selected = period == selected
        stateDescription = if (period == selected) "Dipilih" else "Tidak dipilih"
    }) {
        Text((if (period == selected) "✓ " else "") + period.label())
    }
}

private fun TrendPeriod.label(): String = when (this) {
    TrendPeriod.TODAY -> "Hari ini"
    TrendPeriod.SEVEN_DAYS -> "7 hari"
    TrendPeriod.THIRTY_DAYS -> "30 hari"
    TrendPeriod.THREE_MONTHS -> "3 bulan"
}

private fun MetricKind.label(): String = when (this) {
    MetricKind.HEART_RATE_BPM -> "Denyut"
    MetricKind.PRV_RMSSD_MS -> "PRV RMSSD"
    MetricKind.PRV_SDNN_MS -> "PRV SDNN"
    MetricKind.ESTIMATED_RESPIRATORY_RATE_BPM -> "Laju napas"
    MetricKind.ESTIMATED_SPO2_PERCENT -> "SpO₂"
    MetricKind.ESTIMATED_SYSTOLIC_MMHG -> "Tekanan sistolik"
    MetricKind.ESTIMATED_DIASTOLIC_MMHG -> "Tekanan diastolik"
}

private fun MetricKind.unit(): String = when (this) {
    MetricKind.HEART_RATE_BPM -> "kali/menit"
    MetricKind.PRV_RMSSD_MS, MetricKind.PRV_SDNN_MS -> "ms"
    MetricKind.ESTIMATED_RESPIRATORY_RATE_BPM -> "napas/menit"
    MetricKind.ESTIMATED_SPO2_PERCENT -> "%"
    MetricKind.ESTIMATED_SYSTOLIC_MMHG, MetricKind.ESTIMATED_DIASTOLIC_MMHG -> "mmHg"
}

private fun SupportClassification.label(): String = when (this) {
    SupportClassification.COMPATIBLE -> "kompatibel pada sesi ini"
    SupportClassification.RESTRICTED -> "terbatas pada sesi ini"
    SupportClassification.NOT_SUPPORTED -> "tidak didukung"
}
