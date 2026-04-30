package muhamad.irfan.si_tahu.ui.stok

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Inventory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.utama.SiTahuProTheme
import muhamad.irfan.si_tahu.util.Formatter

class AktivitasMonitoringStok : AktivitasDasar() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        setContent {
            SiTahuProTheme {
                StockMonitoringScreen(
                    onNavigateBack = { finish() },
                    onShowMessage = { pesan -> showMessage(pesan) },
                    onNavigateToDetail = { productId ->
                        val intent = Intent(this, AktivitasDetailStok::class.java)
                        intent.putExtra(EXTRA_PRODUCT_ID, productId)
                        startActivity(intent)
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "extra_product_id"
    }
}

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

// === KOMPONEN UTAMA UI COMPOSE ===
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StockMonitoringScreen(
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // State Data
    var allProducts by remember { mutableStateOf<List<Produk>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var triggerRefresh by remember { mutableStateOf(0) }

    // State Filter & Pencarian
    var searchQuery by remember { mutableStateOf("") }
    var showFilterDialog by remember { mutableStateOf(false) }

    val statusOptions = listOf("Semua Stok", "Aman", "Menipis", "Habis", "Perlu Tindakan")
    var selectedStatus by remember { mutableStateOf(statusOptions.first()) }

    val kategoriOptions = listOf("Semua Jenis", "Dasar", "Olahan")
    var selectedKategori by remember { mutableStateOf(kategoriOptions.first()) }

    // Paginasi
    var currentPage by remember { mutableStateOf(1) }
    val pageSize = 15

    // Tema Warna Pro
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

    // Load Data
    LaunchedEffect(triggerRefresh) {
        isLoading = true
        runCatching { RepositoriFirebaseUtama.muatSemuaProduk() }
            .onSuccess { result ->
                allProducts = result.filter { !it.deleted }.sortedBy { it.name.lowercase() }
                isLoading = false
            }
            .onFailure { e ->
                allProducts = emptyList()
                isLoading = false
                onShowMessage("Gagal memuat stok: ${e.message}")
            }
    }

    // Helper Status
    fun getStatusStok(produk: Produk): String {
        val layakJual = produk.safeStock + produk.nearExpiredStock + produk.edTodayStock
        return when {
            layakJual <= 0 -> "Habis"
            layakJual <= produk.minStock -> "Menipis"
            else -> "Aman"
        }
    }

    // Pemrosesan List & Filter
    val filteredProducts by remember(allProducts, searchQuery, selectedStatus, selectedKategori) {
        derivedStateOf {
            allProducts.filter { produk ->
                val keyword = searchQuery.lowercase()
                val cocokNama = keyword.isBlank() || produk.name.lowercase().contains(keyword)

                val status = getStatusStok(produk)
                val cocokStatus = when (selectedStatus) {
                    "Aman" -> status == "Aman"
                    "Menipis" -> status == "Menipis"
                    "Habis" -> status == "Habis"
                    "Perlu Tindakan" -> produk.expiredStock > 0 || produk.edTodayStock > 0 || produk.nearExpiredStock > 0
                    else -> true
                }

                val cocokKategori = when (selectedKategori) {
                    "Dasar" -> produk.category.equals("DASAR", ignoreCase = true)
                    "Olahan" -> produk.category.equals("OLAHAN", ignoreCase = true)
                    else -> true
                }

                cocokNama && cocokStatus && cocokKategori
            }
        }
    }

    LaunchedEffect(searchQuery, selectedStatus, selectedKategori) { currentPage = 1 }

    // Logika Paginasi
    val totalPages = maxOf(1, ((filteredProducts.size - 1) / pageSize) + 1)
    if (currentPage > totalPages) currentPage = totalPages
    val paginatedProducts = filteredProducts.drop((currentPage - 1) * pageSize).take(pageSize)

    val jumlahFilterAktif = (if (selectedStatus != "Semua Stok") 1 else 0) + (if (selectedKategori != "Semua Jenis") 1 else 0)

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
                            Text("Monitoring Stok", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text("Fisik, jual, dan kadaluarsa", style = MaterialTheme.typography.labelMedium, color = mutedColor)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Rounded.ArrowBack, "Kembali", tint = textColor) } },
                    actions = {
                        IconButton(onClick = { triggerRefresh++ }) {
                            Icon(Icons.Rounded.Refresh, "Segarkan", tint = primaryColor)
                        }
                    },
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
                    placeholder = { Text("Cari nama produk...", color = mutedColor) },
                    leadingIcon = { Icon(Icons.Rounded.Search, "Cari", tint = mutedColor) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(100),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = surfaceColor,
                        unfocusedContainerColor = surfaceColor
                    ),
                    modifier = Modifier.weight(1f).height(54.dp)
                )

