package muhamad.irfan.si_tahu.ui.penjualan

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.ItemDraftRekap
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.utama.AktivitasUtamaAdmin
import muhamad.irfan.si_tahu.ui.utama.SiTahuProTheme
import muhamad.irfan.si_tahu.ui.utama.TabIds
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.InputAngka
import muhamad.irfan.si_tahu.util.PembantuPilihTanggalWaktu
import muhamad.irfan.si_tahu.util.PembantuCetak
import java.util.concurrent.TimeUnit

class AktivitasRekapPasar : AktivitasDasar() {

    private fun tandaiKembaliKePenjualan() {
        getSharedPreferences(NAV_PREF_NAME, MODE_PRIVATE)
            .edit()
            .putInt(NAV_KEY_ADMIN_TAB, TabIds.ADMIN_SALES)
            .apply()
    }

    private fun kembaliKePenjualan() {
        tandaiKembaliKePenjualan()
        startActivity(AktivitasUtamaAdmin.intent(this, TabIds.ADMIN_SALES, clearTop = true))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        setContent {
            SiTahuProTheme {
                MarketRecapScreen(
                    onNavigateBack = { finish() },
                    onShowMessage = { pesan -> showMessage(pesan) },
                    getCurrentUserId = { currentUserId() },
                    onCloseAfterSave = { kembaliKePenjualan() },
                    activityContext = this@AktivitasRekapPasar
                )
            }
        }
    }

    companion object {
        private const val NAV_PREF_NAME = "si_tahu_navigation"
        private const val NAV_KEY_ADMIN_TAB = "next_admin_tab"
    }
}

// Data Class Bantuan
private data class OpsiHargaRekap(
    val label: String,
    val price: Long,
    val defaultCashier: Boolean
)

