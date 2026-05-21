package muhamad.irfan.si_tahu.ui.produksi

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import muhamad.irfan.si_tahu.util.DialogPilihBulanRiwayat
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AktivitasRiwayatProduksi : AktivitasDasar() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        setContent {
            SiTahuProTheme {
                ProductionHistoryScreen(
                    onNavigateBack = { finish() },
                    onLoadDetail = { item ->
                        RepositoriFirebaseUtama.buildProductionDetailText(item.id)
                    },
                    onConfirmCancel = { item, alasan, onSuccess ->
                        lifecycleScope.launch {
                            runCatching { RepositoriFirebaseUtama.batalkanCatatanProduksi(item.id, alasan, currentUserId()) }
                                .onSuccess {
                                    showMessage("Data produksi berhasil dibatalkan")
                                    onSuccess()
                                }
                                .onFailure {
                                    showMessage(it.message ?: "Gagal membatalkan data")
                                }
                        }
                    }
                )
            }
        }
    }
}

data class RiwayatProduksiUiRow(
    val tanggalIso: String,
    val item: ItemBaris
)

// === KONSTANTA FILTER ===
private const val FILTER_SEMUA = "Semua"
private const val FILTER_DASAR = "Dasar"
private const val FILTER_OLAHAN = "Olahan"

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

