package id.carda.core.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountDeletionCoordinatorTest {
    private class FakePending : PendingDeletionStore {
        var value: String? = null
        override fun read() = value
        override fun mark(accountId: String) { value = accountId }
        override fun clear() { value = null }
    }

    @Test fun remoteFailureKeepsLocalData() = runBlocking {
        val pending = FakePending()
        var localCalls = 0
        val outcome = AccountDeletionCoordinator(pending).delete("account-a",
            deleteRemote = { error("wrong password or offline") },
            deleteLocal = { localCalls++ })
        assertEquals(AccountDeletionOutcome.REMOTE_FAILED, outcome)
        assertEquals(0, localCalls)
        assertNull(pending.value)
    }

    @Test fun failedLocalCleanupCanResumeAfterRemoteDeletion() = runBlocking {
        val pending = FakePending()
        var remoteCalls = 0
        val coordinator = AccountDeletionCoordinator(pending)
        val outcome = coordinator.delete("account-a",
            deleteRemote = { remoteCalls++ },
            deleteLocal = { error("disk temporarily unavailable") })
        assertEquals(AccountDeletionOutcome.LOCAL_CLEANUP_PENDING, outcome)
        assertEquals("account-a", pending.value)
        assertEquals(AccountDeletionOutcome.COMPLETE,
            coordinator.resume { accountId -> assertEquals("account-a", accountId) })
        assertEquals(1, remoteCalls)
        assertNull(pending.value)
    }

    @Test fun failedJournalAndCleanupRequireManualDeviceCleanup() = runBlocking {
        val pending = object : PendingDeletionStore {
            override fun read(): String? = null
            override fun mark(accountId: String) { error("storage unavailable") }
            override fun clear() = Unit
        }
        val outcome = AccountDeletionCoordinator(pending).delete("account-a",
            deleteRemote = { }, deleteLocal = { error("storage unavailable") })
        assertEquals(AccountDeletionOutcome.LOCAL_CLEANUP_UNTRACKED, outcome)
    }
}
