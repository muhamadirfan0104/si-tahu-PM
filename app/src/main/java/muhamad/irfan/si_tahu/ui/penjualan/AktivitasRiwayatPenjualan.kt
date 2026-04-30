package muhamad.irfan.si_tahu.ui.penjualan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.utama.SiTahuProTheme
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.PembuatQrBitmap
import muhamad.irfan.si_tahu.util.WarnaBaris
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

private const val XENDIT_API_BASE_RIWAYAT = "https://xendit-sitahu-api.vercel.app"

class AktivitasRiwayatPenjualan : AktivitasDasar() {

    private var judulLayar = "Riwayat Penjualan"
    private var subjudulLayar = "Rumahan dan pasar"
    private var defaultFilter = Companion.FILTER_SEMUA
    private var lockFilter = false
    private var tampilkanTombolTransaksiBaru = true
    private var tampilkanTombolRekapPasar = true
    private var izinkanHapus = true
    private var hanyaKasirLogin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        bacaIntent()

        setContent {
            SiTahuProTheme {
                SalesHistoryScreen(
                    title = judulLayar,
                    subtitle = subjudulLayar,
                    defaultFilter = defaultFilter,
                    lockFilter = lockFilter,
                    izinkanHapus = izinkanHapus,
                    hanyaKasirLogin = hanyaKasirLogin,
                    onNavigateBack = { finish() },
                    activityContext = this@AktivitasRiwayatPenjualan,
                    onShowMessage = { pesan -> showMessage(pesan) },
                    onLoadDetail = { item -> RepositoriFirebaseUtama.buildReceiptText(item.id) },
                    onShare = { item, text ->
                        lifecycleScope.launch {
                            sharePlainText("Penjualan ${item.title}", text)
                        }
                    },
                    onCancelSale = { item, onSuccess -> confirmCancel(item, onSuccess) },
                    onDeleteSale = { item, onSuccess -> confirmDelete(item, onSuccess) },
                    onCheckQrisStatus = { item, onSuccess -> cekStatusQrisPending(item, onSuccess) },
                    onGetQrisData = { id -> RepositoriFirebaseUtama.muatInfoQrisPending(id) },
                    getCurrentUserId = { currentUserId() }
                )
            }
        }
    }

    private fun bacaIntent() {
        judulLayar = intent.getStringExtra(EXTRA_SCREEN_TITLE).orEmpty().ifBlank { "Riwayat Penjualan" }
        subjudulLayar = intent.getStringExtra(EXTRA_SCREEN_SUBTITLE).orEmpty().ifBlank { "Rumahan dan pasar" }
        defaultFilter = intent.getStringExtra(EXTRA_DEFAULT_FILTER).orEmpty().ifBlank { Companion.FILTER_SEMUA }
        lockFilter = intent.getBooleanExtra(EXTRA_LOCK_FILTER, false)
        tampilkanTombolTransaksiBaru = intent.getBooleanExtra(EXTRA_SHOW_NEW_SALE_BUTTON, true)
        tampilkanTombolRekapPasar = intent.getBooleanExtra(EXTRA_SHOW_RECAP_BUTTON, true)
        izinkanHapus = intent.getBooleanExtra(EXTRA_ALLOW_DELETE, true)
        hanyaKasirLogin = intent.getBooleanExtra(EXTRA_ONLY_CURRENT_CASHIER, false)
    }

    private fun confirmCancel(item: ItemBaris, onSuccess: () -> Unit) {
        showInputModal("Batalkan penjualan", "Alasan pembatalan", "Batalkan") { alasan ->
            lifecycleScope.launch {
                runCatching { RepositoriFirebaseUtama.batalkanPenjualan(item.id, alasan, currentUserId()) }
                    .onSuccess {
                        showMessage("Penjualan berhasil dibatalkan")
                        onSuccess()
                    }
                    .onFailure { showMessage(it.message ?: "Gagal membatalkan penjualan") }
            }
        }
    }

    private fun confirmDelete(item: ItemBaris, onSuccess: () -> Unit) {
        confirmCancel(item, onSuccess)
    }

    private fun cekStatusQrisPending(item: ItemBaris, onSuccess: () -> Unit) {
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
                onSuccess()
                if (receipt != null) showReceiptModal("QRIS sudah dibayar", receipt)
                else showMessage("Pembayaran QRIS masih pending")
            }.onFailure {
                showMessage(it.message ?: "Gagal cek status QRIS")
            }
        }
    }

    private suspend fun bacaStatusXendit(externalId: String, fallbackAmount: Long): StatusXenditGateway = withContext(Dispatchers.IO) {
        val response = postJsonRiwayat("/api/cek-status-xendit", JSONObject().put("externalId", externalId))
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
            connection.outputStream.use { output -> output.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream ?: connection.inputStream
            val responseText = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            if (connection.responseCode !in 200..299) {
                val message = runCatching {
                    val json = JSONObject(responseText)
                    json.optString("message").ifBlank { json.optJSONObject("xendit")?.optString("message").orEmpty() }.ifBlank { responseText }
                }.getOrElse { responseText }
                throw IllegalStateException(message)
            }
            responseText
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val EXTRA_SCREEN_TITLE = "extra_screen_title"
        const val EXTRA_SCREEN_SUBTITLE = "extra_screen_subtitle"
        const val EXTRA_DEFAULT_FILTER = "extra_default_filter"
        const val EXTRA_LOCK_FILTER = "extra_lock_filter"
        const val EXTRA_SHOW_NEW_SALE_BUTTON = "extra_show_new_sale_button"
        const val EXTRA_SHOW_RECAP_BUTTON = "extra_show_recap_button"
        const val EXTRA_ALLOW_DELETE = "extra_allow_delete"
        const val EXTRA_ONLY_CURRENT_CASHIER = "extra_only_current_cashier"

        const val FILTER_SEMUA = "Semua"
        const val FILTER_RUMAHAN = "Rumahan"
        const val FILTER_PASAR = "Pasar"

        const val UI_FILTER_SEMUA = "Semua"
        const val UI_FILTER_KASIR = "Kasir"
        const val UI_FILTER_REKAP = "Rekap"
    }
}

