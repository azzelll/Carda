package id.carda.feature.onboarding

import id.carda.core.auth.*
import id.carda.core.model.LocalAccountCleaner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun delayedLogoutInvalidatesCaptureAndExportBeforeNetworkCompletes() = runTest(dispatcher) {
        val store = FakeSessions()
        val remote = FakeIdentity()
        val vm = model(store, remote)
        val prior = vm.state.value
        assertTrue(prior.permits(prior.accountId, prior.generation))
        vm.logout()
        testScheduler.runCurrent()
        assertTrue(vm.state.value.busy)
        assertFalse(vm.state.value.permits(prior.accountId, prior.generation))
        // Observers after recreation obtain the same retained operation, rather than a saved Boolean.
        assertTrue(vm.state.value.busy)
        remote.release.complete(Unit)
        testScheduler.runCurrent()
        assertFalse(vm.state.value.busy)
        assertNull(vm.state.value.accountId)
        assertNull(store.value)
        store.value = session("b8aee0fe-d60c-4137-b7b5-e2990d9b806b")
        vm.reconcileSignIn()
        assertFalse(vm.state.value.permits(prior.accountId, prior.generation))
    }

    @Test fun delayedDeletionBlocksAccessAndCleansOnlyOriginatingAccount() = runTest(dispatcher) {
        val store = FakeSessions()
        val remote = FakeIdentity()
        val cleaned = mutableListOf<String>()
        val vm = model(store, remote, LocalAccountCleaner { cleaned += it })
        val prior = vm.state.value
        vm.deleteAccount("test-password")
        vm.logout() // duplicate operation is rejected
        testScheduler.runCurrent()
        assertFalse(vm.state.value.accessAllowed)
        assertTrue(cleaned.isEmpty())
        remote.release.complete(Unit)
        testScheduler.runCurrent()
        assertEquals(listOf(prior.accountId), cleaned)
        assertEquals(1, remote.calls)
        assertNull(vm.state.value.accountId)
        assertFalse(vm.state.value.busy)
    }

    @Test fun failedDeletionRestoresAccessWithoutRestoringOldExportGeneration() = runTest(dispatcher) {
        val store = FakeSessions()
        val remote = FakeIdentity().apply { failure = IllegalStateException("offline") }
        val vm = model(store, remote)
        val prior = vm.state.value
        vm.deleteAccount("test-password")
        remote.release.complete(Unit)
        testScheduler.runCurrent()
        assertEquals(prior.accountId, vm.state.value.accountId)
        assertTrue(vm.state.value.accessAllowed)
        assertFalse(vm.state.value.permits(prior.accountId, prior.generation))
    }

    private fun model(store: FakeSessions, remote: FakeIdentity,
        cleaner: LocalAccountCleaner = LocalAccountCleaner {}) = AccountSessionViewModel(
        store, IdentityEnvironment(remote), AccountDeletionCoordinator(FakePending()), FakePending(), cleaner)

    private class FakePending : PendingDeletionStore {
        private var value: String? = null
        override fun read() = value
        override fun mark(accountId: String) { value = accountId }
        override fun clear() { value = null }
    }
    private class FakeSessions : SessionStorage {
        var value: AuthSession? = session("ec87a9d1-6035-443b-b14a-ad36d879c9d8")
        override fun read() = value
        override fun clear() { value = null }
    }
    private class FakeIdentity : IdentityAccess {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        var failure: Exception? = null
        private suspend fun waitForResponse() { calls++; release.await(); failure?.let { throw it } }
        override suspend fun logout() = waitForResponse()
        override suspend fun deleteAccount(password: String) = waitForResponse()
        override suspend fun register(email: String, password: String) = Unit
        override suspend fun verifyEmail(token: String, newPassword: String) = Unit
        override suspend fun login(email: String, password: String) = error("unused")
        override suspend fun requestPasswordReset(email: String) = Unit
        override suspend fun confirmPasswordReset(token: String, newPassword: String) = Unit
    }
    companion object {
        private fun session(id: String) = AuthSession(id, "fake-access", "fake-refresh", 1L)
    }
}
