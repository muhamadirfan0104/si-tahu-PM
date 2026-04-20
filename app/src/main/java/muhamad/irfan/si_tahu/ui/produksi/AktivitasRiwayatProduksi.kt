package muhamad.irfan.si_tahu.ui.produksi

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDaftarDasar
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class AktivitasRiwayatProduksi : AktivitasDaftarDasar() {

    private var rows: List<ItemBaris> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreen("Riwayat Produksi", "Dasar dan konversi")
        setPrimaryFilter(listOf("Semua", "Produksi Dasar", "Konversi")) { refresh() }
        hideSecondaryFilter()
    }

    override fun onResume() {
        super.onResume()
        buildRows()
    }

    override fun onSearchChanged() = refresh()

    private fun buildRows() {
        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatRiwayatProduksi() }
                .onSuccess { history ->
                    rows = history.map {
                        ItemBaris(
                            id = it.id,
                            title = it.title,
                            subtitle = it.subtitle,
                            badge = it.badge,
                            amount = it.amount,
                            tone = if (it.badge == "Konversi") WarnaBaris.BLUE else WarnaBaris.GREEN,
                            priceTone = if (it.badge == "Konversi") WarnaBaris.BLUE else WarnaBaris.GREEN
                        )
                    }
                    refresh()
                }
                .onFailure {
                    rows = emptyList()
                    submitRows(emptyList())
                    showMessage(it.message ?: "Gagal memuat riwayat produksi")
                }
        }
    }

    private fun refresh() {
        val keyword = searchText()
        val filter = primarySelection()
        val filtered = rows.filter {
            (filter == "Semua" || it.badge == filter) &&
                (keyword.isBlank() || it.title.lowercase().contains(keyword) || it.subtitle.lowercase().contains(keyword))
        }
        submitRows(filtered)
    }

    override fun onRowClick(item: ItemBaris) {
        lifecycleScope.launch {
            showDetailModal("Detail ${item.badge}", RepositoriFirebaseUtama.buildProductionDetailText(item.id))
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
                        sharePlainText("${item.badge} ${item.id}", RepositoriFirebaseUtama.buildProductionDetailText(item.id))
                    }
                    "Hapus" -> confirmDelete(item)
                }
                true
            }
        }.show()
    }

    private fun confirmDelete(item: ItemBaris) {
        showConfirmationModal("Hapus ${item.badge}", "Data ${item.title} akan dihapus dan stok akan disesuaikan kembali.") {
            lifecycleScope.launch {
                runCatching { RepositoriFirebaseUtama.hapusCatatanProduksi(item.id) }
                    .onSuccess {
                        buildRows()
                        showMessage("Data berhasil dihapus")
                    }
                    .onFailure {
                        showMessage(it.message ?: "Gagal menghapus data")
                    }
            }
        }
    }
}