// === KOMPONEN UI COMPOSE ===

// Extension untuk animasi Shimmer / Skeleton
private fun Modifier.adminShimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(), targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "shimmer_offsetX"
    )
    val isDark = isSystemInDarkTheme()
    val baseColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)
    val highlightColor = if (isDark) Color(0xFF4B5563) else Color(0xFFF3F4F6)
    background(brush = Brush.linearGradient(colors = listOf(baseColor, highlightColor, baseColor), start = Offset(startOffsetX, 0f), end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat()))).onGloballyPositioned { size = it.size }
}

data class RiwayatPenjualanUiRow(
    val tanggalIso: String,
    val item: ItemBaris
)

data class QrisDialogData(
    val item: ItemBaris,
    val info: RepositoriFirebaseUtama.QrisPendingInfo,
    val bitmap: android.graphics.Bitmap
)

private data class StatusXenditGateway(
    val paid: Boolean, val status: String, val paymentId: String, val source: String, val receiptId: String, val paidAt: String, val amount: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SalesHistoryScreen(
    title: String,
    subtitle: String,
    defaultFilter: String,
    lockFilter: Boolean,
    izinkanHapus: Boolean,
    hanyaKasirLogin: Boolean,
    onNavigateBack: () -> Unit,
    activityContext: AppCompatActivity,
    onShowMessage: (String) -> Unit,
    onLoadDetail: suspend (ItemBaris) -> String,
    onShare: (ItemBaris, String) -> Unit,
    onCancelSale: (ItemBaris, () -> Unit) -> Unit,
    onDeleteSale: (ItemBaris, () -> Unit) -> Unit,
    onCheckQrisStatus: (ItemBaris, () -> Unit) -> Unit,
    onGetQrisData: suspend (String) -> RepositoriFirebaseUtama.QrisPendingInfo,
    getCurrentUserId: () -> String
) {
    // State Data
    var rows by remember { mutableStateOf<List<RiwayatPenjualanUiRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var triggerRefresh by remember { mutableStateOf(0) }
    var qrisDialogData by remember { mutableStateOf<QrisDialogData?>(null) }

    // State Dialog Detail Struk
    var selectedDetailItem by remember { mutableStateOf<ItemBaris?>(null) }
    var detailText by remember { mutableStateOf("") }
    var isLoadingDetail by remember { mutableStateOf(false) }

    // State Filter Dialog Modern
    var showFilterDialog by remember { mutableStateOf(false) }

    // Menentukan Kategori Awal
    val initKategori = when (defaultFilter) {
        AktivitasRiwayatPenjualan.FILTER_RUMAHAN -> AktivitasRiwayatPenjualan.UI_FILTER_KASIR
        AktivitasRiwayatPenjualan.FILTER_PASAR -> AktivitasRiwayatPenjualan.UI_FILTER_REKAP
        else -> AktivitasRiwayatPenjualan.UI_FILTER_SEMUA
    }

    // State Filter
    var searchQuery by remember { mutableStateOf("") }
    var kategoriAktif by remember { mutableStateOf(initKategori) }
    var tanggalTunggal by remember { mutableStateOf<String?>(null) }
    var rentangMulai by remember { mutableStateOf<String?>(null) }
    var rentangSelesai by remember { mutableStateOf<String?>(null) }

    // State Paginasi
    var halamanSaatIni by remember { mutableStateOf(1) }
    val itemPerHalaman = 15
    val coroutineScope = rememberCoroutineScope()

    // Tema Warna
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF111827) else Color(0xFFF3F4F6)
    val surfaceColor = if (isDark) Color(0xFF1F2937) else Color(0xFFFFFFFF)
    val primaryColor = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
    val textColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF111827)
    val mutedColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)

    // Palet Status
    val successColor = if (isDark) Color(0xFF10B981) else Color(0xFF059669)
    val warningColor = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)
    val dangerColor = if (isDark) Color(0xFFEF4444) else Color(0xFFDC2626)

    // Palet Sumber
    val kasirColor = if (isDark) Color(0xFF10B981) else Color(0xFF059669) // Hijau
    val rekapColor = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB) // Biru

    // Fetch Data
    LaunchedEffect(triggerRefresh) {
        isLoading = true
        val sourceFilterQuery = when {
            lockFilter && initKategori == AktivitasRiwayatPenjualan.UI_FILTER_REKAP -> "PASAR"
            lockFilter && initKategori == AktivitasRiwayatPenjualan.UI_FILTER_KASIR -> "KASIR"
            else -> null
        }
        runCatching {
            RepositoriFirebaseUtama.muatRiwayatPenjualan(
                sourceFilter = sourceFilterQuery,
                kasirAuthUid = if (hanyaKasirLogin) getCurrentUserId() else null
            )
        }
            .onSuccess { sales ->
                rows = sales.sortedByDescending { it.tanggalIso }.map {
                    val statusLabel = when {
                        it.statusPenjualan.equals("BATAL", true) -> "Batal"
                        it.statusPenjualan.equals("PENDING", true) -> "Pending"
                        else -> "Selesai"
                    }
                    RiwayatPenjualanUiRow(
                        tanggalIso = it.tanggalIso,
                        item = ItemBaris(
                            id = it.id, title = it.title, subtitle = it.subtitle, amount = it.amount, badge = it.badge,
                            tone = if (it.badge.equals(AktivitasRiwayatPenjualan.FILTER_RUMAHAN, true)) WarnaBaris.GREEN else WarnaBaris.BLUE,
                            parameterStatus = statusLabel,
                            parameterTone = when (statusLabel) { "Batal" -> WarnaBaris.RED; "Pending" -> WarnaBaris.GOLD; else -> WarnaBaris.GREEN },
                            actionLabel = "⋮"
                        )
                    )
                }
                isLoading = false
            }
            .onFailure {
                rows = emptyList()
                isLoading = false
            }
    }

    // Filter Logic
    val filteredRows by remember(rows, searchQuery, kategoriAktif, tanggalTunggal, rentangMulai, rentangSelesai) {
        derivedStateOf {
            rows.filter { row ->
                val item = row.item
                val targetDate = Formatter.parseDate(row.tanggalIso)
                val cocokTanggal = cocokFilterTanggal(targetDate, tanggalTunggal, rentangMulai, rentangSelesai)
                val cocokKeyword = searchQuery.isBlank() ||
                        item.id.contains(searchQuery, ignoreCase = true) ||
                        item.title.contains(searchQuery, ignoreCase = true) ||
                        item.subtitle.contains(searchQuery, ignoreCase = true) ||
                        item.amount.contains(searchQuery, ignoreCase = true) ||
                        item.badge.contains(searchQuery, ignoreCase = true) ||
                        item.parameterStatus.contains(searchQuery, ignoreCase = true)
                val cocokKategori = when (kategoriAktif) {
                    AktivitasRiwayatPenjualan.UI_FILTER_KASIR -> item.badge.equals(AktivitasRiwayatPenjualan.FILTER_RUMAHAN, true)
                    AktivitasRiwayatPenjualan.UI_FILTER_REKAP -> item.badge.equals(AktivitasRiwayatPenjualan.FILTER_PASAR, true)
                    else -> true
                }
                cocokTanggal && cocokKeyword && cocokKategori
            }
        }
    }

    LaunchedEffect(searchQuery, kategoriAktif, tanggalTunggal, rentangMulai, rentangSelesai) { halamanSaatIni = 1 }

    // Pagination
    val totalPages = maxOf(1, ((filteredRows.size - 1) / itemPerHalaman) + 1)
    if (halamanSaatIni > totalPages) halamanSaatIni = totalPages
    val paginatedRows = filteredRows.drop((halamanSaatIni - 1) * itemPerHalaman).take(itemPerHalaman)

    // Opsi Filter Kategori
    val kategoriOpsi = if (lockFilter) listOf(kategoriAktif) else listOf(AktivitasRiwayatPenjualan.UI_FILTER_SEMUA, AktivitasRiwayatPenjualan.UI_FILTER_KASIR, AktivitasRiwayatPenjualan.UI_FILTER_REKAP)

    // Aksi Klik Baris
    val handleRowClick = { item: ItemBaris ->
        if (item.parameterStatus.equals("Pending", true)) {
            coroutineScope.launch {
                runCatching { onGetQrisData(item.id) }.onSuccess { info ->
                    if (!info.statusPenjualan.equals("PENDING", true)) {
                        onShowMessage("Transaksi ini tidak lagi pending")
                        triggerRefresh++
                    } else if (info.paymentQrString.isBlank()) {
                        onShowMessage("QRIS transaksi lama belum menyimpan data QR. Batalkan lalu buat QRIS baru.")
                    } else {
                        val bitmap = PembuatQrBitmap.buat(info.paymentQrString, 1000)
                        qrisDialogData = QrisDialogData(item, info, bitmap)
                    }
                }.onFailure { onShowMessage(it.message ?: "Gagal memuat QRIS") }
            }
        } else {
            selectedDetailItem = item
        }
    }

    LaunchedEffect(selectedDetailItem?.id) {
        val item = selectedDetailItem ?: return@LaunchedEffect
        detailText = ""
        isLoadingDetail = true
        detailText = runCatching { onLoadDetail(item) }
            .getOrElse { it.message ?: "Gagal memuat detail penjualan" }
        isLoadingDetail = false
    }

    Scaffold(
        topBar = {
            Surface(
                color = surfaceColor,
                shadowElevation = if (isDark) 0.dp else 4.dp,
                border = if (isDark) BorderStroke(1.dp, borderColor) else null
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = mutedColor)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Rounded.ArrowBack, "Kembali", tint = textColor) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        containerColor = bgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- PENCARIAN & FILTER MODERN ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari nota / ID / status...", color = mutedColor) },
                    leadingIcon = { Icon(Icons.Rounded.Search, "Cari", tint = mutedColor) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(100),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = Color.Transparent, focusedContainerColor = surfaceColor, unfocusedContainerColor = surfaceColor),
                    modifier = Modifier.weight(1f).height(54.dp)
                )

                val hasActiveFilter = (kategoriAktif != AktivitasRiwayatPenjualan.UI_FILTER_SEMUA && !(lockFilter && kategoriOpsi.size == 1)) || tanggalTunggal != null || rentangMulai != null
                Surface(
                    shape = CircleShape,
                    color = if (hasActiveFilter) primaryColor else surfaceColor,
                    border = if (hasActiveFilter) null else BorderStroke(1.dp, borderColor),
                    modifier = Modifier.size(54.dp).clickable { showFilterDialog = true }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.FilterList, "Filter", tint = if (hasActiveFilter) Color.White else textColor)
                        if (hasActiveFilter) Box(Modifier.align(Alignment.TopEnd).padding(12.dp).size(8.dp).clip(CircleShape).background(Color.Red))
                    }
                }
            }

            // --- CHIPS FILTER ---
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if ((kategoriAktif != AktivitasRiwayatPenjualan.UI_FILTER_SEMUA && !lockFilter) || tanggalTunggal != null || rentangMulai != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (kategoriAktif != AktivitasRiwayatPenjualan.UI_FILTER_SEMUA && !lockFilter) {
                            FilterChipVisual(label = kategoriAktif, onRemove = { kategoriAktif = AktivitasRiwayatPenjualan.UI_FILTER_SEMUA }, primaryColor)
                        }
                        if (tanggalTunggal != null) {
                            FilterChipVisual(label = Formatter.readableShortDate(tanggalTunggal!!), onRemove = { tanggalTunggal = null }, primaryColor)
                        } else if (rentangMulai != null && rentangSelesai != null) {
                            FilterChipVisual(label = "${Formatter.readableShortDate(rentangMulai!!)} - ${Formatter.readableShortDate(rentangSelesai!!)}", onRemove = { rentangMulai = null; rentangSelesai = null }, primaryColor)
                        }
                    }
                }
                Text("Menampilkan ${filteredRows.size} transaksi", color = mutedColor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
            }

            // --- DAFTAR RIWAYAT ---
            if (isLoading) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(6) { HistoryRowSkeleton(surfaceColor, borderColor) }
                }
            } else if (filteredRows.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyDataView("Tidak ada transaksi ditemukan", "Coba ubah kata kunci pencarian atau hapus filter kalender.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(paginatedRows) { row ->
                        val item = row.item
                        val isKasir = item.badge.equals(AktivitasRiwayatPenjualan.FILTER_RUMAHAN, true)
                        val sourceColor = if (isKasir) kasirColor else rekapColor
                        val icon = if (isKasir) Icons.Rounded.Storefront else Icons.Rounded.LocalShipping

                        val statusColor = when (item.parameterStatus) {
                            "Batal" -> dangerColor
                            "Pending" -> warningColor
                            else -> successColor
                        }

                        SalesHistoryCard(
                            item = item,
                            sourceColor = sourceColor,
                            statusColor = statusColor,
                            iconVector = icon,
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            izinkanHapus = izinkanHapus,
                            onClick = { handleRowClick(item) },
                            onCancel = { onCancelSale(item) { triggerRefresh++ } },
                            onDelete = { onDeleteSale(item) { triggerRefresh++ } },
                            onShowQris = {
                                coroutineScope.launch {
                                    runCatching { onGetQrisData(item.id) }.onSuccess { info ->
                                        if (info.paymentQrString.isNotBlank()) qrisDialogData = QrisDialogData(item, info, PembuatQrBitmap.buat(info.paymentQrString, 1000))
                                    }
                                }
                            },
                            onCheckQrisStatus = { onCheckQrisStatus(item) { triggerRefresh++ } }
                        )
                    }
                }

                // --- PAGINASI PILL MOBILE ---
                if (totalPages > 1) {
                    Surface(
                        color = surfaceColor,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(100),
                                border = BorderStroke(1.dp, borderColor),
                                color = bgColor
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { if (halamanSaatIni > 1) halamanSaatIni-- }, enabled = halamanSaatIni > 1) {
                                        Icon(Icons.Rounded.ChevronLeft, "Sebelumnya", tint = if (halamanSaatIni > 1) primaryColor else mutedColor)
                                    }
                                    Text("Hal $halamanSaatIni dari $totalPages", color = textColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
                                    IconButton(onClick = { if (halamanSaatIni < totalPages) halamanSaatIni++ }, enabled = halamanSaatIni < totalPages) {
                                        Icon(Icons.Rounded.ChevronRight, "Selanjutnya", tint = if (halamanSaatIni < totalPages) primaryColor else mutedColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // === DIALOG FILTER MODERN ===
        if (showFilterDialog) {
            ModernMobileFilterDialog(
                kategoriOpsi = kategoriOpsi,
                initialKategori = kategoriAktif,
                lockFilter = lockFilter,
                initialRentangMulai = tanggalTunggal ?: rentangMulai,
                initialRentangSelesai = tanggalTunggal ?: rentangSelesai,
                primaryColor = primaryColor,
                surfaceColor = surfaceColor,
                bgColor = bgColor,
                textColor = textColor,
                mutedColor = mutedColor,
                borderColor = borderColor,
                activityContext = activityContext,
                onDismiss = { showFilterDialog = false },
                onReset = {
                    if (!lockFilter) kategoriAktif = initKategori
                    tanggalTunggal = null
                    rentangMulai = null
                    rentangSelesai = null
                    showFilterDialog = false
                },
                onApply = { kategoriBaru, mulaiStr, selesaiStr ->
                    if (!lockFilter) kategoriAktif = kategoriBaru
                    if (mulaiStr == null && selesaiStr == null) {
                        tanggalTunggal = null
                        rentangMulai = null
                        rentangSelesai = null
                    } else if (mulaiStr != null && selesaiStr != null) {
                        if (mulaiStr == selesaiStr) {
                            tanggalTunggal = mulaiStr
                            rentangMulai = null
                            rentangSelesai = null
                        } else if (mulaiStr > selesaiStr) { // terbalik
                            tanggalTunggal = null
                            rentangMulai = selesaiStr
                            rentangSelesai = mulaiStr
                        } else {
                            tanggalTunggal = null
                            rentangMulai = mulaiStr
                            rentangSelesai = selesaiStr
                        }
                    }
                    showFilterDialog = false
                }
            )
        }

        // === DIALOG DETAIL STRUK PRO ===
        val detailItem = selectedDetailItem
        if (detailItem != null) {
            val context = LocalContext.current
            ProDetailDialog(
                title = detailItem.title,
                badge = detailItem.badge,
                detailText = detailText,
                isLoading = isLoadingDetail,
                primaryColor = primaryColor,
                surfaceColor = surfaceColor,
                textColor = textColor,
                borderColor = borderColor,
                extraActions = {
                    DialogActionButton(Icons.Rounded.Share, "Bagikan", primaryColor) {
                        if (detailText.isNotBlank()) onShare(detailItem, detailText)
                    }
                    DialogActionButton(Icons.Rounded.Download, "Unduh PDF", primaryColor) {
                        Toast.makeText(context, "Fitur Unduh PDF segera hadir", Toast.LENGTH_SHORT).show()
                    }
                    DialogActionButton(Icons.Rounded.Print, "Cetak Struk", warningColor) {
                        Toast.makeText(context, "Fitur Cetak Struk segera hadir", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { if (!isLoadingDetail) selectedDetailItem = null }
            )
        }

        // === DIALOG QRIS PENDING ===
        if (qrisDialogData != null) {
            val data = qrisDialogData!!
            Dialog(onDismissRequest = { qrisDialogData = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    shape = RoundedCornerShape(24.dp),
                    color = surfaceColor
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.QrCodeScanner, null, tint = primaryColor)
                            Text("Menunggu Pembayaran", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = textColor)
                        }
                        Text(Formatter.currency(data.info.totalBelanja), fontWeight = FontWeight.Black, color = primaryColor, style = MaterialTheme.typography.headlineMedium)

                        // Gambar QR
                        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 4.dp, border = BorderStroke(1.dp, borderColor)) {
                            Image(bitmap = data.bitmap.asImageBitmap(), contentDescription = "QRIS", modifier = Modifier.size(240.dp).padding(16.dp))
                        }

                        // Info Waktu
                        val sisaMs = (data.info.paymentQrExpiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                        val sisaMenit = TimeUnit.MILLISECONDS.toMinutes(sisaMs)
                        val sisaDetik = TimeUnit.MILLISECONDS.toSeconds(sisaMs) % 60
                        val waktuText = if (sisaMs > 0) String.format(Locale.US, "Kedaluwarsa dalam: %02d:%02d", sisaMenit, sisaDetik) else "Waktu QRIS telah habis"

                        Text(waktuText, color = if (sisaMs > 0) mutedColor else dangerColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Nota: ${data.item.title}", color = mutedColor, style = MaterialTheme.typography.labelSmall)

                        Spacer(Modifier.height(8.dp))

                        // Tombol Aksi
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { qrisDialogData = null; onCheckQrisStatus(data.item) { triggerRefresh++ } },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                            ) { Text("Cek Status Pembayaran", fontWeight = FontWeight.Bold) }

                            OutlinedButton(
                                onClick = { qrisDialogData = null; onCancelSale(data.item) { triggerRefresh++ } },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, dangerColor),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor)
                            ) { Text("Batalkan Transaksi", fontWeight = FontWeight.Bold) }

                            TextButton(onClick = { qrisDialogData = null }, modifier = Modifier.fillMaxWidth()) { Text("Tutup Peringatan", color = mutedColor) }
                        }
                    }
                }
            }
        }
    }
}