                Box {
                    Surface(
                        shape = CircleShape,
                        color = if (jumlahFilterAktif > 0) primaryColor else surfaceColor,
                        border = if (jumlahFilterAktif > 0) null else BorderStroke(1.dp, borderColor),
                        modifier = Modifier.size(54.dp).clickable { showFilterDialog = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.FilterList, "Filter", tint = if (jumlahFilterAktif > 0) Color.White else textColor)
                        }
                    }
                    if (jumlahFilterAktif > 0) {
                        Box(
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(12.dp).clip(CircleShape).background(dangerColor),
                            contentAlignment = Alignment.Center
                        ) {}
                    }
                }
            }

            // --- CHIPS FILTER AKTIF ---
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (jumlahFilterAktif > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selectedKategori != "Semua Jenis") {
                            FilterChipVisual(label = "Jenis: $selectedKategori", onRemove = { selectedKategori = "Semua Jenis" }, primaryColor)
                        }
                        if (selectedStatus != "Semua Stok") {
                            FilterChipVisual(label = "Status: $selectedStatus", onRemove = { selectedStatus = "Semua Stok" }, primaryColor)
                        }
                    }
                }
                Text("Menampilkan ${filteredProducts.size} produk", color = mutedColor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
            }

            // --- DAFTAR PRODUK (STOK) ---
            if (isLoading) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(6) { StockCardSkeleton(surfaceColor, borderColor) }
                }
            } else if (filteredProducts.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Inventory, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                        }
                        Text(if (allProducts.isEmpty()) "Belum ada produk." else "Tidak ada produk yang cocok.", color = mutedColor, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(paginatedProducts) { produk ->
                        val status = getStatusStok(produk)
                        StockCard(
                            produk = produk,
                            statusText = status,
                            surfaceColor = surfaceColor, borderColor = borderColor,
                            textColor = textColor, mutedColor = mutedColor,
                            primaryColor = primaryColor, successColor = successColor,
                            warningColor = warningColor, dangerColor = dangerColor,
                            onClick = { onNavigateToDetail(produk.id) }
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
                                    IconButton(onClick = { if (currentPage > 1) currentPage-- }, enabled = currentPage > 1) {
                                        Icon(Icons.Rounded.ChevronLeft, "Sebelumnya", tint = if (currentPage > 1) primaryColor else mutedColor)
                                    }
                                    Text("Hal $currentPage dari $totalPages", color = textColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
                                    IconButton(onClick = { if (currentPage < totalPages) currentPage++ }, enabled = currentPage < totalPages) {
                                        Icon(Icons.Rounded.ChevronRight, "Selanjutnya", tint = if (currentPage < totalPages) primaryColor else mutedColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // === DIALOG FILTER MELAYANG (MODERN M3) ===
        if (showFilterDialog) {
            var draftStatus by remember { mutableStateOf(selectedStatus) }
            var draftKategori by remember { mutableStateOf(selectedKategori) }

            AlertDialog(
                onDismissRequest = { showFilterDialog = false },
                shape = RoundedCornerShape(24.dp),
                containerColor = surfaceColor,
                title = { Text("Filter Stok", fontWeight = FontWeight.Bold, color = textColor) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Kategori Produk
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Jenis Produk", fontWeight = FontWeight.SemiBold, color = textColor, style = MaterialTheme.typography.bodyMedium)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                kategoriOptions.forEach { pilihan ->
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

                        Divider(color = borderColor)

                        // Status Stok
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Status Ketersediaan", fontWeight = FontWeight.SemiBold, color = textColor, style = MaterialTheme.typography.bodyMedium)
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                statusOptions.forEach { pilihan ->
                                    val isSelected = draftStatus == pilihan
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.Transparent,
                                        border = BorderStroke(1.dp, if (isSelected) primaryColor else borderColor),
                                        modifier = Modifier.clickable { draftStatus = pilihan }
                                    ) {
                                        Text(
                                            text = pilihan,
                                            color = if (isSelected) primaryColor else textColor,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            selectedStatus = draftStatus
                            selectedKategori = draftKategori
                            showFilterDialog = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        Text("Terapkan", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        selectedStatus = "Semua Stok"
                        selectedKategori = "Semua Jenis"
                        showFilterDialog = false
                    }) {
                        Text("Reset", color = mutedColor)
                    }
                }
            )
        }
    }
}

// === KOMPONEN UI REUSABLE ===

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
private fun StockCardSkeleton(surfaceColor: Color, borderColor: Color) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).adminShimmerEffect())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.height(16.dp).fillMaxWidth(0.6f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                    Box(Modifier.height(12.dp).fillMaxWidth(0.3f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                }
            }
            HorizontalDivider(color = borderColor)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.height(14.dp).fillMaxWidth(0.4f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                    Box(Modifier.height(12.dp).fillMaxWidth(0.3f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                }
                Box(Modifier.height(28.dp).width(80.dp).clip(RoundedCornerShape(8.dp)).adminShimmerEffect())
            }
        }
    }
}

// === KOMPONEN KARTU STOK ===
@Composable
private fun StockCard(
    produk: Produk, statusText: String,
    surfaceColor: Color, borderColor: Color, textColor: Color, mutedColor: Color,
    primaryColor: Color, successColor: Color, warningColor: Color, dangerColor: Color,
    onClick: () -> Unit
) {
    val layakJual = produk.safeStock + produk.nearExpiredStock + produk.edTodayStock
    val isExpiredAlert = produk.expiredStock > 0 || produk.edTodayStock > 0 || produk.nearExpiredStock > 0

    // Penentuan Warna berdasarkan Status
    val statusColor = when {
        produk.expiredStock > 0 -> dangerColor
        produk.edTodayStock > 0 || produk.nearExpiredStock > 0 -> warningColor
        statusText == "Aman" -> successColor
        statusText == "Menipis" -> warningColor
        else -> dangerColor // Habis
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header: Icon, Nama, dan Kategori
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    val icon = if (produk.expiredStock > 0) Icons.Rounded.Warning else Icons.Rounded.Inventory
                    Icon(icon, null, tint = statusColor, modifier = Modifier.size(24.dp))
                }

                Column(Modifier.weight(1f)) {
                    Text(produk.name, fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(produk.category, color = mutedColor, style = MaterialTheme.typography.labelMedium)
                }
            }

            HorizontalDivider(color = borderColor)

            // Body: Rincian Stok
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Layak Jual: ${Formatter.ribuan(layakJual.toLong())} ${produk.unit}", color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("Fisik Total: ${Formatter.ribuan(produk.stock.toLong())} ${produk.unit}", color = mutedColor, style = MaterialTheme.typography.bodySmall)

                    if (produk.edTodayStock > 0) {
                        Text("ED Hari Ini: ${Formatter.ribuan(produk.edTodayStock.toLong())} ${produk.unit}", color = warningColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    if (produk.nearExpiredStock > 0) {
                        Text("Hampir ED: ${Formatter.ribuan(produk.nearExpiredStock.toLong())} ${produk.unit}", color = warningColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    if (produk.expiredStock > 0) {
                        Text("Kadaluarsa: ${Formatter.ribuan(produk.expiredStock.toLong())} ${produk.unit}", color = dangerColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }

                // Badge Status Stok di Kanan
                val textBadge = if (isExpiredAlert) "Perlu Tindakan" else statusText
                Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.1f)) {
                    Text(
                        text = textBadge,
                        color = statusColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}