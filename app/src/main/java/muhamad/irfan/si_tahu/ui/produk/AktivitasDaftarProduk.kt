package muhamad.irfan.si_tahu.ui.produk

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.harga.AktivitasDaftarHarga
import muhamad.irfan.si_tahu.ui.parameter.AktivitasDaftarParameter
import muhamad.irfan.si_tahu.ui.utama.SiTahuProTheme
import muhamad.irfan.si_tahu.util.EkstraAplikasi

class AktivitasDaftarProduk : AktivitasDasar() {

    private val refreshOnResume = MutableStateFlow(0)

    override fun onResume() {
        super.onResume()
        refreshOnResume.value++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        setContent {
            SiTahuProTheme {
                val autoRefreshTrigger by refreshOnResume.collectAsState()
                ProductListScreen(
                    autoRefreshTrigger = autoRefreshTrigger,
                    onNavigateBack = { finish() },
                    activityContext = this@AktivitasDaftarProduk,
                    onShowMessage = { pesan -> showMessage(pesan) },
                    onNavigateToForm = { productId ->
                        val intent = Intent(this, AktivitasFormProduk::class.java)
                        if (productId != null) intent.putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, productId)
                        startActivity(intent)
                    },
                    onNavigateToHarga = { productId ->
                        startActivity(Intent(this, AktivitasDaftarHarga::class.java).putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, productId))
                    },
                    onNavigateToParameter = { productId ->
                        startActivity(Intent(this, AktivitasDaftarParameter::class.java).putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, productId))
                    },
                    onConfirmDelete = { product, onSuccess ->
                        showConfirmationModal(
                            title = "Hapus produk?",
                            message = "Produk ${product.namaProduk} akan dihapus dari daftar aktif.",
                            confirmLabel = "Hapus"
                        ) {
                            FirebaseFirestore.getInstance().collection("Produk").document(product.id)
                                .update(
                                    mapOf(
                                        "dihapus" to true,
                                        "aktifDijual" to false,
                                        "tampilDiKasir" to false,
                                        "dihapusPada" to Timestamp.now(),
                                        "diperbaruiPada" to Timestamp.now()
                                    )
                                )
                                .addOnSuccessListener {
                                    showMessage("Produk berhasil dihapus.")
                                    onSuccess()
                                }
                                .addOnFailureListener { showMessage("Gagal menghapus produk: ${it.message}") }
                        }
                    }
                )
            }
        }
    }
}

// === MODEL DATA ===
private data class DataBarisProduk(
    val id: String,
    val kodeProduk: String,
    val namaProduk: String,
    val jenisProduk: String,
    val satuan: String,
    val stokSaatIni: Long,
    val stokMinimum: Long,
    val tampilDiKasir: Boolean,
    val aktifDijual: Boolean,
    val dihapus: Boolean,
    var statusHarga: StatusHargaProduk = StatusHargaProduk(),
    var statusParameter: StatusParameterProduk = StatusParameterProduk()
)

