package muhamad.irfan.si_tahu.ui.penjualan

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import muhamad.irfan.si_tahu.data.ItemKeranjang
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.data.SessionKeranjangRumahan
import muhamad.irfan.si_tahu.databinding.ActivityCashierCheckoutBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.umum.AdapterKeranjang
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.InputRupiah
import muhamad.irfan.si_tahu.util.PembuatQrBitmap
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val XENDIT_API_BASE = "https://xendit-sitahu-api.vercel.app"
private const val XENDIT_TEST_MODE = false
private const val QRIS_EXPIRE_MS = 15 * 60 * 1000L

class AktivitasCheckoutRumahan : AktivitasDasar() {

    private lateinit var binding: ActivityCashierCheckoutBinding
    private lateinit var cartAdapter: AdapterKeranjang

    private var products: List<Produk> = emptyList()
    private var pendingQris: XenditQris? = null
    private var pendingItems: List<ItemKeranjang> = emptyList()
    private var sedangProses = false
    private var qrisDialog: AlertDialog? = null
    private var qrisCountdown: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        binding = ActivityCashierCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Checkout Kasir", "Tunai dan QRIS Xendit")

        setupAdapter()
        setupView()
        loadProducts()
    }

    override fun onResume() {
        super.onResume()
        renderCheckout()
    }

    override fun onDestroy() {
        hentikanCountdownQris()
        qrisDialog?.dismiss()
        qrisDialog = null
        super.onDestroy()
    }

    private fun setupAdapter() {
        cartAdapter = AdapterKeranjang(
            onIncrease = { item ->
                if (tahanPerubahanKeranjang()) return@AdapterKeranjang
                val product = products.firstOrNull { it.id == item.productId } ?: return@AdapterKeranjang
                val success = SessionKeranjangRumahan.changeQty(item.productId, 1, stokLayakJual(product))
                if (!success) {
                    showMessage("Stok layak jual ${product.name} tidak mencukupi")
                    return@AdapterKeranjang
                }
                renderCheckout()
            },
            onDecrease = { item ->
                if (tahanPerubahanKeranjang()) return@AdapterKeranjang
                val product = products.firstOrNull { it.id == item.productId } ?: return@AdapterKeranjang
                SessionKeranjangRumahan.changeQty(item.productId, -1, stokLayakJual(product))
                renderCheckout()
            },
            onRemove = { item ->
                if (tahanPerubahanKeranjang()) return@AdapterKeranjang
                SessionKeranjangRumahan.remove(item.productId)
                renderCheckout()
            },
            getProduk = { productId -> products.firstOrNull { it.id == productId } }
        )
    }

    private fun setupView() {
        binding.rvCart.layoutManager = LinearLayoutManager(this)
        binding.rvCart.adapter = cartAdapter

        binding.spPayment.adapter = AdapterSpinner.stringAdapter(this, listOf("Tunai", "QRIS"))
        binding.spPayment.onItemSelectedListener = CheckoutSpinnerListener { renderCheckout() }

        InputRupiah.pasang(binding.etCashPaid)
        binding.etCashPaid.addTextChangedListener { renderCheckout() }

        binding.btnSaveTransaction.setOnClickListener { saveTransaction() }
        binding.btnXenditShowQris.setOnClickListener {
            pendingQris?.let { tampilkanDialogQris(it) } ?: showMessage("QRIS belum dibuat")
        }
        binding.ivXenditQr.setOnClickListener {
            pendingQris?.let { tampilkanDialogQris(it) }
        }
        binding.btnXenditCheckStatus.setOnClickListener { cekStatusQrisXendit() }
        binding.btnXenditCancel.setOnClickListener { konfirmasiBatalkanQris() }
        binding.btnXenditSimulate.isVisible = XENDIT_TEST_MODE
        binding.btnXenditSimulate.setOnClickListener { simulasiBayarXendit() }
    }

    private fun loadProducts() {
        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatProdukKasir() }
                .onSuccess { result ->
                    products = result
                    renderCheckout()
                }
                .onFailure {
                    products = emptyList()
                    showMessage(it.message ?: "Gagal memuat data produk")
                    renderCheckout()
                }
        }
    }

    private fun currentItems(): List<ItemKeranjang> = SessionKeranjangRumahan.getItems()

    private fun totalAmount(): Long = SessionKeranjangRumahan.totalAmount()

    private fun totalQty(): Int = SessionKeranjangRumahan.totalQty()

    private fun stokLayakJual(product: Produk): Int = product.safeStock + product.nearExpiredStock

    private fun renderCheckout() {
        if (!::binding.isInitialized) return

        val items = currentItems()
        val total = totalAmount()
        val qty = totalQty()
        val method = binding.spPayment.selectedItem?.toString().orEmpty().ifBlank { "Tunai" }
        val paid = InputRupiah.ambilNilai(binding.etCashPaid)
        val change = (paid - total).coerceAtLeast(0L)
        val emptyCart = items.isEmpty()
        val qris = pendingQris
        val qrisAktif = qris != null
        val qrisKedaluwarsa = qris?.isExpired() == true

        cartAdapter.submitList(items)

        binding.tvCheckoutSummary.text = if (emptyCart) {
            "Keranjang kosong"
        } else {
            "${Formatter.ribuan(qty.toLong())} item • ${Formatter.ribuan(items.size.toLong())} baris"
        }
        binding.tvTotalBelanja.text = Formatter.currency(total)
        binding.tvKembalian.text = if (method == "Tunai") Formatter.currency(change) else Formatter.currency(0)

        binding.tilCashPaid.isVisible = method == "Tunai"
        binding.tvPaidLabel.isVisible = method == "Tunai"
        binding.tvPaidAmount.isVisible = method == "Tunai"
        binding.tvPaidAmount.text = Formatter.currency(paid)

        if (method != "Tunai") {
            if (total > 0L) {
                val totalText = Formatter.ribuan(total)
                if (binding.etCashPaid.text?.toString() != totalText) {
                    binding.etCashPaid.setText(totalText)
                    binding.etCashPaid.setSelection(binding.etCashPaid.text?.length ?: 0)
                }
            } else if (binding.etCashPaid.text?.isNotEmpty() == true) {
                binding.etCashPaid.setText("")
            }
        }

        binding.spPayment.isEnabled = !qrisAktif && !sedangProses
        binding.rvCart.alpha = if (qrisAktif) 0.55f else 1f
        binding.cardXenditQris.isVisible = method == "QRIS" || qrisAktif
        binding.tvKasirHint.text = when {
            qrisAktif -> "Menunggu pembayaran QRIS. Keranjang dikunci sementara."
            method == "QRIS" -> "Tekan Buat QRIS untuk menampilkan kode pembayaran."
            else -> "Masukkan uang diterima, lalu simpan transaksi tunai."
        }

        renderPanelQris(qris, qrisKedaluwarsa, total)

        binding.btnSaveTransaction.text = when {
            sedangProses -> "Memproses..."
            method == "QRIS" && qris == null -> "Buat QRIS"
            method == "QRIS" && qris != null -> "Cek Status QRIS"
            else -> "Simpan Tunai"
        }
        binding.btnSaveTransaction.isEnabled = !emptyCart && !sedangProses
        binding.btnSaveTransaction.alpha = if (emptyCart || sedangProses) 0.5f else 1f
    }

    private fun renderPanelQris(qris: XenditQris?, qrisKedaluwarsa: Boolean, total: Long) {
        binding.tvXenditTotal.text = Formatter.currency(qris?.total ?: total)
        binding.btnXenditShowQris.isVisible = false

        if (qris == null) {
            binding.tvXenditStatus.text = "Pembayaran non-tunai"
            binding.tvXenditTimer.isVisible = false
            binding.tvXenditOrderId.isVisible = false
            binding.tvXenditOrderId.text = ""
            binding.ivXenditQr.isVisible = false
            binding.ivXenditQr.tag = null
            binding.rowXenditActions.isVisible = false
            binding.btnXenditCheckStatus.isEnabled = false
            binding.btnXenditCancel.isEnabled = false
            binding.btnXenditSimulate.isVisible = false
            binding.btnXenditSimulate.isEnabled = false
            return
        }

        binding.ivXenditQr.isVisible = true
        if (binding.ivXenditQr.tag != qris.externalId) {
            binding.ivXenditQr.setImageBitmap(PembuatQrBitmap.buat(qris.qrString, 900))
            binding.ivXenditQr.tag = qris.externalId
        }

        binding.tvXenditStatus.text = if (qrisKedaluwarsa) {
            "Kedaluwarsa"
        } else {
            "Menunggu pembayaran"
        }
        binding.tvXenditOrderId.isVisible = true
        binding.tvXenditOrderId.text = "Order: ${qris.externalId}"
        binding.tvXenditTimer.isVisible = true
        binding.tvXenditTimer.text = if (qrisKedaluwarsa) {
            "Kedaluwarsa"
        } else {
            formatDurasi(qris.remainingMs())
        }
        binding.rowXenditActions.isVisible = true
        binding.btnXenditCheckStatus.isEnabled = !sedangProses
        binding.btnXenditCancel.isEnabled = !sedangProses
        binding.btnXenditSimulate.isVisible = XENDIT_TEST_MODE
        binding.btnXenditSimulate.isEnabled = XENDIT_TEST_MODE && !sedangProses
    }


    private fun saveTransaction() {
        val items = currentItems()
        if (items.isEmpty()) {
            showMessage("Keranjang masih kosong")
            return
        }

        val method = binding.spPayment.selectedItem?.toString().orEmpty().ifBlank { "Tunai" }
        val total = totalAmount()
        val cash = InputRupiah.ambilNilai(binding.etCashPaid)

        validasiKeranjang(items)?.let { pesan ->
            showMessage(pesan)
            return
        }

        if (method == "Tunai") {
            if (cash < total) {
                showMessage("Uang dibayar masih kurang")
                return
            }
            simpanTransaksiFinal(
                metode = "Tunai",
                uangDiterima = cash,
                items = items,
                paymentGateway = "",
                paymentOrderId = "",
                paymentQrId = "",
                paymentStatus = "PAID",
                paymentSource = "",
                paymentReferenceId = "",
                paymentPaidAt = "",
                paymentAmount = total
            )
            return
        }

        if (method == "QRIS") {
            if (pendingQris == null) buatQrisXendit(items, total) else cekStatusQrisXendit()
        }
    }

    private fun buatQrisXendit(items: List<ItemKeranjang>, total: Long) {
        if (total < 1500L) {
            showMessage("Minimal pembayaran QRIS Xendit Rp1.500")
            return
        }

        validasiKeranjang(items)?.let { pesan ->
            showMessage(pesan)
            return
        }

        lifecycleScope.launch {
            setBusy(true)
            runCatching {
                val response = postJson(
                    path = "/api/buat-qris-xendit",
                    body = JSONObject().put("total", total)
                )
                val json = JSONObject(response)
                val now = System.currentTimeMillis()
                val qrisDraft = XenditQris(
                    saleId = "",
                    externalId = json.getString("externalId"),
                    qrId = json.optString("qrId"),
                    total = json.optLong("total", total),
                    status = json.optString("status", "ACTIVE"),
                    qrString = json.getString("qrString"),
                    createdAtMillis = now,
                    expiresAtMillis = now + QRIS_EXPIRE_MS
                )
                val saleId = RepositoriFirebaseUtama.buatPenjualanQrisPending(
                    userAuthId = currentUserId(),
                    cartItems = items,
                    products = products,
                    paymentGateway = "XENDIT",
                    paymentOrderId = qrisDraft.externalId,
                    paymentQrId = qrisDraft.qrId,
                    paymentQrString = qrisDraft.qrString,
                    paymentQrCreatedAtMillis = qrisDraft.createdAtMillis,
                    paymentQrExpiresAtMillis = qrisDraft.expiresAtMillis,
                    paymentStatus = qrisDraft.status.ifBlank { "ACTIVE" },
                    paymentAmount = qrisDraft.total
                )
                qrisDraft.copy(saleId = saleId)
            }.onSuccess { qris ->
                pendingQris = qris
                pendingItems = items.map { it.copy() }
                mulaiCountdownQris(qris)
                renderCheckout()
                showMessage("QRIS berhasil dibuat dan masuk riwayat sebagai Pending.")
            }.onFailure {
                showMessage(it.message ?: "Gagal membuat QRIS Xendit")
            }
            setBusy(false)
        }
    }

    private fun cekStatusQrisXendit() {
        val qris = pendingQris
        if (qris == null) {
            showMessage("QRIS belum dibuat")
            return
        }

        lifecycleScope.launch {
            setBusy(true)
            runCatching {
                val response = postJson(
                    path = "/api/cek-status-xendit",
                    body = JSONObject().put("externalId", qris.externalId)
                )
                val json = JSONObject(response)
                val payment = json.optJSONObject("payment")
                val details = payment?.optJSONObject("payment_details")
                XenditStatus(
                    paid = json.optBoolean("paid", false),
                    status = json.optString("status", "PENDING"),
                    paymentId = payment?.optString("id").orEmpty(),
                    source = details?.optString("source").orEmpty(),
                    receiptId = details?.optString("receipt_id").orEmpty(),
                    paidAt = payment?.optString("created").orEmpty(),
                    amount = payment?.optLong("amount", qris.total) ?: qris.total
                )
            }.onSuccess { status ->
                if (status.paid && status.status.equals("COMPLETED", ignoreCase = true)) {
                    selesaikanTransaksiQrisPending(status)
                } else {
                    val tambahan = if (qris.isExpired()) " Jika belum dibayar, buat QRIS baru." else ""
                    showMessage("Pembayaran belum masuk. Status: ${status.status.ifBlank { "PENDING" }}.$tambahan")
                }
            }.onFailure {
                showMessage(it.message ?: "Gagal cek status Xendit")
            }
            setBusy(false)
        }
    }


    private fun selesaikanTransaksiQrisPending(status: XenditStatus) {
        val qris = pendingQris
        if (qris == null) {
            showMessage("QRIS belum dibuat")
            return
        }

        lifecycleScope.launch {
            setBusy(true)
            runCatching {
                val saleId = RepositoriFirebaseUtama.selesaikanPenjualanQrisPending(
                    id = qris.saleId,
                    userAuthId = currentUserId(),
                    products = products,
                    paymentStatus = status.status.uppercase(Locale.US),
                    paymentSource = status.source,
                    paymentReferenceId = status.receiptId.ifBlank { status.paymentId },
                    paymentPaidAt = status.paidAt,
                    paymentAmount = status.amount
                )
                RepositoriFirebaseUtama.buildReceiptText(saleId)
            }.onSuccess { receipt ->
                hentikanCountdownQris()
                pendingQris = null
                pendingItems = emptyList()
                qrisDialog?.dismiss()
                SessionKeranjangRumahan.clear()
                renderCheckout()
                showReceiptDialogAndFinish(receipt)
            }.onFailure {
                showMessage(it.message ?: "Gagal menyelesaikan transaksi QRIS")
            }
            setBusy(false)
        }
    }
    private fun simulasiBayarXendit() {
        val qris = pendingQris
        if (qris == null) {
            showMessage("QRIS belum dibuat")
            return
        }

        lifecycleScope.launch {
            setBusy(true)
            runCatching {
                postJson(
                    path = "/api/simulasi-bayar-xendit",
                    body = JSONObject()
                        .put("externalId", qris.externalId)
                        .put("amount", qris.total)
                )
            }.onSuccess {
                showMessage("Simulasi pembayaran berhasil. Tekan Cek Status untuk menyelesaikan transaksi.")
            }.onFailure {
                showMessage(it.message ?: "Gagal simulasi pembayaran Xendit")
            }
            setBusy(false)
        }
    }

    private fun simpanTransaksiFinal(
        metode: String,
        uangDiterima: Long,
        items: List<ItemKeranjang>,
        paymentGateway: String,
        paymentOrderId: String,
        paymentQrId: String,
        paymentStatus: String,
        paymentSource: String,
        paymentReferenceId: String,
        paymentPaidAt: String,
        paymentAmount: Long
    ) {
        lifecycleScope.launch {
            setBusy(true)
            runCatching {
                val saleId = RepositoriFirebaseUtama.simpanPenjualanRumahan(
                    userAuthId = currentUserId(),
                    metodePembayaranUi = metode,
                    uangDiterima = uangDiterima,
                    cartItems = items,
                    products = products,
                    paymentGateway = paymentGateway,
                    paymentOrderId = paymentOrderId,
                    paymentQrId = paymentQrId,
                    paymentStatus = paymentStatus,
                    paymentSource = paymentSource,
                    paymentReferenceId = paymentReferenceId,
                    paymentPaidAt = paymentPaidAt,
                    paymentAmount = paymentAmount
                )
                RepositoriFirebaseUtama.buildReceiptText(saleId)
            }.onSuccess { receipt ->
                hentikanCountdownQris()
                pendingQris = null
                pendingItems = emptyList()
                qrisDialog?.dismiss()
                SessionKeranjangRumahan.clear()
                renderCheckout()
                showReceiptDialogAndFinish(receipt)
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan transaksi")
            }
            setBusy(false)
        }
    }

    private fun tampilkanDialogQris(qris: XenditQris) {
        qrisDialog?.dismiss()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 24, 32, 8)
        }

        val totalText = TextView(this).apply {
            text = Formatter.currency(qris.total)
            textSize = 24f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }
        val caption = TextView(this).apply {
            text = "Scan QRIS Xendit ini dari aplikasi e-wallet/bank pelanggan."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 16)
        }
        val qrImage = ImageView(this).apply {
            setImageBitmap(PembuatQrBitmap.buat(qris.qrString, 1000))
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.WHITE)
            setPadding(12, 12, 12, 12)
        }
        val orderText = TextView(this).apply {
            text = "Order: ${qris.externalId}\nSisa waktu: ${formatDurasi(qris.remainingMs())}"
            textSize = 12f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        container.addView(totalText)
        container.addView(caption)
        container.addView(qrImage)
        container.addView(orderText)

        val scrollView = ScrollView(this).apply { addView(container) }

        qrisDialog = MaterialAlertDialogBuilder(this)
            .setTitle("QRIS Xendit")
            .setView(scrollView)
            .setPositiveButton("Cek Status") { _, _ -> cekStatusQrisXendit() }
            .setNegativeButton("Tutup", null)
            .create()
            .also { it.show() }
    }

    private fun konfirmasiBatalkanQris() {
        if (pendingQris == null) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Batalkan QRIS?")
            .setMessage("Pembayaran belum disimpan. Pastikan pelanggan belum membayar sebelum membatalkan QRIS ini.")
            .setPositiveButton("Batalkan QRIS") { _, _ -> batalkanQrisPending() }
            .setNegativeButton("Kembali", null)
            .show()
    }

    private fun batalkanQrisPending() {
        val qris = pendingQris ?: return
        lifecycleScope.launch {
            setBusy(true)
            runCatching {
                RepositoriFirebaseUtama.batalkanPenjualan(
                    id = qris.saleId,
                    alasan = "Pembayaran QRIS dibatalkan kasir",
                    userAuthId = currentUserId()
                )
            }.onSuccess {
                pendingQris = null
                pendingItems = emptyList()
                hentikanCountdownQris()
                qrisDialog?.dismiss()
                binding.ivXenditQr.tag = null
                showMessage("QRIS dibatalkan dan riwayat ditandai Batal")
                renderCheckout()
            }.onFailure {
                showMessage(it.message ?: "Gagal membatalkan QRIS")
            }
            setBusy(false)
        }
    }

    private fun mulaiCountdownQris(qris: XenditQris) {
        hentikanCountdownQris()
        qrisCountdown = object : CountDownTimer(qris.remainingMs().coerceAtLeast(1_000L), 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                if (::binding.isInitialized) {
                    binding.tvXenditTimer.text = formatDurasi(millisUntilFinished)
                }
            }

            override fun onFinish() {
                renderCheckout()
            }
        }.start()
    }

    private fun hentikanCountdownQris() {
        qrisCountdown?.cancel()
        qrisCountdown = null
    }

    private fun validasiKeranjang(items: List<ItemKeranjang>): String? {
        items.forEach { item ->
            val produk = products.firstOrNull { it.id == item.productId }
                ?: return "Produk di keranjang tidak ditemukan"
            if (item.qty <= 0) return "Jumlah ${produk.name} tidak valid"
            if (item.price <= 0L) return "Harga ${produk.name} belum valid"
            val stokLayak = stokLayakJual(produk)
            if (stokLayak < item.qty) {
                return "Stok layak jual ${produk.name} tidak cukup. Tersedia $stokLayak, diminta ${item.qty}."
            }
        }
        return null
    }

    private fun tahanPerubahanKeranjang(): Boolean {
        return if (pendingQris != null) {
            showMessage("Selesaikan atau batalkan pembayaran QRIS dulu")
            true
        } else {
            false
        }
    }

    private fun setBusy(value: Boolean) {
        sedangProses = value
        renderCheckout()
    }

    private suspend fun postJson(path: String, body: JSONObject): String = withContext(Dispatchers.IO) {
        val url = URL(XENDIT_API_BASE.trimEnd('/') + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val responseText = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            if (connection.responseCode !in 200..299) {
                val message = runCatching {
                    val json = JSONObject(responseText)
                    json.optString("message")
                        .ifBlank { json.optJSONObject("xendit")?.optString("message").orEmpty() }
                        .ifBlank { responseText }
                }.getOrElse { responseText }
                throw IllegalStateException(message)
            }

            responseText
        } finally {
            connection.disconnect()
        }
    }

    private fun formatDurasi(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(safeMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(safeMs) % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun showReceiptDialogAndFinish(receipt: String) {
        showReceiptModal("Transaksi berhasil", receipt) { finish() }
    }
}

private data class XenditQris(
    val saleId: String,
    val externalId: String,
    val qrId: String,
    val total: Long,
    val status: String,
    val qrString: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long
) {
    fun remainingMs(): Long = expiresAtMillis - System.currentTimeMillis()
    fun isExpired(): Boolean = remainingMs() <= 0L
}

private data class XenditStatus(
    val paid: Boolean,
    val status: String,
    val paymentId: String,
    val source: String,
    val receiptId: String,
    val paidAt: String,
    val amount: Long
)

private class CheckoutSpinnerListener(
    private val onSelected: () -> Unit
) : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        onSelected()
    }

    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
}
