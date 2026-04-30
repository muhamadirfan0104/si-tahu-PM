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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
                    activityContext = this@AktivitasLaporan
                )
            }
        }
    }

    private fun safeRangeName(key: String): String = key.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun exportBukuHarianExcel(rangeKey: String, setLoading: (Boolean) -> Unit) {
        lifecycleScope.launch {
            setLoading(true)
            showMessage("Menyiapkan Excel buku harian...")
            runCatching {
                val text = RepositoriFirebaseUtama.buildBukuHarianPdfText(rangeKey)
                buildBukuHarianXlsxFromText(text)
            }.onSuccess { bytes ->
                setLoading(false)
                saveBytesToDownloads("buku_harian_sitahu_${safeRangeName(rangeKey)}.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes)
            }.onFailure {
                setLoading(false)
                showMessage(it.message ?: "Gagal membuat buku harian Excel")
            }
        }
    }

    private fun exportBukuHarianPdf(rangeKey: String, setLoading: (Boolean) -> Unit) {
        lifecycleScope.launch {
            setLoading(true)
            showMessage("Menyiapkan PDF buku harian...")
            runCatching {
                val text = RepositoriFirebaseUtama.buildBukuHarianPdfText(rangeKey)
                savePdfTextToDownloads("Download Buku Harian PDF", "buku_harian_sitahu_${safeRangeName(rangeKey)}.pdf", text)
            }.onSuccess { setLoading(false) }.onFailure {
                setLoading(false)
                showMessage(it.message ?: "Gagal membuat buku harian PDF")
            }
        }
    }

    private fun exportStokExcel(rangeKey: String, setLoading: (Boolean) -> Unit) {
        lifecycleScope.launch {
            setLoading(true)
            showMessage("Menyiapkan Excel stok produk...")
            runCatching {
                val text = RepositoriFirebaseUtama.buildStokProdukPdfText(rangeKey)
                buildStokProdukXlsxFromText(text)
            }.onSuccess { bytes ->
                setLoading(false)
                saveBytesToDownloads("stok_produk_sitahu_${safeRangeName(rangeKey)}.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes)
            }.onFailure {
                setLoading(false)
                showMessage(it.message ?: "Gagal membuat stok produk Excel")
            }
        }
    }

    private fun exportStokPdf(rangeKey: String, setLoading: (Boolean) -> Unit) {
        lifecycleScope.launch {
            setLoading(true)
            showMessage("Menyiapkan PDF stok produk...")
            runCatching {
                val text = RepositoriFirebaseUtama.buildStokProdukPdfText(rangeKey)
                savePdfTextToDownloads("Download Stok Produk PDF", "stok_produk_sitahu_${safeRangeName(rangeKey)}.pdf", text)
            }.onSuccess { setLoading(false) }.onFailure {
                setLoading(false)
                showMessage(it.message ?: "Gagal membuat stok produk PDF")
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
                showMessage("Tersimpan di Download/$folderName: $fileName")
            } catch (e: Exception) {
                resolver.delete(uri, null, null); throw e
            }
        } else {
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), folderName)
            if (!directory.exists()) directory.mkdirs()
            val file = File(directory, fileName)
            FileOutputStream(file).use { output -> output.write(bytes) }
            MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf(mimeType), null)
            showMessage("Tersimpan di Download/$folderName: $fileName")
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
    activityContext: AppCompatActivity
) {
    val rangeOptions = listOf("Hari ini" to "hari_ini", "7 hari" to "7", "14 hari" to "14", "30 hari" to "30", "90 hari" to "90", "6 bulan" to "180", "1 tahun" to "365", "Semua" to "semua")
    var selectedRange by remember { mutableStateOf(rangeOptions.first()) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }

    // Bottom Sheet States
    var showExportSheet by remember { mutableStateOf(false) }
    var showStatsSheet by remember { mutableStateOf(false) }

    var draftTanggalMulai by remember { mutableStateOf("") }
    var draftTanggalSelesai by remember { mutableStateOf("") }

    var reportData by remember { mutableStateOf<RepositoriFirebaseUtama.RingkasanLaporanFirebase?>(null) }
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
    val warningColor = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)
    val purpleColor = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED)

    val labelBulanFormatter = remember { SimpleDateFormat("MMMM yyyy", Locale("id", "ID")) }

    fun pilihBulanCustom() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            activityContext,
            { _, year, month, _ ->
                val picked = Calendar.getInstance().apply { set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
                val bulanKey = SimpleDateFormat("yyyy-MM", Locale.US).format(picked.time)
                selectedRange = "Bulan ${labelBulanFormatter.format(picked.time)}" to "bulan:$bulanKey"
                showFilterDialog = false
            },
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), 1
        ).show()
    }

    LaunchedEffect(selectedRange) {
        isLoading = true
        runCatching { RepositoriFirebaseUtama.muatLaporan(selectedRange.second) }
            .onSuccess { report -> reportData = report; isLoading = false }
            .onFailure { e -> isLoading = false; onShowMessage(e.message ?: "Gagal memuat laporan") }
    }

    Scaffold(
        topBar = {
            Surface(color = surfaceColor, shadowElevation = if (isDark) 0.dp else 4.dp, border = if (isDark) BorderStroke(1.dp, borderColor) else null) {
                TopAppBar(
                    title = {
                        Column {
                            Text("Pusat Laporan", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text(reportData?.rangeLabel ?: "Ringkasan & Analitik", style = MaterialTheme.typography.labelMedium, color = mutedColor)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Rounded.ArrowBack, "Kembali", tint = textColor) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        containerColor = bgColor
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // --- HEADER FILTER STICKY ---
            Surface(shape = RoundedCornerShape(100), color = surfaceColor, border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp).clickable { showFilterDialog = true }) {
                Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Periode Analisis Laporan", color = mutedColor, style = MaterialTheme.typography.labelSmall)
                        Text(selectedRange.first, fontWeight = FontWeight.Bold, color = primaryColor, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                    Surface(shape = CircleShape, color = primaryColor.copy(alpha = 0.12f), modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.FilterList, "Filter Waktu", tint = primaryColor, modifier = Modifier.size(22.dp)) }
                    }
                }
            }

            if (isLoading) {
                DashboardSkeleton(surfaceColor, borderColor)
            } else if (reportData != null) {
                val report = reportData!!

                // --- DASHBOARD CARDS ---
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val isProfit = report.labaRugi >= 0
                    val profitColor = if (isProfit) successColor else dangerColor
                    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = profitColor), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Icon(if (isProfit) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                            Column {
                                Text(if (isProfit) "Laba Bersih Operasional" else "Rugi Bersih Operasional", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(Formatter.currency(report.labaRugi), fontWeight = FontWeight.Black, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MetricCard(Modifier.weight(1f), "Pemasukan", Formatter.currency(report.totalPenjualan), Icons.Rounded.Payments, primaryColor, surfaceColor, borderColor, textColor, mutedColor)
                        MetricCard(Modifier.weight(1f), "Pengeluaran", Formatter.currency(report.totalPengeluaran), Icons.Rounded.ReceiptLong, warningColor, surfaceColor, borderColor, textColor, mutedColor)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MetricCard(Modifier.weight(1f), "Total Produksi", "${Formatter.ribuan(report.totalProduksi.toLong())} pcs", Icons.Rounded.Factory, successColor, surfaceColor, borderColor, textColor, mutedColor)
                        MetricCard(Modifier.weight(1f), "Item Terjual", "${Formatter.ribuan(report.totalItemTerjual.toLong())} pcs", Icons.Rounded.PointOfSale, primaryColor, surfaceColor, borderColor, textColor, mutedColor)
                    }
                }
            }

            // --- KARTU MENU AKSI UTAMA ---
            Column {
                Text("Pusat Analitik & Laporan", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 16.dp))
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Column {
                        ActionMenuItem(Icons.Rounded.Description, successColor, "Export Laporan Excel", "Unduh buku transaksi, laporan, dan stok", textColor, mutedColor) { showExportSheet = true }
                        HorizontalDivider(color = borderColor)
                        ActionMenuItem(Icons.Rounded.PieChart, warningColor, "Grafik & Statistik", "Analisis tren penjualan, laba, dan performa", textColor, mutedColor) { showStatsSheet = true }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }

        // === DIALOG FILTER WAKTU (POP-UP TENGAH) ===
        if (showFilterDialog) {
            var draftRange by remember { mutableStateOf(selectedRange) }
            AlertDialog(
                onDismissRequest = { showFilterDialog = false }, shape = RoundedCornerShape(24.dp), containerColor = surfaceColor,
                title = { Text("Pilih Periode Laporan", fontWeight = FontWeight.Bold, color = textColor) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Periode Waktu Cepat", fontWeight = FontWeight.SemiBold, color = textColor, style = MaterialTheme.typography.bodyMedium)
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rangeOptions.forEach { pilihan ->
                                val isSelected = draftRange.second == pilihan.second
                                Surface(shape = RoundedCornerShape(12.dp), color = if (isSelected) primaryColor.copy(alpha = 0.15f) else bgColor, border = BorderStroke(1.dp, if (isSelected) primaryColor else borderColor), modifier = Modifier.clickable { draftRange = pilihan }) {
                                    Text(pilihan.first, color = if (isSelected) primaryColor else textColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        HorizontalDivider(color = borderColor)
                        Text("Filter Kalender Spesifik", fontWeight = FontWeight.SemiBold, color = textColor, style = MaterialTheme.typography.bodyMedium)
                        Surface(modifier = Modifier.fillMaxWidth().clickable { showFilterDialog = false; draftTanggalMulai = ""; draftTanggalSelesai = ""; showDateRangePicker = true }, shape = RoundedCornerShape(12.dp), color = bgColor, border = BorderStroke(1.dp, borderColor)) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Rounded.DateRange, contentDescription = null, tint = mutedColor)
                                Text("Rentang Tanggal Custom", color = textColor, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Surface(modifier = Modifier.fillMaxWidth().clickable { showFilterDialog = false; showMonthPicker = true }, shape = RoundedCornerShape(12.dp), color = bgColor, border = BorderStroke(1.dp, borderColor)) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Rounded.DateRange, contentDescription = null, tint = mutedColor)
                                Text("Pilih Satu Bulan Penuh", color = textColor, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = { Button(onClick = { selectedRange = draftRange; showFilterDialog = false }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) { Text("Terapkan", fontWeight = FontWeight.Bold) } },
                dismissButton = { TextButton(onClick = { showFilterDialog = false }) { Text("Batal", color = mutedColor) } }
            )
        }

        // === DIALOG DATE RANGE PICKER (POP-UP KALENDER M3) ===
        if (showDateRangePicker) {
            val localFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val utcFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val labelTanggalFormatterRange = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
            val dateRangePickerState = rememberDateRangePickerState()
            var selectedPreset by remember { mutableStateOf("") }

            Dialog(onDismissRequest = { showDateRangePicker = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.90f), shape = RoundedCornerShape(24.dp), color = surfaceColor) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Pilih Rentang Tanggal", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
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
                                    } else {
                                        onShowMessage("Pilih rentang tanggal terlebih dahulu")
                                    }
                                },
                                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                            ) { Text("Simpan", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }

        // === DIALOG CUSTOM MONTH PICKER (GRID) ===
        if (showMonthPicker) {
            val months = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Ags", "Sep", "Okt", "Nov", "Des")
            var tempYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
            var tempMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
            Dialog(onDismissRequest = { showMonthPicker = false }) {
                Surface(shape = RoundedCornerShape(24.dp), color = surfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Pilih Bulan & Tahun", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
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

        // === BOTTOM SHEET EXPORT LAPORAN ===
        if (showExportSheet) {
            var loadingKey by remember { mutableStateOf<String?>(null) }
            val setExportLoad: (String?) -> (Boolean) -> Unit = { key -> { active -> loadingKey = if (active) key else null } }

            ModalBottomSheet(onDismissRequest = { showExportSheet = false }, containerColor = bgColor, dragHandle = { BottomSheetDefaults.DragHandle() }, windowInsets = WindowInsets.navigationBars) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Export Laporan (${selectedRange.first})", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)

                    Text("Buku Harian", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor)) {
                        Column {
                            ActionMenuItem(Icons.Rounded.ReceiptLong, successColor, "Buku Harian Excel (.xlsx)", "Berisi data penjualan dan pengeluaran", textColor, mutedColor, loadingKey == "bx") { onExportBukuHarianExcel(selectedRange.second, setExportLoad("bx")) }
                            HorizontalDivider(color = borderColor)
                            ActionMenuItem(Icons.Rounded.Description, dangerColor, "Buku Harian PDF (.pdf)", "Tabel debit kredit siap cetak", textColor, mutedColor, loadingKey == "bp") { onExportBukuHarianPdf(selectedRange.second, setExportLoad("bp")) }
                        }
                    }

                    Text("Stok Produk", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor)) {
                        Column {
                            ActionMenuItem(Icons.Rounded.Inventory, warningColor, "Stok Produk Excel (.xlsx)", "Mutasi dipisah per produk", textColor, mutedColor, loadingKey == "sx") { onExportStokExcel(selectedRange.second, setExportLoad("sx")) }
                            HorizontalDivider(color = borderColor)
                            ActionMenuItem(Icons.Rounded.Description, dangerColor, "Stok Produk PDF (.pdf)", "Tabel mutasi stok per produk", textColor, mutedColor, loadingKey == "sp") { onExportStokPdf(selectedRange.second, setExportLoad("sp")) }
                        }
                    }
                }
            }
        }

        // === BOTTOM SHEET STATISTIK (DARI AKTIVITAS STATISTIK) ===
        if (showStatsSheet) {
            var statsData by remember { mutableStateOf<DataStatistikLengkap?>(null) }
            var isStatsLoading by remember { mutableStateOf(true) }

            LaunchedEffect(selectedRange) {
                isStatsLoading = true
                runCatching {
                    val lapor = RepositoriFirebaseUtama.muatLaporan(selectedRange.second)
                    val riw = RepositoriFirebaseUtama.muatRiwayatTransaksi(lapor.rangeKey)
                    val stk = RepositoriFirebaseUtama.muatRingkasanStokDashboard()
                    bangunStatistikLengkap(lapor, stk, riw)
                }.onSuccess { data -> statsData = data; isStatsLoading = false }
                    .onFailure { e -> isStatsLoading = false; onShowMessage(e.message ?: "Gagal muat statistik") }
            }

            ModalBottomSheet(onDismissRequest = { showStatsSheet = false }, containerColor = bgColor, dragHandle = { BottomSheetDefaults.DragHandle() }, windowInsets = WindowInsets.navigationBars) {
                if (isStatsLoading) {
                    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = warningColor) }
                } else if (statsData != null) {
                    val d = statsData!!
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("Analitik & Statistik (${selectedRange.first})", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))

                        Text("Analisis AI Otomatis", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp))
                        AnalisisAiCard(d.analisisAi, surfaceColor, borderColor, textColor, mutedColor, primaryColor, successColor, warningColor, dangerColor)
                        InsightList(d.insight, surfaceColor, borderColor, textColor, mutedColor, primaryColor)

                        Text("Indikator Detail", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp))
                        IndikatorDetailCard(d.indikatorDetail, surfaceColor, borderColor, textColor, mutedColor, primaryColor, successColor, warningColor, dangerColor)

                        Text("Tren Harian", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp))
                        TrendHarianCard(d.trenHarian, surfaceColor, borderColor, textColor, mutedColor, primaryColor, warningColor)

                        Text("Produk Terlaris", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp))
                        AnalitikList("Belum ada produk terjual.", d.laporan.produkTerlaris, surfaceColor, borderColor, textColor, mutedColor, primaryColor)

                        Text("Kesehatan Stok", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp))
                        StokAnalysisCard(d.stok, surfaceColor, borderColor, textColor, mutedColor, successColor, warningColor, dangerColor)

                        Text("Rekomendasi Operasional", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp))
                        InsightList(d.rekomendasi, surfaceColor, borderColor, textColor, mutedColor, successColor)
                    }
                }
            }
        }
    }
}

