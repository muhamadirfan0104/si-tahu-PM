package muhamad.irfan.si_tahu.ui.pengaturan

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Storefront
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.utama.SiTahuProTheme

class AktivitasPengaturanUsaha : AktivitasDasar() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        setContent {
            SiTahuProTheme {
                BusinessSettingsScreen(
                    onNavigateBack = { finish() },
                    onShowMessage = { pesan -> showMessage(pesan) },
                    activityContext = this@AktivitasPengaturanUsaha
                )
            }
        }
    }
}

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
private fun BusinessSettingsScreen(
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    activityContext: AppCompatActivity
) {
    val firestore = FirebaseFirestore.getInstance()
    val docRef = firestore.collection("Pengaturan").document("umum")

    // State Form
    var namaUsaha by remember { mutableStateOf("") }
    var alamat by remember { mutableStateOf("") }
    var nomorTelepon by remember { mutableStateOf("") }
    var logoText by remember { mutableStateOf("") }
    var footerStruk by remember { mutableStateOf("") }
    var catatanUsaha by remember { mutableStateOf("") }

    // State Proses
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

    // Load Data
    LaunchedEffect(Unit) {
        docRef.get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    namaUsaha = doc.getString("namaTampilanToko").orEmpty().ifBlank { doc.getString("namaUsaha").orEmpty() }
                    alamat = doc.getString("alamatToko").orEmpty().ifBlank { doc.getString("alamat").orEmpty() }
                    nomorTelepon = doc.getString("nomorTelepon").orEmpty()
                    logoText = doc.getString("teksLogo").orEmpty().ifBlank { doc.getString("logoText").orEmpty() }
                    footerStruk = doc.getString("footerStruk").orEmpty()
                    catatanUsaha = doc.getString("namaPemilik").orEmpty().ifBlank { doc.getString("catatanUsaha").orEmpty() }
                }
                isLoading = false
            }
            .addOnFailureListener { e ->
                isLoading = false
                onShowMessage("Gagal memuat pengaturan: ${e.message}")
            }
    }

    // Fungsi Simpan
    fun saveBusinessSettings() {
        val namaBersih = namaUsaha.trim()
        val alamatBersih = alamat.trim()
        val logoBersih = logoText.trim()

        if (namaBersih.isBlank()) {
            onShowMessage("Nama usaha wajib diisi.")
            return
        }
        if (alamatBersih.isBlank()) {
            onShowMessage("Alamat wajib diisi.")
            return
        }
        if (logoBersih.length > 8) {
            onShowMessage("Teks Logo maksimal 8 karakter.")
            return
        }

        isSaving = true

        val now = Timestamp.now()
        val data = hashMapOf<String, Any>(
            "namaTampilanToko" to namaBersih,
            "namaPemilik" to catatanUsaha.trim(),
            "alamatToko" to alamatBersih,
            "nomorTelepon" to nomorTelepon.trim(),
            "teksLogo" to logoBersih,
            "footerStruk" to footerStruk.trim(),
            "aktif" to true,
            "diperbaruiPada" to now
        )

        docRef.set(data)
            .addOnSuccessListener {
                isSaving = false
                onShowMessage("Pengaturan usaha berhasil disimpan.")
            }
            .addOnFailureListener { e ->
                isSaving = false
                onShowMessage("Gagal menyimpan pengaturan: ${e.message}")
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
                            Text("Pengaturan Usaha", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text("Kelola identitas usaha", style = MaterialTheme.typography.labelMedium, color = mutedColor)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Rounded.ArrowBack, "Kembali", tint = textColor) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        bottomBar = {
            if (!isLoading) {
                Surface(
                    color = surfaceColor,
                    shadowElevation = 16.dp,
                    border = BorderStroke(1.dp, borderColor),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
                        Button(
                            onClick = { saveBusinessSettings() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text("Menyimpan...", fontWeight = FontWeight.Bold, color = Color.White)
                            } else {
                                Icon(Icons.Rounded.Save, null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Simpan Pengaturan", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        },
        containerColor = bgColor
    ) { paddingValues ->
        if (isLoading) {
            // Tampilan Loading Skeleton
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(20.dp)).adminShimmerEffect())
                repeat(2) {
                    Card(
                        shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor),
                        border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(40.dp).clip(CircleShape).adminShimmerEffect())
                                Box(Modifier.height(20.dp).fillMaxWidth(0.5f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                            }
                            HorizontalDivider(color = borderColor)
                            Box(Modifier.height(56.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).adminShimmerEffect())
                            Box(Modifier.height(56.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).adminShimmerEffect())
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // === KARTU LIVE PREVIEW ===
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = primaryColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Lingkaran Logo
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (logoText.isNotBlank()) logoText.uppercase() else "LT",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }

                        // Nama dan Alamat Usaha
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (namaUsaha.isNotBlank()) namaUsaha else "Nama Usaha",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = if (alamat.isNotBlank()) alamat else "Alamat usaha belum diatur",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // === 1. KARTU IDENTITAS UTAMA ===
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Storefront, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            }
                            Text("Identitas Utama", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                        }

                        HorizontalDivider(color = borderColor)

                        OutlinedTextField(
                            value = namaUsaha,
                            onValueChange = { namaUsaha = it },
                            label = { Text("Nama Usaha", color = mutedColor) },
                            placeholder = { Text("Misal: Tahu Maju Jaya") },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor,
                                focusedContainerColor = bgColor, unfocusedContainerColor = bgColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = logoText,
                                onValueChange = { if (it.length <= 8) logoText = it },
                                label = { Text("Teks Logo", color = mutedColor) },
                                placeholder = { Text("Maks 8") },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor,
                                    focusedContainerColor = bgColor, unfocusedContainerColor = bgColor
                                ),
                                modifier = Modifier.weight(0.4f)
                            )

                            OutlinedTextField(
                                value = nomorTelepon,
                                onValueChange = { nomorTelepon = it.filter { char -> char.isDigit() || char == '+' } },
                                label = { Text("Nomor Telepon", color = mutedColor) },
                                placeholder = { Text("0812xxxxxx") },
                                leadingIcon = { Icon(Icons.Rounded.Call, null, tint = mutedColor) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor,
                                    focusedContainerColor = bgColor, unfocusedContainerColor = bgColor
                                ),
                                modifier = Modifier.weight(0.6f)
                            )
                        }
                    }
                }

                // === 2. KARTU LOKASI & STRUK ===
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.LocationOn, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            }
                            Text("Lokasi & Nota", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                        }

                        HorizontalDivider(color = borderColor)

                        OutlinedTextField(
                            value = alamat,
                            onValueChange = { alamat = it },
                            label = { Text("Alamat Lengkap", color = mutedColor) },
                            placeholder = { Text("Jalan, RT/RW, Kota...") },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(14.dp),
                            minLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor,
                                focusedContainerColor = bgColor, unfocusedContainerColor = bgColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = footerStruk,
                            onValueChange = { footerStruk = it },
                            label = { Text("Pesan Penutup Nota (Footer)", color = mutedColor) },
                            placeholder = { Text("Misal: Terima kasih telah berbelanja.") },
                            leadingIcon = { Icon(Icons.Rounded.ReceiptLong, null, tint = mutedColor) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor,
                                focusedContainerColor = bgColor, unfocusedContainerColor = bgColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // === 3. KARTU INFORMASI TAMBAHAN ===
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Business, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            }
                            Text("Informasi Tambahan", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                        }

                        HorizontalDivider(color = borderColor)

                        OutlinedTextField(
                            value = catatanUsaha,
                            onValueChange = { catatanUsaha = it },
                            label = { Text("Nama Pemilik / Catatan Internal", color = mutedColor) },
                            placeholder = { Text("Info tambahan tentang usaha") },
                            leadingIcon = { Icon(Icons.Rounded.Info, null, tint = mutedColor) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor,
                                focusedContainerColor = bgColor, unfocusedContainerColor = bgColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(100.dp)) // Ruang ekstra untuk Bottom Bar
            }
        }
    }
}