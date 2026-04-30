package muhamad.irfan.si_tahu.ui.parameter

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable // IMPORT FIXED HERE
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
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.PrecisionManufacturing
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.text.style.TextAlign // IMPORT FIXED HERE
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.utama.SiTahuProTheme
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import muhamad.irfan.si_tahu.util.Formatter

class AktivitasDaftarParameter : AktivitasDasar() {

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
                ParameterListScreen(
                    autoRefreshTrigger = autoRefreshTrigger,
                    initialProductId = initialProductId,
                    onNavigateBack = { finish() },
                    onShowMessage = { pesan -> showMessage(pesan) },
                    onShowConfirmation = { title, message, confirmLabel, action ->
                        showConfirmationModal(title, message, confirmLabel, action)
                    },
                    onNavigateToForm = { productId, parameterId ->
                        val intent = Intent(this, AktivitasFormParameter::class.java).apply {
                            putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, productId)
                            if (parameterId != null) putExtra(EkstraAplikasi.EXTRA_PARAMETER_ID, parameterId)
                        }
                        startActivity(intent)
                    },
                    activityContext = this@AktivitasDaftarParameter
                )
            }
        }
    }
}

// === MODEL DATA BANTUAN ===
private data class OpsiProdukParameter(
    val id: String,
    val namaProduk: String,
    val jenisProduk: String,
    val stokSaatIni: Long,
    val satuan: String,
    val aktifDijual: Boolean
)

