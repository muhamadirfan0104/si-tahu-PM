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
                actionLabel = "Hapus",
                tone = if (it.source == "KASIR") RowTone.GOLD else RowTone.BLUE
            )
        })
    }

    override fun onRowClick(item: RowItem) {
        showReceiptModal("Struk ${item.id}", DemoRepository.buildReceiptText(item.id))
    }

    override fun onRowAction(item: RowItem) {
        showConfirmationModal(
            title = "Hapus transaksi penjualan?",
            message = "Transaksi ${item.id} akan dihapus dan stok produk akan dikembalikan. Lanjutkan?"
        ) {
            runCatching { DemoRepository.deleteSale(item.id) }
                .onSuccess {
                    showMessage("Transaksi penjualan berhasil dihapus.")
                    refresh()
                }
                .onFailure { showMessage(it.message ?: "Gagal menghapus transaksi penjualan") }
        }
    }
}
