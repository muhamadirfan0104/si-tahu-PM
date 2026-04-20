package muhamad.irfan.si_tahu.ui.penjualan

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.data.SessionKeranjangRumahan
import muhamad.irfan.si_tahu.databinding.ActivityCashierCheckoutBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.umum.AdapterKeranjang
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.Formatter

class AktivitasCheckoutRumahan : AktivitasDasar() {

    private lateinit var binding: ActivityCashierCheckoutBinding
    private lateinit var cartAdapter: AdapterKeranjang

    private var products: List<Produk> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        binding = ActivityCashierCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Checkout Rumahan", "Review keranjang dan pembayaran")

        cartAdapter = AdapterKeranjang(
            onIncrease = { item ->
                val product = products.firstOrNull { it.id == item.productId } ?: return@AdapterKeranjang
                val success = SessionKeranjangRumahan.changeQty(item.productId, 1, product.stock)
                if (!success) {
                    showMessage("Stok ${product.name} tidak mencukupi")
                    return@AdapterKeranjang
                }
                renderCheckout()
            },
            onDecrease = { item ->
                val product = products.firstOrNull { it.id == item.productId } ?: return@AdapterKeranjang
                SessionKeranjangRumahan.changeQty(item.productId, -1, product.stock)
                renderCheckout()
                if (SessionKeranjangRumahan.isEmpty()) finish()
            },
            onRemove = { item ->
                SessionKeranjangRumahan.remove(item.productId)
                renderCheckout()
                if (SessionKeranjangRumahan.isEmpty()) finish()
            },
            getProduk = { productId ->
                products.firstOrNull { it.id == productId }
            }
        )

        setupView()
        loadProducts()
    }

    override fun onResume() {
        super.onResume()
        if (SessionKeranjangRumahan.isEmpty()) {
            finish()
        } else {
            renderCheckout()
        }
    }

    private fun setupView() {
        binding.rvCart.layoutManager = LinearLayoutManager(this)
        binding.rvCart.adapter = cartAdapter

        binding.spPayment.adapter =
            AdapterSpinner.stringAdapter(this, listOf("Tunai", "QRIS", "Transfer"))

        binding.spPayment.onItemSelectedListener = CheckoutSpinnerListener {
            renderCheckout()
        }

        binding.etCashPaid.addTextChangedListener {
            renderCheckout()
        }

        binding.btnSaveTransaction.setOnClickListener {
            saveTransaction()
        }
    }

    private fun loadProducts() {
        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatProdukKasir() }
                .onSuccess {
                    products = it
                    renderCheckout()
                }
                .onFailure {
                    products = emptyList()
                    showMessage(it.message ?: "Gagal memuat data produk")
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
        val method = binding.spPayment.selectedItem?.toString().orEmpty().ifBlank { "Tunai" }
        val paid = binding.etCashPaid.text?.toString()?.toLongOrNull() ?: 0L
        val change = (paid - total).coerceAtLeast(0L)
        val emptyCart = items.isEmpty()

        cartAdapter.submitList(items)

        binding.tvCheckoutSummary.text = if (emptyCart) {
            "Keranjang kosong"
        } else {
            "$qty item • ${items.size} baris"
        }

        binding.tvTotalBelanja.text = Formatter.currency(total)
        binding.tvKembalian.text =
            if (method == "Tunai") Formatter.currency(change) else Formatter.currency(0)

        binding.tilCashPaid.isVisible = method == "Tunai"
        binding.tvPaidLabel.isVisible = method == "Tunai"
        binding.tvPaidAmount.isVisible = method == "Tunai"
        binding.tvPaidAmount.text = Formatter.currency(paid)

        if (method != "Tunai") {
            if (total > 0L) {
                val totalText = total.toString()
                if (binding.etCashPaid.text?.toString() != totalText) {
                    binding.etCashPaid.setText(totalText)
                }
            } else if (binding.etCashPaid.text?.isNotEmpty() == true) {
                binding.etCashPaid.setText("")
            }
        }

        binding.btnSaveTransaction.isEnabled = !emptyCart
    }

    private fun saveTransaction() {
        val items = currentItems()
        if (items.isEmpty()) {
            showMessage("Keranjang masih kosong")
            return
        }

        val method = binding.spPayment.selectedItem?.toString().orEmpty().ifBlank { "Tunai" }
        val total = totalAmount()
        val cash = binding.etCashPaid.text?.toString()?.toLongOrNull() ?: 0L

        if (method == "Tunai" && cash < total) {
            showMessage("Uang dibayar masih kurang")
            return
        }

        lifecycleScope.launch {
            binding.btnSaveTransaction.isEnabled = false

            runCatching {
                val saleId = RepositoriFirebaseUtama.simpanPenjualanRumahan(
                    userAuthId = currentUserId(),
                    metodePembayaranUi = method,
                    uangDiterima = cash,
                    cartItems = items,
                    products = products
                )
                RepositoriFirebaseUtama.buildReceiptText(saleId)
            }.onSuccess { receipt ->
                SessionKeranjangRumahan.clear()
                showReceiptModal("Transaksi berhasil", receipt)
                finish()
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan transaksi")
                binding.btnSaveTransaction.isEnabled = true
            }
        }
    }
}

private class CheckoutSpinnerListener(
    private val onSelected: () -> Unit
) : android.widget.AdapterView.OnItemSelectedListener {
    override fun onItemSelected(
        parent: android.widget.AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long
    ) = onSelected()

    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
}