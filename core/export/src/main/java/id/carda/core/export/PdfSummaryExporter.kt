package id.carda.core.export

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import id.carda.core.model.MeasurementSummary
import id.carda.core.model.MetricKind
import id.carda.core.model.MetricResult
import id.carda.core.model.MetricUnavailableReason
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Text contract can be unit-tested without rendering Android PDF pages. */
object PdfSummaryContent {
    fun lines(summary: MeasurementSummary, zone: ZoneId = ZoneId.systemDefault()): List<String> {
        require(summary.quality.passed)
        val timestamp = DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))
            .format(Instant.ofEpochMilli(summary.measuredAtEpochMillis).atZone(zone))
        val result = mutableListOf(
            "CARDA - Ringkasan Pengukuran",
            "Waktu: $timestamp",
            "Kondisi sesi (pilihan pengguna): ${summary.activity.label}",
            "Durasi sinyal valid: ${summary.validDurationMillis / 1000} detik",
            "Kualitas sinyal: ${(summary.quality.score * 100).toInt()}% (skor teknis, bukan akurasi)",
            "Perangkat: ${summary.deviceProfile.manufacturer} ${summary.deviceProfile.model}",
            "Android API: ${summary.deviceProfile.androidApiLevel}",
            "Pipeline: ${summary.quality.pipelineVersion}",
            "Model: ${summary.modelVersion ?: "tidak digunakan"}",
            "",
            "Metrik",
        )
        MetricKind.entries.forEach { kind ->
            val text = when (val metric = summary.metrics[kind]) {
                is MetricResult.Available -> "%.1f %s".format(Locale.US, metric.value, kind.unit())
                is MetricResult.Unavailable -> "tidak dapat ditentukan (${metric.reason.label()})"
                null -> "tidak dapat ditentukan (metode belum tersedia)"
            }
            result += "${kind.label()}: $text"
        }
        result += ""
        result += "Hasil ini adalah informasi wellness dari PPG kamera, bukan diagnosis atau layanan darurat."
        result += "PRV berasal dari interval denyut PPG; nilainya belum otomatis setara dengan HRV ECG."
        result += "Metrik yang tidak tersedia tidak boleh ditafsirkan sebagai nol atau kondisi normal."
        return result
    }
}

class PdfSummaryExporter {
    /** Caller owns the user-selected Storage Access Framework stream. */
    fun write(output: OutputStream, summary: MeasurementSummary) {
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                textSize = 12f
            }
            var y = 48f
            PdfSummaryContent.lines(summary).forEach { line ->
                line.wrap(78).forEach { segment ->
                    require(y < 810f) { "PDF content exceeds one page" }
                    page.canvas.drawText(segment, 48f, y, paint)
                    y += 19f
                }
            }
            document.finishPage(page)
            document.writeTo(output)
        } finally {
            document.close()
        }
    }
}

private fun String.wrap(maxCharacters: Int): List<String> {
    if (isEmpty()) return listOf("")
    val result = mutableListOf<String>()
    var remaining = this
    while (remaining.length > maxCharacters) {
        val breakAt = remaining.lastIndexOf(' ', maxCharacters).takeIf { it > 0 } ?: maxCharacters
        result += remaining.substring(0, breakAt)
        remaining = remaining.substring(breakAt).trimStart()
    }
    result += remaining
    return result
}

private fun MetricKind.label(): String = when (this) {
    MetricKind.HEART_RATE_BPM -> "Denyut"
    MetricKind.PRV_RMSSD_MS -> "PRV RMSSD (eksperimental)"
    MetricKind.PRV_SDNN_MS -> "PRV SDNN (eksperimental)"
    MetricKind.ESTIMATED_RESPIRATORY_RATE_BPM -> "Laju napas"
    MetricKind.ESTIMATED_SPO2_PERCENT -> "SpO2 perkiraan"
    MetricKind.ESTIMATED_SYSTOLIC_MMHG -> "Tekanan sistolik perkiraan"
    MetricKind.ESTIMATED_DIASTOLIC_MMHG -> "Tekanan diastolik perkiraan"
}

private fun MetricKind.unit(): String = when (this) {
    MetricKind.HEART_RATE_BPM, MetricKind.ESTIMATED_RESPIRATORY_RATE_BPM -> "kali/menit"
    MetricKind.PRV_RMSSD_MS, MetricKind.PRV_SDNN_MS -> "ms"
    MetricKind.ESTIMATED_SPO2_PERCENT -> "%"
    MetricKind.ESTIMATED_SYSTOLIC_MMHG, MetricKind.ESTIMATED_DIASTOLIC_MMHG -> "mmHg"
}

private fun MetricUnavailableReason.label(): String = when (this) {
    MetricUnavailableReason.QUALITY_REJECTED -> "kualitas sinyal gagal"
    MetricUnavailableReason.INSUFFICIENT_DURATION -> "durasi belum cukup"
    MetricUnavailableReason.INSUFFICIENT_BEATS -> "denyut belum cukup"
    MetricUnavailableReason.UNCERTAIN_BEAT_INTERVALS -> "interval denyut tidak stabil"
    MetricUnavailableReason.UNSUPPORTED_VALUE -> "nilai di luar rentang metode"
    MetricUnavailableReason.METHOD_NOT_VALIDATED -> "metode belum tervalidasi"
    MetricUnavailableReason.MODEL_UNAVAILABLE -> "model belum tersedia"
    MetricUnavailableReason.UNSUPPORTED_DEVICE -> "perangkat tidak didukung"
}
