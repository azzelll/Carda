# Scope final Carda — 30 Oktober 2026

Disusun 17 September 2026 dari proposal 32 halaman, feedback Juri A, aturan repository, dan keputusan pengguna untuk membangun ulang dari nol. Periode kerja: 17 September–29 Oktober; final: 30 Oktober. Seluruh item berikut adalah target, bukan klaim fitur sudah terimplementasi atau pengujian sudah dilakukan. Loop engineering belum dimulai.

Pengguna akan memberikan Figma terbaru. Desain tersebut menjadi acuan visual implementasi setelah tersedia; mockup proposal dipakai untuk mengidentifikasi inkonsistensi lama, bukan dianggap desain terkini. Fondasi, kontrak domain, pipeline PPG, kamera, dan pengujian dapat dikerjakan sebelum Figma tersedia. Jika desain baru memuat metrik di luar scope atau klaim yang tidak didukung, catat dan selaraskan dengan batas kualitas/safety sebelum implementasi.

**Koreksi pengguna, 17 September 2026: tidak boleh ada pengurangan fitur kecuali memang diminta juri.** Rekomendasi pemangkasan pada versi awal dokumen ini dicabut. Prioritas hanya mengatur urutan pengerjaan. Tenggat, kesulitan teknis, atau validasi yang belum selesai tidak otomatis membolehkan penghapusan fitur.

Pada feedback yang tersedia, tidak ada permintaan eksplisit juri untuk menghapus fitur. Karena itu seluruh fitur yang tercantum dalam proposal tetap masuk inventaris final, dengan status asli yang wajib/opsional/lanjutan dicatat. Fitur yang belum terbukti berstatus **belum selesai atau terhambat validasi**, bukan dicoret, disembunyikan sebagai keputusan scope, atau dianggap selesai karena ada placeholder. Pembatasan output ketika sinyal/model tidak layak tetap berlaku dan berbeda dari penghapusan fitur.

## 1. Keputusan produk

**Carda mempertahankan visi proposal: pengukuran berbasis fingertip PPG, metrik fisiologis, ECG Insight, interpretasi, serta riwayat dan tren dalam aplikasi Android dengan pemrosesan lokal.**

Judul dan fitur utama proposal tidak diganti sepihak menjadi aplikasi heart-rate-only. ECG Insight tetap bagian dari target produk, dengan sifat eksperimental dan kebutuhan validasi seperti yang sudah dinyatakan dalam proposal.

Persona proposal tetap menjadi acuan kebutuhan dan usability, termasuk kelompok usia 40–65 tahun, pengguna pascarawat, dan dewasa muda. Klaim manfaat untuk kelompok tersebut harus dibatasi oleh bukti aktual; kebutuhan persona tidak membuktikan kelayakan diagnosis atau pemantauan pemulihan klinis.

Pembeda yang perlu dibuktikan: penyesuaian akuisisi terhadap kapabilitas perangkat, penolakan hasil yang tidak layak, petunjuk pemulihan pengukuran yang mudah dipahami, dan privasi lokal. Posisikan sebagai kontribusi rekayasa terukur; jangan mengklaim metode PPG/rekonstruksi ECG baru atau fitur yang pasti tidak dimiliki kompetitor.

Jika suatu fitur belum memenuhi syarat, catat kebutuhan, hambatan, pekerjaan tersisa, dan bukti yang masih diperlukan. Jangan menyebut seluruh janji proposal sudah selesai atau mengubah janji untuk menutupi pekerjaan yang belum selesai. Pengurangan fitur hanya dapat dicatat dengan permintaan eksplisit juri beserta sumbernya; kritik tentang bukti atau konsistensi tidak memenuhi syarat tersebut.

## 2. Feedback juri menjadi pekerjaan