// === KOMPONEN UTAMA UI ===

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketRecapScreen(
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    getCurrentUserId: () -> String,
    onCloseAfterSave: () -> Unit,
    activityContext: AppCompatActivity
) {
    val coroutineScope = rememberCoroutineScope()

    // State Data Master
    var products by remember { mutableStateOf<List<Produk>>(emptyList()) }
    var draftItems by remember { mutableStateOf<List<ItemDraftRekap>>(emptyList()) }

    // State Pilihan Aktif
    var selectedProduk by remember { mutableStateOf<Produk?>(null) }
    var opsiHargaAktif by remember { mutableStateOf<List<OpsiHargaRekap>>(emptyList()) }
    var selectedHargaIndex by remember { mutableStateOf(0) }

    // State Form
    var qtyInput by remember { mutableStateOf("") }
    var tanggal by remember { mutableStateOf(Formatter.currentDateOnly()) }
    var waktu by remember { mutableStateOf(Formatter.currentTimeOnly()) }

    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var showDateTime by remember { mutableStateOf(false) }
    var showProductPicker by remember { mutableStateOf(false) }
    var showPriceDropdown by remember { mutableStateOf(false) }
    var showCancelDraftDialog by remember { mutableStateOf(false) }

    // State Receipt Dialog Modern
    var savedReceiptText by remember { mutableStateOf<String?>(null) }

    // Tema Warna Dinamis Pro
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF111827) else Color(0xFFF3F4F6)
    val surfaceColor = if (isDark) Color(0xFF1F2937) else Color(0xFFFFFFFF)
    val primaryColor = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
    val successColor = if (isDark) Color(0xFF10B981) else Color(0xFF059669)
    val dangerColor = if (isDark) Color(0xFFEF4444) else Color(0xFFDC2626)
    val warningColor = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)
    val textColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF111827)
    val mutedColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)

    // Fungsi Logika Stok
    fun stokLayakJual(product: Produk): Int = product.safeStock + product.nearExpiredStock + product.edTodayStock
    fun labelStatusStokRekap(product: Produk): String = when {
        stokLayakJual(product) <= 0 && product.expiredStock > 0 -> "Kedaluwarsa"
        stokLayakJual(product) <= 0 -> "Habis"
        product.edTodayStock > 0 -> "ED Hari Ini"
        product.nearExpiredStock > 0 -> "Hampir Kedaluwarsa"
        product.producedToday -> "Produksi Baru"
        else -> "Sisa Stok"
    }
    fun warnaStatusStok(status: String): Color = when (status) {
        "Kedaluwarsa", "Habis" -> dangerColor
        "ED Hari Ini", "Hampir Kedaluwarsa" -> warningColor
        else -> successColor
    }
    fun labelProduksiTerakhirRekap(product: Produk): String {
        val last = product.lastProductionDate.trim()
        if (last.isBlank()) return ""
        return runCatching {
            val todayMillis = Formatter.parseDate("${Formatter.currentDateOnly()}T00:00:00").time
            val lastMillis = Formatter.parseDate("${last}T00:00:00").time
            val days = TimeUnit.MILLISECONDS.toDays(todayMillis - lastMillis).toInt()
            when (days) {
                0 -> "Hari ini"
                1 -> "Kemarin"
                in 2..Int.MAX_VALUE -> "$days hari lalu"
                else -> Formatter.readableShortDate(last)
            }
        }.getOrDefault(Formatter.readableShortDate(last))
    }

    fun isHargaPasar(label: String): Boolean {
        val normalized = label.trim().lowercase()
        return normalized.contains("pasar") || normalized.contains("market")
    }

    fun opsiHargaUntukRekap(product: Produk): List<OpsiHargaRekap> {
        // Rekap pasar boleh memakai semua harga aktif yang terpasang di produk.
        // Harga dengan label pasar tetap diprioritaskan di urutan atas, tetapi pilihan lain
        // seperti kasir, grosir, rumahan, atau titipan tetap bisa dipilih.
        return product.channels
            .filter { it.active && it.price > 0L }
            .sortedWith(
                compareByDescending<muhamad.irfan.si_tahu.data.HargaKanal> { isHargaPasar(it.label) }
                    .thenByDescending { it.defaultCashier }
                    .thenBy { it.label.lowercase() }
            )
            .map { OpsiHargaRekap(it.label, it.price, it.defaultCashier) }
    }

    fun mintaKembali(showConfirm: () -> Unit) {
        if (draftItems.isNotEmpty() || qtyInput.isNotBlank()) showConfirm() else onNavigateBack()
    }

    // Load Data
    val loadProducts = { _: String ->
        isLoading = true
        coroutineScope.launch {
            runCatching {
                RepositoriFirebaseUtama.muatProdukAktif()
                    .filter { product -> stokLayakJual(product) > 0 && opsiHargaUntukRekap(product).isNotEmpty() }
                    .sortedWith(
                        compareBy<Produk> { labelStatusStokRekap(it) != "ED Hari Ini" }
                            .thenBy { labelStatusStokRekap(it) != "Hampir Kedaluwarsa" }
                            .thenBy { it.name.lowercase() }
                    )
            }.onSuccess { result ->
                products = result

                // Pertahankan pilihan jika masih ada di daftar baru, jika tidak pilih yang pertama
                val produkTerpilihSaatIni = selectedProduk
                selectedProduk = if (produkTerpilihSaatIni != null && result.any { it.id == produkTerpilihSaatIni.id }) {
                    result.find { it.id == produkTerpilihSaatIni.id }
                } else {
                    result.firstOrNull()
                }
                isLoading = false
            }.onFailure {
                products = emptyList()
                selectedProduk = null
                isLoading = false
                onShowMessage(it.message ?: "Gagal memuat produk")
            }
        }
    }

    LaunchedEffect(Unit) { loadProducts(tanggal) }

    // Update Harga saat Produk Berganti
    LaunchedEffect(selectedProduk) {
        val produkTerpilih = selectedProduk
        if (produkTerpilih != null) {
            val listHarga = opsiHargaUntukRekap(produkTerpilih)

            opsiHargaAktif = listHarga
            selectedHargaIndex = 0
        } else {
            opsiHargaAktif = emptyList()
            selectedHargaIndex = 0
        }
    }

    val qtyParsed = InputAngka.parseLong(qtyInput).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val stokProdukTerpilih = selectedProduk?.let { stokLayakJual(it) } ?: 0
    val qtySudahDiDraftProdukTerpilih = selectedProduk?.let { produk ->
        draftItems.filter { it.productId == produk.id }.sumOf { it.qty }
    } ?: 0
    val sisaStokProdukTerpilih = (stokProdukTerpilih - qtySudahDiDraftProdukTerpilih).coerceAtLeast(0)
    val selectedHarga = opsiHargaAktif.getOrNull(selectedHargaIndex)
    val stokTidakCukupSaatInput = selectedProduk != null && qtyParsed > 0 && qtyParsed > sisaStokProdukTerpilih
    val pesanStokSaatInput = when {
        selectedProduk == null -> ""
        stokProdukTerpilih <= 0 -> "Stok produk ini habis, tidak bisa masuk draft."
        sisaStokProdukTerpilih <= 0 -> "Semua stok produk ini sudah masuk draft."
        stokTidakCukupSaatInput -> "Stok tidak mencukupi. Sisa bisa dijual: ${Formatter.ribuan(sisaStokProdukTerpilih.toLong())} ${selectedProduk?.unit.orEmpty()}."
        else -> ""
    }
    val bolehMasukDraft = selectedProduk != null &&
            selectedHarga != null &&
            qtyParsed > 0 &&
            !stokTidakCukupSaatInput &&
            sisaStokProdukTerpilih > 0

    // Fungsi Tambah Keranjang
    val addItemToDraft = {
        val produk = selectedProduk
        if (produk == null) onShowMessage("Pilih produk dahulu.")
        else if (selectedHarga == null) onShowMessage("Tidak ada harga aktif untuk produk ini.")
        else {
            val qty = qtyParsed
            val hargaTerpilih = selectedHarga

            if (qty <= 0) {
                onShowMessage("Jumlah harus lebih dari 0")
            } else if (qty > sisaStokProdukTerpilih) {
                onShowMessage("Stok tidak mencukupi. Sisa bisa dijual: ${Formatter.ribuan(sisaStokProdukTerpilih.toLong())} ${produk.unit}")
            } else {
                val draftList = draftItems.toMutableList()
                val existingIdx = draftList.indexOfFirst {
                    it.productId == produk.id &&
                            it.channelLabel.equals(hargaTerpilih.label, true) &&
                            it.price == hargaTerpilih.price
                }

                if (existingIdx >= 0) {
                    val existItem = draftList[existingIdx]
                    draftList[existingIdx] = existItem.copy(qty = existItem.qty + qty)
                } else {
                    draftList.add(ItemDraftRekap(
                        id = "draft-${produk.id}-${hargaTerpilih.label}-${System.currentTimeMillis()}",
                        productId = produk.id,
                        productName = produk.name,
                        channelLabel = hargaTerpilih.label,
                        qty = qty,
                        price = hargaTerpilih.price
                    ))
                }
                draftItems = draftList
                qtyInput = "" // Reset input
            }
        }
    }

    // Menghitung Total Draft Rekap
    val totalQty = draftItems.sumOf { it.qty }
    val totalRevenue = draftItems.sumOf { it.qty.toLong() * it.price }
    val totalJenisProduk = draftItems.map { it.productId }.distinct().size

    BackHandler(enabled = draftItems.isNotEmpty() || qtyInput.isNotBlank()) {
        mintaKembali { showCancelDraftDialog = true }
    }

    if (showCancelDraftDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDraftDialog = false },
            title = { Text("Batalkan rekap?", fontWeight = FontWeight.Bold, color = textColor) },
            text = {
                Text(
                    "Data rekap yang belum disimpan akan hilang. Lanjut kembali?",
                    color = mutedColor
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        draftItems = emptyList()
                        qtyInput = ""
                        showCancelDraftDialog = false
                        onNavigateBack()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = dangerColor)
                ) { Text("Buang Rekap", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDraftDialog = false }) { Text("Lanjut Input", color = mutedColor) }
            },
            containerColor = surfaceColor,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Modern Receipt Dialog
    if (savedReceiptText != null) {
        ProReceiptSuccessDialog(
            receiptText = savedReceiptText!!,
            primaryColor = primaryColor,
            surfaceColor = surfaceColor,
            textColor = textColor,
            borderColor = borderColor,
            successColor = successColor,
            onDismiss = {
                savedReceiptText = null
                onCloseAfterSave()
            }
        )
    }

    if (showProductPicker) {
        ProdukRekapPickerDialog(
            products = products,
            selectedProductId = selectedProduk?.id.orEmpty(),
            textColor = textColor,
            mutedColor = mutedColor,
            surfaceColor = surfaceColor,
            bgColor = bgColor,
            borderColor = borderColor,
            primaryColor = primaryColor,
            stokLayakJual = { stokLayakJual(it) },
            labelStatusStok = { labelStatusStokRekap(it) },
            warnaStatus = { status -> warnaStatusStok(status) },
            labelProduksiTerakhir = { labelProduksiTerakhirRekap(it) },
            onDismiss = { showProductPicker = false },
            onSelected = { product ->
                selectedProduk = product
                showProductPicker = false
            }
        )
    }

    if (showPriceDropdown) {
        HargaRekapPickerDialog(
            options = opsiHargaAktif,
            selectedIndex = selectedHargaIndex,
            textColor = textColor,
            mutedColor = mutedColor,
            surfaceColor = surfaceColor,
            bgColor = bgColor,
            borderColor = borderColor,
            primaryColor = primaryColor,
            onDismiss = { showPriceDropdown = false },
            onSelected = { index ->
                selectedHargaIndex = index
                showPriceDropdown = false
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
                            Text("Rekap Pasar", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text("Pencatatan penjualan kolektif massal", style = MaterialTheme.typography.labelMedium, color = mutedColor)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { mintaKembali { showCancelDraftDialog = true } }) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Kembali", tint = textColor) }
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
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp)) {
                    // Ringkasan singkat rekap
                    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Pendapatan Rekap", color = mutedColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Text(Formatter.currency(totalRevenue), fontWeight = FontWeight.Black, color = successColor, style = MaterialTheme.typography.headlineSmall)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(shape = RoundedCornerShape(8.dp), color = primaryColor.copy(alpha = 0.1f)) {
                                Text("${Formatter.ribuan(totalQty.toLong())} pcs", color = primaryColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                            }
                            Text("${Formatter.ribuan(totalJenisProduk.toLong())} produk", color = mutedColor, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // Tombol Simpan
                    Button(
                        onClick = {
                            if (draftItems.isEmpty()) {
                                onShowMessage("Keranjang rekap masih kosong")
                                return@Button
                            }
                            if (tanggal.isBlank() || waktu.isBlank()) {
                                onShowMessage("Tanggal dan waktu wajib diisi")
                                return@Button
                            }

                            isSaving = true
                            coroutineScope.launch {
                                runCatching {
                                    val saleId = RepositoriFirebaseUtama.simpanRekapPasar(
                                        dateOnly = tanggal,
                                        timeOnly = waktu,
                                        sumberTransaksi = "PASAR",
                                        draftItems = draftItems,
                                        userAuthId = getCurrentUserId(),
                                        products = products
                                    )
                                    RepositoriFirebaseUtama.buildReceiptText(saleId)
                                }.onSuccess { receipt ->
                                    isSaving = false
                                    savedReceiptText = receipt
                                }.onFailure {
                                    isSaving = false
                                    onShowMessage(it.message ?: "Gagal menyimpan rekap")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isSaving && draftItems.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor, disabledContainerColor = primaryColor.copy(alpha = 0.5f))
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Menyimpan...", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        } else {
                            Icon(Icons.Rounded.Done, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Simpan Rekap Pasar", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
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

            // === 1. PENGATURAN TANGGAL ===
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(Modifier.padding(8.dp)) {
                    ToggleSection(
                        title = "Waktu Penjualan (${Formatter.readableShortDate(tanggal)})",
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
                                shape = RoundedCornerShape(14.dp),
                                textStyle = LocalTextStyle.current.copy(color = textColor, fontWeight = FontWeight.Medium),
                                modifier = Modifier.weight(1f).clickable {
                                    PembantuPilihTanggalWaktu.showDatePicker(activityContext, tanggal) {
                                        tanggal = it
                                        draftItems = emptyList() // Kosongkan draft kalau ganti hari
                                        loadProducts(it)
                                    }
                                },
                                enabled = false,
                                colors = OutlinedTextFieldDefaults.colors(disabledTextColor = textColor, disabledBorderColor = borderColor, disabledLabelColor = mutedColor)
                            )
                            OutlinedTextField(
                                value = waktu,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Jam") },
                                shape = RoundedCornerShape(14.dp),
                                textStyle = LocalTextStyle.current.copy(color = textColor, fontWeight = FontWeight.Medium),
                                modifier = Modifier.weight(1f).clickable {
                                    val iso = Formatter.isoDate(tanggal, "$waktu:00")
                                    PembantuPilihTanggalWaktu.showTimePicker(activityContext, iso) { waktu = it }
                                },
                                enabled = false,
                                colors = OutlinedTextFieldDefaults.colors(disabledTextColor = textColor, disabledBorderColor = borderColor, disabledLabelColor = mutedColor)
                            )
                        }
                    }
                }
            }

            // === 2. FORM INPUT BARANG ===
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Tambah Produk Terjual", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                        Text("Input produk yang laku di pasar.", color = mutedColor, style = MaterialTheme.typography.bodySmall)
                    }

                    // Box Pilih Produk
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = bgColor,
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isLoading && products.isNotEmpty()) {
                                showProductPicker = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                val initial = selectedProduk?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "P"
                                Text(if (isLoading) "..." else initial, color = primaryColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(text = if (isLoading) "Memuat..." else selectedProduk?.name ?: "Pilih produk...", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.bodyLarge)

                                val status = selectedProduk?.let { labelStatusStokRekap(it) } ?: ""
                                val meta = if (isLoading) "Tunggu sebentar" else selectedProduk?.let {
                                    "Stok: ${Formatter.ribuan(stokLayakJual(it).toLong())} ${it.unit} • $status"
                                } ?: "Ketuk untuk memilih"

                                Text(text = meta, color = if(status == "ED Hari Ini" || status == "Hampir Kedaluwarsa") warningColor else if(status == "Kedaluwarsa" || status == "Habis") dangerColor else mutedColor, style = MaterialTheme.typography.labelMedium)

                                selectedProduk?.let { produk ->
                                    val infoProduksi = labelProduksiTerakhirRekap(produk)
                                    val infoEd = when (labelStatusStokRekap(produk)) {
                                        "ED Hari Ini" -> listOf("Prioritaskan dijual", "Prod: $infoProduksi").filter { it.isNotBlank() }.joinToString(" • ")
                                        "Hampir Kedaluwarsa" -> listOf("Mendekati ED", "Prod: $infoProduksi").filter { it.isNotBlank() }.joinToString(" • ")
                                        else -> "Prod: $infoProduksi"
                                    }
                                    if (infoEd.isNotBlank()) Text(text = infoEd, color = mutedColor, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Pilih", tint = mutedColor)
                        }
                    }

                    // Baris Harga & Jumlah
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        // Dropdown Harga
                        Box(Modifier.weight(1.2f)) {
                            OutlinedTextField(
                                value = selectedHarga?.label ?: "Harga belum ada",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Kanal Harga") },
                                trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, null, tint = primaryColor) },
                                shape = RoundedCornerShape(16.dp),
                                textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.SemiBold, color = textColor),
                                supportingText = { Text(" ") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = primaryColor,
                                    unfocusedBorderColor = borderColor,
                                    focusedContainerColor = bgColor,
                                    unfocusedContainerColor = bgColor,
                                    focusedLabelColor = primaryColor,
                                    unfocusedLabelColor = mutedColor
                                ),
                                modifier = Modifier.fillMaxWidth().clickable { if (opsiHargaAktif.size > 1) showPriceDropdown = true }
                            )
                            Spacer(Modifier.matchParentSize().background(Color.Transparent).clickable { if (opsiHargaAktif.size > 1) showPriceDropdown = true })
                        }

                        // Input Jumlah
                        OutlinedTextField(
                            value = qtyInput,
                            onValueChange = { qtyInput = InputAngka.formatInput(it) },
                            label = { Text("Pcs") },
                            isError = stokTidakCukupSaatInput || (selectedProduk != null && stokProdukTerpilih <= 0),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            shape = RoundedCornerShape(16.dp),
                            supportingText = {
                                if (selectedProduk != null) {
                                    Text("Sisa: ${Formatter.ribuan(sisaStokProdukTerpilih.toLong())} ${selectedProduk?.unit.orEmpty()}")
                                } else {
                                    Text(" ")
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (stokTidakCukupSaatInput) dangerColor else primaryColor,
                                unfocusedBorderColor = if (stokTidakCukupSaatInput) dangerColor else borderColor,
                                errorBorderColor = dangerColor,
                                errorLabelColor = dangerColor,
                                errorSupportingTextColor = dangerColor,
                                focusedContainerColor = bgColor,
                                unfocusedContainerColor = bgColor,
                                errorContainerColor = bgColor
                            ),
                            textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Start, color = textColor),
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    AnimatedVisibility(visible = pesanStokSaatInput.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = dangerColor.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, dangerColor.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                pesanStokSaatInput,
                                color = dangerColor,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }

                    // Info Harga & Tombol Tambah
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        val hargaText = selectedHarga?.let { Formatter.currency(it.price) } ?: "-"
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(successColor.copy(alpha=0.15f)), contentAlignment=Alignment.Center) {
                                Icon(Icons.Rounded.MonetizationOn, null, tint = successColor, modifier = Modifier.size(20.dp))
                            }
                            Text(hargaText, color = successColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                        }

                        Button(
                            onClick = addItemToDraft,
                            enabled = bolehMasukDraft,
                            shape = RoundedCornerShape(100),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor, disabledContainerColor = primaryColor.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Masuk Draft", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // === 3. DAFTAR DRAFT REKAP ===
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Draft Keranjang", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp))

                    if (draftItems.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Belum ada barang yang dicatat.", color = mutedColor, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            draftItems.forEach { item ->
                                val stokProduk = products.find { it.id == item.productId }?.let { stokLayakJual(it) } ?: item.qty
                                val totalDraftProduk = draftItems.filter { it.productId == item.productId }.sumOf { it.qty }
                                val bisaTambah = totalDraftProduk < stokProduk
                                DraftItemRow(
                                    item = item,
                                    surfaceColor = surfaceColor,
                                    bgColor = bgColor,
                                    borderColor = borderColor,
                                    textColor = textColor,
                                    mutedColor = mutedColor,
                                    primaryColor = primaryColor,
                                    dangerColor = dangerColor,
                                    canIncrease = bisaTambah,
                                    onDecrease = {
                                        draftItems = draftItems.mapNotNull { draft ->
                                            if (draft.id == item.id) {
                                                val qtyBaru = draft.qty - 1
                                                if (qtyBaru > 0) draft.copy(qty = qtyBaru) else null
                                            } else draft
                                        }
                                    },
                                    onIncrease = {
                                        val stokSaatIni = products.find { it.id == item.productId }?.let { stokLayakJual(it) } ?: item.qty
                                        val totalDraftSaatIni = draftItems.filter { it.productId == item.productId }.sumOf { it.qty }
                                        if (totalDraftSaatIni >= stokSaatIni) {
                                            onShowMessage("Stok layak tidak mencukupi untuk menambah jumlah.")
                                        } else {
                                            draftItems = draftItems.map { draft ->
                                                if (draft.id == item.id) draft.copy(qty = draft.qty + 1) else draft
                                            }
                                        }
                                    },
                                    onDelete = { draftItems = draftItems.filterNot { it.id == item.id } }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(180.dp)) // Jarak ekstra untuk Bottom Bar
        }
    }
}

