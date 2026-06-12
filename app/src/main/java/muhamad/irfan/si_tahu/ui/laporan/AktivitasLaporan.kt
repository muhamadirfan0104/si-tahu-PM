package muhamad.irfan.si_tahu.ui.laporan

import android.app.DatePickerDialog
import android.content.ContentValues
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.utama.SiTahuProTheme
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.utilitas.PengaturanUsahaCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

class AktivitasLaporan : AktivitasDasar() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        setContent {
            SiTahuProTheme {
                ReportDashboardScreen(
                    onNavigateBack = { finish() },
                    onShowMessage = { pesan -> showMessage(pesan) },
                    onExportBukuHarianExcel = { range, cb -> exportBukuHarianExcel(range, cb) },
                    onExportBukuHarianPdf = { range, cb -> exportBukuHarianPdf(range, cb) },
                    onExportStokExcel = { range, cb -> exportStokExcel(range, cb) },
                    onExportStokPdf = { range, cb -> exportStokPdf(range, cb) },
                    onExportMutasiExcel = { rangeLabel, rangeKey, jenisFilter, rows, saldoAwal, cb -> exportMutasiExcel(rangeLabel, rangeKey, jenisFilter, rows, saldoAwal, cb) },
                    onExportMutasiPdf = { rangeLabel, rangeKey, jenisFilter, rows, saldoAwal, cb -> exportMutasiPdf(rangeLabel, rangeKey, jenisFilter, rows, saldoAwal, cb) },
                    activityContext = this@AktivitasLaporan
                )
            }
        }
    }

    private fun safeRangeName(key: String): String = key.replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_').ifBlank { "periode" }

    private fun metaDariText(text: String, key: String): String {
        val prefix = "@@$key="
        return text.lineSequence().firstOrNull { it.startsWith(prefix) }?.substringAfter("=")?.trim().orEmpty()
    }

    private fun tanggalSekarangFile(): String = Formatter.currentDateOnly()

    private fun tambahHariTanggalFile(tanggal: String, jumlahHari: Int): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return runCatching {
            val cal = Calendar.getInstance().apply {
                time = formatter.parse(tanggal) ?: Date()
                add(Calendar.DAY_OF_MONTH, jumlahHari)
            }
            formatter.format(cal.time)
        }.getOrElse { tanggal }
    }

    private fun akhirBulanFile(yyyyMm: String): String {
        return runCatching {
            val parts = yyyyMm.split("-")
            val tahun = parts.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
            val bulan = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 12) ?: (Calendar.getInstance().get(Calendar.MONTH) + 1)
            val cal = Calendar.getInstance().apply {
                clear()
                set(tahun, bulan - 1, 1)
                add(Calendar.MONTH, 1)
                add(Calendar.DAY_OF_MONTH, -1)
            }
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        }.getOrElse { "$yyyyMm-01" }
    }

    private fun suffixFileRentangTanggal(rangeKey: String, fallbackLabel: String = ""): String {
        val raw = rangeKey.trim().lowercase(Locale.US)
        val today = tanggalSekarangFile()

        fun gabung(start: String, end: String): String {
            val awal = if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(start)) start else today
            val akhir = if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(end)) end else awal
            val ordered = if (awal <= akhir) awal to akhir else akhir to awal
            return if (ordered.first == ordered.second) ordered.first else "${ordered.first}_sampai_${ordered.second}"
        }

        return when {
            raw == "hari_ini" || raw == "today" || raw.contains("hari ini") -> today
            raw.startsWith("custom:") -> {
                val parts = raw.split(":")
                gabung(parts.getOrNull(1).orEmpty(), parts.getOrNull(2).orEmpty())
            }
            raw.startsWith("bulan:") -> {
                val ym = raw.removePrefix("bulan:").take(7)
                if (Regex("\\d{4}-\\d{2}").matches(ym)) gabung("$ym-01", akhirBulanFile(ym)) else today
            }
            raw == "semua" || raw == "all" || raw.contains("semua") -> "semua_data"
            raw.toIntOrNull() != null -> {
                val jumlahHari = raw.toInt().coerceAtLeast(1)
                val start = tambahHariTanggalFile(today, -(jumlahHari - 1))
                gabung(start, today)
            }
            else -> safeRangeName(fallbackLabel.ifBlank { rangeKey })
        }
    }

    private fun namaFilePeriode(text: String, fallbackRangeKey: String): String {
        val periode = metaDariText(text, "PERIODE")
        return suffixFileRentangTanggal(fallbackRangeKey, periode)
    }

    private fun exportBukuHarianExcel(rangeKey: String, setLoading: (Boolean) -> Unit) {
        lifecycleScope.launch {
            setLoading(true)
            runCatching {
                val text = RepositoriFirebaseUtama.buildBukuHarianPdfText(rangeKey)
                val periodeFile = namaFilePeriode(text, rangeKey)
                val bytes = buildBukuHarianXlsxFromText(text)
                saveBytesToDownloads("buku_harian_sitahu_${periodeFile}.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes)
            }.onSuccess {
                setLoading(false)
            }.onFailure {
                setLoading(false)
                showMessage(it.message ?: "Gagal membuat buku harian Excel")
            }
        }
    }

    private fun exportBukuHarianPdf(rangeKey: String, setLoading: (Boolean) -> Unit) {
        lifecycleScope.launch {
            setLoading(true)
            runCatching {
                val text = RepositoriFirebaseUtama.buildBukuHarianPdfText(rangeKey)
                val periodeFile = namaFilePeriode(text, rangeKey)
                savePdfTextToDownloads("Buku Harian SI Tahu", "buku_harian_sitahu_${periodeFile}.pdf", text)
            }.onSuccess {
                setLoading(false)
            }.onFailure {
                setLoading(false)
                showMessage(it.message ?: "Gagal membuat buku harian PDF")
            }
        }
    }

    private fun exportStokExcel(rangeKey: String, setLoading: (Boolean) -> Unit) {
        lifecycleScope.launch {
            setLoading(true)
            runCatching {
                val text = RepositoriFirebaseUtama.buildStokProdukPdfText(rangeKey)
                val bytes = buildStokProdukXlsxFromText(text)
                saveBytesToDownloads("stok_produk_sitahu_${suffixFileRentangTanggal(rangeKey)}.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes)
            }.onSuccess {
                setLoading(false)
            }.onFailure {
                setLoading(false)
                showMessage(it.message ?: "Gagal membuat stok produk Excel")
            }
        }
    }

    private fun exportStokPdf(rangeKey: String, setLoading: (Boolean) -> Unit) {
        lifecycleScope.launch {
            setLoading(true)
            runCatching {
                val text = RepositoriFirebaseUtama.buildStokProdukPdfText(rangeKey)
                savePdfTextToDownloads("Unduh Stok Produk PDF", "stok_produk_sitahu_${suffixFileRentangTanggal(rangeKey)}.pdf", text)
            }.onSuccess {
                setLoading(false)
            }.onFailure {
                setLoading(false)
                showMessage(it.message ?: "Gagal membuat stok produk PDF")
            }
        }
    }

    private fun exportMutasiExcel(
        rangeLabel: String,
        rangeKey: String,
        jenisFilter: String,
        rows: List<RepositoriFirebaseUtama.BarisRiwayatTransaksi>,
        saldoAwal: Long,
        setLoading: (Boolean) -> Unit
    ) {
        lifecycleScope.launch {
            setLoading(true)
            runCatching {
                val identitas = PengaturanUsahaCache.baca(this@AktivitasLaporan)
                val tipe = tipeLaporanNormal(jenisFilter)
                val bytes = if (tipe == REPORT_PRODUKSI) {
                    val text = RepositoriFirebaseUtama.buildStokProdukPdfText(rangeKey)
                    val (_, products) = parseStokProduk(text)
                    buildLaporanProduksiXlsx(identitas, rangeLabel, products)
                } else {
                    buildXlsxWorkbook(listOf(tipe to dataSheetUntukLaporan(identitas, rangeLabel, jenisFilter, rows, saldoAwal)))
                }
                val prefix = namaFileLaporanPrefix(jenisFilter)
                val suffix = suffixFileRentangTanggal(rangeKey, rangeLabel)
                saveBytesToDownloads(
                    "${prefix}_sitahu_${suffix}.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    bytes
                )
            }.onSuccess {
                setLoading(false)
            }.onFailure {
                setLoading(false)
                showMessage(it.message ?: "Gagal membuat file Excel laporan")
            }
        }
    }

    private fun exportMutasiPdf(
        rangeLabel: String,
        rangeKey: String,
        jenisFilter: String,
        rows: List<RepositoriFirebaseUtama.BarisRiwayatTransaksi>,
        saldoAwal: Long,
        setLoading: (Boolean) -> Unit
    ) {
        lifecycleScope.launch {
            setLoading(true)
            runCatching {
                val identitas = PengaturanUsahaCache.baca(this@AktivitasLaporan)
                val tipe = tipeLaporanNormal(jenisFilter)
                val pdf = if (tipe == REPORT_PRODUKSI) {
                    val text = RepositoriFirebaseUtama.buildStokProdukPdfText(rangeKey)
                    val (_, products) = parseStokProduk(text)
                    buildLaporanProduksiPdf(identitas, rangeLabel, products)
                } else {
                    buildPdfUntukLaporan(identitas, rangeLabel, jenisFilter, rows, saldoAwal)
                }
                val bytes = ByteArrayOutputStream().use { output ->
                    pdf.writeTo(output)
                    pdf.close()
                    output.toByteArray()
                }
                val prefix = namaFileLaporanPrefix(jenisFilter)
                val suffix = suffixFileRentangTanggal(rangeKey, rangeLabel)
                saveBytesToDownloads("${prefix}_sitahu_${suffix}.pdf", "application/pdf", bytes)
            }.onSuccess {
                setLoading(false)
            }.onFailure {
                setLoading(false)
                showMessage(it.message ?: "Gagal membuat file PDF laporan")
            }
        }
    }

    private fun savePdfTextToDownloads(title: String, fileName: String, text: String) {
        val pdf = when {
            text.lineSequence().any { it.trim() == "@@TYPE=BUKU_HARIAN" } -> buildBukuHarianPdf(title, text)
            text.lineSequence().any { it.trim() == "@@TYPE=STOK_PRODUK" } -> buildStokProdukPdf(title, text)
            else -> buildPlainTextPdf(title, text)
        }
        val bytes = ByteArrayOutputStream().use { output ->
            pdf.writeTo(output); pdf.close(); output.toByteArray()
        }
        saveBytesToDownloads(fileName, "application/pdf", bytes)
    }

    private fun saveBytesToDownloads(fileName: String, mimeType: String, bytes: ByteArray) {
        val folderName = "SI Tahu/Laporan"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$folderName")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Gagal membuat file")
            try {
                resolver.openOutputStream(uri)?.use { output -> output.write(bytes) }
                values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                showMessage("Tersimpan di Unduh/$folderName: $fileName")
            } catch (e: Exception) {
                resolver.delete(uri, null, null); throw e
            }
        } else {
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), folderName)
            if (!directory.exists()) directory.mkdirs()
            val file = File(directory, fileName)
            FileOutputStream(file).use { output -> output.write(bytes) }
            MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf(mimeType), null)
            showMessage("Tersimpan di Unduh/$folderName: $fileName")
        }
    }
}

// === MODEL STATISTIK & EXPORT ===
private data class StatistikUser(val nama: String, val transaksi: Int, val nominalPenjualan: Long, val aktivitas: Int)
private data class StatistikKategori(val nama: String, val jumlah: Int, val nominal: Long = 0L)
private data class StatistikHari(val label: String, val pemasukan: Long, val pengeluaran: Long, val transaksi: Int)
private data class StatistikIndikator(val title: String, val value: String, val note: String, val tone: String = "normal")
private data class AnalisisAiStatistik(val skor: Int, val status: String, val ringkasan: String, val prioritas: List<String>, val peluang: List<String>)
private data class DataStatistikLengkap(
    val laporan: RepositoriFirebaseUtama.RingkasanLaporanFirebase, val stok: RepositoriFirebaseUtama.RingkasanStokDashboard,
    val riwayat: List<RepositoriFirebaseUtama.BarisRiwayatTransaksi>, val aktivitasUser: List<StatistikUser>,
    val komposisiJenis: List<StatistikKategori>, val komposisiKanal: List<StatistikKategori>,
    val trenHarian: List<StatistikHari>, val indikatorDetail: List<StatistikIndikator>,
    val analisisAi: AnalisisAiStatistik, val insight: List<String>, val rekomendasi: List<String>
)
private data class PdfBukuRow(val tanggal: String, val uraian: String, val user: String, val debit: String, val kredit: String, val saldo: String)
private data class PdfStokRow(val tanggal: String, val uraian: String, val user: String, val masuk: String, val keluar: String, val saldo: String, val catatan: String)
private data class PdfStokProduk(val nama: String, val kodeKategori: String, val stokSaatIni: String, val stokLayak: String, val rincianEd: String, val mutasi: List<PdfStokRow>)
private data class PreviewLaporanRequest(val jenis: String, val format: String, val judul: String, val rangeLabel: String, val rangeKey: String)
private data class PreviewRowsLaporan(val metaLabel: String, val headers: List<String>, val rows: List<List<String>>, val totalRows: Int)
private data class DataMuatLaporan(val report: RepositoriFirebaseUtama.RingkasanLaporanFirebase, val rows: List<RepositoriFirebaseUtama.BarisRiwayatTransaksi>, val allRows: List<RepositoriFirebaseUtama.BarisRiwayatTransaksi>, val products: List<PdfStokProduk>)

private const val REPORT_KEUANGAN = "Laporan Keuangan"
private const val REPORT_PRODUKSI = "Laporan Produksi"
private const val STATUS_LAPORAN_SEMUA = "semua"
private const val STATUS_LAPORAN_PENDING = "pending"
private const val STATUS_LAPORAN_SELESAI = "selesai"
private const val STATUS_LAPORAN_BATAL_GAGAL = "batal_gagal"

private data class HeaderKolomLaporan(val label: String, val sublabel: String = "")
private data class BarisPreviewKeuangan(
    val tanggal: String,
    val uraian: String,
    val teller: String,
    val debit: String,
    val kredit: String,
    val saldo: String
)
private data class BarisPreviewProduksiMutasi(
    val tanggal: String,
    val produk: String,
    val uraian: String,
    val user: String,
    val masuk: String,
    val keluar: String,
    val stok: String
)

