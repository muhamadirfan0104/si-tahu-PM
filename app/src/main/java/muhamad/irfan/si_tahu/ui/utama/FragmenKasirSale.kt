package muhamad.irfan.si_tahu.ui.utama

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.ItemKeranjang
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.data.SessionKeranjangRumahan
import muhamad.irfan.si_tahu.databinding.FragmentCashierSaleBinding
import muhamad.irfan.si_tahu.ui.dasar.FragmenDasar
import muhamad.irfan.si_tahu.ui.umum.AdapterKeranjang
import muhamad.irfan.si_tahu.ui.umum.AdapterProduk
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.Formatter

class FragmenKasirSale : FragmenDasar(R.layout.fragment_cashier_sale) {

    private var _binding: FragmentCashierSaleBinding? = null
    private val binding get() = _binding!!

    private lateinit var productAdapter: AdapterProduk
    private lateinit var cartAdapter: AdapterKeranjang

    private var products: List<Produk> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentCashierSaleBinding.bind(view)

        binding.toolbar.visibility = View.GONE

        setupAdapters()
        setupView()
        loadProducts()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            renderProducts()
            renderCheckout()
        }
    }

    private fun setupAdapters() {
        productAdapter = AdapterProduk(
            onAdd = { addToCart(it) },
            getHarga = { defaultPrice(it) },
            getStatus = { productStatus(it) }
        )

        cartAdapter = AdapterKeranjang(
            onIncrease = { item ->
                val product = products.firstOrNull { it.id == item.productId } ?: return@AdapterKeranjang
                val success = SessionKeranjangRumahan.changeQty(item.productId, 1, product.stock)
                if (!success) {
                    showMessage(binding.root, "Stok ${product.name} tidak mencukupi")
                    return@AdapterKeranjang
                }
                renderCheckout()
            },
            onDecrease = { item ->
                val product = products.firstOrNull { it.id == item.productId } ?: return@AdapterKeranjang
                SessionKeranjangRumahan.changeQty(item.productId, -1, product.stock)
                renderCheckout()
            },
            onRemove = { item ->
                SessionKeranjangRumahan.remove(item.productId)
                renderCheckout()
            },
            getProduk = { productId ->
                products.firstOrNull { it.id == productId }
            }
        )
    }

    private fun setupView() {
        binding.rvProducts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProducts.adapter = productAdapter

        binding.rvCart.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCart.adapter = cartAdapter

        binding.spCategory.adapter =
            AdapterSpinner.stringAdapter(requireContext(), listOf("Semua"))

        binding.spPayment.adapter =
            AdapterSpinner.stringAdapter(requireContext(), listOf("Tunai", "QRIS", "Transfer"))

        binding.spCategory.onItemSelectedListener = FragmentSpinnerListener {
            renderProducts()
        }

        binding.spPayment.onItemSelectedListener = FragmentSpinnerListener {
            renderCheckout()
        }

        binding.etSearch.addTextChangedListener {
            renderProducts()
        }

        binding.etCashPaid.addTextChangedListener {
            renderCheckout()
        }

        binding.btnClearCart.setOnClickListener {
            SessionKeranjangRumahan.clear()
            renderCheckout()
        }

        binding.btnCheckout.setOnClickListener {
            saveTransaction()
        }
    }

    private fun loadProducts() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatProdukKasir() }
                .onSuccess { result ->
                    products = result
                        .filter { hasValidCashierPrice(it) }
                        .sortedBy { it.name.lowercase() }

                    val categories = listOf("Semua") + products.map { it.category }
                        .distinct()
                        .sorted()

                    binding.spCategory.adapter =
                        AdapterSpinner.stringAdapter(requireContext(), categories)

                    renderProducts()
                    renderCheckout()
                }
                .onFailure {
                    products = emptyList()
                    renderProducts()
                    renderCheckout()
                    showMessage(binding.root, it.message ?: "Gagal memuat produk")
                }
        }
    }

    private fun defaultPrice(product: Produk): Long {
        return product.channels.firstOrNull { it.defaultCashier && it.active }?.price
            ?: product.channels.firstOrNull { it.active }?.price
            ?: 0L
    }

    private fun hasValidCashierPrice(product: Produk): Boolean {
        return defaultPrice(product) > 0L
    }

    private fun productStatus(product: Produk): String = when {
        product.stock <= 0 -> "Habis"
        product.stock <= product.minStock -> "Menipis"
        else -> "Aman"
    }

    private fun renderProducts() {
        val keyword = binding.etSearch.text?.toString().orEmpty().trim().lowercase()
        val category = binding.spCategory.selectedItem?.toString().orEmpty().ifBlank { "Semua" }

        val filtered = products
            .filter { product ->
                val cocokKeyword =
                    keyword.isBlank() ||
                            product.name.lowercase().contains(keyword) ||
                            product.code.lowercase().contains(keyword)

                val cocokKategori =
                    category == "Semua" || product.category == category

                cocokKeyword && cocokKategori
            }
            .sortedBy { it.name.lowercase() }

        productAdapter.submitList(filtered)

        binding.tvProductSummary.text = when {
            products.isEmpty() ->
                "Belum ada produk kasir dengan harga aktif di atas 0"
            filtered.isEmpty() ->
                "Tidak ada produk yang cocok"
            else ->
                "${filtered.size} produk tampil"
        }
    }

    private fun addToCart(product: Produk) {
        if (defaultPrice(product) <= 0L) {
            showMessage(binding.root, "Produk ${product.name} belum punya harga kasir yang valid")
            return
        }

        if (product.stock <= 0) {
            showMessage(binding.root, "Produk ${product.name} sedang habis")
            return
        }

        val success = SessionKeranjangRumahan.addOrIncrease(
            productId = product.id,
            price = defaultPrice(product),
            maxStock = product.stock
        )

        if (!success) {
            showMessage(binding.root, "Stok ${product.name} tidak mencukupi")
            return
        }

        renderCheckout()
        showMessage(binding.root, "${product.name} masuk ke keranjang")
    }

    private fun currentItems(): List<ItemKeranjang> = SessionKeranjangRumahan.getItems()

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

        binding.tvCartSummary.text = if (emptyCart) {
            "Keranjang kosong"
        } else {
            "$qty item • ${items.size} baris transaksi"
        }

        binding.tvCartHeader.text = if (emptyCart) {
            "Keranjang transaksi"
        } else {
            "Keranjang transaksi • $qty item"
        }

        binding.tvTotalBelanja.text = Formatter.currency(total)
        binding.tvMetodeBayar.text = method
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

        binding.tvCartEmpty.isVisible = emptyCart
        binding.rvCart.isVisible = !emptyCart

        binding.btnCheckout.isEnabled = !emptyCart
        binding.btnClearCart.isEnabled = !emptyCart
    }

    private fun saveTransaction() {
        val items = currentItems()
        if (items.isEmpty()) {
            showMessage(binding.root, "Keranjang masih kosong")
            return
        }

        val method = binding.spPayment.selectedItem?.toString().orEmpty().ifBlank { "Tunai" }
        val total = totalAmount()
        val cash = binding.etCashPaid.text?.toString()?.toLongOrNull() ?: 0L

        if (method == "Tunai" && cash < total) {
            showMessage(binding.root, "Uang dibayar masih kurang")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.btnCheckout.isEnabled = false

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
                renderProducts()
                renderCheckout()
                showReceiptModal("Transaksi berhasil", receipt)

                (activity as? AktivitasUtamaKasir)?.openTab(R.id.nav_cashier_history)
            }.onFailure {
                showMessage(binding.root, it.message ?: "Gagal menyimpan transaksi")
                binding.btnCheckout.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

private class FragmentSpinnerListener(
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