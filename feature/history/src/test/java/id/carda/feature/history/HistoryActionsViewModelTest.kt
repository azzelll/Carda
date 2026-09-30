package id.carda.feature.history

import id.carda.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryActionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun failedDeletionIsShownAndCanBeRetriedWithoutDeletingOtherAccount() = runTest(dispatcher) {
        val requested = mutableListOf<String>()
        val repo = object : MeasurementRepository {
            override suspend fun save(accountId: String, summary: MeasurementSummary): Long = error("unused")
            override fun observeHistory(accountId: String) = flowOf(emptyList<StoredMeasurement>())
            override suspend fun find(accountId: String, id: Long): StoredMeasurement? = null
            override suspend fun deleteAll(accountId: String): Int {
                requested += accountId
                if (requested.size == 1) error("disk unavailable")
                return 1
            }
        }
        val vm = HistoryActionsViewModel(repo)
        vm.deleteAll("fixture-account")
        vm.deleteAll("fixture-account")
        testScheduler.runCurrent()
        assertEquals(listOf("fixture-account"), requested)
        assertTrue(vm.state.value.message.contains("belum terhapus"))
        assertFalse(vm.state.value.busy)
        vm.deleteAll("fixture-account")
        testScheduler.runCurrent()
        assertEquals(listOf("fixture-account", "fixture-account"), requested)
        assertTrue(vm.state.value.message.contains("ini dihapus"))
    }
}
