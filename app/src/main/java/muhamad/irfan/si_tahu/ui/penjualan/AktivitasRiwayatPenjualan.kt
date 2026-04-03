package muhamad.irfan.si_tahu.ui.sales

import android.os.Bundle
import muhamad.irfan.si_tahu.data.RepositoriLokal
import muhamad.irfan.si_tahu.ui.base.AktivitasDaftarDasar
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class AktivitasRiwayatPenjualan : AktivitasDaftarDasar() {
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
        submitRows(RepositoriLokal.allSales().filter {
            val text = (it.id + " " + it.source + " " + it.items.joinToString(" ") { item -> RepositoriLokal.getProduct(item.productId)?.name ?: "" }).lowercase()
            text.contains(query) && (source == "Semua" || it.source == source)
        }.map {
            ItemBaris(
                id = it.id,
                title = it.id,
                subtitle = it.source + " • " + it.items.joinToString(", ") { item -> (RepositoriLokal.getProduct(item.productId)?.name ?: "Produk") + " x" + item.qty },
                badge = it.paymentMethod,
                amount = Formatter.currency(it.total),
                actionLabel = "Hapus",
                tone = if (it.source == "KASIR") WarnaBaris.GOLD else WarnaBaris.BLUE
            )
        })
    }

    override fun onRowClick(item: ItemBaris) {
        showReceiptModal("Struk ${item.id}", RepositoriLokal.buildReceiptText(item.id))
    }

    override fun onRowAction(item: ItemBaris) {
        showConfirmationModal(
            title = "Hapus transaksi penjualan?",
            message = "Transaksi ${item.id} akan dihapus dan stok produk akan dikembalikan. Lanjutkan?"
        ) {
            runCatching { RepositoriLokal.deleteSale(item.id) }
                .onSuccess {
                    showMessage("Transaksi penjualan berhasil dihapus.")
                    refresh()
                }
                .onFailure { showMessage(it.message ?: "Gagal menghapus transaksi penjualan") }
        }
    }
}
