Integrasi Midtrans Payment Link untuk kasir SI Tahu

File Android yang diubah:
1. java/muhamad/irfan/si_tahu/ui/penjualan/AktivitasCheckoutRumahan.kt
2. res/layout/activity_cashier_checkout.xml
3. java/muhamad/irfan/si_tahu/data/RepositoriFirebaseUtama.kt

File Vercel yang disertakan:
1. api/buat-payment-link.js
2. api/cek-status.js

Alur kasir:
1. Kasir pilih metode QRIS.
2. Tombol berubah menjadi Buat Link Pembayaran.
3. Aplikasi membuat Payment Link ke https://midtrans-sitahu-api.vercel.app/api/buat-payment-link.
4. Link Midtrans otomatis dibuka di browser.
5. Keranjang dikunci selama ada draft pembayaran.
6. Kasir tekan Cek Status Pembayaran.
7. Jika status settlement/capture, transaksi baru disimpan ke Firestore dan stok berkurang.
8. Data paymentGateway, paymentOrderId, paymentUrl, dan statusPembayaran ikut tersimpan di dokumen Penjualan.

Catatan:
- Pastikan Vercel environment variable MIDTRANS_SERVER_KEY dan MIDTRANS_IS_PRODUCTION sudah benar.
- Untuk saat ini MIDTRANS_IS_PRODUCTION harus false karena masih memakai Midtrans Sandbox.
- Kalau URL Vercel kamu berubah, ubah konstanta MIDTRANS_API_BASE di AktivitasCheckoutRumahan.kt.
- Jika cek status sebelum pelanggan membuka/membayar link, status akan tetap pending.

UPDATE WEBVIEW IN-APP PAYMENT
- Payment Link Midtrans sekarang dibuka di dalam aplikasi lewat AktivitasMidtransPaymentWebView, bukan browser luar.
- Kasir tetap berada di SI Tahu selama pembayaran.
- Jika halaman Midtrans membuka deeplink e-wallet/aplikasi lain, WebView akan meneruskan ke aplikasi terkait.
- Setelah pelanggan membayar, tekan "Cek Status Pembayaran" di halaman pembayaran atau panel checkout.
- Activity baru: java/muhamad/irfan/si_tahu/ui/penjualan/AktivitasMidtransPaymentWebView.kt
- Layout baru: res/layout/activity_midtrans_payment_webview.xml
