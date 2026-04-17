package muhamad.irfan.si_tahu.ui.stok

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDaftarDasar
import muhamad.irfan.si_tahu.ui.penjualan.AktivitasMenuPenjualan
import muhamad.irfan.si_tahu.ui.produksi.AktivitasMenuProduksi
import muhamad.irfan.si_tahu.ui.utama.AktivitasUtamaAdmin
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class AktivitasMonitoringStok : AktivitasDaftarDasar() {

    private var products: List<Produk> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreen("Monitoring Stok", "Pantau stok semua produk", showBack = false)
        setPrimaryFilter(listOf("Semua", "Aman", "Menipis", "Habis")) { refresh() }
        hideSecondaryFilter()
        hideButtons()
        setFabAdd {
            startActivity(Intent(this, AktivitasStockAdjustment::class.java))
        }
        binding.bottomNavigation.isVisible = true
        binding.bottomDivider.isVisible = true
        binding.fabAdd.updateLayoutParams<android.widget.FrameLayout.LayoutParams> {
            bottomMargin = (96 * resources.displayMetrics.density).toInt()
        }
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.menu.clear()
        binding.bottomNavigation.inflateMenu(R.menu.menu_bottom_admin)
        binding.bottomNavigation.selectedItemId = R.id.nav_admin_stock
        binding.bottomNavigation.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_admin_dashboard -> {
                    startActivity(AktivitasUtamaAdmin.intent(this, R.id.nav_admin_dashboard, clearTop = true))
                    true
                }
                R.id.nav_admin_menu -> {
                    startActivity(AktivitasUtamaAdmin.intent(this, R.id.nav_admin_menu, clearTop = true))
                    true
                }
                R.id.nav_admin_production -> {
                    startActivity(Intent(this, AktivitasMenuProduksi::class.java))
                    true
                }
                R.id.nav_admin_sales -> {
                    startActivity(Intent(this, AktivitasMenuPenjualan::class.java))
                    true
                }
                R.id.nav_admin_stock -> true
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadProducts()
    }

    override fun onSearchChanged() = refresh()

    private fun productStatus(product: Produk): String = when {
        product.stock <= 0 -> "Habis"
        product.stock <= product.minStock -> "Menipis"
        else -> "Aman"
    }

    private fun loadProducts() {
        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatMonitoringStok() }
                .onSuccess {
                    products = it
                    refresh()
                }
                .onFailure {
                    products = emptyList()
                    submitRows(emptyList())
                    showMessage(it.message ?: "Gagal memuat stok")
                }
        }
    }

    private fun refresh() {
        val keyword = searchText()
        val filter = primarySelection()
        val rows = products.filter { product ->
            val status = productStatus(product)
            (filter == "Semua" || filter == status) &&
                (keyword.isBlank() || product.name.lowercase().contains(keyword) || product.code.lowercase().contains(keyword))
        }.map { product ->
            val status = productStatus(product)
            ItemBaris(
                id = product.id,
                title = product.name,
                subtitle = "${product.code} • ${product.category} • ${product.unit}",
                badge = status,
                amount = "Stok ${product.stock} • Min ${product.minStock}",
                priceStatus = if (product.showInCashier) "Tampil di kasir" else "Tidak tampil",
                parameterStatus = if (product.active) "Aktif" else "Nonaktif",
                tone = when (status) {
                    "Aman" -> WarnaBaris.GREEN
                    "Menipis" -> WarnaBaris.GOLD
                    else -> WarnaBaris.ORANGE
                },
                priceTone = WarnaBaris.BLUE,
                parameterTone = if (product.active) WarnaBaris.GREEN else WarnaBaris.ORANGE,
                actionLabel = "⋮"
            )
        }
        submitRows(rows)
    }

    override fun onRowClick(item: ItemBaris) {
        startActivity(Intent(this, AktivitasDetailStok::class.java).putExtra(AktivitasDetailStok.EXTRA_PRODUCT_ID, item.id))
    }

    override fun onRowAction(item: ItemBaris, anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Lihat detail stok")
            menu.add("Adjustment")
            setOnMenuItemClickListener {
                when (it.title.toString()) {
                    "Lihat detail stok" -> onRowClick(item)
                    "Adjustment" -> startActivity(Intent(this@AktivitasMonitoringStok, AktivitasStockAdjustment::class.java).putExtra(AktivitasStockAdjustment.EXTRA_PRODUCT_ID, item.id))
                }
                true
            }
        }.show()
    }
}
