package muhamad.irfan.si_tahu.ui.penjualan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDaftarDasar
import muhamad.irfan.si_tahu.ui.utama.AktivitasUtamaKasir
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.PembantuPilihTanggalWaktu
import muhamad.irfan.si_tahu.util.WarnaBaris
import java.util.Calendar
import java.util.Date

class AktivitasRiwayatPenjualan : AktivitasDaftarDasar() {

    private var semuaRows: List<RiwayatPenjualanUiRow> = emptyList()
    private var filteredRows: List<RiwayatPenjualanUiRow> = emptyList()

    private var halamanSaatIni = 1
    private val itemPerHalaman = 5

    private var judulLayar = "Riwayat Penjualan"
    private var subjudulLayar = "Rumahan dan pasar"
    private var defaultFilter = FILTER_SEMUA
    private var lockFilter = false
    private var tampilkanTombolTransaksiBaru = true
    private var tampilkanTombolRekapPasar = true
    private var izinkanHapus = true

    private var tanggalTunggal: String? = null
    private var rentangMulai: String? = null
    private var rentangSelesai: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bacaIntent()
        configureScreen(judulLayar, subjudulLayar)

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

        if (tampilkanTombolTransaksiBaru || tampilkanTombolRekapPasar) {
            // tombol filter tanggal tetap tampil
        }

        updateFilterTanggalUi()

        if (tampilkanTombolTransaksiBaru) {
            binding.btnPrimary.text = labelTombolTanggal()
            binding.btnPrimary.setOnClickListener { bukaPilihTanggal() }

            binding.btnSecondary.text = labelTombolRentang()
            binding.btnSecondary.setOnClickListener { bukaPilihRentang() }
        }

        if (tampilkanTombolTransaksiBaru && tampilkanTombolRekapPasar) {
            // riwayat kasir lama punya dua tombol aksi, sekarang kita prioritaskan filter tanggal
        }

        if (!tampilkanTombolTransaksiBaru && !tampilkanTombolRekapPasar) {
            // admin tetap pakai tombol filter tanggal
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
        judulLayar = intent.getStringExtra(EXTRA_SCREEN_TITLE)
            .orEmpty()
            .ifBlank { "Riwayat Penjualan" }

        subjudulLayar = intent.getStringExtra(EXTRA_SCREEN_SUBTITLE)
            .orEmpty()
            .ifBlank { "Rumahan dan pasar" }

        defaultFilter = intent.getStringExtra(EXTRA_DEFAULT_FILTER)
            .orEmpty()
            .ifBlank { FILTER_SEMUA }

        lockFilter = intent.getBooleanExtra(EXTRA_LOCK_FILTER, false)
        tampilkanTombolTransaksiBaru =
            intent.getBooleanExtra(EXTRA_SHOW_NEW_SALE_BUTTON, true)
        tampilkanTombolRekapPasar =
            intent.getBooleanExtra(EXTRA_SHOW_RECAP_BUTTON, true)
        izinkanHapus = intent.getBooleanExtra(EXTRA_ALLOW_DELETE, true)
    }

    private fun buildRows() {
        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatRiwayatPenjualan() }
                .onSuccess { sales ->
                    semuaRows = sales
                        .sortedByDescending { it.tanggalIso }
                        .filter { sale ->
                            when (defaultFilter) {
                                FILTER_RUMAHAN -> sale.badge.equals(FILTER_RUMAHAN, true)
                                FILTER_PASAR -> sale.badge.equals(FILTER_PASAR, true)
                                else -> true
                            }
                        }
                        .map {
                            val statusLabel = if (it.statusPenjualan.equals("BATAL", true)) {
                                "Batal"
                            } else {
                                "Selesai"
                            }

                            RiwayatPenjualanUiRow(
                                tanggalIso = it.tanggalIso,
                                item = ItemBaris(
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
                                    parameterStatus = statusLabel,
                                    parameterTone = if (statusLabel == "Batal") {
                                        WarnaBaris.RED
                                    } else {
                                        WarnaBaris.GREEN
                                    },
                                    actionLabel = "⋮"
                                )
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

        filteredRows = semuaRows.filter { row ->
            val item = row.item

            val cocokTanggal = cocokFilterTanggal(Formatter.parseDate(row.tanggalIso))
            val cocokKeyword =
                keyword.isBlank() ||
                        item.title.lowercase().contains(keyword) ||
                        item.subtitle.lowercase().contains(keyword) ||
                        item.badge.lowercase().contains(keyword) ||
                        item.parameterStatus.lowercase().contains(keyword)

            cocokTanggal && cocokKeyword
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
            filteredRows.subList(fromIndex, untilIndex).map { it.item }
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
                "$subjudulLayar • ${Formatter.readableDate(tanggalTunggal)}"
            !rentangMulai.isNullOrBlank() && !rentangSelesai.isNullOrBlank() ->
                "$subjudulLayar • ${Formatter.readableShortDate(rentangMulai)} - ${Formatter.readableShortDate(rentangSelesai)}"
            else ->
                "$subjudulLayar • semua tanggal"
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
            runCatching { RepositoriFirebaseUtama.buildReceiptText(item.id) }
                .onSuccess { detail ->
                    showReceiptModal("Detail Penjualan", detail)
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
            if (!item.parameterStatus.equals("Batal", true)) {
                menu.add("Batalkan penjualan")
            }
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

                    "Batalkan penjualan" -> confirmCancel(item)
                    "Hapus" -> confirmDelete(item)
                }
                true
            }
        }.show()
    }

    private fun confirmCancel(item: ItemBaris) {
        showInputModal(
            title = "Batalkan penjualan",
            hint = "Alasan pembatalan",
            confirmLabel = "Batalkan"
        ) { alasan ->
            lifecycleScope.launch {
                runCatching {
                    RepositoriFirebaseUtama.batalkanPenjualan(
                        item.id,
                        alasan,
                        currentUserId()
                    )
                }
                    .onSuccess {
                        buildRows()
                        showMessage("Penjualan berhasil dibatalkan")
                    }
                    .onFailure {
                        showMessage(it.message ?: "Gagal membatalkan penjualan")
                    }
            }
        }
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

data class RiwayatPenjualanUiRow(
    val tanggalIso: String,
    val item: ItemBaris
)