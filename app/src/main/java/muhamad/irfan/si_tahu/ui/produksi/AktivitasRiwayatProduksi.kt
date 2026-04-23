package muhamad.irfan.si_tahu.ui.produksi

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDaftarDasar
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.PembantuPilihTanggalWaktu
import muhamad.irfan.si_tahu.util.WarnaBaris
import java.util.Calendar
import java.util.Date

class AktivitasRiwayatProduksi : AktivitasDaftarDasar() {

    private var rows: List<RiwayatProduksiUiRow> = emptyList()
    private var filteredRows: List<RiwayatProduksiUiRow> = emptyList()
    private var halamanSaatIni = 1
    private val itemPerHalaman = 5

    private var tanggalTunggal: String? = null
    private var rentangMulai: String? = null
    private var rentangSelesai: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreen("Riwayat Produksi", "Dasar dan produk olahan")

        hidePrimaryFilter()
        hideSecondaryFilter()

        setPrimaryButton("Pilih Tanggal") {
            bukaPilihTanggal()
        }

        setSecondaryButton("Pilih Rentang") {
            bukaPilihRentang()
        }

        binding.btnPrimary.setOnLongClickListener {
            resetFilterTanggal()
            true
        }

        binding.btnSecondary.setOnLongClickListener {
            resetFilterTanggal()
            true
        }

        updateFilterTanggalUi()
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
                                tone = if (it.badge == "Produk Olahan") {
                                    WarnaBaris.BLUE
                                } else {
                                    WarnaBaris.GREEN
                                },
                                priceTone = if (it.badge == "Produk Olahan") {
                                    WarnaBaris.BLUE
                                } else {
                                    WarnaBaris.GREEN
                                }
                            )
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

        filteredRows = rows.filter { row ->
            val item = row.item
            val cocokTanggal = cocokFilterTanggal(Formatter.parseDate(row.tanggalIso))
            val cocokKeyword =
                keyword.isBlank() ||
                        item.title.lowercase().contains(keyword) ||
                        item.subtitle.lowercase().contains(keyword) ||
                        item.badge.lowercase().contains(keyword)

            cocokTanggal && cocokKeyword
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
    }

    private fun bukaPilihTanggal() {
        PembantuPilihTanggalWaktu.showDatePicker(this, tanggalTunggal) { hasil ->
            tanggalTunggal = hasil
            rentangMulai = null
            rentangSelesai = null
            halamanSaatIni = 1
            updateFilterTanggalUi()
            refresh()
        }
    }

    private fun bukaPilihRentang() {
        PembantuPilihTanggalWaktu.showDatePicker(this, rentangMulai) { mulai ->
            PembantuPilihTanggalWaktu.showDatePicker(this, rentangSelesai ?: mulai) { selesai ->
                val tanggalMulai = Formatter.parseDate(mulai)
                val tanggalSelesai = Formatter.parseDate(selesai)

                if (tanggalMulai.after(tanggalSelesai)) {
                    rentangMulai = selesai
                    rentangSelesai = mulai
                } else {
                    rentangMulai = mulai
                    rentangSelesai = selesai
                }

                tanggalTunggal = null
                halamanSaatIni = 1
                updateFilterTanggalUi()
                refresh()
            }
        }
    }

    private fun resetFilterTanggal() {
        tanggalTunggal = null
        rentangMulai = null
        rentangSelesai = null
        halamanSaatIni = 1
        updateFilterTanggalUi()
        refresh()
        showMessage("Filter tanggal direset")
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

    private fun updateFilterTanggalUi() {
        binding.buttonRow.visibility = View.VISIBLE
        binding.btnPrimary.text = labelTombolTanggal()
        binding.btnSecondary.text = labelTombolRentang()
        binding.toolbar.subtitle = subtitleAktif()
    }

    private fun labelTombolTanggal(): String {
        return tanggalTunggal?.let { Formatter.readableShortDate(it) } ?: "Pilih Tanggal"
    }

    private fun labelTombolRentang(): String {
        return if (!rentangMulai.isNullOrBlank() && !rentangSelesai.isNullOrBlank()) {
            "${Formatter.readableShortDate(rentangMulai)} - ${Formatter.readableShortDate(rentangSelesai)}"
        } else {
            "Pilih Rentang"
        }
    }

    private fun subtitleAktif(): String {
        return when {
            !tanggalTunggal.isNullOrBlank() ->
                "Dasar dan produk olahan • ${Formatter.readableDate(tanggalTunggal)}"
            !rentangMulai.isNullOrBlank() && !rentangSelesai.isNullOrBlank() ->
                "Dasar dan produk olahan • ${Formatter.readableShortDate(rentangMulai)} - ${Formatter.readableShortDate(rentangSelesai)}"
            else ->
                "Dasar dan produk olahan • semua tanggal"
        }
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
}

data class RiwayatProduksiUiRow(
    val tanggalIso: String,
    val item: ItemBaris
)