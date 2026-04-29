package muhamad.irfan.si_tahu.ui.penjualan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AktivitasCheckoutRumahan : AktivitasDasar() {

    private lateinit var binding: ActivityCashierCheckoutBinding
    private lateinit var cartAdapter: AdapterKeranjang

    private var products: List<Produk> = emptyList()
    private var paymentPending: PaymentLinkDraft? = null
    private var paymentPendingItems: List<ItemKeranjang> = emptyList()
    private var processingPayment = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        binding = ActivityCashierCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Checkout Rumahan", "Review keranjang dan pembayaran")

        setupAdapter()
        setupView()
        loadProducts()
    }

    override fun onResume() {
        super.onResume()
        if (this::binding.isInitialized) {
            renderCheckout()
        }
    }

    private fun setupAdapter() {
        cartAdapter = AdapterKeranjang(
            onIncrease = { item ->
                if (!bolehUbahKeranjang()) return@AdapterKeranjang
                val product = products.firstOrNull { it.id == item.productId } ?: return@AdapterKeranjang
                val success = SessionKeranjangRumahan.changeQty(item.productId, 1, product.stock)
                if (!success) {
                    showMessage("Stok ${product.name} tidak mencukupi")
                    return@AdapterKeranjang
                }
                renderCheckout()
            },
            onDecrease = { item ->
                if (!bolehUbahKeranjang()) return@AdapterKeranjang
                val product = products.firstOrNull { it.id == item.productId } ?: return@AdapterKeranjang
                SessionKeranjangRumahan.changeQty(item.productId, -1, product.stock)
                renderCheckout()
            },
            onRemove = { item ->
                if (!bolehUbahKeranjang()) return@AdapterKeranjang
                SessionKeranjangRumahan.remove(item.productId)
                renderCheckout()
            },
            getProduk = { productId ->
                products.firstOrNull { it.id == productId }
            }
        )
    }

    private fun setupView() {
        binding.rvCart.layoutManager = LinearLayoutManager(this)
        binding.rvCart.adapter = cartAdapter

        binding.spPayment.adapter =
            AdapterSpinner.stringAdapter(this, listOf("Tunai", "QRIS", "Transfer"))

        binding.spPayment.onItemSelectedListener = CheckoutSpinnerListener {
            renderCheckout()
        }

        InputRupiah.pasang(binding.etCashPaid)

        binding.etCashPaid.addTextChangedListener {
            renderCheckout()
        }

        binding.btnSaveTransaction.setOnClickListener {
            saveTransaction()
        }

        binding.btnOpenPaymentLink.setOnClickListener {
            val payment = paymentPending
            if (payment == null) {
                showMessage("Belum ada link pembayaran")
            } else {
                bukaPaymentLink(payment.paymentUrl)
            }
        }

        binding.btnCheckPaymentStatus.setOnClickListener {
            val payment = paymentPending
            if (payment == null) {
                showMessage("Belum ada pembayaran yang perlu dicek")
            } else {
                cekStatusDanSimpanPaymentLink(payment)
            }
        }

        binding.btnCancelPaymentLink.setOnClickListener {
            konfirmasiBatalkanPaymentLink()
        }
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

    private fun currentItems() = SessionKeranjangRumahan.getItems()

    private fun totalAmount(): Long = SessionKeranjangRumahan.totalAmount()

    private fun totalQty(): Int = SessionKeranjangRumahan.totalQty()

    private fun renderCheckout() {
        val items = currentItems()
        val total = totalAmount()
        val qty = totalQty()
        val method = selectedPaymentMethod()
        val paid = InputRupiah.ambilNilai(binding.etCashPaid)
        val change = (paid - total).coerceAtLeast(0L)
        val emptyCart = items.isEmpty()
        val tunai = method == "Tunai"
        val qris = method == "QRIS"

        cartAdapter.submitList(items)

        binding.tvCheckoutSummary.text = if (emptyCart) {
            "Keranjang kosong"
        } else {
            "${Formatter.ribuan(qty.toLong())} item • ${Formatter.ribuan(items.size.toLong())} baris"
        }

        binding.tvTotalBelanja.text = Formatter.currency(total)
        binding.tvKembalian.text = if (tunai) Formatter.currency(change) else Formatter.currency(0)

        binding.tilCashPaid.isVisible = tunai
        binding.tvPaidLabel.isVisible = tunai
        binding.tvPaidAmount.isVisible = tunai
        binding.tvPaidAmount.text = Formatter.currency(paid)

        if (!tunai) {
            isiUangDiterimaDenganTotal(total)
        }

        binding.tvPaymentHint.text = when (method) {
            "Tunai" -> "Input uang diterima, lalu sistem menghitung kembalian otomatis."
            "QRIS" -> "Midtrans Payment Link akan dibuat sesuai total belanja. Transaksi baru disimpan setelah status pembayaran berhasil."
            "Transfer" -> "Transfer dicatat manual. Pastikan kasir sudah menerima bukti transfer sebelum menyimpan transaksi."
            else -> "Pilih metode pembayaran untuk menyelesaikan transaksi."
        }

        val adaPendingPayment = paymentPending != null
        binding.btnSaveTransaction.text = when {
            emptyCart -> "Keranjang Kosong"
            adaPendingPayment -> "Selesaikan Pembayaran"
            qris -> "Buat Link Pembayaran"
            else -> "Simpan Transaksi"
        }
        binding.btnSaveTransaction.isEnabled = !emptyCart && !adaPendingPayment && !processingPayment
        binding.btnSaveTransaction.alpha = if (binding.btnSaveTransaction.isEnabled) 1f else 0.5f

        renderPaymentPanel()
    }

    private fun renderPaymentPanel() {
        val payment = paymentPending
        val visible = payment != null
        binding.cardPaymentProgress.isVisible = visible
        if (payment == null) return

        binding.tvPaymentStatus.text = payment.statusLabel()
        binding.tvPaymentOrder.text = "Order ID: ${payment.orderId}"
        binding.tvPaymentAmount.text = "Total: ${Formatter.currency(payment.total)}"
        binding.tvPaymentInstruction.text = when (payment.statusLower()) {
            "settlement", "capture" -> "Pembayaran berhasil. Menyimpan transaksi..."
            "expire", "cancel", "deny", "failure" -> "Pembayaran tidak berhasil. Batalkan draft ini, lalu buat transaksi baru bila diperlukan."
            else -> "Link sudah dibuat. Buka link di perangkat pelanggan/kasir, selesaikan pembayaran, lalu tekan Cek Status."
        }

        binding.btnOpenPaymentLink.isEnabled = !processingPayment
        binding.btnCheckPaymentStatus.isEnabled = !processingPayment
        binding.btnCancelPaymentLink.isEnabled = !processingPayment
    }

    private fun saveTransaction() {
        val items = currentItems()
        if (items.isEmpty()) {
            showMessage("Keranjang masih kosong")
            return
        }

        if (paymentPending != null) {
            showMessage("Selesaikan atau batalkan pembayaran Midtrans dulu")
            return
        }

        val method = selectedPaymentMethod()
        val total = totalAmount()
        val cash = InputRupiah.ambilNilai(binding.etCashPaid)

        if (method == "Tunai" && cash < total) {
            showMessage("Uang dibayar masih kurang")
            return
        }

        if (method == "QRIS") {
            mulaiPaymentLinkMidtrans(items, total)
            return
        }

        lifecycleScope.launch {
            setProcessing(true)

            runCatching {
                val saleId = RepositoriFirebaseUtama.simpanPenjualanRumahan(
                    userAuthId = currentUserId(),
                    metodePembayaranUi = method,
                    uangDiterima = cash,
                    cartItems = items,
                    products = products,
                    statusPembayaran = "PAID"
                )
                RepositoriFirebaseUtama.buildReceiptText(saleId)
            }.onSuccess { receipt ->
                SessionKeranjangRumahan.clear()
                renderCheckout()
                showReceiptDialogAndFinish(receipt)
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan transaksi")
                setProcessing(false)
            }
        }
    }

    private fun mulaiPaymentLinkMidtrans(items: List<ItemKeranjang>, total: Long) {
        lifecycleScope.launch {
            setProcessing(true)

            runCatching {
                buatPaymentLinkMidtrans(total)
            }.onSuccess { hasil ->
                paymentPending = hasil
                paymentPendingItems = items.map { it.copy() }
                setProcessing(false)
                bukaPaymentLink(hasil.paymentUrl)
                showMessage("Link pembayaran Midtrans berhasil dibuat")
            }.onFailure {
                showMessage(it.message ?: "Gagal membuat Payment Link Midtrans")
                setProcessing(false)
            }
        }
    }

    private fun cekStatusDanSimpanPaymentLink(payment: PaymentLinkDraft) {
        lifecycleScope.launch {
            setProcessing(true)

            runCatching {
                cekStatusPaymentLink(payment.orderId)
            }.onSuccess { status ->
                val updatedPayment = payment.copy(
                    statusPembayaran = status.transactionStatus.ifBlank { "pending" },
                    fraudStatus = status.fraudStatus
                )
                paymentPending = updatedPayment

                when {
                    status.berhasil -> {
                        simpanTransaksiSetelahPaymentLinkBerhasil(updatedPayment, status)
                    }
                    status.gagal -> {
                        showMessage("Pembayaran ${status.label()}. Batalkan draft bila ingin membuat link baru.")
                        setProcessing(false)
                    }
                    else -> {
                        showMessage("Pembayaran belum selesai. Status: ${status.label()}")
                        setProcessing(false)
                    }
                }
            }.onFailure {
                showMessage(it.message ?: "Gagal cek status pembayaran")
                setProcessing(false)
            }
        }
    }

    private suspend fun simpanTransaksiSetelahPaymentLinkBerhasil(
        payment: PaymentLinkDraft,
        status: StatusPaymentLink
    ) {
        val snapshotItems = paymentPendingItems.map { it.copy() }
        if (snapshotItems.isEmpty()) {
            showMessage("Item pembayaran tidak ditemukan. Cek riwayat Midtrans sebelum membuat transaksi baru.")
            setProcessing(false)
            return
        }

        runCatching {
            val freshProducts = RepositoriFirebaseUtama.muatProdukKasir()
            val saleId = RepositoriFirebaseUtama.simpanPenjualanRumahan(
                userAuthId = currentUserId(),
                metodePembayaranUi = "QRIS",
                uangDiterima = payment.total,
                cartItems = snapshotItems,
                products = freshProducts,
                paymentGateway = "MIDTRANS_PAYMENT_LINK",
                paymentOrderId = payment.orderId,
                paymentUrl = payment.paymentUrl,
                statusPembayaran = status.transactionStatus.uppercase().ifBlank { "SETTLEMENT" }
            )
            products = freshProducts
            RepositoriFirebaseUtama.buildReceiptText(saleId)
        }.onSuccess { receipt ->
            paymentPending = null
            paymentPendingItems = emptyList()
            processingPayment = false
            SessionKeranjangRumahan.clear()
            renderCheckout()
            showReceiptDialogAndFinish(receipt)
        }.onFailure {
            showMessage(it.message ?: "Pembayaran berhasil, tapi transaksi gagal disimpan")
            setProcessing(false)
        }
    }

    private fun konfirmasiBatalkanPaymentLink() {
        val payment = paymentPending
        if (payment == null) {
            showMessage("Tidak ada pembayaran yang sedang diproses")
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Batalkan pembayaran ini?")
            .setMessage(
                "Draft pembayaran ${payment.orderId} akan dihapus dari layar kasir. " +
                    "Pastikan pelanggan belum membayar link ini sebelum membatalkan."
            )
            .setNegativeButton("Kembali", null)
            .setPositiveButton("Batalkan") { _, _ ->
                paymentPending = null
                paymentPendingItems = emptyList()
                showMessage("Draft pembayaran dibatalkan")
                renderCheckout()
            }
            .show()
    }

    private fun bukaPaymentLink(paymentUrl: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl)))
        }.onFailure {
            showMessage("Tidak bisa membuka link pembayaran")
        }
    }

    private suspend fun buatPaymentLinkMidtrans(total: Long): PaymentLinkDraft {
        val response = postJson(
            endpoint = "$MIDTRANS_API_BASE/api/buat-payment-link",
            payload = JSONObject().put("total", total)
        )

        val orderId = response.optString("orderId")
        val paymentUrl = response.optString("paymentUrl")

        if (orderId.isBlank()) throw IllegalStateException("Order ID Midtrans kosong")
        if (paymentUrl.isBlank()) throw IllegalStateException("Payment URL Midtrans kosong")

        return PaymentLinkDraft(
            orderId = orderId,
            total = response.optLong("total", total),
            paymentUrl = paymentUrl,
            statusPembayaran = "pending",
            fraudStatus = ""
        )
    }

    private suspend fun cekStatusPaymentLink(orderId: String): StatusPaymentLink {
        val response = postJson(
            endpoint = "$MIDTRANS_API_BASE/api/cek-status",
            payload = JSONObject().put("orderId", orderId)
        )

        return StatusPaymentLink(
            transactionStatus = response.optString("transactionStatus").ifBlank { "pending" },
            fraudStatus = response.optString("fraudStatus")
        )
    }

    private suspend fun postJson(endpoint: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        try {
            connection.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = parseJsonResponse(text)

            if (code !in 200..299) {
                val pesan = json.optString("message")
                    .ifBlank { json.optString("status_message") }
                    .ifBlank { text.ifBlank { "HTTP $code" } }
                throw IllegalStateException(pesan)
            }

            json
        } finally {
            connection.disconnect()
        }
    }

    private fun parseJsonResponse(text: String): JSONObject {
        if (text.isBlank()) return JSONObject()
        return runCatching { JSONObject(text) }.getOrElse {
            JSONObject().put("message", text.take(180))
        }
    }

    private fun selectedPaymentMethod(): String =
        binding.spPayment.selectedItem?.toString().orEmpty().ifBlank { "Tunai" }

    private fun isiUangDiterimaDenganTotal(total: Long) {
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

    private fun bolehUbahKeranjang(): Boolean {
        if (paymentPending == null) return true
        showMessage("Selesaikan atau batalkan pembayaran Midtrans dulu")
        return false
    }

    private fun setProcessing(processing: Boolean) {
        processingPayment = processing
        renderCheckout()
    }

    private fun showReceiptDialogAndFinish(receipt: String) {
        showReceiptModal("Transaksi berhasil", receipt) {
            finish()
        }
    }

    private data class PaymentLinkDraft(
        val orderId: String,
        val total: Long,
        val paymentUrl: String,
        val statusPembayaran: String,
        val fraudStatus: String
    ) {
        fun statusLower(): String = statusPembayaran.lowercase()

        fun statusLabel(): String = when (statusLower()) {
            "settlement" -> "Pembayaran berhasil"
            "capture" -> "Pembayaran berhasil"
            "pending" -> "Menunggu pembayaran"
            "expire" -> "Pembayaran kedaluwarsa"
            "cancel" -> "Pembayaran dibatalkan"
            "deny" -> "Pembayaran ditolak"
            "failure" -> "Pembayaran gagal"
            else -> "Status: ${statusPembayaran.ifBlank { "pending" }}"
        }
    }

    private data class StatusPaymentLink(
        val transactionStatus: String,
        val fraudStatus: String
    ) {
        val berhasil: Boolean
            get() {
                val status = transactionStatus.lowercase()
                val fraud = fraudStatus.lowercase()
                return status == "settlement" || (status == "capture" && (fraud.isBlank() || fraud == "accept"))
            }

        val gagal: Boolean
            get() = transactionStatus.lowercase() in setOf("expire", "cancel", "deny", "failure")

        fun label(): String = when (transactionStatus.lowercase()) {
            "settlement" -> "berhasil"
            "capture" -> "berhasil"
            "pending" -> "pending"
            "expire" -> "kedaluwarsa"
            "cancel" -> "dibatalkan"
            "deny" -> "ditolak"
            "failure" -> "gagal"
            else -> transactionStatus.ifBlank { "pending" }
        }
    }

    companion object {
        private const val MIDTRANS_API_BASE = "https://midtrans-sitahu-api.vercel.app"
    }
}

private class CheckoutSpinnerListener(
    private val onSelected: () -> Unit
) : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(
        parent: AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long
    ) {
        onSelected()
    }

    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
}
