package muhamad.irfan.si_tahupm.ui.price

import android.content.Intent
import android.os.Bundle
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.ui.base.BaseListScreenActivity
import muhamad.irfan.si_tahupm.ui.product.ProductListActivity
import muhamad.irfan.si_tahupm.util.AppExtras
import muhamad.irfan.si_tahupm.util.Formatters
import muhamad.irfan.si_tahupm.util.RowItem
import muhamad.irfan.si_tahupm.util.RowTone

class PriceListActivity : BaseListScreenActivity() {
    private var initialSelectionIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreen("Harga Kanal", "Atur harga per kanal")
        hideSearch()
        val products = DemoRepository.allProducts()
        val initialProductId = intent.getStringExtra(AppExtras.EXTRA_PRODUCT_ID)
        initialSelectionIndex = products.indexOfFirst { it.id == initialProductId }.takeIf { it >= 0 } ?: 0
        setPrimaryFilter(products.map { it.name }, initialSelectionIndex) { refresh() }
        hideSecondaryFilter()
        setPrimaryButton("Tambah Harga") {
            startActivity(Intent(this, PriceFormActivity::class.java).putExtra(AppExtras.EXTRA_PRODUCT_ID, selectedProductId()))
        }
        setSecondaryButton("Data Produk") { startActivity(Intent(this, ProductListActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun selectedProductId(): String {
        val selectedName = primarySelection()
        return DemoRepository.allProducts().firstOrNull { it.name == selectedName }?.id
            ?: DemoRepository.allProducts().first().id
    }

    private fun refresh() {
        val product = DemoRepository.getProduct(selectedProductId()) ?: return
        submitRows(DemoRepository.visibleChannels(product).map {
            RowItem(
                id = it.id,
                title = it.label,
                subtitle = if (it.active) "Kanal aktif" else "Kanal nonaktif",
                badge = if (it.defaultCashier) "Default" else "Harga",
                amount = Formatters.currency(it.price),
                actionLabel = "Hapus",
                tone = if (it.defaultCashier) RowTone.GREEN else RowTone.GOLD
            )
        })
    }

    override fun onRowClick(item: RowItem) {
        startActivity(
            Intent(this, PriceFormActivity::class.java)
                .putExtra(AppExtras.EXTRA_PRODUCT_ID, selectedProductId())
                .putExtra(AppExtras.EXTRA_PRICE_ID, item.id)
        )
    }

    override fun onRowAction(item: RowItem) {
        runCatching { DemoRepository.softDeleteChannel(selectedProductId(), item.id) }
            .onSuccess {
                showMessage("Harga kanal berhasil dihapus secara soft delete.")
                refresh()
            }
            .onFailure { showMessage(it.message ?: "Gagal menghapus harga kanal") }
    }
}