| Aspek / skor Juri A | Masalah yang disorot | Perbaikan untuk final | Bukti yang dibawa |
| --- | --- | --- | --- |
| Inovasi / 5 | Integrasi menarik, metode bukan hal baru | Akuisisi sesuai perangkat, SQI yang memberi alasan, dan alur retry yang terbukti membantu | Perbandingan pipeline baseline dengan pipeline ber-SQI: error hasil diterima, persentase sesi menghasilkan hasil, waktu dan retry |
| Dampak dan sustainability / 5 | Dampak klinis, pemeliharaan, keterlibatan dokter, dan operasional belum terbukti | Batasi manfaat pada pengukuran dan pemahaman hasil; tetapkan penanggung jawab rilis, model, dan matriks perangkat | Hasil pilot, rencana pemeliharaan, catatan tinjauan ahli bila benar-benar dilakukan |
| UI/usability / 5 | Baru mockup; tidak ada SUS, waktu, kegagalan, atau uji lansia | Alur ukur sederhana dan aksesibel; perbaiki grafik dan bahasa hasil | APK, uji tugas, SUS, waktu penyelesaian, bantuan dan kegagalan menurut kelompok pengguna |
| Proses pengembangan / 5 | Prosedur pengujian belum disertai hasil | Setiap fitur punya kriteria penerimaan, tes, hasil uji perangkat, dan iterasi | Backlog, catatan perubahan, laporan tes, temuan sebelum/sesudah perbaikan |
| Kesesuaian ide–software / 5 | ECG belum terbukti; metrik dan login tidak konsisten | Pertahankan fitur, lengkapi pembuktian ECG, dan selesaikan kontradiksi status metrik serta kebijakan akun | Matriks kebutuhan–implementasi–pengujian dan demo aktual |
| Urgensi / 6 | PPG tidak menggantikan seluruh informasi ECG | Hubungkan masalah dengan manfaat yang dapat diukur | Pengguna bisa mengukur dan memahami keterbatasan; tanpa janji diagnosis atau penurunan kejadian penyakit |

Juri B dan C berisi `null` pada screenshot; tidak ada masukan tambahan yang bisa disimpulkan.

## 3. Fondasi dan fitur inti — urutan pengerjaan awal

Bagian ini bukan keseluruhan scope final. F09–F20 pada bagian berikut tetap tercakup.

| ID | Fitur dan perilaku | Kriteria penerimaan | Pemilik area |
| --- | --- | --- | --- |
| F01 | **Onboarding dan akses pengguna.** Penjelasan fungsi, batasan, persetujuan, izin kamera, serta kebutuhan registrasi/login dalam proposal. | Pengguna dapat menolak persetujuan/izin dan mencoba kembali. Konflik tanpa akun vs registrasi/login wajib diselesaikan sebagai keputusan produk eksplisit; jangan otomatis menghapus autentikasi atau membuat backend. Profil lengkap dilacak di F15. | `feature:onboarding`, `core:data` |
| F02 | **Cek kesiapan perangkat.** Periksa kamera belakang, torch, konfigurasi stream, cadence aktual, dan probe sinyal. Tampilkan kompatibel, terbatas, atau tidak didukung beserta alasannya. | Perangkat dengan spesifikasi nominal yang cukup tetap dapat ditolak ketika probe gagal. Ada uji profil kompatibel dan terbatas/tidak didukung. Profil mencatat karakteristik aktual dan versi. | `core:camera`, `core:model` |
| F03 | **Pengukuran terpandu.** Pilihan aktivitas sebelum ukur, instruksi posisi jari; grafik PPG aktual; estimasi HR sementara setelah jendela valid; hitung mundur, konfirmasi batal, jeda dan retry. | Kamera/torch berhenti saat batal, keluar, background, timeout, dan error. Sesi dibatasi; progres tidak menghitung segmen buruk sebagai data valid. Angka sementara maupun akhir harus lolos gate; angka sementara tidak disimpan sebagai hasil akhir. | `feature:measurement`, `core:camera` |
| F04 | **SQI dengan alasan dan tindakan.** Periksa kegelapan, saturasi/clipping, kontak, gerak/noise, cadence, dan jumlah sampel. Beri instruksi singkat sesuai gangguan yang teramati. | Sinyal gagal tidak menghasilkan angka, ECG, atau titik tren; hasil lama tidak tertinggal saat retry. Threshold memiliki versi dan dasar evaluasi. Uji fixture mencakup seluruh kondisi penolakan. | `core:ppg` |
| F05 | **Hasil denyut yang transparan.** BPM dari PPG yang lolos gate; waktu, durasi valid, kualitas, dan batasan. Detail teknis tersedia sebagai informasi sekunder. | Hasil cocok dengan pipeline yang diuji. Ada pengujian terhadap pembanding pada waktu yang diselaraskan. Tanpa kategori diagnosis, skor risiko penyakit, atau persentase “akurasi” yang berasal dari SQI. | `core:ppg`, `feature:result` |
| F06 | **Riwayat dan tren lokal.** Daftar sesi, pencarian, filter, detail, grafik semua metrik yang tervalidasi untuk Hari Ini/7 Hari/30 Hari/3 Bulan, perbandingan baseline dan badge tren. | Hanya hasil lolos gate masuk tren; data sedikit menampilkan keadaan “belum cukup data”. Sumbu grafik sesuai waktu dan satuan metrik. Interpretasi badge punya dasar dan tidak menyimpulkan penyakit. Data bertahan setelah restart; hapus semua meminta konfirmasi. | `core:data`, `feature:history` |
| F07 | **Edukasi dan aksesibilitas.** Penjelasan PPG, cara ukur, penyebab gagal, arti kualitas, serta batas hasil. Teks dapat diperbesar, kontrol mudah disentuh, label pembaca layar, status tidak bergantung pada warna. | Uji alur pada font besar dan pembaca layar; pengguna usia lanjut diikutkan dalam uji tugas. Instruksi gagal bisa dipahami dan ditindaklanjuti. | `core:designsystem`, seluruh `feature:*` |
| F08 | **Privasi dan informasi perangkat.** Pemrosesan offline, ringkasan lokal, hapus data, halaman kompatibilitas/batasan, versi aplikasi dan pipeline. | Tidak menyimpan frames/raw PPG; tidak mengirim hasil lewat telemetry/logcat; audit backup Android agar data pengukuran tidak tersalin ke cloud tanpa keputusan opt-in. Pengukuran tetap berfungsi tanpa internet. | `core:data`, `feature:profile`, `app` |

