package id.carda.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Safety copy derived from repository scope and privacy rules; clinical interpretation remains open. */
@Composable
fun EducationScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Panduan dan batasan Carda", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() })
        Topic("Apa yang dibaca kamera?",
            "PPG (photoplethysmography) adalah sinyal perubahan cahaya dari ujung jari. Grafik saat pengukuran berasal dari kamera dan hanya digunakan sementara. Grafik itu bukan rekaman ECG jantung.")
        Topic("Langkah pengukuran",
            "Pilih kondisi sebelum ukur dan baca persetujuan. Berikan izin kamera, lalu ikuti pemeriksaan perangkat. Letakkan ujung jari menutupi kamera belakang dan lampu kilat dengan nyaman. Pertahankan posisi dan ikuti umpan balik layar. Anda dapat membatalkan sesi; kamera dan lampu kilat akan dihentikan.")
        Topic("Jika sinyal belum layak",
            "Cahaya terlalu rendah, sinyal terlalu terang atau terpotong, kontak yang kurang, gerakan, serta waktu frame yang tidak stabil dapat membuat sesi ditolak. Ikuti alasan yang tampil lalu coba sesi baru. Carda tidak mengganti kegagalan dengan angka nol atau angka perkiraan.")
        Topic("Kualitas dan denyut jantung",
            "SQI (Signal Quality Index) adalah skor teknis kualitas sinyal, bukan persentase akurasi kesehatan. Denyut sementara hanya tampil setelah gate kualitas lolos; tunggu sesi lengkap. Nilai dari build penelitian ini belum dibandingkan dengan alat referensi pada ponsel fisik.")
        Topic("PRV, RMSSD dan SDNN",
            "PRV adalah variabilitas interval denyut dari PPG. RMSSD dan SDNN merangkum variasi tersebut dalam milidetik dan memiliki syarat sampel tersendiri. PRV tidak otomatis sama dengan HRV dari ECG. Lolos pemeriksaan denyut jantung tidak menjamin PRV dapat ditentukan, dan angka ini tidak digunakan untuk memberi label stres atau pemulihan tanpa bukti.")
        Topic("ECG Insight dan metrik penelitian",
            "ECG Insight memerlukan model lokal, pemeriksaan kualitas, evaluasi dan penjelasan batasnya. Estimasi SpO₂, laju napas dan tekanan darah juga memerlukan metode serta pembanding masing-masing. Jika belum didukung, Carda menampilkan status belum dapat ditentukan tanpa membuat angka atau grafik contoh.")
        Topic("Interval R–R dan laju napas berbeda",
            "Interval R–R pada ECG adalah jarak waktu antarpuncak R, sedangkan laju napas dihitung dalam napas per menit. Keduanya bukan metrik yang sama.")
        Topic("Akun, riwayat dan privasi",
            "Registrasi, login baru, pemulihan dan penghapusan akun daring membutuhkan internet. Sesudah login berhasil, pengukuran dan riwayat lokal dapat digunakan offline. Profil dan hasil tetap pada perangkat; login di ponsel lain tidak memulihkan data tersebut. Menghapus riwayat lokal berbeda dari menghapus akun daring. PDF dibagikan hanya saat Anda memilih tujuan atau penerima.")
        Topic("Batas penggunaan",
            "Carda adalah prototipe wellness dan penelitian. Hasilnya bukan diagnosis, layanan darurat, atau dasar mengubah pengobatan. Hasil dan perubahan tren tidak boleh dianggap sebagai kepastian kondisi kesehatan.")
        Text("Versi materi: education-v1 · menunggu uji pemahaman pengguna dan tinjauan ahli.")
        Button(onClick = onBack) { Text("Kembali") }
    }
}

@Composable
private fun Topic(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Text(body, style = MaterialTheme.typography.bodyLarge)
}
