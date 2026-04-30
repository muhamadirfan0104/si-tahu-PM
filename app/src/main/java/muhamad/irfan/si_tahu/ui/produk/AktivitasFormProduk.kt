package muhamad.irfan.si_tahu.ui.produk

import android.os.Bundle
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.utama.SiTahuProTheme
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import muhamad.irfan.si_tahu.util.InputAngka

class AktivitasFormProduk : AktivitasDasar() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        val editingProductId = intent.getStringExtra(EkstraAplikasi.EXTRA_PRODUCT_ID)

        setContent {
            SiTahuProTheme {
                ProductFormScreen(
                    editingProductId = editingProductId,
                    onNavigateBack = { finish() },
                    onShowMessage = { pesan -> showMessage(pesan) },
                    onSaveSuccess = {
                        setResult(RESULT_OK)
                        finish()
                    },
                    onShowConfirmation = { title, message, confirmLabel, action ->
                        showConfirmationModal(title, message, confirmLabel, action)
                    }
                )
            }
        }
    }
}

// === KOMPONEN UTAMA UI ===

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductFormScreen(
    editingProductId: String?,
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    onSaveSuccess: () -> Unit,
    onShowConfirmation: (String, String, String, () -> Unit) -> Unit
) {
    val firestore = FirebaseFirestore.getInstance()
    val isEditing = !editingProductId.isNullOrBlank()

    // Kategori
    val categories = listOf("DASAR", "OLAHAN")
    var existingKodeProduk by remember { mutableStateOf<String?>(null) }

    // State Form: Info Dasar
    var namaProduk by remember { mutableStateOf("") }
    var jenisProdukIndex by remember { mutableStateOf(0) }
    var satuan by remember { mutableStateOf("") }
    var showCategoryDropdown by remember { mutableStateOf(false) }

    // State Form: Stok & Kadaluarsa
    var stokMinimum by remember { mutableStateOf("") }
    var masaSimpanHari by remember { mutableStateOf("2") }
    var hariHampirKadaluarsa by remember { mutableStateOf("1") }

    // State Form: Visibilitas
    var aktifDijual by remember { mutableStateOf(true) }
    var tampilDiKasir by remember { mutableStateOf(true) }

    var isLoading by remember { mutableStateOf(isEditing) }
    var isSaving by remember { mutableStateOf(false) }

    // Tema Warna Pro
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF111827) else Color(0xFFF3F4F6)
    val surfaceColor = if (isDark) Color(0xFF1F2937) else Color(0xFFFFFFFF)
    val primaryColor = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
    val textColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF111827)
    val mutedColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)
    val dangerColor = if (isDark) Color(0xFFEF4444) else Color(0xFFDC2626)

    // Logika Keterikatan Switch
    LaunchedEffect(aktifDijual) {
        if (!aktifDijual) tampilDiKasir = false
    }

    // Load Data jika mode Edit
    LaunchedEffect(editingProductId) {
        if (editingProductId != null) {
            firestore.collection("Produk").document(editingProductId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        existingKodeProduk = doc.getString("kodeProduk").orEmpty()
                        namaProduk = doc.getString("namaProduk").orEmpty()

                        val loadedJenis = doc.getString("jenisProduk").orEmpty()
                        jenisProdukIndex = categories.indexOf(loadedJenis).takeIf { it >= 0 } ?: 0

                        satuan = doc.getString("satuan").orEmpty()
                        stokMinimum = InputAngka.formatInput((doc.getLong("stokMinimum") ?: 0L).toString())
                        masaSimpanHari = InputAngka.formatInput((doc.getLong("masaSimpanHari") ?: 2L).toString())
                        hariHampirKadaluarsa = InputAngka.formatInput((doc.getLong("hariHampirKadaluarsa") ?: 1L).toString())

                        aktifDijual = doc.getBoolean("aktifDijual") ?: true
                        tampilDiKasir = doc.getBoolean("tampilDiKasir") ?: true
                    } else {
                        onShowMessage("Data produk tidak ditemukan.")
                        onNavigateBack()
                    }
                    isLoading = false
                }
                .addOnFailureListener { e ->
                    onShowMessage("Gagal memuat produk: ${e.message}")
                    onNavigateBack()
                }
        }
    }

    fun saveProduct() {
        val namaClean = namaProduk.trim()
        val satuanClean = satuan.trim()

        if (namaClean.isBlank()) {
            onShowMessage("Nama produk wajib diisi")
            return
        }
        if (satuanClean.isBlank()) {
            onShowMessage("Satuan wajib diisi")
            return
        }

        isSaving = true
        val minStokVal = InputAngka.parseLong(stokMinimum)
        val masaSimpanVal = InputAngka.parseLong(masaSimpanHari).takeIf { it > 0L } ?: 2L
        val hampirExpVal = InputAngka.parseLong(hariHampirKadaluarsa).coerceAtLeast(0L).coerceAtMost(masaSimpanVal)
        val jenisTerpilih = categories[jenisProdukIndex]

        val now = Timestamp.now()
        val finalTampilDiKasir = if (aktifDijual) tampilDiKasir else false

        val persistData = { pId: String, pKode: String ->
            val data = mutableMapOf<String, Any?>(
                "kodeProduk" to pKode,
                "namaProduk" to namaClean,
                "jenisProduk" to jenisTerpilih,
                "satuan" to satuanClean,
                "stokMinimum" to minStokVal,
                "masaSimpanHari" to masaSimpanVal,
                "hariHampirKadaluarsa" to hampirExpVal,
                "tampilDiKasir" to finalTampilDiKasir,
                "aktifDijual" to aktifDijual,
                "urlFoto" to "",
                "dihapus" to false,
                "dihapusPada" to null,
                "diperbaruiPada" to now
            )

            if (!isEditing) {
                data["stokSaatIni"] = 0L
                data["dibuatPada"] = now
            }

            val docRef = firestore.collection("Produk").document(pId)
            val task = if (isEditing) docRef.set(data, SetOptions.merge()) else docRef.set(data)

            task.addOnSuccessListener {
                isSaving = false
                onShowMessage("Produk berhasil disimpan.")
                onSaveSuccess()
            }.addOnFailureListener { e ->
                isSaving = false
                onShowMessage("Gagal menyimpan produk: ${e.message}")
            }
        }

        if (!isEditing) {
            val suffix = System.currentTimeMillis().toString().takeLast(6)
            val newId = "prd_${System.currentTimeMillis()}"
            val newKode = "PRD$suffix"
            persistData(newId, newKode)
        } else {
            val finalCode = existingKodeProduk.orEmpty().ifBlank { "PRD${System.currentTimeMillis().toString().takeLast(6)}" }
            persistData(editingProductId!!, finalCode)
        }
    }

    fun deleteProduct() {
        if (isEditing) {
            onShowConfirmation(
                "Hapus produk?",
                "Produk $namaProduk akan dinonaktifkan dan dihapus dari daftar aktif.",
                "Hapus"
            ) {
                isSaving = true
                val now = Timestamp.now()
                firestore.collection("Produk").document(editingProductId!!)
                    .update(
                        mapOf(
                            "dihapus" to true,
                            "aktifDijual" to false,
                            "tampilDiKasir" to false,
                            "dihapusPada" to now,
                            "diperbaruiPada" to now
                        )
                    )
                    .addOnSuccessListener {
                        isSaving = false
                        onShowMessage("Produk berhasil dihapus.")
                        onSaveSuccess()
                    }
                    .addOnFailureListener { e ->
                        isSaving = false
                        onShowMessage("Gagal menghapus produk: ${e.message}")
                    }
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
                            Text(if (isEditing) "Edit Produk" else "Tambah Produk", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text("Manajemen data master", style = MaterialTheme.typography.labelMedium, color = mutedColor)
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
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp)) {
                    Button(
                        onClick = { saveProduct() },
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
                            Text("Simpan Produk", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
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

                // === 1. KARTU INFORMASI DASAR ===
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
                                Text("Informasi Dasar", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                                Text("Nama dan jenis produk", color = mutedColor, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        OutlinedTextField(
                            value = namaProduk,
                            onValueChange = { namaProduk = it },
                            label = { Text("Nama Produk") },
                            placeholder = { Text("Misal: Tahu Putih Besar") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(14.dp),
                            textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Medium, color = textColor),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor, focusedContainerColor = bgColor, unfocusedContainerColor = bgColor),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Dropdown Kategori
                            Box(Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = categories[jenisProdukIndex],
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Kategori") },
                                    trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, null, tint = primaryColor) },
                                    shape = RoundedCornerShape(14.dp),
                                    textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, color = textColor),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor, focusedContainerColor = bgColor, unfocusedContainerColor = bgColor),
                                    modifier = Modifier.fillMaxWidth().clickable { showCategoryDropdown = true }
                                )
                                DropdownMenu(expanded = showCategoryDropdown, onDismissRequest = { showCategoryDropdown = false }, modifier = Modifier.background(surfaceColor)) {
                                    categories.forEachIndexed { index, cat ->
                                        DropdownMenuItem(text = { Text(cat) }, onClick = { jenisProdukIndex = index; showCategoryDropdown = false })
                                    }
                                }
                                Spacer(Modifier.matchParentSize().background(Color.Transparent).clickable { showCategoryDropdown = true })
                            }

                            // Input Satuan
                            OutlinedTextField(
                                value = satuan,
                                onValueChange = { satuan = it },
                                label = { Text("Satuan") },
                                placeholder = { Text("Pcs / Kg") },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                shape = RoundedCornerShape(14.dp),
                                textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Medium, color = textColor),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor, focusedContainerColor = bgColor, unfocusedContainerColor = bgColor),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // === 2. KARTU STOK & KADALUARSA ===
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Settings, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("Aturan Stok & ED", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                                Text("Batas minimum dan kadaluarsa", color = mutedColor, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        OutlinedTextField(
                            value = stokMinimum,
                            onValueChange = { stokMinimum = InputAngka.formatInput(it) },
                            label = { Text("Peringatan Stok Minimum") },
                            placeholder = { Text("Misal: 50") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(14.dp),
                            textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Medium, color = textColor),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor, focusedContainerColor = bgColor, unfocusedContainerColor = bgColor),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = masaSimpanHari,
                                onValueChange = { masaSimpanHari = InputAngka.formatInput(it) },
                                label = { Text("Masa Simpan") },
                                suffix = { Text("Hari", color = mutedColor) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                shape = RoundedCornerShape(14.dp),
                                textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, color = textColor),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor, focusedContainerColor = bgColor, unfocusedContainerColor = bgColor),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = hariHampirKadaluarsa,
                                onValueChange = { hariHampirKadaluarsa = InputAngka.formatInput(it) },
                                label = { Text("Warning Exp") },
                                suffix = { Text("Hari Sisa", color = mutedColor) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                shape = RoundedCornerShape(14.dp),
                                textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, color = textColor),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor, focusedContainerColor = bgColor, unfocusedContainerColor = bgColor),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Surface(shape = RoundedCornerShape(16.dp), color = primaryColor.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Rounded.Info, null, tint = primaryColor, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                                Text("Barang otomatis berstatus 'Hampir Kadaluarsa' ketika sisa hari mencapai angka warning di atas.", color = primaryColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2)
                            }
                        }
                    }
                }

                // === 3. KARTU VISIBILITAS ===
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
                            Text("Visibilitas Sistem", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                        }
                        HorizontalDivider(color = borderColor, modifier = Modifier.padding(bottom = 4.dp))

                        // Toggle Aktif Dijual
                        Row(
                            Modifier.fillMaxWidth().clickable { aktifDijual = !aktifDijual }.padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Produk Aktif", fontWeight = FontWeight.Bold, color = textColor)
                                Text("Bisa diproduksi dan tampil di harga", color = mutedColor, style = MaterialTheme.typography.labelMedium)
                            }
                            Switch(checked = aktifDijual, onCheckedChange = { aktifDijual = it }, colors = SwitchDefaults.colors(checkedTrackColor = primaryColor))
                        }

                        HorizontalDivider(color = borderColor)

                        // Toggle Kasir
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = aktifDijual) { if (aktifDijual) tampilDiKasir = !tampilDiKasir }.padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Tampil di Kasir", fontWeight = FontWeight.Bold, color = if (aktifDijual) textColor else mutedColor)
                                Text("Shortcut tombol cepat di menu Kasir", color = mutedColor, style = MaterialTheme.typography.labelMedium)
                            }
                            Switch(checked = tampilDiKasir, onCheckedChange = { tampilDiKasir = it }, enabled = aktifDijual, colors = SwitchDefaults.colors(checkedTrackColor = primaryColor))
                        }
                    }
                }

                // === 4. TOMBOL HAPUS (Hanya muncul saat edit) ===
                if (isEditing) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { deleteProduct() },
                        shape = RoundedCornerShape(16.dp),
                        color = dangerColor.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, dangerColor.copy(alpha = 0.3f))
                    ) {
                        Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.DeleteForever, null, tint = dangerColor, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Hapus Produk Ini", fontWeight = FontWeight.Bold, color = dangerColor, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                Spacer(Modifier.height(100.dp)) // Ruang ekstra untuk Bottom Bar
            }
        }
    }
}