private fun tipeLaporanNormal(value: String): String = when {
    value.equals(REPORT_PRODUKSI, true) -> REPORT_PRODUKSI
    else -> REPORT_KEUANGAN
}

private fun cocokStatusLaporan(
    row: RepositoriFirebaseUtama.BarisRiwayatTransaksi,
    statusKey: String
): Boolean {
    val status = row.status.trim().lowercase(Locale("id", "ID"))
    val badge = row.badge.trim().lowercase(Locale("id", "ID"))
    val gabungan = "$status $badge"

    return when (statusKey) {
        STATUS_LAPORAN_PENDING -> {
            row.jenis.equals("Penjualan", true) &&
                    (
                            gabungan.contains("pending") ||
                                    gabungan.contains("belum terbayar") ||
                                    gabungan.contains("belum dibayar") ||
                                    gabungan.contains("menunggu")
                            )
        }

        STATUS_LAPORAN_SELESAI -> {
            gabungan.contains("selesai") ||
                    gabungan.contains("paid") ||
                    gabungan.contains("lunas") ||
                    gabungan.contains("tercatat")
        }

        STATUS_LAPORAN_BATAL_GAGAL -> {
            gabungan.contains("batal") ||
                    gabungan.contains("dibatalkan") ||
                    gabungan.contains("cancel") ||
                    gabungan.contains("cancelled") ||
                    gabungan.contains("gagal") ||
                    gabungan.contains("tidak terbayar")
        }

        else -> true
    }
}

private fun awalPeriode(rangeKey: String): Date? {
    val normalized = rangeKey.trim().lowercase(Locale.US)
    val dateOnly = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return runCatching {
        when {
            normalized == "semua" -> null
            normalized == "hari_ini" -> dateOnly.parse(dateOnly.format(Date()))
            normalized.startsWith("custom:") -> dateOnly.parse(normalized.split(":").getOrNull(1).orEmpty())
            normalized.startsWith("bulan:") -> dateOnly.parse("${normalized.removePrefix("bulan:")}-01")
            normalized.toIntOrNull() != null -> {
                val days = normalized.toIntOrNull()?.coerceAtLeast(1) ?: 1
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, -(days - 1))
                }.time
            }
            else -> null
        }
    }.getOrNull()
}

private fun nominalArusKas(row: RepositoriFirebaseUtama.BarisRiwayatTransaksi): Long {
    val nominal = kotlin.math.abs(Formatter.parseRupiah(row.amount))
    return when {
        row.jenis.equals("Penjualan", true) && !row.status.equals("Batal", true) -> nominal
        row.jenis.equals("Pengeluaran", true) -> -nominal
        else -> 0L
    }
}

private fun hitungSaldoSebelumPeriode(rowsSemua: List<RepositoriFirebaseUtama.BarisRiwayatTransaksi>, rangeKey: String): Long {
    val start = awalPeriode(rangeKey) ?: return 0L
    return rowsSemua
        .filter { row ->
            val tanggal = Formatter.parseDate(row.tanggalIso)
            tanggal.before(start) && (row.jenis.equals("Penjualan", true) || row.jenis.equals("Pengeluaran", true))
        }
        .sumOf { nominalArusKas(it) }
}

private fun buildPreviewKeuanganRows(
    rows: List<RepositoriFirebaseUtama.BarisRiwayatTransaksi>,
    saldoAwal: Long = 0L,
    tampilkanSaldoAwal: Boolean = true
): List<BarisPreviewKeuangan> {
    val transaksiKeuangan = rows
        .filter { it.jenis.equals("Penjualan", true) || it.jenis.equals("Pengeluaran", true) }
        .sortedBy { Formatter.parseDate(it.tanggalIso) }

    val hasil = mutableListOf<BarisPreviewKeuangan>()
    var saldo = saldoAwal
    if (tampilkanSaldoAwal) {
        hasil += BarisPreviewKeuangan(
            tanggal = "Sebelum periode",
            uraian = "Saldo awal sebelum periode laporan",
            teller = "-",
            debit = Formatter.currency(0L),
            kredit = Formatter.currency(0L),
            saldo = Formatter.currency(saldoAwal)
        )
    }

    transaksiKeuangan.forEach { row ->
        val nominal = kotlin.math.abs(Formatter.parseRupiah(row.amount))
        val debit = if (row.jenis.equals("Pengeluaran", true)) nominal else 0L
        val kredit = if (row.jenis.equals("Penjualan", true) && !row.status.equals("Batal", true)) nominal else 0L
        saldo += kredit - debit
        val uraian = when {
            row.jenis.equals("Penjualan", true) -> buildString {
                append("Penjualan ")
                append(row.title.ifBlank { "Transaksi" })
                if (row.badge.isNotBlank()) append(" • ").append(row.badge)
                if (row.status.isNotBlank()) append(" • ").append(row.status)
            }
            else -> buildString {
                append("Pengeluaran ")
                append(row.title.ifBlank { "Operasional" })
                if (row.status.isNotBlank()) append(" • ").append(row.status)
            }
        }
        hasil += BarisPreviewKeuangan(
            tanggal = Formatter.readableDateTime(row.tanggalIso),
            uraian = uraian,
            teller = row.userId.ifBlank { row.userName }.ifBlank { "-" },
            debit = Formatter.currency(debit),
            kredit = Formatter.currency(kredit),
            saldo = Formatter.currency(saldo)
        )
    }
    return hasil
}

private fun stokAwalProduk(p: PdfStokProduk): Long {
    val first = p.mutasi.firstOrNull()
    if (first != null) {
        return angkaPertama(first.saldo) - angkaPertama(first.masuk) + angkaPertama(first.keluar)
    }
    return angkaPertama(p.stokSaatIni)
}

private fun buildPreviewProduksiMutasiRows(products: List<PdfStokProduk>): List<BarisPreviewProduksiMutasi> {
    val pembuka = products.map { p ->
        BarisPreviewProduksiMutasi(
            tanggal = "Sebelum periode",
            produk = p.nama.ifBlank { "Produk" },
            uraian = "Stok awal sebelum periode laporan",
            user = "-",
            masuk = "0",
            keluar = "0",
            stok = Formatter.ribuan(stokAwalProduk(p))
        )
    }
    val mutasi = products.flatMap { p ->
        p.mutasi.map { row ->
            BarisPreviewProduksiMutasi(
                tanggal = row.tanggal,
                produk = p.nama.ifBlank { "Produk" },
                uraian = row.uraian.ifBlank { "Mutasi stok" },
                user = row.user.ifBlank { "Pengguna" },
                masuk = row.masuk.ifBlank { "0" },
                keluar = row.keluar.ifBlank { "0" },
                stok = row.saldo.ifBlank { "0" }
            )
        }
    }.sortedBy { Formatter.parseDate(it.tanggal) }
    return pembuka + mutasi
}

private fun buildPreviewProduksiMutasiRowsPerProduk(p: PdfStokProduk): List<BarisPreviewProduksiMutasi> {
    val rows = mutableListOf<BarisPreviewProduksiMutasi>()
    rows += BarisPreviewProduksiMutasi(
        tanggal = "Sebelum periode",
        produk = p.nama.ifBlank { "Produk" },
        uraian = "Stok awal sebelum periode laporan",
        user = "-",
        masuk = "0",
        keluar = "0",
        stok = Formatter.ribuan(stokAwalProduk(p))
    )
    p.mutasi.forEach { row ->
        rows += BarisPreviewProduksiMutasi(
            tanggal = row.tanggal,
            produk = p.nama.ifBlank { "Produk" },
            uraian = row.uraian.ifBlank { "Mutasi stok" },
            user = row.user.ifBlank { "Pengguna" },
            masuk = row.masuk.ifBlank { "0" },
            keluar = row.keluar.ifBlank { "0" },
            stok = row.saldo.ifBlank { "0" }
        )
    }
    return rows
}

private fun headerKolomUntukLaporan(tipe: String): List<HeaderKolomLaporan> = when (tipeLaporanNormal(tipe)) {
    REPORT_PRODUKSI -> listOf(
        HeaderKolomLaporan("Tanggal Mutasi", "Mutation Date"),
        HeaderKolomLaporan("Produk", "Product"),
        HeaderKolomLaporan("Uraian Mutasi", "Mutation Description"),
        HeaderKolomLaporan("User", "User"),
        HeaderKolomLaporan("Masuk", "In"),
        HeaderKolomLaporan("Keluar", "Out"),
        HeaderKolomLaporan("Stok", "Stock")
    )
    else -> listOf(
        HeaderKolomLaporan("Tanggal Transaksi", "Transaction Date"),
        HeaderKolomLaporan("Uraian Transaksi", "Transaction Description"),
        HeaderKolomLaporan("Teller", "User ID"),
        HeaderKolomLaporan("Debet", "Debit"),
        HeaderKolomLaporan("Kredit", "Credit"),
        HeaderKolomLaporan("Saldo", "Balance")
    )
}

private fun deskripsiRingkasLaporan(tipe: String): String = when (tipeLaporanNormal(tipe)) {
    REPORT_PRODUKSI -> "Mutasi stok produksi sesuai periode."
    else -> "Ringkasan transaksi dan saldo sesuai periode."
}

private fun namaFileLaporanPrefix(tipe: String): String = when (tipeLaporanNormal(tipe)) {
    REPORT_PRODUKSI -> "laporan_produksi"
    else -> "laporan_keuangan"
}

private fun headerMetadataSheet(
    identitas: PengaturanUsahaCache.IdentitasUsaha,
    judul: String,
    rangeLabel: String,
    dataDitampilkan: String
): MutableList<List<String>> {
    val namaUsaha = identitas.namaUsaha.ifBlank { "SI Tahu" }
    val rows = mutableListOf<List<String>>()
    rows += listOf(judul.uppercase(Locale.US))
    rows += listOf("Nama Usaha", namaUsaha)
    if (identitas.alamat.isNotBlank()) rows += listOf("Alamat", identitas.alamat)
    if (identitas.nomorTelepon.isNotBlank()) rows += listOf("Telepon", identitas.nomorTelepon)
    rows += listOf("Periode", rangeLabel)
    rows += listOf("Data Ditampilkan", dataDitampilkan)
    rows += listOf("Tanggal Cetak", SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date()))
    rows += emptyList<String>()
    return rows
}

private fun buildLaporanProduksiXlsx(
    identitas: PengaturanUsahaCache.IdentitasUsaha,
    rangeLabel: String,
    products: List<PdfStokProduk>
): ByteArray {
    val sheets = mutableListOf<Pair<String, List<List<String>>>>()
    val used = mutableSetOf<String>()

    val semuaRows = headerMetadataSheet(
        identitas,
        "Laporan Produksi - Mutasi Stok Semua Produk",
        rangeLabel,
        "Mutasi stok semua produk"
    )
    semuaRows += listOf("Tanggal Mutasi", "Produk", "Uraian Mutasi", "User", "Masuk", "Keluar", "Stok")
    val semuaMutasi = buildPreviewProduksiMutasiRows(products)
    if (semuaMutasi.isEmpty()) {
        semuaRows += listOf("-", "-", "Belum ada produk atau mutasi stok pada periode ini", "-", "-", "-", "-")
    } else {
        semuaMutasi.forEach { r -> semuaRows += listOf(r.tanggal, r.produk, r.uraian, r.user, r.masuk, r.keluar, r.stok) }
    }
    sheets += uniqueXlsxSheetName("Semua Produk", "Semua Produk", used) to semuaRows

    products.forEachIndexed { index, product ->
        val detailRows = headerMetadataSheet(
            identitas,
            "Laporan Produksi - Detail Mutasi Produk",
            rangeLabel,
            "Detail mutasi stok per produk"
        )
        detailRows += listOf("Produk", product.nama)
        if (product.kodeKategori.isNotBlank()) detailRows += listOf("Kode/Kategori", product.kodeKategori)
        if (product.stokSaatIni.isNotBlank()) detailRows += listOf("Stok Saat Ini", product.stokSaatIni)
        if (product.stokLayak.isNotBlank()) detailRows += listOf("Stok Layak", product.stokLayak)
        if (product.rincianEd.isNotBlank()) detailRows += listOf("Rincian ED", product.rincianEd)
        detailRows += listOf("Stok Awal Periode", Formatter.ribuan(stokAwalProduk(product)))
        detailRows += emptyList<String>()
        detailRows += listOf("Tanggal Mutasi", "Produk", "Uraian Mutasi", "User", "Masuk", "Keluar", "Stok")
        val detailMutasi = buildPreviewProduksiMutasiRowsPerProduk(product)
        detailMutasi.forEach { r -> detailRows += listOf(r.tanggal, r.produk, r.uraian, r.user, r.masuk, r.keluar, r.stok) }
        sheets += uniqueXlsxSheetName(product.nama, "Produk ${index + 1}", used) to detailRows
    }

    return buildXlsxWorkbook(sheets)
}

private fun buildLaporanProduksiPdf(
    identitas: PengaturanUsahaCache.IdentitasUsaha,
    rangeLabel: String,
    products: List<PdfStokProduk>
): PdfDocument {
    val namaUsaha = identitas.namaUsaha.ifBlank { "SI Tahu" }
    val columns = floatArrayOf(94f, 112f, 220f, 78f, 68f, 68f, 80f)
    val headers = listOf("Tanggal Mutasi", "Produk", "Uraian Mutasi", "User", "Masuk", "Keluar", "Stok")
    val align = listOf(0, 0, 0, 0, 1, 1, 1)

    fun drawHeader(state: PdfPageState, title: String, extra: List<Pair<String, String>> = emptyList()) {
        state.drawTitle(namaUsaha.uppercase(Locale.US))
        if (identitas.alamat.isNotBlank()) state.drawMeta("Alamat", identitas.alamat)
        if (identitas.nomorTelepon.isNotBlank()) state.drawMeta("Telepon", identitas.nomorTelepon)
        state.drawTitle(title.uppercase(Locale.US))
        state.drawMeta("Periode", rangeLabel)
        extra.forEach { (label, value) -> if (value.isNotBlank()) state.drawMeta(label, value) }
        state.drawMeta("Tanggal Cetak", SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date()))
        state.space(8f)
        state.drawTableHeader(headers, columns, align)
    }

    return createLandscapePdf("Laporan Produksi") { state ->
        drawHeader(state, "Laporan Produksi - Mutasi Stok Semua Produk")
        val semuaRows = buildPreviewProduksiMutasiRows(products)
        if (semuaRows.isEmpty()) {
            state.drawTableRow(listOf("-", "-", "Belum ada produk atau mutasi stok pada periode ini", "-", "-", "-", "-"), columns, align) { state.drawTableHeader(headers, columns, align) }
        } else {
            semuaRows.forEach { r ->
                state.drawTableRow(listOf(r.tanggal, r.produk, r.uraian, r.user, r.masuk, r.keluar, r.stok), columns, align) { state.drawTableHeader(headers, columns, align) }
            }
        }

        products.forEach { product ->
            state.newPage()
            drawHeader(
                state,
                "Detail Mutasi Produk",
                listOf(
                    "Produk" to product.nama,
                    "Kode/Kategori" to product.kodeKategori,
                    "Stok Awal Periode" to Formatter.ribuan(stokAwalProduk(product)),
                    "Stok Saat Ini" to product.stokSaatIni,
                    "Stok Layak" to product.stokLayak
                )
            )
            buildPreviewProduksiMutasiRowsPerProduk(product).forEach { r ->
                state.drawTableRow(listOf(r.tanggal, r.produk, r.uraian, r.user, r.masuk, r.keluar, r.stok), columns, align) { state.drawTableHeader(headers, columns, align) }
            }
        }
    }
}

