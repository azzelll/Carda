package id.carda.feature.measurement

import id.carda.core.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeasurementRecordingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun repeatedCompletedSummaryDuringRecompositionIsSavedOnce() = runTest(dispatcher) {
        val repo = FakeMeasurements()
        val vm = MeasurementRecordingViewModel(repo, FakeProfiles())
        val summary = fixture()
        vm.record("fixture-account", summary)
        vm.record("fixture-account", summary)
        testScheduler.runCurrent()
        assertEquals(1, repo.calls)
        assertEquals(RecordingState.Saving, vm.state.value)
        repo.release.complete(Unit)
        testScheduler.runCurrent()
        vm.record("fixture-account", summary)
        assertEquals(RecordingState.Saved, vm.state.value)
        assertEquals(1, repo.calls)
    }

    @Test fun failedWriteCanBeExplicitlyRetriedAndDebugCaptureIsNeverSaved() = runTest(dispatcher) {
        val repo = FakeMeasurements().apply { failure = true; release.complete(Unit) }
        val vm = MeasurementRecordingViewModel(repo, FakeProfiles())
        val summary = fixture()
        vm.record(null, summary)
        assertEquals(0, repo.calls)
        vm.record("fixture-account", summary)
        testScheduler.runCurrent()
        assertEquals(RecordingState.Failed, vm.state.value)
        vm.record("fixture-account", summary)
        assertEquals(1, repo.calls)
        repo.failure = false
        vm.record("fixture-account", summary, retry = true)
        testScheduler.runCurrent()
        assertEquals(RecordingState.Saved, vm.state.value)
        assertEquals(2, repo.calls)
    }

    private class FakeMeasurements : MeasurementRepository {
        var calls = 0
        var failure = false
        val release = CompletableDeferred<Unit>()
        override suspend fun save(accountId: String, summary: MeasurementSummary): Long {
            calls++; release.await(); if (failure) error("Temporary storage failure"); return 1
        }
        override fun observeHistory(accountId: String) = flowOf(emptyList<StoredMeasurement>())
        override suspend fun find(accountId: String, id: Long): StoredMeasurement? = null
        override suspend fun deleteAll(accountId: String) = 0
    }
    private class FakeProfiles : LocalProfileRepository {
        override fun observe(accountId: String) = flowOf(LocalProfile(accountId))
        override suspend fun save(profile: LocalProfile) = Unit
        override suspend fun delete(accountId: String) = Unit
        override fun observeConsentVersion(accountId: String): Flow<String?> = flowOf(null)
        override suspend fun acceptConsent(accountId: String, version: String) = Unit
        override suspend fun revokeConsent(accountId: String) = Unit
        override fun observeReminderHour(accountId: String): Flow<Int?> = flowOf(null)
        override suspend fun setReminderHour(accountId: String, hour: Int?) = Unit
        override fun observeDeviceCompatibility(accountId: String): Flow<DeviceCompatibilityRecord?> = flowOf(null)
        override suspend fun saveDeviceCompatibility(accountId: String, record: DeviceCompatibilityRecord) = Unit
        override suspend fun deleteDeviceCompatibility(accountId: String) = Unit
    }
    private fun fixture(): MeasurementSummary {
        val quality = QualityReport(true, 1.0, emptySet(), 900, 30_000, "synthetic-fixture")
        val device = DeviceProfile("Synthetic", "Fixture", 36, "arm64-v8a", "test",
            "synthetic-fixture", true, true, 1280, 720, 30.0, null, "test",
            emptySet(), 1.0, SupportClassification.COMPATIBLE)
        return MeasurementSummary(1L, 30_000, quality, device,
            mapOf(MetricKind.HEART_RATE_BPM to MetricResult.Available(75.0)))
    }
}
