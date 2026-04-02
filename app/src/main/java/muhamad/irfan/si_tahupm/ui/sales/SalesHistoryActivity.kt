package muhamad.irfan.si_tahupm.ui.sales

import android.os.Bundle
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.ui.base.BaseListScreenActivity
import muhamad.irfan.si_tahupm.util.Formatters
import muhamad.irfan.si_tahupm.util.RowItem
import muhamad.irfan.si_tahupm.util.RowTone

class SalesHistoryActivity : BaseListScreenActivity() {
    private val sourceOptions = listOf("Semua", "KASIR", "PASAR", "RESELLER", "GROSIR")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreen("Riwayat Penjualan", "Kasir dan rekap pasar")
        setPrimaryFilter(sourceOptions) { refresh() }
        hideSecondaryFilter()
        hideButtons()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onSearchChanged() = refresh()

    private fun refresh() {
        val query = searchText()
        val source = primarySelection()
        submitRows(DemoRepository.allSales().filter {
            val text = (it.id + " " + it.source + " " + it.items.joinToString(" ") { item -> DemoRepository.getProduct(item.productId)?.name ?: "" }).lowercase()
            text.contains(query) && (source == "Semua" || it.source == source)
        }.map {
            RowItem(
                id = it.id,
                title = it.id,
                subtitle = it.source + " • " + it.items.joinToString(", ") { item -> (DemoRepository.getProduct(item.productId)?.name ?: "Produk") + " x" + item.qty },
                badge = it.paymentMethod,
                amount = Formatters.currency(it.total),
                tone = if (it.source == "KASIR") RowTone.GOLD else RowTone.BLUE
            )
        })
    }

    override fun onRowClick(item: RowItem) {
        showDetailModal(item.title, DemoRepository.buildReceiptText(item.id))
    }
}