private fun dataSheetUntukLaporan(
    identitas: PengaturanUsahaCache.IdentitasUsaha,
    rangeLabel: String,
    tipeLaporan: String,
    rows: List<RepositoriFirebaseUtama.BarisRiwayatTransaksi>,
    saldoAwal: Long = 0L
): List<List<String>> {
    val namaUsaha = identitas.namaUsaha.ifBlank { "SI Tahu" }
    val tipe = tipeLaporanNormal(tipeLaporan)
    val sheetRows = mutableListOf<List<String>>()
    sheetRows += listOf(tipe.uppercase(Locale.US))
    sheetRows += listOf("Nama Usaha", namaUsaha)
    if (identitas.alamat.isNotBlank()) sheetRows += listOf("Alamat", identitas.alamat)
    if (identitas.nomorTelepon.isNotBlank()) sheetRows += listOf("Telepon", identitas.nomorTelepon)
    sheetRows += listOf("Periode", rangeLabel)
    if (tipe == REPORT_KEUANGAN) sheetRows += listOf("Saldo Awal", Formatter.currency(saldoAwal))
    sheetRows += listOf("Data Ditampilkan", if (tipe == REPORT_PRODUKSI) "Riwayat Produksi" else "Arus Kas Masuk/Keluar")
    sheetRows += listOf("Tanggal Cetak", SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date()))
    sheetRows += emptyList<String>()
    if (tipe == REPORT_PRODUKSI) {
        sheetRows += listOf("Tanggal Mutasi", "Produk", "Uraian Mutasi", "User", "Masuk", "Keluar", "Stok")
        sheetRows += listOf("-", "-", "Gunakan export Laporan Produksi untuk sheet Semua Produk dan detail per produk.", "-", "-", "-", "-")
    } else {
        sheetRows += listOf("Tanggal Transaksi", "Uraian Transaksi", "Teller", "Debet", "Kredit", "Saldo")
        val previewRows = buildPreviewKeuanganRows(rows, saldoAwal)
        previewRows.forEach { r -> sheetRows += listOf(r.tanggal, r.uraian, r.teller, r.debit, r.kredit, r.saldo) }
    }
    return sheetRows
}

private fun buildPdfUntukLaporan(
    identitas: PengaturanUsahaCache.IdentitasUsaha,
    rangeLabel: String,
    tipeLaporan: String,
    rows: List<RepositoriFirebaseUtama.BarisRiwayatTransaksi>,
    saldoAwal: Long = 0L
): PdfDocument {
    val namaUsaha = identitas.namaUsaha.ifBlank { "SI Tahu" }
    val tipe = tipeLaporanNormal(tipeLaporan)
    return createLandscapePdf(tipe) { state ->
        state.drawTitle(namaUsaha.uppercase(Locale.US))
        if (identitas.alamat.isNotBlank()) state.drawMeta("Alamat", identitas.alamat)
        if (identitas.nomorTelepon.isNotBlank()) state.drawMeta("Telepon", identitas.nomorTelepon)
        state.drawTitle(tipe.uppercase(Locale.US))
        state.drawMeta("Periode", rangeLabel)
        if (tipe == REPORT_KEUANGAN) state.drawMeta("Saldo Awal", Formatter.currency(saldoAwal))
        state.drawMeta("Data Ditampilkan", if (tipe == REPORT_PRODUKSI) "Riwayat Produksi" else "Arus Kas Masuk/Keluar")
        state.drawMeta("Tanggal Cetak", SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date()))
        state.space(8f)
        if (tipe == REPORT_PRODUKSI) {
            val c = floatArrayOf(96f, 112f, 210f, 78f, 70f, 70f, 82f)
            val h = listOf("Tanggal Mutasi", "Produk", "Uraian Mutasi", "User", "Masuk", "Keluar", "Stok")
            val align = listOf(0, 0, 0, 0, 1, 1, 1)
            state.drawTableHeader(h, c, align)
            state.drawTableRow(listOf("-", "-", "Gunakan export Laporan Produksi untuk mutasi stok produk.", "-", "-", "-", "-"), c, align) { state.drawTableHeader(h, c, align) }
        } else {
            val c = floatArrayOf(108f, 270f, 88f, 94f, 94f, 94f)
            val h = listOf("Tanggal Transaksi", "Uraian Transaksi", "Teller", "Debet", "Kredit", "Saldo")
            val align = listOf(0, 0, 0, 1, 1, 1)
            state.drawTableHeader(h, c, align)
            val previewRows = buildPreviewKeuanganRows(rows, saldoAwal)
            previewRows.forEach { r ->
                state.drawTableRow(listOf(r.tanggal, r.uraian, r.teller, r.debit, r.kredit, r.saldo), c, align) { state.drawTableHeader(h, c, align) }
            }
        }
    }
}

// === ANIMASI SKELETON ===
private fun Modifier.adminShimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val startOffsetX by transition.animateFloat(initialValue = -2 * size.width.toFloat(), targetValue = 2 * size.width.toFloat(), animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "shimmer_offsetX")
    val isDark = isSystemInDarkTheme()
    val baseColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)
    val highlightColor = if (isDark) Color(0xFF4B5563) else Color(0xFFF3F4F6)
    background(brush = Brush.linearGradient(colors = listOf(baseColor, highlightColor, baseColor), start = Offset(startOffsetX, 0f), end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat()))).onGloballyPositioned { size = it.size }
}

