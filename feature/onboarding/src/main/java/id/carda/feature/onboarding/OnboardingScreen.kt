package id.carda.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import id.carda.core.model.Sex

@Composable
fun OnboardingRoute(onSignedIn: () -> Unit, viewModel: OnboardingViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state) {
        if (state == OnboardingState.SignedIn) {
            onSignedIn()
            viewModel.acknowledgeSignIn()
        }
    }
    OnboardingScreen(state, viewModel::retryLocalProfile, viewModel::submit)
}

/** Input controls are transient; operation state and effects belong to the ViewModel. */
@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onRetryLocalProfile: () -> Unit = {},
    onSubmit: (AccountAction, String, String, String, RegistrationDetails?) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var registration by rememberSaveable { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf(Sex.UNSPECIFIED) }
    var phone by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf("") }
    val revision = (state as? OnboardingState.Ready)?.clearSecretsRevision
    LaunchedEffect(revision) {
        if (revision != null && revision > 0) { password = ""; token = "" }
    }
    LaunchedEffect(state) {
        if (state == OnboardingState.LocalProfilePending || state == OnboardingState.SignedIn) {
            password = ""; token = ""
        }
    }
    if (state == OnboardingState.Unconfigured) {
        Text("Server akun belum dikonfigurasi pada build ini.")
        return
    }
    if (state == OnboardingState.LocalProfilePending) {
        Text("Anda sudah masuk, tetapi profil lokal belum tersimpan. Coba simpan kembali.")
        Button(onClick = onRetryLocalProfile) { Text("Ulangi menyimpan profil lokal") }
        return
    }
    val enabled = state is OnboardingState.Ready
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(email, { email = it }, enabled = enabled, label = { Text("Email") })
        OutlinedTextField(password, { password = it }, enabled = enabled,
            label = { Text("Kata sandi") }, visualTransformation = PasswordVisualTransformation())
        Text("Untuk daftar, verifikasi email, atau reset, gunakan kata sandi 12–128 karakter. Saat verifikasi, masukkan kata sandi yang ingin Anda pakai untuk masuk.")
        Button(enabled = enabled, onClick = { onSubmit(AccountAction.LOGIN, email, password, token, null) }) { Text("Masuk") }
        Button(enabled = enabled, onClick = { registration = !registration; formError = "" }) { Text("Daftar") }
        if (registration) {
            Text("Detail profil baru disimpan di ponsel setelah Anda berhasil masuk pada sesi aplikasi ini. Jika aplikasi ditutup atau dimulai ulang sebelum login, isi kembali detail melalui Profil lokal setelah masuk. Hanya email dan kredensial dikirim ke server.")
            OutlinedTextField(fullName, { fullName = it.take(100) }, enabled = enabled, label = { Text("Nama untuk profil lokal") })
            OutlinedTextField(birthDate, { birthDate = it.take(10) }, enabled = enabled, label = { Text("Tanggal lahir (YYYY-MM-DD)") })
            Button(enabled = enabled, onClick = { sex = Sex.entries[(sex.ordinal + 1) % Sex.entries.size] }) {
                Text("Jenis kelamin: " + when (sex) {
                    Sex.UNSPECIFIED -> "Tidak diisi"
                    Sex.FEMALE -> "Perempuan"
                    Sex.MALE -> "Laki-laki"
                    Sex.OTHER -> "Lainnya"
                })
            }
            OutlinedTextField(phone, { phone = it.take(32) }, enabled = enabled, label = { Text("Nomor telepon untuk profil lokal") })
            if (formError.isNotBlank()) Text(formError)
            Button(enabled = enabled, onClick = {
                val details = runCatching {
                    RegistrationDetails(fullName.trim(), birthDate.trim().ifBlank { null }, sex, phone.trim())
                }.getOrNull()
                if (details == null) formError = "Periksa tanggal lahir dan nomor telepon."
                else { formError = ""; onSubmit(AccountAction.REGISTER, email, password, token, details) }
            }) { Text("Kirim pendaftaran") }
        }
        OutlinedTextField(token, { token = it }, enabled = enabled,
            label = { Text("Kode verifikasi atau reset") }, visualTransformation = PasswordVisualTransformation())
        Button(enabled = enabled, onClick = { onSubmit(AccountAction.VERIFY_EMAIL, email, password, token, null) }) { Text("Verifikasi email dan tetapkan kata sandi") }
        Button(enabled = enabled, onClick = { onSubmit(AccountAction.REQUEST_RESET, email, password, token, null) }) { Text("Minta reset kata sandi") }
        Button(enabled = enabled, onClick = { onSubmit(AccountAction.CONFIRM_RESET, email, password, token, null) }) { Text("Konfirmasi reset") }
        when (state) {
            is OnboardingState.Ready -> if (state.message.isNotBlank()) Text(state.message)
            is OnboardingState.Working -> Text("Memproses permintaan akun…")
            else -> Unit
        }
    }
}
