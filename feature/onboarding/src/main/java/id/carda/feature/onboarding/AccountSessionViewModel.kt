package id.carda.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.carda.core.auth.AccountDeletionCoordinator
import id.carda.core.auth.AccountDeletionOutcome
import id.carda.core.auth.IdentityEnvironment
import id.carda.core.model.LocalAccountCleaner
import id.carda.core.auth.PendingDeletionStore
import id.carda.core.auth.SessionStorage
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccountSessionState(
    val accountId: String?,
    val busy: Boolean = false,
    val cleanupPending: Boolean = false,
    val message: String = "",
    val generation: Long = 0,
) {
    val accessAllowed: Boolean get() = !busy && !cleanupPending
    fun permits(owner: String?, revision: Long): Boolean =
        accessAllowed && accountId == owner && generation == revision
}

/** Account operations survive Activity recreation and invalidate all in-flight feature access. */
@HiltViewModel
class AccountSessionViewModel @Inject constructor(
    private val sessions: SessionStorage,
    private val identity: IdentityEnvironment,
    private val deletion: AccountDeletionCoordinator,
    pending: PendingDeletionStore,
    private val cleaner: LocalAccountCleaner,
) : ViewModel() {
    private val mutable = MutableStateFlow(AccountSessionState(sessions.read()?.accountId,
        cleanupPending = pending.read() != null))
    val state = mutable.asStateFlow()

    init { if (mutable.value.cleanupPending) resumeCleanup() }

    fun reconcileSignIn() {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(accountId = sessions.read()?.accountId,
            generation = mutable.value.generation + 1,
            message = "Login berhasil. Pengukuran lokal dapat berjalan offline.")
    }

    fun logout() {
        if (mutable.value.busy || mutable.value.accountId == null) return
        begin()
        viewModelScope.launch {
            var message = "Sesi lokal dihapus."
            try { identity.access?.logout() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "Sesi lokal dihapus; pencabutan server perlu koneksi." }
            finally {
                sessions.clear()
                mutable.value = mutable.value.copy(accountId = null, busy = false, message = message)
            }
        }
    }

    val canDeleteAccount: Boolean get() = identity.access != null

    fun deleteAccount(password: String) {
        val accountId = mutable.value.accountId ?: return
        val access = identity.access ?: return
        if (mutable.value.busy || password.isBlank()) return
        begin()
        viewModelScope.launch {
            val outcome = deletion.delete(accountId, { access.deleteAccount(password) }, cleaner::clean)
            val ended = outcome != AccountDeletionOutcome.REMOTE_FAILED
            if (ended) sessions.clear()
            mutable.value = mutable.value.copy(accountId = if (ended) null else accountId,
                busy = false, cleanupPending = outcome == AccountDeletionOutcome.LOCAL_CLEANUP_PENDING,
                message = when (outcome) {
                    AccountDeletionOutcome.REMOTE_FAILED -> "Akun daring belum terhapus; data lokal tetap ada. Periksa kata sandi dan koneksi."
                    AccountDeletionOutcome.LOCAL_CLEANUP_PENDING -> "Akun daring terhapus, tetapi pembersihan data lokal perlu diulang."
                    AccountDeletionOutcome.LOCAL_CLEANUP_UNTRACKED -> "Akun daring terhapus, tetapi data lokal belum seluruhnya bersih. Bersihkan data aplikasi dari pengaturan perangkat."
                    AccountDeletionOutcome.COMPLETE -> "Akun daring dan data lokal akun ini dihapus."
                })
        }
    }

    fun resumeCleanup() {
        if (mutable.value.busy || !mutable.value.cleanupPending) return
        begin()
        viewModelScope.launch {
            val outcome = deletion.resume(cleaner::clean)
            val pending = outcome == AccountDeletionOutcome.LOCAL_CLEANUP_PENDING
            mutable.value = mutable.value.copy(busy = false, cleanupPending = pending,
                message = if (pending) "Data lokal belum seluruhnya terhapus. Coba lagi atau bersihkan data aplikasi dari pengaturan perangkat."
                else "Pembersihan data lokal selesai.")
        }
    }

    private fun begin() {
        mutable.value = mutable.value.copy(busy = true, generation = mutable.value.generation + 1)
    }
}
