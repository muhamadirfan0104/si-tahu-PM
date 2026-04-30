package muhamad.irfan.si_tahu.ui.harga

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storefront
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.utama.SiTahuProTheme
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import muhamad.irfan.si_tahu.util.Formatter

class AktivitasDaftarHarga : AktivitasDasar() {

    private val refreshOnResume = MutableStateFlow(0)

    override fun onResume() {
        super.onResume()
        refreshOnResume.value++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        val initialProductId = intent.getStringExtra(EkstraAplikasi.EXTRA_PRODUCT_ID)

        setContent {
            SiTahuProTheme {
                val autoRefreshTrigger by refreshOnResume.collectAsState()
                PriceListScreen(
                    autoRefreshTrigger = autoRefreshTrigger,
                    initialProductId = initialProductId,
                    onNavigateBack = { finish() },
                    onShowMessage = { pesan -> showMessage(pesan) },
                    onShowConfirmation = { title, message, confirmLabel, action ->
                        showConfirmationModal(title, message, confirmLabel, action)
                    },
                    onNavigateToForm = { productId, priceId ->
                        val intent = Intent(this, AktivitasFormHarga::class.java).apply {
                            putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, productId)
                            if (priceId != null) putExtra(EkstraAplikasi.EXTRA_PRICE_ID, priceId)
                        }
                        startActivity(intent)
                    },
                    activityContext = this@AktivitasDaftarHarga
                )
            }
        }
    }
}

// === MODEL DATA ===
private data class OpsiProdukHarga(
    val id: String,
    val name: String,
    val jenisProduk: String,
    val stokSaatIni: Long,
    val satuan: String,
    val aktifDijual: Boolean
)