// === KOMPONEN UTAMA UI ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductionHistoryScreen(
    onNavigateBack: () -> Unit,
    onLoadDetail: suspend (ItemBaris) -> String,
    onConfirmCancel: (ItemBaris, String, () -> Unit) -> Unit
) {
    // State Data
    var rows by remember { mutableStateOf<List<RiwayatProduksiUiRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var triggerRefresh by remember { mutableStateOf(0) }

    // State Filter & Pencarian
    var searchQuery by remember { mutableStateOf("") }
    var kategoriAktif by remember { mutableStateOf(FILTER_SEMUA) }
    var tanggalTunggal by remember { mutableStateOf<String?>(null) }
    var rentangMulai by remember { mutableStateOf<String?>(null) }
    var rentangSelesai by remember { mutableStateOf<String?>(null) }

    // State Dialog
    val coroutineScope = rememberCoroutineScope()
    var showFilterDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var detailTitle by remember { mutableStateOf("Detail Produksi") }
    var detailBadge by remember { mutableStateOf("PRODUKSI") }
    var detailText by remember { mutableStateOf("") }
    var detailLoading by remember { mutableStateOf(false) }

    // State Dialog Batalkan Riwayat
    var itemToCancel by remember { mutableStateOf<ItemBaris?>(null) }

    fun bukaDetail(item: ItemBaris) {
        detailTitle = item.title
        detailBadge = item.badge
        detailText = ""
        detailLoading = true
        showDetailDialog = true
        coroutineScope.launch {
            runCatching { onLoadDetail(item) }
                .onSuccess { detailText = it }
                .onFailure { detailText = it.message ?: "Gagal memuat detail produksi" }
            detailLoading = false
        }
    }

    // State Paginasi
    var halamanSaatIni by remember { mutableStateOf(1) }
    val itemPerHalaman = 10

    // Tema Warna
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF111827) else Color(0xFFF3F4F6)
    val surfaceColor = if (isDark) Color(0xFF1F2937) else Color(0xFFFFFFFF)
    val primaryColor = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
    val textColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF111827)
    val mutedColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)

    val dasarColor = if (isDark) Color(0xFF10B981) else Color(0xFF059669) // Hijau
    val olahanColor = if (isDark) Color(0xFF8B5CF6) else Color(0xFF6D28D9) // Ungu
    val dangerColor = if (isDark) Color(0xFFEF4444) else Color(0xFFDC2626) // Merah

    // Fungsi Fetch Data
    LaunchedEffect(triggerRefresh) {
        isLoading = true
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
                            tone = if (it.badge.contains("Olahan", true)) WarnaBaris.BLUE else WarnaBaris.GREEN,
                            priceTone = if (it.badge.contains("Olahan", true)) WarnaBaris.BLUE else WarnaBaris.GREEN
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

    // Perhitungan Filter Data Realtime
    val filteredRows by remember(rows, searchQuery, kategoriAktif, tanggalTunggal, rentangMulai, rentangSelesai) {
        derivedStateOf {
            rows.filter { row ->
                val item = row.item
                val targetDate = Formatter.parseDate(row.tanggalIso)

                val cocokTanggal = cocokFilterTanggal(targetDate, tanggalTunggal, rentangMulai, rentangSelesai)
                val kataKunci = searchQuery.trim()
                val cocokKeyword = kataKunci.isBlank() ||
                        item.id.contains(kataKunci, ignoreCase = true) ||
                        item.title.contains(kataKunci, ignoreCase = true) ||
                        item.subtitle.contains(kataKunci, ignoreCase = true) ||
                        item.badge.contains(kataKunci, ignoreCase = true) ||
                        item.amount.contains(kataKunci, ignoreCase = true)

                val cocokKategori = when (kategoriAktif) {
                    FILTER_DASAR -> !item.badge.contains("Olahan", true)
                    FILTER_OLAHAN -> item.badge.contains("Olahan", true)
                    else -> true
                }

                cocokTanggal && cocokKeyword && cocokKategori
            }
        }
    }

    // Reset halaman ke-1 jika filter berubah
    LaunchedEffect(searchQuery, kategoriAktif, tanggalTunggal, rentangMulai, rentangSelesai) {
        halamanSaatIni = 1
    }

    // Perhitungan Paginasi
    val totalPages = maxOf(1, ((filteredRows.size - 1) / itemPerHalaman) + 1)
    if (halamanSaatIni > totalPages) halamanSaatIni = totalPages
    val paginatedRows = filteredRows.drop((halamanSaatIni - 1) * itemPerHalaman).take(itemPerHalaman)

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
                            Text("Riwayat Produksi", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text("Daftar aktivitas dapur", style = MaterialTheme.typography.labelMedium, color = mutedColor)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Kembali", tint = textColor)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        containerColor = bgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- BAR PENCARIAN & FILTER (DESAIN MODERN) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari produk atau ID...", color = mutedColor) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Cari", tint = mutedColor) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(100),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = surfaceColor,
                        unfocusedContainerColor = surfaceColor
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                )

                // Tombol Filter
                val hasActiveFilter = kategoriAktif != FILTER_SEMUA || tanggalTunggal != null || rentangMulai != null
                Surface(
                    shape = CircleShape,
                    color = if (hasActiveFilter) primaryColor else surfaceColor,
                    border = if (hasActiveFilter) null else BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .size(54.dp)
                        .clickable { showFilterDialog = true }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.FilterList, contentDescription = "Filter", tint = if (hasActiveFilter) Color.White else textColor)
                        if (hasActiveFilter) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dangerColor)
                            )
                        }
                    }
                }
            }

            // --- CHIPS FILTER AKTIF ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (kategoriAktif != FILTER_SEMUA || tanggalTunggal != null || rentangMulai != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (kategoriAktif != FILTER_SEMUA) {
                            FilterChipVisual(label = "Jenis: $kategoriAktif", onRemove = { kategoriAktif = FILTER_SEMUA }, primaryColor)
                        }
                        if (tanggalTunggal != null) {
                            FilterChipVisual(label = "Tanggal: ${Formatter.readableShortDate(tanggalTunggal!!)}", onRemove = { tanggalTunggal = null }, primaryColor)
                        } else if (rentangMulai != null && rentangSelesai != null) {
                            FilterChipVisual(label = "Rentang: ${Formatter.readableShortDate(rentangMulai!!)} - ${Formatter.readableShortDate(rentangSelesai!!)}", onRemove = { rentangMulai = null; rentangSelesai = null }, primaryColor)
                        }
                    }
                }

                Text(
                    text = "Menampilkan ${filteredRows.size} riwayat",
                    color = mutedColor,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 4.dp)
                )
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
                    EmptyDataView("Belum ada riwayat yang sesuai", "Coba ubah kata kunci, filter, atau rentang tanggal.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(paginatedRows) { row ->
                        val item = row.item
                        val isOlahan = item.badge.contains("Olahan", true)
                        val itemColor = if (isOlahan) olahanColor else dasarColor

                        val ikonPilihan = if (isOlahan) Icons.Rounded.Refresh else Icons.Rounded.CheckCircle

                        ProductionHistoryCard(
                            item = item,
                            itemColor = itemColor,
                            ikonVector = ikonPilihan,
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            dangerColor = dangerColor,
                            onClick = { bukaDetail(item) },
                            onCancelClick = { itemToCancel = item }
                        )
                    }
                }

                // --- PAGINASI BAWAH (DESAIN PILL MOBILE) ---
                if (totalPages > 1) {
                    Surface(
                        color = surfaceColor,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                                .navigationBarsPadding(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(100),
                                border = BorderStroke(1.dp, borderColor),
                                color = bgColor
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { if (halamanSaatIni > 1) halamanSaatIni-- },
                                        enabled = halamanSaatIni > 1
                                    ) {
                                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Sebelumnya", tint = if (halamanSaatIni > 1) primaryColor else mutedColor)
                                    }

                                    Text(
                                        text = "Hal $halamanSaatIni dari $totalPages",
                                        color = textColor,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )

                                    IconButton(
                                        onClick = { if (halamanSaatIni < totalPages) halamanSaatIni++ },
                                        enabled = halamanSaatIni < totalPages
                                    ) {
                                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Selanjutnya", tint = if (halamanSaatIni < totalPages) primaryColor else mutedColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // === DIALOG DETAIL "STRUK" ===
    if (showDetailDialog) {
        ProDetailDialog(
            title = detailTitle,
            badge = detailBadge,
            detailText = detailText,
            isLoading = detailLoading,
            primaryColor = primaryColor,
            surfaceColor = surfaceColor,
            textColor = textColor,
            borderColor = borderColor,
            onDismiss = { if (!detailLoading) showDetailDialog = false }
        )
    }

    // === DIALOG KONFIRMASI PEMBATALAN (MODERN INPUT MODAL) ===
    if (itemToCancel != null) {
        ModernCancelInputDialog(
            item = itemToCancel!!,
            surfaceColor = surfaceColor,
            textColor = textColor,
            mutedColor = mutedColor,
            borderColor = borderColor,
            dangerColor = dangerColor,
            bgColor = bgColor,
            onDismiss = { itemToCancel = null },
            onConfirm = { alasan ->
                onConfirmCancel(itemToCancel!!, alasan) {
                    triggerRefresh++
                }
                itemToCancel = null
            }
        )
    }

    // === DIALOG FILTER MODERN (MOBILE OPTIMIZED SIMPLIFIED) ===
    if (showFilterDialog) {
        ModernMobileFilterDialog(
            initialKategori = kategoriAktif,
            initialRentangMulai = tanggalTunggal ?: rentangMulai,
            initialRentangSelesai = tanggalTunggal ?: rentangSelesai,
            primaryColor = primaryColor,
            surfaceColor = surfaceColor,
            bgColor = bgColor,
            textColor = textColor,
            mutedColor = mutedColor,
            borderColor = borderColor,
            onDismiss = { showFilterDialog = false },
            onReset = {
                kategoriAktif = FILTER_SEMUA
                tanggalTunggal = null
                rentangMulai = null
                rentangSelesai = null
                showFilterDialog = false
            },
            onApply = { kategori, mulai, selesai ->
                kategoriAktif = kategori
                if (mulai == null && selesai == null) {
                    tanggalTunggal = null
                    rentangMulai = null
                    rentangSelesai = null
                } else if (mulai != null && selesai != null) {
                    if (mulai == selesai) {
                        tanggalTunggal = mulai
                        rentangMulai = null
                        rentangSelesai = null
                    } else if (mulai > selesai) {
                        tanggalTunggal = null
                        rentangMulai = selesai
                        rentangSelesai = mulai
                    } else {
                        tanggalTunggal = null
                        rentangMulai = mulai
                        rentangSelesai = selesai
                    }
                }
                showFilterDialog = false
            }
        )
    }
}

// === KOMPONEN UI TAMBAHAN & REUSABLE ===

@Composable
private fun ModernCancelInputDialog(
    item: ItemBaris,
    surfaceColor: Color,
    textColor: Color,
    mutedColor: Color,
    borderColor: Color,
    dangerColor: Color,
    bgColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reasonText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = surfaceColor,
        title = {
            Text(
                "Batalkan Produksi?",
                fontWeight = FontWeight.Bold,
                color = textColor,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Riwayat ${item.title} akan dibatalkan dan memengaruhi stok. Silakan masukkan alasan pembatalan untuk pencatatan:",
                    color = mutedColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    placeholder = { Text("Misal: Salah input jumlah", color = mutedColor) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = dangerColor,
                        unfocusedBorderColor = borderColor,
                        focusedContainerColor = bgColor,
                        unfocusedContainerColor = bgColor,
                        cursorColor = dangerColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reasonText.trim()) },
                enabled = reasonText.trim().isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = dangerColor,
                    disabledContainerColor = dangerColor.copy(alpha = 0.5f)
                )
            ) {
                Text("Batalkan Riwayat", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = mutedColor, fontWeight = FontWeight.Bold)
            }
        }
    )
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernMobileFilterDialog(
    initialKategori: String,
    initialRentangMulai: String?,
    initialRentangSelesai: String?,
    primaryColor: Color,
    surfaceColor: Color,
    bgColor: Color,
    textColor: Color,
    mutedColor: Color,
    borderColor: Color,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onApply: (kategori: String, rentangMulai: String?, rentangSelesai: String?) -> Unit
) {
    var draftKategori by remember { mutableStateOf(initialKategori) }
    var draftMulai by remember { mutableStateOf(initialRentangMulai.orEmpty()) }
    var draftSelesai by remember { mutableStateOf(initialRentangSelesai.orEmpty()) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }

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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(FILTER_SEMUA, FILTER_DASAR, FILTER_OLAHAN).forEach { pilihan ->
                        val isSelected = draftKategori == pilihan
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (isSelected) primaryColor else borderColor),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { draftKategori = pilihan }
                        ) {
                            Text(
                                text = pilihan,
                                color = if (isSelected) primaryColor else textColor,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
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

                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { showMonthPicker = true },
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
                        Text("Pilih satu bulan penuh", color = textColor, style = MaterialTheme.typography.bodyMedium)
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
                "Hari Ini" -> {
                    dateRangePickerState.setSelection(todayUtcMillis, todayUtcMillis)
                }
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

                    // Presets
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

                    // Kalender Utama
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

                    // Action Buttons (Batal & Simpan)
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

    if (showMonthPicker) {
        DialogPilihBulanRiwayat(
            initialDate = draftMulai.ifBlank { null },
            primaryColor = primaryColor,
            surfaceColor = surfaceColor,
            bgColor = bgColor,
            textColor = textColor,
            mutedColor = mutedColor,
            borderColor = borderColor,
            onDismiss = { showMonthPicker = false },
            onApply = { mulai, selesai, _ ->
                draftMulai = mulai
                draftSelesai = selesai
                showMonthPicker = false
            }
        )
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
                                text = detailText.ifBlank { "Detail data belum tersedia." },
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
private fun ProductionHistoryCard(
    item: ItemBaris,
    itemColor: Color,
    ikonVector: ImageVector,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    dangerColor: Color,
    onClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isBatal = item.badge.contains("Batal", true) || item.amount.equals("BATAL", true)
    val cardOpacity = if (isBatal) 0.6f else 1f

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor.copy(alpha = cardOpacity)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(if (isBatal) mutedColor.copy(alpha = 0.1f) else itemColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = ikonVector, contentDescription = null, tint = if (isBatal) mutedColor else itemColor, modifier = Modifier.size(24.dp))
            }

            // Info
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, fontWeight = FontWeight.Bold, color = if (isBatal) mutedColor else textColor, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.subtitle, color = mutedColor, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // Hasil dan Menu
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.amount, fontWeight = FontWeight.Bold, color = if (isBatal) mutedColor else itemColor, style = MaterialTheme.typography.titleMedium)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isBatal) mutedColor.copy(alpha = 0.1f) else itemColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = item.badge,
                        color = if (isBatal) mutedColor else itemColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Dropdown Menu (Titik Tiga)
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Opsi", tint = mutedColor)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(surfaceColor)
                ) {
                    DropdownMenuItem(
                        text = { Text("Lihat Detail", color = textColor) },
                        onClick = { showMenu = false; onClick() }
                    )
                    if (!isBatal) {
                        DropdownMenuItem(
                            text = { Text("Batalkan Riwayat", color = dangerColor, fontWeight = FontWeight.SemiBold) },
                            onClick = { showMenu = false; onCancelClick() }
                        )
                    }
                }
            }
        }
    }
}

// === FUNGSI UTILITAS FILTER TANGGAL ===

private fun cocokFilterTanggal(tanggalData: Date, tanggalTunggal: String?, rentangMulai: String?, rentangSelesai: String?): Boolean {
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

private fun isSameDay(first: Date, second: Date): Boolean {
    val cal1 = Calendar.getInstance().apply { time = first }
    val cal2 = Calendar.getInstance().apply { time = second }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
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