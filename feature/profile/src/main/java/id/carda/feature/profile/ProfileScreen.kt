package id.carda.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.carda.core.model.HealthCondition
import id.carda.core.model.DeviceCompatibilityRecord
import id.carda.core.model.LocalProfile
import id.carda.core.model.QualityIssue
import id.carda.core.model.SupportClassification
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import id.carda.core.model.Sex

/** Local-only profile form. Health fields never enter the identity API. */
@Composable
fun ProfileScreen(
    profile: LocalProfile,
    deviceCompatibility: DeviceCompatibilityRecord?,
    reminderHour: Int?,
    notificationPermissionGranted: Boolean,
    reminderMessage: String,
    onReminderHourChange: (Int?) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSave: (LocalProfile) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actionMessage: String = "",
    isSaving: Boolean = false,
) {
    var fullName by remember(profile.accountId) { mutableStateOf(profile.fullName) }
    var birthDate by remember(profile.accountId) { mutableStateOf(profile.birthDate.orEmpty()) }
    var sex by remember(profile.accountId) { mutableStateOf(profile.sex) }
    var phone by remember(profile.accountId) { mutableStateOf(profile.phone) }
    var height by remember(profile.accountId) { mutableStateOf(profile.heightCm?.toString().orEmpty()) }
    var weight by remember(profile.accountId) { mutableStateOf(profile.weightKg?.toString().orEmpty()) }
    var conditions by remember(profile.accountId) { mutableStateOf(profile.conditions) }
    var medicines by remember(profile.accountId) { mutableStateOf(profile.medicines) }
    var error by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(profile) {
        fullName = profile.fullName
        birthDate = profile.birthDate.orEmpty()
        sex = profile.sex
        phone = profile.phone
        height = profile.heightCm?.toString().orEmpty()
        weight = profile.weightKg?.toString().orEmpty()
        conditions = profile.conditions
        medicines = profile.medicines
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Profil lokal", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() })
        if (actionMessage.isNotBlank()) Text(actionMessage)
        if (isSaving) Text("Menyimpan perubahan lokal…")
        Text("Informasi ini hanya tersimpan di perangkat. Kondisi dan obat tidak dipakai untuk diagnosis otomatis.")
        Text("Kesiapan perangkat terakhir", style = MaterialTheme.typography.titleMedium)
        if (deviceCompatibility == null) Text("Belum ada pemeriksaan kamera pada akun ini.")
        else {
            val device = deviceCompatibility.profile
            val checkedAt = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.forLanguageTag("id-ID"))
                .format(Instant.ofEpochMilli(deviceCompatibility.observedAtEpochMillis).atZone(ZoneId.systemDefault()))
            Text("Diperiksa $checkedAt waktu perangkat.")
            Text("${device.manufacturer} ${device.model} · Android API ${device.androidApiLevel} · ${device.supportClassification.label()}")
            Text("Versi aplikasi ${device.appVersion}; ABI ${device.abi.ifBlank { "belum tercatat" }}.")
            Text("Kamera belakang: ${if (device.hasRearCamera) "ada" else "tidak ada"}; lampu kilat: ${if (device.hasTorch) "ada" else "tidak ada"}.")
            val size = if (device.analysisWidth == null || device.analysisHeight == null) "belum tercatat"
                else "${device.analysisWidth} × ${device.analysisHeight}"
            val cadence = device.observedFramesPerSecond?.let { "%.1f fps".format(it) } ?: "belum terukur"
            Text("Stream analisis: $size; cadence: $cadence; pipeline ${device.pipelineVersion}.")
            Text("Exposure: ${device.exposureDescription ?: "belum tersedia"}; flash: ${device.flashDescription ?: "belum tersedia"}.")
            if (device.rejectionReasons.isNotEmpty()) Text("Alasan pembatasan: " +
                device.rejectionReasons.joinToString { it.label() } + ".")
            Text("Catatan ini hanya berlaku untuk sesi dan konfigurasi yang diuji, bukan jaminan akurasi.")
        }
        Text("Pengingat pengukuran", style = MaterialTheme.typography.titleMedium)
        Text(if (reminderHour == null) "Pengingat mati." else "Pengingat harian sekitar pukul %02d.00 waktu perangkat. Android dapat menundanya.".format(reminderHour))
        if (!notificationPermissionGranted) {
            Text("Izin notifikasi belum aktif. Pengingat tidak akan muncul sampai izin diberikan.")
            Button(onClick = onOpenNotificationSettings) { Text("Buka pengaturan notifikasi") }
        }
        if (reminderMessage.isNotBlank()) Text(reminderMessage)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !isSaving, onClick = { onReminderHourChange(8) }, modifier = Modifier.fillMaxWidth()) { Text("08.00") }
            Button(enabled = !isSaving, onClick = { onReminderHourChange(12) }, modifier = Modifier.fillMaxWidth()) { Text("12.00") }
            Button(enabled = !isSaving, onClick = { onReminderHourChange(18) }, modifier = Modifier.fillMaxWidth()) { Text("18.00") }
        }
        Button(enabled = !isSaving && reminderHour != null, onClick = { onReminderHourChange(null) }) {
            Text("Matikan pengingat")
        }
        OutlinedTextField(fullName, { fullName = it }, enabled = !isSaving, label = { Text("Nama") })
        OutlinedTextField(birthDate, { birthDate = it }, enabled = !isSaving, label = { Text("Tanggal lahir (YYYY-MM-DD)") })
        Button(enabled = !isSaving, onClick = { sex = Sex.entries[(sex.ordinal + 1) % Sex.entries.size] }) {
            Text("Jenis kelamin: ${sex.label()}")
        }
        OutlinedTextField(phone, { phone = it }, enabled = !isSaving, label = { Text("Nomor telepon") })
        OutlinedTextField(height, { height = it }, enabled = !isSaving, label = { Text("Tinggi badan (cm)") })
        OutlinedTextField(weight, { weight = it }, enabled = !isSaving, label = { Text("Berat badan (kg)") })
        val bmi = runCatching {
            val h = height.toDoubleOrNull()
            val w = weight.toDoubleOrNull()
            if (h == null || w == null || h !in 50.0..300.0 || w !in 2.0..500.0) null
            else w / ((h / 100) * (h / 100))
        }.getOrNull()
        Text(if (bmi == null) "BMI: data tinggi dan berat belum lengkap" else "BMI: %.1f kg/m²".format(bmi))
        Text("Riwayat yang Anda pilih", style = MaterialTheme.typography.titleMedium)
        HealthCondition.entries.forEach { condition ->
            Row {
                Checkbox(
                    checked = condition in conditions,
                    enabled = !isSaving,
                    onCheckedChange = { selected ->
                        conditions = if (selected) conditions + condition else conditions - condition
                    },
                    modifier = Modifier.semantics { contentDescription = condition.label() },
                )
                Text(condition.label(), modifier = Modifier.padding(top = 12.dp))
            }
        }
        OutlinedTextField(medicines, { medicines = it }, enabled = !isSaving, label = { Text("Obat yang digunakan (opsional)") })
        if (error.isNotBlank()) Text(error)
        Button(enabled = !isSaving, onClick = {
            val candidate = runCatching {
                LocalProfile(
                    accountId = profile.accountId,
                    fullName = fullName.trim(),
                    birthDate = birthDate.trim().ifBlank { null },
                    sex = sex,
                    phone = phone.trim(),
                    heightCm = height.trim().ifBlank { null }?.toDouble(),
                    weightKg = weight.trim().ifBlank { null }?.toDouble(),
                    conditions = conditions,
                    medicines = medicines.trim(),
                )
            }.getOrNull()
            if (candidate == null) error = "Periksa tanggal lahir, tinggi, dan berat badan."
            else { error = ""; onSave(candidate) }
        }) { Text("Simpan profil lokal") }
        if (confirmDelete) {
            Text("Hapus profil lokal akun ini dari perangkat? Riwayat pengukuran tidak ikut terhapus.")
            Button(enabled = !isSaving, onClick = { confirmDelete = false; onDelete() }) { Text("Ya, hapus profil") }
            Button(onClick = { confirmDelete = false }) { Text("Batal") }
        } else Button(enabled = !isSaving, onClick = { confirmDelete = true }) { Text("Hapus profil lokal") }
        Button(onClick = onBack) { Text("Kembali") }
    }
}

