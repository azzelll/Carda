package id.carda.feature.result

import id.carda.core.model.MetricKind
import id.carda.core.model.MetricResult
import id.carda.core.model.MetricUnavailableReason
import id.carda.core.model.ExperimentalInsightResult
import java.util.Locale

data class MetricPresentation(val title: String, val value: String, val available: Boolean)

data class InsightPresentation(val available: Boolean, val description: String, val timing: String)

/** ECG Insight has its own model/confidence gate; SQI alone never makes it available. */
fun presentInsight(result: ExperimentalInsightResult?): InsightPresentation {
    if (result !is ExperimentalInsightResult.Available) {
        return InsightPresentation(false,
            "ECG Insight belum tersedia tanpa model lokal berlisensi, confidence yang dievaluasi, dan bukti pada kamera ponsel.",
            "Interval R–R tidak dapat ditentukan.")
    }
    val timing = result.estimatedTiming?.let {
        String.format(Locale.forLanguageTag("id-ID"),
            "Interval R–R jantung estimasi: %.1f ms; metode %s.", it.meanRrMillis, it.methodVersion)
    } ?: "Interval R–R tidak dapat ditentukan dari waveform estimasi ini."
    return InsightPresentation(true,
        String.format(Locale.forLanguageTag("id-ID"),
            "Waveform ECG estimasi eksperimental dari PPG, bukan rekaman ECG. Model %s; confidence terkalibrasi %.2f.",
            result.modelVersion, result.confidence), timing)
}

/** Every absent or rejected metric keeps its reason instead of borrowing a prior value. */
fun presentMetric(kind: MetricKind, result: MetricResult<Double>?): MetricPresentation {
    val title = when (kind) {
        MetricKind.HEART_RATE_BPM -> "Denyut jantung"
        MetricKind.PRV_RMSSD_MS -> "PRV RMSSD"
        MetricKind.PRV_SDNN_MS -> "PRV SDNN"
        MetricKind.ESTIMATED_RESPIRATORY_RATE_BPM -> "Estimasi laju napas"
        MetricKind.ESTIMATED_SPO2_PERCENT -> "Estimasi SpO₂"
        MetricKind.ESTIMATED_SYSTOLIC_MMHG -> "Estimasi tekanan sistolik"
        MetricKind.ESTIMATED_DIASTOLIC_MMHG -> "Estimasi tekanan diastolik"
    }
    if (result !is MetricResult.Available) {
        val reason = when ((result as? MetricResult.Unavailable)?.reason) {
            MetricUnavailableReason.QUALITY_REJECTED -> "kualitas sinyal tidak cukup"
            MetricUnavailableReason.INSUFFICIENT_DURATION -> "durasi valid belum cukup"
            MetricUnavailableReason.INSUFFICIENT_BEATS -> "denyut terdeteksi belum cukup"
            MetricUnavailableReason.UNCERTAIN_BEAT_INTERVALS -> "interval denyut belum stabil"
            MetricUnavailableReason.UNSUPPORTED_VALUE -> "nilai di luar kondisi yang didukung"
            MetricUnavailableReason.UNSUPPORTED_DEVICE -> "perangkat tidak didukung"
            MetricUnavailableReason.MODEL_UNAVAILABLE -> "model belum tersedia"
            MetricUnavailableReason.METHOD_NOT_VALIDATED, null -> "metode belum tervalidasi"
        }
        return MetricPresentation(title, "Tidak dapat ditentukan: $reason.", false)
    }
    val unit = when (kind) {
        MetricKind.HEART_RATE_BPM -> "kali/menit"
        MetricKind.PRV_RMSSD_MS, MetricKind.PRV_SDNN_MS -> "ms"
        MetricKind.ESTIMATED_RESPIRATORY_RATE_BPM -> "napas/menit"
        MetricKind.ESTIMATED_SPO2_PERCENT -> "%"
        MetricKind.ESTIMATED_SYSTOLIC_MMHG, MetricKind.ESTIMATED_DIASTOLIC_MMHG -> "mmHg"
    }
    return MetricPresentation(title, String.format(Locale.forLanguageTag("id-ID"), "%.1f %s", result.value, unit), true)
}
