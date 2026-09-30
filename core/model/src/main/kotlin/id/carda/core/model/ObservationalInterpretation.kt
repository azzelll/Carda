package id.carda.core.model

/** Source categories remain tracked for expert review; none is assigned by this policy. */
enum class ProposedOverallRiskLevel {
    NORMAL, NEEDS_ATTENTION, RECHECK_SUGGESTED, MEDICAL_CONSULTATION_SUGGESTED,
}

data class Observation(
    val ruleId: String,
    val sourceReference: String,
    val message: String,
)

sealed interface OverallRiskAssessment {
    data class Unavailable(val reason: String) : OverallRiskAssessment
}

data class InterpretationReport(
    val policyVersion: String,
    val observations: List<Observation>,
    val overallRisk: OverallRiskAssessment,
)

/**
 * Describes only observed capture context and provenance. Clinical risk rules cannot be
 * installed here without a separate reviewed policy and evidence gate.
 */
class ObservationalInterpretationEngine {
    fun evaluate(summary: MeasurementSummary): InterpretationReport {
        val source = "docs/ppg-quality-and-safety.md"
        val context = when (summary.activity) {
            MeasurementActivity.RESTING -> Observation("ACTIVITY_RESTING", source,
                "Sesi ini dicatat saat istirahat. Hasil hanya menggambarkan pengukuran ini.")
            MeasurementActivity.RECENT_ACTIVITY -> Observation("ACTIVITY_RECENT", source,
                "Sesi ini dicatat setelah beraktivitas. Perbandingan dengan sesi istirahat perlu konteks.")
            MeasurementActivity.UNSPECIFIED -> Observation("ACTIVITY_UNKNOWN", source,
                "Aktivitas saat pengukuran tidak dinyatakan. Perbandingan antar sesi terbatas.")
        }
        val observations = mutableListOf(context)
        if (summary.metrics[MetricKind.HEART_RATE_BPM] is MetricResult.Available) {
            observations += Observation("PPG_HR_SOURCE", source,
                "Denyut berasal dari PPG yang lolos pemeriksaan kualitas sinyal teknis.")
        }
        if (summary.metrics[MetricKind.PRV_RMSSD_MS] is MetricResult.Available &&
            summary.metrics[MetricKind.PRV_SDNN_MS] is MetricResult.Available) {
            observations += Observation("PPG_PRV_SOURCE", source,
                "RMSSD dan SDNN berasal dari interval denyut PPG; keduanya bukan pengukuran HRV ECG.")
        }
        return InterpretationReport("observation-0.1", observations,
            OverallRiskAssessment.Unavailable(
                "Risiko keseluruhan belum dapat ditentukan tanpa aturan dan bukti yang ditinjau ahli."))
    }
}
