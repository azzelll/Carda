package id.carda.core.data

import id.carda.core.model.DeviceProfile
import id.carda.core.model.MeasurementSummary
import id.carda.core.model.MetricKind
import id.carda.core.model.MetricResult
import id.carda.core.model.MetricUnavailableReason
import id.carda.core.model.QualityReport
import id.carda.core.model.SupportClassification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMeasurementRepositoryTest {
    private val accountA = "ec87a9d1-6035-443b-b14a-ad36d879c9d8"
    private val accountB = "43718466-854c-4c97-b2c8-50dfb369fa2a"

    @Test fun historyIsAccountScopedAndDeleteIsExplicit() = runBlocking {
        val repository = RoomMeasurementRepository(FakeDao())
        repository.save(accountA, validSummary(72.0))
        repository.save(accountB, validSummary(81.0))
        assertEquals(72.0, ((repository.observeHistory(accountA).first().single()
            .summary.metrics[MetricKind.HEART_RATE_BPM]) as MetricResult.Available).value, 0.0)
        assertEquals(1, repository.observeHistory(accountB).first().size)
        assertEquals(1, repository.deleteAll(accountA))
        assertTrue(repository.observeHistory(accountA).first().isEmpty())
        assertEquals(1, repository.observeHistory(accountB).first().size)
    }

    @Test fun metricStatusesRoundTripWithoutFabricatingValues() {
        val metrics = mapOf(
            MetricKind.HEART_RATE_BPM to MetricResult.Available(72.25),
            MetricKind.PRV_RMSSD_MS to MetricResult.Unavailable(MetricUnavailableReason.INSUFFICIENT_DURATION),
        )
        assertEquals(metrics, MetricStatusCodec.decode(MetricStatusCodec.encode(metrics)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidAccountCannotOwnHistory() = runBlocking {
        RoomMeasurementRepository(FakeDao()).save("unscoped", validSummary(72.0))
        Unit
    }

    private fun validSummary(bpm: Double): MeasurementSummary {
        val quality = QualityReport(true, 1.0, emptySet(), 900, 30_000, "ppg-0.1")
        val profile = DeviceProfile("Example", "Phone", 36, "arm64-v8a", "0.1.0", "ppg-0.1",
            true, true, 1280, 720, 30.0, null, "torch on", emptySet(), 1.0,
            SupportClassification.COMPATIBLE)
        return MeasurementSummary(1_790_000_000_000, 30_000, quality, profile,
            mapOf(MetricKind.HEART_RATE_BPM to MetricResult.Available(bpm)))
    }

    private class FakeDao : MeasurementDao {
        private val rows = MutableStateFlow<List<MeasurementEntity>>(emptyList())
        private var nextId = 1L
        override suspend fun insert(entity: MeasurementEntity): Long {
            val id = nextId++
            rows.value = rows.value + entity.copy(id = id)
            return id
        }
        override fun observe(accountId: String): Flow<List<MeasurementEntity>> =
            rows.map { current -> current.filter { it.accountId == accountId } }
        override suspend fun find(accountId: String, id: Long): MeasurementEntity? =
            rows.value.firstOrNull { it.accountId == accountId && it.id == id }
        override suspend fun deleteAll(accountId: String): Int {
            val count = rows.value.count { it.accountId == accountId }
            rows.value = rows.value.filterNot { it.accountId == accountId }
            return count
        }
    }
}
