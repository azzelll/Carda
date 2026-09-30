package id.carda.feature.profile

import id.carda.core.model.*
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
class ProfileActionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun failedSaveKeepsRetryStateAndDuplicateTapDoesNotStartSecondWrite() = runTest(dispatcher) {
        val repo = FakeProfiles()
        val vm = ProfileActionsViewModel(repo)
        val profile = LocalProfile("fixture-account")
        vm.save(profile)
        vm.save(profile)
        testScheduler.runCurrent()
        assertEquals(1, repo.calls)
        assertFalse(vm.state.value.busy)
        assertTrue(vm.state.value.message.contains("belum tersimpan"))
        vm.save(profile)
        testScheduler.runCurrent()
        assertEquals(2, repo.calls)
    }

    private class FakeProfiles : LocalProfileRepository {
        var calls = 0
        override fun observe(accountId: String) = flowOf(LocalProfile(accountId))
        override suspend fun save(profile: LocalProfile) { calls++; error("temporary disk failure") }
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
}
