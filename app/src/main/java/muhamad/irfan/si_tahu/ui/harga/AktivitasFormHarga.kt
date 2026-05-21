package muhamad.irfan.si_tahu.ui.harga

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
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Info
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

class AktivitasFormHarga : AktivitasDasar() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        val requestedProductId = intent.getStringExtra(EkstraAplikasi.EXTRA_PRODUCT_ID)
        val editingPriceId = intent.getStringExtra(EkstraAplikasi.EXTRA_PRICE_ID)

        setContent {
            SiTahuProTheme {
                PriceFormScreen(
                    requestedProductId = requestedProductId,
                    editingPriceId = editingPriceId,
                    onNavigateBack = { finish() },
                    onShowMessage = { pesan -> showMessage(pesan) },
                    onSaveSuccess = { productId ->
                        setResult(RESULT_OK, Intent().putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, productId))
                        finish()
                    },
                    activityContext = this@AktivitasFormHarga
                )
            }
        }
    }
}

// === MODEL DATA BANTUAN ===
private data class OpsiProdukHargaForm(
    val id: String,
    val name: String
)

// === KOMPONEN UTAMA UI ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceFormScreen(
    requestedProductId: String?,
    editingPriceId: String?,
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    onSaveSuccess: (String) -> Unit,
    activityContext: AppCompatActivity
) {
    val firestore = FirebaseFirestore.getInstance()
    val isEditing = !editingPriceId.isNullOrBlank()

    // State Data Master
    var products by remember { mutableStateOf<List<OpsiProdukHargaForm>>(emptyList()) }
    var selectedProductIndex by remember { mutableStateOf(0) }

    // State Form
    var kanalInput by remember { mutableStateOf("") }
    var hargaSatuanRaw by remember { mutableStateOf("") }
    var aktif by remember { mutableStateOf(true) }
    var hargaUtama by remember { mutableStateOf(false) }

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

    // Format Rupiah (Raw Text to Number)
    val hargaSatuan = InputAngka.parseLong(hargaSatuanRaw)

    // Logika Keterikatan Switch
    LaunchedEffect(aktif) {
        if (!aktif) hargaUtama = false
    }

    // Load Data
    LaunchedEffect(Unit) {
        firestore.collection("Produk").get()
            .addOnSuccessListener { snapshot ->
                products = snapshot.documents
                    .filter { it.getBoolean("dihapus") != true }
                    .map { doc -> OpsiProdukHargaForm(id = doc.id, name = doc.getString("namaProduk").orEmpty()) }
                    .sortedBy { it.name }

                if (products.isEmpty()) {
                    onShowMessage("Belum ada produk. Tambahkan produk dulu.")
                    onNavigateBack()
                    return@addOnSuccessListener
                }

                val initialIndex = if (requestedProductId != null) {
                    products.indexOfFirst { it.id == requestedProductId }.takeIf { it >= 0 } ?: 0
                } else 0
                selectedProductIndex = initialIndex

                // Load Data Jika Mode Edit
                if (isEditing) {
                    val pId = products[initialIndex].id
                    firestore.collection("Produk").document(pId).collection("hargaJual").document(editingPriceId!!).get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                kanalInput = doc.getString("kanalHarga").orEmpty().ifBlank { doc.getString("namaHarga").orEmpty() }
                                hargaSatuanRaw = InputAngka.formatInput((doc.getLong("hargaSatuan") ?: 0L).toString())
                                aktif = doc.getBoolean("aktif") ?: true
                                hargaUtama = doc.getBoolean("hargaUtama") ?: false
                            }
                            isLoading = false
                        }
                        .addOnFailureListener { e ->
                            onShowMessage("Gagal memuat harga kanal: ${e.message}")
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

    fun savePrice() {
        val selectedProduct = products.getOrNull(selectedProductIndex)
        val kanalKey = kanalInput.trim().uppercase().replace("\\s+".toRegex(), " ")

        if (selectedProduct == null) {
            onShowMessage("Produk belum tersedia.")
            return
        }
        if (kanalInput.isBlank()) {
            onShowMessage("Nama kanal (Label) wajib diisi.")
            return
        }
        if (hargaSatuan <= 0L) {
            onShowMessage("Nominal harga harus lebih dari 0.")
            return
        }

        isSaving = true
        val hargaRef = firestore.collection("Produk").document(selectedProduct.id).collection("hargaJual")

        // Cek Duplikasi Nama Kanal
        hargaRef.get().addOnSuccessListener { snapshot ->
            val duplicateExists = snapshot.documents.any {
                it.getBoolean("dihapus") != true && it.getString("kanalKey").orEmpty() == kanalKey && it.id != editingPriceId
            }

            if (duplicateExists) {
                isSaving = false
                onShowMessage("Nama kanal '$kanalInput' sudah dipakai untuk produk ini.")
                return@addOnSuccessListener
            }

            // Proses Simpan Data
            val now = Timestamp.now()
            val data = hashMapOf<String, Any?>(
                "kanalHarga" to kanalInput.trim(),
                "namaHarga" to kanalInput.trim(),
                "kanalKey" to kanalKey,
                "hargaSatuan" to hargaSatuan,
                "hargaUtama" to hargaUtama,
                "aktif" to aktif,
                "dihapus" to false,
                "dihapusPada" to null,
                "diperbaruiPada" to now
            )

            val priceIdToSave = editingPriceId ?: "hrg_${selectedProduct.id.takeLast(4)}_${System.currentTimeMillis()}"
            if (!isEditing) data["dibuatPada"] = now

            hargaRef.document(priceIdToSave).set(data, SetOptions.merge())
                .addOnSuccessListener {
                    // Logika Penetapan Default Kasir Otomatis
                    hargaRef.get().addOnSuccessListener { innerSnapshot ->
                        val activeDocs = innerSnapshot.documents.filter { it.getBoolean("dihapus") != true && it.getBoolean("aktif") != false }

                        if (activeDocs.isEmpty()) {
                            hargaRef.document(priceIdToSave).update(mapOf("aktif" to true, "hargaUtama" to true))
                                .addOnSuccessListener {
                                    onShowMessage("Setiap produk harus memiliki satu harga aktif. Harga ini ditetapkan sebagai harga utama.")
                                    onSaveSuccess(selectedProduct.id)
                                }
                            return@addOnSuccessListener
                        }

                        if (hargaUtama) {
                            val batch = firestore.batch()
                            innerSnapshot.documents.filter { it.getBoolean("dihapus") != true }.forEach { doc ->
                                if (doc.id != priceIdToSave && doc.getBoolean("hargaUtama") == true) batch.update(doc.reference, "hargaUtama", false)
                            }
                            batch.commit().addOnSuccessListener {
                                onShowMessage("Harga kanal berhasil disimpan.")
                                onSaveSuccess(selectedProduct.id)
                            }
                        } else {
                            val hasOtherPrimary = activeDocs.any { it.id != priceIdToSave && it.getBoolean("hargaUtama") == true }
                            if (!hasOtherPrimary) {
                                val fallbackDoc = activeDocs.firstOrNull { it.id == priceIdToSave } ?: activeDocs.first()
                                hargaRef.document(fallbackDoc.id).update("hargaUtama", true)
                                    .addOnSuccessListener {
                                        onShowMessage("Karena belum ada default, sistem menunjuk salah satu harga sebagai Harga Utama Kasir.")
                                        onSaveSuccess(selectedProduct.id)
                                    }
                            } else {
                                onShowMessage("Harga kanal berhasil disimpan.")
                                onSaveSuccess(selectedProduct.id)
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    isSaving = false
                    onShowMessage("Gagal menyimpan harga: ${it.message}")
                }
        }.addOnFailureListener {
            isSaving = false
            onShowMessage("Gagal mengecek nama kanal: ${it.message}")
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
                            Text(if (isEditing) "Edit Harga Kanal" else "Tambah Harga Kanal", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text("Atur variasi harga produk", style = MaterialTheme.typography.labelMedium, color = mutedColor)
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
                        onClick = { savePrice() },
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
                            Text("Simpan Harga", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
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
                                Text("Produk Tujuan", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                                Text("Produk induk yang diatur harganya", color = mutedColor, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        OutlinedTextField(
                            value = if (products.isNotEmpty()) products[selectedProductIndex].name else "Memuat produk...",
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Produk") },
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

                // === 2. KARTU DETAIL HARGA ===
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.AttachMoney, null, tint = primaryColor, modifier = Modifier.size(24.dp))
                            }
                            Column {
                                Text("Detail Harga Jual", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                                Text("Isi kanal penjualan dan nominal harga", color = mutedColor, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        OutlinedTextField(
                            value = kanalInput,
                            onValueChange = { kanalInput = it },
                            label = { Text("Nama Kanal Penjualan") },
                            placeholder = { Text("Contoh: Kasir, Pasar, GoFood, GrabFood") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(14.dp),
                            textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Medium, color = textColor),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor, focusedContainerColor = bgColor, unfocusedContainerColor = bgColor),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = hargaSatuanRaw,
                            onValueChange = { input -> hargaSatuanRaw = InputAngka.formatInput(input) },
                            label = { Text("Harga Satuan") },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            shape = RoundedCornerShape(14.dp),
                            textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Start, color = textColor),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor, focusedContainerColor = bgColor, unfocusedContainerColor = bgColor),
                            modifier = Modifier.fillMaxWidth(),
                            prefix = { Text("Rp ", color = mutedColor, fontWeight = FontWeight.SemiBold) }
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

                        // Toggle Harga Aktif
                        Row(
                            Modifier.fillMaxWidth().clickable { aktif = !aktif }.padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Harga Aktif", fontWeight = FontWeight.Bold, color = textColor)
                                Text("Aktifkan agar harga ini bisa digunakan saat transaksi", color = mutedColor, style = MaterialTheme.typography.labelMedium)
                            }
                            Switch(checked = aktif, onCheckedChange = { aktif = it }, colors = SwitchDefaults.colors(checkedTrackColor = primaryColor))
                        }

                        HorizontalDivider(color = borderColor)

                        // Toggle Default Kasir
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = aktif) { if (aktif) hargaUtama = !hargaUtama }.padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Jadikan Harga Utama Kasir", fontWeight = FontWeight.Bold, color = if (aktif) textColor else mutedColor)
                                Text("Harga ini otomatis dipakai saat transaksi kasir dibuat", color = mutedColor, style = MaterialTheme.typography.labelMedium)
                            }
                            Switch(checked = hargaUtama, onCheckedChange = { hargaUtama = it }, enabled = aktif, colors = SwitchDefaults.colors(checkedTrackColor = primaryColor))
                        }
                    }
                }

                // Info Box Tambahan Pro
                Surface(shape = RoundedCornerShape(16.dp), color = primaryColor.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.Info, null, tint = primaryColor, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                        Text("Setiap produk harus memiliki minimal satu harga aktif agar dapat dijual di kasir.", color = primaryColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2)
                    }
                }

                Spacer(Modifier.height(100.dp)) // Ruang ekstra untuk Bottom Bar
            }
        }
    }
}