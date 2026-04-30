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
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
import kotlin.math.roundToInt

class AktivitasProduksiTahuDasar : AktivitasDasar() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        setContent {
            SiTahuProTheme {
                BasicProductionScreen(
                    onNavigateBack = { finish() },
                    onShowMessage = { pesan -> showMessage(pesan) },
                    getCurrentUserId = { currentUserId() },
                    onSaveSuccess = {
                        setResult(android.app.Activity.RESULT_OK)
                        showMessage("Produksi dasar berhasil disimpan.")
                        finish()
                    },
                    activityContext = this@AktivitasProduksiTahuDasar
                )
            }
        }
    }
}

// === KOMPONEN UTAMA UI ===

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BasicProductionScreen(
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    getCurrentUserId: () -> String,
    onSaveSuccess: () -> Unit,
    activityContext: AppCompatActivity
) {
    // State UI
    var daftarProdukDasar by remember { mutableStateOf<List<Produk>>(emptyList()) }
    var selectedProduk by remember { mutableStateOf<Produk?>(null) }
    var resultPerBatch by remember { mutableStateOf<Double?>(null) }

    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // Form States
    var batches by remember { mutableStateOf("") }
    var tanggal by remember { mutableStateOf(Formatter.currentDateOnly()) }
    var waktu by remember { mutableStateOf(Formatter.currentTimeOnly()) }
    var catatan by remember { mutableStateOf("") }

    // Visibility Toggles
    var showDateTime by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Warna Tema Dinamis
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF111827) else Color(0xFFF3F4F6)
    val surfaceColor = if (isDark) Color(0xFF1F2937) else Color(0xFFFFFFFF)
    val primaryColor = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
    val textColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF111827)
    val mutedColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)
    val successColor = if (isDark) Color(0xFF10B981) else Color(0xFF059669)
    val successLightColor = if (isDark) Color(0xFF064E3B) else Color(0xFFD1FAE5)

    // Perhitungan Estimasi
    val inputDouble = InputAngka.parseDouble(batches)
    val estimasi = if (resultPerBatch != null && inputDouble > 0) {
        (resultPerBatch!! * inputDouble).roundToInt()
    } else 0

    // Load Data Awal
    LaunchedEffect(Unit) {
        isLoading = true
        runCatching { RepositoriFirebaseUtama.muatProdukProduksiDasar() }
            .onSuccess { products ->
                daftarProdukDasar = products
                selectedProduk = products.firstOrNull()
                isLoading = false
                if (products.isEmpty()) {
                    onShowMessage("Belum ada produk DASAR di Firebase.")
                }
            }
            .onFailure {
                isLoading = false
                onShowMessage(it.message ?: "Gagal memuat produk dasar")
            }
    }

    // Load Parameter otomatis
    LaunchedEffect(selectedProduk) {
        val produk = selectedProduk
        if (produk != null) {
            runCatching { RepositoriFirebaseUtama.muatParameterAktif(produk.id) }
                .onSuccess { parameter ->
                    resultPerBatch = parameter?.resultPerBatch?.toDouble()
                }
                .onFailure {
                    resultPerBatch = null
                }
        } else {
            resultPerBatch = null
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
                            Text("Produksi Dasar", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text("Catat hasil masak dapur", style = MaterialTheme.typography.labelMedium, color = mutedColor)
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
        // TOMBOL MELAYANG DI BAWAH (STICKY BOTTOM BAR)
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
                    Button(
                        onClick = {
                            if (selectedProduk == null) {
                                onShowMessage("Pilih produk dasar terlebih dahulu.")
                                return@Button
                            }
                            if (inputDouble <= 0) {
                                onShowMessage("Jumlah masak harus lebih dari 0.")
                                return@Button
                            }
                            if (tanggal.isBlank() || waktu.isBlank()) {
                                onShowMessage("Tanggal dan waktu wajib diisi.")
                                return@Button
                            }

                            val produkTerpilih = selectedProduk
                            if (produkTerpilih == null) return@Button
                            val jumlahMasak = inputDouble
                            val dateTime = Formatter.isoDate(tanggal, "$waktu:00")
                            isSaving = true

                            coroutineScope.launch {
                                runCatching {
                                    RepositoriFirebaseUtama.simpanProduksiDasar(
                                        dateTime = dateTime,
                                        productId = produkTerpilih.id,
                                        batches = jumlahMasak,
                                        note = catatan,
                                        userAuthId = getCurrentUserId()
                                    )
                                }.onSuccess {
                                    isSaving = false
                                    onSaveSuccess()
                                }.onFailure { error ->
                                    isSaving = false
                                    onShowMessage(error.message ?: "Gagal menyimpan produksi. Cek produk, parameter, dan koneksi.")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isSaving && selectedProduk != null && inputDouble > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor,
                            disabledContainerColor = primaryColor.copy(alpha = 0.5f)
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Menyimpan...", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Simpan Produksi", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
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

            // KARTU INPUT UTAMA
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {

                    Text("Pilih Produk", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)

                    ProductDropdownFieldProduksiDasar(
                        label = "Produk dasar",
                        produk = daftarProdukDasar,
                        selectedProduk = selectedProduk,
                        emptyMessage = "Tidak ada produk DASAR aktif",
                        isLoading = isLoading,
                        accentColor = primaryColor,
                        textColor = textColor,
                        mutedColor = mutedColor,
                        borderColor = borderColor,
                        surfaceColor = surfaceColor,
                        onSelected = { selectedProduk = it }
                    )

                    // Info Parameter
                    selectedProduk?.let { produkTerpilih ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = primaryColor.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Info, contentDescription = "Info", tint = primaryColor, modifier = Modifier.size(20.dp))
                                val paramText = if (resultPerBatch != null) {
                                    "1x masak = ${Formatter.ribuan(resultPerBatch!!.toLong())} ${produkTerpilih.unit}"
                                } else {
                                    "Parameter belum diatur di menu master."
                                }
                                Text(paramText, color = primaryColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    HorizontalDivider(color = borderColor)

                    // Input Jumlah Masak
                    OutlinedTextField(
                        value = batches,
                        onValueChange = { batches = InputAngka.formatInputDesimal(it) },
                        label = { Text("Jumlah Masak (Batches)") },
                        placeholder = { Text("Contoh: 1,5 atau 1.000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(16.dp),
                        textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Start, color = textColor),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = borderColor,
                            focusedContainerColor = bgColor,
                            unfocusedContainerColor = bgColor,
                            focusedLabelColor = primaryColor,
                            unfocusedLabelColor = mutedColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Badge Estimasi Hasil (SUDAH DIKECILKAN)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (estimasi > 0) successLightColor else bgColor,
                        border = if (estimasi > 0) null else BorderStroke(1.dp, borderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Estimasi Hasil",
                                color = if (estimasi > 0) successColor else mutedColor,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (estimasi > 0) "${Formatter.ribuan(estimasi.toLong())} ${selectedProduk?.unit ?: ""}" else "-",
                                color = if (estimasi > 0) successColor else mutedColor,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }

            // KARTU PENGATURAN TAMBAHAN
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Pengaturan Tambahan", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        // Toggle Tanggal
                        ToggleSection(
                            title = "Atur Tanggal & Waktu",
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
                            title = "Tambah Catatan (Opsional)",
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
                                    placeholder = { Text("Tulis catatan tambahan di sini...") },
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
            Spacer(Modifier.height(80.dp))
        }
    }
}


@Composable
private fun ProductDropdownFieldProduksiDasar(
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
        "$status • Stok ${Formatter.ribuan(it.stock.toLong())} ${it.unit} • ${it.category}"
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
                title = { Text("Pilih $label", fontWeight = FontWeight.Bold, color = textColor) },
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
                                        Text(
                                            "$status • Stok ${Formatter.ribuan(item.stock.toLong())} ${item.unit} • ${item.category}",
                                            color = mutedColor,
                                            style = MaterialTheme.typography.labelMedium
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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