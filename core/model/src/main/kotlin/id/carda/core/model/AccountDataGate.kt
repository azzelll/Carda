package id.carda.core.model

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serialize finite local writes with account deletion; stale jobs cannot recreate erased data. */
class AccountDataGate {
    private val mutex = Mutex()
    private val deletedAccounts = mutableSetOf<String>()

    suspend fun <T> write(accountId: String, action: suspend () -> T): T = mutex.withLock {
        check(accountId !in deletedAccounts) { "Local account access has ended" }
        action()
    }

    suspend fun erase(accountId: String, cleanup: suspend () -> Unit) = mutex.withLock {
        // Remains blocked even if cleanup fails. The durable deletion journal permits a retry.
        deletedAccounts += accountId
        cleanup()
    }
}

fun interface LocalAccountCleaner {
    suspend fun clean(accountId: String)
}
