import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  title: "Carda | Prototipe wellness berbasis PPG",
  description: "Carda adalah prototipe penelitian yang mempelajari pengukuran PPG melalui kamera ponsel dengan pemeriksaan kualitas sinyal dan pemrosesan lokal.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="id">
      <body>
        <header className="border-b border-teal-900/10 bg-white">
          <nav aria-label="Navigasi utama" className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-5 py-4">
            <Link href="/" className="text-xl font-bold tracking-tight text-teal-950">Carda</Link>
            <div className="flex flex-wrap gap-2 text-sm font-semibold sm:gap-5">
              <Link href="/#cara-kerja" className="inline-flex min-h-11 items-center px-1 py-3 hover:underline">Cara kerja</Link>
              <Link href="/#kompatibilitas" className="inline-flex min-h-11 items-center px-1 py-3 hover:underline">Kompatibilitas</Link>
              <Link href="/privasi" className="inline-flex min-h-11 items-center px-1 py-3 hover:underline">Privasi</Link>
              <Link href="/#kontak" className="inline-flex min-h-11 items-center px-1 py-3 hover:underline">Kontak</Link>
            </div>
          </nav>
        </header>
        {children}
        <footer className="border-t border-teal-900/10 bg-white px-5 py-8 text-center text-sm text-teal-950/70">
          <p>© 2026 Carda · Prototipe penelitian dan kompetisi</p>
          <p className="mt-2">Bukan alat medis, layanan darurat, atau alat diagnosis.</p>
        </footer>
      </body>
    </html>
  );
}