private data class DataBarisParameter(
    val id: String,
    val idProduk: String,
    val namaProduk: String,
    val hasilPerProduksi: Long,
    val satuanHasil: String,
    val aktif: Boolean,
    val catatan: String,
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
private fun ParameterListScreen(
    autoRefreshTrigger: Int,
    initialProductId: String?,
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    onShowConfirmation: (String, String, String, () -> Unit) -> Unit,
    onNavigateToForm: (String, String?) -> Unit,
    activityContext: AppCompatActivity
) {
    val firestore = FirebaseFirestore.getInstance()

    // State Data
    var products by remember { mutableStateOf<List<OpsiProdukParameter>>(emptyList()) }
    var parameters by remember { mutableStateOf<List<DataBarisParameter>>(emptyList()) }
    var selectedProduct by remember { mutableStateOf<OpsiProdukParameter?>(null) }

    // State UI
    var isLoadingProducts by remember { mutableStateOf(true) }
    var isLoadingParameters by remember { mutableStateOf(false) }
    var triggerRefreshParameters by remember { mutableStateOf(0) }
    var showProductPicker by remember { mutableStateOf(false) }
    var productSearchQuery by remember { mutableStateOf("") }

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
        firestore.collection("Produk").whereEqualTo("jenisProduk", "DASAR").get()
            .addOnSuccessListener { snapshot ->
                products = snapshot.documents
                    .filter { it.getBoolean("dihapus") != true }
                    .map { doc ->
                        OpsiProdukParameter(
                            id = doc.id,
                            namaProduk = doc.getString("namaProduk").orEmpty(),
                            jenisProduk = doc.getString("jenisProduk").orEmpty().ifBlank { "DASAR" },
                            stokSaatIni = doc.getLong("stokSaatIni") ?: 0L,
                            satuan = doc.getString("satuan").orEmpty(),
                            aktifDijual = doc.getBoolean("aktifDijual") ?: true
                        )
                    }.sortedBy { it.namaProduk.lowercase() }

                if (products.isNotEmpty()) {
                    selectedProduct = products.firstOrNull { it.id == initialProductId } ?: products.firstOrNull()
                }
                isLoadingProducts = false
            }
            .addOnFailureListener { e ->
                isLoadingProducts = false
                onShowMessage("Gagal memuat produk dasar: ${e.message}")
            }
    }

    // 2. Load Parameter ketika Produk Berubah atau di-Refresh
    LaunchedEffect(selectedProduct, triggerRefreshParameters, autoRefreshTrigger) {
        val produkTerpilih = selectedProduct
        if (produkTerpilih != null) {
            isLoadingParameters = true
            firestore.collection("Produk").document(produkTerpilih.id).collection("parameterProduksi").get()
                .addOnSuccessListener { snapshot ->
                    parameters = snapshot.documents
                        .filter { it.getBoolean("dihapus") != true }
                        .map { doc ->
                            DataBarisParameter(
                                id = doc.id,
                                idProduk = doc.getString("idProduk").orEmpty().ifBlank { produkTerpilih.id },
                                namaProduk = doc.getString("namaProduk").orEmpty(),
                                hasilPerProduksi = doc.getLong("hasilPerProduksi") ?: 0L,
                                satuanHasil = doc.getString("satuanHasil").orEmpty(),
                                aktif = doc.getBoolean("aktif") ?: false,
                                catatan = doc.getString("catatan").orEmpty(),
                                dibuatPadaMillis = doc.getTimestamp("dibuatPada")?.toDate()?.time ?: 0L
                            )
                        }.sortedByDescending { it.dibuatPadaMillis }
                    isLoadingParameters = false
                }
                .addOnFailureListener { e ->
                    isLoadingParameters = false
                    parameters = emptyList()
                    onShowMessage("Gagal memuat parameter: ${e.message}")
                }
        } else {
            parameters = emptyList()
        }
    }

    // --- FUNGSI LOGIKA FIRESTORE ---

    fun toggleParameter(parameter: DataBarisParameter) {
        val nextActive = !parameter.aktif
        val paramRef = firestore.collection("Produk").document(parameter.idProduk).collection("parameterProduksi")

        if (nextActive) {
            paramRef.get().addOnSuccessListener { snapshot ->
                val docs = snapshot.documents.filter { it.getBoolean("dihapus") != true }
                val batch = firestore.batch()
                val now = Timestamp.now()

                docs.forEach { doc ->
                    batch.update(doc.reference, mapOf("aktif" to (doc.id == parameter.id), "diperbaruiPada" to now))
                }
                batch.commit().addOnSuccessListener { onShowMessage("Parameter aktif diperbarui."); triggerRefreshParameters++ }
            }
        } else {
            paramRef.document(parameter.id).update(mapOf("aktif" to false, "diperbaruiPada" to Timestamp.now()))
                .addOnSuccessListener { onShowMessage("Parameter dinonaktifkan."); triggerRefreshParameters++ }
        }
    }

    fun deleteParameter(parameter: DataBarisParameter) {
        val paramRef = firestore.collection("Produk").document(parameter.idProduk).collection("parameterProduksi")

        paramRef.document(parameter.id).get().addOnSuccessListener { targetDoc ->
            val isActive = targetDoc.getBoolean("aktif") == true
            paramRef.get().addOnSuccessListener { snapshot ->
                val otherDocs = snapshot.documents.filter { it.getBoolean("dihapus") != true && it.id != parameter.id }
                val replacementDoc = otherDocs.firstOrNull()
                val batch = firestore.batch()
                val now = Timestamp.now()

                batch.update(paramRef.document(parameter.id), mapOf("dihapus" to true, "aktif" to false, "dihapusPada" to now, "diperbaruiPada" to now))

                if (isActive && replacementDoc != null) {
                    batch.update(replacementDoc.reference, mapOf("aktif" to true, "diperbaruiPada" to now))
                }

                batch.commit().addOnSuccessListener { onShowMessage("Parameter dihapus."); triggerRefreshParameters++ }
            }
        }
    }

    // --- STRUKTUR UI ---
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
                            Text("Parameter Produksi", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text("Standar hasil per masak", style = MaterialTheme.typography.labelMedium, color = mutedColor)
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
                    Icon(Icons.Rounded.Add, "Tambah Parameter", tint = Color.White)
                }
            }
        },
        containerColor = bgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- KARTU PILIH PRODUK DASAR (HEADER PRO) ---
            Card(
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Category, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                        Text(if (produkTerkunci) "Target Produk" else "Pilih Produk Dasar", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        val displayValue = if (isLoadingProducts) "Memuat..." else selectedProduct?.namaProduk ?: "Belum ada produk..."

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

                        if (!produkTerkunci && !isLoadingProducts && products.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable {
                                        productSearchQuery = ""
                                        showProductPicker = true
                                    }
                            )
                        }
                    }

                    selectedProduct?.let { produk ->
                        val infoProduk = "${produk.jenisProduk} • Stok ${Formatter.ribuan(produk.stokSaatIni)} ${produk.satuan}"
                        Text(text = infoProduk, color = mutedColor, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            // --- DAFTAR PARAMETER ---
            if (isLoadingParameters) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(4) { ParameterCardSkeleton(surfaceColor, borderColor) }
                }
            } else if (selectedProduct == null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyDataView("Pilih Produk", "Silakan pilih produk dasar terlebih dahulu untuk mengatur standar produksi.")
                }
            } else if (parameters.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyDataView("Belum Ada Parameter", "Tekan tombol + di bawah untuk menambahkan spesifikasi masak baru.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(parameters) { param ->
                        ParameterCard(
                            parameter = param,
                            productName = selectedProduct?.namaProduk ?: "Produk",
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            successColor = successColor,
                            warningColor = warningColor,
                            dangerColor = dangerColor,
                            onClick = { selectedProduct?.let { p -> onNavigateToForm(p.id, param.id) } },
                            onToggleActive = { toggleParameter(param) },
                            onDelete = {
                                onShowConfirmation("Hapus Parameter?", "Parameter untuk ${param.namaProduk} akan dihapus permanen.", "Hapus") {
                                    deleteParameter(param)
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showProductPicker) {
            DialogPilihProdukParameter(
                produk = products,
                selectedId = selectedProduct?.id,
                query = productSearchQuery,
                onQueryChange = { productSearchQuery = it },
                surfaceColor = surfaceColor,
                bgColor = bgColor,
                primaryColor = primaryColor,
                textColor = textColor,
                mutedColor = mutedColor,
                borderColor = borderColor,
                onDismiss = { showProductPicker = false },
                onSelected = { produk ->
                    selectedProduct = produk
                    showProductPicker = false
                    triggerRefreshParameters++
                }
            )
        }
    }
}

// === KOMPONEN UI REUSABLE & MODERNA ===

@Composable
private fun EmptyDataView(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PrecisionManufacturing, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
        }
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = Color.Gray, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ParameterCardSkeleton(surfaceColor: Color, borderColor: Color) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).adminShimmerEffect())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.height(16.dp).fillMaxWidth(0.5f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                    Box(Modifier.height(12.dp).fillMaxWidth(0.3f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                }
                Box(Modifier.size(28.dp).clip(CircleShape).adminShimmerEffect())
            }
            HorizontalDivider(color = borderColor)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.height(20.dp).width(60.dp).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                Box(Modifier.height(14.dp).width(80.dp).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
            }
        }
    }
}

