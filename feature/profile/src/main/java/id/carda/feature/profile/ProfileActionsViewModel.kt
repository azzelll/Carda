package id.carda.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.carda.core.model.LocalProfile
import id.carda.core.model.LocalProfileRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileActionState(val accountId: String? = null, val busy: Boolean = false, val message: String = "")

/** Finite mutations have an error state; storage exceptions never escape a UI coroutine. */
@HiltViewModel
class ProfileActionsViewModel @Inject constructor(private val profiles: LocalProfileRepository) : ViewModel() {
    private val mutable = MutableStateFlow(ProfileActionState())
    val state = mutable.asStateFlow()

    fun save(profile: LocalProfile) = perform(profile.accountId, "Profil lokal tersimpan.") { profiles.save(profile) }
    fun delete(accountId: String) = perform(accountId, "Profil lokal dihapus.") { profiles.delete(accountId) }
    fun consent(accountId: String, version: String?) = perform(accountId, "Persetujuan diperbarui.") {
        if (version == null) profiles.revokeConsent(accountId) else profiles.acceptConsent(accountId, version)
    }
    fun reminder(accountId: String, hour: Int?) = perform(accountId,
        if (hour == null) "Pengingat dimatikan." else "Pengingat sekitar pukul %02d.00 dipilih.".format(hour)) {
        profiles.setReminderHour(accountId, hour)
    }

    private fun perform(accountId: String, success: String, action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = ProfileActionState(accountId, busy = true)
        viewModelScope.launch {
            mutable.value = try {
                action()
                ProfileActionState(accountId, message = success)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { ProfileActionState(accountId, message = "Perubahan belum tersimpan. Periksa kembali dan coba lagi.") }
        }
    }
}