private data class DataBarisHarga(
    val id: String,
    val kanalHarga: String,
    val hargaSatuan: Long,
    val aktif: Boolean,
    val hargaUtama: Boolean,
    val dibuatPadaMillis: Long
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
private fun PriceListScreen(
    autoRefreshTrigger: Int,
    initialProductId: String?,
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    onShowConfirmation: (String, String, String, () -> Unit) -> Unit,
    onNavigateToForm: (String, String?) -> Unit,
    activityContext: AppCompatActivity
) {
    val firestore = FirebaseFirestore.getInstance()

    // State Data Master
    var products by remember { mutableStateOf<List<OpsiProdukHarga>>(emptyList()) }
    var channels by remember { mutableStateOf<List<DataBarisHarga>>(emptyList()) }
    var selectedProduct by remember { mutableStateOf<OpsiProdukHarga?>(null) }

    // State UI
    var isLoadingProducts by remember { mutableStateOf(true) }
    var isLoadingChannels by remember { mutableStateOf(false) }
    var triggerRefreshChannels by remember { mutableStateOf(0) }
    var showProductPicker by remember { mutableStateOf(false) }
    var productSearchQuery by remember { mutableStateOf("") }
    var productFilter by remember { mutableStateOf("SEMUA") }

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
    val produkTerkunci = !initialProductId.isNullOrBlank()

    // 1. Load Produk Awal
    LaunchedEffect(autoRefreshTrigger) {
        firestore.collection("Produk").get()
            .addOnSuccessListener { snapshot ->
                products = snapshot.documents
                    .filter { it.getBoolean("dihapus") != true }
                    .map { doc ->
                        OpsiProdukHarga(
                            id = doc.id,
                            name = doc.getString("namaProduk").orEmpty(),
                            jenisProduk = doc.getString("jenisProduk").orEmpty(),
                            stokSaatIni = doc.getLong("stokSaatIni") ?: 0L,
                            satuan = doc.getString("satuan").orEmpty(),
                            aktifDijual = doc.getBoolean("aktifDijual") ?: true
                        )
                    }.sortedBy { it.name.lowercase() }

                if (products.isNotEmpty()) {
                    selectedProduct = products.firstOrNull { it.id == initialProductId } ?: products.firstOrNull()
                }
                isLoadingProducts = false
            }
            .addOnFailureListener { e ->
                isLoadingProducts = false
                onShowMessage("Gagal memuat produk: ${e.message}")
            }
    }

    // 2. Load Harga Kanal ketika Produk Berubah atau di-Refresh
    LaunchedEffect(selectedProduct, triggerRefreshChannels, autoRefreshTrigger) {
        val produkTerpilih = selectedProduct
        if (produkTerpilih != null) {
            isLoadingChannels = true
            firestore.collection("Produk").document(produkTerpilih.id).collection("hargaJual").get()
                .addOnSuccessListener { snapshot ->
                    channels = snapshot.documents
                        .filter { it.getBoolean("dihapus") != true }
                        .map { doc ->
                            DataBarisHarga(
                                id = doc.id,
                                kanalHarga = doc.getString("kanalHarga").orEmpty().ifBlank { doc.getString("namaHarga").orEmpty() },
                                hargaSatuan = doc.getLong("hargaSatuan") ?: 0L,
                                aktif = doc.getBoolean("aktif") ?: true,
                                hargaUtama = doc.getBoolean("hargaUtama") ?: false,
                                dibuatPadaMillis = doc.getTimestamp("dibuatPada")?.toDate()?.time ?: 0L
                            )
                        }.sortedByDescending { it.dibuatPadaMillis }
                    isLoadingChannels = false
                }
                .addOnFailureListener { e ->
                    isLoadingChannels = false
                    channels = emptyList()
                    onShowMessage("Gagal memuat harga kanal: ${e.message}")
                }
        } else {
            channels = emptyList()
        }
    }

    // --- FUNGSI LOGIKA FIRESTORE LOKAL ---

    fun setDefaultKasir(productId: String, channel: DataBarisHarga) {
        val hargaRef = firestore.collection("Produk").document(productId).collection("hargaJual")
        hargaRef.get().addOnSuccessListener { snapshot ->
            val docs = snapshot.documents.filter { it.getBoolean("dihapus") != true }
            val batch = firestore.batch()
            val now = Timestamp.now()

            docs.forEach { doc ->
                val isTarget = doc.id == channel.id
                batch.update(doc.reference, mapOf("hargaUtama" to isTarget, "aktif" to if (isTarget) true else (doc.getBoolean("aktif") ?: true), "diperbaruiPada" to now))
            }
            batch.commit().addOnSuccessListener {
                onShowMessage("Harga default kasir berhasil diperbarui.")
                triggerRefreshChannels++
            }.addOnFailureListener { onShowMessage("Gagal mengubah default kasir: ${it.message}") }
        }
    }

    fun togglePrice(productId: String, channel: DataBarisHarga) {
        val nextActive = !channel.aktif
        val hargaRef = firestore.collection("Produk").document(productId).collection("hargaJual")

        if (nextActive) {
            // Mengaktifkan Harga
            hargaRef.get().addOnSuccessListener { snapshot ->
                val activeDocs = snapshot.documents.filter { it.getBoolean("dihapus") != true && it.getBoolean("aktif") != false && it.id != channel.id }
                val shouldBePrimary = activeDocs.isEmpty()
                val now = Timestamp.now()
                val batch = firestore.batch()

                batch.update(hargaRef.document(channel.id), mapOf("aktif" to true, "hargaUtama" to shouldBePrimary, "diperbaruiPada" to now))
                if (shouldBePrimary) {
                    activeDocs.forEach { doc ->
                        if (doc.getBoolean("hargaUtama") == true) batch.update(doc.reference, mapOf("hargaUtama" to false, "diperbaruiPada" to now))
                    }
                }
                batch.commit().addOnSuccessListener { onShowMessage("Harga kanal diaktifkan."); triggerRefreshChannels++ }
            }
        } else {
            // Menonaktifkan Harga (Guard: Minimal 1 aktif)
            hargaRef.document(channel.id).get().addOnSuccessListener { targetDoc ->
                val isPrimary = targetDoc.getBoolean("hargaUtama") == true
                hargaRef.get().addOnSuccessListener { snapshot ->
                    val otherActiveDocs = snapshot.documents.filter { it.getBoolean("dihapus") != true && it.getBoolean("aktif") != false && it.id != channel.id }

                    if (otherActiveDocs.isEmpty()) {
                        onShowMessage("Tidak bisa menonaktifkan harga ini karena harus ada minimal satu harga aktif.")
                        return@addOnSuccessListener
                    }

                    val batch = firestore.batch()
                    val now = Timestamp.now()
                    batch.update(hargaRef.document(channel.id), mapOf("aktif" to false, "hargaUtama" to false, "diperbaruiPada" to now))

                    if (isPrimary) {
                        otherActiveDocs.firstOrNull()?.let { replacement ->
                            batch.update(replacement.reference, mapOf("hargaUtama" to true, "diperbaruiPada" to now))
                        }
                    }
                    batch.commit().addOnSuccessListener { onShowMessage("Harga kanal dinonaktifkan."); triggerRefreshChannels++ }
                }
            }
        }
    }

    fun deletePrice(productId: String, channel: DataBarisHarga) {
        val hargaRef = firestore.collection("Produk").document(productId).collection("hargaJual")
        hargaRef.document(channel.id).get().addOnSuccessListener { targetDoc ->
            val isPrimary = targetDoc.getBoolean("hargaUtama") == true
            val isActive = targetDoc.getBoolean("aktif") ?: true

            hargaRef.get().addOnSuccessListener { snapshot ->
                val otherActiveDocs = snapshot.documents.filter { it.getBoolean("dihapus") != true && it.getBoolean("aktif") != false && it.id != channel.id }

                if (isActive && otherActiveDocs.isEmpty()) {
                    onShowMessage("Tidak bisa menghapus harga ini karena ini harga aktif terakhir.")
                    return@addOnSuccessListener
                }

                val batch = firestore.batch()
                val now = Timestamp.now()
                batch.update(hargaRef.document(channel.id), mapOf("dihapus" to true, "aktif" to false, "hargaUtama" to false, "dihapusPada" to now, "diperbaruiPada" to now))

                if (isPrimary) {
                    otherActiveDocs.firstOrNull()?.let { replacement ->
                        batch.update(replacement.reference, mapOf("hargaUtama" to true, "diperbaruiPada" to now))
                    }
                }
                batch.commit().addOnSuccessListener { onShowMessage("Harga kanal dihapus."); triggerRefreshChannels++ }
            }
        }
    }

    // --- UI STRUCTURE ---
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
                            Text("Harga Kanal", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text("Atur variasi harga penjualan", style = MaterialTheme.typography.labelMedium, color = mutedColor)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Rounded.ArrowBack, "Kembali", tint = textColor) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(visible = selectedProduct != null, enter = fadeIn(), exit = fadeOut()) {
                FloatingActionButton(
                    onClick = { selectedProduct?.let { onNavigateToForm(it.id, null) } },
                    containerColor = primaryColor,
                    shape = CircleShape
                ) {
                    Icon(Icons.Rounded.Add, "Tambah Harga", tint = Color.White)
                }
            }
        },
        containerColor = bgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- KARTU PILIH PRODUK (HEADER) ---
            Card(
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Category, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                        Text(if (produkTerkunci) "Target Produk" else "Pilih Produk", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                    }

                    // Tampilan TextField Pemilih
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val displayValue = if (isLoadingProducts) "Memuat..." else selectedProduct?.name ?: "Belum ada produk..."

                        OutlinedTextField(
                            value = displayValue,
                            onValueChange = {},
                            readOnly = true,
                            enabled = !produkTerkunci,
                            label = { Text("Produk Terpilih") },
                            trailingIcon = {
                                if (!produkTerkunci && products.isNotEmpty()) {
                                    Icon(imageVector = Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = primaryColor)
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, color = textColor),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = borderColor,
                                disabledTextColor = textColor,
                                disabledLabelColor = mutedColor,
                                disabledTrailingIconColor = mutedColor,
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = borderColor,
                                focusedContainerColor = bgColor,
                                unfocusedContainerColor = bgColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Transparant overlay
                        if (!produkTerkunci && !isLoadingProducts && products.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable {
                                        productSearchQuery = ""
                                        productFilter = "SEMUA"
                                        showProductPicker = true
                                    }
                            )
                        }
                    }

                    // Info Keterangan Produk Terpilih
                    selectedProduct?.let { produk ->
                        val infoProduk = "${produk.jenisProduk.ifBlank { "Produk" }} • Stok ${Formatter.ribuan(produk.stokSaatIni)} ${produk.satuan}"
                        Text(text = infoProduk, color = mutedColor, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            // --- DAFTAR HARGA KANAL ---
            if (isLoadingChannels) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(4) { PriceCardSkeleton(surfaceColor, borderColor) }
                }
            } else if (selectedProduct == null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyDataView("Pilih Produk", "Silakan pilih produk terlebih dahulu untuk mengatur harga.")
                }
            } else if (channels.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyDataView("Belum Anda Harga", "Tekan tombol + di bawah untuk menambahkan variasi harga.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(channels) { channel ->
                        PriceCard(
                            channel = channel,
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            successColor = successColor,
                            warningColor = warningColor,
                            dangerColor = dangerColor,
                            onEdit = { selectedProduct?.let { p -> onNavigateToForm(p.id, channel.id) } },
                            onSetDefault = { selectedProduct?.let { p -> setDefaultKasir(p.id, channel) } },
                            onToggleActive = { selectedProduct?.let { p ->
                                onShowConfirmation(
                                    if (channel.aktif) "Nonaktifkan Harga?" else "Aktifkan Harga?",
                                    "Harga kanal ${channel.kanalHarga} akan di${if (channel.aktif) "non" else ""}aktifkan.",
                                    if (channel.aktif) "Nonaktifkan" else "Aktifkan"
                                ) { togglePrice(p.id, channel) }
                            }},
                            onDelete = { selectedProduct?.let { p ->
                                onShowConfirmation("Hapus Harga Kanal?", "Harga ${channel.kanalHarga} akan dihapus. Transaksi lama tidak terpengaruh.", "Hapus") {
                                    deletePrice(p.id, channel)
                                }
                            }}
                        )
                    }
                }
            }
        }


        if (showProductPicker) {
            ProductPickerHargaDialog(
                products = products,
                selectedProduct = selectedProduct,
                searchQuery = productSearchQuery,
                onSearchQueryChange = { productSearchQuery = it },
                productFilter = productFilter,
                onProductFilterChange = { productFilter = it },
                textColor = textColor,
                mutedColor = mutedColor,
                surfaceColor = surfaceColor,
                bgColor = bgColor,
                borderColor = borderColor,
                primaryColor = primaryColor,
                onDismiss = { showProductPicker = false },
                onSelected = { produk ->
                    selectedProduct = produk
                    showProductPicker = false
                    triggerRefreshChannels++
                }
            )
        }
    }
}