// === KOMPONEN UTAMA UI COMPOSE ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDashboardScreen(
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    onExportBukuHarianExcel: (String, (Boolean) -> Unit) -> Unit,
    onExportBukuHarianPdf: (String, (Boolean) -> Unit) -> Unit,
    onExportStokExcel: (String, (Boolean) -> Unit) -> Unit,
    onExportStokPdf: (String, (Boolean) -> Unit) -> Unit,
    onExportMutasiExcel: (String, String, String, List<RepositoriFirebaseUtama.BarisRiwayatTransaksi>, Long, (Boolean) -> Unit) -> Unit,
    onExportMutasiPdf: (String, String, String, List<RepositoriFirebaseUtama.BarisRiwayatTransaksi>, Long, (Boolean) -> Unit) -> Unit,
    activityContext: AppCompatActivity
) {
    val rangeOptions = listOf("Hari ini" to "hari_ini", "7 hari" to "7", "14 hari" to "14", "30 hari" to "30", "90 hari" to "90", "6 bulan" to "180", "1 tahun" to "365", "Semua" to "semua")
    var selectedRange by remember { mutableStateOf(rangeOptions.first()) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var selectedJenisLaporan by remember { mutableStateOf(REPORT_KEUANGAN) }
    var exportLoading by remember { mutableStateOf(false) }

    val statusOptions = remember {
        listOf(
            "Semua status" to STATUS_LAPORAN_SEMUA,
            "Pembayaran pending" to STATUS_LAPORAN_PENDING,
            "Selesai" to STATUS_LAPORAN_SELESAI,
            "Dibatalkan / gagal" to STATUS_LAPORAN_BATAL_GAGAL
        )
    }

    var selectedStatusLaporan by remember { mutableStateOf(statusOptions.first()) }

    var draftTanggalMulai by remember { mutableStateOf("") }
    var draftTanggalSelesai by remember { mutableStateOf("") }

    var reportData by remember { mutableStateOf<RepositoriFirebaseUtama.RingkasanLaporanFirebase?>(null) }
    var riwayatData by remember { mutableStateOf<List<RepositoriFirebaseUtama.BarisRiwayatTransaksi>>(emptyList()) }
    var riwayatSemuaData by remember { mutableStateOf<List<RepositoriFirebaseUtama.BarisRiwayatTransaksi>>(emptyList()) }
    var stokProdukData by remember { mutableStateOf<List<PdfStokProduk>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF111827) else Color(0xFFF3F4F6)
    val surfaceColor = if (isDark) Color(0xFF1F2937) else Color(0xFFFFFFFF)
    val primaryColor = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
    val textColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF111827)
    val mutedColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)
    val successColor = if (isDark) Color(0xFF10B981) else Color(0xFF059669)
    val dangerColor = if (isDark) Color(0xFFEF4444) else Color(0xFFDC2626)

    val identitasUsaha = remember(activityContext) { PengaturanUsahaCache.baca(activityContext) }
    val pilihanJenisLaporan = remember { listOf(REPORT_KEUANGAN, REPORT_PRODUKSI) }
    val labelBulanFormatter = remember { SimpleDateFormat("MMMM yyyy", Locale("id", "ID")) }

    val saldoAwalKeuangan = remember(riwayatSemuaData, selectedRange) {
        hitungSaldoSebelumPeriode(riwayatSemuaData, selectedRange.second) }
    val riwayatDataTerfilterStatus = remember(
        riwayatData,
        selectedJenisLaporan,
        selectedStatusLaporan
    ) {
        if (tipeLaporanNormal(selectedJenisLaporan) == REPORT_PRODUKSI) {
            riwayatData
        } else {
            riwayatData.filter { row ->
                cocokStatusLaporan(row, selectedStatusLaporan.second)
            }
        }
    }
    val financePreviewRows = remember(riwayatDataTerfilterStatus, saldoAwalKeuangan) {
        buildPreviewKeuanganRows(riwayatDataTerfilterStatus, saldoAwalKeuangan) }
    val productionPreviewRows = remember(stokProdukData) {
        buildPreviewProduksiMutasiRows(stokProdukData) }
    val rowsUntukEkspor = remember(riwayatDataTerfilterStatus, selectedJenisLaporan) {
        when (tipeLaporanNormal(selectedJenisLaporan)) {
            REPORT_PRODUKSI -> riwayatDataTerfilterStatus.filter {
                it.jenis.equals("Produksi", true) || it.jenis.equals("Produk Olahan", true)
            }
            else -> riwayatDataTerfilterStatus.filter {
                it.jenis.equals("Penjualan", true) || it.jenis.equals("Pengeluaran", true)
            }
        }
    }

    LaunchedEffect(selectedRange) {
        isLoading = true
        runCatching {
            val report = RepositoriFirebaseUtama.muatLaporan(selectedRange.second)
            val rows = RepositoriFirebaseUtama.muatRiwayatTransaksi(report.rangeKey)
            val allRows = if (report.rangeKey == "semua") rows else RepositoriFirebaseUtama.muatRiwayatTransaksi("semua")
            val stokText = RepositoriFirebaseUtama.buildStokProdukPdfText(report.rangeKey)
            val (_, products) = parseStokProduk(stokText)
            DataMuatLaporan(report, rows, allRows, products)
        }.onSuccess { data ->
            reportData = data.report
            riwayatData = data.rows
            riwayatSemuaData = data.allRows
            stokProdukData = data.products
            isLoading = false
        }.onFailure { e ->
            isLoading = false
            reportData = null
            riwayatData = emptyList()
            riwayatSemuaData = emptyList()
            stokProdukData = emptyList()
            onShowMessage(e.message ?: "Gagal memuat data laporan")
        }
    }

    Scaffold(
        topBar = {
            Surface(color = surfaceColor, shadowElevation = if (isDark) 0.dp else 4.dp, border = if (isDark) BorderStroke(1.dp, borderColor) else null) {
                TopAppBar(
                    title = {
                        Column {
                            Text("Laporan", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text(
                                if (tipeLaporanNormal(selectedJenisLaporan) == REPORT_PRODUKSI) {
                                    selectedJenisLaporan
                                } else {
                                    "$selectedJenisLaporan • ${selectedStatusLaporan.first}"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = mutedColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Rounded.ArrowBack, "Kembali", tint = textColor) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        containerColor = bgColor
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = surfaceColor,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp).clickable { showFilterDialog = true }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Periode", color = mutedColor, style = MaterialTheme.typography.labelSmall)
                            Text(selectedRange.first, fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Rounded.FilterList, "Filter", tint = primaryColor)
                    }
                }
            }

            if (isLoading) {
                item { DashboardSkeleton(surfaceColor, borderColor) }
            } else {
                item {
                    JenisLaporanFilterChips(
                        options = pilihanJenisLaporan,
                        selected = selectedJenisLaporan,
                        onSelected = {
                            selectedJenisLaporan = it

                            if (tipeLaporanNormal(it) == REPORT_PRODUKSI) {
                                selectedStatusLaporan = statusOptions.first()
                            }
                        },
                        surfaceColor = surfaceColor,
                        borderColor = borderColor,
                        textColor = textColor,
                        primaryColor = primaryColor,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                item {
                    HeaderDokumenLaporan(
                        identitasUsaha = identitasUsaha,
                        jenisLaporan = selectedJenisLaporan,
                        periodeLabel = reportData?.rangeLabel ?: selectedRange.first,
                        jumlahBaris = if (tipeLaporanNormal(selectedJenisLaporan) == REPORT_PRODUKSI) productionPreviewRows.size else rowsUntukEkspor.size,
                        description = deskripsiRingkasLaporan(selectedJenisLaporan),
                        saldoAwal = saldoAwalKeuangan,
                        surfaceColor = surfaceColor,
                        borderColor = borderColor,
                        textColor = textColor,
                        mutedColor = mutedColor,
                        primaryColor = primaryColor,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                item {
                    LaporanMutasiToolbar(
                        jumlahData = if (tipeLaporanNormal(selectedJenisLaporan) == REPORT_PRODUKSI) productionPreviewRows.size else rowsUntukEkspor.size,
                        isLoading = exportLoading,
                        onPdfClick = {
                            val bolehEkspor = rowsUntukEkspor.isNotEmpty() || tipeLaporanNormal(selectedJenisLaporan) == REPORT_KEUANGAN || (tipeLaporanNormal(selectedJenisLaporan) == REPORT_PRODUKSI && stokProdukData.isNotEmpty())
                            if (!bolehEkspor) onShowMessage("Belum ada data yang bisa dicetak pada periode ini")
                            else onExportMutasiPdf(reportData?.rangeLabel ?: selectedRange.first, reportData?.rangeKey ?: selectedRange.second, selectedJenisLaporan, rowsUntukEkspor, saldoAwalKeuangan) { exportLoading = it }
                        },
                        onExcelClick = {
                            val bolehEkspor = rowsUntukEkspor.isNotEmpty() || tipeLaporanNormal(selectedJenisLaporan) == REPORT_KEUANGAN || (tipeLaporanNormal(selectedJenisLaporan) == REPORT_PRODUKSI && stokProdukData.isNotEmpty())
                            if (!bolehEkspor) onShowMessage("Belum ada data yang bisa diunduh pada periode ini")
                            else onExportMutasiExcel(reportData?.rangeLabel ?: selectedRange.first, reportData?.rangeKey ?: selectedRange.second, selectedJenisLaporan, rowsUntukEkspor, saldoAwalKeuangan) { exportLoading = it }
                        },
                        surfaceColor = surfaceColor,
                        borderColor = borderColor,
                        textColor = textColor,
                        mutedColor = mutedColor,
                        primaryColor = primaryColor,
                        successColor = successColor,
                        dangerColor = dangerColor,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                item {
                    if (tipeLaporanNormal(selectedJenisLaporan) == REPORT_PRODUKSI) {
                        TabelDokumenLaporan(
                            headers = headerKolomUntukLaporan(selectedJenisLaporan),
                            rows = productionPreviewRows.map { listOf(it.tanggal, it.produk, it.uraian, it.user, it.masuk, it.keluar, it.stok) },
                            widths = listOf(126.dp, 150.dp, 300.dp, 96.dp, 86.dp, 86.dp, 96.dp),
                            numericColumns = setOf(4, 5, 6),
                            emptyRow = listOf("-", "-", "Belum ada data mutasi stok produksi pada periode ini", "-", "-", "-", "-"),
                            surfaceColor = surfaceColor,
                            bgColor = bgColor,
                            borderColor = borderColor,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            primaryColor = primaryColor,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    } else {
                        TabelDokumenLaporan(
                            headers = headerKolomUntukLaporan(selectedJenisLaporan),
                            rows = financePreviewRows.map { listOf(it.tanggal, it.uraian, it.teller, it.debit, it.kredit, it.saldo) },
                            widths = listOf(126.dp, 320.dp, 92.dp, 110.dp, 110.dp, 118.dp),
                            numericColumns = setOf(3, 4, 5),
                            emptyRow = listOf("-", "Belum ada data keuangan pada periode ini", "-", "-", "-", "-"),
                            surfaceColor = surfaceColor,
                            bgColor = bgColor,
                            borderColor = borderColor,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            primaryColor = primaryColor,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            }
        }

        if (showFilterDialog) {
            var draftStatus by remember { mutableStateOf(selectedStatusLaporan) }

            AlertDialog(
                onDismissRequest = { showFilterDialog = false },
                shape = RoundedCornerShape(24.dp),
                containerColor = surfaceColor,
                title = {
                    Text(
                        "Filter Laporan",
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Filter kalender spesifik",
                            color = mutedColor,
                            style = MaterialTheme.typography.labelMedium
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showFilterDialog = false
                                    draftTanggalMulai = ""
                                    draftTanggalSelesai = ""
                                    showDateRangePicker = true
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = bgColor,
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.DateRange,
                                    contentDescription = null,
                                    tint = mutedColor
                                )

                                Text(
                                    "Rentang tanggal custom",
                                    color = textColor,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showFilterDialog = false
                                    showMonthPicker = true
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = bgColor,
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.DateRange,
                                    contentDescription = null,
                                    tint = mutedColor
                                )

                                Text(
                                    "Pilih satu bulan penuh",
                                    color = textColor,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        if (tipeLaporanNormal(selectedJenisLaporan) != REPORT_PRODUKSI) {
                            HorizontalDivider(color = borderColor)

                            Text(
                                "Status Pembayaran",
                                color = mutedColor,
                                style = MaterialTheme.typography.labelMedium
                            )

                            statusOptions.forEach { option ->
                                val isSelected = option == draftStatus

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) {
                                        primaryColor.copy(alpha = 0.10f)
                                    } else {
                                        bgColor
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) primaryColor else borderColor
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            draftStatus = option
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = 14.dp,
                                            vertical = 12.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            option.first,
                                            color = if (isSelected) primaryColor else textColor,
                                            fontWeight = if (isSelected) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Medium
                                            }
                                        )

                                        if (isSelected) {
                                            Icon(
                                                Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = primaryColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            selectedStatusLaporan = draftStatus
                            showFilterDialog = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        Text(
                            "Terapkan",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showFilterDialog = false }
                    ) {
                        Text(
                            "Batal",
                            color = mutedColor
                        )
                    }
                }
            )
        }

        if (showDateRangePicker) {
            val utcFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val labelTanggalFormatterRange = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
            val dateRangePickerState = rememberDateRangePickerState()

            Dialog(onDismissRequest = { showDateRangePicker = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.90f), shape = RoundedCornerShape(24.dp), color = surfaceColor) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Pilih rentang tanggal", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            IconButton(onClick = { showDateRangePicker = false }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, "Tutup", tint = textColor) }
                        }
                        HorizontalDivider(color = borderColor)
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            DateRangePicker(state = dateRangePickerState, title = null, headline = null, showModeToggle = false, modifier = Modifier.fillMaxSize(), colors = DatePickerDefaults.colors(containerColor = Color.Transparent, dayContentColor = textColor, selectedDayContainerColor = primaryColor, selectedDayContentColor = Color.White, dayInSelectionRangeContainerColor = primaryColor.copy(alpha = 0.2f), dayInSelectionRangeContentColor = primaryColor))
                        }
                        HorizontalDivider(color = borderColor)
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { showDateRangePicker = false }) { Text("Batal", color = mutedColor, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val sMillis = dateRangePickerState.selectedStartDateMillis
                                    val eMillis = dateRangePickerState.selectedEndDateMillis
                                    if (sMillis != null) {
                                        val startIso = utcFormat.format(Date(sMillis))
                                        val endIso = eMillis?.let { utcFormat.format(Date(it)) } ?: startIso
                                        val startLabel = labelTanggalFormatterRange.format(Date(sMillis))
                                        val endLabel = eMillis?.let { labelTanggalFormatterRange.format(Date(it)) } ?: startLabel
                                        val rangeLabel = if (startLabel == endLabel) startLabel else "$startLabel - $endLabel"
                                        selectedRange = rangeLabel to "custom:$startIso:$endIso"
                                        showDateRangePicker = false
                                    } else onShowMessage("Pilih rentang tanggal terlebih dahulu")
                                },
                                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                            ) { Text("Simpan", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }

        if (showMonthPicker) {
            val months = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Ags", "Sep", "Okt", "Nov", "Des")
            var tempYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
            var tempMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
            Dialog(onDismissRequest = { showMonthPicker = false }) {
                Surface(shape = RoundedCornerShape(24.dp), color = surfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Pilih bulan & tahun", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            IconButton(onClick = { tempYear-- }, modifier = Modifier.background(bgColor, CircleShape)) { Icon(Icons.Rounded.ChevronLeft, "Tahun Sebelumnya", tint = textColor) }
                            Text("$tempYear", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            IconButton(onClick = { tempYear++ }, modifier = Modifier.background(bgColor, CircleShape)) { Icon(Icons.Rounded.ChevronRight, "Tahun Selanjutnya", tint = textColor) }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (row in 0..3) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    for (col in 0..2) {
                                        val mIndex = row * 3 + col
                                        val isSelected = tempMonth == mIndex
                                        Surface(shape = RoundedCornerShape(12.dp), color = if (isSelected) primaryColor else bgColor, border = BorderStroke(1.dp, if (isSelected) primaryColor else borderColor), modifier = Modifier.weight(1f).clickable { tempMonth = mIndex }) {
                                            Text(months[mIndex], color = if (isSelected) Color.White else textColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = borderColor, modifier = Modifier.padding(top = 8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { showMonthPicker = false }) { Text("Batal", color = mutedColor, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                val formattedMonth = String.format(Locale.US, "%02d", tempMonth + 1)
                                val bulanKey = "$tempYear-$formattedMonth"
                                val calendar = Calendar.getInstance().apply { set(tempYear, tempMonth, 1) }
                                selectedRange = "Bulan ${labelBulanFormatter.format(calendar.time)}" to "bulan:$bulanKey"
                                showMonthPicker = false
                            }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) { Text("Simpan", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

// === KOMPONEN BANTUAN UI ===
@Composable
private fun JenisLaporanFilterChips(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { option ->
            val isSelected = option == selected
            Surface(
                shape = RoundedCornerShape(100),
                color = if (isSelected) primaryColor else surfaceColor,
                border = BorderStroke(1.dp, if (isSelected) primaryColor else borderColor),
                modifier = Modifier.clickable { onSelected(option) }
            ) {
                Text(option, color = if (isSelected) Color.White else textColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun HeaderDokumenLaporan(
    identitasUsaha: PengaturanUsahaCache.IdentitasUsaha,
    jenisLaporan: String,
    periodeLabel: String,
    jumlahBaris: Int,
    description: String,
    saldoAwal: Long,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val tipe = tipeLaporanNormal(jenisLaporan)
    Surface(shape = RoundedCornerShape(18.dp), color = surfaceColor, border = BorderStroke(1.dp, borderColor), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(primaryColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (tipe == REPORT_PRODUKSI) Icons.Rounded.Inventory else Icons.Rounded.ReceiptLong, null, tint = primaryColor, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(jenisLaporan, color = textColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(description, color = mutedColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoDokumenLaporan("Periode", periodeLabel, textColor, mutedColor, Modifier.weight(1f))
                if (tipe == REPORT_KEUANGAN) {
                    InfoDokumenLaporan("Saldo Awal", Formatter.currency(saldoAwal), textColor, mutedColor, Modifier.weight(1f))
                } else {
                    InfoDokumenLaporan("Kolom", "Masuk / Keluar / Stok", textColor, mutedColor, Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoDokumenLaporan("Data", "$jumlahBaris baris", textColor, mutedColor, Modifier.weight(1f))
                InfoDokumenLaporan("Output", "PDF / Excel", textColor, mutedColor, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TabelDokumenLaporan(
    headers: List<HeaderKolomLaporan>,
    rows: List<List<String>>,
    widths: List<androidx.compose.ui.unit.Dp>,
    numericColumns: Set<Int>,
    emptyRow: List<String>,
    surfaceColor: Color,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val horizontalScroll = rememberScrollState()
    Surface(shape = RoundedCornerShape(18.dp), color = surfaceColor, border = BorderStroke(1.dp, borderColor), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Data laporan", color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("Preview ringkas sebelum export", color = mutedColor, style = MaterialTheme.typography.labelSmall)
                }
                Text("${rows.size} baris", color = primaryColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
            HorizontalDivider(color = borderColor)
            Box(Modifier.fillMaxWidth().horizontalScroll(horizontalScroll)) {
                Column(Modifier.width(widths.fold(0.dp) { acc, item -> acc + item })) {
                    HeaderRowDokumen(headers, widths, textColor, mutedColor, borderColor, bgColor, numericColumns)
                    val safeRows = if (rows.isEmpty()) listOf(emptyRow) else rows
                    safeRows.forEachIndexed { index, row ->
                        DataRowDokumen(
                            values = row,
                            widths = widths,
                            numericColumns = numericColumns,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            borderColor = borderColor,
                            backgroundColor = if (index % 2 == 0) surfaceColor else bgColor.copy(alpha = 0.45f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRowDokumen(
    headers: List<HeaderKolomLaporan>,
    widths: List<androidx.compose.ui.unit.Dp>,
    textColor: Color,
    mutedColor: Color,
    borderColor: Color,
    backgroundColor: Color,
    numericColumns: Set<Int>
) {
    Row(Modifier.background(backgroundColor).heightIn(min = 52.dp)) {
        headers.forEachIndexed { index, header ->
            Box(
                modifier = Modifier.width(widths[index]).padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = if (numericColumns.contains(index)) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Column(horizontalAlignment = if (numericColumns.contains(index)) Alignment.End else Alignment.Start) {
                    Text(header.label, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, textAlign = if (numericColumns.contains(index)) TextAlign.End else TextAlign.Start)
                    if (header.sublabel.isNotBlank()) Text(header.sublabel, color = mutedColor, style = MaterialTheme.typography.labelSmall, textAlign = if (numericColumns.contains(index)) TextAlign.End else TextAlign.Start)
                }
            }
        }
    }
    HorizontalDivider(color = borderColor)
}

@Composable
private fun DataRowDokumen(
    values: List<String>,
    widths: List<androidx.compose.ui.unit.Dp>,
    numericColumns: Set<Int>,
    textColor: Color,
    mutedColor: Color,
    borderColor: Color,
    backgroundColor: Color
) {
    Row(Modifier.background(backgroundColor).heightIn(min = 48.dp)) {
        values.forEachIndexed { index, value ->
            Box(
                modifier = Modifier.width(widths[index]).padding(horizontal = 10.dp, vertical = 9.dp),
                contentAlignment = if (numericColumns.contains(index)) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Text(
                    value.ifBlank { "-" },
                    color = if (value.isBlank()) mutedColor else textColor,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (numericColumns.contains(index)) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = if (numericColumns.contains(index)) TextAlign.End else TextAlign.Start,
                    maxLines = if (index == 1 || index == 2 || index == 4) 3 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    HorizontalDivider(color = borderColor)
}

@Composable
private fun MutasiPreviewUtamaCard(
    identitasUsaha: PengaturanUsahaCache.IdentitasUsaha,
    periodeLabel: String,
    jenisFilter: String,
    jumlahData: Int,
    totalDataSemua: Int,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    primaryColor: Color
) {
    val namaUsaha = identitasUsaha.namaUsaha.ifBlank { "SI Tahu" }
    val teksLogo = identitasUsaha.teksLogo.ifBlank { PengaturanUsahaCache.buatTeksLogoDariNama(namaUsaha) }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(primaryColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Text(teksLogo.take(4).uppercase(Locale.US), color = primaryColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                }
                Column(Modifier.weight(1f)) {
                    Text(namaUsaha.uppercase(Locale.US), color = textColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(identitasUsaha.alamat.ifBlank { "Alamat usaha belum diisi di pengaturan" }, color = mutedColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (identitasUsaha.nomorTelepon.isNotBlank()) {
                        Text("Telp. ${identitasUsaha.nomorTelepon}", color = mutedColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            HorizontalDivider(color = borderColor)
            Text("MUTASI LAPORAN", color = textColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoDokumenLaporan("Periode", periodeLabel, textColor, mutedColor, Modifier.weight(1f))
                InfoDokumenLaporan("Jenis", jenisFilter, textColor, mutedColor, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoDokumenLaporan("Data Tampil", "$jumlahData data", textColor, mutedColor, Modifier.weight(1f))
                InfoDokumenLaporan("Total Filter Periode", "$totalDataSemua data", textColor, mutedColor, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InfoDokumenLaporan(label: String, value: String, textColor: Color, mutedColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = mutedColor, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = textColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LaporanMutasiToolbar(
    jumlahData: Int,
    isLoading: Boolean,
    onPdfClick: () -> Unit,
    onExcelClick: () -> Unit,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    primaryColor: Color,
    successColor: Color,
    dangerColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(shape = RoundedCornerShape(16.dp), color = surfaceColor, border = BorderStroke(1.dp, borderColor), modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("Export", color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(if (jumlahData > 0) "$jumlahData baris siap diekspor" else "Belum ada data", color = mutedColor, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onPdfClick,
                enabled = !isLoading,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, dangerColor.copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Rounded.Description, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isLoading) "Proses" else "PDF", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = onExcelClick,
                enabled = !isLoading,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, successColor.copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = successColor),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Rounded.ReceiptLong, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Excel", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun MutasiLaporanTablePreview(
    rows: List<RepositoriFirebaseUtama.BarisRiwayatTransaksi>,
    surfaceColor: Color,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val horizontalScroll = rememberScrollState()
    val widths = listOf(136.dp, 104.dp, 104.dp, 280.dp, 124.dp, 132.dp)
    Surface(shape = RoundedCornerShape(18.dp), color = surfaceColor, border = BorderStroke(1.dp, borderColor), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Preview tabel laporan", color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("Tampilan ini mengikuti kolom PDF dan Excel", color = mutedColor, style = MaterialTheme.typography.labelSmall)
                }
                Text("${rows.size} baris", color = primaryColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
            HorizontalDivider(color = borderColor)
            Box(Modifier.fillMaxWidth().horizontalScroll(horizontalScroll)) {
                Column(Modifier.width(widths.fold(0.dp) { acc, item -> acc + item })) {
                    MutasiTableRow(
                        values = listOf("Tanggal", "Jenis", "Status", "Uraian", "User", "Nominal"),
                        widths = widths,
                        textColor = textColor,
                        mutedColor = mutedColor,
                        borderColor = borderColor,
                        backgroundColor = bgColor,
                        isHeader = true,
                        alignRightLast = true
                    )
                    if (rows.isEmpty()) {
                        MutasiTableRow(
                            values = listOf("-", "-", "-", "Belum ada data pada filter ini", "-", "-"),
                            widths = widths,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            borderColor = borderColor,
                            backgroundColor = surfaceColor,
                            isHeader = false,
                            alignRightLast = true
                        )
                    } else {
                        rows.forEachIndexed { index, row ->
                            MutasiTableRow(
                                values = listOf(
                                    Formatter.readableDateTime(row.tanggalIso),
                                    row.jenis.ifBlank { "-" },
                                    row.status.ifBlank { row.badge.ifBlank { "Tercatat" } },
                                    row.title.ifBlank { row.subtitle.ifBlank { "-" } },
                                    row.userName.ifBlank { "-" },
                                    row.amount.ifBlank { "-" }
                                ),
                                widths = widths,
                                textColor = textColor,
                                mutedColor = mutedColor,
                                borderColor = borderColor,
                                backgroundColor = if (index % 2 == 0) surfaceColor else bgColor.copy(alpha = 0.45f),
                                isHeader = false,
                                alignRightLast = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MutasiTableRow(
    values: List<String>,
    widths: List<androidx.compose.ui.unit.Dp>,
    textColor: Color,
    mutedColor: Color,
    borderColor: Color,
    backgroundColor: Color,
    isHeader: Boolean,
    alignRightLast: Boolean
) {
    Row(Modifier.background(backgroundColor).heightIn(min = if (isHeader) 42.dp else 46.dp)) {
        values.forEachIndexed { index, value ->
            Box(
                modifier = Modifier.width(widths[index]).heightIn(min = if (isHeader) 42.dp else 46.dp).padding(horizontal = 10.dp, vertical = 9.dp),
                contentAlignment = if (alignRightLast && index == values.lastIndex) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Text(
                    value,
                    color = if (isHeader) textColor else if (index == values.lastIndex) textColor else mutedColor,
                    fontWeight = if (isHeader || index == values.lastIndex) FontWeight.Bold else FontWeight.Medium,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = if (alignRightLast && index == values.lastIndex) TextAlign.End else TextAlign.Start,
                    maxLines = if (index == 3) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    HorizontalDivider(color = borderColor)
}

@Composable
private fun MutasiSummaryPill(title: String, value: String, tone: Color, textColor: Color, mutedColor: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = tone.copy(alpha = 0.10f), border = BorderStroke(1.dp, tone.copy(alpha = 0.18f)), modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = mutedColor, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = tone, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun JenisMutasiFilterChips(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    surfaceColor: Color,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Filter Jenis Riwayat", color = mutedColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 4.dp)) {
            items(options) { option ->
                val isSelected = option == selected
                Surface(
                    shape = RoundedCornerShape(100),
                    color = if (isSelected) primaryColor else surfaceColor,
                    border = BorderStroke(1.dp, if (isSelected) primaryColor else borderColor),
                    modifier = Modifier.clickable { onSelected(option) }
                ) {
                    Text(option, color = if (isSelected) Color.White else textColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
        }
    }
}

@Composable
private fun TanggalMutasiDivider(label: String, mutedColor: Color, borderColor: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = mutedColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        HorizontalDivider(color = borderColor, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MutasiLaporanRowCard(
    row: RepositoriFirebaseUtama.BarisRiwayatTransaksi,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    primaryColor: Color,
    successColor: Color,
    warningColor: Color,
    dangerColor: Color,
    purpleColor: Color,
    modifier: Modifier = Modifier
) {
    val accentColor = when {
        row.status.equals("Batal", true) -> dangerColor
        row.jenis.equals("Penjualan", true) -> successColor
        row.jenis.equals("Pengeluaran", true) -> dangerColor
        row.jenis.equals("Produksi", true) -> primaryColor
        row.jenis.equals("Produk Olahan", true) -> purpleColor
        else -> warningColor
    }
    val icon = when {
        row.jenis.equals("Penjualan", true) -> Icons.Rounded.PointOfSale
        row.jenis.equals("Pengeluaran", true) -> Icons.Rounded.ReceiptLong
        row.jenis.equals("Produksi", true) -> Icons.Rounded.Factory
        row.jenis.equals("Produk Olahan", true) -> Icons.Rounded.Inventory
        else -> Icons.Rounded.FilterList
    }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(row.title.ifBlank { row.jenis }, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Surface(shape = RoundedCornerShape(100), color = accentColor.copy(alpha = 0.10f)) {
                        Text(row.jenis.ifBlank { "Riwayat" }, color = accentColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                Text(row.subtitle.ifBlank { row.userName }, color = mutedColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${waktuMutasi(row.tanggalIso)} • ${row.status.ifBlank { row.badge.ifBlank { "Tercatat" } }}", color = mutedColor, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(row.amount.ifBlank { "-" }, color = accentColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(min = 86.dp, max = 118.dp))
        }
    }
}

private fun labelTanggalMutasi(tanggalIso: String): String = Formatter.readableDate(tanggalIso)
private fun waktuMutasi(tanggalIso: String): String = Formatter.readableTime(tanggalIso)

@Composable
private fun RiwayatLaporanHeader(
    periodeLabel: String,
    jumlahData: Int,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    primaryColor: Color
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ReceiptLong, null, tint = primaryColor, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Data Riwayat", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                Text(periodeLabel, color = mutedColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(shape = RoundedCornerShape(100), color = primaryColor.copy(alpha = 0.12f)) {
                Text("$jumlahData data", color = primaryColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
    }
}

@Composable
private fun RiwayatKosongCard(surfaceColor: Color, borderColor: Color, textColor: Color, mutedColor: Color, primaryColor: Color) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.FilterList, null, tint = primaryColor, modifier = Modifier.size(28.dp))
            }
            Text("Belum ada riwayat", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text("Tidak ada transaksi, produksi, pengeluaran, atau penyesuaian stok pada filter ini.", color = mutedColor, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RiwayatLaporanCard(
    row: RepositoriFirebaseUtama.BarisRiwayatTransaksi,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    primaryColor: Color,
    successColor: Color,
    warningColor: Color,
    dangerColor: Color,
    purpleColor: Color,
    modifier: Modifier = Modifier
) {
    val accentColor = when {
        row.status.equals("Batal", true) -> dangerColor
        row.jenis.equals("Penjualan", true) -> primaryColor
        row.jenis.equals("Pengeluaran", true) -> dangerColor
        row.jenis.equals("Produksi", true) -> successColor
        row.jenis.equals("Produk Olahan", true) -> purpleColor
        else -> warningColor
    }
    val icon = when {
        row.jenis.equals("Penjualan", true) -> Icons.Rounded.PointOfSale
        row.jenis.equals("Pengeluaran", true) -> Icons.Rounded.ReceiptLong
        row.jenis.equals("Produksi", true) -> Icons.Rounded.Factory
        row.jenis.equals("Produk Olahan", true) -> Icons.Rounded.Inventory
        else -> Icons.Rounded.FilterList
    }

    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = accentColor, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(row.title.ifBlank { row.jenis }, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(row.subtitle.ifBlank { Formatter.readableDateTime(row.tanggalIso) }, color = mutedColor, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(row.amount.ifBlank { "-" }, color = accentColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Surface(shape = RoundedCornerShape(100), color = accentColor.copy(alpha = 0.10f), border = BorderStroke(1.dp, accentColor.copy(alpha = 0.20f))) {
                    Text(row.jenis.ifBlank { "Riwayat" }, color = accentColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
                Surface(shape = RoundedCornerShape(100), color = accentColor.copy(alpha = 0.08f), border = BorderStroke(1.dp, accentColor.copy(alpha = 0.18f))) {
                    Text(row.status.ifBlank { row.badge.ifBlank { "Tercatat" } }, color = accentColor, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(Formatter.readableDateTime(row.tanggalIso), color = mutedColor, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MetricCard(modifier: Modifier = Modifier, title: String, value: String, icon: ImageVector, iconColor: Color, surfaceColor: Color, borderColor: Color, textColor: Color, mutedColor: Color) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor), modifier = modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp)) }
            Column {
                Text(title, color = mutedColor, style = MaterialTheme.typography.labelMedium)
                Text(value, fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ActionMenuItem(icon: ImageVector, iconTint: Color, title: String, subtitle: String, textColor: Color, mutedColor: Color, isLoading: Boolean = false, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(enabled = !isLoading, onClick = onClick).padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = mutedColor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
        }
        if (isLoading) CircularProgressIndicator(color = iconTint, modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.ArrowForwardIos, null, tint = mutedColor, modifier = Modifier.size(16.dp))
    }
}


@Composable
private fun LaporanPreviewTable(
    request: PreviewLaporanRequest,
    text: String,
    surfaceColor: Color,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val preview = remember(request, text) { buildPreviewRowsLaporan(request, text) }
    val horizontalState = rememberScrollState()
    Surface(shape = RoundedCornerShape(20.dp), color = surfaceColor, border = BorderStroke(1.dp, borderColor), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(preview.metaLabel.ifBlank { request.rangeLabel }, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("${preview.totalRows} baris data ditemukan", color = mutedColor, style = MaterialTheme.typography.bodySmall)
                }
                Surface(shape = RoundedCornerShape(100), color = primaryColor.copy(alpha = 0.12f)) {
                    Text(request.format.uppercase(Locale.US), color = primaryColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }

            if (preview.rows.isEmpty()) {
                Surface(shape = RoundedCornerShape(16.dp), color = bgColor, modifier = Modifier.fillMaxWidth()) {
                    Text("Belum ada data pada periode ini.", color = mutedColor, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .horizontalScroll(horizontalState)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(min = 760.dp)
                            .fillMaxHeight()
                    ) {
                        item {
                            PreviewHeaderRow(preview.headers, bgColor, borderColor, textColor)
                        }
                        itemsIndexed(preview.rows) { index, row ->
                            PreviewDataRow(row, if (index % 2 == 0) surfaceColor else bgColor.copy(alpha = 0.55f), borderColor, textColor, mutedColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewHeaderRow(headers: List<String>, bgColor: Color, borderColor: Color, textColor: Color) {
    Row(Modifier.widthIn(min = 760.dp).background(bgColor).padding(vertical = 9.dp)) {
        headers.forEach { header ->
            Text(header, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(126.dp).padding(horizontal = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    HorizontalDivider(color = borderColor)
}

@Composable
private fun PreviewDataRow(row: List<String>, rowColor: Color, borderColor: Color, textColor: Color, mutedColor: Color) {
    Row(Modifier.widthIn(min = 760.dp).background(rowColor).padding(vertical = 8.dp)) {
        row.forEach { value ->
            val isEmpty = value.isBlank()
            Text(if (isEmpty) "-" else value, color = if (isEmpty) mutedColor else textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(126.dp).padding(horizontal = 8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
    HorizontalDivider(color = borderColor.copy(alpha = 0.6f))
}

private fun buildPreviewRowsLaporan(request: PreviewLaporanRequest, text: String): PreviewRowsLaporan {
    return if (request.jenis == "buku") {
        val (meta, rows) = parseBukuRows(text)
        PreviewRowsLaporan(
            metaLabel = meta["tanggal"].orEmpty().ifBlank { meta["periode"].orEmpty() },
            headers = listOf("Tanggal", "Uraian", "User", "Debit", "Kredit", "Saldo"),
            rows = rows.map { listOf(it.tanggal, it.uraian, it.user, it.debit, it.kredit, it.saldo) },
            totalRows = rows.size
        )
    } else {
        val (meta, products) = parseStokProduk(text)
        val flatRows = mutableListOf<List<String>>()
        products.forEach { product ->
            flatRows += listOf("Produk", product.nama, product.kodeKategori, product.stokSaatIni, product.stokLayak, product.rincianEd)
            product.mutasi.forEach { row ->
                flatRows += listOf(row.tanggal, row.uraian, row.user, row.masuk, row.keluar, row.saldo)
            }
        }
        PreviewRowsLaporan(
            metaLabel = meta["tanggal"].orEmpty().ifBlank { meta["periode"].orEmpty() },
            headers = listOf("Tanggal", "Uraian", "User", "Masuk", "Keluar", "Saldo"),
            rows = flatRows,
            totalRows = products.sumOf { it.mutasi.size }
        )
    }
}


@Composable
private fun DashboardSkeleton(surfaceColor: Color, borderColor: Color) {
    Column(
        Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Filter chip skeleton
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) { index ->
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = surfaceColor,
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Box(
                        Modifier
                            .padding(horizontal = if (index == 0) 18.dp else 16.dp, vertical = 10.dp)
                            .width(if (index == 0) 112.dp else 96.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .adminShimmerEffect()
                    )
                }
            }
        }

        // Header laporan skeleton
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = surfaceColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .adminShimmerEffect()
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.fillMaxWidth(0.48f).height(18.dp).clip(RoundedCornerShape(6.dp)).adminShimmerEffect())
                        Box(Modifier.fillMaxWidth(0.72f).height(12.dp).clip(RoundedCornerShape(6.dp)).adminShimmerEffect())
                    }
                }
                HorizontalDivider(color = borderColor)
                repeat(2) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        repeat(2) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(Modifier.fillMaxWidth(0.35f).height(11.dp).clip(RoundedCornerShape(6.dp)).adminShimmerEffect())
                                Box(Modifier.fillMaxWidth(0.78f).height(15.dp).clip(RoundedCornerShape(6.dp)).adminShimmerEffect())
                            }
                        }
                    }
                }
            }
        }

        // Toolbar export skeleton
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = surfaceColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.fillMaxWidth(0.28f).height(16.dp).clip(RoundedCornerShape(6.dp)).adminShimmerEffect())
                    Box(Modifier.fillMaxWidth(0.55f).height(12.dp).clip(RoundedCornerShape(6.dp)).adminShimmerEffect())
                }
                repeat(2) {
                    Box(
                        Modifier
                            .width(78.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .adminShimmerEffect()
                    )
                }
            }
        }

        // Tabel skeleton yang mirip tampilan laporan
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = surfaceColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth(0.30f).height(16.dp).clip(RoundedCornerShape(6.dp)).adminShimmerEffect())
                        Box(Modifier.fillMaxWidth(0.42f).height(12.dp).clip(RoundedCornerShape(6.dp)).adminShimmerEffect())
                    }
                    Box(Modifier.width(64.dp).height(14.dp).clip(RoundedCornerShape(6.dp)).adminShimmerEffect())
                }
                HorizontalDivider(color = borderColor)
                Box(Modifier.fillMaxWidth().padding(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(92.dp, 180.dp, 88.dp, 88.dp, 88.dp, 96.dp).forEach { width ->
                                Box(Modifier.width(width).height(14.dp).clip(RoundedCornerShape(6.dp)).adminShimmerEffect())
                            }
                        }
                        repeat(5) { rowIndex ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf(92.dp, 180.dp, 88.dp, 88.dp, 88.dp, 96.dp).forEachIndexed { cellIndex, width ->
                                    val adjusted = when (cellIndex) {
                                        1 -> width - 24.dp
                                        else -> width - if (rowIndex % 2 == 0) 10.dp else 18.dp
                                    }
                                    Box(Modifier.width(adjusted).height(12.dp).clip(RoundedCornerShape(6.dp)).adminShimmerEffect())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// === KOMPONEN UI STATISTIK ===

@Composable
private fun AnalisisAiCard(ai: AnalisisAiStatistik, surfaceColor: Color, borderColor: Color, textColor: Color, mutedColor: Color, primaryColor: Color, successColor: Color, warningColor: Color, dangerColor: Color) {
    val tone = when { ai.skor >= 75 -> successColor; ai.skor >= 55 -> primaryColor; ai.skor >= 40 -> warningColor; else -> dangerColor }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(58.dp).clip(CircleShape).background(tone.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Text(ai.skor.toString(), color = tone, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge) }
                Column(Modifier.weight(1f)) {
                    Text("Skor AI Operasional", color = mutedColor, style = MaterialTheme.typography.labelMedium)
                    Text(ai.status, color = textColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                }
            }
            Text(ai.ringkasan, color = textColor, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(color = borderColor)
            Text("Prioritas Tindakan", color = textColor, fontWeight = FontWeight.Bold)
            ai.prioritas.forEach { text -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) { Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(warningColor)); Text(text, color = mutedColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)) } }
            Spacer(Modifier.height(2.dp))
            Text("Peluang & Saran", color = textColor, fontWeight = FontWeight.Bold)
            ai.peluang.forEach { text -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) { Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(successColor)); Text(text, color = mutedColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)) } }
        }
    }
}

@Composable
private fun InsightList(items: List<String>, surfaceColor: Color, borderColor: Color, textColor: Color, mutedColor: Color, accentColor: Color) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Column {
            items.forEachIndexed { index, text ->
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Text((index + 1).toString(), color = accentColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) }
                    Text(text, color = if (index == 0) textColor else mutedColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
                if (index < items.lastIndex) HorizontalDivider(color = borderColor)
            }
        }
    }
}

@Composable
private fun IndikatorDetailCard(items: List<StatistikIndikator>, surfaceColor: Color, borderColor: Color, textColor: Color, mutedColor: Color, primaryColor: Color, successColor: Color, warningColor: Color, dangerColor: Color) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Column {
            items.forEachIndexed { index, item ->
                val tone = when (item.tone) { "good" -> successColor; "warn" -> warningColor; "bad" -> dangerColor; else -> primaryColor }
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(tone.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Text((index + 1).toString(), color = tone, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium) }
                    Column(Modifier.weight(1f)) { Text(item.title, color = textColor, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(item.note, color = mutedColor, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    Text(item.value, color = tone, fontWeight = FontWeight.Black, textAlign = TextAlign.End, modifier = Modifier.width(112.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (index < items.lastIndex) HorizontalDivider(color = borderColor)
            }
        }
    }
}

@Composable
private fun TrendHarianCard(tren: List<StatistikHari>, surfaceColor: Color, borderColor: Color, textColor: Color, mutedColor: Color, primaryColor: Color, warningColor: Color) {
    val maxValue = tren.maxOfOrNull { maxOf(it.pemasukan, it.pengeluaran) } ?: 0L
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        if (tren.isEmpty()) {
            Text("Belum ada tren harian.", color = mutedColor, modifier = Modifier.padding(18.dp), textAlign = TextAlign.Center)
        } else {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                tren.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(item.label, color = textColor, fontWeight = FontWeight.SemiBold)
                            Text("${Formatter.currency(item.pemasukan)} • ${item.transaksi} trx", color = mutedColor, style = MaterialTheme.typography.labelMedium)
                        }
                        val fracIn = if (maxValue <= 0L) 0f else (item.pemasukan.toFloat() / maxValue.toFloat()).coerceIn(0.04f, 1f)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Masuk", color = mutedColor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(46.dp))
                            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(99.dp)).background(primaryColor.copy(alpha = 0.12f))) { Box(Modifier.fillMaxWidth(fracIn).height(8.dp).clip(RoundedCornerShape(99.dp)).background(primaryColor)) }
                            Text(Formatter.ribuan(item.pemasukan), color = mutedColor, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, modifier = Modifier.width(72.dp))
                        }
                        if (item.pengeluaran > 0L) {
                            val fracOut = if (maxValue <= 0L) 0f else (item.pengeluaran.toFloat() / maxValue.toFloat()).coerceIn(0.04f, 1f)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Keluar", color = mutedColor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(46.dp))
                                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(99.dp)).background(warningColor.copy(alpha = 0.12f))) { Box(Modifier.fillMaxWidth(fracOut).height(8.dp).clip(RoundedCornerShape(99.dp)).background(warningColor)) }
                                Text(Formatter.ribuan(item.pengeluaran), color = mutedColor, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, modifier = Modifier.width(72.dp))
                            }
                        }
                    }
                    if (item != tren.last()) HorizontalDivider(color = borderColor)
                }
            }
        }
    }
}

@Composable
private fun AnalitikList(emptyText: String, items: List<RepositoriFirebaseUtama.ItemAnalitikLaporan>, surfaceColor: Color, borderColor: Color, textColor: Color, mutedColor: Color, accentColor: Color) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        if (items.isEmpty()) {
            Text(emptyText, color = mutedColor, modifier = Modifier.padding(18.dp), textAlign = TextAlign.Center)
        } else {
            Column {
                items.take(8).forEachIndexed { index, item ->
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(30.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Text((index + 1).toString(), color = accentColor, fontWeight = FontWeight.Bold) }
                        Column(Modifier.weight(1f)) { Text(item.title, color = textColor, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(item.subtitle, color = mutedColor, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        Text(item.amount, color = accentColor, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
                    }
                    if (index < items.take(8).lastIndex) HorizontalDivider(color = borderColor)
                }
            }
        }
    }
}

@Composable
private fun StokAnalysisCard(stok: RepositoriFirebaseUtama.RingkasanStokDashboard, surfaceColor: Color, borderColor: Color, textColor: Color, mutedColor: Color, successColor: Color, warningColor: Color, dangerColor: Color) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStockMetric("Produk", stok.totalProdukAktif.toString(), Icons.Rounded.Inventory, successColor, textColor, mutedColor, Modifier.weight(1f))
                MiniStockMetric("Layak", Formatter.ribuan(stok.totalStokLayakJual), Icons.Rounded.PointOfSale, successColor, textColor, mutedColor, Modifier.weight(1f))
                MiniStockMetric("Kritis", stok.totalPerluTindakan.toString(), Icons.Rounded.ShowChart, warningColor, textColor, mutedColor, Modifier.weight(1f))
            }
            HorizontalDivider(color = borderColor)
            Text("Rincian ED & Stok", color = textColor, fontWeight = FontWeight.Bold)
            Text("Aman ${Formatter.ribuan(stok.totalStokAman)} pcs • ED/Hampir Kedaluwarsa ${Formatter.ribuan(stok.totalHampirKadaluarsa)} pcs • Kedaluwarsa ${Formatter.ribuan(stok.totalKadaluarsa)} pcs", color = mutedColor)
        }
    }
}

@Composable
private fun MiniStockMetric(title: String, value: String, icon: ImageVector, color: Color, textColor: Color, mutedColor: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)) }
        Text(value, color = textColor, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(title, color = mutedColor, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

// === FUNGSI LOGIKA STATISTIK & EXPORT LENGKAP ===

private fun bangunStatistikLengkap(laporan: RepositoriFirebaseUtama.RingkasanLaporanFirebase, stok: RepositoriFirebaseUtama.RingkasanStokDashboard, riwayat: List<RepositoriFirebaseUtama.BarisRiwayatTransaksi>): DataStatistikLengkap {
    val userMap = linkedMapOf<String, MutableList<RepositoriFirebaseUtama.BarisRiwayatTransaksi>>()
    riwayat.forEach { row -> userMap.getOrPut(row.userName.ifBlank { "Pengguna" }) { mutableListOf() }.add(row) }
    val aktivitasUser = userMap.map { (nama, rows) -> StatistikUser(nama, rows.count { it.jenis.equals("Penjualan", true) }, rows.filter { it.jenis.equals("Penjualan", true) }.sumOf { angkaDariText(it.amount) }, rows.size) }.sortedWith(compareByDescending<StatistikUser> { it.nominalPenjualan }.thenByDescending { it.aktivitas }).take(8)

    val jenis = riwayat.groupBy { it.jenis.ifBlank { "Aktivitas" } }.map { (nama, rows) -> StatistikKategori(nama, rows.size, rows.sumOf { abs(angkaDariText(it.amount)) }) }.sortedByDescending { it.jumlah }
    val kanal = riwayat.filter { it.jenis.equals("Penjualan", true) }.groupBy { it.badge.ifBlank { "Penjualan" } }.map { (nama, rows) -> StatistikKategori(nama, rows.size, rows.sumOf { angkaDariText(it.amount) }) }.sortedByDescending { it.nominal }

    val inputTanggal = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val outputTanggal = SimpleDateFormat("dd MMM", Locale("id", "ID"))
    val hariMap = linkedMapOf<String, MutableList<RepositoriFirebaseUtama.BarisRiwayatTransaksi>>()
    riwayat.sortedBy { it.tanggalIso }.forEach { row -> hariMap.getOrPut(row.tanggalIso.take(10).ifBlank { "-" }) { mutableListOf() }.add(row) }
    val daftarHari = hariMap.entries.toList()
    val mulaiHari = if (daftarHari.size > 10) daftarHari.size - 10 else 0
    val tren = daftarHari.subList(mulaiHari, daftarHari.size).map { entry ->
        val label = runCatching { outputTanggal.format(inputTanggal.parse(entry.key)!!) }.getOrElse { entry.key }
        StatistikHari(label, entry.value.filter { it.jenis.equals("Penjualan", true) }.sumOf { angkaDariText(it.amount) }, entry.value.filter { it.jenis.equals("Pengeluaran", true) }.sumOf { abs(angkaDariText(it.amount)) }, entry.value.count { it.jenis.equals("Penjualan", true) })
    }

    val margin = persen(laporan.labaRugi, laporan.totalPenjualan)
    val rasioBiaya = persen(laporan.totalPengeluaran, laporan.totalPenjualan)
    val avgTransaksi = avg(laporan.totalPenjualan, laporan.totalTransaksi)
    val produkTop = laporan.produkTerlaris.firstOrNull()
    val pengeluaranTop = laporan.kategoriPengeluaran.firstOrNull()
    val transaksiPenjualan = riwayat.count { it.jenis.equals("Penjualan", true) }
    val transaksiPengeluaran = riwayat.count { it.jenis.equals("Pengeluaran", true) }
    val transaksiProduksi = riwayat.count { it.jenis.equals("Produksi", true) }
    val hariAktif = hariMap.size.coerceAtLeast(1)
    val itemPerTransaksi = if (laporan.totalTransaksi <= 0) 0.0 else laporan.totalItemTerjual.toDouble() / laporan.totalTransaksi.toDouble()
    val produksiTerjualRatio = if (laporan.totalProduksi <= 0) 0.0 else laporan.totalItemTerjual.toDouble() / laporan.totalProduksi.toDouble() * 100.0
    val stokLayakRatio = if (stok.totalStokFisik <= 0) 0.0 else stok.totalStokLayakJual.toDouble() / stok.totalStokFisik.toDouble() * 100.0
    val produkTopShare = if (laporan.totalItemTerjual <= 0 || produkTop == null) 0.0 else produkTop.qty.toDouble() / laporan.totalItemTerjual.toDouble() * 100.0
    val kanalDominan = kanal.firstOrNull()?.nama ?: "-"
    val userTeraktif = aktivitasUser.firstOrNull()?.nama ?: "-"
    val hariTerbaik = tren.sortedByDescending { it.pemasukan }.firstOrNull()
    val hariTerendah = tren.filter { it.pemasukan > 0L }.sortedBy { it.pemasukan }.firstOrNull()
    val trenAwal = tren.take((tren.size / 2).coerceAtLeast(1)).sumOf { it.pemasukan }
    val trenAkhir = tren.drop((tren.size / 2).coerceAtLeast(1)).sumOf { it.pemasukan }
    val perubahanTren = if (trenAwal <= 0L) 0.0 else ((trenAkhir - trenAwal).toDouble() / trenAwal.toDouble()) * 100.0

    val indikatorDetail = listOf(
        StatistikIndikator("Margin Laba", formatPersen(margin), "Laba dibanding pemasukan", if (margin >= 20.0) "good" else if (margin < 5.0) "bad" else "warn"),
        StatistikIndikator("Rasio Pengeluaran", formatPersen(rasioBiaya), "Biaya dibanding pemasukan", if (rasioBiaya <= 45.0) "good" else if (rasioBiaya > 75.0) "bad" else "warn"),
        StatistikIndikator("Rata-rata Transaksi", Formatter.currency(avgTransaksi), "Omzet rata-rata per transaksi", if (avgTransaksi >= 20000) "good" else "normal"),
        StatistikIndikator("Item per Transaksi", formatAngkaDesimal(itemPerTransaksi), "Jumlah pcs rata-rata per transaksi", if (itemPerTransaksi >= 3.0) "good" else "normal"),
        StatistikIndikator("Transaksi per Hari", formatAngkaDesimal(laporan.totalTransaksi.toDouble() / hariAktif.toDouble()), "Rata-rata aktivitas jual harian", "normal"),
        StatistikIndikator("Produksi vs Terjual", formatPersen(produksiTerjualRatio), "Perbandingan item terjual terhadap produksi", if (produksiTerjualRatio in 70.0..120.0) "good" else "warn"),
        StatistikIndikator("Kesehatan Stok", formatPersen(stokLayakRatio), "Stok layak dari total stok fisik", if (stokLayakRatio >= 80.0) "good" else if (stokLayakRatio < 50.0) "bad" else "warn"),
        StatistikIndikator("Ketergantungan Produk", formatPersen(produkTopShare), "Porsi produk terlaris dari total item", if (produkTopShare > 60.0) "warn" else "good"),
        StatistikIndikator("Kanal Dominan", kanalDominan, "Kanal dengan omzet/aktivitas terbesar", "normal"),
        StatistikIndikator("Hari Terbaik", hariTerbaik?.let { "${it.label} • ${Formatter.currency(it.pemasukan)}" } ?: "-", "Pemasukan harian tertinggi", "good"),
        StatistikIndikator("Perubahan Tren", formatPersen(perubahanTren), "Perbandingan paruh akhir vs paruh awal tren", if (perubahanTren >= 0.0) "good" else "warn")
    )

    val analisisAi = bangunAnalisisAiStatistik(laporan, stok, margin, rasioBiaya, avgTransaksi, produkTopShare, perubahanTren, produkTop, pengeluaranTop, kanalDominan, userTeraktif)

    val insight = mutableListOf<String>()
    insight += if (laporan.labaRugi >= 0) "Periode ini menghasilkan laba ${Formatter.currency(laporan.labaRugi)} dengan margin sekitar ${formatPersen(margin)}." else "Periode ini masih rugi ${Formatter.currency(abs(laporan.labaRugi))}. Pengeluaran perlu dikontrol sebelum omzet berikutnya masuk."
    insight += "Rata-rata nilai per transaksi adalah ${Formatter.currency(avgTransaksi)} dari ${Formatter.ribuan(laporan.totalTransaksi.toLong())} transaksi."
    insight += "Rasio pengeluaran terhadap pemasukan sekitar ${formatPersen(rasioBiaya)}. Semakin kecil rasio ini, semakin sehat laba bersih."
    if (produkTop != null) insight += "Produk paling kuat adalah ${produkTop.title} dengan ${Formatter.ribuan(produkTop.qty.toLong())} pcs terjual dan omzet ${Formatter.currency(produkTop.nominal)}."
    insight += "Stok layak jual saat ini ${Formatter.ribuan(stok.totalStokLayakJual)} pcs dari total stok fisik ${Formatter.ribuan(stok.totalStokFisik)} pcs."
    if (hariTerbaik != null) insight += "Hari terbaik pada data tren adalah ${hariTerbaik.label} dengan pemasukan ${Formatter.currency(hariTerbaik.pemasukan)}."

    val rekomendasi = mutableListOf<String>()
    if (stok.totalKadaluarsa > 0) rekomendasi += "Segera tindak ${Formatter.ribuan(stok.totalKadaluarsa)} pcs stok kedaluwarsa agar tidak tercampur dengan stok layak jual."
    if (stok.totalHampirKadaluarsa > 0) rekomendasi += "Prioritaskan penjualan ${Formatter.ribuan(stok.totalHampirKadaluarsa)} pcs stok ED hari ini/hampir kedaluwarsa dengan prinsip stok lama keluar lebih dulu."
    if (stok.totalMenipis > 0 || stok.totalHabis > 0) rekomendasi += "Ada ${stok.totalMenipis + stok.totalHabis} produk menipis/habis. Jadwalkan produksi atau restock bahan untuk produk prioritas."
    if (rasioBiaya > 70.0) rekomendasi += "Rasio pengeluaran tinggi. Periksa biaya terbesar dan bandingkan dengan omzet produk terlaris."
    if (rekomendasi.isEmpty()) rekomendasi += "Kondisi periode ini relatif aman. Pertahankan pencatatan produksi, penjualan, pengeluaran, dan stok secara konsisten."

    return DataStatistikLengkap(laporan, stok, riwayat, aktivitasUser, jenis, kanal, tren, indikatorDetail, analisisAi, insight, rekomendasi)
}

private fun angkaDariText(value: String): Long {
    val sign = if (value.trim().startsWith("-")) -1L else 1L
    val digits = Regex("\\d+").findAll(value).joinToString("") { it.value }
    return (digits.toLongOrNull() ?: 0L) * sign
}

private fun avg(total: Long, count: Int): Long = if (count <= 0) 0L else total / count
private fun persen(part: Long, total: Long): Double = if (total <= 0L) 0.0 else (part.toDouble() / total.toDouble()) * 100.0
private fun formatPersen(value: Double): String = "${(value * 10.0).roundToInt() / 10.0}%"
private fun formatAngkaDesimal(value: Double): String = "${(value * 10.0).roundToInt() / 10.0}"

private fun bangunAnalisisAiStatistik(laporan: RepositoriFirebaseUtama.RingkasanLaporanFirebase, stok: RepositoriFirebaseUtama.RingkasanStokDashboard, margin: Double, rasioBiaya: Double, avgTransaksi: Long, produkTopShare: Double, perubahanTren: Double, produkTop: RepositoriFirebaseUtama.ItemAnalitikLaporan?, pengeluaranTop: RepositoriFirebaseUtama.ItemAnalitikLaporan?, kanalDominan: String, userTeraktif: String): AnalisisAiStatistik {
    var skor = 70
    if (laporan.totalTransaksi <= 0) skor -= 20
    if (laporan.labaRugi < 0) skor -= 25 else skor += 8
    if (margin >= 25.0) skor += 12 else if (margin < 8.0) skor -= 10
    if (rasioBiaya > 75.0) skor -= 15 else if (rasioBiaya < 45.0 && laporan.totalPenjualan > 0) skor += 8
    if (stok.totalKadaluarsa > 0) skor -= 15
    if (stok.totalMenipis + stok.totalHabis > 0) skor -= 10
    if (produkTopShare > 65.0) skor -= 5
    if (perubahanTren > 15.0) skor += 8 else if (perubahanTren < -15.0) skor -= 8
    skor = skor.coerceIn(0, 100)

    val status = when { skor >= 85 -> "Sangat Sehat"; skor >= 70 -> "Sehat"; skor >= 55 -> "Cukup Stabil"; skor >= 40 -> "Perlu Perhatian"; else -> "Perlu Tindakan Cepat" }
    val ringkasan = when { laporan.totalTransaksi <= 0 -> "AI belum menemukan transaksi pada periode ini."; laporan.labaRugi < 0 -> "AI melihat bisnis masih rugi pada periode ini. Pengeluaran lebih besar dari pemasukan."; skor >= 70 -> "AI melihat kondisi usaha cukup baik."; else -> "AI melihat beberapa indikator perlu diperbaiki, terutama biaya dan stok kritis." }

    val prioritas = mutableListOf<String>()
    if (laporan.labaRugi < 0) prioritas += "Pulihkan laba: cek harga jual, biaya bahan, dan pengeluaran terbesar."
    if (rasioBiaya > 70.0) prioritas += "Tekan pengeluaran: rasio biaya ${formatPersen(rasioBiaya)} cukup tinggi."
    if (stok.totalKadaluarsa > 0) prioritas += "Tindak stok kedaluwarsa: ${Formatter.ribuan(stok.totalKadaluarsa)} pcs perlu dipisah."
    if (prioritas.isEmpty()) prioritas += "Tidak ada prioritas kritis. Pertahankan pencatatan."

    val peluang = mutableListOf<String>()
    if (produkTop != null) peluang += "Perkuat produk unggulan ${produkTop.title}: kontribusinya ${formatPersen(produkTopShare)} dari item terjual."
    if (kanalDominan != "-") peluang += "Optimalkan kanal $kanalDominan karena paling dominan."
    if (peluang.isEmpty()) peluang += "Tambahkan lebih banyak transaksi agar AI bisa membaca peluang produk."

    return AnalisisAiStatistik(skor, status, ringkasan, prioritas.take(5), peluang.take(5))
}

private fun buildBukuHarianXlsxFromText(text: String): ByteArray {
    val (meta, rows) = parseBukuRows(text)
    val sheetRows = mutableListOf<List<String>>()
    sheetRows += listOf("BUKU HARIAN SI TAHU")
    sheetRows += listOf("Tanggal Laporan", meta["tanggal"].orEmpty())
    sheetRows += listOf("Periode Transaksi", meta["periode"].orEmpty())
    sheetRows += listOf("Saldo Awal", meta["saldoAwal"].orEmpty())
    sheetRows += emptyList<String>()
    sheetRows += listOf("Tanggal Transaksi", "Uraian Transaksi", "User", "Debit/Pengeluaran", "Kredit/Pemasukan", "Saldo")
    if (rows.isEmpty()) sheetRows += listOf("Belum ada data yang ditampilkan pada periode ini.") else rows.forEach { sheetRows += listOf(it.tanggal, it.uraian, it.user, it.debit, it.kredit, it.saldo) }
    sheetRows += emptyList<String>()
    sheetRows += listOf("Saldo Akhir", meta["saldo"].orEmpty())
    return buildXlsxWorkbook(listOf("Buku Harian" to sheetRows))
}

private fun buildStokProdukXlsxFromText(text: String): ByteArray {
    val (meta, products) = parseStokProduk(text)
    val sheets = mutableListOf<Pair<String, List<List<String>>>>()
    val sumRows = mutableListOf<List<String>>()
    sumRows += listOf("RINGKASAN STOK PRODUK SI TAHU")
    sumRows += listOf("Tanggal Laporan", meta["tanggal"].orEmpty())
    sumRows += emptyList<String>()
    sumRows += listOf("Produk", "Kode", "Fisik", "Layak", "ED", "Hampir Kedaluwarsa", "Kedaluwarsa")
    if (products.isEmpty()) sumRows += listOf("Belum ada produk aktif.") else products.forEach { sumRows += listOf(it.nama, it.kodeKategori, it.stokSaatIni, it.stokLayak, angkaRincianEd(it.rincianEd, "ED Hari Ini").toString(), angkaRincianEd(it.rincianEd, "Hampir Kedaluwarsa").toString(), angkaRincianEd(it.rincianEd, "Kedaluwarsa").toString()) }
    sheets += "Ringkasan" to sumRows
    val used = mutableSetOf("ringkasan")
    products.forEachIndexed { i, p ->
        val rows = mutableListOf<List<String>>()
        rows += listOf("MUTASI STOK PRODUK")
        rows += listOf("Produk", p.nama)
        rows += listOf("Layak Jual", p.stokLayak)
        rows += emptyList<String>()
        rows += listOf("Tanggal Transaksi", "Uraian Transaksi", "User", "Masuk", "Keluar", "Saldo", "Catatan")
        if (p.mutasi.isEmpty()) rows += listOf("Belum ada mutasi.") else p.mutasi.forEach { rows += listOf(it.tanggal, it.uraian, it.user, it.masuk, it.keluar, it.saldo, it.catatan) }
        sheets += uniqueXlsxSheetName(p.nama, "P${i + 1}", used) to rows
    }
    return buildXlsxWorkbook(sheets)
}

private fun buildMutasiRiwayatXlsx(
    identitas: PengaturanUsahaCache.IdentitasUsaha,
    rangeLabel: String,
    jenisFilter: String,
    rows: List<RepositoriFirebaseUtama.BarisRiwayatTransaksi>
): ByteArray {
    val namaUsaha = identitas.namaUsaha.ifBlank { "SI Tahu" }
    val sheetRows = mutableListOf<List<String>>()
    sheetRows += listOf("MUTASI LAPORAN SI TAHU")
    sheetRows += listOf("Nama Usaha", namaUsaha)
    if (identitas.alamat.isNotBlank()) sheetRows += listOf("Alamat", identitas.alamat)
    if (identitas.nomorTelepon.isNotBlank()) sheetRows += listOf("Telepon", identitas.nomorTelepon)
    sheetRows += listOf("Periode", rangeLabel)
    sheetRows += listOf("Jenis Riwayat", jenisFilter)
    sheetRows += listOf("Tanggal Cetak", SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date()))
    sheetRows += emptyList<String>()
    sheetRows += listOf("Tanggal", "Jenis", "Status", "Uraian", "User", "Nominal")
    if (rows.isEmpty()) {
        sheetRows += listOf("-", "-", "-", "Belum ada data pada filter ini.", "-", "-")
    } else {
        rows.forEach { row ->
            sheetRows += listOf(
                Formatter.readableDateTime(row.tanggalIso),
                row.jenis,
                row.status.ifBlank { row.badge.ifBlank { "Tercatat" } },
                row.title.ifBlank { row.subtitle },
                row.userName,
                row.amount.ifBlank { "-" }
            )
        }
    }
    return buildXlsxWorkbook(listOf("Mutasi" to sheetRows))
}

private fun uniqueXlsxSheetName(base: String, fallback: String, used: MutableSet<String>): String {
    var c = safeWorksheetName(base, fallback); var count = 2
    while (!used.add(c.lowercase(Locale.US))) { val suf = " $count"; c = safeWorksheetName(base.take((31 - suf.length).coerceAtLeast(1)) + suf, fallback); count++ }
    return c
}
private fun safeWorksheetName(value: String, fallback: String): String = value.ifBlank { fallback }.replace(Regex("[\\[\\]\\*\\?/\\:]"), " ").trim().take(31).ifBlank { fallback }
private fun xlsxEscape(value: String): String = buildString(value.length) { value.forEach { if (it == '\t' || it == '\n' || it == '\r' || it.toInt() >= 32) append(it) } }.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
private fun xlsxColumnName(index: Int): String { var v = index + 1; val b = StringBuilder(); while (v > 0) { val r = (v - 1) % 26; b.insert(0, ('A'.toInt() + r).toChar()); v = (v - 1) / 26 }; return b.toString() }

private fun buildXlsxWorkbook(sheets: List<Pair<String, List<List<String>>>>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("[Content_Types].xml")); zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>${sheets.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" }}</Types>""".toByteArray(Charsets.UTF_8)); zip.closeEntry()
        zip.putNextEntry(ZipEntry("_rels/.rels")); zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""".toByteArray(Charsets.UTF_8)); zip.closeEntry()
        zip.putNextEntry(ZipEntry("xl/workbook.xml")); zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>${sheets.mapIndexed { i, p -> "<sheet name=\"${xlsxEscape(p.first)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("")}</sheets></workbook>""".toByteArray(Charsets.UTF_8)); zip.closeEntry()
        zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels")); zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${sheets.mapIndexed { i, _ -> "<Relationship Id=\"rId${i + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${i + 1}.xml\"/>" }.joinToString("")}</Relationships>""".toByteArray(Charsets.UTF_8)); zip.closeEntry()
        zip.putNextEntry(ZipEntry("xl/styles.xml")); zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="1"><font><sz val="10"/><name val="Calibri"/></font></fonts><cellXfs count="1"><xf numFmtId="0" fontId="0" applyAlignment="1"><alignment wrapText="1" vertical="top"/></xf></cellXfs></styleSheet>""".toByteArray(Charsets.UTF_8)); zip.closeEntry()
        sheets.forEachIndexed { i, p ->
            val maxCol = p.second.maxOfOrNull { it.size } ?: 1
            val cols = (0 until maxCol).joinToString("") { "<col min=\"${it + 1}\" max=\"${it + 1}\" width=\"24\" customWidth=\"1\"/>" }
            val rows = p.second.mapIndexed { ri, r -> "<row r=\"${ri + 1}\">" + r.mapIndexed { ci, v -> "<c r=\"${xlsxColumnName(ci)}${ri + 1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xlsxEscape(v)}</t></is></c>" }.joinToString("") + "</row>" }.joinToString("")
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet${i + 1}.xml")); zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><cols>$cols</cols><sheetData>$rows</sheetData></worksheet>""".toByteArray(Charsets.UTF_8)); zip.closeEntry()
        }
    }
    return out.toByteArray()
}

private fun buildMutasiRiwayatPdf(
    identitas: PengaturanUsahaCache.IdentitasUsaha,
    rangeLabel: String,
    jenisFilter: String,
    rows: List<RepositoriFirebaseUtama.BarisRiwayatTransaksi>
): PdfDocument {
    val namaUsaha = identitas.namaUsaha.ifBlank { "SI Tahu" }
    return createLandscapePdf("Mutasi Laporan SI Tahu") { state ->
        state.drawTitle(namaUsaha.uppercase(Locale.US))
        if (identitas.alamat.isNotBlank()) state.drawMeta("Alamat", identitas.alamat)
        if (identitas.nomorTelepon.isNotBlank()) state.drawMeta("Telepon", identitas.nomorTelepon)
        state.drawTitle("MUTASI LAPORAN")
        state.drawMeta("Periode", rangeLabel)
        state.drawMeta("Jenis Riwayat", jenisFilter)
        state.drawMeta("Tanggal Cetak", SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date()))
        state.drawMeta("Jumlah Data", rows.size.toString())
        state.space(8f)
        val c = floatArrayOf(112f, 82f, 80f, 238f, 92f, 122f)
        val h = listOf("Tanggal", "Jenis", "Status", "Uraian", "User", "Nominal")
        val align = listOf(0, 0, 0, 0, 0, 1)
        state.drawTableHeader(h, c, align)
        if (rows.isEmpty()) {
            state.drawTableRow(listOf("-", "-", "-", "Belum ada data pada filter ini", "-", "-"), c, align) { state.drawTableHeader(h, c, align) }
        } else {
            rows.forEach { row ->
                state.drawTableRow(
                    listOf(
                        Formatter.readableDateTime(row.tanggalIso),
                        row.jenis,
                        row.status.ifBlank { row.badge.ifBlank { "Tercatat" } },
                        row.title.ifBlank { row.subtitle },
                        row.userName,
                        row.amount.ifBlank { "-" }
                    ),
                    c,
                    align
                ) { state.drawTableHeader(h, c, align) }
            }
        }
    }
}

private fun buildBukuHarianPdf(title: String, text: String): PdfDocument {
    val (meta, rows) = parseBukuRows(text)
    return createLandscapePdf(title) { state ->
        state.drawTitle("BUKU HARIAN SI TAHU")
        state.drawMeta("Tanggal Laporan", meta["tanggal"].orEmpty())
        state.drawMeta("Periode Transaksi", meta["periode"].orEmpty())
        state.drawMeta("Saldo Awal", meta["saldoAwal"].orEmpty())
        state.space(10f)
        val c = floatArrayOf(96f, 300f, 88f, 86f, 86f, 86f); val h = listOf("Tanggal", "Uraian", "User", "Debit", "Kredit", "Saldo")
        state.drawTableHeader(h, c, listOf(0, 0, 0, 1, 1, 1))
        rows.forEach { state.drawTableRow(listOf(it.tanggal, it.uraian, it.user, it.debit, it.kredit, it.saldo), c, listOf(0, 0, 0, 1, 1, 1)) { state.drawTableHeader(h, c, listOf(0, 0, 0, 1, 1, 1)) } }
        state.space(8f)
        state.drawMeta("Saldo Akhir", meta["saldo"].orEmpty())
    }
}
private fun buildStokProdukPdf(title: String, text: String): PdfDocument {
    val (meta, products) = parseStokProduk(text)
    return createLandscapePdf(title) { state ->
        state.drawTitle("MUTASI STOK PRODUK SI TAHU")
        state.drawMeta("Tanggal Laporan", meta["tanggal"].orEmpty())
        val c = floatArrayOf(92f, 318f, 82f, 78f, 78f, 78f); val h = listOf("Tanggal", "Uraian", "User", "Masuk", "Keluar", "Saldo")
        products.forEachIndexed { i, p ->
            if (i > 0) state.newPage()
            state.drawTitle(p.nama); state.drawTableHeader(h, c, listOf(0, 0, 0, 1, 1, 1))
            p.mutasi.forEach { state.drawTableRow(listOf(it.tanggal, it.uraian, it.user, it.masuk, it.keluar, it.saldo), c, listOf(0, 0, 0, 1, 1, 1)) { state.drawTableHeader(h, c, listOf(0, 0, 0, 1, 1, 1)) } }
        }
    }
}
private fun buildPlainTextPdf(title: String, text: String): PdfDocument = createLandscapePdf(title) { state -> state.drawTitle(title); text.lineSequence().forEach { state.drawWrappedText(it, state.bodyPaint, state.contentWidth) } }

private fun parseBukuRows(text: String): Pair<Map<String, String>, List<PdfBukuRow>> {
    val meta = mutableMapOf<String, String>(); val rows = mutableListOf<PdfBukuRow>()
    text.lineSequence().forEach { val l = it.trimEnd(); when {
        l.startsWith("@@TANGGAL=") -> meta["tanggal"] = l.substringAfter("=").trim()
        l.startsWith("@@PERIODE=") -> meta["periode"] = l.substringAfter("=").trim()
        l.startsWith("@@SALDO_AWAL=") -> meta["saldoAwal"] = l.substringAfter("=").trim()
        l.startsWith("@@SALDO=") -> meta["saldo"] = l.substringAfter("=").trim()
        l.startsWith("@@ROW=") -> { val f = l.substringAfter("=").trim().split('	'); rows += PdfBukuRow(f.getOrElse(0){""}, f.getOrElse(1){""}, f.getOrElse(2){""}, f.getOrElse(3){""}, f.getOrElse(4){""}, f.getOrElse(5){""}) }
    } }
    return meta to rows
}
private fun parseStokProduk(text: String): Pair<Map<String, String>, List<PdfStokProduk>> {
    val meta = mutableMapOf<String, String>()
    val products = mutableListOf<PdfStokProduk>()
    var namaProduk = ""
    var kodeKategori = ""
    var stokSaatIni = ""
    var stokLayak = ""
    var rincianEd = ""
    var rows = mutableListOf<PdfStokRow>()

    fun flush() {
        if (namaProduk.isNotBlank()) {
            products += PdfStokProduk(
                nama = namaProduk,
                kodeKategori = kodeKategori,
                stokSaatIni = stokSaatIni,
                stokLayak = stokLayak,
                rincianEd = rincianEd,
                mutasi = rows.toList()
            )
        }
        namaProduk = ""
        kodeKategori = ""
        stokSaatIni = ""
        stokLayak = ""
        rincianEd = ""
        rows = mutableListOf()
    }

    text.lineSequence().forEach { line ->
        val l = line.trimEnd()
        when {
            l.startsWith("@@TANGGAL=") -> meta["tanggal"] = l.substringAfter("=").trim()
            l.startsWith("@@PERIODE=") -> meta["periode"] = l.substringAfter("=").trim()
            l.startsWith("@@PRODUCT_BEGIN") -> flush()
            l.startsWith("@@PRODUK=") -> namaProduk = l.substringAfter("=").trim()
            l.startsWith("@@KODE_KATEGORI=") -> kodeKategori = l.substringAfter("=").trim()
            l.startsWith("@@STOK_SAAT_INI=") -> stokSaatIni = l.substringAfter("=").trim()
            l.startsWith("@@STOK_LAYAK=") -> stokLayak = l.substringAfter("=").trim()
            l.startsWith("@@RINCIAN_ED=") -> rincianEd = l.substringAfter("=").trim()
            l.startsWith("@@ROW=") -> {
                val f = l.substringAfter("=").trim().split('	')
                rows += PdfStokRow(
                    tanggal = f.getOrElse(0) { "" },
                    uraian = f.getOrElse(1) { "" },
                    user = f.getOrElse(2) { "" },
                    masuk = f.getOrElse(3) { "" },
                    keluar = f.getOrElse(4) { "" },
                    saldo = f.getOrElse(5) { "" },
                    catatan = f.getOrElse(6) { "" }
                )
            }
            l.startsWith("@@PRODUCT_END") -> flush()
        }
    }
    flush()
    return meta to products
}
private fun angkaPertama(value: String): Long = Regex("-?\\d+").find(value.replace(".", ""))?.value?.toLongOrNull() ?: 0L
private fun angkaRincianEd(value: String, label: String): Long = Regex("$label\\s+([0-9.]+)", RegexOption.IGNORE_CASE).find(value)?.groupValues?.getOrNull(1)?.replace(".", "")?.toLongOrNull() ?: 0L

private class PdfPageState(val pdf: PdfDocument, val title: String) {
    val w = 842f; val h = 595f; val m = 44f; val contentWidth = w - m * 2; var y = m; var pageNum = 0; lateinit var page: PdfDocument.Page; lateinit var canvas: android.graphics.Canvas
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.BLACK; textSize = 8.4f }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.BLACK; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textSize = 12f }
    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.BLACK; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textSize = 8.4f }
    fun newPage() { if (pageNum > 0) pdf.finishPage(page); pageNum++; page = pdf.startPage(PdfDocument.PageInfo.Builder(w.toInt(), h.toInt(), pageNum).create()); canvas = page.canvas; y = m }
    fun ensureSpace(req: Float, rh: (() -> Unit)? = null) { if (y + req > h - m) { newPage(); rh?.invoke() } }
    fun space(dy: Float) { y += dy }
    fun drawTitle(t: String) { ensureSpace(20f); canvas.drawText(t, m, y, titlePaint); y += 20f }
    fun drawMeta(l: String, v: String) { ensureSpace(14f); canvas.drawText("$l: $v", m, y, bodyPaint); y += 14f }
    fun drawTableHeader(h: List<String>, cw: FloatArray, a: List<Int>) { ensureSpace(16f); var x = m; h.forEachIndexed { i, txt -> canvas.drawText(txt, x, y, headerPaint); x += cw[i] }; y += 16f }
    fun drawTableRow(r: List<String>, cw: FloatArray, a: List<Int>, rh: (() -> Unit)? = null) { ensureSpace(12f, rh); var x = m; r.forEachIndexed { i, txt -> val len = bodyPaint.measureText(txt); val drawX = if (a[i] == 1) x + cw[i] - len - 10f else x; canvas.drawText(txt.take(50), drawX, y, bodyPaint); x += cw[i] }; y += 12f }
    fun drawWrappedText(t: String, p: Paint, mw: Float) { ensureSpace(14f); canvas.drawText(t.take(120), m, y, p); y += 14f }
}
private fun createLandscapePdf(title: String, block: (PdfPageState) -> Unit): PdfDocument { val p = PdfDocument(); val s = PdfPageState(p, title); s.newPage(); block(s); p.finishPage(s.page); return p }