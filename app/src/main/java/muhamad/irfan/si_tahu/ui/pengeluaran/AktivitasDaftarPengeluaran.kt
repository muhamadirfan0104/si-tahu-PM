package muhamad.irfan.si_tahu.ui.pengeluaran

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.utama.SiTahuProTheme
import muhamad.irfan.si_tahu.util.Formatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AktivitasDaftarPengeluaran : AktivitasDasar() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        setContent {
            SiTahuProTheme {
                ExpenseListScreen(
                    onNavigateBack = { finish() },
                    onShowMessage = { pesan -> showMessage(pesan) },
                    onNavigateToForm = { expenseId ->
                        val intent = Intent(this, AktivitasFormPengeluaran::class.java)
                        if (expenseId != null) intent.putExtra(AktivitasFormPengeluaran.EXTRA_EXPENSE_ID, expenseId)
                        startActivity(intent)
                    },
                    onShowConfirmation = { title, message, confirmLabel, action ->
                        showConfirmationModal(title, message, confirmLabel, action)
                    }
                )
            }
        }
    }
}

// === MODEL DATA BANTUAN UNTUK COMPOSE ===
private data class PengeluaranItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val amount: String,
    val amountLong: Long,
    val tanggalIso: String,
    val kategori: String,
    val catatan: String
)

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
private fun ExpenseListScreen(
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    onNavigateToForm: (String?) -> Unit,
    onShowConfirmation: (String, String, String, () -> Unit) -> Unit
) {
    val firestore = FirebaseFirestore.getInstance()
    val coroutineScope = rememberCoroutineScope()

    // State Data
    var expenses by remember { mutableStateOf<List<PengeluaranItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var triggerRefresh by remember { mutableStateOf(0) }

    // State Pencarian & Filter
    var searchQuery by remember { mutableStateOf("") }
    val filterOptions = listOf("Semua", "Hari ini", "7 hari", "30 hari", "Rentang")
    var activeFilter by remember { mutableStateOf(filterOptions.first()) }
    var tanggalMulaiFilter by remember { mutableStateOf<String?>(null) }
    var tanggalSelesaiFilter by remember { mutableStateOf<String?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }

    // State Dialog Detail Struk
    var showDetailDialog by remember { mutableStateOf(false) }
    var detailTitle by remember { mutableStateOf("") }
    var detailBadge by remember { mutableStateOf("PENGELUARAN") }
    var detailText by remember { mutableStateOf("") }
    var isDetailLoading by remember { mutableStateOf(false) }

    // Paginasi
    var halamanSaatIni by remember { mutableStateOf(1) }
    val itemPerHalaman = 15

    // Tema Warna Pro
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF111827) else Color(0xFFF3F4F6)
    val surfaceColor = if (isDark) Color(0xFF1F2937) else Color(0xFFFFFFFF)
    val primaryColor = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
    val textColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF111827)
    val mutedColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)

    val dangerColor = if (isDark) Color(0xFFEF4444) else Color(0xFFDC2626)
    val warningColor = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)

    // Load Data
    LaunchedEffect(triggerRefresh) {
        isLoading = true
        runCatching { RepositoriFirebaseUtama.muatRiwayatPengeluaran() }
            .onSuccess { rows ->
                expenses = rows.map {
                    PengeluaranItem(
                        id = it.id,
                        title = it.title,
                        subtitle = if (it.catatan.isBlank()) it.subtitle else "${it.subtitle} • ${it.catatan}",
                        badge = it.badge,
                        amount = it.amount,
                        amountLong = it.amount.filter { char -> char.isDigit() }.toLongOrNull() ?: 0L,
                        tanggalIso = it.tanggalIso,
                        kategori = it.kategori,
                        catatan = it.catatan
                    )
                }
                isLoading = false
            }
            .onFailure {
                expenses = emptyList()
                isLoading = false
                onShowMessage(it.message ?: "Gagal memuat pengeluaran")
            }
    }

    // Helper Filter Tanggal
    fun dalamHariTerakhir(key: String, days: Int): Boolean {
        val today = Formatter.currentDateOnly()
        return try {
            val startMillis = Formatter.parseDate("${today}T00:00:00").time - (days - 1) * 24L * 60L * 60L * 1000L
            val start = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(startMillis))
            key in start..today
        } catch (e: Exception) {
            false
        }
    }

    fun cocokRange(tanggalIso: String, filter: String): Boolean {
        val key = Formatter.toDateOnly(tanggalIso)
        val today = Formatter.currentDateOnly()
        return when (filter) {
            "Hari ini" -> key == today
            "7 hari" -> dalamHariTerakhir(key, 7)
            "30 hari" -> dalamHariTerakhir(key, 30)
            "Rentang" -> {
                val start = tanggalMulaiFilter.orEmpty()
                val end = tanggalSelesaiFilter.orEmpty().ifBlank { start }
                start.isNotBlank() && end.isNotBlank() && key in minOf(start, end)..maxOf(start, end)
            }
            else -> true
        }
    }

    val labelFilterAktif by remember(activeFilter, tanggalMulaiFilter, tanggalSelesaiFilter) {
        derivedStateOf {
            if (activeFilter == "Rentang" && !tanggalMulaiFilter.isNullOrBlank()) {
                val start = tanggalMulaiFilter!!
                val end = tanggalSelesaiFilter.orEmpty().ifBlank { start }
                if (start == end) {
                    Formatter.readableShortDate(start)
                } else {
                    "${Formatter.readableShortDate(start)} - ${Formatter.readableShortDate(end)}"
                }
            } else activeFilter
        }
    }

    // Logika Pemrosesan List
    val dateFilteredRows by remember(expenses, activeFilter, tanggalMulaiFilter, tanggalSelesaiFilter) {
        derivedStateOf { expenses.filter { item -> cocokRange(item.tanggalIso, activeFilter) } }
    }

    val filteredRows by remember(dateFilteredRows, searchQuery) {
        derivedStateOf {
            dateFilteredRows.filter { item ->
                val keyword = searchQuery.lowercase()
                keyword.isBlank() ||
                        item.title.lowercase().contains(keyword) ||
                        item.subtitle.lowercase().contains(keyword) ||
                        item.kategori.lowercase().contains(keyword) ||
                        item.catatan.lowercase().contains(keyword) ||
                        item.id.lowercase().contains(keyword) ||
                        item.amount.lowercase().contains(keyword)
            }
        }
    }

    LaunchedEffect(searchQuery, activeFilter, tanggalMulaiFilter, tanggalSelesaiFilter) { halamanSaatIni = 1 }

    // Paginasi
    val totalPages = maxOf(1, ((filteredRows.size - 1) / itemPerHalaman) + 1)
    if (halamanSaatIni > totalPages) halamanSaatIni = totalPages
    val paginatedRows = filteredRows.drop((halamanSaatIni - 1) * itemPerHalaman).take(itemPerHalaman)

    // Aksi Fetch Detail
    fun fetchDetailThenAction(expenseId: String, labelTitle: String, badge: String) {
        detailTitle = labelTitle
        detailBadge = badge
        detailText = ""
        isDetailLoading = true
        showDetailDialog = true
        coroutineScope.launch {
            runCatching { RepositoriFirebaseUtama.buildExpenseDetailText(expenseId) }
                .onSuccess { detail ->
                    detailText = detail.replace("Catatan:", "Alasan:").replace("catatan:", "alasan:")
                    isDetailLoading = false
                }
                .onFailure {
                    detailText = it.message ?: "Gagal memuat detail pengeluaran"
                    isDetailLoading = false
                }
        }
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
                            Text("Pengeluaran", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text("Input dan kelola biaya operasional", style = MaterialTheme.typography.labelMedium, color = mutedColor)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Rounded.ArrowBack, "Kembali", tint = textColor) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToForm(null) }, containerColor = primaryColor, shape = CircleShape) {
                Icon(Icons.Rounded.Add, "Tambah Pengeluaran", tint = Color.White)
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
                    placeholder = { Text("Cari pengeluaran...", color = mutedColor) },
                    leadingIcon = { Icon(Icons.Rounded.Search, "Cari", tint = mutedColor) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(100),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = Color.Transparent, focusedContainerColor = surfaceColor, unfocusedContainerColor = surfaceColor),
                    modifier = Modifier.weight(1f).height(54.dp)
                )

                val hasActiveFilter = activeFilter != "Semua"
                Surface(
                    shape = CircleShape,
                    color = if (hasActiveFilter) primaryColor else surfaceColor,
                    border = if (hasActiveFilter) null else BorderStroke(1.dp, borderColor),
                    modifier = Modifier.size(54.dp).clickable {
                        showFilterDialog = true
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.FilterList, "Filter", tint = if (hasActiveFilter) Color.White else textColor)
                    }
                }
            }

            // --- CHIPS FILTER AKTIF ---
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (activeFilter != "Semua") {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        FilterChipVisual(label = labelFilterAktif, onRemove = {
                            activeFilter = "Semua"
                            tanggalMulaiFilter = null
                            tanggalSelesaiFilter = null
                        }, primaryColor)
                    }
                }
                Text("Menampilkan ${filteredRows.size} pengeluaran", color = mutedColor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
            }

            // --- DAFTAR PENGELUARAN ---
            if (isLoading) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(6) { ExpenseRowSkeleton(surfaceColor, borderColor) }
                }
            } else if (filteredRows.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyDataView("Tidak ada data pengeluaran", "Coba hapus kriteria filter atau ubah pencarian.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp), // Fix padding bawah agar tidak tertutup FAB
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(paginatedRows) { expense ->
                        ExpenseCard(
                            expense = expense,
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            primaryColor = primaryColor,
                            warningColor = warningColor,
                            dangerColor = dangerColor,
                            onViewDetail = {
                                fetchDetailThenAction(expense.id, expense.title, "PENGELUARAN")
                            },
                            onEdit = { onNavigateToForm(expense.id) },
                            onDelete = {
                                onShowConfirmation("Hapus pengeluaran", "Pengeluaran ${expense.title} akan disembunyikan dari laporan aktif.", "Hapus") {
                                    coroutineScope.launch {
                                        runCatching { RepositoriFirebaseUtama.hapusPengeluaran(expense.id) }
                                            .onSuccess {
                                                onShowMessage("Pengeluaran berhasil dihapus dari daftar aktif")
                                                triggerRefresh++
                                            }
                                            .onFailure { onShowMessage(it.message ?: "Gagal menghapus pengeluaran") }
                                    }
                                }
                            }
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

        // === DIALOG FILTER TANGGAL MODERN ---
        if (showFilterDialog) {
            ModernExpenseFilterDialog(
                filterOptions = filterOptions,
                initialFilter = activeFilter,
                initialRentangMulai = tanggalMulaiFilter,
                initialRentangSelesai = tanggalSelesaiFilter,
                primaryColor = primaryColor,
                surfaceColor = surfaceColor,
                bgColor = bgColor,
                textColor = textColor,
                mutedColor = mutedColor,
                borderColor = borderColor,
                onDismiss = { showFilterDialog = false },
                onReset = {
                    activeFilter = "Semua"
                    tanggalMulaiFilter = null
                    tanggalSelesaiFilter = null
                    showFilterDialog = false
                },
                onApply = { filter, mulaiStr, selesaiStr ->
                    activeFilter = filter
                    tanggalMulaiFilter = mulaiStr
                    tanggalSelesaiFilter = selesaiStr
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

// === KOMPONEN DIALOG FILTER MODERN ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernExpenseFilterDialog(
    filterOptions: List<String>,
    initialFilter: String,
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
    onApply: (filter: String, mulai: String?, selesai: String?) -> Unit
) {
    var draftFilter by remember { mutableStateOf(initialFilter) }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = surfaceColor,
        title = { Text("Filter Pengeluaran", fontWeight = FontWeight.Bold, color = textColor) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Periode Waktu Cepat", fontWeight = FontWeight.SemiBold, color = textColor, style = MaterialTheme.typography.bodyMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(filterOptions.filter { it != "Rentang" }) { pilihan ->
                        val isSelected = draftFilter == pilihan
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (isSelected) primaryColor else borderColor),
                            modifier = Modifier.clickable {
                                draftFilter = pilihan
                                draftMulai = ""
                                draftSelesai = ""
                            }
                        ) {
                            Text(
                                text = pilihan,
                                color = if (isSelected) primaryColor else textColor,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = borderColor)
                Text("Rentang Kalender Spesifik", fontWeight = FontWeight.SemiBold, color = textColor, style = MaterialTheme.typography.bodyMedium)
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        draftFilter = "Rentang"
                        showDateRangePicker = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (draftFilter == "Rentang") primaryColor.copy(alpha = 0.08f) else Color.Transparent,
                    border = BorderStroke(1.dp, if (draftFilter == "Rentang") primaryColor else borderColor)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.DateRange, contentDescription = null, tint = if (draftFilter == "Rentang") primaryColor else mutedColor)
                        Text(text = dateLabel, color = if (draftMulai.isNotBlank()) textColor else mutedColor, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(draftFilter, draftMulai.ifBlank { null }, draftSelesai.ifBlank { null }) },
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
                    HorizontalDivider(color = borderColor)
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
                    HorizontalDivider(color = borderColor)
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
}

// === FUNGSI UTILITAS PENGELUARAN ===

private fun String?.ifBlankOrNull(defaultValue: () -> String?): String? {
    return if (this.isNullOrBlank()) defaultValue() else this
}

private fun normalizeExpenseDateInput(input: String?): String? {
    if (input.isNullOrBlank()) return null
    val value = input.trim()
    val patterns = listOf("yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy")
    val output = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    for (pattern in patterns) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(value)
        }.getOrNull()
        if (parsed != null) return output.format(parsed)
    }
    return null
}

// === KOMPONEN REUSABLE & SKELETON ===

@Composable
private fun FilterChipVisual(label: String, onRemove: () -> Unit, primaryColor: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = primaryColor.copy(alpha = 0.1f), modifier = Modifier.clickable(onClick = onRemove)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = primaryColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Icon(Icons.Rounded.Close, "Hapus", tint = primaryColor, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun EmptyDataView(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ReceiptLong, null, tint = Color.Gray, modifier = Modifier.size(32.dp)) }
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = Color.Gray, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ExpenseRowSkeleton(surfaceColor: Color, borderColor: Color) {
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

@Composable
private fun ExpenseCard(
    expense: PengeluaranItem,
    surfaceColor: Color, borderColor: Color, textColor: Color, mutedColor: Color,
    primaryColor: Color, warningColor: Color, dangerColor: Color,
    onViewDetail: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // State untuk mengontrol dropdown menu melayang
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onViewDetail)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header: Icon, Nama, Kode, dan Titik Tiga Dropdown
            Row(
                verticalAlignment = Alignment.CenterVertically, // Sejajar lurus di tengah
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(warningColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ReceiptLong, null, tint = warningColor, modifier = Modifier.size(24.dp))
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        text = expense.title,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = expense.subtitle,
                        color = mutedColor,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Dropdown Menu Titik Tiga Melayang
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Opsi", tint = mutedColor)
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(surfaceColor)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Lihat Detail", color = textColor) },
                            onClick = { showMenu = false; onViewDetail() }
                        )

                        DropdownMenuItem(
                            text = { Text("Edit Data", color = textColor) },
                            onClick = { showMenu = false; onEdit() }
                        )

                        DropdownMenuItem(
                            text = { Text("Hapus Pengeluaran", color = dangerColor, fontWeight = FontWeight.SemiBold) },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }

            HorizontalDivider(color = borderColor)

            // Body: Kategori & Total Nominal
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(6.dp), color = warningColor.copy(alpha = 0.1f)) {
                    Text(
                        text = expense.badge,
                        color = warningColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(expense.amount, fontWeight = FontWeight.Bold, color = warningColor, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}