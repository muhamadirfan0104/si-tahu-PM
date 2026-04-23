package muhamad.irfan.si_tahu.ui.utama

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.data.SessionKeranjangRumahan
import muhamad.irfan.si_tahu.databinding.FragmentCashierSaleBinding
import muhamad.irfan.si_tahu.ui.dasar.FragmenDasar
import muhamad.irfan.si_tahu.ui.penjualan.AktivitasCheckoutRumahan
import muhamad.irfan.si_tahu.ui.umum.AdapterKeranjang
import muhamad.irfan.si_tahu.ui.umum.AdapterProduk
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.Formatter

class FragmenKasirSale : FragmenDasar(R.layout.fragment_cashier_sale) {

    private var _binding: FragmentCashierSaleBinding? = null
    private val binding get() = _binding!!

    private lateinit var cartBottomSheetBehavior: BottomSheetBehavior<View>

    private val productAdapter by lazy {
        AdapterProduk(
            onAdd = ::addToCart,
            getHarga = ::defaultPrice,
            getStatus = ::productStatus
        )
    }

    private val cartAdapter by lazy {
        AdapterKeranjang(
            onIncrease = { item -> changeCartItemQty(item.productId, 1) },
            onDecrease = { item -> changeCartItemQty(item.productId, -1) },
            onRemove = { item ->
                SessionKeranjangRumahan.remove(item.productId)
                renderBottomCart()
            },
            getProduk = { productId ->
                products.firstOrNull { it.id == productId }
            }
        )
    }

    private val pageSize = 5

    private var products: List<Produk> = emptyList()
    private var currentPage = 1
    private var totalPages = 1

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentCashierSaleBinding.bind(view)

        setupView()
        loadProducts()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            renderBottomCart()
            renderProducts()
        }
    }

    private fun setupView() {
        binding.rvProducts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProducts.adapter = productAdapter

        binding.rvCart.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCart.adapter = cartAdapter
        binding.rvCart.isNestedScrollingEnabled = true

        binding.spCategory.adapter =
            AdapterSpinner.stringAdapter(requireContext(), listOf("Semua"))

        binding.spStockMode.adapter =
            AdapterSpinner.stringAdapter(
                requireContext(),
                listOf(MODE_READY, MODE_EMPTY)
            )

        binding.spCategory.onItemSelectedListener = FragmentSpinnerListener {
            currentPage = 1
            renderProducts()
        }

        binding.spStockMode.onItemSelectedListener = FragmentSpinnerListener {
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

        binding.btnCheckout.setOnClickListener {
            if (SessionKeranjangRumahan.getItems().isEmpty()) return@setOnClickListener
            startActivity(Intent(requireContext(), AktivitasCheckoutRumahan::class.java))
        }

        binding.layoutCartHeader.setOnClickListener {
            toggleCartSheet()
        }
        binding.viewCartHandle.setOnClickListener {
            toggleCartSheet()
        }

        cartBottomSheetBehavior = BottomSheetBehavior.from(binding.cardBottomCart)
        binding.cardBottomCart.post {
            cartBottomSheetBehavior.peekHeight = dpToPx(190)
            cartBottomSheetBehavior.isHideable = false
            cartBottomSheetBehavior.skipCollapsed = false
            cartBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        renderBottomCart()
    }

    private fun toggleCartSheet() {
        cartBottomSheetBehavior.state = when (cartBottomSheetBehavior.state) {
            BottomSheetBehavior.STATE_EXPANDED -> BottomSheetBehavior.STATE_COLLAPSED
            else -> BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun loadProducts() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatProdukKasir() }
                .onSuccess { result ->
                    products = result
                        .filter { product -> hasValidCashierPrice(product) }
                        .sortedWith(productComparator())

                    val categories = listOf("Semua") + products
                        .map { product -> product.category }
                        .distinct()
                        .sorted()

                    binding.spCategory.adapter =
                        AdapterSpinner.stringAdapter(requireContext(), categories)

                    binding.spStockMode.setSelection(0)
                    currentPage = 1

                    renderProducts()
                    renderBottomCart()
                }
                .onFailure { error ->
                    products = emptyList()
                    currentPage = 1
                    renderProducts()
                    renderBottomCart()
                    showMessage(binding.root, error.message ?: "Gagal memuat produk")
                }
        }
    }

    private fun defaultPrice(product: Produk): Long {
        return product.channels.firstOrNull { channel ->
            channel.defaultCashier && channel.active
        }?.price
            ?: product.channels.firstOrNull { channel -> channel.active }?.price
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
            { product -> statusRank(productStatus(product)) },
            { product -> product.name.lowercase() }
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

        val totalReady = products.count { product -> productStatus(product) == STATUS_READY }
        val totalEmpty = products.count { product -> productStatus(product) == STATUS_EMPTY }

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
            showMessage(binding.root, "Produk ${product.name} belum punya harga kasir yang valid")
            return
        }

        if (product.stock <= 0) {
            showMessage(binding.root, "Produk ${product.name} sedang habis")
            return
        }

        val wasEmpty = SessionKeranjangRumahan.isEmpty()
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
        if (wasEmpty) {
            cartBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        showMessage(binding.root, "${product.name} masuk ke keranjang")
    }

    private fun changeCartItemQty(productId: String, delta: Int) {
        val product = products.firstOrNull { it.id == productId } ?: return

        if (delta > 0) {
            val success = SessionKeranjangRumahan.changeQty(productId, 1, product.stock)
            if (!success) {
                showMessage(binding.root, "Stok ${product.name} tidak mencukupi")
                return
            }
        } else {
            SessionKeranjangRumahan.changeQty(productId, -1, product.stock)
        }

        renderBottomCart()
    }

    private fun renderBottomCart() {
        val items = SessionKeranjangRumahan.getItems()
        val totalQty = SessionKeranjangRumahan.totalQty()
        val totalAmount = SessionKeranjangRumahan.totalAmount()
        val emptyCart = items.isEmpty()

        cartAdapter.submitList(items)

        binding.tvCartTitle.text = if (emptyCart) {
            "Keranjang kosong"
        } else {
            "$totalQty item di keranjang"
        }

        binding.tvCartSubtitle.text = if (emptyCart) {
            "Tarik panel ke atas untuk melihat item"
        } else {
            "${items.size} baris produk • tarik ke atas untuk melihat lebih banyak"
        }

        binding.tvCartTotal.text = Formatter.currency(totalAmount)
        binding.tvEmptyCart.isVisible = emptyCart
        binding.rvCart.isVisible = !emptyCart
        binding.btnCheckout.isEnabled = !emptyCart
        binding.btnCheckout.alpha = if (emptyCart) 0.5f else 1f

        if (emptyCart && ::cartBottomSheetBehavior.isInitialized) {
            cartBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    private fun dpToPx(valueDp: Int): Int {
        return (valueDp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val MODE_READY = "Siap Dijual"
        private const val MODE_EMPTY = "Habis"

        private const val STATUS_READY = "Siap Dijual"
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