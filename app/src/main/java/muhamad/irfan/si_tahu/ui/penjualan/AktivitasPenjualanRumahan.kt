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
import muhamad.irfan.si_tahu.utilitas.PembantuFilterRiwayat

class AktivitasPenjualanRumahan : AktivitasDasar() {

    private lateinit var binding: ActivityCashierSaleCatalogBinding
    private lateinit var productAdapter: AdapterProduk

    private val pageSize = 5

    private var products: List<Produk> = emptyList()
    private var categoryOptions = listOf("Semua")
    private var kategoriAktif = "Semua"
    private var modeAktif = MODE_ALL
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
            AdapterSpinner.stringAdapter(this, listOf(MODE_ALL, MODE_READY, MODE_EMPTY))

        binding.spCategory.isVisible = false
        binding.spStockMode.isVisible = false
        binding.tvFilterBadge.isVisible = false

        binding.btnOpenFilters.setOnClickListener {
            bukaFilter()
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

                    categoryOptions = listOf("Semua") + products.map { it.category }
                        .distinct()
                        .sorted()

                    binding.spCategory.adapter =
                        AdapterSpinner.stringAdapter(this@AktivitasPenjualanRumahan, categoryOptions)

                    if (kategoriAktif !in categoryOptions) kategoriAktif = "Semua"
                    modeAktif = MODE_ALL
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

    private fun stokLayakJual(product: Produk): Int {
        return product.safeStock + product.nearExpiredStock
    }

    private fun productStatus(product: Produk): String = when {
        stokLayakJual(product) <= 0 && product.expiredStock > 0 -> STATUS_EXPIRED
        stokLayakJual(product) <= 0 -> STATUS_EMPTY
        product.nearExpiredStock > 0 -> STATUS_NEAR_EXPIRED
        product.producedToday -> STATUS_PRODUCED_TODAY
        else -> STATUS_LEFTOVER
    }

    private fun productComparator(): Comparator<Produk> {
        return compareBy<Produk>(
            { statusRank(productStatus(it)) },
            { it.name.lowercase() }
        )
    }

    private fun statusRank(status: String): Int {
        return when (status) {
            STATUS_PRODUCED_TODAY -> 0
            STATUS_LEFTOVER -> 1
            STATUS_NEAR_EXPIRED -> 2
            STATUS_EMPTY -> 3
            STATUS_EXPIRED -> 4
            else -> 5
        }
    }

    private fun selectedMode(): String {
        return modeAktif
    }

    private fun renderProducts() {
        val keyword = binding.etSearch.text?.toString().orEmpty().trim().lowercase()
        val mode = selectedMode()

        val filtered = products
            .filter { product ->
                val status = productStatus(product)

                val cocokKeyword =
                    keyword.isBlank() ||
                            product.name.lowercase().contains(keyword) ||
                            product.code.lowercase().contains(keyword)

                val cocokMode = when (mode) {
                    MODE_READY -> stokLayakJual(product) > 0
                    MODE_EMPTY -> stokLayakJual(product) <= 0
                    else -> true
                }

                cocokKeyword && cocokMode
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

        val totalReady = products.count { stokLayakJual(it) > 0 }
        val totalEmpty = products.count { stokLayakJual(it) <= 0 }
        val totalNearExpired = products.count { it.nearExpiredStock > 0 }
        val totalExpired = products.count { it.expiredStock > 0 }

        binding.tvProductSummary.text = when {
            products.isEmpty() ->
                "Belum ada produk kasir dengan harga aktif di atas 0"
            filtered.isEmpty() ->
                "Tidak ada produk yang cocok • siap dijual $totalReady • habis $totalEmpty • hampir ED $totalNearExpired • ED $totalExpired"
            else ->
                "${filtered.size} produk total • siap dijual $totalReady • habis $totalEmpty • hampir ED $totalNearExpired • ED $totalExpired"
        }

        binding.tvEmptyProducts.isVisible = pagedProducts.isEmpty()
        binding.rvProducts.isVisible = pagedProducts.isNotEmpty()
        binding.paginationContainer.isVisible = filtered.size > pageSize
        binding.tvPageInfo.text = "Halaman $currentPage dari $totalPages"
        binding.btnPagePrev.isEnabled = currentPage > 1
        binding.btnPagePrev.alpha = if (currentPage > 1) 1f else 0.45f
        binding.btnPageNext.isEnabled = currentPage < totalPages
        binding.btnPageNext.alpha = if (currentPage < totalPages) 1f else 0.45f
        updateFilterUi()
    }

    private fun bukaFilter() {
        PembantuFilterRiwayat.show(
            activity = this,
            kategori = listOf(MODE_ALL, MODE_READY, MODE_EMPTY),
            kategoriTerpilih = modeAktif,
            tanggalLabel = null,
            jumlahFilterAktif = jumlahFilterAktif(),
            onKategoriDipilih = { pilihan ->
                modeAktif = pilihan
                currentPage = 1
                renderProducts()
            },
            onPilihTanggal = {},
            onHapusTanggal = {},
            onReset = {
                modeAktif = MODE_ALL
                currentPage = 1
                renderProducts()
                showMessage("Filter produk kasir direset")
            },
            kategoriLabel = "Status Produk",
            tampilkanTanggal = false
        )
    }

    private fun jumlahFilterAktif(): Int {
        var total = 0
        if (modeAktif != MODE_ALL) total++
        return total
    }

    private fun updateFilterUi() {
        binding.tvFilterBadge.isVisible = jumlahFilterAktif() > 0
        binding.tvFilterBadge.text = jumlahFilterAktif().toString()
    }

    private fun addToCart(product: Produk) {
        if (defaultPrice(product) <= 0L) {
            showMessage("Produk ${product.name} belum punya harga kasir yang valid")
            return
        }

        val stokLayak = stokLayakJual(product)
        if (stokLayak <= 0) {
            val message = if (product.expiredStock > 0) {
                "Stok ${product.name} sudah kadaluarsa dan tidak bisa dijual"
            } else {
                "Produk ${product.name} sedang habis"
            }
            showMessage(message)
            return
        }

        val success = SessionKeranjangRumahan.addOrIncrease(
            productId = product.id,
            price = defaultPrice(product),
            maxStock = stokLayak
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
            products.firstOrNull { it.id == productId }?.let { stokLayakJual(it) } ?: 0
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
        private const val MODE_ALL = "Semua"
        private const val MODE_READY = "Siap Dijual"
        private const val MODE_EMPTY = "Habis"

        private const val STATUS_PRODUCED_TODAY = "Produksi Hari Ini"
        private const val STATUS_LEFTOVER = "Stok Sisa"
        private const val STATUS_NEAR_EXPIRED = "Hampir Kadaluarsa"
        private const val STATUS_EXPIRED = "Kadaluarsa"
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