@Composable
private fun DialogPilihProdukParameter(
    produk: List<OpsiProdukParameter>,
    selectedId: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    surfaceColor: Color,
    bgColor: Color,
    primaryColor: Color,
    textColor: Color,
    mutedColor: Color,
    borderColor: Color,
    onDismiss: () -> Unit,
    onSelected: (OpsiProdukParameter) -> Unit
) {
    val filteredProduk = produk.filter { item ->
        val keyword = query.trim().lowercase()
        keyword.isBlank() ||
                item.namaProduk.lowercase().contains(keyword) ||
                item.id.lowercase().contains(keyword) ||
                item.jenisProduk.lowercase().contains(keyword)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surfaceColor,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Pilih Produk Dasar", color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("Produk kategori DASAR untuk parameter produksi", color = mutedColor, style = MaterialTheme.typography.labelMedium)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("Cari nama produk / ID") },
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
                    val filterOptions = listOf("SEMUA" to "Semua", "AKTIF" to "Aktif")
                    items(filterOptions) { (key, label) ->
                        val isSelected = key == "SEMUA"
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) primaryColor.copy(alpha = 0.14f) else bgColor,
                            border = BorderStroke(1.dp, if (isSelected) primaryColor else borderColor),
                            modifier = Modifier.clickable { }
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) primaryColor else textColor,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center, // ALIGN FIXED HERE
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                if (filteredProduk.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Produk tidak ditemukan", color = mutedColor)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredProduk) { item ->
                            val dipilih = item.id == selectedId
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (dipilih) primaryColor.copy(alpha = 0.12f) else bgColor,
                                border = BorderStroke(1.dp, if (dipilih) primaryColor else borderColor),
                                modifier = Modifier.fillMaxWidth().clickable { onSelected(item) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(42.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            item.namaProduk.firstOrNull()?.uppercaseChar()?.toString() ?: "P",
                                            color = primaryColor,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.namaProduk, color = textColor, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${item.jenisProduk} • Stok ${Formatter.ribuan(item.stokSaatIni)} ${item.satuan.ifBlank { "pcs" }}",
                                            color = mutedColor,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                    if (dipilih) {
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
private fun ParameterCard(
    parameter: DataBarisParameter,
    productName: String,
    surfaceColor: Color, borderColor: Color, textColor: Color, mutedColor: Color,
    successColor: Color, warningColor: Color, dangerColor: Color,
    onClick: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = if (parameter.aktif) successColor else warningColor
    val badgeText = if (parameter.aktif) "Aktif" else "Nonaktif"
    val displayTitle = parameter.namaProduk.ifBlank { productName }

    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Tune, null, tint = statusColor, modifier = Modifier.size(24.dp))
                }

                Column(Modifier.weight(1f)) {
                    Text(displayTitle, fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.bodyLarge)
                    Text("Hasil: ${Formatter.ribuan(parameter.hasilPerProduksi)} ${parameter.satuanHasil}", color = mutedColor, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 2.dp))
                }

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
                            text = { Text("Edit Data Parameter", color = textColor) },
                            onClick = { showMenu = false; onClick() }
                        )

                        val toggleText = if (parameter.aktif) "Nonaktifkan Parameter" else "Jadikan Aktif (Utama)"
                        val toggleColor = if (parameter.aktif) warningColor else successColor
                        DropdownMenuItem(
                            text = { Text(toggleText, color = toggleColor) },
                            onClick = { showMenu = false; onToggleActive() }
                        )

                        DropdownMenuItem(
                            text = { Text("Hapus Parameter", color = dangerColor, fontWeight = FontWeight.SemiBold) },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }

            HorizontalDivider(color = borderColor)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.1f)) {
                    Text(badgeText, color = statusColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }

                if (parameter.catatan.isNotBlank()) {
                    Text("📝 Ada Catatan Alasan", color = mutedColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                } else {
                    Text("ID: ${parameter.id.takeLast(6).uppercase()}", color = mutedColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}