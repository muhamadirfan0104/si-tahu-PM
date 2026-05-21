package muhamad.irfan.si_tahu.ui.stok

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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
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

class AktivitasRiwayatSemuaStok : AktivitasDasar() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        setContent {
            SiTahuProTheme {
                StockHistoryScreen(
                    onNavigateBack = { finish() },
                    onShowMessage = { pesan -> showMessage(pesan) },
                    onCancelAdjustment = { row, onSuccess ->
                        showInputModal("Batalkan penyesuaian", "Alasan pembatalan", "Batalkan") { alasan ->
                            lifecycleScope.launch {
                                runCatching { RepositoriFirebaseUtama.batalkanPenyesuaianStok(row.referensiId, alasan, currentUserId()) }
                                    .onSuccess {
                                        showMessage("Penyesuaian stok berhasil dibatalkan")
                                        onSuccess()
                                    }
                                    .onFailure { showMessage(it.message ?: "Gagal membatalkan penyesuaian stok") }
                            }
                        }
                    },
                    activityContext = this@AktivitasRiwayatSemuaStok
                )
            }
        }
    }
}

// === MODEL DATA ===
data class RiwayatStokGlobalUi(
    val mutationId: String,
    val referensiId: String,
    val productId: String,
    val namaProduk: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val amount: String,
    val kategori: String,
    val dibuatPada: Timestamp,
    val isMasuk: Boolean,
    val isDibatalkan: Boolean,
    val bisaDibatalkan: Boolean
)

