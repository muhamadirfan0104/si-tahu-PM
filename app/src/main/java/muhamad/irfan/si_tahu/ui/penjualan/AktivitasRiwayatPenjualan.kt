package muhamad.irfan.si_tahu.ui.penjualan

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDaftarDasar
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris
import muhamad.irfan.si_tahu.util.PembuatQrBitmap
import muhamad.irfan.si_tahu.utilitas.PembantuFilterRiwayat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val XENDIT_API_BASE_RIWAYAT = "https://xendit-sitahu-api.vercel.app"

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

    private var kategoriAktif = UI_FILTER_SEMUA
    private var tanggalTunggal: String? = null
    private var rentangMulai: String? = null
    private var rentangSelesai: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bacaIntent()
        configureScreen(
            title = "",
            subtitle = null,
            searchHint = "Cari transaksi..."
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

        kategoriAktif = when (defaultFilter) {
            FILTER_RUMAHAN -> UI_FILTER_KASIR
            FILTER_PASAR -> UI_FILTER_REKAP
            else -> UI_FILTER_SEMUA
        }
    }

    private fun buildRows() {
        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatRiwayatPenjualan() }
                .onSuccess { sales ->
                    semuaRows = sales
                        .sortedByDescending { it.tanggalIso }
                        .map {
                            val statusLabel = when {
                                it.statusPenjualan.equals("BATAL", true) -> "Batal"
                                it.statusPenjualan.equals("PENDING", true) -> "Pending"
                                else -> "Selesai"
                            }

                            RiwayatPenjualanUiRow(
                                tanggalIso = it.tanggalIso,
                                item = ItemBaris(
                                    id = it.id,
                                    title = it.title,
                                    subtitle = it.subtitle,
                                    amount = it.amount,
                                    badge = it.badge,
                                    tone = when {
                                        it.badge.equals(FILTER_RUMAHAN, true) -> WarnaBaris.GREEN
                                        it.badge.equals(FILTER_PASAR, true) -> WarnaBaris.BLUE
                                        else -> WarnaBaris.GOLD
                                    },
                                    parameterStatus = statusLabel,
                                    parameterTone = when (statusLabel) {
                                        "Batal" -> WarnaBaris.RED
                                        "Pending" -> WarnaBaris.GOLD
                                        else -> WarnaBaris.GREEN
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
            val cocokKategori = when (kategoriAktif) {
                UI_FILTER_KASIR -> item.badge.equals(FILTER_RUMAHAN, true)
                UI_FILTER_REKAP -> item.badge.equals(FILTER_PASAR, true)
                else -> true
            }

            cocokTanggal && cocokKeyword && cocokKategori
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

        updateFilterUi()
    }

    private fun bukaBottomSheetFilter() {
        PembantuFilterRiwayat.show(
            activity = this,
            kategori = kategoriOpsi(),
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

    private fun kategoriOpsi(): List<String> {
        return if (lockFilter) {
            listOf(kategoriAktif)
        } else {
            listOf(UI_FILTER_SEMUA, UI_FILTER_KASIR, UI_FILTER_REKAP)
        }
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

        picker.show(supportFragmentManager, "filter_range_penjualan")
    }

    private fun resetSemuaFilter() {
        if (!lockFilter) {
            kategoriAktif = when (defaultFilter) {
                FILTER_RUMAHAN -> UI_FILTER_KASIR
                FILTER_PASAR -> UI_FILTER_REKAP
                else -> UI_FILTER_SEMUA
            }
        }
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

    private fun labelDateRangeUntukField(): String? {
        return when {
            !tanggalTunggal.isNullOrBlank() ->
                Formatter.readableDate(tanggalTunggal)
            !rentangMulai.isNullOrBlank() && !rentangSelesai.isNullOrBlank() ->
                "${Formatter.readableShortDate(rentangMulai)} - ${Formatter.readableShortDate(rentangSelesai)}"
            else ->
                null
        }
    }

    private fun jumlahFilterAktif(): Int {
        var total = 0
        if (kategoriAktif != UI_FILTER_SEMUA && !(lockFilter && kategoriOpsi().size == 1)) total++
        if (punyaFilterTanggal()) total++
        return total
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
        if (item.parameterStatus.equals("Pending", true)) {
            tampilkanQrisPending(item)
            return
        }
        tampilkanDetailPenjualan(item)
    }

    private fun tampilkanDetailPenjualan(item: ItemBaris) {
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
            if (item.parameterStatus.equals("Pending", true)) {
                menu.add("Lihat QRIS")
                menu.add("Cek status QRIS")
                menu.add("Batalkan QRIS")
            } else if (!item.parameterStatus.equals("Batal", true)) {
                menu.add("Batalkan penjualan")
            }
            if (izinkanHapus) {
                menu.add("Hapus")
            }

            setOnMenuItemClickListener {
                when (it.title.toString()) {
                    "Lihat detail" -> tampilkanDetailPenjualan(item)

                    "Lihat QRIS" -> tampilkanQrisPending(item)
                    "Bagikan" -> lifecycleScope.launch {
                        runCatching { RepositoriFirebaseUtama.buildReceiptText(item.id) }
                            .onSuccess { detail ->
                                sharePlainText("Penjualan ${item.title}", detail)
                            }
                            .onFailure {
                                showMessage(it.message ?: "Gagal membagikan detail penjualan")
                            }
                    }

                    "Cek status QRIS" -> cekStatusQrisPending(item)
                    "Batalkan QRIS" -> confirmCancel(item)
                    "Batalkan penjualan" -> confirmCancel(item)
                    "Hapus" -> confirmDelete(item)
                }
                true
            }
        }.show()
    }


    private fun tampilkanQrisPending(item: ItemBaris) {
        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatInfoQrisPending(item.id) }
                .onSuccess { info ->
                    if (!info.statusPenjualan.equals("PENDING", true)) {
                        showMessage("Transaksi ini tidak lagi pending")
                        buildRows()
                        return@onSuccess
                    }
                    if (info.paymentQrString.isBlank()) {
                        showMessage("QRIS transaksi lama belum menyimpan data QR. Batalkan lalu buat QRIS baru.")
                        return@onSuccess
                    }
                    tampilkanDialogQrisPending(item, info)
                }
                .onFailure {
                    showMessage(it.message ?: "Gagal memuat QRIS")
                }
        }
    }

    private fun tampilkanDialogQrisPending(item: ItemBaris, info: RepositoriFirebaseUtama.QrisPendingInfo) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 24, 32, 8)
        }

        val totalText = TextView(this).apply {
            text = Formatter.currency(info.totalBelanja)
            textSize = 24f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }

        val caption = TextView(this).apply {
            text = "Scan QRIS Xendit ini. Transaksi masih pending dan belum mengurangi stok."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 16)
        }

        val qrImage = ImageView(this).apply {
            setImageBitmap(PembuatQrBitmap.buat(info.paymentQrString, 1000))
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.WHITE)
            setPadding(12, 12, 12, 12)
        }

        val waktuText = TextView(this).apply {
            val sisa = if (info.paymentQrExpiresAtMillis > 0L) {
                info.paymentQrExpiresAtMillis - System.currentTimeMillis()
            } else {
                0L
            }
            text = buildString {
                append("Order: ${info.paymentOrderId}\n")
                append("QR ID: ${info.paymentQrId.ifBlank { "-" }}\n")
                append(if (sisa > 0L) "Sisa waktu: ${formatDurasiRiwayat(sisa)}" else "Status waktu: cek status / batalkan jika belum dibayar")
            }
            textSize = 12f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        container.addView(totalText)
        container.addView(caption)
        container.addView(qrImage)
        container.addView(waktuText)

        val scrollView = ScrollView(this).apply { addView(container) }

        MaterialAlertDialogBuilder(this)
            .setTitle("QRIS Pending")
            .setView(scrollView)
            .setPositiveButton("Cek Status") { _, _ -> cekStatusQrisPending(item) }
            .setNegativeButton("Tutup", null)
            .setNeutralButton("Batalkan") { _, _ -> confirmCancel(item) }
            .show()
    }

    private fun formatDurasiRiwayat(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(safeMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(safeMs) % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun cekStatusQrisPending(item: ItemBaris) {
        lifecycleScope.launch {
            runCatching {
                val info = RepositoriFirebaseUtama.muatInfoQrisPending(item.id)
                require(info.paymentOrderId.isNotBlank()) { "Order ID QRIS belum tercatat" }
                val status = bacaStatusXendit(info.paymentOrderId, info.totalBelanja)
                if (status.paid && status.status.equals("COMPLETED", ignoreCase = true)) {
                    val products = RepositoriFirebaseUtama.muatProdukKasir()
                    val saleId = RepositoriFirebaseUtama.selesaikanPenjualanQrisPending(
                        id = item.id,
                        userAuthId = currentUserId(),
                        products = products,
                        paymentStatus = status.status.uppercase(Locale.US),
                        paymentSource = status.source,
                        paymentReferenceId = status.receiptId.ifBlank { status.paymentId },
                        paymentPaidAt = status.paidAt,
                        paymentAmount = status.amount
                    )
                    RepositoriFirebaseUtama.buildReceiptText(saleId)
                } else {
                    null
                }
            }.onSuccess { receipt ->
                buildRows()
                if (receipt != null) {
                    showReceiptModal("QRIS sudah dibayar", receipt)
                } else {
                    showMessage("Pembayaran QRIS masih pending")
                }
            }.onFailure {
                showMessage(it.message ?: "Gagal cek status QRIS")
            }
        }
    }

    private suspend fun bacaStatusXendit(externalId: String, fallbackAmount: Long): StatusXenditGateway = withContext(Dispatchers.IO) {
        val response = postJsonRiwayat(
            path = "/api/cek-status-xendit",
            body = JSONObject().put("externalId", externalId)
        )
        val json = JSONObject(response)
        val payment = json.optJSONObject("payment")
        val details = payment?.optJSONObject("payment_details")
        StatusXenditGateway(
            paid = json.optBoolean("paid", false),
            status = json.optString("status", "PENDING"),
            paymentId = payment?.optString("id").orEmpty(),
            source = details?.optString("source").orEmpty(),
            receiptId = details?.optString("receipt_id").orEmpty(),
            paidAt = payment?.optString("created").orEmpty(),
            amount = payment?.optLong("amount", fallbackAmount) ?: fallbackAmount
        )
    }

    private suspend fun postJsonRiwayat(path: String, body: JSONObject): String = withContext(Dispatchers.IO) {
        val url = URL(XENDIT_API_BASE_RIWAYAT.trimEnd('/') + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val responseText = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            if (connection.responseCode !in 200..299) {
                val message = runCatching {
                    val json = JSONObject(responseText)
                    json.optString("message")
                        .ifBlank { json.optJSONObject("xendit")?.optString("message").orEmpty() }
                        .ifBlank { responseText }
                }.getOrElse { responseText }
                throw IllegalStateException(message)
            }
            responseText
        } finally {
            connection.disconnect()
        }
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

        private const val UI_FILTER_SEMUA = "Semua"
        private const val UI_FILTER_KASIR = "Kasir"
        private const val UI_FILTER_REKAP = "Rekap"

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

private data class StatusXenditGateway(
    val paid: Boolean,
    val status: String,
    val paymentId: String,
    val source: String,
    val receiptId: String,
    val paidAt: String,
    val amount: Long
)

data class RiwayatPenjualanUiRow(
    val tanggalIso: String,
    val item: ItemBaris
)
