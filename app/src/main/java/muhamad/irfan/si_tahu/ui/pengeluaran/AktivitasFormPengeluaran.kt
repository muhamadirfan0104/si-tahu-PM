package muhamad.irfan.si_tahu.ui.pengeluaran

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.utama.SiTahuProTheme
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.InputAngka
import muhamad.irfan.si_tahu.util.PembantuPilihTanggalWaktu

class AktivitasFormPengeluaran : AktivitasDasar() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        val existingId = intent.getStringExtra(EXTRA_EXPENSE_ID)

        setContent {
            SiTahuProTheme {
                ExpenseFormScreen(
                    existingId = existingId,
                    onNavigateBack = { finish() },
                    onShowMessage = { pesan -> showMessage(pesan) },
                    getCurrentUserId = { currentUserId() },
                    onSaveSuccess = { detail ->
                        setResult(RESULT_OK)
                        showReceiptModal(
                            title = "Pengeluaran Tersimpan",
                            receiptText = detail,
                            pdfLabel = "Simpan PDF"
                        ) {
                            finish()
                        }
                    },
                    activityContext = this@AktivitasFormPengeluaran
                )
            }
        }
    }

    companion object {
        const val EXTRA_EXPENSE_ID = "extra_expense_id"
    }
}

// === KOMPONEN UTAMA UI ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseFormScreen(
    existingId: String?,
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    getCurrentUserId: () -> String,
    onSaveSuccess: (String) -> Unit,
    activityContext: AppCompatActivity
) {
    val isEditing = !existingId.isNullOrBlank()
    val coroutineScope = rememberCoroutineScope()

    // State Form
    var tanggal by remember { mutableStateOf(Formatter.currentDateOnly()) }
    var waktu by remember { mutableStateOf(Formatter.currentTimeOnly()) }
    var namaPengeluaran by remember { mutableStateOf("") }
    var nominalRaw by remember { mutableStateOf("") }
    var catatan by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(isEditing) }
    var isSaving by remember { mutableStateOf(false) }

    // Format Rupiah Realtime (Raw Text ke Number)
    val nominal = InputAngka.parseLong(nominalRaw)

    // Tema Warna Pro
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF111827) else Color(0xFFF3F4F6)
    val surfaceColor = if (isDark) Color(0xFF1F2937) else Color(0xFFFFFFFF)
    val primaryColor = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
    val textColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF111827)
    val mutedColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)

    // Load Data Jika Edit
    LaunchedEffect(existingId) {
        if (isEditing && existingId != null) {
            coroutineScope.launch {
                runCatching { RepositoriFirebaseUtama.muatPengeluaranById(existingId) }
                    .onSuccess { item ->
                        if (item == null) {
                            onShowMessage("Data pengeluaran tidak ditemukan.")
                            onNavigateBack()
                            return@onSuccess
                        }

                        // Ekstrak Tanggal dan Waktu dari format yang tersimpan
                        tanggal = Formatter.toDateOnly(item.date)
                        try {
                            val splitDate = item.date.split("T")
                            if (splitDate.size > 1) {
                                waktu = splitDate[1].take(5) // Mengambil "HH:mm"
                            }
                        } catch (e: Exception) {
                            // Abaikan jika format bukan ISO, fallback ke waktu saat ini
                        }

                        namaPengeluaran = item.category
                        nominalRaw = InputAngka.formatInput(item.amount.toString())
                        catatan = item.note
                        isLoading = false
                    }
                    .onFailure {
                        onShowMessage("Gagal memuat pengeluaran: ${it.message}")
                        onNavigateBack()
                    }
            }
        }
    }

    // Fungsi Simpan Terstruktur
    fun saveExpense() {
        val namaPengeluaranBersih = namaPengeluaran.trim()
        val tanggalBersih = tanggal.trim()
        val catatanBersih = catatan.trim()

        if (tanggalBersih.isBlank() || waktu.isBlank()) {
            onShowMessage("Tanggal dan jam wajib diisi.")
            return
        }
        if (namaPengeluaranBersih.isBlank()) {
            onShowMessage("Nama pengeluaran wajib diisi.")
            return
        }
        if (nominal <= 0L) {
            onShowMessage("Nominal pengeluaran harus lebih dari 0.")
            return
        }

        isSaving = true
        coroutineScope.launch {
            runCatching {
                val newId = RepositoriFirebaseUtama.simpanPengeluaran(
                    existingId = existingId,
                    dateOnly = tanggalBersih,
                    timeOnly = waktu,
                    category = namaPengeluaranBersih,
                    amount = nominal,
                    note = catatanBersih,
                    userAuthId = getCurrentUserId()
                )
                RepositoriFirebaseUtama.buildExpenseDetailText(newId)
            }
                .onSuccess { detailText ->
                    isSaving = false
                    onSaveSuccess(detailText.replace("Catatan:", "Alasan:").replace("catatan:", "alasan:"))
                }
                .onFailure { e ->
                    isSaving = false
                    onShowMessage(e.message ?: "Gagal menyimpan pengeluaran.")
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
                            Text(if (isEditing) "Edit Pengeluaran" else "Input Pengeluaran", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text("Catat biaya operasional usaha", style = MaterialTheme.typography.labelMedium, color = mutedColor)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Rounded.ArrowBack, "Kembali", tint = textColor) } },
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
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp)) {
                    Button(
                        onClick = { saveExpense() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isSaving && !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Menyimpan...", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        } else {
                            Icon(Icons.Rounded.Save, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isEditing) "Update Pengeluaran" else "Simpan Pengeluaran", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        },
        containerColor = bgColor
    ) { paddingValues ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = primaryColor) }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {

                // === KARTU 1: DETAIL PENGELUARAN ===
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.ReceiptLong, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("Detail Pengeluaran", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                                Text("Nama dan nominal biaya", color = mutedColor, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        OutlinedTextField(
                            value = namaPengeluaran,
                            onValueChange = { namaPengeluaran = it },
                            label = { Text("Nama / Jenis Pengeluaran") },
                            placeholder = { Text("Misal: Listrik, Gaji, Bahan Baku") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(14.dp),
                            textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Medium, color = textColor),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor, focusedContainerColor = bgColor, unfocusedContainerColor = bgColor),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = nominalRaw,
                            onValueChange = { input -> nominalRaw = InputAngka.formatInput(input) },
                            label = { Text("Nominal Pengeluaran") },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            shape = RoundedCornerShape(14.dp),
                            textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Start, color = textColor),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor, focusedContainerColor = bgColor, unfocusedContainerColor = bgColor),
                            modifier = Modifier.fillMaxWidth(),
                            prefix = { Text("Rp ", color = mutedColor, fontWeight = FontWeight.Medium) }
                        )
                    }
                }

                // === KARTU 2: WAKTU & CATATAN ===
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.DateRange, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("Waktu & Catatan", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                                Text("Tanggal transaksi dan keterangan", color = mutedColor, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        // Baris Tanggal dan Jam menggunakan overlay transparan agar mudah di-klik
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = tanggal,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = false,
                                    label = { Text("Tanggal") },
                                    shape = RoundedCornerShape(14.dp),
                                    textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, color = textColor),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = textColor,
                                        disabledBorderColor = borderColor,
                                        disabledLabelColor = mutedColor,
                                        disabledContainerColor = bgColor
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Box(modifier = Modifier
                                    .matchParentSize()
                                    .clickable {
                                        PembantuPilihTanggalWaktu.showDatePicker(activityContext, tanggal) { tanggal = it }
                                    }
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = waktu,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = false,
                                    label = { Text("Jam") },
                                    shape = RoundedCornerShape(14.dp),
                                    textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, color = textColor),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = textColor,
                                        disabledBorderColor = borderColor,
                                        disabledLabelColor = mutedColor,
                                        disabledContainerColor = bgColor
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Box(modifier = Modifier
                                    .matchParentSize()
                                    .clickable {
                                        val iso = Formatter.isoDate(tanggal, "$waktu:00")
                                        PembantuPilihTanggalWaktu.showTimePicker(activityContext, iso) { waktu = it }
                                    }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = catatan,
                            onValueChange = { catatan = it },
                            label = { Text("Catatan Tambahan (Opsional)") },
                            placeholder = { Text("Misal: Pembelian dari toko Bintang...") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            shape = RoundedCornerShape(14.dp),
                            textStyle = LocalTextStyle.current.copy(color = textColor),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor, focusedContainerColor = bgColor, unfocusedContainerColor = bgColor),
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        )
                    }
                }

                Spacer(Modifier.height(100.dp)) // Ruang ekstra untuk Bottom Bar
            }
        }
    }
}