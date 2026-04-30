ANDROID SI TAHU - KASIR PROFESIONAL QRIS XENDIT
===============================================

Yang berubah di Android:
- Checkout kasir hanya menampilkan Tunai dan QRIS.
- QRIS Xendit tampil langsung di halaman checkout, tidak memakai WebView dan tidak membuka browser.
- Keranjang dikunci saat QRIS aktif agar nominal tidak berubah setelah QR dibuat.
- Tombol checkout berubah sesuai alur:
  Tunai: Simpan Tunai
  QRIS belum dibuat: Buat QRIS
  QRIS aktif: Cek Status QRIS
- Transaksi QRIS baru disimpan jika status Xendit COMPLETED.
- Ada countdown 15 menit sebagai kontrol layar kasir.

Dependency wajib:
implementation 'com.google.zxing:core:3.5.3'

Konfigurasi penting:
Buka:
java/muhamad/irfan/si_tahu/ui/penjualan/AktivitasCheckoutRumahan.kt

Cek konstanta:
private const val XENDIT_API_BASE = "https://xendit-sitahu-api.vercel.app"
private const val XENDIT_TEST_MODE = false

Jika masih memakai domain lama Vercel, ubah XENDIT_API_BASE sesuai domain aktif.
Jika masih test dengan simulasi Xendit, ubah XENDIT_TEST_MODE menjadi true.