Ketentuan implementasi:

- Ikuti arsitektur aplikasi native Kotlin, Compose, CameraX, Room, DataStore, dan Hilt yang sudah diputuskan di repository. Landing page proposal adalah deliverable komunikasi terpisah, bukan klien web pengukuran. Backend dan aliran data eksternal tidak diasumsikan dari penyebutan akun; selesaikan keputusan akses pengguna dahulu.
- Penyesuaian kamera hanya memakai kemampuan yang tersedia dan telah diuji. Fallback tetap harus lulus SQI; jangan melonggarkan ambang agar semua HP diberi label kompatibel.
- Jangan menyebut “tekanan jari terlalu kuat” sebagai kepastian jika sinyal hanya menunjukkan saturasi/kontak buruk. Jelaskan observasi dan sarankan memperbaiki posisi.
- Durasi 30–60 detik adalah titik awal dari proposal, bukan bukti kecukupan untuk semua metrik. Tetapkan jendela valid dan timeout berdasarkan evaluasi.
- Jangan menggambarkan sinyal tidak teratur sebagai noise secara otomatis hanya karena periodisitasnya rendah. Dokumentasikan kondisi yang belum didukung dan alasan penolakan tanpa menyimpulkan kondisi medis.
- Simpan ringkasan: waktu, nilai yang layak, SQI, durasi, profil perangkat, status, serta versi pipeline/model. Diagnostik teknis hanya agregat; bukan raw data atau hasil kesehatan dalam log.

## 4. HRV dan ECG tetap dalam scope, dengan pembuktian khusus

### F09 — Variabilitas denyut berbasis PPG

Pertahankan **RMSSD dan SDNN** sesuai kebutuhan proposal. Jelaskan bahwa variabilitas diperoleh dari denyut PPG/PRV dan tidak otomatis setara dengan HRV berbasis ECG. RMSSD dapat dikerjakan lebih dahulu; SDNN tetap pekerjaan yang harus dituntaskan. Interpretasi terkait kondisi otonom/pemulihan yang muncul dalam proposal atau mockup perlu dasar evaluasi tersendiri dan dilacak melalui F14.

Syarat rilis:

