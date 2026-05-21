package muhamad.irfan.si_tahu.ui.stok

import android.content.Intent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.utama.SiTahuProTheme
import muhamad.irfan.si_tahu.util.Formatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AktivitasDetailStok : AktivitasDasar() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        val productId = intent.getStringExtra(AktivitasMonitoringStok.EXTRA_PRODUCT_ID).orEmpty()

        if (productId.isBlank()) {
            showMessage("Produk tidak valid.")
            finish()
            return
        }

        setContent {
            SiTahuProTheme {
                StockDetailScreen(
                    productId = productId,
                    onNavigateBack = { finish() },
                    onShowMessage = { pesan -> showMessage(pesan) },
                    onNavigateToAdjustment = { isExpiredMode ->
                        val intent = Intent(this, AktivitasStockAdjustment::class.java)
                        intent.putExtra(AktivitasStockAdjustment.EXTRA_PRODUCT_ID, productId)
                        if (isExpiredMode) intent.putExtra(AktivitasStockAdjustment.EXTRA_EXPIRED_MODE, true)
                        startActivity(intent)
                    },
                    onCancelAdjustment = { movement, onSuccess ->
                        showInputModal("Batalkan penyesuaian", "Alasan pembatalan", "Batalkan") { alasan ->
                            lifecycleScope.launch {
                                runCatching { RepositoriFirebaseUtama.batalkanPenyesuaianStok(movement.referensiId, alasan, currentUserId()) }
                                    .onSuccess {
                                        showMessage("Penyesuaian stok berhasil dibatalkan")
                                        onSuccess()
                                    }
                                    .onFailure { showMessage(it.message ?: "Gagal membatalkan penyesuaian stok") }
                            }
                        }
                    }
                )
            }
        }
    }
}

// === MODEL DATA ===
private data class ProductDetail(
    val name: String,
    val category: String,
    val unit: String,
    val minStock: Long,
    val totalPhysical: Long,
    val safeStock: Long,
    val nearExpiredStock: Long,
    val edTodayStock: Long,
    val expiredStock: Long,
    val nearestExpiryDate: String,
    val statusText: String
)

