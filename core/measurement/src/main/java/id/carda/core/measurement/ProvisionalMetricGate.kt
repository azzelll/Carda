package id.carda.core.measurement

import id.carda.core.model.MetricResult
import id.carda.core.ppg.PpgAnalysis

/** Temporary HR has the same SQI requirement as the final result. */
fun provisionalHeartRate(analysis: PpgAnalysis): Double? =
    if (analysis.quality.passed) (analysis.heartRateBpm as? MetricResult.Available)?.value else null
