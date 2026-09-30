import Link from "next/link";

export default function PrivacyPage() {
  return (
    <main className="mx-auto max-w-3xl px-5 py-16 leading-relaxed">
      <p className="text-sm font-semibold uppercase tracking-wide text-teal-700">Rancangan · 29 September 2026</p>
      <h1 className="mt-3 text-4xl font-bold">Informasi privasi Carda</h1>
      <p className="mt-6">Dokumen ini menjelaskan rancangan teknis pada build pengembangan. Kebijakan publik final, identitas pengelola, kontak, masa simpan server, dan pemeriksaan hukum masih memerlukan persetujuan tim sebelum distribusi.</p>
      <h2 className="mt-10 text-2xl font-semibold">Data akun</h2>
      <p className="mt-3">Layanan akun menggunakan email, hash kata sandi, token sesi yang disimpan sebagai hash, dan metadata keamanan minimum untuk registrasi, login, reset kata sandi, serta penghapusan akun. Akun baru dan pemulihan kata sandi memerlukan internet.</p>
      <h2 className="mt-10 text-2xl font-semibold">Data kesehatan dan kamera</h2>
      <p className="mt-3">Frame kamera dan sinyal PPG mentah diproses sementara di perangkat selama sesi aktif. Profil kesehatan, ringkasan pengukuran yang lolos pemeriksaan kualitas, dan riwayat berada di perangkat; data tersebut tidak disinkronkan ke layanan akun. Masuk di ponsel baru tidak memulihkan riwayat dari ponsel lama.</p>
      <h2 className="mt-10 text-2xl font-semibold">Pilihan Anda</h2>
      <p className="mt-3">Anda dapat menghapus riwayat dan profil lokal. Penghapusan akun daring adalah tindakan terpisah yang memerlukan internet. Ekspor PDF dan berbagi hanya dimulai saat Anda memilihnya; tujuan file atau aplikasi penerima berada di bawah pilihan Anda. Pengingat lokal memerlukan izin notifikasi dan dapat dimatikan.</p>
      <h2 className="mt-10 text-2xl font-semibold">Batasan</h2>
      <p className="mt-3">Build ini masih dalam pengujian. Audit jaringan, backup, keamanan layanan, dan kebijakan publik final belum selesai. Jangan gunakan Carda untuk diagnosis atau keputusan pengobatan.</p>
      <Link href="/" className="mt-10 inline-block font-semibold text-teal-800 underline underline-offset-4">Kembali ke beranda</Link>
    </main>
  );
}
