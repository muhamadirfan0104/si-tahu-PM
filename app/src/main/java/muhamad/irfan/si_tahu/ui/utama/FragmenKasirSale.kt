package muhamad.irfan.si_tahu.ui.utama

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.data.SessionKeranjangRumahan
import muhamad.irfan.si_tahu.databinding.FragmentCashierSaleBinding
import muhamad.irfan.si_tahu.ui.dasar.FragmenDasar
import muhamad.irfan.si_tahu.ui.penjualan.AktivitasCheckoutRumahan
import muhamad.irfan.si_tahu.ui.umum.AdapterProduk
import muhamad.irfan.si_tahu.util.AdapterSpinner

class FragmenKasirSale : FragmenDasar(R.layout.fragment_cashier_sale) {

    private var _binding: FragmentCashierSaleBinding? = null
    private val binding get() = _binding!!

    private lateinit var productAdapter: AdapterProduk
    private var products: List<Produk> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentCashierSaleBinding.bind(view)

        binding.toolbar.visibility = View.GONE

        setupAdapter()
        setupView()
        loadProducts()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            renderProducts()
            renderBottomCart()
        }
    }

    private fun setupAdapter() {
        productAdapter = AdapterProduk(
            onAdd = { addToCart(it) },
            getHarga = { defaultPrice(it) },
            getStatus = { productStatus(it) }
        )
    }

    private fun setupView() {
        binding.rvProducts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProducts.adapter = productAdapter

        binding.spCategory.adapter =
            AdapterSpinner.stringAdapter(requireContext(), listOf("Semua"))

        binding.spStockMode.adapter =
            AdapterSpinner.stringAdapter(
                requireContext(),
                listOf(MODE_READY, MODE_ALL, MODE_LOW, MODE_EMPTY)
            )

        binding.spCategory.onItemSelectedListener = FragmentSpinnerListener {
            renderProducts()
        }

        binding.spStockMode.onItemSelectedListener = FragmentSpinnerListener {
            renderProducts()
        }

        binding.etSearch.addTextChangedListener {
            renderProducts()
        }

        binding.btnMinusQty.setOnClickListener {
            changeCartQty(-1)
        }

        binding.btnPlusQty.setOnClickListener {
            changeCartQty(1)
        }

        binding.btnCheckout.setOnClickListener {
            if (SessionKeranjangRumahan.getItems().isEmpty()) return@setOnClickListener

            startActivity(Intent(requireContext(), AktivitasCheckoutRumahan::class.java))
        }

        renderBottomCart()
    }

    private fun loadProducts() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatProdukKasir() }
                .onSuccess { result ->
                    products = result
                        .filter { hasValidCashierPrice(it) }
                        .sortedWith(productComparator())

                    val categories = listOf("Semua") + products.map { it.category }
                        .distinct()
                        .sorted()

                    binding.spCategory.adapter =
                        AdapterSpinner.stringAdapter(requireContext(), categories)

                    binding.spStockMode.setSelection(0)

                    renderProducts()
                    renderBottomCart()
                }
                .onFailure {
                    products = emptyList()
                    renderProducts()
                    renderBottomCart()
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
        product.stock <= 0 -> STATUS_EMPTY
        product.stock <= product.minStock -> STATUS_LOW
        else -> STATUS_READY
    }

    private fun productComparator(): Comparator<Produk> {
        return compareBy<Produk>(
            { statusRank(productStatus(it)) },
            { it.name.lowercase() }
        )
    }

    private fun statusRank(status: String): Int {
        return when (status) {
            STATUS_READY -> 0
            STATUS_LOW -> 1
            STATUS_EMPTY -> 2
            else -> 3
        }
    }

    private fun selectedMode(): String {
        return binding.spStockMode.selectedItem?.toString().orEmpty().ifBlank { MODE_READY }
    }

    private fun renderProducts() {
        val keyword = binding.etSearch.text?.toString().orEmpty().trim().lowercase()
        val category = binding.spCategory.selectedItem?.toString().orEmpty().ifBlank { "Semua" }
        val mode = selectedMode()

        val filtered = products
            .filter { product ->
                val status = productStatus(product)

                val cocokKeyword =
                    keyword.isBlank() ||
                            product.name.lowercase().contains(keyword) ||
                            product.code.lowercase().contains(keyword)

                val cocokKategori =
                    category == "Semua" || product.category == category

                val cocokMode = when (mode) {
                    MODE_READY -> status == STATUS_READY
                    MODE_LOW -> status == STATUS_LOW
                    MODE_EMPTY -> status == STATUS_EMPTY
                    MODE_ALL -> true
                    else -> status == STATUS_READY
                }

                cocokKeyword && cocokKategori && cocokMode
            }
            .sortedWith(productComparator())

        productAdapter.submitList(filtered)

        val totalReady = products.count { productStatus(it) == STATUS_READY }
        val totalLow = products.count { productStatus(it) == STATUS_LOW }
        val totalEmpty = products.count { productStatus(it) == STATUS_EMPTY }

        binding.tvProductSummary.text = when {
            products.isEmpty() ->
                "Belum ada produk kasir dengan harga aktif di atas 0"
            filtered.isEmpty() ->
                "Tidak ada produk yang cocok • siap $totalReady • menipis $totalLow • habis $totalEmpty"
            else ->
                "${filtered.size} produk tampil • siap $totalReady • menipis $totalLow • habis $totalEmpty"
        }

        binding.tvEmptyProducts.isVisible = filtered.isEmpty()
        binding.rvProducts.isVisible = filtered.isNotEmpty()
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

        renderBottomCart()
        showMessage(binding.root, "${product.name} masuk ke keranjang")
    }

    private fun changeCartQty(delta: Int) {
        val items = SessionKeranjangRumahan.getItems()
        if (items.isEmpty()) return

        val lastItem = items.last()
        val product = products.firstOrNull { it.id == lastItem.productId } ?: return

        if (delta > 0) {
            val success = SessionKeranjangRumahan.changeQty(lastItem.productId, 1, product.stock)
            if (!success) {
                showMessage(binding.root, "Stok ${product.name} tidak mencukupi")
                return
            }
        } else {
            SessionKeranjangRumahan.changeQty(lastItem.productId, -1, product.stock)
        }

        renderBottomCart()
    }

    private fun renderBottomCart() {
        val items = SessionKeranjangRumahan.getItems()
        val totalQty = SessionKeranjangRumahan.totalQty()
        val emptyCart = items.isEmpty()

        binding.tvCartTitle.text = if (emptyCart) "Keranjang kosong" else "Keranjang"
        binding.tvCartSubtitle.text = if (emptyCart) "Belum ada produk" else "$totalQty item di keranjang"
        binding.tvQtyCart.text = totalQty.toString()

        binding.btnMinusQty.isEnabled = !emptyCart
        binding.btnPlusQty.isEnabled = !emptyCart
        binding.btnCheckout.isEnabled = !emptyCart
        binding.btnCheckout.alpha = if (emptyCart) 0.5f else 1f
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val MODE_READY = "Siap Dijual"
        private const val MODE_ALL = "Semua"
        private const val MODE_LOW = "Menipis"
        private const val MODE_EMPTY = "Habis"

        private const val STATUS_READY = "Aman"
        private const val STATUS_LOW = "Menipis"
        private const val STATUS_EMPTY = "Habis"
    }
}

private class FragmentSpinnerListener(
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