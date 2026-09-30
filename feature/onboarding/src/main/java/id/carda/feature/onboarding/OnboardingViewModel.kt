package id.carda.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.carda.core.auth.AuthApiException
import id.carda.core.auth.IdentityEnvironment
import id.carda.core.model.LocalProfileRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.util.Locale

enum class AccountAction { LOGIN, REGISTER, VERIFY_EMAIL, REQUEST_RESET, CONFIRM_RESET }

sealed interface OnboardingState {
    data object Unconfigured : OnboardingState
    data class Ready(val message: String = "", val clearSecretsRevision: Int = 0) : OnboardingState
    data class Working(val action: AccountAction) : OnboardingState
    data object LocalProfilePending : OnboardingState
    data object SignedIn : OnboardingState
}

/** Credentials exist only as request parameters, never in exposed or saved UI state. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val environment: IdentityEnvironment,
    private val profiles: LocalProfileRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow<OnboardingState>(
        if (environment.access == null) OnboardingState.Unconfigured else OnboardingState.Ready())
    val state = mutableState.asStateFlow()
    private var completedRequests = 0
    private var registeredAddress: String? = null
    private var registrationDetails: RegistrationDetails? = null
    private var pendingProfileAccount: String? = null

    fun acknowledgeSignIn() { mutableState.value = OnboardingState.Ready() }

    fun submit(action: AccountAction, email: String = "", password: String = "", token: String = "",
        details: RegistrationDetails? = null) {
        val access = environment.access ?: return
        if (mutableState.value is OnboardingState.Working || pendingProfileAccount != null) return
        val address = email.trim()
        val code = token.trim()
        val invalid = when (action) {
            AccountAction.LOGIN, AccountAction.REGISTER, AccountAction.REQUEST_RESET ->
                address.length !in 3..254 || '@' !in address || address.any { it.isWhitespace() }
            else -> code.isBlank() || code.length > 128
        }
        if (invalid) {
            mutableState.value = OnboardingState.Ready("Periksa email atau kode yang dimasukkan.", completedRequests)
            return
        }
        if (action in setOf(AccountAction.REGISTER, AccountAction.VERIFY_EMAIL, AccountAction.CONFIRM_RESET) &&
            password.length !in 12..128) {
            mutableState.value = OnboardingState.Ready("Kata sandi baru harus 12–128 karakter.", completedRequests)
            return
        }
        if (action == AccountAction.LOGIN && password.isBlank()) {
            mutableState.value = OnboardingState.Ready("Isi kata sandi untuk masuk.", completedRequests)
            return
        }
        mutableState.value = OnboardingState.Working(action)
        viewModelScope.launch {
            try {
                val message = when (action) {
                    AccountAction.LOGIN -> {
                        val session = access.login(address, password)
                        if (registeredAddress == address.lowercase(Locale.ROOT) && registrationDetails != null) {
                            pendingProfileAccount = session.accountId
                            finishLocalProfile()
                        } else {
                            registrationDetails = null
                            registeredAddress = null
                            mutableState.value = OnboardingState.SignedIn
                        }
                        return@launch
                    }
                    AccountAction.REGISTER -> {
                        access.register(address, password)
                        registeredAddress = address.lowercase(Locale.ROOT)
                        registrationDetails = details
                        "Jika pendaftaran diterima, periksa email untuk kode verifikasi."
                    }
                    AccountAction.VERIFY_EMAIL -> {
                        access.verifyEmail(code, password)
                        "Email terverifikasi. Masuk dengan kata sandi yang Anda pilih saat verifikasi."
                    }
                    AccountAction.REQUEST_RESET -> {
                        access.requestPasswordReset(address)
                        "Jika akun tersedia, instruksi reset dikirim lewat email."
                    }
                    AccountAction.CONFIRM_RESET -> {
                        access.confirmPasswordReset(code, password)
                        "Kata sandi diperbarui. Silakan masuk."
                    }
                }
                mutableState.value = OnboardingState.Ready(message, ++completedRequests)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val message = when ((failure as? AuthApiException)?.status) {
                    429 -> "Terlalu banyak percobaan. Tunggu sebentar lalu coba lagi."
                    400 -> "Permintaan belum diterima. Periksa email, kata sandi, atau kode."
                    401 -> "Akun, kata sandi, atau kode belum dapat digunakan. Periksa lalu coba lagi."
                    else -> "Permintaan belum berhasil. Periksa koneksi lalu coba lagi."
                }
                mutableState.value = OnboardingState.Ready(message, ++completedRequests)
            }
        }
    }

    fun retryLocalProfile() {
        if (mutableState.value != OnboardingState.LocalProfilePending) return
        mutableState.value = OnboardingState.Working(AccountAction.LOGIN)
        viewModelScope.launch { finishLocalProfile() }
    }

    private suspend fun finishLocalProfile() {
        val account = pendingProfileAccount ?: return
        val details = registrationDetails ?: return
        try {
            val current = profiles.observe(account).first()
            profiles.save(details.applyTo(current)) // Preserve health/height/weight preferences.
            pendingProfileAccount = null
            registrationDetails = null
            registeredAddress = null
            mutableState.value = OnboardingState.SignedIn
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableState.value = OnboardingState.LocalProfilePending }
    }
}
