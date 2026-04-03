package muhamad.irfan.si_tahu.ui.history

import android.os.Bundle
import muhamad.irfan.si_tahu.data.RepositoriLokal
import muhamad.irfan.si_tahu.data.BarisTransaksi
import muhamad.irfan.si_tahu.ui.base.AktivitasDaftarDasar
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class AktivitasRiwayatTransaksi : AktivitasDaftarDasar() {
    private val typeOptions = listOf("Semua", "Penjualan", "Rekap Pasar", "Produksi", "Konversi", "Pengeluaran", "Adjustment")
    private var currentRows: List<BarisTransaksi> = emptyList()

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
        currentRows = RepositoriLokal.transactions().filter {
            (it.id + " " + it.subtitle).lowercase().contains(query) && (type == "Semua" || it.type == type)
        }
        submitRows(currentRows.map {
            ItemBaris(
                id = it.id,
                title = it.id,
                subtitle = it.type + " • " + it.subtitle,
                badge = it.type,
                amount = it.valueText,
                actionLabel = "Hapus",
                tone = when (it.type) {
                    "Produksi" -> WarnaBaris.GREEN
                    "Konversi" -> WarnaBaris.BLUE
                    "Pengeluaran" -> WarnaBaris.ORANGE
                    "Adjustment" -> WarnaBaris.ORANGE
                    else -> WarnaBaris.GOLD
                }
            )
        })
    }

    override fun onRowClick(item: ItemBaris) {
        val detail = currentRows.firstOrNull { it.id == item.id } ?: return
        if (detail.type == "Penjualan" || detail.type == "Rekap Pasar") {
            showReceiptModal("Struk ${detail.id}", RepositoriLokal.buildReceiptText(detail.id))
        } else {
            showDetailModal(detail.id, RepositoriLokal.buildTransactionDetailText(detail.id, detail.type))
        }
    }

    override fun onRowAction(item: ItemBaris) {
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
                    "Penjualan", "Rekap Pasar" -> RepositoriLokal.deleteSale(row.id)
                    "Produksi" -> RepositoriLokal.deleteProduction(row.id)
                    "Konversi" -> RepositoriLokal.deleteConversion(row.id)
                    "Pengeluaran" -> RepositoriLokal.deleteExpense(row.id)
                    else -> RepositoriLokal.deleteAdjustment(row.id)
                }
            }.onSuccess {
                showMessage("Data ${row.type.lowercase()} berhasil dihapus.")
                refresh()
            }.onFailure { showMessage(it.message ?: "Gagal menghapus ${row.type.lowercase()}") }
        }
    }
}
