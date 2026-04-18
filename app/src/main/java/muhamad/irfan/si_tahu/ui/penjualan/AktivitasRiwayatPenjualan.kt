package muhamad.irfan.si_tahu.ui.penjualan

import android.content.Intent
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

    private var semuaRows: List<ItemBaris> = emptyList()
    private var filteredRows: List<ItemBaris> = emptyList()

    private var halamanSaatIni = 1
    private val itemPerHalaman = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreen("Riwayat Penjualan", "Rumahan dan pasar")

        setPrimaryFilter(listOf("Semua", "Rumahan", "Pasar")) {
            halamanSaatIni = 1
            refresh()
        }
        hideSecondaryFilter()

        setPrimaryButton("Penjualan Rumahan") {
            startActivity(Intent(this, AktivitasPenjualanRumahan::class.java))
        }

        setSecondaryButton("Rekap Pasar") {
            startActivity(Intent(this, AktivitasRekapPasar::class.java))
        }
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
            runCatching { RepositoriFirebaseUtama.muatRiwayatPenjualan() }
                .onSuccess { sales ->
                    semuaRows = sales.map {
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
                    halamanSaatIni = 1
                    refresh()
                }
                .onFailure {
                    semuaRows = emptyList()
                    filteredRows = emptyList()
                    halamanSaatIni = 1
                    hidePagination()
                    submitRows(emptyList(), "Riwayat penjualan belum tersedia")
                    showMessage(it.message ?: "Gagal memuat riwayat penjualan")
                }
        }
    }

    private fun refresh() {
        val keyword = searchText()
        val filter = primarySelection()

        filteredRows = semuaRows.filter {
            (filter == "Semua" || it.badge == filter) &&
                    (
                            keyword.isBlank() ||
                                    it.title.lowercase().contains(keyword) ||
                                    it.subtitle.lowercase().contains(keyword) ||
                                    it.badge.lowercase().contains(keyword)
                            )
        }

        val totalPages =
            if (filteredRows.isEmpty()) 1 else ((filteredRows.size - 1) / itemPerHalaman) + 1

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
            if (semuaRows.isEmpty()) "Belum ada riwayat penjualan" else "Tidak ada data yang cocok"
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
            runCatching { RepositoriFirebaseUtama.buildReceiptText(item.id) }
                .onSuccess { detail ->
                    showReceiptModal("Detail Penjualan", detail, "Bagikan")
                }
                .onFailure {
                    showMessage(it.message ?: "Gagal memuat detail penjualan")
                }
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
                        runCatching { RepositoriFirebaseUtama.buildReceiptText(item.id) }
                            .onSuccess { detail ->
                                sharePlainText("Penjualan ${item.title}", detail)
                            }
                            .onFailure {
                                showMessage(it.message ?: "Gagal membagikan detail penjualan")
                            }
                    }
                    "Hapus" -> confirmDelete(item)
                }
                true
            }
        }.show()
    }

    private fun confirmDelete(item: ItemBaris) {
        showConfirmationModal(
            "Hapus transaksi",
            "Transaksi ${item.title} akan dihapus dan stok produk dikembalikan."
        ) {
            lifecycleScope.launch {
                runCatching { RepositoriFirebaseUtama.hapusPenjualan(item.id) }
                    .onSuccess {
                        buildRows()
                        showMessage("Transaksi berhasil dihapus")
                    }
                    .onFailure {
                        showMessage(it.message ?: "Gagal menghapus transaksi")
                    }
            }
        }
    }
}