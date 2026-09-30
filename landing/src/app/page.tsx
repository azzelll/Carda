import { Activity, Camera, HeartPulse, LockKeyhole, ShieldCheck } from "lucide-react";
import Link from "next/link";

const principles = [
  { title: "Kualitas dahulu", description: "Sinyal yang tidak cukup baik ditolak dan diberi petunjuk untuk mencoba lagi.", Icon: Activity },
  { title: "Pemrosesan di perangkat", description: "Frame kamera, sinyal PPG mentah, profil kesehatan, dan riwayat tidak dikirim ke layanan akun.", Icon: LockKeyhole },
  { title: "Batasan terbuka", description: "Hasil adalah informasi wellness dan memerlukan pengujian pada perangkat serta kondisi penggunaan nyata.", Icon: ShieldCheck },
];

export default function HomePage() {
  return (
    <main>
      <section className="bg-gradient-to-br from-teal-950 via-teal-900 to-emerald-800 px-5 py-20 text-white">
        <div className="mx-auto max-w-6xl">
          <p className="mb-5 inline-flex rounded-full border border-white/40 px-4 py-2 text-sm">Prototipe Carda · dalam pengembangan</p>
          <h1 className="max-w-3xl text-4xl font-bold leading-tight sm:text-6xl">Pahami sinyal. Ketahui batasnya.</h1>
          <p className="mt-6 max-w-2xl text-lg leading-relaxed text-teal-50">Carda meneliti pengukuran photoplethysmography (PPG) dari kamera belakang dan lampu kilat ponsel Android. Aplikasi memeriksa kualitas sinyal sebelum memperlihatkan hasil.</p>
          <div className="mt-8 flex flex-wrap gap-3">
            <a href="#cara-kerja" className="rounded-xl bg-white px-6 py-3 font-semibold text-teal-950 hover:bg-teal-50">Pelajari cara kerja</a>
            <a href="#ketersediaan" className="rounded-xl border border-white px-6 py-3 font-semibold hover:bg-white/10">Status aplikasi</a>
          </div>
          <p className="mt-8 max-w-2xl text-sm text-teal-50">Carda bukan alat medis. Jangan gunakan hasilnya untuk diagnosis, penanganan darurat, atau perubahan pengobatan.</p>
        </div>
      </section>

      <section id="cara-kerja" className="mx-auto max-w-6xl px-5 py-16">
        <h2 className="text-3xl font-bold">Cara kerja yang sedang diuji</h2>
        <div className="mt-8 grid gap-5 md:grid-cols-3">
          <article className="rounded-2xl bg-white p-6 shadow-sm"><Camera aria-hidden="true" className="mb-4 text-teal-700"/><h3 className="text-xl font-semibold">1. Rekam sementara</h3><p className="mt-3 leading-relaxed">Jari diletakkan pada kamera belakang dan lampu kilat. Kamera aktif selama sesi yang Anda mulai.</p></article>
          <article className="rounded-2xl bg-white p-6 shadow-sm"><Activity aria-hidden="true" className="mb-4 text-teal-700"/><h3 className="text-xl font-semibold">2. Periksa kualitas</h3><p className="mt-3 leading-relaxed">Perangkat memeriksa cahaya, kontak, kestabilan, dan waktu sampel. Sinyal buruk menghasilkan alasan dan pilihan ulang.</p></article>
          <article className="rounded-2xl bg-white p-6 shadow-sm"><HeartPulse aria-hidden="true" className="mb-4 text-teal-700"/><h3 className="text-xl font-semibold">3. Lihat informasi</h3><p className="mt-3 leading-relaxed">Pada sinyal yang lolos, build penelitian menghitung detak jantung dan indikator variabilitas denyut berbasis PPG. Ketepatannya pada pengguna nyata masih perlu pembanding.</p></article>
        </div>
      </section>

      <section className="bg-white px-5 py-16">
        <div className="mx-auto max-w-6xl">
          <h2 className="text-3xl font-bold">Apa yang tersedia dalam build pengembangan?</h2>
          <p className="mt-4 max-w-3xl leading-relaxed">Alur akun lokal uji, penolakan perangkat tanpa lampu kilat, pemrosesan PPG/SQI, riwayat ringkasan lokal, profil, dan ekspor PDF sudah memiliki implementasi awal. Pengukuran ujung ke ujung pada ponsel fisik dan ketepatan fisiologis belum terverifikasi.</p>
          <div className="mt-8 rounded-2xl border border-amber-300 bg-amber-50 p-6">
            <h3 className="font-semibold">Fitur penelitian yang masih terbuka</h3>
            <p className="mt-2 leading-relaxed">ECG Insight, estimasi SpO₂, laju napas, tekanan darah, serta interpretasi risiko memerlukan metode, kalibrasi, alat pembanding, dan tinjauan ahli. Fitur tersebut tetap dalam lingkup pengembangan, tanpa angka atau label klinis yang belum terbukti.</p>
          </div>
        </div>
      </section>

      <section id="kompatibilitas" className="mx-auto max-w-6xl px-5 py-16">
        <h2 className="text-3xl font-bold">Kompatibilitas perangkat</h2>
        <p className="mt-4 max-w-3xl leading-relaxed">Carda menargetkan Android 8.0 (API 26) atau lebih baru, kamera RGB belakang, dan lampu kilat. Ketersediaan perangkat keras saja belum menjamin sinyal layak. Aplikasi memeriksa kemampuan kamera dan kualitas sinyal saat digunakan; daftar ponsel tervalidasi belum tersedia.</p>
      </section>

      <section id="ketersediaan" className="bg-teal-50 px-5 py-16">
        <div className="mx-auto max-w-6xl">
          <h2 className="text-3xl font-bold">Status unduhan</h2>
          <p className="mt-4 max-w-3xl leading-relaxed">Carda belum tersedia untuk unduhan publik. Tautan resmi akan ditambahkan setelah build, pengujian perangkat, pemeriksaan privasi, dan keputusan distribusi tercatat. Jangan memasang APK dari sumber yang mengatasnamakan Carda tanpa konfirmasi tim.</p>
        </div>
      </section>

      <section id="kontak" className="mx-auto max-w-6xl px-5 py-16">
        <h2 className="text-3xl font-bold">Kontak dan kebijakan</h2>
        <p className="mt-4 max-w-3xl leading-relaxed">Saluran kontak publik tim belum ditetapkan. Informasi kontak yang telah disetujui tim akan ditambahkan sebelum halaman ini diterbitkan sebagai situs resmi.</p>
        <Link href="/privasi" className="mt-5 inline-block font-semibold text-teal-800 underline underline-offset-4">Baca rancangan informasi privasi</Link>
      </section>
    </main>
  );
}
