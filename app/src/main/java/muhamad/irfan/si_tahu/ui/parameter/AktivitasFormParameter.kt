package muhamad.irfan.si_tahu.ui.parameter

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
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PrecisionManufacturing
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Storefront
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
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.utama.SiTahuProTheme
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import muhamad.irfan.si_tahu.util.InputAngka

class AktivitasFormParameter : AktivitasDasar() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        val requestedProductId = intent.getStringExtra(EkstraAplikasi.EXTRA_PRODUCT_ID)
        val editingParameterId = intent.getStringExtra(EkstraAplikasi.EXTRA_PARAMETER_ID)

        setContent {
            SiTahuProTheme {
                ParameterFormScreen(
                    requestedProductId = requestedProductId,
                    editingParameterId = editingParameterId,
                    onNavigateBack = { finish() },
                    onShowMessage = { pesan -> showMessage(pesan) },
                    onSaveSuccess = { productId ->
                        setResult(RESULT_OK, Intent().putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, productId))
                        finish()
                    },
                    activityContext = this@AktivitasFormParameter
                )
            }
        }
    }
}

// === MODEL DATA BANTUAN ===
private data class OpsiProdukDasarForm(
    val id: String,
    val namaProduk: String,
    val satuan: String
)

// === KOMPONEN UTAMA UI ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParameterFormScreen(
    requestedProductId: String?,
    editingParameterId: String?,
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    onSaveSuccess: (String) -> Unit,
    activityContext: AppCompatActivity
) {
    val firestore = FirebaseFirestore.getInstance()
    val isEditing = !editingParameterId.isNullOrBlank()

    // State Data Master
    var products by remember { mutableStateOf<List<OpsiProdukDasarForm>>(emptyList()) }
    var selectedProductIndex by remember { mutableStateOf(0) }

    // State Form
    var hasilRaw by remember { mutableStateOf("") }
    var catatanInput by remember { mutableStateOf("") }
    var aktif by remember { mutableStateOf(true) }

    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // Tema Warna Pro
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF111827) else Color(0xFFF3F4F6)
    val surfaceColor = if (isDark) Color(0xFF1F2937) else Color(0xFFFFFFFF)
    val primaryColor = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
    val textColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF111827)
    val mutedColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)

    val hasilPerProduksi = InputAngka.parseLong(hasilRaw)

    // Fungsi Internal Bantuan
    fun parameterCollection(productId: String) = firestore.collection("Produk").document(productId).collection("parameterProduksi")

    // Load Data
    LaunchedEffect(Unit) {
        firestore.collection("Produk").whereEqualTo("jenisProduk", "DASAR").get()
            .addOnSuccessListener { snapshot ->
                products = snapshot.documents
                    .filter { it.getBoolean("dihapus") != true }
                    .map { doc ->
                        OpsiProdukDasarForm(
                            id = doc.id,
                            namaProduk = doc.getString("namaProduk").orEmpty(),
                            satuan = doc.getString("satuan").orEmpty()
                        )
                    }
                    .sortedBy { it.namaProduk.lowercase() }

                if (products.isEmpty()) {
                    onShowMessage("Belum ada produk dasar. Tambahkan produk DASAR dulu.")
                    onNavigateBack()
                    return@addOnSuccessListener
                }

                val initialIndex = if (requestedProductId != null) {
                    products.indexOfFirst { it.id == requestedProductId }.takeIf { it >= 0 } ?: 0
                } else 0
                selectedProductIndex = initialIndex

                // Load Data Jika Mode Edit
                if (isEditing && requestedProductId != null) {
                    val pId = products[initialIndex].id
                    parameterCollection(pId).document(editingParameterId!!).get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists() && doc.getBoolean("dihapus") != true) {
                                hasilRaw = InputAngka.formatInput((doc.getLong("hasilPerProduksi") ?: 0L).toString())
                                catatanInput = doc.getString("catatan").orEmpty()
                                aktif = doc.getBoolean("aktif") ?: true
                            } else {
                                onShowMessage("Parameter ini sudah dihapus atau tidak ditemukan.")
                                onNavigateBack()
                            }
                            isLoading = false
                        }
                        .addOnFailureListener { e ->
                            onShowMessage("Gagal memuat parameter: ${e.message}")
                            onNavigateBack()
                        }
                } else {
                    isLoading = false
                }
            }
            .addOnFailureListener { e ->
                onShowMessage("Gagal memuat produk: ${e.message}")
                onNavigateBack()
            }
    }

    fun saveParameter() {
        val selectedProduct = products.getOrNull(selectedProductIndex)

        if (selectedProduct == null) {
            onShowMessage("Produk dasar belum tersedia.")
            return
        }
        if (hasilPerProduksi <= 0L) {
            onShowMessage("Hasil per produksi harus lebih dari 0.")
            return
        }

        isSaving = true
        val parameterIdToSave = editingParameterId ?: "ppm_${System.currentTimeMillis()}"
        val now = Timestamp.now()

        val data = hashMapOf<String, Any?>(
            "idProduk" to selectedProduct.id,
            "namaProduk" to selectedProduct.namaProduk,
            "hasilPerProduksi" to hasilPerProduksi,
            "satuanHasil" to selectedProduct.satuan,
            "aktif" to aktif,
            "catatan" to catatanInput.trim(),
            "dihapus" to false,
            "dihapusPada" to null,
            "diperbaruiPada" to now
        )

        if (!isEditing) {
            data["dibuatPada"] = now
        }

        parameterCollection(selectedProduct.id).document(parameterIdToSave)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                if (aktif) {
                    parameterCollection(selectedProduct.id).get().addOnSuccessListener { snapshot ->
                        val activeDocs = snapshot.documents.filter { it.getBoolean("dihapus") != true && it.getBoolean("aktif") != false && it.id != parameterIdToSave }
                        val batch = firestore.batch()

                        activeDocs.forEach { doc ->
                            batch.update(doc.reference, mapOf("aktif" to false, "diperbaruiPada" to now))
                        }

                        batch.commit().addOnSuccessListener {
                            onShowMessage("Parameter berhasil disimpan dan diaktifkan.")
                            onSaveSuccess(selectedProduct.id)
                        }
                    }.addOnFailureListener {
                        onShowMessage("Parameter tersimpan, namun gagal menyinkronkan status aktif.")
                        onSaveSuccess(selectedProduct.id)
                    }
                } else {
                    onShowMessage("Parameter produksi berhasil disimpan.")
                    onSaveSuccess(selectedProduct.id)
                }
            }
            .addOnFailureListener { e ->
                isSaving = false
                onShowMessage("Gagal menyimpan parameter: ${e.message}")
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
                            Text(if (isEditing) "Edit Parameter" else "Tambah Parameter", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text("Standar hasil produksi", style = MaterialTheme.typography.labelMedium, color = mutedColor)
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
                        onClick = { saveParameter() },
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
                            Text("Simpan Parameter", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
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

                // === 1. KARTU TARGET PRODUK ===
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Category, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("Target Produk", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                                Text("Produk induk kategori DASAR", color = mutedColor, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        OutlinedTextField(
                            value = if (products.isNotEmpty()) products[selectedProductIndex].namaProduk else "Memuat produk...",
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Produk Dasar") },
                            shape = RoundedCornerShape(14.dp),
                            textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, color = textColor),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = borderColor,
                                disabledTextColor = textColor,
                                disabledLabelColor = mutedColor,
                                disabledContainerColor = bgColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // === 2. KARTU OUTPUT PRODUKSI ===
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.PrecisionManufacturing, null, tint = primaryColor, modifier = Modifier.size(24.dp))
                            }
                            Column {
                                Text("Output Produksi", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                                Text("Isi target kuantitas masak", color = mutedColor, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        val selectedUnit = if (products.isNotEmpty()) products[selectedProductIndex].satuan else ""

                        OutlinedTextField(
                            value = hasilRaw,
                            onValueChange = { input -> hasilRaw = InputAngka.formatInput(input) },
                            label = { Text("Target Hasil per Batch") },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(14.dp),
                            textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Start, color = textColor),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor, focusedContainerColor = bgColor, unfocusedContainerColor = bgColor),
                            modifier = Modifier.fillMaxWidth(),
                            suffix = { Text(selectedUnit.ifBlank { "Pcs" }, color = mutedColor, fontWeight = FontWeight.Medium) }
                        )

                        OutlinedTextField(
                            value = catatanInput,
                            onValueChange = { catatanInput = it },
                            label = { Text("Catatan / Keterangan (Opsional)") },
                            placeholder = { Text("Misal: Standar menggunakan cetakan kayu besar...") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            shape = RoundedCornerShape(14.dp),
                            textStyle = LocalTextStyle.current.copy(color = textColor),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor, focusedContainerColor = bgColor, unfocusedContainerColor = bgColor),
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        )
                    }
                }

                // === 3. KARTU PENGATURAN STATUS ===
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Storefront, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            }
                            Text("Pengaturan Prioritas", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                        }
                        HorizontalDivider(color = borderColor, modifier = Modifier.padding(bottom = 4.dp))

                        // Toggle Parameter Aktif
                        Row(
                            Modifier.fillMaxWidth().clickable { aktif = !aktif }.padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Jadikan Parameter Utama", fontWeight = FontWeight.Bold, color = textColor)
                                Text("Akan langsung digunakan saat dapur memasak produk ini", color = mutedColor, style = MaterialTheme.typography.labelMedium)
                            }
                            Switch(checked = aktif, onCheckedChange = { aktif = it }, colors = SwitchDefaults.colors(checkedTrackColor = primaryColor))
                        }
                    }
                }

                // Info Box Tambahan Pro
                Surface(shape = RoundedCornerShape(16.dp), color = primaryColor.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.Info, null, tint = primaryColor, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                        Text("Jika opsi di atas diaktifkan, maka otomatis parameter lama yang terikat dengan produk ini akan dinonaktifkan oleh sistem.", color = primaryColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2)
                    }
                }

                Spacer(Modifier.height(100.dp)) // Ruang ekstra untuk Bottom Bar
            }
        }
    }
}