package muhamad.irfan.si_tahupm.ui.history

import android.os.Bundle
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.ui.base.BaseListScreenActivity
import muhamad.irfan.si_tahupm.util.RowItem
import muhamad.irfan.si_tahupm.util.RowTone

class TransactionHistoryActivity : BaseListScreenActivity() {
    private val typeOptions = listOf("Semua", "Penjualan", "Rekap Pasar", "Produksi", "Konversi", "Pengeluaran")
    private var currentRows: List<muhamad.irfan.si_tahupm.data.TransactionRow> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreen("Riwayat Transaksi", "Histori lintas modul")
        setPrimaryFilter(typeOptions) { refresh() }
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
        val type = primarySelection()
        currentRows = DemoRepository.transactions().filter {
            (it.id + " " + it.subtitle).lowercase().contains(query) && (type == "Semua" || it.type == type)
        }
        submitRows(currentRows.map {
            RowItem(
                id = it.id,
                title = it.id,
                subtitle = it.type + " • " + it.subtitle,
                badge = it.type,
                amount = it.valueText,
                tone = when (it.type) {
                    "Produksi" -> RowTone.GREEN
                    "Konversi" -> RowTone.BLUE
                    "Pengeluaran" -> RowTone.ORANGE
                    else -> RowTone.GOLD
                }
            )
        })
    }

    override fun onRowClick(item: RowItem) {
        val detail = currentRows.firstOrNull { it.id == item.id } ?: return
        showDetailModal(detail.id, DemoRepository.buildTransactionDetailText(detail.id, detail.type))
    }
}
