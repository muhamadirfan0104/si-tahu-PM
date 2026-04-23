package muhamad.irfan.si_tahu.ui.penjualan

import android.content.Intent
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.data.SessionKeranjangRumahan
import muhamad.irfan.si_tahu.databinding.ActivityCashierSaleCatalogBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.umum.AdapterProduk
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.Formatter

class AktivitasPenjualanRumahan : AktivitasDasar() {

    private lateinit var binding: ActivityCashierSaleCatalogBinding
    private lateinit var productAdapter: AdapterProduk

    private val pageSize = 5

    private var products: List<Produk> = emptyList()
    private var currentPage = 1
    private var totalPages = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        binding = ActivityCashierSaleCatalogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(
            binding.toolbar,
            "Penjualan Rumahan",
            "Tampilkan produk siap dijual atau habis dengan harga kasir yang valid"
        )

        productAdapter = AdapterProduk(
            onAdd = { addToCart(it) },
            getHarga = { defaultPrice(it) },
            getStatus = { productStatus(it) }
        )

        setupView()
        loadProducts()
    }

    override fun onResume() {
        super.onResume()
        renderBottomAction()
        renderProducts()
    }

    private fun setupView() {
        binding.rvProducts.layoutManager = LinearLayoutManager(this)
        binding.rvProducts.adapter = productAdapter

        binding.spCategory.adapter =
            AdapterSpinner.stringAdapter(this, listOf("Semua"))

        binding.spStockMode.adapter =
            AdapterSpinner.stringAdapter(
                this,
                listOf(
                    MODE_READY,
                    MODE_EMPTY
                )
            )

        binding.spCategory.onItemSelectedListener = SimpleSpinnerListener {
            currentPage = 1
            renderProducts()
        }

        binding.spStockMode.onItemSelectedListener = SimpleSpinnerListener {
            currentPage = 1
            renderProducts()
        }

        binding.etSearch.addTextChangedListener {
            currentPage = 1
            renderProducts()
        }

        binding.btnPagePrev.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                renderProducts()
            }
        }

        binding.btnPageNext.setOnClickListener {
            if (currentPage < totalPages) {
                currentPage++
                renderProducts()
            }
        }

        binding.layoutCartAction.setOnClickListener {
            openCheckout()
        }

        binding.btnCheckout.setOnClickListener {
            openCheckout()
        }

        binding.btnCartPlus.setOnClickListener {
            increaseFocusedItem()
        }

        binding.btnCartMinus.setOnClickListener {
            decreaseFocusedItem()
        }
    }

    private fun loadProducts() {
        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatProdukKasir() }
                .onSuccess { result ->
                    products = result
                        .filter { hasValidCashierPrice(it) }
                        .sortedWith(productComparator())

                    val categories = listOf("Semua") + products.map { it.category }
                        .distinct()
                        .sorted()

                    binding.spCategory.adapter =
                        AdapterSpinner.stringAdapter(this@AktivitasPenjualanRumahan, categories)

                    binding.spStockMode.setSelection(0)
                    currentPage = 1

                    renderProducts()
                    renderBottomAction()
                }
                .onFailure {
                    products = emptyList()
                    currentPage = 1
                    renderProducts()
                    renderBottomAction()
                    showMessage(it.message ?: "Gagal memuat produk")
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
            STATUS_EMPTY -> 1
            else -> 2
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
                    MODE_EMPTY -> status == STATUS_EMPTY
                    else -> status == STATUS_READY
                }

                cocokKeyword && cocokKategori && cocokMode
            }
            .sortedWith(productComparator())

        totalPages = if (filtered.isEmpty()) 1 else ((filtered.size - 1) / pageSize) + 1
        if (currentPage > totalPages) currentPage = totalPages
        if (currentPage < 1) currentPage = 1

        val fromIndex = (currentPage - 1) * pageSize
        val toIndex = minOf(fromIndex + pageSize, filtered.size)
        val pagedProducts =
            if (filtered.isEmpty()) emptyList() else filtered.subList(fromIndex, toIndex)

        productAdapter.submitList(pagedProducts)

        val totalReady = products.count { productStatus(it) == STATUS_READY }
        val totalEmpty = products.count { productStatus(it) == STATUS_EMPTY }

        binding.tvProductSummary.text = when {
            products.isEmpty() ->
                "Belum ada produk kasir dengan harga aktif di atas 0"
            filtered.isEmpty() ->
                "Tidak ada produk yang cocok • siap dijual $totalReady • habis $totalEmpty"
            else ->
                "${filtered.size} produk total • tampil ${pagedProducts.size} di halaman $currentPage • siap dijual $totalReady • habis $totalEmpty"
        }

        binding.tvEmptyProducts.isVisible = pagedProducts.isEmpty()
        binding.rvProducts.isVisible = pagedProducts.isNotEmpty()
        binding.paginationContainer.isVisible = filtered.size > pageSize
        binding.tvPageInfo.text = "Halaman $currentPage dari $totalPages"
        binding.btnPagePrev.isEnabled = currentPage > 1
        binding.btnPagePrev.alpha = if (currentPage > 1) 1f else 0.45f
        binding.btnPageNext.isEnabled = currentPage < totalPages
        binding.btnPageNext.alpha = if (currentPage < totalPages) 1f else 0.45f
    }

    private fun addToCart(product: Produk) {
        if (defaultPrice(product) <= 0L) {
            showMessage("Produk ${product.name} belum punya harga kasir yang valid")
            return
        }

        if (product.stock <= 0) {
            showMessage("Produk ${product.name} sedang habis")
            return
        }

        val success = SessionKeranjangRumahan.addOrIncrease(
            productId = product.id,
            price = defaultPrice(product),
            maxStock = product.stock
        )

        if (!success) {
            showMessage("Stok ${product.name} tidak mencukupi")
            return
        }

        renderBottomAction()
        showMessage("${product.name} masuk ke keranjang")
    }

    private fun increaseFocusedItem() {
        if (SessionKeranjangRumahan.isEmpty()) return

        val success = SessionKeranjangRumahan.increaseFocused { productId ->
            products.firstOrNull { it.id == productId }?.stock ?: 0
        }

        if (!success) {
            showMessage("Stok produk di keranjang tidak mencukupi")
            return
        }

        renderBottomAction()
    }

    private fun decreaseFocusedItem() {
        if (SessionKeranjangRumahan.isEmpty()) return
        SessionKeranjangRumahan.decreaseFocused()
        renderBottomAction()
    }

    private fun renderBottomAction() {
        val totalQty = SessionKeranjangRumahan.totalQty()
        val totalAmount = SessionKeranjangRumahan.totalAmount()
        val hasItems = totalQty > 0

        binding.tvCartBadge.text = if (hasItems) totalQty.toString() else "0"
        binding.tvCartLabel.text = if (hasItems) {
            "$totalQty item"
        } else {
            "Keranjang kosong"
        }
        binding.tvCartAmount.text = if (hasItems) {
            Formatter.currency(totalAmount)
        } else {
            "Belum ada produk"
        }

        binding.btnCheckout.isEnabled = hasItems
        binding.btnCartPlus.isEnabled = hasItems
        binding.btnCartMinus.isEnabled = hasItems

        binding.btnCartPlus.alpha = if (hasItems) 1f else 0.5f
        binding.btnCartMinus.alpha = if (hasItems) 1f else 0.5f
        binding.layoutCartAction.alpha = if (hasItems) 1f else 0.75f
    }

    private fun openCheckout() {
        if (SessionKeranjangRumahan.isEmpty()) return
        startActivity(Intent(this, AktivitasCheckoutRumahan::class.java))
    }

    companion object {
        private const val MODE_READY = "Siap Dijual"
        private const val MODE_EMPTY = "Habis"

        private const val STATUS_READY = "Siap Dijual"
        private const val STATUS_EMPTY = "Habis"
    }
}

private class SimpleSpinnerListener(
    private val onSelected: () -> Unit
) : android.widget.AdapterView.OnItemSelectedListener {
    override fun onItemSelected(
        parent: android.widget.AdapterView<*>?,
        view: android.view.View?,
        position: Int,
        id: Long
    ) {
        onSelected()
    }

    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
}