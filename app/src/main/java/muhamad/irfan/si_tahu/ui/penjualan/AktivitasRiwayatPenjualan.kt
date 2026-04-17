package muhamad.irfan.si_tahu.ui.penjualan

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDaftarDasar
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class AktivitasRiwayatPenjualan : AktivitasDaftarDasar() {

    private var rows: List<ItemBaris> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreen("Riwayat Penjualan", "Rumahan dan pasar")
        setPrimaryFilter(listOf("Semua", "Rumahan", "PASAR", "RESELLER")) { refresh() }
        hideSecondaryFilter()
        setPrimaryButton("Penjualan Rumahan") { startActivity(android.content.Intent(this, AktivitasPenjualanRumahan::class.java)) }
        setSecondaryButton("Rekap Pasar") { startActivity(android.content.Intent(this, AktivitasRekapPasar::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        buildRows()
    }

    override fun onSearchChanged() = refresh()

    private fun buildRows() {
        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatRiwayatPenjualan() }
                .onSuccess { sales ->
                    rows = sales.map {
                        ItemBaris(
                            id = it.id,
                            title = it.title,
                            subtitle = it.subtitle,
                            amount = it.amount,
                            badge = it.badge,
                            tone = if (it.badge == "Rumahan") WarnaBaris.GREEN else WarnaBaris.BLUE,
                            actionLabel = "⋮"
                        )
                    }
                    refresh()
                }
                .onFailure {
                    rows = emptyList()
                    submitRows(emptyList())
                    showMessage(it.message ?: "Gagal memuat riwayat penjualan")
                }
        }
    }

    private fun refresh() {
        val keyword = searchText()
        val filter = primarySelection()
        submitRows(rows.filter {
            (filter == "Semua" || it.badge == filter) &&
                (keyword.isBlank() || it.title.lowercase().contains(keyword) || it.subtitle.lowercase().contains(keyword))
        })
    }

    override fun onRowClick(item: ItemBaris) {
        lifecycleScope.launch {
            showReceiptModal("Detail Penjualan", RepositoriFirebaseUtama.buildReceiptText(item.id), "Bagikan")
        }
    }

    override fun onRowAction(item: ItemBaris, anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Lihat detail")
            menu.add("Bagikan")
            menu.add("Hapus")
            setOnMenuItemClickListener {
                when (it.title.toString()) {
                    "Lihat detail" -> onRowClick(item)
                    "Bagikan" -> lifecycleScope.launch {
                        sharePlainText("Penjualan ${item.id}", RepositoriFirebaseUtama.buildReceiptText(item.id))
                    }
                    "Hapus" -> confirmDelete(item)
                }
                true
            }
        }.show()
    }

    private fun confirmDelete(item: ItemBaris) {
        showConfirmationModal("Hapus transaksi", "Transaksi ${item.title} akan dihapus dan stok produk dikembalikan.") {
            lifecycleScope.launch {
                runCatching { RepositoriFirebaseUtama.hapusPenjualan(item.id) }
                    .onSuccess {
                        buildRows()
                        showMessage("Transaksi berhasil dihapus")
                    }
                    .onFailure { showMessage(it.message ?: "Gagal menghapus transaksi") }
            }
        }
    }
}
