package muhamad.irfan.si_tahu.ui.penjualan

import android.content.Intent
import android.os.Bundle
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

    private var products: List<Produk> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        binding = ActivityCashierSaleCatalogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Penjualan Rumahan", "Pilih produk untuk dimasukkan ke keranjang")

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

        binding.spCategory.onItemSelectedListener = SimpleSpinnerListener {
            renderProducts()
        }

        binding.etSearch.addTextChangedListener {
            renderProducts()
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

                    val categories = listOf("Semua") + products.map { it.category }
                        .distinct()
                        .sorted()

                    binding.spCategory.adapter =
                        AdapterSpinner.stringAdapter(this@AktivitasPenjualanRumahan, categories)

                    renderProducts()
                    renderBottomAction()
                }
                .onFailure {
                    products = emptyList()
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

    private fun productStatus(product: Produk): String = when {
        product.stock <= 0 -> "Habis"
        product.stock <= product.minStock -> "Menipis"
        else -> "Aman"
    }

    private fun renderProducts() {
        val keyword = binding.etSearch.text?.toString().orEmpty().trim().lowercase()
        val category = binding.spCategory.selectedItem?.toString().orEmpty().ifBlank { "Semua" }

        val filtered = products.filter {
            (keyword.isBlank()
                    || it.name.lowercase().contains(keyword)
                    || it.code.lowercase().contains(keyword)) &&
                    (category == "Semua" || it.category == category)
        }

        productAdapter.submitList(filtered)

        binding.tvProductSummary.text = when {
            products.isEmpty() -> "Belum ada produk aktif untuk kasir"
            filtered.isEmpty() -> "Tidak ada produk yang cocok"
            else -> "${filtered.size} produk tampil • stok real-time"
        }
    }

    private fun addToCart(product: Produk) {
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
}

private class SimpleSpinnerListener(
    private val onSelected: () -> Unit
) : android.widget.AdapterView.OnItemSelectedListener {
    override fun onItemSelected(
        parent: android.widget.AdapterView<*>?,
        view: android.view.View?,
        position: Int,
        id: Long
    ) = onSelected()

    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
}