// === KOMPONEN UI REUSABLE ===

@Composable
private fun EmptyDataView(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Storefront, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
        }
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = Color.Gray, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PriceCardSkeleton(surfaceColor: Color, borderColor: Color) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).adminShimmerEffect())
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.height(16.dp).fillMaxWidth(0.6f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.height(18.dp).width(50.dp).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                    Box(Modifier.height(18.dp).width(70.dp).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.height(20.dp).width(80.dp).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                Box(Modifier.size(28.dp).clip(CircleShape).adminShimmerEffect())
            }
        }
    }
}

@Composable
private fun ProductPickerHargaDialog(
    products: List<OpsiProdukHarga>,
    selectedProduct: OpsiProdukHarga?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    productFilter: String,
    onProductFilterChange: (String) -> Unit,
    textColor: Color,
    mutedColor: Color,
    surfaceColor: Color,
    bgColor: Color,
    borderColor: Color,
    primaryColor: Color,
    onDismiss: () -> Unit,
    onSelected: (OpsiProdukHarga) -> Unit
) {
    val query = searchQuery.trim().lowercase()
    val filteredProducts = products.filter { produk ->
        val matchSearch = query.isBlank() ||
                produk.name.lowercase().contains(query) ||
                produk.id.lowercase().contains(query) ||
                produk.jenisProduk.lowercase().contains(query)
        val matchFilter = when (productFilter) {
            "DASAR" -> produk.jenisProduk.equals("DASAR", ignoreCase = true)
            "OLAHAN" -> produk.jenisProduk.equals("OLAHAN", ignoreCase = true)
            "AKTIF" -> produk.aktifDijual
            else -> true
        }
        matchSearch && matchFilter
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surfaceColor,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Pilih Produk", color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("Pilih produk untuk mengatur harga kanal", color = mutedColor, style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text("Cari produk") },
                    placeholder = { Text("Nama atau jenis...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = mutedColor) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = borderColor,
                        focusedContainerColor = bgColor,
                        unfocusedContainerColor = bgColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    val filterOptions = listOf("SEMUA" to "Semua", "DASAR" to "Dasar", "OLAHAN" to "Olahan", "AKTIF" to "Aktif")
                    items(filterOptions) { (key, label) ->
                        val isSelected = productFilter == key
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) primaryColor.copy(alpha = 0.14f) else bgColor,
                            border = BorderStroke(1.dp, if (isSelected) primaryColor else borderColor),
                            modifier = Modifier.clickable { onProductFilterChange(key) }
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) primaryColor else textColor,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                if (filteredProducts.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Produk tidak ditemukan", color = mutedColor)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredProducts, key = { it.id }) { produk ->
                            val isSelected = selectedProduct?.id == produk.id
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) primaryColor.copy(alpha = 0.10f) else bgColor,
                                border = BorderStroke(1.dp, if (isSelected) primaryColor else borderColor),
                                modifier = Modifier.fillMaxWidth().clickable { onSelected(produk) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(42.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            produk.name.firstOrNull()?.uppercaseChar()?.toString() ?: "P",
                                            color = primaryColor,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(produk.name.ifBlank { "Tanpa Nama" }, color = textColor, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${produk.jenisProduk.ifBlank { "Produk" }} • Stok ${Formatter.ribuan(produk.stokSaatIni)} ${produk.satuan}",
                                            color = mutedColor,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = primaryColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = mutedColor, fontWeight = FontWeight.Bold) }
        }
    )
}

@Composable
private fun PriceCard(
    channel: DataBarisHarga,
    surfaceColor: Color, borderColor: Color, textColor: Color, mutedColor: Color,
    successColor: Color, warningColor: Color, dangerColor: Color,
    onEdit: () -> Unit,
    onSetDefault: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = if (channel.aktif) successColor else warningColor
    val badgeText = if (channel.aktif) "Aktif" else "Nonaktif"

    // State internal kartu untuk mengontrol tampilan DropdownMenu melayang (anchored)
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AttachMoney, null, tint = statusColor, modifier = Modifier.size(24.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(channel.kanalHarga, fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.1f)) {
                        Text(badgeText, color = statusColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    if (channel.hargaUtama) {
                        Surface(shape = RoundedCornerShape(6.dp), color = successColor.copy(alpha = 0.1f)) {
                            Text("Default Kasir", color = successColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(Formatter.currency(channel.hargaSatuan), fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)

                // Kontainer jangkar Dropdown Menu
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.MoreVert, "Opsi", tint = mutedColor)
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(surfaceColor)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Harga Ini", color = textColor) },
                            onClick = { showMenu = false; onEdit() }
                        )

                        if (!channel.hargaUtama) {
                            DropdownMenuItem(
                                text = { Text("Jadikan Default Kasir", color = successColor) },
                                onClick = { showMenu = false; onSetDefault() }
                            )
                        }

                        val toggleText = if (channel.aktif) "Nonaktifkan Harga" else "Aktifkan Harga"
                        val toggleColor = if (channel.aktif) warningColor else successColor
                        DropdownMenuItem(
                            text = { Text(toggleText, color = toggleColor) },
                            onClick = { showMenu = false; onToggleActive() }
                        )

                        DropdownMenuItem(
                            text = { Text("Hapus Harga", color = dangerColor, fontWeight = FontWeight.SemiBold) },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}