// === KOMPONEN BANTUAN UI ===
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
private fun DashboardSkeleton(surfaceColor: Color, borderColor: Color) {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth().height(100.dp)) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(56.dp).clip(CircleShape).adminShimmerEffect())
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.height(16.dp).fillMaxWidth(0.5f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect()); Box(Modifier.height(24.dp).fillMaxWidth(0.7f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect()) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(2) {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).height(120.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(40.dp).clip(CircleShape).adminShimmerEffect())
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.height(14.dp).fillMaxWidth(0.6f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect()); Box(Modifier.height(20.dp).fillMaxWidth(0.9f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect()) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(2) {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).height(120.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(40.dp).clip(CircleShape).adminShimmerEffect())
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.height(14.dp).fillMaxWidth(0.6f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect()); Box(Modifier.height(20.dp).fillMaxWidth(0.9f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect()) }
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
            Text("Aman ${Formatter.ribuan(stok.totalStokAman)} pcs • ED/Hampir ED ${Formatter.ribuan(stok.totalHampirKadaluarsa)} pcs • Kadaluarsa ${Formatter.ribuan(stok.totalKadaluarsa)} pcs", color = mutedColor)
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
    if (stok.totalKadaluarsa > 0) rekomendasi += "Segera tindak ${Formatter.ribuan(stok.totalKadaluarsa)} pcs stok kadaluarsa agar tidak tercampur dengan stok layak jual."
    if (stok.totalHampirKadaluarsa > 0) rekomendasi += "Prioritaskan penjualan ${Formatter.ribuan(stok.totalHampirKadaluarsa)} pcs stok ED hari ini/hampir ED dengan prinsip stok lama keluar lebih dulu."
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
    if (stok.totalKadaluarsa > 0) prioritas += "Tindak stok kadaluarsa: ${Formatter.ribuan(stok.totalKadaluarsa)} pcs perlu dipisah."
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
    sheetRows += emptyList<String>()
    sheetRows += listOf("Tanggal Transaksi", "Uraian Transaksi", "User", "Debit/Pengeluaran", "Kredit/Pemasukan", "Saldo")
    if (rows.isEmpty()) sheetRows += listOf("Belum ada data pada periode ini.") else rows.forEach { sheetRows += listOf(it.tanggal, it.uraian, it.user, it.debit, it.kredit, it.saldo) }
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
    sumRows += listOf("Produk", "Kode", "Fisik", "Layak", "ED", "Hampir ED", "Kadaluarsa")
    if (products.isEmpty()) sumRows += listOf("Belum ada produk aktif.") else products.forEach { sumRows += listOf(it.nama, it.kodeKategori, it.stokSaatIni, it.stokLayak, angkaRincianEd(it.rincianEd, "ED Hari Ini").toString(), angkaRincianEd(it.rincianEd, "Hampir ED").toString(), angkaRincianEd(it.rincianEd, "Kadaluarsa").toString()) }
    sheets += "Ringkasan" to sumRows
    val used = mutableSetOf("ringkasan")
    products.forEachIndexed { i, p ->
        val rows = mutableListOf<List<String>>()
        rows += listOf("MUTASI STOK PRODUK")
        rows += listOf("Produk", p.nama)
        rows += listOf("Stok Layak", p.stokLayak)
        rows += emptyList<String>()
        rows += listOf("Tanggal Transaksi", "Uraian Transaksi", "User", "Masuk", "Keluar", "Saldo", "Catatan")
        if (p.mutasi.isEmpty()) rows += listOf("Belum ada mutasi.") else p.mutasi.forEach { rows += listOf(it.tanggal, it.uraian, it.user, it.masuk, it.keluar, it.saldo, it.catatan) }
        sheets += uniqueXlsxSheetName(p.nama, "P${i + 1}", used) to rows
    }
    return buildXlsxWorkbook(sheets)
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

private fun buildBukuHarianPdf(title: String, text: String): PdfDocument {
    val (meta, rows) = parseBukuRows(text)
    return createLandscapePdf(title) { state ->
        state.drawTitle("BUKU HARIAN SI TAHU")
        state.drawMeta("Tanggal Laporan", meta["tanggal"].orEmpty())
        state.space(10f)
        val c = floatArrayOf(96f, 300f, 88f, 86f, 86f, 86f); val h = listOf("Tanggal", "Uraian", "User", "Debit", "Kredit", "Saldo")
        state.drawTableHeader(h, c, listOf(0, 0, 0, 1, 1, 1))
        rows.forEach { state.drawTableRow(listOf(it.tanggal, it.uraian, it.user, it.debit, it.kredit, it.saldo), c, listOf(0, 0, 0, 1, 1, 1)) { state.drawTableHeader(h, c, listOf(0, 0, 0, 1, 1, 1)) } }
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
    text.lineSequence().forEach { val l = it.trimEnd(); when { l.startsWith("@@TANGGAL=") -> meta["tanggal"] = l.substringAfter("=").trim(); l.startsWith("@@ROW=") -> { val f = l.substringAfter("=").trim().split('\t'); rows += PdfBukuRow(f.getOrElse(0){""}, f.getOrElse(1){""}, f.getOrElse(2){""}, f.getOrElse(3){""}, f.getOrElse(4){""}, f.getOrElse(5){""}) } } }
    return meta to rows
}
private fun parseStokProduk(text: String): Pair<Map<String, String>, List<PdfStokProduk>> {
    val meta = mutableMapOf<String, String>(); val p = mutableListOf<PdfStokProduk>(); var n = ""; var rows = mutableListOf<PdfStokRow>()
    fun flush() { if (n.isNotBlank()) p += PdfStokProduk(n, "", "", "", "", rows.toList()); n = ""; rows.clear() }
    text.lineSequence().forEach { val l = it.trimEnd(); when { l.startsWith("@@TANGGAL=") -> meta["tanggal"] = l.substringAfter("=").trim(); l.startsWith("@@PRODUCT_BEGIN") -> flush(); l.startsWith("@@PRODUK=") -> n = l.substringAfter("=").trim(); l.startsWith("@@ROW=") -> { val f = l.substringAfter("=").trim().split('\t'); rows += PdfStokRow(f.getOrElse(0){""}, f.getOrElse(1){""}, f.getOrElse(2){""}, f.getOrElse(3){""}, f.getOrElse(4){""}, f.getOrElse(5){""}, "") }; l.startsWith("@@PRODUCT_END") -> flush() } }
    flush(); return meta to p
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