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
    private var filteredRows: List<ItemBaris> = emptyList()
    private var halamanSaatIni = 1
    private val itemPerHalaman = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreen("Riwayat Produksi", "Dasar dan produk olahan")
        setPrimaryFilter(listOf("Semua", "Produksi Dasar", "Produk Olahan")) {
            halamanSaatIni = 1
            refresh()
        }
        hideSecondaryFilter()
    }

    override fun onResume() {
        super.onResume()
        buildRows()
    }

    override fun onSearchChanged() {
        halamanSaatIni = 1
        refresh()
    }

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
                            actionLabel = "⋮",
                            tone = if (it.badge == "Produk Olahan") WarnaBaris.BLUE else WarnaBaris.GREEN,
                            priceTone = if (it.badge == "Produk Olahan") WarnaBaris.BLUE else WarnaBaris.GREEN
                        )
                    }
                    filteredRows = rows
                    halamanSaatIni = 1
                    refresh()
                }
                .onFailure {
                    rows = emptyList()
                    filteredRows = emptyList()
                    halamanSaatIni = 1
                    hidePagination()
                    submitRows(emptyList(), "Riwayat produksi belum tersedia")
                    showMessage(it.message ?: "Gagal memuat riwayat produksi")
                }
        }
    }

    private fun refresh() {
        val keyword = searchText()
        val filter = primarySelection().ifBlank { "Semua" }

        filteredRows = rows.filter {
            (filter == "Semua" || it.badge == filter) &&
                (
                    keyword.isBlank() ||
                        it.title.lowercase().contains(keyword) ||
                        it.subtitle.lowercase().contains(keyword) ||
                        it.badge.lowercase().contains(keyword)
                    )
        }

        val totalPages = if (filteredRows.isEmpty()) {
            1
        } else {
            ((filteredRows.size - 1) / itemPerHalaman) + 1
        }

        if (halamanSaatIni > totalPages) halamanSaatIni = totalPages
        if (halamanSaatIni < 1) halamanSaatIni = 1

        val fromIndex = (halamanSaatIni - 1) * itemPerHalaman
        val untilIndex = minOf(fromIndex + itemPerHalaman, filteredRows.size)

        val currentPageRows = if (filteredRows.isEmpty()) {
            emptyList()
        } else {
            filteredRows.subList(fromIndex, untilIndex)
        }

        submitRows(
            currentPageRows,
            if (rows.isEmpty()) "Belum ada riwayat produksi" else "Tidak ada data yang cocok"
        )

        if (filteredRows.isEmpty()) {
            hidePagination()
        } else {
            showPagination(
                currentPage = halamanSaatIni,
                totalPages = totalPages,
                onPrev = if (halamanSaatIni > 1) {
                    {
                        halamanSaatIni--
                        refresh()
                    }
                } else {
                    null
                },
                onNext = if (halamanSaatIni < totalPages) {
                    {
                        halamanSaatIni++
                        refresh()
                    }
                } else {
                    null
                }
            )
        }
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