// === KONSTANTA FILTER ===
private const val FILTER_SEMUA = "Semua Penyesuaian"
private const val FILTER_ADJUSTMENT_KURANG = "Kurangi Stok"
private const val FILTER_ADJUSTMENT_ED = "Buang Produk Kedaluwarsa"

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
private fun StockHistoryScreen(
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    onCancelAdjustment: (RiwayatStokGlobalUi, () -> Unit) -> Unit,
    activityContext: AppCompatActivity
) {
    val firestore = FirebaseFirestore.getInstance()
    val coroutineScope = rememberCoroutineScope()

    // State Data
    var rows by remember { mutableStateOf<List<RiwayatStokGlobalUi>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var triggerRefresh by remember { mutableStateOf(0) }

    // State Filter & Pencarian
    var searchQuery by remember { mutableStateOf("") }
    var kategoriAktif by remember { mutableStateOf(FILTER_SEMUA) }
    var tanggalTunggal by remember { mutableStateOf<String?>(null) }
    var rentangMulai by remember { mutableStateOf<String?>(null) }
    var rentangSelesai by remember { mutableStateOf<String?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }

    // State popup detail riwayat stok
    var showDetailDialog by remember { mutableStateOf(false) }
    var detailTitle by remember { mutableStateOf("") }
    var detailBadge by remember { mutableStateOf("ADJUSTMENT") }
    var detailText by remember { mutableStateOf("") }
    var isDetailLoading by remember { mutableStateOf(false) }

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

    val dangerColor = if (isDark) Color(0xFFEF4444) else Color(0xFFDC2626)
    val warningColor = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)

    fun tampilkanDetailRiwayat(row: RiwayatStokGlobalUi) {
        detailTitle = row.namaProduk
        detailBadge = if (row.title.contains("ED", true)) "BUANG KEDALUWARSA" else "OPNAME"
        detailText = ""
        isDetailLoading = true
        showDetailDialog = true
        coroutineScope.launch {
            runCatching { RepositoriFirebaseUtama.buildStockMutationDetailText(row.mutationId) }
                .onSuccess { detail ->
                    // Mengubah label "Catatan:" menjadi "Alasan:"
                    val formattedDetail = detail.replace("Catatan:", "Alasan:").replace("catatan:", "alasan:")
                    detailText = formattedDetail
                    isDetailLoading = false
                }
                .onFailure { throwable ->
                    detailText = throwable.message ?: "Gagal memuat detail riwayat stok"
                    isDetailLoading = false
                }
        }
    }

    // Fungsi Fetch Data
    LaunchedEffect(triggerRefresh) {
        isLoading = true
        firestore.collection("RiwayatStok").get()
            .addOnSuccessListener { snapshot ->
                rows = snapshot.documents
                    .filter { doc ->
                        val jenisMutasi = doc.getString("jenisMutasi").orEmpty()
                        jenisMutasi.contains("ADJUSTMENT", ignoreCase = true) &&
                            !jenisMutasi.contains("PEMBATALAN", ignoreCase = true)
                    }
                    .map { doc ->
                        val jenisMutasi = doc.getString("jenisMutasi").orEmpty()
                        val qtyMasuk = doc.getLong("qtyMasuk") ?: 0L
                        val qtyKeluar = doc.getLong("qtyKeluar") ?: 0L
                        val stokSebelum = doc.getLong("stokSebelum") ?: 0L
                        val stokSesudah = doc.getLong("stokSesudah") ?: 0L
                        val catatan = doc.getString("catatan").orEmpty()
                        val sumberMutasi = doc.getString("sumberMutasi").orEmpty()
                        val referensiId = doc.getString("referensiId").orEmpty()
                        val namaProduk = doc.getString("namaProduk").orEmpty().ifBlank { "Produk" }
                        val produkId = doc.getString("idProduk").orEmpty()
                        val waktuMutasi = doc.getTimestamp("tanggalMutasi") ?: doc.getTimestamp("dibuatPada") ?: Timestamp.now()
                        val isDibatalkan = doc.getBoolean("dibatalkan") == true

                        val title = when {
                            jenisMutasi.contains("ADJUSTMENT_KADALUARSA", ignoreCase = true) -> "Buang Produk Kedaluwarsa"
                            jenisMutasi.contains("ADJUSTMENT_KURANG", ignoreCase = true) -> "Koreksi Pengurangan Stok"
                            else -> "Penyesuaian Stok"
                        }

                        RiwayatStokGlobalUi(
                            mutationId = doc.id,
                            referensiId = referensiId,
                            productId = produkId,
                            namaProduk = namaProduk,
                            title = title,
                            subtitle = buildString {
                                if (catatan.isNotBlank()) append("Alasan: $catatan")
                                else append(sumberMutasi.ifBlank { "Perubahan stok" })
                                append(" • Stok ${Formatter.ribuan(stokSebelum)} → ${Formatter.ribuan(stokSesudah)}")
                            },
                            badge = Formatter.readableDateTime(isoFromTimestamp(waktuMutasi)),
                            amount = when {
                                isDibatalkan -> "BATAL"
                                qtyMasuk > 0L -> "+${Formatter.ribuan(qtyMasuk)}"
                                qtyKeluar > 0L -> "-${Formatter.ribuan(qtyKeluar)}"
                                else -> "0"
                            },
                            isMasuk = qtyMasuk > 0L,
                            isDibatalkan = isDibatalkan,
                            kategori = kategoriUntuk(jenisMutasi, qtyMasuk, qtyKeluar),
                            dibuatPada = waktuMutasi,
                            bisaDibatalkan = referensiId.isNotBlank() && sumberMutasi.equals("PenyesuaianStok", true) && !isDibatalkan
                        )
                    }.sortedByDescending { it.dibuatPada.toDate().time }
                isLoading = false
            }
            .addOnFailureListener {
                isLoading = false
                onShowMessage("Gagal memuat riwayat stok: ${it.message}")
            }
    }

    // Perhitungan Filter Data Realtime
    val filteredRows by remember(rows, searchQuery, kategoriAktif, tanggalTunggal, rentangMulai, rentangSelesai) {
        derivedStateOf {
            rows.filter { row ->
                val cocokKeyword = searchQuery.isBlank() ||
                        row.namaProduk.contains(searchQuery, ignoreCase = true) ||
                        row.subtitle.contains(searchQuery, ignoreCase = true) ||
                        row.title.contains(searchQuery, ignoreCase = true)

                val cocokTanggal = cocokFilterTanggal(row.dibuatPada.toDate(), tanggalTunggal, rentangMulai, rentangSelesai)

                val cocokKategori = when (kategoriAktif) {
                    FILTER_ADJUSTMENT_KURANG -> row.title.contains("Kurang", true)
                    FILTER_ADJUSTMENT_ED -> row.title.contains("ED", true) || row.title.contains("Kedaluwarsa", true)
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
    val kategoriOpsi = listOf(FILTER_SEMUA, FILTER_ADJUSTMENT_KURANG, FILTER_ADJUSTMENT_ED)

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
                            Text("Riwayat Penyesuaian Stok", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text("Riwayat penyesuaian stok dan produk kedaluwarsa", style = MaterialTheme.typography.labelMedium, color = mutedColor)
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
            // --- PENCARIAN & FILTER ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari produk atau alasan...", color = mutedColor) },
                    leadingIcon = { Icon(Icons.Rounded.Search, "Cari", tint = mutedColor) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(100),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = Color.Transparent, focusedContainerColor = surfaceColor, unfocusedContainerColor = surfaceColor),
                    modifier = Modifier.weight(1f).height(54.dp)
                )

                val hasActiveFilter = kategoriAktif != FILTER_SEMUA || tanggalTunggal != null || rentangMulai != null
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
                if (kategoriAktif != FILTER_SEMUA || tanggalTunggal != null || rentangMulai != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (kategoriAktif != FILTER_SEMUA) {
                            FilterChipVisual(label = kategoriAktif, onRemove = { kategoriAktif = FILTER_SEMUA }, primaryColor)
                        }
                        if (tanggalTunggal != null) {
                            FilterChipVisual(label = Formatter.readableShortDate(tanggalTunggal!!), onRemove = { tanggalTunggal = null }, primaryColor)
                        } else if (rentangMulai != null && rentangSelesai != null) {
                            FilterChipVisual(label = "${Formatter.readableShortDate(rentangMulai!!)} - ${Formatter.readableShortDate(rentangSelesai!!)}", onRemove = { rentangMulai = null; rentangSelesai = null }, primaryColor)
                        }
                    }
                }
                Text("Menampilkan ${filteredRows.size} mutasi", color = mutedColor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
            }

            // --- DAFTAR RIWAYAT SKELETON ---
            if (isLoading) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(6) { HistoryRowSkeleton(surfaceColor, borderColor) }
                }
            } else if (filteredRows.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyDataView("Belum ada riwayat yang sesuai", "Coba ubah pencarian, filter, atau rentang tanggal.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(paginatedRows) { row ->
                        val itemColor = when {
                            row.isDibatalkan -> mutedColor
                            row.title.contains("ED", true) || row.title.contains("Kedaluwarsa", true) -> warningColor
                            else -> dangerColor
                        }
                        val iconVector = if (row.title.contains("ED", true) || row.title.contains("Kedaluwarsa", true)) Icons.Rounded.DeleteForever else Icons.Rounded.SyncAlt

                        StockHistoryCard(
                            row = row,
                            itemColor = itemColor,
                            iconVector = iconVector,
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            onClick = { tampilkanDetailRiwayat(row) },
                            onCancel = { onCancelAdjustment(row) { triggerRefresh++ } }
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

        // === DIALOG FILTER MODERN HIDE CALENDAR ===
        if (showFilterDialog) {
            ModernMobileFilterDialog(
                kategoriOpsi = kategoriOpsi,
                initialKategori = kategoriAktif,
                lockFilter = false,
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
                    kategoriAktif = FILTER_SEMUA
                    tanggalTunggal = null
                    rentangMulai = null
                    rentangSelesai = null
                    showFilterDialog = false
                },
                onApply = { kategori, mulaiStr, selesaiStr ->
                    kategoriAktif = kategori
                    if (mulaiStr == null && selesaiStr == null) {
                        tanggalTunggal = null
                        rentangMulai = null
                        rentangSelesai = null
                    } else if (mulaiStr != null && selesaiStr != null) {
                        if (mulaiStr == selesaiStr) {
                            tanggalTunggal = mulaiStr
                            rentangMulai = null
                            rentangSelesai = null
                        } else if (mulaiStr > selesaiStr) {
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
        if (showDetailDialog) {
            ProDetailDialog(
                title = detailTitle,
                badge = detailBadge,
                detailText = detailText,
                isLoading = isDetailLoading,
                primaryColor = primaryColor,
                surfaceColor = surfaceColor,
                textColor = textColor,
                borderColor = borderColor,
                onDismiss = { if (!isDetailLoading) showDetailDialog = false }
            )
        }
    }
}

// === KOMPONEN UI REUSABLE & MODERNA ===

@Composable
private fun FilterChipVisual(label: String, onRemove: () -> Unit, primaryColor: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = primaryColor.copy(alpha = 0.1f), modifier = Modifier.clickable(onClick = onRemove)) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = primaryColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Icon(Icons.Rounded.Close, "Hapus", tint = primaryColor, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun EmptyDataView(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ListAlt, null, tint = Color.Gray, modifier = Modifier.size(32.dp)) }
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = Color.Gray, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StockHistoryCard(
    row: RiwayatStokGlobalUi,
    itemColor: Color,
    iconVector: ImageVector,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    onClick: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(itemColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(iconVector, null, tint = itemColor, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.namaProduk, fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(row.title, color = textColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    if (row.isDibatalkan) {
                        Surface(shape = RoundedCornerShape(50), color = Color(0xFFEF4444).copy(alpha = 0.10f), border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.22f))) {
                            Text("Batal", color = Color(0xFFEF4444), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(row.subtitle, color = mutedColor, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.amount, fontWeight = FontWeight.Bold, color = itemColor, style = MaterialTheme.typography.titleMedium)
                Surface(shape = RoundedCornerShape(6.dp), color = mutedColor.copy(alpha = 0.1f)) {
                    Text(row.badge.split(" ").take(2).joinToString(" "), color = mutedColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                if (row.bisaDibatalkan) {
                    TextButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(28.dp)) {
                        Text("Batalkan", color = Color.Red, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
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
                Box(Modifier.height(16.dp).fillMaxWidth(0.6f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                Box(Modifier.height(12.dp).fillMaxWidth(0.4f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                Box(Modifier.height(10.dp).fillMaxWidth(0.8f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.height(18.dp).width(60.dp).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                Box(Modifier.height(14.dp).width(50.dp).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
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
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.height(14.dp).fillMaxWidth(0.5f).align(Alignment.CenterHorizontally).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
            Box(Modifier.height(12.dp).fillMaxWidth(0.3f).align(Alignment.CenterHorizontally).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
            Spacer(Modifier.height(8.dp))
            repeat(4) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.height(12.dp).fillMaxWidth(0.4f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                    Box(Modifier.height(12.dp).fillMaxWidth(0.25f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(borderColor.copy(alpha = 0.5f)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.height(16.dp).fillMaxWidth(0.3f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                Box(Modifier.height(18.dp).fillMaxWidth(0.35f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
            }
        }
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
    onDismiss: () -> Unit
) {
    val isDark = isSystemInDarkTheme()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surfaceColor,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column {
                Text(badge.uppercase(), color = primaryColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
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
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Tutup", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
    )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = surfaceColor,
        title = { Text("Filter Lanjutan", fontWeight = FontWeight.Bold, color = textColor) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Jenis Penyesuaian", fontWeight = FontWeight.SemiBold, color = textColor, style = MaterialTheme.typography.bodyMedium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    kategoriOpsi.chunked(2).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { pilihan ->
                                val isSelected = draftKategori == pilihan
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.Transparent,
                                    border = BorderStroke(1.dp, if (isSelected) primaryColor else borderColor),
                                    modifier = Modifier.weight(1f).clickable { draftKategori = pilihan }
                                ) {
                                    Text(pilihan, color = if (isSelected) primaryColor else textColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Divider(color = borderColor)
                Text("Rentang Tanggal", fontWeight = FontWeight.SemiBold, color = textColor, style = MaterialTheme.typography.bodyMedium)
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { showDateRangePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
            ) { Text("Terapkan", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onReset) { Text("Reset", color = mutedColor) } }
    )

    if (showDateRangePicker) {
        val localFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val utcFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        fun getUtcMillis(dateStr: String?): Long? = runCatching { utcFormat.parse(dateStr.orEmpty())?.time }.getOrNull()
        fun getUtcMillisFromOffset(offsetDays: Int): Long {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, offsetDays)
            return utcFormat.parse(localFormat.format(cal.time))?.time ?: 0L
        }

        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = getUtcMillis(draftMulai),
            initialSelectedEndDateMillis = getUtcMillis(draftSelesai)
        )
        var selectedPreset by remember { mutableStateOf("") }
        fun applyPreset(preset: String) {
            selectedPreset = preset
            val todayUtc = utcFormat.parse(localFormat.format(Date()))?.time ?: 0L
            when (preset) {
                "Hari Ini" -> dateRangePickerState.setSelection(todayUtc, todayUtc)
                "Kemarin" -> {
                    val yest = getUtcMillisFromOffset(-1)
                    dateRangePickerState.setSelection(yest, yest)
                }
                "Minggu Ini" -> {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    val sUtc = utcFormat.parse(localFormat.format(cal.time))?.time
                    cal.add(Calendar.DAY_OF_YEAR, 6)
                    val eUtc = utcFormat.parse(localFormat.format(cal.time))?.time
                    if (sUtc != null && eUtc != null) dateRangePickerState.setSelection(sUtc, eUtc)
                }
                "Bulan Ini" -> {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    val sUtc = utcFormat.parse(localFormat.format(cal.time))?.time
                    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                    val eUtc = utcFormat.parse(localFormat.format(cal.time))?.time
                    if (sUtc != null && eUtc != null) dateRangePickerState.setSelection(sUtc, eUtc)
                }
            }
        }

        Dialog(onDismissRequest = { showDateRangePicker = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.90f), shape = RoundedCornerShape(24.dp), color = surfaceColor) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Pilih Rentang Tanggal", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = { showDateRangePicker = false }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, "Tutup", tint = textColor) }
                    }
                    Divider(color = borderColor)
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Hari Ini", "Kemarin", "Minggu Ini", "Bulan Ini")) { preset ->
                            val isSelected = selectedPreset == preset
                            Surface(shape = RoundedCornerShape(100), color = if (isSelected) primaryColor.copy(alpha = 0.15f) else bgColor, border = BorderStroke(1.dp, if (isSelected) primaryColor else borderColor), modifier = Modifier.clickable { applyPreset(preset) }) {
                                Text(preset, color = if (isSelected) primaryColor else textColor, style = MaterialTheme.typography.labelMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        DateRangePicker(
                            state = dateRangePickerState, title = null, headline = null, showModeToggle = false, modifier = Modifier.fillMaxSize(),
                            colors = DatePickerDefaults.colors(containerColor = Color.Transparent, dayContentColor = textColor, selectedDayContainerColor = primaryColor, selectedDayContentColor = Color.White, dayInSelectionRangeContainerColor = primaryColor.copy(alpha = 0.2f), dayInSelectionRangeContentColor = primaryColor)
                        )
                    }
                    Divider(color = borderColor)
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showDateRangePicker = false }) { Text("Batal", color = mutedColor, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val sMillis = dateRangePickerState.selectedStartDateMillis
                                val eMillis = dateRangePickerState.selectedEndDateMillis
                                draftMulai = sMillis?.let { utcFormat.format(Date(it)) } ?: ""
                                draftSelesai = eMillis?.let { utcFormat.format(Date(it)) } ?: draftMulai
                                showDateRangePicker = false
                            },
                            shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) { Text("Simpan", fontWeight = FontWeight.Bold) }
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

// === FUNGSI UTILITAS ===

private fun isoFromTimestamp(timestamp: Timestamp?): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    return formatter.format((timestamp ?: Timestamp.now()).toDate())
}

private fun kategoriUntuk(jenisMutasi: String, qtyMasuk: Long, qtyKeluar: Long): String {
    return when {
        jenisMutasi.contains("KADALUARSA", ignoreCase = true) -> FILTER_ADJUSTMENT_ED
        jenisMutasi.contains("ADJUSTMENT", ignoreCase = true) -> FILTER_ADJUSTMENT_KURANG
        else -> "Lainnya"
    }
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