// === KOMPONEN UI REUSABLE & MODERNA ===

@Composable
private fun HistoryRowSkeleton(surfaceColor: Color, borderColor: Color) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).adminShimmerEffect())
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.height(16.dp).fillMaxWidth(0.7f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                Box(Modifier.height(12.dp).fillMaxWidth(0.5f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.height(20.dp).width(50.dp).clip(RoundedCornerShape(6.dp)).adminShimmerEffect())
                    Box(Modifier.height(20.dp).width(60.dp).clip(RoundedCornerShape(6.dp)).adminShimmerEffect())
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.height(18.dp).width(80.dp).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                Box(Modifier.size(24.dp).clip(CircleShape).adminShimmerEffect())
            }
        }
    }
}

@Composable
private fun EmptyDataView(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Inbox, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
        }
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = Color.Gray, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}
@Composable
private fun ProDetailDialog(
    title: String,
    badge: String,
    detailText: String,
    isLoading: Boolean,
    primaryColor: Color,
    surfaceColor: Color,
    textColor: Color,
    borderColor: Color,
    extraActions: @Composable (RowScope.() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val isBatal = badge.contains("Batal", true)
    val badgeColor = if (isBatal) Color.Red else primaryColor

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surfaceColor,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column {
                Text(badge.uppercase(), color = badgeColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text(title, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (isLoading) {
                    // Animasi Loading Kertas Struk
                    HistoryDetailContentSkeleton(borderColor)
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDark) Color(0xFF111827) else Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                text = detailText.ifBlank { "Detail belum tersedia." },
                                color = textColor,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 380.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth()) {
                if (extraActions != null && !isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        extraActions()
                    }
                }
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Tutup", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    )
}

// Tambahkan komponen kerangka skeleton ini tepat di bawah ProDetailDialog
@Composable
private fun HistoryDetailContentSkeleton(borderColor: Color) {
    val isDark = isSystemInDarkTheme()
    val surfaceColor = if (isDark) Color(0xFF111827) else Color(0xFFF8FAFC)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Skeleton Header Struk (Tengah)
            Box(Modifier.height(14.dp).fillMaxWidth(0.5f).align(Alignment.CenterHorizontally).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
            Box(Modifier.height(12.dp).fillMaxWidth(0.3f).align(Alignment.CenterHorizontally).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())

            Spacer(Modifier.height(8.dp))

            // Skeleton Item Rincian (Kiri dan Kanan)
            repeat(5) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.height(12.dp).fillMaxWidth(0.4f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                    Box(Modifier.height(12.dp).fillMaxWidth(0.25f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                }
            }

            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(borderColor.copy(alpha = 0.5f))) // Garis Pemisah

            // Skeleton Total Harga (Kiri dan Kanan, sedikit lebih tebal)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.height(16.dp).fillMaxWidth(0.3f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                Box(Modifier.height(18.dp).fillMaxWidth(0.35f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
            }
        }
    }
}
@Composable
private fun DialogActionButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp)
    ) {
        Box(
            modifier = Modifier.size(46.dp).clip(CircleShape).background(color.copy(alpha=0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernMobileFilterDialog(
    kategoriOpsi: List<String>,
    initialKategori: String,
    lockFilter: Boolean,
    initialRentangMulai: String?,
    initialRentangSelesai: String?,
    primaryColor: Color,
    surfaceColor: Color,
    bgColor: Color,
    textColor: Color,
    mutedColor: Color,
    borderColor: Color,
    activityContext: AppCompatActivity,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onApply: (kategori: String, rentangMulai: String?, rentangSelesai: String?) -> Unit
) {
    var draftKategori by remember { mutableStateOf(initialKategori) }
    var draftMulai by remember { mutableStateOf(initialRentangMulai.orEmpty()) }
    var draftSelesai by remember { mutableStateOf(initialRentangSelesai.orEmpty()) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    fun formatDateToLocalId(dateStr: String): String {
        if (dateStr.isBlank()) return ""
        return runCatching {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)
            SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(d!!)
        }.getOrDefault(dateStr)
    }

    val dateLabel = if (draftMulai.isNotBlank() && draftSelesai.isNotBlank()) {
        if (draftMulai == draftSelesai) formatDateToLocalId(draftMulai)
        else "${formatDateToLocalId(draftMulai)} - ${formatDateToLocalId(draftSelesai)}"
    } else {
        "Pilih rentang tanggal"
    }

    // --- DIALOG FILTER UTAMA (SIMPEL) ---
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = surfaceColor,
        title = { Text("Filter Lanjutan", fontWeight = FontWeight.Bold, color = textColor) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Kategori
                Text("Jenis Transaksi", fontWeight = FontWeight.SemiBold, color = textColor, style = MaterialTheme.typography.bodyMedium)
                if (lockFilter) {
                    Surface(shape = RoundedCornerShape(12.dp), color = primaryColor.copy(alpha = 0.12f)) {
                        Text(draftKategori, color = primaryColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        kategoriOpsi.forEach { pilihan ->
                            val isSelected = draftKategori == pilihan
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.Transparent,
                                border = BorderStroke(1.dp, if (isSelected) primaryColor else borderColor),
                                modifier = Modifier.weight(1f).clickable { draftKategori = pilihan }
                            ) {
                                Text(pilihan, color = if (isSelected) primaryColor else textColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Divider(color = borderColor)

                // Date Range Button
                Text("Rentang Tanggal", fontWeight = FontWeight.SemiBold, color = textColor, style = MaterialTheme.typography.bodyMedium)
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { showDateRangePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.DateRange, contentDescription = null, tint = mutedColor)
                        Text(text = dateLabel, color = if (draftMulai.isNotBlank()) textColor else mutedColor, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(draftKategori, draftMulai.ifBlank { null }, draftSelesai.ifBlank { null }) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                Text("Terapkan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) { Text("Reset", color = mutedColor) }
        }
    )

    // --- DIALOG KALENDER TERSEMBUNYI (DATE RANGE PICKER) ---
    if (showDateRangePicker) {
        val localFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val utcFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

        fun getUtcMillis(dateStr: String?): Long? {
            if (dateStr.isNullOrBlank()) return null
            return runCatching { utcFormat.parse(dateStr)?.time }.getOrNull()
        }

        fun getUtcMillisFromOffset(offsetDays: Int): Long {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, offsetDays)
            val dateStr = localFormat.format(cal.time)
            return utcFormat.parse(dateStr)?.time ?: 0L
        }

        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = getUtcMillis(draftMulai),
            initialSelectedEndDateMillis = getUtcMillis(draftSelesai)
        )
        var selectedPreset by remember { mutableStateOf("") }

        fun applyPreset(preset: String) {
            selectedPreset = preset
            val todayStr = localFormat.format(Date())
            val todayUtcMillis = utcFormat.parse(todayStr)?.time ?: 0L

            when (preset) {
                "Hari Ini" -> dateRangePickerState.setSelection(todayUtcMillis, todayUtcMillis)
                "Kemarin" -> {
                    val yestMillis = getUtcMillisFromOffset(-1)
                    dateRangePickerState.setSelection(yestMillis, yestMillis)
                }
                "Minggu Ini" -> {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    val startStr = localFormat.format(cal.time)
                    cal.add(Calendar.DAY_OF_YEAR, 6)
                    val endStr = localFormat.format(cal.time)
                    val startUtc = utcFormat.parse(startStr)?.time
                    val endUtc = utcFormat.parse(endStr)?.time
                    if (startUtc != null && endUtc != null) dateRangePickerState.setSelection(startUtc, endUtc)
                }
                "Bulan Ini" -> {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    val startStr = localFormat.format(cal.time)
                    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                    val endStr = localFormat.format(cal.time)
                    val startUtc = utcFormat.parse(startStr)?.time
                    val endUtc = utcFormat.parse(endStr)?.time
                    if (startUtc != null && endUtc != null) dateRangePickerState.setSelection(startUtc, endUtc)
                }
            }
        }

        Dialog(
            onDismissRequest = { showDateRangePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.90f),
                shape = RoundedCornerShape(24.dp),
                color = surfaceColor
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pilih Rentang Tanggal", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = { showDateRangePicker = false }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Tutup", tint = textColor)
                        }
                    }

                    Divider(color = borderColor)

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf("Hari Ini", "Kemarin", "Minggu Ini", "Bulan Ini")
                        items(presets) { preset ->
                            val isSelected = selectedPreset == preset
                            Surface(
                                shape = RoundedCornerShape(100),
                                color = if (isSelected) primaryColor.copy(alpha = 0.15f) else bgColor,
                                border = BorderStroke(1.dp, if (isSelected) primaryColor else borderColor),
                                modifier = Modifier.clickable { applyPreset(preset) }
                            ) {
                                Text(
                                    text = preset,
                                    color = if (isSelected) primaryColor else textColor,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        DateRangePicker(
                            state = dateRangePickerState,
                            title = null,
                            headline = null,
                            showModeToggle = false,
                            modifier = Modifier.fillMaxSize(),
                            colors = DatePickerDefaults.colors(
                                containerColor = Color.Transparent,
                                dayContentColor = textColor,
                                selectedDayContainerColor = primaryColor,
                                selectedDayContentColor = Color.White,
                                dayInSelectionRangeContainerColor = primaryColor.copy(alpha = 0.2f),
                                dayInSelectionRangeContentColor = primaryColor
                            )
                        )
                    }

                    Divider(color = borderColor)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showDateRangePicker = false }) {
                            Text("Batal", color = mutedColor, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val startMillis = dateRangePickerState.selectedStartDateMillis
                                val endMillis = dateRangePickerState.selectedEndDateMillis
                                val mulaiStr = startMillis?.let { utcFormat.format(Date(it)) } ?: ""
                                val selesaiStr = endMillis?.let { utcFormat.format(Date(it)) } ?: mulaiStr

                                draftMulai = mulaiStr
                                draftSelesai = selesaiStr
                                showDateRangePicker = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            Text("Simpan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipVisual(label: String, onRemove: () -> Unit, primaryColor: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = primaryColor.copy(alpha = 0.1f),
        modifier = Modifier.clickable(onClick = onRemove)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, color = primaryColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Icon(Icons.Rounded.Close, "Hapus", tint = primaryColor, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun SalesHistoryCard(
    item: ItemBaris,
    sourceColor: Color,
    statusColor: Color,
    iconVector: ImageVector,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    izinkanHapus: Boolean,
    onClick: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onShowQris: () -> Unit,
    onCheckQrisStatus: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isPending = item.parameterStatus.equals("Pending", true)
    val isBatal = item.parameterStatus.equals("Batal", true)
    val cardOpacity = if (isBatal) 0.6f else 1f

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor.copy(alpha = cardOpacity)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(if (isBatal) mutedColor.copy(alpha = 0.1f) else sourceColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(iconVector, null, tint = if (isBatal) mutedColor else sourceColor, modifier = Modifier.size(24.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Bold, color = if (isBatal) mutedColor else textColor, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.subtitle, color = mutedColor, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(6.dp), color = if (isBatal) mutedColor.copy(alpha = 0.1f) else sourceColor.copy(alpha = 0.1f)) {
                        Text(item.badge, color = if (isBatal) mutedColor else sourceColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = if (isBatal) mutedColor.copy(alpha = 0.1f) else statusColor.copy(alpha = 0.1f)) {
                        Text(item.parameterStatus, color = if (isBatal) mutedColor else statusColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(item.amount, fontWeight = FontWeight.Bold, color = if (isBatal) mutedColor else textColor, style = MaterialTheme.typography.titleMedium)

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.MoreVert, "Opsi", tint = mutedColor) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(surfaceColor)) {
                        DropdownMenuItem(text = { Text("Lihat Detail & Struk", color = textColor) }, onClick = { showMenu = false; onClick() })

                        if (isPending) {
                            DropdownMenuItem(text = { Text("Lihat QRIS", color = textColor) }, onClick = { showMenu = false; onShowQris() })
                            DropdownMenuItem(text = { Text("Cek Status QRIS", color = textColor) }, onClick = { showMenu = false; onCheckQrisStatus() })
                            DropdownMenuItem(text = { Text("Batalkan Transaksi", color = Color.Red) }, onClick = { showMenu = false; onCancel() })
                        } else if (!isBatal && izinkanHapus) {
                            DropdownMenuItem(text = { Text("Batalkan Penjualan", color = Color.Red) }, onClick = { showMenu = false; onCancel() })
                        }
                    }
                }
            }
        }
    }
}

// === FUNGSI UTILITAS FILTER TANGGAL ===
private fun normalisasiInputTanggal(raw: String?): String? {
    val value = raw.orEmpty().trim()
    if (value.isBlank()) return null
    val output = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val formats = listOf(
        SimpleDateFormat("yyyy-MM-dd", Locale.US),
        SimpleDateFormat("dd/MM/yyyy", Locale.US),
        SimpleDateFormat("dd-MM-yyyy", Locale.US)
    )
    formats.forEach { format ->
        format.isLenient = false
        val parsed = runCatching { format.parse(value) }.getOrNull()
        if (parsed != null) return output.format(parsed)
    }
    return null
}

private fun cocokFilterTanggal(tanggalData: Date, tanggalTunggal: String?, rentangMulai: String?, rentangSelesai: String?): Boolean {
    tanggalTunggal?.let { single -> return isSameDay(tanggalData, Formatter.parseDate(single)) }
    if (!rentangMulai.isNullOrBlank() && !rentangSelesai.isNullOrBlank()) {
        val mulai = startOfDay(Formatter.parseDate(rentangMulai))
        val selesai = endOfDay(Formatter.parseDate(rentangSelesai))
        return !tanggalData.before(mulai) && !tanggalData.after(selesai)
    }
    return true
}

private fun isSameDay(first: Date, second: Date): Boolean {
    val cal1 = Calendar.getInstance().apply { time = first }
    val cal2 = Calendar.getInstance().apply { time = second }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun startOfDay(date: Date): Date = Calendar.getInstance().apply { time = date; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.time
private fun endOfDay(date: Date): Date = Calendar.getInstance().apply { time = date; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.time