private data class StatusHargaProduk(val total: Int = 0, val aktif: Int = 0)
private data class StatusParameterProduk(val total: Int = 0, val aktif: Int = 0)

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
private fun ProductListScreen(
    autoRefreshTrigger: Int,
    onNavigateBack: () -> Unit,
    activityContext: AppCompatActivity,
    onShowMessage: (String) -> Unit,
    onNavigateToForm: (String?) -> Unit,
    onNavigateToHarga: (String) -> Unit,
    onNavigateToParameter: (String) -> Unit,
    onConfirmDelete: (DataBarisProduk, () -> Unit) -> Unit
) {
    val firestore = FirebaseFirestore.getInstance()

    // State Data
    var products by remember { mutableStateOf<List<DataBarisProduk>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var triggerRefresh by remember { mutableStateOf(0) }

    // State Filter & Pencarian
    var searchQuery by remember { mutableStateOf("") }
    val categories = listOf("Semua", "DASAR", "OLAHAN")
    var kategoriAktif by remember { mutableStateOf(categories.first()) }
    var showFilterDialog by remember { mutableStateOf(false) }

    // State Paginasi
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

    val successColor = if (isDark) Color(0xFF10B981) else Color(0xFF059669)
    val warningColor = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)
    val dangerColor = if (isDark) Color(0xFFEF4444) else Color(0xFFDC2626)
    val infoColor = if (isDark) Color(0xFF8B5CF6) else Color(0xFF6D28D9)

    // Load Data
    LaunchedEffect(triggerRefresh, autoRefreshTrigger) {
        isLoading = true
        firestore.collection("Produk")
            .orderBy("dibuatPada", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val loadedProducts = snapshot.documents
                    .filter { it.getBoolean("dihapus") != true }
                    .map { doc ->
                        DataBarisProduk(
                            id = doc.id,
                            kodeProduk = doc.getString("kodeProduk").orEmpty(),
                            namaProduk = doc.getString("namaProduk").orEmpty(),
                            jenisProduk = doc.getString("jenisProduk").orEmpty(),
                            satuan = doc.getString("satuan").orEmpty(),
                            stokSaatIni = doc.getLong("stokSaatIni") ?: 0L,
                            stokMinimum = doc.getLong("stokMinimum") ?: 0L,
                            tampilDiKasir = doc.getBoolean("tampilDiKasir") ?: false,
                            aktifDijual = doc.getBoolean("aktifDijual") ?: false,
                            dihapus = doc.getBoolean("dihapus") ?: false
                        )
                    }

                if (loadedProducts.isEmpty()) {
                    products = emptyList()
                    isLoading = false
                    return@addOnSuccessListener
                }

                val priceTasks = loadedProducts.map { product ->
                    firestore.collection("Produk").document(product.id).collection("hargaJual").get()
                        .continueWith { task ->
                            val docs = task.result?.documents.orEmpty().filter { it.getBoolean("dihapus") != true }
                            product.id to StatusHargaProduk(total = docs.size, aktif = docs.count { it.getBoolean("aktif") ?: true })
                        }
                }

                val paramTasks = loadedProducts.map { product ->
                    firestore.collection("Produk").document(product.id).collection("parameterProduksi").get()
                        .continueWith { task ->
                            val docs = task.result?.documents.orEmpty().filter { it.getBoolean("dihapus") != true }
                            product.id to StatusParameterProduk(total = docs.size, aktif = docs.count { it.getBoolean("aktif") == true })
                        }
                }

                Tasks.whenAllSuccess<Pair<String, StatusHargaProduk>>(priceTasks).addOnCompleteListener { priceResultTask ->
                    Tasks.whenAllSuccess<Pair<String, StatusParameterProduk>>(paramTasks).addOnCompleteListener { paramResultTask ->
                        val priceMap = priceResultTask.result?.toMap() ?: emptyMap()
                        val paramMap = paramResultTask.result?.toMap() ?: emptyMap()

                        products = loadedProducts.map { p ->
                            p.copy(
                                statusHarga = priceMap[p.id] ?: StatusHargaProduk(),
                                statusParameter = paramMap[p.id] ?: StatusParameterProduk()
                            )
                        }
                        isLoading = false
                    }
                }
            }
            .addOnFailureListener {
                products = emptyList()
                isLoading = false
                onShowMessage("Gagal memuat produk: ${it.message}")
            }
    }

    // Filter Logic
    val filteredRows by remember(products, searchQuery, kategoriAktif) {
        derivedStateOf {
            products.filter { p ->
                val cocokKeyword = searchQuery.isBlank() || p.namaProduk.contains(searchQuery, ignoreCase = true) || p.kodeProduk.contains(searchQuery, ignoreCase = true)
                val cocokKategori = kategoriAktif == "Semua" || p.jenisProduk.equals(kategoriAktif, ignoreCase = true)
                cocokKeyword && cocokKategori
            }
        }
    }

    LaunchedEffect(searchQuery, kategoriAktif) { halamanSaatIni = 1 }

    // Pagination
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
                            Text("Daftar Produk", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text("Kelola produk dasar & olahan", style = MaterialTheme.typography.labelMedium, color = mutedColor)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Rounded.ArrowBack, "Kembali", tint = textColor) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToForm(null) }, containerColor = primaryColor, shape = CircleShape) {
                Icon(Icons.Rounded.Add, "Tambah", tint = Color.White)
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
                    placeholder = { Text("Cari produk atau kode...", color = mutedColor) },
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

                val hasActiveFilter = kategoriAktif != "Semua"
                Surface(
                    shape = CircleShape,
                    color = if (hasActiveFilter) primaryColor else surfaceColor,
                    border = if (hasActiveFilter) null else BorderStroke(1.dp, borderColor),
                    modifier = Modifier.size(54.dp).clickable { showFilterDialog = true }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.FilterList, "Filter", tint = if (hasActiveFilter) Color.White else textColor)
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (kategoriAktif != "Semua") {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        FilterChipVisual(label = "Jenis: $kategoriAktif", onRemove = { kategoriAktif = "Semua" }, primaryColor)
                    }
                }
                Text("Menampilkan ${filteredRows.size} produk", color = mutedColor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
            }

            // --- DAFTAR PRODUK ---
            if (isLoading) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(6) { ProductCardSkeleton(surfaceColor, borderColor) }
                }
            } else if (filteredRows.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyDataView("Produk tidak ditemukan", "Coba ubah pencarian, filter, atau rentang tanggal.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(paginatedRows) { product ->
                        ProductCard(
                            product = product,
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            primaryColor = primaryColor,
                            successColor = successColor,
                            warningColor = warningColor,
                            dangerColor = dangerColor,
                            infoColor = infoColor,
                            onClick = { onNavigateToForm(product.id) },
                            onEditHarga = { onNavigateToHarga(product.id) },
                            onEditParameter = { onNavigateToParameter(product.id) },
                            onDelete = { onConfirmDelete(product) { triggerRefresh++ } }
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

        // === DIALOG FILTER MODERN ---
        if (showFilterDialog) {
            ModernProductFilterDialog(
                kategoriOpsi = categories,
                initialKategori = kategoriAktif,
                primaryColor = primaryColor,
                surfaceColor = surfaceColor,
                bgColor = bgColor,
                textColor = textColor,
                mutedColor = mutedColor,
                borderColor = borderColor,
                onDismiss = { showFilterDialog = false },
                onReset = {
                    kategoriAktif = "Semua"
                    showFilterDialog = false
                },
                onApply = { kategori ->
                    kategoriAktif = kategori
                    showFilterDialog = false
                }
            )
        }
    }
}

// === KOMPONEN UI REUSABLE & MODERNA ===

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
        Box(Modifier.size(64.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Category, null, tint = Color.Gray, modifier = Modifier.size(32.dp)) }
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = Color.Gray, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ProductCardSkeleton(surfaceColor: Color, borderColor: Color) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).adminShimmerEffect())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.height(16.dp).fillMaxWidth(0.7f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                    Box(Modifier.height(12.dp).fillMaxWidth(0.5f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                }
                Box(Modifier.size(28.dp).clip(CircleShape).adminShimmerEffect())
            }
            HorizontalDivider(color = borderColor)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.height(24.dp).width(60.dp).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                Box(Modifier.height(20.dp).width(80.dp).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.height(14.dp).width(100.dp).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                Box(Modifier.height(14.dp).width(100.dp).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: DataBarisProduk,
    surfaceColor: Color, borderColor: Color, textColor: Color, mutedColor: Color, primaryColor: Color,
    successColor: Color, warningColor: Color, dangerColor: Color, infoColor: Color,
    onClick: () -> Unit,
    onEditHarga: () -> Unit,
    onEditParameter: () -> Unit,
    onDelete: () -> Unit
) {
    val stockColor = when {
        product.stokSaatIni <= 0L -> dangerColor
        product.stokSaatIni <= product.stokMinimum -> warningColor
        else -> successColor
    }

    val itemColor = if (product.jenisProduk.equals("OLAHAN", true)) infoColor else primaryColor
    val iconVector = if (product.jenisProduk.equals("OLAHAN", true)) Icons.Rounded.Category else Icons.Rounded.Inventory

    // State untuk mengontrol kemunculan dropdown menu titik tiga
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header: Icon, Nama, Kode, dan Titik Tiga
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(itemColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(iconVector, null, tint = itemColor, modifier = Modifier.size(24.dp))
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        text = product.namaProduk,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${product.kodeProduk} • ${product.jenisProduk} • ${product.satuan}",
                        color = mutedColor,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Dropdown Menu Titik Tiga (Terpasang Langsung)
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
                            text = { Text("Edit Harga Jual", color = textColor) },
                            onClick = { showMenu = false; onEditHarga() }
                        )

                        if (product.jenisProduk == "DASAR") {
                            DropdownMenuItem(
                                text = { Text("Edit Parameter Produksi", color = textColor) },
                                onClick = { showMenu = false; onEditParameter() }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text("Hapus Produk", color = dangerColor, fontWeight = FontWeight.SemiBold) },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }

            HorizontalDivider(color = borderColor)

            // Body: Status Aktif & Stok
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(6.dp), color = if (product.aktifDijual) successColor.copy(alpha = 0.1f) else mutedColor.copy(alpha = 0.1f)) {
                    Text(
                        text = if (product.aktifDijual) "Dijual (Aktif)" else "Tidak Dijual",
                        color = if (product.aktifDijual) successColor else mutedColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text("Stok ${product.stokSaatIni}", fontWeight = FontWeight.Bold, color = stockColor, style = MaterialTheme.typography.bodyLarge)
            }

            // Footer: Status Info Sub-Data
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val hargaColor = when {
                    product.statusHarga.total <= 0 -> warningColor
                    product.statusHarga.aktif <= 0 -> dangerColor
                    else -> mutedColor
                }
                val paramColor = when {
                    product.jenisProduk != "DASAR" -> mutedColor
                    product.statusParameter.total <= 0 -> warningColor
                    product.statusParameter.aktif <= 0 -> dangerColor
                    else -> mutedColor
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.AttachMoney, null, tint = hargaColor, modifier = Modifier.size(14.dp))
                    Text(
                        text = if (product.statusHarga.total <= 0) "Harga Kosong" else if (product.statusHarga.aktif <= 0) "Tidak Ada Harga Aktif" else "Harga Siap",
                        color = hargaColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Tune, null, tint = paramColor, modifier = Modifier.size(14.dp))
                    Text(
                        text = if (product.jenisProduk != "DASAR") "Bukan Dasar" else if (product.statusParameter.total <= 0) "Parameter Kosong" else if (product.statusParameter.aktif <= 0) "Parameter Mati" else "Parameter Siap",
                        color = paramColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernProductFilterDialog(
    kategoriOpsi: List<String>,
    initialKategori: String,
    primaryColor: Color,
    surfaceColor: Color,
    bgColor: Color,
    textColor: Color,
    mutedColor: Color,
    borderColor: Color,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onApply: (kategori: String) -> Unit
) {
    var draftKategori by remember { mutableStateOf(initialKategori) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = surfaceColor,
        title = { Text("Filter Produk", fontWeight = FontWeight.Bold, color = textColor) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Jenis Kategori Produk", fontWeight = FontWeight.SemiBold, color = textColor, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    kategoriOpsi.forEach { pilihan ->
                        val isSelected = draftKategori == pilihan
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (isSelected) primaryColor else borderColor),
                            modifier = Modifier.weight(1f).clickable { draftKategori = pilihan }
                        ) {
                            Text(
                                text = pilihan.lowercase().replaceFirstChar { it.uppercase() },
                                color = if (isSelected) primaryColor else textColor,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 12.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(draftKategori) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) { Text("Terapkan", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onReset) { Text("Reset", color = mutedColor) } }
    )
}