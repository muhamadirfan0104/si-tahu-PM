package muhamad.irfan.si_tahu.ui.penjualan

import android.os.Bundle
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.ItemKeranjang
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.FragmentCashierSaleBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.umum.AdapterKeranjang
import muhamad.irfan.si_tahu.ui.umum.AdapterProduk
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.Formatter

class AktivitasPenjualanRumahan : AktivitasDasar() {

    private lateinit var binding: FragmentCashierSaleBinding
    private lateinit var productAdapter: AdapterProduk
    private lateinit var cartAdapter: AdapterKeranjang

    private var products: List<Produk> = emptyList()
    private val cartItems = mutableListOf<ItemKeranjang>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        binding = FragmentCashierSaleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        productAdapter = AdapterProduk(
            onAdd = { addToCart(it) },
            getHarga = { defaultPrice(it) },
            getStatus = { productStatus(it) }
        )
        cartAdapter = AdapterKeranjang(
            onIncrease = { changeCart(it.productId, 1) },
            onDecrease = { changeCart(it.productId, -1) },
            onRemove = { item ->
                cartItems.removeAll { it.productId == item.productId }
                renderCart()
            },
            getProduk = { productId -> products.firstOrNull { it.id == productId } }
        )

        setupView()
        loadProducts()
    }

    override fun onResume() {
        super.onResume()
        renderCart()
    }

    private fun setupView() {
        binding.rvProducts.layoutManager = LinearLayoutManager(this)
        binding.rvProducts.adapter = productAdapter
        binding.rvCart.layoutManager = LinearLayoutManager(this)
        binding.rvCart.adapter = cartAdapter

        binding.spPayment.adapter = AdapterSpinner.stringAdapter(this, listOf("Tunai", "QRIS", "Transfer"))
        binding.spCategory.onItemSelectedListener = SimpleSpinnerListener { renderProducts() }
        binding.spPayment.onItemSelectedListener = SimpleSpinnerListener { renderCart() }
        binding.etSearch.addTextChangedListener { renderProducts() }
        binding.etCashPaid.addTextChangedListener { renderCart() }
        binding.btnClearCart.setOnClickListener {
            cartItems.clear()
            renderCart()
        }
        binding.btnCheckout.setOnClickListener { checkout() }
    }

    private fun loadProducts() {
        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatProdukKasir() }
                .onSuccess {
                    products = it
                    val categories = listOf("Semua") + products.map { produk -> produk.category }.distinct().sorted()
                    binding.spCategory.adapter = AdapterSpinner.stringAdapter(this@AktivitasPenjualanRumahan, categories)
                    renderProducts()
                    renderCart()
                }
                .onFailure {
                    products = emptyList()
                    renderProducts()
                    showMessage(it.message ?: "Gagal memuat produk")
                }
        }
    }

    private fun defaultPrice(product: Produk): Long {
        return product.channels.firstOrNull { it.defaultCashier && it.active }?.price
            ?: product.channels.firstOrNull { it.active }?.price
            ?: 0L
    }

    private fun productStatus(product: Produk): String = when {
        product.stock <= 0 -> "Habis"
        product.stock <= product.minStock -> "Menipis"
        else -> "Aman"
    }

    private fun renderProducts() {
        val keyword = binding.etSearch.text?.toString().orEmpty().trim().lowercase()
        val category = binding.spCategory.selectedItem?.toString().orEmpty().ifBlank { "Semua" }
        val filtered = products.filter {
            (keyword.isBlank() || it.name.lowercase().contains(keyword) || it.code.lowercase().contains(keyword)) &&
                (category == "Semua" || it.category == category)
        }
        productAdapter.submitList(filtered)
    }

    private fun addToCart(product: Produk) {
        val current = cartItems.firstOrNull { it.productId == product.id }
        val nextQty = (current?.qty ?: 0) + 1
        if (nextQty > product.stock) {
            showMessage("Stok ${product.name} tidak mencukupi")
            return
        }

        if (current == null) {
            cartItems += ItemKeranjang(product.id, 1, defaultPrice(product))
        } else {
            current.qty = nextQty
        }
        renderCart()
    }

    private fun changeCart(productId: String, delta: Int) {
        val current = cartItems.firstOrNull { it.productId == productId } ?: return
        val product = products.firstOrNull { it.id == productId } ?: return
        val nextQty = current.qty + delta
        if (nextQty <= 0) {
            cartItems.removeAll { it.productId == productId }
        } else {
            if (nextQty > product.stock) {
                showMessage("Stok ${product.name} tidak mencukupi")
                return
            }
            current.qty = nextQty
        }
        renderCart()
    }

    private fun cartTotal(): Long = cartItems.sumOf { it.qty.toLong() * it.price }
    private fun cartCount(): Int = cartItems.sumOf { it.qty }

    private fun renderCart() {
        val method = binding.spPayment.selectedItem?.toString().orEmpty().ifBlank { "Tunai" }
        cartAdapter.submitList(cartItems.toList())
        val total = cartTotal()
        binding.tvCartSummary.text = "${cartCount()} item • Total ${Formatter.currency(total)}"
        binding.tvCartHeader.text = "Keranjang Penjualan Rumahan"
        binding.etCashPaid.isEnabled = method == "Tunai"
        if (method != "Tunai" && total > 0L) {
            val totalText = total.toString()
            if (binding.etCashPaid.text?.toString() != totalText) {
                binding.etCashPaid.setText(totalText)
            }
        }
    }

    private fun checkout() {
        val method = binding.spPayment.selectedItem?.toString().orEmpty().ifBlank { "Tunai" }
        val cash = binding.etCashPaid.text?.toString()?.toLongOrNull() ?: 0L

        lifecycleScope.launch {
            binding.btnCheckout.isEnabled = false
            runCatching {
                val saleId = RepositoriFirebaseUtama.simpanPenjualanRumahan(
                    userAuthId = currentUserId(),
                    metodePembayaranUi = method,
                    uangDiterima = cash,
                    cartItems = cartItems.toList(),
                    products = products
                )
                RepositoriFirebaseUtama.buildReceiptText(saleId)
            }.onSuccess { receipt ->
                showReceiptModal("Transaksi berhasil", receipt)
                cartItems.clear()
                binding.etCashPaid.setText("")
                loadProducts()
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan transaksi")
            }
            binding.btnCheckout.isEnabled = true
        }
    }
}

private class SimpleSpinnerListener(
    private val onSelected: () -> Unit
) : android.widget.AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) = onSelected()
    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
}
