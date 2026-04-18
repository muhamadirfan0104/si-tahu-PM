package muhamad.irfan.si_tahu.ui.penjualan

import android.content.Context
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

    private var judulLayar = "Riwayat Penjualan"
    private var subjudulLayar = "Rumahan dan pasar"
    private var defaultFilter = FILTER_SEMUA
    private var lockFilter = false
    private var tampilkanTombolTransaksiBaru = true
    private var tampilkanTombolRekapPasar = true
    private var izinkanHapus = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bacaIntent()
        configureScreen(judulLayar, subjudulLayar)

        if (lockFilter) {
            hidePrimaryFilter()
        } else {
            setPrimaryFilter(
                options = listOf(FILTER_SEMUA, FILTER_RUMAHAN, FILTER_PASAR),
                selectedIndex = selectedFilterIndex(defaultFilter)
            ) {
                halamanSaatIni = 1
                refresh()
            }
        }

        hideSecondaryFilter()

        if (tampilkanTombolTransaksiBaru) {
            setPrimaryButton("Transaksi Baru") {
                startActivity(Intent(this, AktivitasPenjualanRumahan::class.java))
            }
        } else {
            hidePrimaryButton()
        }

        if (tampilkanTombolRekapPasar) {
            setSecondaryButton("Rekap Pasar") {
                startActivity(Intent(this, AktivitasRekapPasar::class.java))
            }
        } else {
            hideSecondaryButton()
        }

        if (!tampilkanTombolTransaksiBaru && !tampilkanTombolRekapPasar) {
            hideButtons()
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

    private fun bacaIntent() {
        judulLayar = intent.getStringExtra(EXTRA_SCREEN_TITLE).orEmpty()
            .ifBlank { "Riwayat Penjualan" }

        subjudulLayar = intent.getStringExtra(EXTRA_SCREEN_SUBTITLE).orEmpty()
            .ifBlank { "Rumahan dan pasar" }

        defaultFilter = intent.getStringExtra(EXTRA_DEFAULT_FILTER).orEmpty()
            .ifBlank { FILTER_SEMUA }

        lockFilter = intent.getBooleanExtra(EXTRA_LOCK_FILTER, false)
        tampilkanTombolTransaksiBaru =
            intent.getBooleanExtra(EXTRA_SHOW_NEW_SALE_BUTTON, true)
        tampilkanTombolRekapPasar =
            intent.getBooleanExtra(EXTRA_SHOW_RECAP_BUTTON, true)
        izinkanHapus = intent.getBooleanExtra(EXTRA_ALLOW_DELETE, true)
    }

    private fun selectedFilterIndex(filter: String): Int {
        return when (filter) {
            FILTER_RUMAHAN -> 1
            FILTER_PASAR -> 2
            else -> 0
        }
    }

    private fun buildRows() {
        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatRiwayatPenjualan() }
                .onSuccess { sales ->
                    semuaRows = sales
                        .sortedByDescending { it.tanggalIso }
                        .map {
                            ItemBaris(
                                id = it.id,
                                title = it.title,
                                subtitle = it.subtitle,
                                amount = it.amount,
                                badge = it.badge,
                                tone = when (it.badge) {
                                    FILTER_RUMAHAN -> WarnaBaris.GREEN
                                    FILTER_PASAR -> WarnaBaris.BLUE
                                    else -> WarnaBaris.GOLD
                                },
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
        val filter = if (lockFilter) {
            defaultFilter
        } else {
            primarySelection().ifBlank { defaultFilter }
        }

        filteredRows = semuaRows.filter {
            val cocokFilter = filter == FILTER_SEMUA || it.badge == filter
            val cocokKeyword =
                keyword.isBlank() ||
                        it.title.lowercase().contains(keyword) ||
                        it.subtitle.lowercase().contains(keyword) ||
                        it.badge.lowercase().contains(keyword)

            cocokFilter && cocokKeyword
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
            if (izinkanHapus) {
                menu.add("Hapus")
            }

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

    companion object {
        private const val EXTRA_SCREEN_TITLE = "extra_screen_title"
        private const val EXTRA_SCREEN_SUBTITLE = "extra_screen_subtitle"
        private const val EXTRA_DEFAULT_FILTER = "extra_default_filter"
        private const val EXTRA_LOCK_FILTER = "extra_lock_filter"
        private const val EXTRA_SHOW_NEW_SALE_BUTTON = "extra_show_new_sale_button"
        private const val EXTRA_SHOW_RECAP_BUTTON = "extra_show_recap_button"
        private const val EXTRA_ALLOW_DELETE = "extra_allow_delete"

        private const val FILTER_SEMUA = "Semua"
        private const val FILTER_RUMAHAN = "Rumahan"
        private const val FILTER_PASAR = "Pasar"

        fun intentRiwayatRumahanAdmin(context: Context): Intent {
            return Intent(context, AktivitasRiwayatPenjualan::class.java)
                .putExtra(EXTRA_SCREEN_TITLE, "Riwayat Penjualan Rumahan")
                .putExtra(EXTRA_SCREEN_SUBTITLE, "Transaksi eceran yang sudah tersimpan")
                .putExtra(EXTRA_DEFAULT_FILTER, FILTER_RUMAHAN)
                .putExtra(EXTRA_LOCK_FILTER, true)
                .putExtra(EXTRA_SHOW_NEW_SALE_BUTTON, false)
                .putExtra(EXTRA_SHOW_RECAP_BUTTON, false)
                .putExtra(EXTRA_ALLOW_DELETE, false)
        }

        fun intentRiwayatSemuaAdmin(context: Context): Intent {
            return Intent(context, AktivitasRiwayatPenjualan::class.java)
                .putExtra(EXTRA_SCREEN_TITLE, "Riwayat Penjualan")
                .putExtra(EXTRA_SCREEN_SUBTITLE, "Rumahan dan pasar")
                .putExtra(EXTRA_DEFAULT_FILTER, FILTER_SEMUA)
                .putExtra(EXTRA_LOCK_FILTER, false)
                .putExtra(EXTRA_SHOW_NEW_SALE_BUTTON, false)
                .putExtra(EXTRA_SHOW_RECAP_BUTTON, false)
                .putExtra(EXTRA_ALLOW_DELETE, false)
        }

        fun intentRiwayatKasir(context: Context): Intent {
            return Intent(context, AktivitasRiwayatPenjualan::class.java)
                .putExtra(EXTRA_SCREEN_TITLE, "Riwayat Penjualan")
                .putExtra(EXTRA_SCREEN_SUBTITLE, "Transaksi rumahan kasir")
                .putExtra(EXTRA_DEFAULT_FILTER, FILTER_RUMAHAN)
                .putExtra(EXTRA_LOCK_FILTER, false)
                .putExtra(EXTRA_SHOW_NEW_SALE_BUTTON, true)
                .putExtra(EXTRA_SHOW_RECAP_BUTTON, false)
                .putExtra(EXTRA_ALLOW_DELETE, true)
        }
    }
}