package muhamad.irfan.si_tahupm.ui.history

import android.os.Bundle
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.data.TransactionRow
import muhamad.irfan.si_tahupm.ui.base.BaseListScreenActivity
import muhamad.irfan.si_tahupm.util.RowItem
import muhamad.irfan.si_tahupm.util.RowTone

class TransactionHistoryActivity : BaseListScreenActivity() {
    private val typeOptions = listOf("Semua", "Penjualan", "Rekap Pasar", "Produksi", "Konversi", "Pengeluaran", "Adjustment")
    private var currentRows: List<TransactionRow> = emptyList()

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
                actionLabel = "Hapus",
                tone = when (it.type) {
                    "Produksi" -> RowTone.GREEN
                    "Konversi" -> RowTone.BLUE
                    "Pengeluaran" -> RowTone.ORANGE
                    "Adjustment" -> RowTone.ORANGE
                    else -> RowTone.GOLD
                }
            )
        })
    }

    override fun onRowClick(item: RowItem) {
        val detail = currentRows.firstOrNull { it.id == item.id } ?: return
        if (detail.type == "Penjualan" || detail.type == "Rekap Pasar") {
            showReceiptModal("Struk ${detail.id}", DemoRepository.buildReceiptText(detail.id))
        } else {
            showDetailModal(detail.id, DemoRepository.buildTransactionDetailText(detail.id, detail.type))
        }
    }

    override fun onRowAction(item: RowItem) {
        val row = currentRows.firstOrNull { it.id == item.id } ?: return
        val deleteMessage = when (row.type) {
            "Penjualan", "Rekap Pasar" -> "Transaksi ${row.id} akan dihapus dan stok produk akan dikembalikan. Lanjutkan?"
            "Produksi" -> "Produksi ${row.id} akan dihapus dan stok hasil produksi akan dikurangi kembali. Lanjutkan?"
            "Konversi" -> "Konversi ${row.id} akan dihapus dan stok bahan/hasil akan dibalikkan. Lanjutkan?"
            "Pengeluaran" -> "Pengeluaran ${row.id} akan dihapus dari riwayat. Lanjutkan?"
            else -> "Adjustment ${row.id} akan dihapus dan stok akan dibalikkan. Lanjutkan?"
        }
        showConfirmationModal("Hapus ${row.type.lowercase()}?", deleteMessage) {
            runCatching {
                when (row.type) {
                    "Penjualan", "Rekap Pasar" -> DemoRepository.deleteSale(row.id)
                    "Produksi" -> DemoRepository.deleteProduction(row.id)
                    "Konversi" -> DemoRepository.deleteConversion(row.id)
                    "Pengeluaran" -> DemoRepository.deleteExpense(row.id)
                    else -> DemoRepository.deleteAdjustment(row.id)
                }
            }.onSuccess {
                showMessage("Data ${row.type.lowercase()} berhasil dihapus.")
                refresh()
            }.onFailure { showMessage(it.message ?: "Gagal menghapus ${row.type.lowercase()}") }
        }
    }
}
