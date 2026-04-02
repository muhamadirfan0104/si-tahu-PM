package muhamad.irfan.si_tahupm.ui.product

import android.content.Intent
import android.os.Bundle
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.ui.base.BaseListScreenActivity
import muhamad.irfan.si_tahupm.ui.price.PriceListActivity
import muhamad.irfan.si_tahupm.util.AppExtras
import muhamad.irfan.si_tahupm.util.RowItem
import muhamad.irfan.si_tahupm.util.RowTone

class ProductListActivity : BaseListScreenActivity() {
    private val categories = listOf("Semua", "DASAR", "OLAHAN")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreen("Daftar Produk", "Kelola produk dasar dan olahan")
        setPrimaryFilter(categories) { refresh() }
        hideSecondaryFilter()
        setPrimaryButton("Tambah Produk") { startActivity(Intent(this, ProductFormActivity::class.java)) }
        setSecondaryButton("Harga Kanal") { startActivity(Intent(this, PriceListActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onSearchChanged() = refresh()

    private fun refresh() {
        val query = searchText()
        val category = primarySelection()
        submitRows(DemoRepository.allProducts().filter {
            it.name.lowercase().contains(query) && (category == "Semua" || it.category == category)
        }.map {
            RowItem(
                id = it.id,
                title = it.name,
                subtitle = "${it.code} • ${it.category} • ${it.unit}",
                badge = if (it.active) "Aktif" else "Nonaktif",
                amount = "Stok ${it.stock}",
                actionLabel = "Hapus",
                tone = when (DemoRepository.productStatus(it)) {
                    "Aman" -> RowTone.GREEN
                    "Menipis" -> RowTone.GOLD
                    else -> RowTone.ORANGE
                }
            )
        })
    }

    override fun onRowClick(item: RowItem) {
        startActivity(Intent(this, ProductFormActivity::class.java).putExtra(AppExtras.EXTRA_PRODUCT_ID, item.id))
    }

    override fun onRowAction(item: RowItem) {
        runCatching { DemoRepository.softDeleteProduct(item.id) }
            .onSuccess {
                showMessage("Produk berhasil dihapus secara soft delete.")
                refresh()
            }
            .onFailure { showMessage(it.message ?: "Gagal menghapus produk") }
    }
}
