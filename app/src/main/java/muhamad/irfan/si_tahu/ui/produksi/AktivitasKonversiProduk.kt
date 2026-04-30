package muhamad.irfan.si_tahu.ui.produksi

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
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapVert
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
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.utama.SiTahuProTheme
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.InputAngka
import muhamad.irfan.si_tahu.util.PembantuPilihTanggalWaktu

private fun stokLayakPakaiBahan(produk: Produk): Int = produk.safeStock + produk.nearExpiredStock + produk.edTodayStock

private fun labelStatusLayakPakai(produk: Produk): String = when {
    stokLayakPakaiBahan(produk) <= 0 && produk.expiredStock > 0 -> "Kadaluarsa"
    stokLayakPakaiBahan(produk) <= 0 -> "Habis"
    produk.edTodayStock > 0 -> "ED Hari Ini"
    produk.nearExpiredStock > 0 -> "Hampir ED"
    produk.producedToday -> "Produksi Hari Ini"
    else -> "Aman"
}

class AktivitasKonversiProduk : AktivitasDasar() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        setContent {
            SiTahuProTheme {
                ConversionScreen(
                    onNavigateBack = { finish() },
                    onShowMessage = { pesan -> showMessage(pesan) },
                    getCurrentUserId = { currentUserId() },
                    onSaveSuccess = {
                        setResult(android.app.Activity.RESULT_OK)
                        finish()
                    },
                    activityContext = this@AktivitasKonversiProduk
                )
            }
        }
    }
}

