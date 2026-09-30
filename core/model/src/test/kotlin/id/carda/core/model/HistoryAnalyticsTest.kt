package id.carda.core.model

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryAnalyticsTest {
    private val zone = ZoneId.of("Asia/Jakarta")
    private val today = LocalDate.of(2026, 9, 29)
    private val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun todayExcludesYesterdayAndUnavailableResults() {
        val records = listOf(
            record(1, today, 72.0),
            record(2, today.minusDays(1), 80.0),
            record(3, today, null),
        )
        assertEquals(listOf(72.0), HistoryAnalytics.points(records, MetricKind.HEART_RATE_BPM,
            TrendPeriod.TODAY, now, zone).map { it.value })
    }

    @Test fun sevenDayBaselineNeedsThreeDistinctPriorDays() {
        assertNull(HistoryAnalytics.priorSevenDayBaseline(listOf(
            record(1, today.minusDays(1), 70.0), record(2, today.minusDays(2), 80.0),
        ), MetricKind.HEART_RATE_BPM, now, zone))
        val baseline = HistoryAnalytics.priorSevenDayBaseline(listOf(
            record(1, today.minusDays(1), 70.0),
            record(2, today.minusDays(2), 80.0),
            record(3, today.minusDays(3), 90.0),
            record(4, today, 100.0),
        ), MetricKind.HEART_RATE_BPM, now, zone)
        assertEquals(80.0, baseline!!.mean, 0.0)
        assertEquals(3, baseline.distinctDays)
    }

    @Test fun comparisonUsesLatestTodayAndPriorDaysOnly() {
        val records = listOf(
            record(1, today.minusDays(1), 70.0),
            record(2, today.minusDays(2), 80.0),
            record(3, today.minusDays(3), 90.0),
            record(4, today, 95.0),
            record(5, today, 100.0),
        )
        val comparison = HistoryAnalytics.currentVsPriorSevenDay(
            records, MetricKind.HEART_RATE_BPM, now, zone)
        assertEquals(100.0, comparison!!.latestToday.value, 0.0)
        assertEquals(80.0, comparison.baseline.mean, 0.0)
        assertEquals(20.0, comparison.difference, 0.0)
        assertNull(HistoryAnalytics.currentVsPriorSevenDay(
            records.dropLast(2), MetricKind.HEART_RATE_BPM, now, zone))
    }

    @Test fun trendScaleUsesSelectedCalendarWindowRatherThanFirstAndLastResult() {
        val points = HistoryAnalytics.points(listOf(
            record(1, today.minusDays(1), 70.0),
            record(2, today, 80.0),
        ), MetricKind.HEART_RATE_BPM, TrendPeriod.SEVEN_DAYS, now, zone)
        val scale = TrendPlotScale.from(points, TrendPeriod.SEVEN_DAYS, now, zone)
        assertEquals(today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli(), scale.startEpochMillis)
        assertEquals(now, scale.endEpochMillis)
        assertEquals(0.0, scale.xFraction(scale.startEpochMillis), 0.0001)
        assertEquals(1.0, scale.xFraction(now), 0.0001)
        assertEquals(true, scale.xFraction(points.first().measuredAtEpochMillis) > 0.75)
        assertEquals(true, scale.xFraction(points.last().measuredAtEpochMillis) < 1.0)
        assertEquals(1.0, scale.yFraction(80.0), 0.0001)
    }

    @Test fun singleValueTrendGetsFiniteVisibleVerticalScale() {
        val points = HistoryAnalytics.points(listOf(record(1, today, 72.0)),
            MetricKind.HEART_RATE_BPM, TrendPeriod.TODAY, now, zone)
        val scale = TrendPlotScale.from(points, TrendPeriod.TODAY, now, zone)
        assertEquals(0.5, scale.yFraction(72.0), 0.0001)
    }

    private fun record(id: Long, date: LocalDate, bpm: Double?): StoredMeasurement {
        val time = date.atTime(10, id.toInt()).atZone(zone).toInstant().toEpochMilli()
        val quality = QualityReport(true, 1.0, emptySet(), 900, 30_000, "ppg-0.1")
        val device = DeviceProfile("Example", "Phone", 36, "arm64-v8a", "0.1.0", "ppg-0.1",
            true, true, 1280, 720, 30.0, null, "torch on", emptySet(), 1.0,
            SupportClassification.COMPATIBLE)
        val metric = if (bpm == null) MetricResult.Unavailable(MetricUnavailableReason.METHOD_NOT_VALIDATED)
            else MetricResult.Available(bpm)
        val metrics = if (bpm == null) mapOf(
            MetricKind.HEART_RATE_BPM to metric,
            MetricKind.PRV_RMSSD_MS to MetricResult.Available(35.0),
        ) else mapOf(MetricKind.HEART_RATE_BPM to metric)
        return StoredMeasurement(id, MeasurementSummary(time, 30_000, quality, device, metrics))
    }
}
