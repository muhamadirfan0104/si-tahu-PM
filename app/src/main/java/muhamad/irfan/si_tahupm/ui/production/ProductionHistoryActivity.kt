package muhamad.irfan.si_tahupm.ui.production

import android.os.Bundle
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.ui.base.BaseListScreenActivity
import muhamad.irfan.si_tahupm.util.Formatters
import muhamad.irfan.si_tahupm.util.RowItem
import muhamad.irfan.si_tahupm.util.RowTone

class ProductionHistoryActivity : BaseListScreenActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreen("Riwayat Produksi", "Daftar produksi tahu dasar")
        hidePrimaryFilter()
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
        submitRows(
            DemoRepository.allProductionLogs().filter {
                val text = (it.id + " " + (DemoRepository.getProduct(it.productId)?.name ?: "")).lowercase()
                text.contains(query)
            }.map {
                RowItem(
                    id = it.id,
                    title = it.id,
                    subtitle = (DemoRepository.getProduct(it.productId)?.name ?: "Produk") + " • " + Formatters.readableDateTime(it.date),
                    badge = "Produksi",
                    amount = it.result.toString() + " pcs",
                    tone = RowTone.GREEN
                )
            }
        )
    }

    override fun onRowClick(item: RowItem) {
        showDetailModal(item.title, DemoRepository.buildProductionDetailText(item.id))
    }
}