// === KOMPONEN UTAMA UI ===

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversionScreen(
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    getCurrentUserId: () -> String,
    onSaveSuccess: () -> Unit,
    activityContext: AppCompatActivity
) {
    // State UI
    var daftarProdukDasar by remember { mutableStateOf<List<Produk>>(emptyList()) }
    var daftarProdukOlahan by remember { mutableStateOf<List<Produk>>(emptyList()) }

    var selectedProdukDasar by remember { mutableStateOf<Produk?>(null) }
    var selectedProdukOlahan by remember { mutableStateOf<Produk?>(null) }

    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // Form States
    var qtyBahan by remember { mutableStateOf("") }
    var qtyHasil by remember { mutableStateOf("") }
    var tanggal by remember { mutableStateOf(Formatter.currentDateOnly()) }
    var waktu by remember { mutableStateOf(Formatter.currentTimeOnly()) }
    var catatan by remember { mutableStateOf("") }

    // Visibility Toggles
    var showDateTime by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Warna Tema Dinamis Pro
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF111827) else Color(0xFFF3F4F6)
    val surfaceColor = if (isDark) Color(0xFF1F2937) else Color(0xFFFFFFFF)
    val primaryColor = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
    val secondaryColor = if (isDark) Color(0xFF8B5CF6) else Color(0xFF6D28D9)
    val textColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF111827)
    val mutedColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)

    // Load Data Awal.
    LaunchedEffect(Unit) {
        isLoading = true
        runCatching {
            RepositoriFirebaseUtama.muatProdukProduksiDasar() to RepositoriFirebaseUtama.muatProdukProduksiOlahan()
        }.onSuccess { (produkDasar, produkOlahan) ->
            val produkDasarLayak = produkDasar.filter { stokLayakPakaiBahan(it) > 0 }
            daftarProdukDasar = produkDasarLayak
            daftarProdukOlahan = produkOlahan
            selectedProdukDasar = produkDasarLayak.firstOrNull()
            selectedProdukOlahan = produkOlahan.firstOrNull()
            isLoading = false
            when {
                produkDasar.isEmpty() || produkOlahan.isEmpty() -> onShowMessage("Produk DASAR/OLAHAN belum lengkap di Firebase.")
                produkDasarLayak.isEmpty() -> onShowMessage("Semua bahan dasar habis atau kadaluarsa. Tidak ada stok layak pakai untuk produksi olahan.")
            }
        }.onFailure {
            isLoading = false
            onShowMessage(it.message ?: "Gagal memuat data produk")
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
                            Text("Konversi Olahan", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text("Ubah bahan mentah menjadi hasil", style = MaterialTheme.typography.labelMedium, color = mutedColor)
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
        bottomBar = {
            Surface(
                color = surfaceColor,
                shadowElevation = 16.dp,
                border = if (isDark) BorderStroke(1.dp, borderColor) else null,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(24.dp)
                ) {
                    val inputBahanInt = InputAngka.parseLong(qtyBahan).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    val inputHasilInt = InputAngka.parseLong(qtyHasil).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    val isFormValid = selectedProdukDasar != null &&
                            selectedProdukOlahan != null &&
                            inputBahanInt > 0 &&
                            inputHasilInt > 0

                    Button(
                        onClick = {
                            if (selectedProdukDasar == null || selectedProdukOlahan == null) {
                                onShowMessage("Pilih bahan dan hasil terlebih dahulu.")
                                return@Button
                            }
                            if (selectedProdukDasar!!.id == selectedProdukOlahan!!.id) {
                                onShowMessage("Produk asal dan hasil tidak boleh sama.")
                                return@Button
                            }
                            if (inputBahanInt <= 0 || inputHasilInt <= 0) {
                                onShowMessage("Jumlah bahan dan hasil harus lebih dari 0.")
                                return@Button
                            }
                            if (tanggal.isBlank() || waktu.isBlank()) {
                                onShowMessage("Tanggal dan waktu wajib diisi.")
                                return@Button
                            }

                            val bahanTerpilih = selectedProdukDasar
                            val hasilTerpilih = selectedProdukOlahan
                            if (bahanTerpilih == null || hasilTerpilih == null) return@Button
                            val jumlahBahan = inputBahanInt
                            val jumlahHasil = inputHasilInt
                            val stokLayakBahan = stokLayakPakaiBahan(bahanTerpilih)
                            if (jumlahBahan > stokLayakBahan) {
                                onShowMessage("Stok layak pakai ${bahanTerpilih.name} hanya ${Formatter.ribuan(stokLayakBahan.toLong())} ${bahanTerpilih.unit}. Stok kadaluarsa tidak bisa dipakai.")
                                return@Button
                            }
                            val dateTime = Formatter.isoDate(tanggal, "$waktu:00")
                            isSaving = true

                            coroutineScope.launch {
                                runCatching {
                                    RepositoriFirebaseUtama.simpanKonversi(
                                        dateTime = dateTime,
                                        fromProductId = bahanTerpilih.id,
                                        toProductId = hasilTerpilih.id,
                                        inputQty = jumlahBahan,
                                        outputQty = jumlahHasil,
                                        note = catatan,
                                        userAuthId = getCurrentUserId()
                                    )
                                }.onSuccess {
                                    isSaving = false
                                    onShowMessage("Berhasil! ${bahanTerpilih.name} -${Formatter.ribuan(jumlahBahan.toLong())}, ${hasilTerpilih.name} +${Formatter.ribuan(jumlahHasil.toLong())}.")
                                    onSaveSuccess()
                                }.onFailure { error ->
                                    isSaving = false
                                    onShowMessage(error.message ?: "Gagal menyimpan produk olahan. Cek stok bahan dan koneksi.")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isSaving && isFormValid,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = secondaryColor,
                            disabledContainerColor = secondaryColor.copy(alpha = 0.5f)
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Memproses...", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        } else {
                            Icon(Icons.Rounded.SwapVert, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Simpan Konversi", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
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

            // === 1. KARTU ALUR KONVERSI TERPADU (Visual Flow) ===
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(Modifier.padding(24.dp)) {

                    Text("Alur Produksi", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))

                    // --- BLOK BAHAN BAKU ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(bgColor)
                            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Dari Bahan Baku",
                            color = primaryColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        ProductDropdownFieldKonversi(
                            label = "Pilih Bahan",
                            produk = daftarProdukDasar,
                            selectedProduk = selectedProdukDasar,
                            emptyMessage = "Tidak ada bahan dasar layak pakai",
                            isLoading = isLoading,
                            gunakanStokLayak = true,
                            accentColor = primaryColor,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            borderColor = borderColor,
                            surfaceColor = surfaceColor,
                            onSelected = { selectedProdukDasar = it }
                        )

                        OutlinedTextField(
                            value = qtyBahan,
                            onValueChange = { qtyBahan = InputAngka.formatInput(it) },
                            label = { Text("Jumlah Terpakai") },
                            placeholder = { Text("0") },
                            suffix = { Text(selectedProdukDasar?.unit ?: "-", color = mutedColor, fontWeight = FontWeight.Medium) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(14.dp),
                            textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Start, color = textColor),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = borderColor,
                                focusedContainerColor = surfaceColor,
                                unfocusedContainerColor = surfaceColor,
                                focusedLabelColor = primaryColor,
                                unfocusedLabelColor = mutedColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        selectedProdukDasar?.let { bahan ->
                            Text(
                                "Stok layak pakai: ${Formatter.ribuan(stokLayakPakaiBahan(bahan).toLong())} ${bahan.unit} • ${labelStatusLayakPakai(bahan)}",
                                color = mutedColor,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    // --- IKON PENGHUBUNG TRANSAKSI ---
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = primaryColor,
                            shadowElevation = 2.dp,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.ArrowDownward, contentDescription = "Menjadi", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // --- BLOK HASIL OLAHAN ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(bgColor)
                            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Menjadi Hasil Olahan",
                            color = secondaryColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        ProductDropdownFieldKonversi(
                            label = "Pilih Hasil Olahan",
                            produk = daftarProdukOlahan,
                            selectedProduk = selectedProdukOlahan,
                            emptyMessage = "Tidak ada produk OLAHAN aktif",
                            isLoading = isLoading,
                            accentColor = secondaryColor,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            borderColor = borderColor,
                            surfaceColor = surfaceColor,
                            onSelected = { selectedProdukOlahan = it }
                        )

                        OutlinedTextField(
                            value = qtyHasil,
                            onValueChange = { qtyHasil = InputAngka.formatInput(it) },
                            label = { Text("Hasil Didapat") },
                            placeholder = { Text("0") },
                            suffix = { Text(selectedProdukOlahan?.unit ?: "-", color = mutedColor, fontWeight = FontWeight.Medium) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            shape = RoundedCornerShape(14.dp),
                            textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Start, color = textColor),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = secondaryColor,
                                unfocusedBorderColor = borderColor,
                                focusedContainerColor = surfaceColor,
                                unfocusedContainerColor = surfaceColor,
                                focusedLabelColor = secondaryColor,
                                unfocusedLabelColor = mutedColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // === 2. KARTU PENGATURAN TAMBAHAN ===
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Pengaturan Lanjutan", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        // Toggle Tanggal
                        ToggleSection(
                            title = "Waktu Pencatatan",
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
                                    modifier = Modifier.weight(1f).clickable {
                                        PembantuPilihTanggalWaktu.showDatePicker(activityContext, tanggal) { tanggal = it }
                                    },
                                    enabled = false,
                                    textStyle = LocalTextStyle.current.copy(color = textColor, fontWeight = FontWeight.Medium),
                                    colors = OutlinedTextFieldDefaults.colors(disabledTextColor = textColor, disabledBorderColor = borderColor, disabledLabelColor = mutedColor)
                                )
                                OutlinedTextField(
                                    value = waktu,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Jam") },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f).clickable {
                                        val iso = Formatter.isoDate(tanggal, "$waktu:00")
                                        PembantuPilihTanggalWaktu.showTimePicker(activityContext, iso) { waktu = it }
                                    },
                                    enabled = false,
                                    textStyle = LocalTextStyle.current.copy(color = textColor, fontWeight = FontWeight.Medium),
                                    colors = OutlinedTextFieldDefaults.colors(disabledTextColor = textColor, disabledBorderColor = borderColor, disabledLabelColor = mutedColor)
                                )
                            }
                        }

                        HorizontalDivider(color = borderColor, modifier = Modifier.padding(horizontal = 16.dp))

                        // Toggle Catatan
                        ToggleSection(
                            title = "Catatan Tambahan (Opsional)",
                            icon = Icons.Rounded.Edit,
                            isExpanded = showNote,
                            onToggle = { showNote = !showNote },
                            textColor = textColor,
                            mutedColor = mutedColor
                        ) {
                            Box(Modifier.padding(horizontal = 8.dp, vertical = 8.dp).padding(bottom = 8.dp)) {
                                OutlinedTextField(
                                    value = catatan,
                                    onValueChange = { catatan = it },
                                    placeholder = { Text("Tulis keterangan...") },
                                    shape = RoundedCornerShape(14.dp),
                                    textStyle = LocalTextStyle.current.copy(color = textColor),
                                    modifier = Modifier.fillMaxWidth().height(120.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = primaryColor,
                                        unfocusedBorderColor = borderColor,
                                        focusedContainerColor = bgColor,
                                        unfocusedContainerColor = bgColor
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Spacer ekstra agar konten paling bawah tidak tertutup oleh tombol sticky
            Spacer(Modifier.height(100.dp))
        }
    }
}


@Composable
private fun ProductDropdownFieldKonversi(
    label: String,
    produk: List<Produk>,
    selectedProduk: Produk?,
    emptyMessage: String,
    isLoading: Boolean,
    accentColor: Color,
    textColor: Color,
    mutedColor: Color,
    borderColor: Color,
    surfaceColor: Color,
    gunakanStokLayak: Boolean = false,
    onSelected: (Produk) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val enabled = !isLoading && produk.isNotEmpty()
    val valueText = when {
        isLoading -> "Memuat produk dari Firebase..."
        produk.isEmpty() -> emptyMessage
        else -> selectedProduk?.name ?: "Pilih produk"
    }
    val supportText = selectedProduk?.let {
        val status = if (it.active) "Aktif" else "Nonaktif"
        val stokInfo = if (gunakanStokLayak) {
            "Stok layak ${Formatter.ribuan(stokLayakPakaiBahan(it).toLong())} ${it.unit} • ${labelStatusLayakPakai(it)}"
        } else {
            "Stok ${Formatter.ribuan(it.stock.toLong())} ${it.unit} • ${it.category}"
        }
        "$status • $stokInfo"
    } ?: if (enabled) "Tekan untuk memilih produk" else "Pengecekan selesai."

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valueText,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label) },
            leadingIcon = {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(selectedProduk?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "P", color = accentColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            },
            trailingIcon = {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Pilih", tint = if (enabled) accentColor else mutedColor)
            },
            supportingText = { Text(supportText, color = mutedColor) },
            shape = RoundedCornerShape(16.dp),
            textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold),
            colors = OutlinedTextFieldDefaults.colors(
                disabledBorderColor = borderColor,
                disabledContainerColor = surfaceColor,
                disabledTextColor = if (enabled) textColor else mutedColor,
                disabledLabelColor = mutedColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = enabled) { showPicker = true }
        )

        if (showPicker) {
            AlertDialog(
                onDismissRequest = { showPicker = false },
                title = { Text(label, fontWeight = FontWeight.Bold, color = textColor) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        produk.forEach { item ->
                            val isSelected = item.id == selectedProduk?.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        onSelected(item)
                                        showPicker = false
                                    },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) accentColor.copy(alpha = 0.12f) else surfaceColor,
                                border = BorderStroke(1.dp, if (isSelected) accentColor else borderColor)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        Modifier.size(40.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(item.name.firstOrNull()?.uppercaseChar()?.toString() ?: "P", color = accentColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.bodyLarge)
                                        val status = if (item.active) "Aktif" else "Nonaktif"
                                        val stokInfo = if (gunakanStokLayak) {
                                            "Stok layak ${Formatter.ribuan(stokLayakPakaiBahan(item).toLong())} ${item.unit} • ${labelStatusLayakPakai(item)}"
                                        } else {
                                            "Stok ${Formatter.ribuan(item.stock.toLong())} ${item.unit} • ${item.category}"
                                        }
                                        Text(
                                            "$status • $stokInfo",
                                            color = mutedColor,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPicker = false }) {
                        Text("Tutup", color = accentColor, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = surfaceColor,
                shape = RoundedCornerShape(24.dp)
            )
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
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onToggle)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(mutedColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = mutedColor, modifier = Modifier.size(20.dp))
            }
            Text(title, fontWeight = FontWeight.SemiBold, color = textColor, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = mutedColor, modifier = Modifier.rotate(rotation))
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            content()
        }
    }
}