// === KOMPONEN UI TAMBAHAN & REUSABLE ===

@Composable
private fun ProReceiptSuccessDialog(
    receiptText: String,
    primaryColor: Color,
    surfaceColor: Color,
    textColor: Color,
    borderColor: Color,
    successColor: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    AlertDialog(
        onDismissRequest = { /* Block dismiss until button is clicked */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        shape = RoundedCornerShape(24.dp),
        containerColor = surfaceColor,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(successColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = successColor, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("Rekap Tersimpan!", color = textColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF111827) else Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = receiptText,
                        color = textColor,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DialogActionButton(Icons.Rounded.Share, "Bagikan", primaryColor) {
                        runCatching { PembantuCetak.shareStrukPdf(context, "Nota Rekap Pasar", receiptText) }
                            .onFailure { Toast.makeText(context, it.message ?: "Gagal membagikan PDF nota", Toast.LENGTH_SHORT).show() }
                    }
                    DialogActionButton(Icons.Rounded.Download, "Unduh", primaryColor) {
                        runCatching { PembantuCetak.downloadStrukPdf(context, "Nota Rekap Pasar", receiptText) }
                            .onFailure { Toast.makeText(context, it.message ?: "Gagal download PDF nota", Toast.LENGTH_SHORT).show() }
                    }
                    DialogActionButton(Icons.Rounded.Print, "Cetak", primaryColor) {
                        runCatching { PembantuCetak.printNota(context, "Nota Rekap Pasar", receiptText) }
                            .onFailure { Toast.makeText(context, it.message ?: "Gagal mencetak nota", Toast.LENGTH_SHORT).show() }
                    }
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Tutup & Kembali", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    )
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

@Composable
private fun ProdukRekapPickerDialog(
    products: List<Produk>,
    selectedProductId: String,
    textColor: Color,
    mutedColor: Color,
    surfaceColor: Color,
    bgColor: Color,
    borderColor: Color,
    primaryColor: Color,
    stokLayakJual: (Produk) -> Int,
    labelStatusStok: (Produk) -> String,
    warnaStatus: (String) -> Color,
    labelProduksiTerakhir: (Produk) -> String,
    onDismiss: () -> Unit,
    onSelected: (Produk) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Siap") }
    val normalizedQuery = query.trim().lowercase()
    val filteredProducts = products
        .asSequence()
        .filter { product ->
            normalizedQuery.isBlank() ||
                    product.name.lowercase().contains(normalizedQuery) ||
                    product.code.lowercase().contains(normalizedQuery) ||
                    product.category.lowercase().contains(normalizedQuery)
        }
        .filter { product ->
            when (filter) {
                "Siap" -> stokLayakJual(product) > 0
                "Habis" -> stokLayakJual(product) <= 0
                else -> true
            }
        }
        .sortedWith(
            compareByDescending<Produk> { stokLayakJual(it) > 0 }
                .thenBy { it.name.lowercase() }
        )
        .toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = surfaceColor,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Pilih Produk", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                Text("Daftar produk dengan semua harga aktif", color = mutedColor, style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Cari produk atau kode...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint=mutedColor) },
                    singleLine = true,
                    shape = RoundedCornerShape(100),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = bgColor,
                        unfocusedContainerColor = bgColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Semua", "Siap", "Habis").forEach { item ->
                        val selected = filter == item
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) primaryColor.copy(alpha = 0.14f) else bgColor,
                            border = BorderStroke(1.dp, if (selected) primaryColor else borderColor),
                            modifier = Modifier.clickable { filter = item }
                        ) {
                            Text(
                                item,
                                color = if (selected) primaryColor else mutedColor,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                if (filteredProducts.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Produk tidak ditemukan", color = mutedColor)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        filteredProducts.forEach { product ->
                            ProdukRekapPickerRow(
                                product = product,
                                selected = product.id == selectedProductId,
                                textColor = textColor,
                                mutedColor = mutedColor,
                                bgColor = bgColor,
                                borderColor = borderColor,
                                primaryColor = primaryColor,
                                stock = stokLayakJual(product),
                                status = labelStatusStok(product),
                                colorStatus = warnaStatus(labelStatusStok(product)),
                                productionInfo = labelProduksiTerakhir(product),
                                onClick = { onSelected(product) }
                            )
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
private fun ProdukRekapPickerRow(
    product: Produk,
    selected: Boolean,
    textColor: Color,
    mutedColor: Color,
    bgColor: Color,
    borderColor: Color,
    primaryColor: Color,
    stock: Int,
    status: String,
    colorStatus: Color,
    productionInfo: String,
    onClick: () -> Unit
) {
    val firstPrice = product.channels
        .filter { it.active && it.price > 0L }
        .sortedWith(compareByDescending<muhamad.irfan.si_tahu.data.HargaKanal> { it.label.lowercase().contains("pasar") || it.label.lowercase().contains("market") }.thenByDescending { it.defaultCashier })
        .firstOrNull()?.price
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) primaryColor.copy(alpha = 0.08f) else bgColor,
        border = BorderStroke(1.dp, if (selected) primaryColor else borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (selected) primaryColor else colorStatus.copy(alpha=0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    product.name.firstOrNull()?.uppercaseChar()?.toString() ?: "P",
                    color = if(selected) Color.White else colorStatus,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(product.name, color = textColor, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOf(product.code, product.category).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { product.unit },
                    color = mutedColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
                Text(
                    "Stok ${Formatter.ribuan(stock.toLong())} ${product.unit}",
                    color = mutedColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    firstPrice?.let { Formatter.currency(it) } ?: "Harga -",
                    color = primaryColor,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(shape = RoundedCornerShape(6.dp), color = colorStatus.copy(alpha=0.1f)) {
                    Text(status, color = colorStatus, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun HargaRekapPickerDialog(
    options: List<OpsiHargaRekap>,
    selectedIndex: Int,
    textColor: Color,
    mutedColor: Color,
    surfaceColor: Color,
    bgColor: Color,
    borderColor: Color,
    primaryColor: Color,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Pilih Harga", fontWeight = FontWeight.Bold, color = textColor) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                options.forEachIndexed { index, option ->
                    val selected = index == selectedIndex
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) primaryColor.copy(alpha = 0.10f) else bgColor,
                        border = BorderStroke(1.dp, if (selected) primaryColor else borderColor),
                        modifier = Modifier.fillMaxWidth().clickable { onSelected(index) }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(option.label, color = if(selected) primaryColor else textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (option.defaultCashier) "Harga utama" else "Harga aktif",
                                    color = mutedColor,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text(Formatter.currency(option.price), color = if(selected) primaryColor else textColor, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = mutedColor, fontWeight=FontWeight.Bold) } },
        containerColor = surfaceColor
    )
}

@Composable
private fun DraftItemRow(
    item: ItemDraftRekap,
    surfaceColor: Color,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    primaryColor: Color,
    dangerColor: Color,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.productName, fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.channelLabel, color = mutedColor, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    Formatter.currency(item.qty.toLong() * item.price),
                    fontWeight = FontWeight.Black,
                    color = textColor,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(100),
                    color = surfaceColor,
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        IconButton(onClick = onDecrease, modifier = Modifier.size(34.dp)) {
                            Text("-", color = dangerColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "${Formatter.ribuan(item.qty.toLong())} pcs",
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.widthIn(min = 72.dp),
                            textAlign = TextAlign.Center
                        )
                        IconButton(onClick = onIncrease, enabled = canIncrease, modifier = Modifier.size(34.dp)) {
                            Text(
                                "+",
                                color = if (canIncrease) primaryColor else mutedColor.copy(alpha = 0.45f),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "@ ${Formatter.currency(item.price)}",
                        color = mutedColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Hapus", tint = dangerColor, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

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
            Box(Modifier.size(36.dp).clip(CircleShape).background(mutedColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = mutedColor, modifier = Modifier.size(18.dp))
            }
            Text(title, fontWeight = FontWeight.SemiBold, color = textColor, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = mutedColor, modifier = Modifier.rotate(rotation))
        }
        AnimatedVisibility(visible = isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            content()
        }
    }
}