- Timestamp, deteksi interval, penanganan artefak, durasi, dan cadence memenuhi evaluasi khusus PRV. Lolos gate HR belum otomatis cukup untuk PRV.
- Bandingkan interval terhadap referensi ECG yang memadai; angka BPM dari pulse oximeter tidak memvalidasi PRV.
- Dokumentasikan populasi, posisi, durasi, perangkat, error dan batas kesepakatan; tampilkan nilai hanya pada kondisi yang sudah diuji.
- Jika gagal, HR boleh tetap tampil bila memenuhi gate-nya sendiri; PRV menampilkan “belum dapat ditentukan”, tanpa angka tebakan.

**14 Oktober** adalah checkpoint kemajuan validasi, bukan tanggal penghapusan fitur. Jika pembanding atau bukti belum memadai, laporkan hambatan dan pekerjaan tersisa; fitur tetap dalam scope dan belum boleh diklaim selesai.

PRV dan HRV tidak dapat otomatis dianggap setara; pengaruh sampling dan lokasi ukur perlu dipertimbangkan. Studi [Burma dkk., 2024](https://pubmed.ncbi.nlm.nih.gov/38610260/) mendukung perlunya validasi tersendiri, tetapi bukan validasi Carda dan bukan dasar menyalin ambang sampling universal.

### F10 — ECG Insight eksperimental

Karena rekonstruksi ECG ada dalam judul dan janji utama proposal, lakukan pemeriksaan kelayakan sejak minggu pertama. Modul ini tidak cukup dinilai dari grafik yang tampak seperti ECG.

Inventaris fungsinya tetap mencakup rekonstruksi on-device, grafik yang dapat digeser, penanda estimasi R-peak, RR interval rata-rata, interpretasi pola, confidence, dan disclaimer. Kategori yang tertulis di proposal (Normal Sinus Rhythm, Slightly Irregular, Irregular Rhythm, Possible Arrhythmia, Low Confidence) dicatat sebagai kebutuhan interpretasi yang harus ditinjau dan dibuktikan; pencatatan ini bukan izin menampilkan diagnosis tanpa dasar. Tidak boleh diam-diam mengganti seluruh fungsi tersebut dengan grafik dekoratif.

Syarat rilis:

1. Model, lisensi, model card, versi, preprocessing, dan kontrak input/output tersedia; inferensi benar-benar berjalan lokal.
2. Ada evaluasi dengan pasangan PPG–ECG tersinkron; pemisahan train/test menurut subjek mencegah kebocoran antarwindow.
3. Laporkan error waveform, korelasi dan kesalahan waktu puncak bila relevan, termasuk contoh gagal. Korelasi saja tidak membuktikan kegunaan klinis.
4. Evaluasi kesesuaian domain kamera ponsel. Hasil pada PPG sensor klinis saja tidak cukup untuk menampilkan rekonstruksi pengguna Carda sebagai hasil tervalidasi.
5. Gate mencakup SQI, bentuk input, model tersedia, perangkat, dan ukuran kepercayaan yang memang punya dasar evaluasi. Jangan mengganti confidence model dengan SQI atau persentase rekaan.
6. UI menyebut “estimasi model dari PPG — eksperimental”; tanpa label aritmia, normal sinus rhythm, atau diagnosis. Kegagalan model tidak menggagalkan hasil HR yang valid.

**23 September:** inventaris model/data/lisensi dan jalur validasi harus jelas. **7 Oktober:** review hasil integrasi, bukti yang tersedia, dan hambatan. Bukti offline dilaporkan sebagai bukti offline; belum memenuhi seluruh kebutuhan integrasi pengguna. Jika input/model belum memenuhi syarat, tampilkan status tidak dapat ditentukan dengan alasan. Pekerjaan yang belum tervalidasi tetap terbuka; jangan memakai animasi/grafik contoh sebagai hasil pengguna atau menganggap fitur selesai hanya karena memiliki halaman.

## 5. Fitur proposal yang dipertahankan dalam inventaris

| ID | Fitur/deliverable | Status sumber dan pekerjaan yang diperlukan |
| --- | --- | --- |
| F11 | **Estimasi SpO₂** | Opsional pada batasan, tetapi eksplisit pada kebutuhan dan UI. Tetap dicatat dan dikerjakan jalur metode/validasinya; selesaikan kontradiksi status tanpa otomatis mencoret fitur. HR/SQI yang lolos tidak memvalidasi SpO₂. |
| F12 | **Respiratory rate** | Opsional pada batasan, eksplisit pada kebutuhan dan UI. Pertahankan ekstraksi, hasil, interpretasi dan tren; perlukan referensi serta evaluasi metrik tersendiri. |
| F13 | **Estimasi tekanan darah** | Disebut pada tujuan/mockup dan pengembangan lanjutan pada batasan. Pertahankan dalam inventaris dengan status sumber tersebut, identifikasi kebutuhan metode/kalibrasi/validasi. Tidak otomatis dipromosikan menjadi fitur tervalidasi maupun dihapus karena tenggat. |
| F14 | **Interpretasi, risk alert, dan ringkasan kondisi** | Pertahankan fungsi informasi kontekstual, peringatan berbasis aturan, dan ringkasan hasil. Dasar aturan, kualitas input, batas kepercayaan, dan wording harus ditinjau. Label klinis tanpa bukti adalah hambatan yang perlu diselesaikan, bukan alasan menghapus modul. |
| F15 | **Profil, BMI, riwayat kesehatan dan obat** | Pertahankan kebutuhan input/edit profil, BMI, pilihan riwayat kondisi, obat opsional, dan preferensi. Tetapkan kebutuhan masing-masing data, penyimpanan lokal, persetujuan dan penghapusan; jangan mengarang pengaruh obat atau memaksakan pengumpulan data yang tidak diperlukan. |
| F16 | **Berbagi, unduh dan ekspor hasil** | Eksplisit pada kebutuhan hasil/profil. Tetap dicatat; pengguna memilih tindakan dan tujuan. Gunakan ringkasan yang diizinkan beserta kualitas/batasannya, tanpa raw PPG/frame atau pengiriman otomatis. |
| F17 | **Notifikasi dan pengaturannya** | Tercantum pada arsitektur informasi profil. Pertahankan; detail jenis notifikasi perlu diselaraskan dengan Figma terbaru. Notifikasi tidak memberi dasar untuk pemantauan kamera background. |
| F18 | **Dashboard dan insight kontekstual** | Pertahankan hasil terbaru, ringkasan kondisi/insight, dan tombol mulai. Klaim dari mockup seperti pemulihan/stres memerlukan definisi dan bukti; jangan menampilkan narasi kesehatan rekaan. |
| F19 | **Landing page** | Deliverable komunikasi eksplisit pada bagian 4.3. Selaraskan fitur, status dan klaim dengan aplikasi aktual. Tidak dihapus sebagai penghematan waktu. |
| F20 | **Persiapan distribusi/Play Store** | Pertahankan privacy policy, data safety, aset, closed testing dan persiapan rilis yang disebut proposal. Pisahkan pekerjaan tim dari waktu peninjauan platform; persyaratan lomba tetap perlu diverifikasi. |

Kebutuhan akun tetap terlacak di F01; tidak dihapus karena keputusan awal tanpa akun. Cloud sync, koneksi caregiver/dokter, wearable, dan klien iOS tidak otomatis ditambahkan: proposal sendiri membatasi atau menempatkannya sebagai kemungkinan masa depan. Ini mempertahankan batas asli, bukan pemangkasan fitur oleh agen.

Konflik dengan roadmap MVP lama harus dicatat terbuka. Instruksi terbaru pengguna mempertahankan inventaris proposal untuk final; ketentuan kualitas, privasi, dan larangan klaim diagnosis tanpa bukti tetap berlaku. Adanya fitur dalam inventaris bukan otorisasi mengirim data atau menampilkan estimasi tanpa validasi.

## 6. Perbaikan langsung pada proposal dan UI

Nomor di bawah adalah **halaman PDF (1–32)**, bukan nomor halaman cetak.

| Lokasi | Temuan | Keputusan perbaikan |
| --- | --- | --- |
| PDF 2, 7–9, 17–18 | Klaim deteksi dini/monitoring penyakit serta pengguna pascarawat berhadapan dengan batas non-diagnostik | Pertahankan fungsi dan persona; perbaiki batas klaim berdasarkan bukti tanpa sepihak mengganti identitas produk |
| PDF 9 vs 18, 26 | Tanpa akun bertentangan dengan registrasi/login dan data profil luas | Catat sebagai keputusan akses pengguna yang harus diselesaikan secara eksplisit; jangan menghapus autentikasi atau membuat backend otomatis |
| PDF 9 vs 19–20, 28–29 | SpO₂/RR opsional tetapi ditampilkan sebagai fitur wajib; mockup juga memuat tekanan darah | Pertahankan F11–F13, samakan status di seluruh dokumen setelah keputusan eksplisit, dan lengkapi metode serta validasi |
| PDF 13 vs arsitektur repo | Proposal menyebut React Native; keputusan repo adalah native Kotlin | Dokumentasikan perubahan keputusan dan gunakan satu stack secara konsisten |
| PDF 20, 30 | Kategori irama dan interpretasi ECG belum disertai bukti | Pertahankan F10/F14; buktikan fungsi dan tinjau wording. Hasil yang belum layak tidak boleh tampil sebagai diagnosis |
| PDF 28–29 | Grafik menyerupai ECG ditempatkan pada konteks pengukuran kamera/denyut | Grafik akuisisi harus memakai PPG aktual dan dilabeli PPG |
| PDF 29 vs 20 | Mockup memberi label “Normal” pada SpO₂ 90%, sementara tabel kebutuhan menyebut batas berbeda | Perbaiki konsistensi aturan, contoh, dan copy berdasarkan tinjauan yang memadai; kartu SpO₂ tetap terlacak di F11 |
| PDF 21, 31 | Grafik tren memakai waveform ECG dan label membaik/memburuk | Gunakan tanggal vs BPM/nilai metrik yang diizinkan; deskripsikan perubahan numerik tanpa penilaian kesehatan |
| PDF 28 | Klaim MVP end-to-end dan validasi pengguna tanpa tabel bukti | Ubah status sesuai hasil rebuild aktual, sertakan versi APK, hasil uji, jumlah partisipan dan batasan |

## 7. Paket bukti wajib

Angka rekrutmen di bawah adalah **target pilot yang diusulkan untuk perencanaan**, bukan sampel yang sudah tersedia, persyaratan lomba, atau kecukupan validasi klinis. Jadwalkan perangkat, pembanding, dan partisipan pada minggu pertama.

| Paket | Target kerja | Hasil yang dilaporkan |
| --- | --- | --- |
| Pengujian perangkat | 5 Android dari setidaknya 3 merek jika tersedia; variasi kelas/perilaku kamera; minimal satu profil terbatas | Model/OS/versi build, resolusi, cadence, status torch/exposure jika tersedia, keberhasilan, penolakan, alasan, waktu dan batas dukungan |
| Evaluasi HR | Target 20 partisipan, 3 percobaan berpasangan per orang dalam kondisi istirahat | Identitas/metode pembanding, sinkronisasi, jumlah orang dan percobaan, MAE, bias, limits of agreement, error per perangkat, retry dan seluruh sesi gagal |
| Pengujian usability | Target 15–20 orang, termasuk setidaknya 5 usia 60+ bila dapat direkrut | Selesaikan ukur, pulihkan kegagalan, pahami hasil/batasan, temukan riwayat, dan hapus data; laporkan SUS, waktu, keberhasilan, bantuan, serta temuan per kelompok |
| Kontribusi SQI | Evaluasi baseline vs gate memakai fixture sintetis/dataset berlisensi yang sesuai dan pengujian terkontrol | Error hasil diterima beserta proporsi sesi yang ditolak/diterima. Jangan “menaikkan akurasi” dengan menyembunyikan mayoritas kegagalan |
| Privasi dan reliabilitas | Tes offline, lifecycle kamera, hapus data, audit log/network/backup | Bukti tidak ada output setelah SQI gagal, tidak ada kamera/torch tertinggal, dan tidak ada kebocoran data |
| HRV/ECG | Evaluasi wajib untuk menuntaskan F09/F10; mulai jalur bukti sejak awal | Model/pipeline version, referensi, protokol, data evaluasi yang sah, batasan dan kegagalan |
| SpO₂/RR/tekanan darah dan interpretasi | Rancang evaluasi terpisah untuk F11–F14; catat status wajib/opsional/lanjutan sesuai keputusan yang diselaraskan | Metode, pembanding yang sesuai tiap metrik, kondisi penggunaan, error, batasan, dan kebutuhan bukti yang masih terbuka; evaluasi HR bukan penggantinya |
| Profil/ekspor/notifikasi/dashboard/distribusi | Penerimaan F15–F20 sesuai kebutuhan proposal | Alur nyata, kendali pengguna, privasi, aksesibilitas, konsistensi klaim, serta status persiapan distribusi |

Untuk HR, utamakan referensi ECG bila tersedia; pulse oximeter dapat menjadi pembanding awal dengan keterbatasan dan averaging yang dicatat. Jangan menyebut smartwatch sebagai gold standard tanpa dasar. Percobaan berulang bukan partisipan independen; laporkan keduanya dan perhitungkan pengelompokan per orang.

Sebelum uji akhir, tetapkan ambang penerimaan error, durasi, coverage dan retry pada protokol, lalu bekukan pipeline. Jangan mengubah ambang setelah melihat hasil uji akhir demi memenuhi target. Target UX internal awal: setidaknya 90% peserta menyelesaikan alur tanpa bantuan dan median waktu sampai hasil maksimal 3 menit; laporkan hasil apa adanya. SQI gagal harus memblokir hasil pada seluruh tes penolakan.

Pengumpulan manusia perlu persetujuan dan pengaturan penelitian institusi yang relevan. Tidak ada unggahan/raw recording otomatis dari aplikasi. Bukti yang masuk repository hanya agregat yang tidak mengidentifikasi pengguna; fixtures harus sintetis atau diizinkan dan tidak sensitif. Jangan menyalin data pengukuran partisipan ke screenshot presentasi tanpa dasar persetujuan.

Untuk sustainability, siapkan satu halaman: pemilik pengujian perangkat, jadwal uji ulang tiap rilis/perubahan pipeline, biaya perangkat dan pembanding, versi/rollback model, kebijakan dukungan perangkat, dan jalur masukan pengguna. Catat tinjauan tenaga medis bila benar-benar dilakukan; jangan menyebut mitra/validasi klinis sebelum ada bukti. Klasifikasi regulasi dan langkah distribusi perlu ditinjau sebelum peluncuran publik; disclaimer sendiri tidak menentukan status regulasi.

## 8. Jadwal menuju final

| Tanggal 2026 | Hasil yang harus tersedia | Keputusan/checkpoint |
| --- | --- | --- |
| 17–23 September | Fondasi Android, state/event/effect, kontrak domain, pipeline sintetis/probe kamera; inventaris lengkap F01–F20 dan kebutuhan pembanding | Buktikan capture nyata pada sedikitnya satu HP; mulai riset/validasi HRV, ECG, SpO₂, RR, tekanan darah dan interpretasi; selesaikan keputusan akun/status metrik |
| 24–30 September | Guided capture, timestamp/ROI/filter, SQI, penolakan dan retry; tes lifecycle | Capture nyata di profil kompatibel dan terbatas; sinyal buruk tidak menghasilkan nilai |
| 1–7 Oktober | HR, hasil, riwayat/hapus data, profil perangkat; perbandingan HR awal; progres integrasi metrik/model | Checkpoint F09–F14 dan hambatannya; fitur dasar bisa didemokan offline end-to-end tanpa mengurangi inventaris |
| 8–14 Oktober | Tren, profil, ekspor, notifikasi, dashboard, edukasi, aksesibilitas; integrasi metrik/model; landing page dan persiapan distribusi | Review kelengkapan F01–F20, bukti dan kapasitas; bekukan penambahan kebutuhan baru, bukan membuang fitur yang belum selesai |
| 15–21 Oktober | Uji pengguna/perangkat, evaluasi per metrik/model dan penyelesaian gap integrasi | Laporan agregat dan daftar masalah berprioritas; perbaikan yang mengubah pipeline memerlukan evaluasi ulang |
| 22–27 Oktober | Perbaikan blocker dan uji ulang yang relevan; rilis kandidat, laporan hasil, proposal selaras | Tidak ada blocker privasi, lifecycle, atau keluaran setelah SQI gagal |
| 28–29 Oktober | Latihan demo, APK kandidat final, video cadangan berlabel, slide dan matriks bukti | Bekukan kandidat; verifikasi ulang instalasi dan skenario demo |
| 30 Oktober | Presentasi/demo final | Jelaskan scope dan keterbatasan sesuai bukti aktual |

Uji perangkat dan pengguna berlangsung iteratif sejak fitur dasar dapat digunakan; 15–21 Oktober adalah target jendela evaluasi akhir, bukan pertama kali mencoba di luar HP pengembang. Jadwal merupakan target kerja, bukan bukti seluruh fitur pasti selesai. Jika kapasitas, metode, atau data tidak cukup, laporkan risiko lebih awal beserta kebutuhan sumber daya dan pekerjaan tersisa; jangan memotong fitur, mengurangi pengujian, atau mengarang keberhasilan untuk mengejar tanggal.

## 9. Acuan loop engineering berikutnya

Setiap loop mengambil satu ID fitur dengan state/event/effect, implementasi terkecil sesuai ownership, tes perilaku, bukti verifikasi, dan pembaruan status. Selalu pisahkan hasil tes sintetis/emulator dari bukti perangkat fisik/manusia. Agen dapat membantu implementasi dan analisis, tetapi pengambilan data nyata dan perekrutan tetap membutuhkan tim.

Urutan fondasi: F01 + fondasi → F02/F03 + pipeline deterministik → F04/F05 → F06/F08 → F07. Jalur metode dan bukti F09–F14 dimulai minggu pertama; F15–F20 memiliki item implementasi tersendiri. Urutan bukan izin mengabaikan fitur di belakang.

Skenario demo dasar: onboarding/akses sesuai keputusan akun → cek perangkat → coba dengan kontak buruk → aplikasi menolak dan memberi petunjuk → ulangi dengan sinyal layak → tampilkan hasil dan kualitas → lihat tren lokal → tunjukkan kontrol hapus data. Tambahkan skenario penerimaan F09–F20, bukan menganggap demo dasar mewakili seluruh scope. Data contoh, bila dipakai untuk menunjukkan histori beberapa hari, harus berlabel simulasi dan terpisah dari sesi nyata.

Selesai berarti kriteria fitur terpenuhi dan bukti tersedia, bukan hanya layar dapat dibuka. Checklist final mencakup unit test, lint, pengujian CameraX/Room yang relevan, matriks perangkat, laporan usability dan tiap metrik, konsistensi dokumen, serta status seluruh F01–F20. Untuk setiap fitur, bedakan belum dimulai, dikerjakan, terhambat, diimplementasikan, dan diverifikasi. Catat sumber permintaan juri jika kelak benar-benar ada pengurangan fitur; saat ini tidak ada.

## 10. Sumber dan batas interpretasi

- Proposal: `RASAGA_Proposal Gemastik_PPL (2).pdf`, 32 halaman; fokus kebutuhan PDF 18–23, implementasi 26–27, hasil dan mockup 28–31.
- Screenshot feedback 16 September 2026: Juri A, dengan enam aspek penilaian di atas. Feedback adalah masukan evaluasi, bukan perintah implementasi otomatis.
- [Arsitektur](architecture.md), [roadmap MVP](mvp-roadmap.md), dan [kualitas/privasi/safety](ppg-quality-and-safety.md) tetap menjadi batas implementasi.
- [Burma dkk., 2024 — PRV dan HRV](https://pubmed.ncbi.nlm.nih.gov/38610260/): dasar kehati-hatian terminologi dan evaluasi variabilitas, bukan bukti performa aplikasi.
- [Xuan dkk., 2023 — kalibrasi kamera smartphone](https://www.frontiersin.org/journals/digital-health/articles/10.3389/fdgth.2023.1301019/full): eksperimen menunjukkan pengaruh pengaturan kamera dan kalibrasi terhadap pengukuran optik pada perangkat yang diuji. Ini mendukung kebutuhan pemeriksaan perangkat; tidak membuktikan akurasi HR/SpO₂ Carda atau bahwa algoritma yang sama otomatis cocok untuk semua HP.

Prioritas, tenggat keputusan, target rekrutmen, dan kriteria UX di dokumen ini merupakan rekomendasi perencanaan untuk rebuild hingga 30 Oktober. Hasil studi lain, skor kualitas sinyal, dan hasil pilot tidak boleh diubah menjadi klaim validasi klinis produk.