private data class StockMovement(
    val id: String,
    val referensiId: String,
    val title: String,
    val dateLabel: String,
    val amountText: String,
    val isCanceled: Boolean,
    val canCancel: Boolean
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

// === TAMPILAN UTAMA (COMPACT & SEPARATED) ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockDetailScreen(
    productId: String,
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    onNavigateToAdjustment: (Boolean) -> Unit,
    onCancelAdjustment: (StockMovement, () -> Unit) -> Unit
) {
    val firestore = FirebaseFirestore.getInstance()
    val coroutineScope = rememberCoroutineScope()

    var productDetail by remember { mutableStateOf<ProductDetail?>(null) }
    var movements by remember { mutableStateOf<List<StockMovement>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var triggerRefresh by remember { mutableStateOf(0) }

    var showDetailDialog by remember { mutableStateOf(false) }
    var detailTitle by remember { mutableStateOf("") }
    var detailBadge by remember { mutableStateOf("ADJUSTMENT") }
    var detailText by remember { mutableStateOf("") }
    var isDetailLoading by remember { mutableStateOf(false) }

    var currentPage by remember { mutableStateOf(1) }
    val pageSize = 10

    // Konfigurasi Warna
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF111827) else Color(0xFFFFFFFF)
    val surfaceColor = if (isDark) Color(0xFF1F2937) else Color(0xFFFFFFFF)
    val textColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF1F2937)
    val mutedColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)
    val separatorColor = if (isDark) Color(0xFF1F2937) else Color(0xFFF3F4F6) // Warna pemisah seksi
    val primaryColor = Color(0xFF3B82F6)

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

    fun tampilkanDetailRiwayat(move: StockMovement) {
        detailTitle = productDetail?.name ?: "Produk"
        detailBadge = when {
            move.title.contains("Pembatalan", true) -> "BATAL"
            move.title.contains("ED", true) -> "BUANG KEDALUWARSA"
            else -> "ADJUSTMENT"
        }
        detailText = ""
        isDetailLoading = true
        showDetailDialog = true
        coroutineScope.launch {
            runCatching { RepositoriFirebaseUtama.buildStockMutationDetailText(move.id) }
                .onSuccess { detail ->
                    detailText = detail.replace("Catatan:", "Alasan:").replace("catatan:", "alasan:")
                    isDetailLoading = false
                }
                .onFailure { throwable ->
                    detailText = throwable.message ?: "Gagal memuat detail riwayat"
                    isDetailLoading = false
                }
        }
    }

    // Muat ulang otomatis saat stok produk ini, batch, atau riwayat penyesuaian berubah.
    DisposableEffect(productId) {
        val registrations = listOf(
            firestore.collection("Produk").document(productId).addSnapshotListener { _, _ -> triggerRefresh++ },
            firestore.collection("BatchStok").whereEqualTo("idProduk", productId).addSnapshotListener { _, _ -> triggerRefresh++ },
            firestore.collection("RiwayatStok").whereEqualTo("idProduk", productId).addSnapshotListener { _, _ -> triggerRefresh++ },
            firestore.collection("PenyesuaianStok").whereEqualTo("idProduk", productId).addSnapshotListener { _, _ -> triggerRefresh++ }
        )
        onDispose { registrations.forEach { it.remove() } }
    }

    // Logika Pengambilan Data (Tetap)
    LaunchedEffect(productId, triggerRefresh) {
        isLoading = productDetail == null
        firestore.collection("Produk").document(productId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onShowMessage("Produk tidak ditemukan.")
                    onNavigateBack()
                    return@addOnSuccessListener
                }

                val unit = doc.getString("satuan").orEmpty().ifBlank { "pcs" }
                val physicalStock = doc.getLong("stokSaatIni") ?: 0L
                val minStock = doc.getLong("stokMinimum") ?: 0L
                val nearWarningDays = (doc.getLong("hariHampirKadaluarsa") ?: 1L).toInt().coerceAtLeast(0)

                firestore.collection("BatchStok").whereEqualTo("idProduk", productId).get()
                    .addOnSuccessListener { batchSnap ->
                        val today = Formatter.currentDateOnly()
                        val warningLimit = run {
                            val cal = Calendar.getInstance()
                            cal.time = Formatter.parseDate("${today}T00:00:00")
                            cal.add(Calendar.DAY_OF_MONTH, nearWarningDays)
                            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                        }

                        var aman = 0L
                        var hampir = 0L
                        var edHariIni = 0L
                        var kadaluarsa = 0L
                        var nearestExpiry = ""

                        batchSnap.documents.filter { (it.getLong("qtySisa") ?: 0L) > 0L }.forEach { bDoc ->
                            val qty = bDoc.getLong("qtySisa") ?: 0L
                            val expiry = bDoc.getString("kunciTanggalKadaluarsa").orEmpty().ifBlank {
                                bDoc.getTimestamp("tanggalKadaluarsa")?.toDate()?.let { tanggal ->
                                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(tanggal)
                                }.orEmpty()
                            }

                            if (expiry.isNotBlank() && (nearestExpiry.isBlank() || expiry < nearestExpiry)) nearestExpiry = expiry

                            when {
                                expiry.isBlank() -> aman += qty
                                expiry < today -> kadaluarsa += qty
                                expiry == today -> edHariIni += qty
                                expiry <= warningLimit -> hampir += qty
                                else -> aman += qty
                            }
                        }

                        val totalBatchQty = aman + hampir + edHariIni + kadaluarsa
                        val oldStockNoBatch = (physicalStock - totalBatchQty).coerceAtLeast(0L)
                        aman += oldStockNoBatch
                        val safeStock = aman + hampir + edHariIni

                        val status = when {
                            safeStock <= 0L && kadaluarsa > 0L -> "Perlu Tindakan"
                            safeStock <= 0L -> "Habis"
                            kadaluarsa > 0L -> "Perlu Tindakan"
                            edHariIni > 0L -> "ED Hari Ini"
                            hampir > 0L -> "Hampir Kedaluwarsa"
                            safeStock <= minStock -> "Menipis"
                            else -> "Aman"
                        }

                        productDetail = ProductDetail(
                            name = doc.getString("namaProduk").orEmpty(),
                            category = doc.getString("jenisProduk").orEmpty(),
                            unit = unit, minStock = minStock, totalPhysical = physicalStock,
                            safeStock = safeStock, nearExpiredStock = hampir, edTodayStock = edHariIni,
                            expiredStock = kadaluarsa, nearestExpiryDate = nearestExpiry, statusText = status
                        )
                    }

                firestore.collection("RiwayatStok").whereEqualTo("idProduk", productId).get()
                    .addOnSuccessListener { mutasiSnap ->
                        movements = mutasiSnap.documents
                            .filter { mDoc ->
                                val jenisMutasi = mDoc.getString("jenisMutasi").orEmpty()
                                jenisMutasi.contains("ADJUSTMENT", ignoreCase = true) &&
                                    !jenisMutasi.contains("PEMBATALAN", ignoreCase = true)
                            }
                            .sortedByDescending { (it.getTimestamp("tanggalMutasi") ?: it.getTimestamp("dibuatPada"))?.toDate()?.time ?: 0L }
                            .map { mDoc ->
                                val jenisMutasi = mDoc.getString("jenisMutasi").orEmpty()
                                val qtyMasuk = mDoc.getLong("qtyMasuk") ?: 0L
                                val qtyKeluar = mDoc.getLong("qtyKeluar") ?: 0L
                                val waktuMutasi = mDoc.getTimestamp("tanggalMutasi") ?: mDoc.getTimestamp("dibuatPada") ?: Timestamp.now()
                                val tanggalLabel = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(waktuMutasi.toDate())

                                val referensiId = mDoc.getString("referensiId").orEmpty()
                                val sumberMutasi = mDoc.getString("sumberMutasi").orEmpty()
                                val isCancellationRow = jenisMutasi.contains("PEMBATALAN", ignoreCase = true)
                                val isCanceled = mDoc.getBoolean("dibatalkan") == true || isCancellationRow

                                val title = when {
                                    isCancellationRow && jenisMutasi.contains("KADALUARSA", ignoreCase = true) -> "Pembatalan Buang Produk Kedaluwarsa"
                                    isCancellationRow -> "Pembatalan Penyesuaian"
                                    jenisMutasi.contains("ADJUSTMENT_KADALUARSA", ignoreCase = true) -> "Buang Produk Kedaluwarsa"
                                    jenisMutasi.contains("ADJUSTMENT_KURANG", ignoreCase = true) -> "Koreksi Pengurangan Stok"
                                    else -> "Penyesuaian Stok"
                                }

                                val amountText = when {
                                    qtyMasuk > 0L -> "+${Formatter.ribuan(qtyMasuk)} $unit"
                                    qtyKeluar > 0L -> "-${Formatter.ribuan(qtyKeluar)} $unit"
                                    else -> "0 $unit"
                                }

                                StockMovement(
                                    id = mDoc.id,
                                    referensiId = referensiId,
                                    title = title,
                                    amountText = amountText,
                                    dateLabel = tanggalLabel,
                                    isCanceled = isCanceled,
                                    canCancel = referensiId.isNotBlank() &&
                                        sumberMutasi.equals("PenyesuaianStok", ignoreCase = true) &&
                                        !isCanceled &&
                                        !isCancellationRow
                                )
                            }
                        isLoading = false
                    }
            }
            .addOnFailureListener {
                isLoading = false
                onShowMessage("Gagal memuat detail produk.")
            }
    }

    val totalPages = maxOf(1, ((movements.size - 1) / pageSize) + 1)
    if (currentPage > totalPages) currentPage = totalPages
    val paginatedMovements = movements.drop((currentPage - 1) * pageSize).take(pageSize)

    Scaffold(
        topBar = {
            TopAppBar( // TopAppBar standar agar lebih ringkas
                title = { Text("Detail Stok", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Rounded.ArrowBack, "Kembali", tint = textColor) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        },
        containerColor = bgColor
    ) { paddingValues ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Color(0xFF3B82F6)) }
        } else if (productDetail != null) {
            val product = productDetail!!

            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

                // --- AREA ATAS: INFO PRODUK & STOK ---
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // Header Produk
                    Spacer(Modifier.height(8.dp))
                    Text(product.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
                    Text(product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis)

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        val statusColor = if(product.expiredStock > 0) Color(0xFFEF4444) else if(product.statusText == "Menipis") Color(0xFFF59E0B) else Color(0xFF10B981)
                        Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                        Spacer(Modifier.width(6.dp))
                        Text(product.statusText, style = MaterialTheme.typography.bodySmall, color = mutedColor)
                    }

                    Spacer(Modifier.height(16.dp))

                    // Stok Rangkuman
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Layak Jual", style = MaterialTheme.typography.labelSmall, color = mutedColor)
                            Text("${Formatter.ribuan(product.safeStock)} ${product.unit}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = textColor)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total Fisik", style = MaterialTheme.typography.labelSmall, color = mutedColor)
                            Text("${Formatter.ribuan(product.totalPhysical)} ${product.unit}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = textColor)
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = borderColor)

                    // Rincian Batch (Font Diperkecil)
                    val amanStock = product.totalPhysical - product.nearExpiredStock - product.edTodayStock - product.expiredStock
                    CompactMetricRow("Aman", "${Formatter.ribuan(amanStock)} ${product.unit}", Color(0xFF10B981), mutedColor)
                    CompactMetricRow("Hampir Kedaluwarsa", "${Formatter.ribuan(product.nearExpiredStock)} ${product.unit}", Color(0xFFF59E0B), mutedColor)
                    CompactMetricRow("ED Hari Ini", "${Formatter.ribuan(product.edTodayStock)} ${product.unit}", Color(0xFFF59E0B), mutedColor)
                    CompactMetricRow("Kedaluwarsa", "${Formatter.ribuan(product.expiredStock)} ${product.unit}", Color(0xFFEF4444), mutedColor)

                    if (product.nearestExpiryDate.isNotBlank()) {
                        Text("ED terdekat: ${Formatter.readableDate(product.nearestExpiryDate)}", color = mutedColor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }

                    Spacer(Modifier.height(16.dp))

                    // Tombol Aksi (Tinggi diperkecil, Teks diperkecil)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onNavigateToAdjustment(false) },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            border = BorderStroke(1.dp, Color(0xFF3B82F6))
                        ) {
                            Text("Penyesuaian", color = Color(0xFF3B82F6), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }

                        if (product.expiredStock > 0) {
                            Button(
                                onClick = { onNavigateToAdjustment(true) },
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                            ) {
                                Text("Buang Produk Kedaluwarsa", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // --- PEMISAH VISUAL TEGAS ---
                Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(separatorColor))

                // --- AREA BAWAH: RIWAYAT STOK ---
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Text(
                        text = "Riwayat Penyesuaian",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                    )

                    if (movements.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Belum ada riwayat penyesuaian.", color = mutedColor, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(paginatedMovements) { move ->
                                CompactMovementRow(
                                    move = move,
                                    textColor = textColor,
                                    mutedColor = mutedColor,
                                    borderColor = borderColor,
                                    onClick = { tampilkanDetailRiwayat(move) },
                                    onCancel = if (move.canCancel) {
                                        { onCancelAdjustment(move) { triggerRefresh++ } }
                                    } else null
                                )
                            }

                            // Navigasi Paginasi Diperkecil
                            if (totalPages > 1) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = { if (currentPage > 1) currentPage-- }, enabled = currentPage > 1, contentPadding = PaddingValues(0.dp)) {
                                            Text("Prev", style = MaterialTheme.typography.labelMedium, color = if(currentPage > 1) Color(0xFF3B82F6) else mutedColor)
                                        }
                                        Text("$currentPage / $totalPages", color = mutedColor, style = MaterialTheme.typography.labelSmall)
                                        TextButton(onClick = { if (currentPage < totalPages) currentPage++ }, enabled = currentPage < totalPages, contentPadding = PaddingValues(0.dp)) {
                                            Text("Next", style = MaterialTheme.typography.labelMedium, color = if(currentPage < totalPages) Color(0xFF3B82F6) else mutedColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// === KOMPONEN UI BANTUAN (COMPACT) ===

@Composable
private fun CompactMetricRow(label: String, value: String, valueColor: Color, labelColor: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = labelColor)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun CompactMovementRow(
    move: StockMovement,
    textColor: Color,
    mutedColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val isPositive = move.amountText.startsWith("+")
    val amountColor = if (isPositive) Color(0xFF10B981) else if (move.amountText.startsWith("-")) Color(0xFFEF4444) else textColor
    val cancelColor = Color(0xFFEF4444)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(move.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (move.isCanceled) {
                        Surface(shape = RoundedCornerShape(50), color = cancelColor.copy(alpha = 0.10f), border = BorderStroke(1.dp, cancelColor.copy(alpha = 0.22f))) {
                            Text("Batal", color = cancelColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(move.dateLabel, style = MaterialTheme.typography.labelSmall, color = mutedColor)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(move.amountText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = amountColor)
                if (onCancel != null) {
                    TextButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(28.dp)) {
                        Text("Batalkan", color = cancelColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(top = 10.dp), thickness = 0.5.dp, color = borderColor)
    }
}

// === KOMPONEN DIALOG DETAIL PRO ===

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
    val badgeColor = if (badge.contains("BUANG", true)) Color.Red else primaryColor

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