package muhamad.irfan.si_tahu.ui.stok

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search // IMPORT AMAN
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.utama.SiTahuProTheme
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.InputAngka
import muhamad.irfan.si_tahu.util.PembantuPilihTanggalWaktu

class AktivitasStockAdjustment : AktivitasDasar() {

    // Konfigurasi Intent
    private var modeKadaluarsa = false
    private var intentProductId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        modeKadaluarsa = intent.getBooleanExtra(EXTRA_EXPIRED_MODE, false)
        intentProductId = intent.getStringExtra(EXTRA_PRODUCT_ID)

        setContent {
            SiTahuProTheme {
                StockAdjustmentScreen(
                    modeKadaluarsa = modeKadaluarsa,
                    initialProductId = intentProductId,
                    onNavigateBack = { finish() },
                    onShowMessage = { pesan -> showMessage(pesan) },
                    getCurrentUserId = { currentUserId() },
                    onSaveSuccess = {
                        // REVISI: Langsung tandai berhasil dan tutup halaman, tanpa memunculkan receiptModal
                        setResult(android.app.Activity.RESULT_OK, Intent().putExtra(EXTRA_STOCK_UPDATED, true))
                        showMessage(if (modeKadaluarsa) "Stok kadaluarsa berhasil dibuang" else "Penyesuaian stok berhasil disimpan")
                        finish()
                    },
                    activityContext = this@AktivitasStockAdjustment
                )
            }
        }
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "extra_product_id"
        const val EXTRA_STOCK_UPDATED = "extra_stock_updated"
        const val EXTRA_EXPIRED_MODE = "extra_expired_mode"
    }
}

