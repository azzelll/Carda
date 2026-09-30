package id.carda.core.model

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AccountDataGateTest {
    @Test fun deletionWaitsForInFlightWriteAndRejectsStaleWriteAfterward() = runTest {
        val gate = AccountDataGate()
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        var rows = 0
        val write = launch { gate.write("account-a") {
            writeStarted.complete(Unit); releaseWrite.await(); rows++
        } }
        writeStarted.await()
        val cleanup = launch { gate.erase("account-a") { rows = 0 } }
        testScheduler.runCurrent()
        assertFalse(cleanup.isCompleted)
        releaseWrite.complete(Unit)
        write.join(); cleanup.join()
        assertEquals(0, rows)
        var rejected = false
        try { gate.write("account-a") { rows++ } } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
        assertEquals(0, rows)
        gate.write("account-b") { rows++ }
        assertEquals(1, rows)
    }

    @Test fun failedCleanupStillBlocksStaleWritesAndCanBeRetried() = runTest {
        val gate = AccountDataGate()
        runCatching { gate.erase("account-a") { error("disk unavailable") } }
        var staleWriteExecuted = false
        assertTrue(runCatching { gate.write("account-a") { staleWriteExecuted = true } }.exceptionOrNull()
            is IllegalStateException)
        assertFalse(staleWriteExecuted)
        var retried = false
        gate.erase("account-a") { retried = true }
        assertTrue(retried)
    }
}
