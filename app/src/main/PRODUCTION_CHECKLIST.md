# SI TAHU - Checklist Rilis Production

Gunakan zip ini sebagai baseline final. Sebelum rilis ke mitra umum, jalankan checklist ini dari project root Android Studio.

## 1. Build bersih
- File > Sync Project with Gradle Files
- Build > Clean Project
- Build > Assemble Project
- Build > Generate Signed App Bundle / APK

## 2. Payment gateway
- Debug build: tombol `Simulasi Bayar` QRIS aktif untuk test.
- Release build: tombol simulasi tidak muncul; kasir memakai `Cek Status Pembayaran` setelah pelanggan scan QRIS dari aplikasi pembayaran.
- Backend/Vercel wajib memakai secret key production jika sudah live.
- Set `ALLOWED_ORIGIN` di Vercel agar CORS tidak terbuka untuk semua origin.

## 3. Role akses
- ADMIN boleh masuk mode admin dan mode kasir.
- KASIR hanya boleh masuk mode kasir.
- Terapkan `firestore.rules` di Firebase Console sebelum production.

## 4. Konsistensi data
Uji setelah input data baru:
- Dashboard admin langsung berubah.
- Dashboard kasir langsung berubah.
- Pengeluaran langsung muncul di dashboard/laporan.
- Penyesuaian stok langsung mengubah monitoring stok dan riwayat stok.
- Penjualan QRIS selesai masuk riwayat, laporan, dan stok.
- Transaksi QRIS pending/expired tidak boleh cetak nota lunas.

## 5. Skenario QRIS
- Pending: hanya tampil/cetak QRIS, tidak cetak nota lunas.
- Selesai: tampil animasi berhasil, nota boleh dicetak.
- Belum Terbayar/expired: status berubah, QRIS hilang, nota lunas tidak tersedia.
- Batal: stok dikembalikan dan riwayat berstatus batal.

## 6. Data dummy final
Buat minimal:
- 1 akun ADMIN aktif.
- 1 akun KASIR aktif.
- 3 produk aktif dengan harga kasir/pasar/rumahan.
- 1 produksi dasar, 1 produksi olahan.
- 1 penjualan tunai, 1 QRIS pending, 1 QRIS selesai.
- 1 pengeluaran.
- 1 penyesuaian stok.

## 7. Catatan keamanan
- `android:allowBackup` sudah dimatikan.
- File backup rules mengecualikan shared preferences, database, dan file lokal.
- Secret key payment gateway tidak boleh masuk ke aplikasi Android.