// === KOMPONEN UTAMA UI ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockAdjustmentScreen(
    modeKadaluarsa: Boolean,
    initialProductId: String?,
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    getCurrentUserId: () -> String,
    onSaveSuccess: () -> Unit, // Tipe diganti tanpa membawa String rincian lagi
    activityContext: AppCompatActivity
) {
    // State Data Master
    var products by remember { mutableStateOf<List<Produk>>(emptyList()) }
    var selectedProduk by remember { mutableStateOf<Produk?>(null) }
    var stokKadaluarsaTerpilih by remember { mutableStateOf(0L) }
    var showProductPicker by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // Status Terkunci
    val isProductLocked = !initialProductId.isNullOrBlank()

    // State Form
    var qtyInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf(if (modeKadaluarsa) "Dibuang karena kadaluarsa / tidak layak jual" else "") }
    var tanggal by remember { mutableStateOf(Formatter.currentDateOnly()) }
    var waktu by remember { mutableStateOf(Formatter.currentTimeOnly()) }
    var showDateTime by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Tema Warna Dinamis Pro
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF111827) else Color(0xFFF3F4F6)
    val surfaceColor = if (isDark) Color(0xFF1F2937) else Color(0xFFFFFFFF)
    val primaryColor = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
    val dangerColor = if (isDark) Color(0xFFEF4444) else Color(0xFFDC2626)
    val textColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF111827)
    val mutedColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)

    val activeColor = if (modeKadaluarsa) dangerColor else primaryColor
    val titleText = if (modeKadaluarsa) "Buang Kadaluarsa" else "Opname (Adjustment)"
    val subtitleText = if (modeKadaluarsa) "Tindak lanjuti stok tak layak jual" else "Koreksi selisih stok fisik & sistem"
    val qtyLabel = if (modeKadaluarsa) "Jumlah Dibuang" else "Jumlah Dikurangi"

    // Load Data
    val fetchExpiredStock = { productId: String ->
        coroutineScope.launch {
            runCatching { RepositoriFirebaseUtama.muatTotalStokKadaluarsa(productId) }
                .onSuccess { expiredQty ->
                    stokKadaluarsaTerpilih = expiredQty
                    if (modeKadaluarsa && expiredQty > 0L) {
                        qtyInput = InputAngka.formatInput(expiredQty.toString())
                    }
                }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { RepositoriFirebaseUtama.muatSemuaProduk() }
            .onSuccess { result ->
                products = result.sortedBy { it.name.lowercase() }

                selectedProduk = when {
                    isProductLocked && result.any { it.id == initialProductId } -> result.find { it.id == initialProductId }
                    else -> result.firstOrNull()
                }

                selectedProduk?.id?.let { fetchExpiredStock(it) }
                isLoading = false
            }
            .onFailure {
                isLoading = false
                onShowMessage(it.message ?: "Gagal memuat produk")
            }
    }

    // Fungsi Validasi & Simpan
    val saveAdjustment = {
        val produkTerpilih = selectedProduk
        if (produkTerpilih == null) {
            onShowMessage("Produk belum dipilih.")
        } else {
            val qty = InputAngka.parseLong(qtyInput)
            if (tanggal.isBlank() || waktu.isBlank()) onShowMessage("Tanggal & waktu wajib diisi.")
            else if (qty <= 0L) onShowMessage("Jumlah stok harus lebih dari 0.")
            else if (qty > Int.MAX_VALUE) onShowMessage("Jumlah terlalu besar.")
            else if (noteInput.isBlank()) onShowMessage("Catatan/alasan wajib diisi.")
            else if (modeKadaluarsa && qty > stokKadaluarsaTerpilih) onShowMessage("Jumlah melebihi stok kadaluarsa (${Formatter.ribuan(stokKadaluarsaTerpilih)} ${produkTerpilih.unit}).")
            else if (!modeKadaluarsa && qty > produkTerpilih.stock) onShowMessage("Jumlah adjustment melebihi stok saat ini (${Formatter.ribuan(produkTerpilih.stock.toLong())} ${produkTerpilih.unit}).")
            else {
                isSaving = true
                coroutineScope.launch {
                    runCatching {
                        if (modeKadaluarsa) {
                            RepositoriFirebaseUtama.simpanAdjustmentKadaluarsa(
                                dateOnly = tanggal, timeOnly = waktu, productId = produkTerpilih.id,
                                qty = qty.toInt(), note = noteInput, userAuthId = getCurrentUserId()
                            )
                        } else {
                            RepositoriFirebaseUtama.simpanAdjustment(
                                dateOnly = tanggal, timeOnly = waktu, productId = produkTerpilih.id,
                                type = "subtract", qty = qty.toInt(), note = noteInput, userAuthId = getCurrentUserId()
                            )
                        }
                    }.onSuccess {
                        isSaving = false
                        onSaveSuccess() // Panggil callback langsung untuk exit screen
                    }.onFailure {
                        isSaving = false
                        onShowMessage(it.message ?: "Gagal menyimpan adjustment")
                    }
                }
            }
        }
    }

    if (showProductPicker) {
        StockAdjustmentProductPickerDialog(
            products = products,
            selectedId = selectedProduk?.id,
            activeColor = activeColor,
            surfaceColor = surfaceColor,
            bgColor = bgColor,
            textColor = textColor,
            mutedColor = mutedColor,
            borderColor = borderColor,
            onDismiss = { showProductPicker = false },
            onSelected = { produk ->
                selectedProduk = produk
                qtyInput = ""
                fetchExpiredStock(produk.id)
                showProductPicker = false
            }
        )
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
                            Text(titleText, fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text(subtitleText, style = MaterialTheme.typography.labelMedium, color = mutedColor)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) { Icon(Icons.Rounded.ArrowBack, "Kembali", tint = textColor) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        bottomBar = {
            Surface(
                color = surfaceColor,
                shadowElevation = 24.dp,
                border = if (isDark) BorderStroke(1.dp, borderColor) else null,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp)) {
                    val isValid = selectedProduk != null && InputAngka.parseLong(qtyInput) > 0L && noteInput.isNotBlank()
                    Button(
                        onClick = { saveAdjustment() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isSaving && isValid,
                        colors = ButtonDefaults.buttonColors(containerColor = activeColor, disabledContainerColor = activeColor.copy(alpha = 0.5f))
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Memproses...", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        } else {
                            Icon(if (modeKadaluarsa) Icons.Rounded.DeleteForever else Icons.Rounded.SyncAlt, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(if (modeKadaluarsa) "Buang Stok Kadaluarsa" else "Simpan Penyesuaian", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        },
        containerColor = bgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // === 1. KARTU TARGET PRODUK ===
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(activeColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Category, null, tint = activeColor, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(if (isProductLocked) "Target Produk" else "Pilih Produk", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                            Text("Data stok diambil langsung dari server", color = mutedColor, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        val displayValue = if (isLoading) "Memuat produk..." else selectedProduk?.name ?: "Pilih produk dari daftar"

                        OutlinedTextField(
                            value = displayValue,
                            onValueChange = {},
                            readOnly = true,
                            enabled = !isProductLocked,
                            label = { Text("Produk Terpilih") },
                            trailingIcon = {
                                if (!isProductLocked) {
                                    Icon(imageVector = Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = activeColor)
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, color = textColor),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = borderColor,
                                disabledTextColor = textColor,
                                disabledLabelColor = mutedColor,
                                disabledTrailingIconColor = mutedColor,
                                focusedBorderColor = activeColor,
                                unfocusedBorderColor = borderColor,
                                focusedContainerColor = bgColor,
                                unfocusedContainerColor = bgColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (!isProductLocked && !isLoading) {
                            Box(modifier = Modifier.matchParentSize().clickable { showProductPicker = true })
                        }
                    }

                    selectedProduk?.let { produkTerpilih ->
                        Surface(shape = RoundedCornerShape(16.dp), color = activeColor.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Rounded.Info, null, tint = activeColor, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                                Text(
                                    text = if (modeKadaluarsa) "Sistem mencatat ada ${Formatter.ribuan(stokKadaluarsaTerpilih)} ${produkTerpilih.unit} stok kadaluarsa. Tindakan ini akan membuangnya secara permanen."
                                    else "Tersedia fisik ${Formatter.ribuan(produkTerpilih.stock.toLong())} ${produkTerpilih.unit}. Penyesuaian mengurangi stok fisik dan mengambil dari batch layak jual (FEFO).",
                                    color = activeColor,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2
                                )
                            }
                        }
                    }
                }
            }

            // === 2. KARTU DETAIL PENYESUAIAN ===
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(activeColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Edit, null, tint = activeColor, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Detail Tindakan", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                            Text("Isi jumlah dan alasan perubahan", color = mutedColor, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (!modeKadaluarsa) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = activeColor.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, activeColor.copy(alpha = 0.20f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.SyncAlt, contentDescription = null, tint = activeColor, modifier = Modifier.size(20.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Mode Kurangi Stok", color = activeColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Hanya untuk selisih fisik, rusak, hilang, atau koreksi minus.", color = activeColor.copy(alpha=0.8f), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = qtyInput,
                        onValueChange = { qtyInput = InputAngka.formatInput(it) },
                        label = { Text(qtyLabel) },
                        placeholder = { Text("0") },
                        suffix = { Text(selectedProduk?.unit ?: "-", color = mutedColor, fontWeight = FontWeight.Medium) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(14.dp),
                        textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Start, color = textColor),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeColor,
                            unfocusedBorderColor = borderColor,
                            focusedContainerColor = bgColor,
                            unfocusedContainerColor = bgColor,
                            focusedLabelColor = activeColor,
                            unfocusedLabelColor = mutedColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text("Alasan / Catatan (Wajib)") },
                        placeholder = { Text("Misal: Tahu hancur saat dipindahkan...") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(14.dp),
                        textStyle = LocalTextStyle.current.copy(color = textColor),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeColor,
                            unfocusedBorderColor = borderColor,
                            focusedContainerColor = bgColor,
                            unfocusedContainerColor = bgColor,
                            focusedLabelColor = activeColor,
                            unfocusedLabelColor = mutedColor
                        ),
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    )
                }
            }

            // === 3. PENGATURAN TANGGAL ===
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Pengaturan Waktu", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        ToggleSection(
                            title = "Waktu Pencatatan (${Formatter.readableShortDate(tanggal)})",
                            icon = Icons.Rounded.DateRange,
                            isExpanded = showDateTime,
                            onToggle = { showDateTime = !showDateTime },
                            textColor = textColor,
                            mutedColor = mutedColor
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = tanggal,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Tanggal") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).clickable {
                                        PembantuPilihTanggalWaktu.showDatePicker(activityContext, tanggal) { tanggal = it }
                                    },
                                    enabled = false,
                                    textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Medium, color = textColor),
                                    colors = OutlinedTextFieldDefaults.colors(disabledTextColor = textColor, disabledBorderColor = borderColor, disabledLabelColor = mutedColor)
                                )
                                OutlinedTextField(
                                    value = waktu,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Jam") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).clickable {
                                        val iso = Formatter.isoDate(tanggal, "$waktu:00")
                                        PembantuPilihTanggalWaktu.showTimePicker(activityContext, iso) { waktu = it }
                                    },
                                    enabled = false,
                                    textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Medium, color = textColor),
                                    colors = OutlinedTextFieldDefaults.colors(disabledTextColor = textColor, disabledBorderColor = borderColor, disabledLabelColor = mutedColor)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

// === KOMPONEN DIALOG PICKER ===
@Composable
private fun StockAdjustmentProductPickerDialog(
    products: List<Produk>,
    selectedId: String?,
    activeColor: Color,
    surfaceColor: Color,
    bgColor: Color,
    textColor: Color,
    mutedColor: Color,
    borderColor: Color,
    onDismiss: () -> Unit,
    onSelected: (Produk) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var kategori by remember { mutableStateOf("Semua") }
    val kategoriOptions = listOf("Semua", "DASAR", "OLAHAN")

    val filtered = products.filter { produk ->
        val cocokQuery = query.isBlank() || produk.name.contains(query, ignoreCase = true) || produk.code.contains(query, ignoreCase = true)
        val cocokKategori = kategori == "Semua" || produk.category.equals(kategori, ignoreCase = true)
        cocokQuery && cocokKategori
    }.sortedBy { it.name.lowercase() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Pilih Produk", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                Text("Daftar stok tersedia di sistem", color = mutedColor, style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Cari nama / kode...", color = mutedColor) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint=mutedColor) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(100),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = activeColor, unfocusedBorderColor = Color.Transparent, focusedContainerColor = bgColor, unfocusedContainerColor = bgColor),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    kategoriOptions.forEach { opsi ->
                        val selected = kategori == opsi
                        Surface(
                            modifier = Modifier.weight(1f).clickable { kategori = opsi },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) activeColor.copy(alpha = 0.15f) else bgColor,
                            border = BorderStroke(1.dp, if (selected) activeColor else borderColor)
                        ) {
                            Text(
                                opsi.lowercase().replaceFirstChar { it.uppercase() },
                                color = if (selected) activeColor else textColor,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp), contentAlignment = Alignment.Center) {
                        Text("Produk tidak ditemukan", color = mutedColor)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        filtered.forEach { produk ->
                            val selected = selectedId == produk.id
                            val stokLayak = produk.safeStock + produk.nearExpiredStock + produk.edTodayStock
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { onSelected(produk) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) activeColor.copy(alpha = 0.10f) else bgColor,
                                border = BorderStroke(1.dp, if (selected) activeColor else borderColor)
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Box(Modifier.size(42.dp).clip(CircleShape).background(activeColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                        Text(produk.name.firstOrNull()?.uppercaseChar()?.toString() ?: "P", color = activeColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(produk.name, color = textColor, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                                        Text("${produk.category} • ${produk.code}", color = mutedColor, style = MaterialTheme.typography.labelSmall)
                                        Text("Fisik ${Formatter.ribuan(produk.stock.toLong())} ${produk.unit} • Layak ${Formatter.ribuan(stokLayak.toLong())}", color = mutedColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = activeColor, modifier = Modifier.size(24.dp))
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
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = surfaceColor
    )
}

// Komponen Toggle Animasi Halus
@Composable
private fun ToggleSection(
    title: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    textColor: Color,
    mutedColor: Color,
    content: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "arrow_rotation")
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onToggle).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(mutedColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = mutedColor, modifier = Modifier.size(20.dp))
            }
            Text(title, fontWeight = FontWeight.SemiBold, color = textColor, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = mutedColor, modifier = Modifier.rotate(rotation))
        }
        AnimatedVisibility(visible = isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            content()
        }
    }
}