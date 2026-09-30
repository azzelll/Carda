package id.carda.core.auth

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** A no-backup marker lets local cleanup resume if it fails after server deletion. */
interface PendingDeletionStore {
    fun read(): String?
    fun mark(accountId: String)
    fun clear()
}

class FilePendingDeletionStore(context: Context) : PendingDeletionStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "carda_pending_account_deletion"))

    override fun read(): String? = runCatching {
        file.openRead().bufferedReader().use { it.readText() }
            .takeIf { UUID.fromString(it).toString() == it }
    }.getOrNull()

    override fun mark(accountId: String) {
        require(UUID.fromString(accountId).toString() == accountId)
        val output = file.startWrite()
        try {
            output.write(accountId.toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (failure: Throwable) {
            file.failWrite(output)
            throw failure
        }
    }

    override fun clear() { file.delete() }
}

enum class AccountDeletionOutcome {
    REMOTE_FAILED, LOCAL_CLEANUP_PENDING, LOCAL_CLEANUP_UNTRACKED, COMPLETE,
}

class AccountDeletionCoordinator(private val pending: PendingDeletionStore) {
    suspend fun delete(
        accountId: String,
        deleteRemote: suspend () -> Unit,
        deleteLocal: suspend (String) -> Unit,
    ): AccountDeletionOutcome {
        try { deleteRemote() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return AccountDeletionOutcome.REMOTE_FAILED }
        return withContext(NonCancellable) {
            val marked = try { pending.mark(accountId); true } catch (_: Exception) { false }
            try {
                if (marked) finishPending(accountId, deleteLocal)
                else { deleteLocal(accountId); AccountDeletionOutcome.COMPLETE }
            } catch (_: Exception) {
                if (marked) AccountDeletionOutcome.LOCAL_CLEANUP_PENDING
                else AccountDeletionOutcome.LOCAL_CLEANUP_UNTRACKED
            }
        }
    }

    suspend fun resume(deleteLocal: suspend (String) -> Unit): AccountDeletionOutcome {
        val accountId = pending.read() ?: return AccountDeletionOutcome.COMPLETE
        return try { finishPending(accountId, deleteLocal) }
        catch (_: Exception) { AccountDeletionOutcome.LOCAL_CLEANUP_PENDING }
    }

    private suspend fun finishPending(
        accountId: String,
        deleteLocal: suspend (String) -> Unit,
    ): AccountDeletionOutcome {
        deleteLocal(accountId)
        pending.clear()
        return AccountDeletionOutcome.COMPLETE
    }
}