private fun Sex.label(): String = when (this) {
    Sex.FEMALE -> "Perempuan"
    Sex.MALE -> "Laki-laki"
    Sex.OTHER -> "Lainnya"
    Sex.UNSPECIFIED -> "Tidak diisi"
}

private fun SupportClassification.label(): String = when (this) {
    SupportClassification.COMPATIBLE -> "kompatibel pada sesi ini"
    SupportClassification.RESTRICTED -> "terbatas pada sesi ini"
    SupportClassification.NOT_SUPPORTED -> "tidak didukung"
}

private fun QualityIssue.label(): String = when (this) {
    QualityIssue.NO_REAR_CAMERA -> "kamera belakang tidak tersedia"
    QualityIssue.NO_TORCH, QualityIssue.TORCH_OFF -> "lampu kilat tidak tersedia atau mati"
    QualityIssue.UNSUPPORTED_ANALYSIS_STREAM -> "stream kamera tidak didukung"
    QualityIssue.SAMPLE_BUDGET_EXCEEDED -> "aliran kamera melampaui batas sesi"
    QualityIssue.INSUFFICIENT_SAMPLES -> "sampel belum cukup"
    QualityIssue.INVALID_TIMESTAMPS -> "waktu frame tidak valid"
    QualityIssue.UNSTABLE_FRAME_CADENCE -> "cadence frame tidak stabil"
    QualityIssue.TOO_DARK -> "sinyal terlalu gelap"
    QualityIssue.SATURATED -> "sinyal terlalu terang atau jenuh"
    QualityIssue.POOR_CONTACT -> "cakupan jari kurang"
    QualityIssue.CLIPPED -> "nilai sinyal terpotong"
    QualityIssue.EXCESSIVE_MOTION -> "gerakan terlalu besar"
    QualityIssue.EXCESSIVE_NOISE -> "noise sinyal terlalu tinggi"
    QualityIssue.LOW_PULSATILE_SIGNAL -> "denyut pada sinyal terlalu lemah"
    QualityIssue.SCORE_BELOW_THRESHOLD -> "skor kualitas di bawah batas"
}

private fun HealthCondition.label(): String = when (this) {
    HealthCondition.HYPERTENSION -> "Hipertensi"
    HealthCondition.ARRHYTHMIA -> "Aritmia"
    HealthCondition.TYPE_1_DIABETES -> "Diabetes tipe 1"
    HealthCondition.TYPE_2_DIABETES -> "Diabetes tipe 2"
    HealthCondition.CORONARY_DISEASE -> "Penyakit jantung koroner"
    HealthCondition.HEART_SURGERY -> "Pernah operasi jantung"
    HealthCondition.HEART_FAILURE -> "Gagal jantung"
}
