package muhamad.irfan.si_tahu.ui.produksi

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDaftarDasar
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris
import muhamad.irfan.si_tahu.utilitas.PembantuFilterRiwayat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AktivitasRiwayatProduksi : AktivitasDaftarDasar() {

    private var rows: List<RiwayatProduksiUiRow> = emptyList()
    private var filteredRows: List<RiwayatProduksiUiRow> = emptyList()
    private var halamanSaatIni = 1
    private val itemPerHalaman = 5

    private var kategoriAktif = FILTER_SEMUA
    private var tanggalTunggal: String? = null
    private var rentangMulai: String? = null
    private var rentangSelesai: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreen(
            title = "",
            subtitle = null,
            searchHint = "Cari produksi..."
        )

        hidePrimaryFilter()
        hideSecondaryFilter()
        hideButtons()
        binding.cardDateFilter.visibility = View.GONE
        showFilterButton(View.OnClickListener { bukaBottomSheetFilter() })
        updateFilterUi()
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
                        RiwayatProduksiUiRow(
                            tanggalIso = it.tanggalIso,
                            item = ItemBaris(
                                id = it.id,
                                title = it.title,
                                subtitle = it.subtitle,
                                badge = it.badge,
                                amount = it.amount,
                                actionLabel = "⋮",
                                tone = if (it.badge.contains("Olahan", true)) {
                                    WarnaBaris.BLUE
                                } else {
                                    WarnaBaris.GREEN
                                },
                                priceTone = if (it.badge.contains("Olahan", true)) {
                                    WarnaBaris.BLUE
                                } else {
                                    WarnaBaris.GREEN
                                }
                            )
                        )
                    }
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

        filteredRows = rows.filter { row ->
            val item = row.item
            val cocokTanggal = cocokFilterTanggal(Formatter.parseDate(row.tanggalIso))
            val cocokKeyword =
                keyword.isBlank() ||
                        item.title.lowercase().contains(keyword) ||
                        item.subtitle.lowercase().contains(keyword) ||
                        item.badge.lowercase().contains(keyword)
            val cocokKategori = when (kategoriAktif) {
                FILTER_DASAR -> !item.badge.contains("Olahan", true)
                FILTER_OLAHAN -> item.badge.contains("Olahan", true)
                else -> true
            }

            cocokTanggal && cocokKeyword && cocokKategori
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
            filteredRows.subList(fromIndex, untilIndex).map { it.item }
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

        updateFilterUi()
    }

    private fun bukaBottomSheetFilter() {
        PembantuFilterRiwayat.show(
            activity = this,
            kategori = listOf(FILTER_SEMUA, FILTER_DASAR, FILTER_OLAHAN),
            kategoriTerpilih = kategoriAktif,
            tanggalLabel = labelDateRangeUntukField(),
            jumlahFilterAktif = jumlahFilterAktif(),
            onKategoriDipilih = {
                kategoriAktif = it
                halamanSaatIni = 1
                refresh()
            },
            onPilihTanggal = { bukaDateRangePicker() },
            onHapusTanggal = {
                clearDateFilter(showToast = true)
            },
            onReset = {
                resetSemuaFilter()
            }
        )
    }

    private fun bukaDateRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Pilih rentang")
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val start = selection.first ?: return@addOnPositiveButtonClickListener
            val end = selection.second ?: start
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val mulai = formatter.format(Date(start))
            val selesai = formatter.format(Date(end))

            if (isSameDay(Formatter.parseDate(mulai), Formatter.parseDate(selesai))) {
                tanggalTunggal = mulai
                rentangMulai = null
                rentangSelesai = null
            } else if (Formatter.parseDate(mulai).after(Formatter.parseDate(selesai))) {
                tanggalTunggal = null
                rentangMulai = selesai
                rentangSelesai = mulai
            } else {
                tanggalTunggal = null
                rentangMulai = mulai
                rentangSelesai = selesai
            }

            halamanSaatIni = 1
            refresh()
        }

        picker.show(supportFragmentManager, "filter_range_produksi")
    }

    private fun resetSemuaFilter() {
        kategoriAktif = FILTER_SEMUA
        clearDateFilter(showToast = false)
        halamanSaatIni = 1
        refresh()
        showMessage("Semua filter direset")
    }

    private fun clearDateFilter(showToast: Boolean) {
        tanggalTunggal = null
        rentangMulai = null
        rentangSelesai = null
        halamanSaatIni = 1
        refresh()
        if (showToast) {
            showMessage("Filter tanggal dihapus")
        }
    }

    private fun cocokFilterTanggal(tanggalData: Date): Boolean {
        tanggalTunggal?.let { single ->
            val target = Formatter.parseDate(single)
            return isSameDay(tanggalData, target)
        }

        if (!rentangMulai.isNullOrBlank() && !rentangSelesai.isNullOrBlank()) {
            val mulai = startOfDay(Formatter.parseDate(rentangMulai))
            val selesai = endOfDay(Formatter.parseDate(rentangSelesai))
            return !tanggalData.before(mulai) && !tanggalData.after(selesai)
        }

        return true
    }

    private fun updateFilterUi() {
        setFilterBadge(jumlahFilterAktif())
        binding.btnResetFilter.visibility = View.GONE
        binding.toolbar.subtitle = null
    }

    private fun jumlahFilterAktif(): Int {
        var total = 0
        if (kategoriAktif != FILTER_SEMUA) total++
        if (punyaFilterTanggal()) total++
        return total
    }

    private fun labelDateRangeUntukField(): String? {
        return when {
            !tanggalTunggal.isNullOrBlank() ->
                "${Formatter.readableDate(tanggalTunggal)}"
            !rentangMulai.isNullOrBlank() && !rentangSelesai.isNullOrBlank() ->
                "${Formatter.readableShortDate(rentangMulai)} - ${Formatter.readableShortDate(rentangSelesai)}"
            else ->
                null
        }
    }

    private fun punyaFilterTanggal(): Boolean {
        return !tanggalTunggal.isNullOrBlank() ||
                (!rentangMulai.isNullOrBlank() && !rentangSelesai.isNullOrBlank())
    }

    private fun isSameDay(first: Date, second: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = first }
        val cal2 = Calendar.getInstance().apply { time = second }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun startOfDay(date: Date): Date {
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    private fun endOfDay(date: Date): Date {
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time
    }

    override fun onRowClick(item: ItemBaris) {
        lifecycleScope.launch {
            showDetailModal(
                "Detail ${item.badge}",
                RepositoriFirebaseUtama.buildProductionDetailText(item.id)
            )
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
                        sharePlainText(
                            "${item.badge} ${item.id}",
                            RepositoriFirebaseUtama.buildProductionDetailText(item.id)
                        )
                    }
                    "Hapus" -> confirmDelete(item)
                }
                true
            }
        }.show()
    }

    private fun confirmDelete(item: ItemBaris) {
        showConfirmationModal(
            "Hapus ${item.badge}",
            "Data ${item.title} akan dihapus dan stok akan disesuaikan kembali."
        ) {
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

    companion object {
        private const val FILTER_SEMUA = "Semua"
        private const val FILTER_DASAR = "Dasar"
        private const val FILTER_OLAHAN = "Olahan"
    }
}

data class RiwayatProduksiUiRow(
    val tanggalIso: String,
    val item: ItemBaris
)
