package id.carda.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class TrendPeriod { TODAY, SEVEN_DAYS, THIRTY_DAYS, THREE_MONTHS }

data class TrendPoint(val measuredAtEpochMillis: Long, val value: Double, val qualityScore: Double)
/** Plot positions use the selected calendar period, including days without measurements. */
data class TrendPlotScale(
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val minimumValue: Double,
    val maximumValue: Double,
) {
    init {
        require(endEpochMillis > startEpochMillis)
        require(minimumValue.isFinite() && maximumValue.isFinite() && maximumValue > minimumValue)
    }

    fun xFraction(epochMillis: Long): Double =
        ((epochMillis - startEpochMillis).toDouble() / (endEpochMillis - startEpochMillis)).coerceIn(0.0, 1.0)

    fun yFraction(value: Double): Double =
        ((value - minimumValue) / (maximumValue - minimumValue)).coerceIn(0.0, 1.0)

    companion object {
        fun from(points: List<TrendPoint>, period: TrendPeriod, nowEpochMillis: Long, zone: ZoneId): TrendPlotScale {
            require(points.isNotEmpty())
            val smallest = points.minOf { it.value }
            val largest = points.maxOf { it.value }
            val minimum = if (smallest == largest) (smallest - 0.5).coerceAtLeast(0.0) else smallest
            val maximum = if (smallest == largest) largest + 0.5 else largest
            return TrendPlotScale(HistoryAnalytics.periodStartEpochMillis(period, nowEpochMillis, zone),
                nowEpochMillis, minimum, maximum)
        }
    }
}
data class SevenDayBaseline(val mean: Double, val results: Int, val distinctDays: Int)
data class TrendComparison(val latestToday: TrendPoint, val baseline: SevenDayBaseline) {
    val difference: Double get() = latestToday.value - baseline.mean
}

/** Calendar windows and summaries over accepted local results only. No health-risk interpretation. */
object HistoryAnalytics {
    fun periodStartEpochMillis(period: TrendPeriod, nowEpochMillis: Long, zone: ZoneId): Long {
        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
        val start = when (period) {
            TrendPeriod.TODAY -> today
            TrendPeriod.SEVEN_DAYS -> today.minusDays(6)
            TrendPeriod.THIRTY_DAYS -> today.minusDays(29)
            TrendPeriod.THREE_MONTHS -> today.minusMonths(3)
        }
        return start.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** A numeric comparison only: it makes no wellness or clinical interpretation. */
    fun currentVsPriorSevenDay(
        history: List<StoredMeasurement>, metric: MetricKind,
        nowEpochMillis: Long, zone: ZoneId,
    ): TrendComparison? {
        val latestToday = points(history, metric, TrendPeriod.TODAY, nowEpochMillis, zone)
            .lastOrNull() ?: return null
        val baseline = priorSevenDayBaseline(history, metric, nowEpochMillis, zone) ?: return null
        return TrendComparison(latestToday, baseline)
    }

    fun points(
        history: List<StoredMeasurement>, metric: MetricKind, period: TrendPeriod,
        nowEpochMillis: Long, zone: ZoneId,
    ): List<TrendPoint> {
        val start = periodStartEpochMillis(period, nowEpochMillis, zone)
        return history.mapNotNull { record ->
            val summary = record.summary
            if (!summary.quality.passed || summary.measuredAtEpochMillis !in start..nowEpochMillis) return@mapNotNull null
            val available = summary.metrics[metric] as? MetricResult.Available ?: return@mapNotNull null
            TrendPoint(summary.measuredAtEpochMillis, available.value, summary.quality.score)
        }.sortedBy { it.measuredAtEpochMillis }
    }

    /** Prior seven complete local calendar days; at least three observed days for a baseline. */
    fun priorSevenDayBaseline(
        history: List<StoredMeasurement>, metric: MetricKind,
        nowEpochMillis: Long, zone: ZoneId,
    ): SevenDayBaseline? {
        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
        val start = today.minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val endExclusive = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val eligible = history.mapNotNull { record ->
            val summary = record.summary
            if (!summary.quality.passed || summary.measuredAtEpochMillis < start ||
                summary.measuredAtEpochMillis >= endExclusive
            ) return@mapNotNull null
            val available = summary.metrics[metric] as? MetricResult.Available ?: return@mapNotNull null
            Instant.ofEpochMilli(summary.measuredAtEpochMillis).atZone(zone).toLocalDate() to available.value
        }
        val days = eligible.map { it.first }.toSet().size
        if (days < 3) return null
        return SevenDayBaseline(eligible.map { it.second }.average(), eligible.size, days)
    }
}
