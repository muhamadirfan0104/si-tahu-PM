package muhamad.irfan.si_tahu.ui.pengguna

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.utama.SiTahuProTheme
import muhamad.irfan.si_tahu.util.EkstraAplikasi

class AktivitasDaftarPengguna : AktivitasDasar() {

    private val refreshOnResume = MutableStateFlow(0)

    override fun onResume() {
        super.onResume()
        refreshOnResume.value++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        setContent {
            SiTahuProTheme {
                val autoRefreshTrigger by refreshOnResume.collectAsState()
                UserListScreen(
                    autoRefreshTrigger = autoRefreshTrigger,
                    onNavigateBack = { finish() },
                    onShowMessage = { pesan -> showMessage(pesan) },
                    onNavigateToForm = { userId ->
                        val intent = Intent(this, AktivitasFormPengguna::class.java)
                        if (userId != null) intent.putExtra(EkstraAplikasi.EXTRA_USER_ID, userId)
                        startActivity(intent)
                    },
                    onShowConfirmation = { title, message, confirmLabel, action ->
                        showConfirmationModal(title, message, confirmLabel, action)
                    }
                )
            }
        }
    }
}

// === MODEL DATA BANTUAN UNTUK COMPOSE ===
private data class DataBarisPengguna(
    val id: String,
    val namaPengguna: String,
    val email: String,
    val nomorTelepon: String,
    val peranAsli: String,
    val aktif: Boolean,
    val dibuatPadaMillis: Long
)

// === KOMPONEN UTAMA UI ===
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun UserListScreen(
    autoRefreshTrigger: Int,
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    onNavigateToForm: (String?) -> Unit,
    onShowConfirmation: (String, String, String, () -> Unit) -> Unit
) {
    val firestore = FirebaseFirestore.getInstance()

    // State Data
    var users by remember { mutableStateOf<List<DataBarisPengguna>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var triggerRefresh by remember { mutableStateOf(0) }

    // State Pencarian & Filter
    var searchQuery by remember { mutableStateOf("") }
    val roleOptions = listOf("Semua", "ADMIN", "KASIR")
    var roleAktif by remember { mutableStateOf(roleOptions.first()) }
    val statusOptions = listOf("Semua", "Aktif", "Nonaktif")
    var statusAktif by remember { mutableStateOf(statusOptions.first()) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var halamanSaatIni by remember { mutableStateOf(1) }
    val itemKecilPerHalaman = 15

    // Tema Warna Pro
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF111827) else Color(0xFFF3F4F6)
    val surfaceColor = if (isDark) Color(0xFF1F2937) else Color(0xFFFFFFFF)
    val primaryColor = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
    val textColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF111827)
    val mutedColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)

    val successColor = if (isDark) Color(0xFF10B981) else Color(0xFF059669)
    val dangerColor = if (isDark) Color(0xFFEF4444) else Color(0xFFDC2626)
    val warningColor = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)

    // Load Data
    LaunchedEffect(triggerRefresh, autoRefreshTrigger) {
        isLoading = true
        firestore.collection("Pengguna").get()
            .addOnSuccessListener { snapshot ->
                users = snapshot.documents
                    .filter { it.getBoolean("dihapus") != true }
                    .map { doc ->
                        DataBarisPengguna(
                            id = doc.id,
                            namaPengguna = doc.getString("namaPengguna").orEmpty(),
                            email = doc.getString("email").orEmpty(),
                            nomorTelepon = doc.getString("nomorTelepon").orEmpty(),
                            peranAsli = doc.getString("peranAsli").orEmpty(),
                            aktif = doc.getBoolean("aktif") ?: true,
                            dibuatPadaMillis = doc.getTimestamp("dibuatPada")?.toDate()?.time ?: 0L
                        )
                    }.sortedByDescending { it.dibuatPadaMillis }
                isLoading = false
            }
            .addOnFailureListener { e ->
                users = emptyList()
                isLoading = false
                onShowMessage("Gagal memuat pengguna: ${e.message}")
            }
    }

    // Logika Pemrosesan List
    val filteredUsers by remember(users, searchQuery, roleAktif, statusAktif) {
        derivedStateOf {
            users.filter { item ->
                val query = searchQuery.lowercase()
                val matchQuery = query.isBlank() ||
                        item.namaPengguna.lowercase().contains(query) ||
                        item.email.lowercase().contains(query) ||
                        item.nomorTelepon.lowercase().contains(query)

                val matchRole = when (roleAktif) {
                    "ADMIN" -> item.peranAsli == "ADMIN"
                    "KASIR" -> item.peranAsli == "KASIR"
                    else -> true
                }

                val matchStatus = when (statusAktif) {
                    "Aktif" -> item.aktif
                    "Nonaktif" -> !item.aktif
                    else -> true
                }

                matchQuery && matchRole && matchStatus
            }
        }
    }

    val jumlahFilterAktif = (if (roleAktif != "Semua") 1 else 0) + (if (statusAktif != "Semua") 1 else 0)

    LaunchedEffect(searchQuery, roleAktif, statusAktif, users.size) {
        halamanSaatIni = 1
    }
    val totalHalaman = maxOf(1, ((filteredUsers.size - 1) / itemKecilPerHalaman) + 1)
    if (halamanSaatIni > totalHalaman) halamanSaatIni = totalHalaman
    val penggunaTampil = filteredUsers.drop((halamanSaatIni - 1) * itemKecilPerHalaman).take(itemKecilPerHalaman)

    // --- FUNGSI AKSI PENGGUNA ---
    fun toggleUserStatus(user: DataBarisPengguna) {
        onShowConfirmation(
            if (user.aktif) "Nonaktifkan pengguna?" else "Aktifkan pengguna?",
            "Pengguna ${user.namaPengguna} akan di${if (user.aktif) "non" else ""}aktifkan.",
            if (user.aktif) "Nonaktifkan" else "Aktifkan"
        ) {
            firestore.collection("Pengguna").document(user.id)
                .update(mapOf("aktif" to !user.aktif, "bolehMasuk" to !user.aktif, "diperbaruiPada" to Timestamp.now()))
                .addOnSuccessListener {
                    onShowMessage("Status pengguna berhasil diubah.")
                    triggerRefresh++
                }
                .addOnFailureListener { e -> onShowMessage("Gagal mengubah status: ${e.message}") }
        }
    }

    fun deleteUser(user: DataBarisPengguna) {
        onShowConfirmation("Hapus pengguna?", "Pengguna ${user.namaPengguna} akan disembunyikan dari daftar aktif. Riwayat lama tetap aman.", "Hapus") {
            firestore.collection("Pengguna").document(user.id)
                .update(mapOf(
                    "dihapus" to true,
                    "aktif" to false,
                    "bolehMasuk" to false,
                    "dihapusPada" to Timestamp.now(),
                    "diperbaruiPada" to Timestamp.now()
                ))
                .addOnSuccessListener {
                    onShowMessage("Pengguna berhasil dihapus dari daftar aktif.")
                    triggerRefresh++
                }
                .addOnFailureListener { e -> onShowMessage("Gagal menghapus pengguna: ${e.message}") }
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
                            Text("Pengguna", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text("Kelola admin dan kasir", style = MaterialTheme.typography.labelMedium, color = mutedColor)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Rounded.ArrowBack, "Kembali", tint = textColor) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToForm(null) }, containerColor = primaryColor, shape = CircleShape) {
                Icon(Icons.Rounded.Add, "Tambah", tint = Color.White)
            }
        },
        containerColor = bgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- PENCARIAN & FILTER MODERN ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari nama, email, telepon...", color = mutedColor) },
                    leadingIcon = { Icon(Icons.Rounded.Search, "Cari", tint = mutedColor) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(100),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = surfaceColor,
                        unfocusedContainerColor = surfaceColor
                    ),
                    modifier = Modifier.weight(1f).height(54.dp)
                )

                val hasActiveFilter = jumlahFilterAktif > 0
                Surface(
                    shape = CircleShape,
                    color = if (hasActiveFilter) primaryColor else surfaceColor,
                    border = if (hasActiveFilter) null else BorderStroke(1.dp, borderColor),
                    modifier = Modifier.size(54.dp).clickable { showFilterDialog = true }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.FilterList, "Filter", tint = if (hasActiveFilter) Color.White else textColor)
                        if (hasActiveFilter) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dangerColor)
                            )
                        }
                    }
                }
            }

            // --- CHIPS FILTER AKTIF ---
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (jumlahFilterAktif > 0) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (roleAktif != "Semua") {
                            FilterChipVisual(label = "Peran: $roleAktif", onRemove = { roleAktif = "Semua" }, primaryColor)
                        }
                        if (statusAktif != "Semua") {
                            FilterChipVisual(label = "Status: $statusAktif", onRemove = { statusAktif = "Semua" }, primaryColor)
                        }
                    }
                }
                Text("Menampilkan ${filteredUsers.size} pengguna", color = mutedColor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
            }

            // --- DAFTAR PENGGUNA ---
            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = primaryColor) }
            } else if (filteredUsers.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Person, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                        }
                        Text("Pengguna tidak ditemukan", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(if (users.isEmpty()) "Belum ada pengguna." else "Coba ubah pencarian, filter, atau rentang tanggal.", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(penggunaTampil) { user ->
                        UserCard(
                            user = user,
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            primaryColor = primaryColor,
                            successColor = successColor,
                            dangerColor = dangerColor,
                            warningColor = warningColor,
                            onClick = { onNavigateToForm(user.id) },
                            onEdit = { onNavigateToForm(user.id) },
                            onToggleStatus = { toggleUserStatus(user) },
                            onDelete = { deleteUser(user) }
                        )
                    }
                    if (totalHalaman > 1) {
                        item {
                            PaginationListCard(
                                halamanSaatIni = halamanSaatIni,
                                totalHalaman = totalHalaman,
                                primaryColor = primaryColor,
                                surfaceColor = surfaceColor,
                                borderColor = borderColor,
                                textColor = textColor,
                                mutedColor = mutedColor,
                                onPrev = { if (halamanSaatIni > 1) halamanSaatIni-- },
                                onNext = { if (halamanSaatIni < totalHalaman) halamanSaatIni++ }
                            )
                        }
                    }
                }
            }
        }

// === DIALOG FILTER MODERN ---
        if (showFilterDialog) {
            ModernUserFilterDialog(
                roleOptions = roleOptions,
                initialRole = roleAktif,
                statusOptions = statusOptions,
                initialStatus = statusAktif,
                primaryColor = primaryColor,
                surfaceColor = surfaceColor,
                bgColor = bgColor,
                textColor = textColor,
                mutedColor = mutedColor,
                borderColor = borderColor,
                onDismiss = { showFilterDialog = false },
                onReset = {
                    roleAktif = "Semua"
                    statusAktif = "Semua"
                    showFilterDialog = false
                },
                onApply = { r, s ->
                    roleAktif = r
                    statusAktif = s
                    showFilterDialog = false
                }
            )
        }
    }
}

@Composable
private fun PaginationListCard(
    halamanSaatIni: Int,
    totalHalaman: Int,
    primaryColor: Color,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 80.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onPrev, enabled = halamanSaatIni > 1) {
                Text("Sebelumnya", color = if (halamanSaatIni > 1) primaryColor else mutedColor, fontWeight = FontWeight.Bold)
            }
            Text("Hal $halamanSaatIni dari $totalHalaman", color = textColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onNext, enabled = halamanSaatIni < totalHalaman) {
                Text("Selanjutnya", color = if (halamanSaatIni < totalHalaman) primaryColor else mutedColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// === KOMPONEN UI REUSABLE & MODERN ===

@Composable
private fun FilterChipVisual(label: String, onRemove: () -> Unit, primaryColor: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = primaryColor.copy(alpha = 0.1f), modifier = Modifier.clickable(onClick = onRemove)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = primaryColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Icon(Icons.Rounded.Close, "Hapus", tint = primaryColor, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun UserCard(
    user: DataBarisPengguna,
    surfaceColor: Color, borderColor: Color, textColor: Color, mutedColor: Color,
    primaryColor: Color, successColor: Color, dangerColor: Color, warningColor: Color,
    onClick: () -> Unit, onEdit: () -> Unit, onToggleStatus: () -> Unit, onDelete: () -> Unit
) {
    val isAdmin = user.peranAsli == "ADMIN"
    val roleColor = if (isAdmin) primaryColor else warningColor
    val icon = if (isAdmin) Icons.Rounded.AdminPanelSettings else Icons.Rounded.Person
    val statusColor = if (user.aktif) successColor else dangerColor
    val statusText = if (user.aktif) "Akun Aktif" else "Akun Nonaktif"

    // State untuk mengontrol Dropdown Menu Titik Tiga
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(roleColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = roleColor, modifier = Modifier.size(24.dp))
                }

                Column(Modifier.weight(1f)) {
                    Text(user.namaPengguna.ifBlank { user.id }, fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(user.email.ifBlank { "-" }, color = mutedColor, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(user.nomorTelepon.ifBlank { "-" }, color = mutedColor, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                // Dropdown Menu Titik Tiga
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp).offset(x = 8.dp, y = (-8).dp)
                    ) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Opsi", tint = mutedColor)
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(surfaceColor)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Data", color = textColor) },
                            onClick = { showMenu = false; onEdit() }
                        )

                        val toggleText = if (user.aktif) "Nonaktifkan Pengguna" else "Aktifkan Pengguna"
                        val toggleColor = if (user.aktif) warningColor else successColor
                        DropdownMenuItem(
                            text = { Text(toggleText, color = toggleColor) },
                            onClick = { showMenu = false; onToggleStatus() }
                        )

                        DropdownMenuItem(
                            text = { Text("Hapus Pengguna", color = dangerColor, fontWeight = FontWeight.SemiBold) },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }

            HorizontalDivider(color = borderColor)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(4.dp), color = roleColor.copy(alpha = 0.1f)) {
                    Text(user.peranAsli.ifBlank { "PENGGUNA" }, color = roleColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                    Text(statusText, fontWeight = FontWeight.Bold, color = statusColor, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernUserFilterDialog(
    roleOptions: List<String>,
    initialRole: String,
    statusOptions: List<String>,
    initialStatus: String,
    primaryColor: Color,
    surfaceColor: Color,
    bgColor: Color,
    textColor: Color,
    mutedColor: Color,
    borderColor: Color,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onApply: (role: String, status: String) -> Unit
) {
    var draftRole by remember { mutableStateOf(initialRole) }
    var draftStatus by remember { mutableStateOf(initialStatus) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = surfaceColor,
        title = { Text("Filter Pengguna", fontWeight = FontWeight.Bold, color = textColor) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Peran Pengguna", fontWeight = FontWeight.SemiBold, color = textColor, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    roleOptions.forEach { pilihan ->
                        val isSelected = draftRole == pilihan
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (isSelected) primaryColor else borderColor),
                            modifier = Modifier.weight(1f).clickable { draftRole = pilihan }
                        ) {
                            Text(
                                text = pilihan.lowercase().replaceFirstChar { it.uppercase() },
                                color = if (isSelected) primaryColor else textColor,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 12.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                Text("Status Akun", fontWeight = FontWeight.SemiBold, color = textColor, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    statusOptions.forEach { pilihan ->
                        val isSelected = draftStatus == pilihan
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (isSelected) primaryColor else borderColor),
                            modifier = Modifier.weight(1f).clickable { draftStatus = pilihan }
                        ) {
                            Text(
                                text = pilihan.lowercase().replaceFirstChar { it.uppercase() },
                                color = if (isSelected) primaryColor else textColor,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 12.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(draftRole, draftStatus) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) { Text("Terapkan", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onReset) { Text("Reset", color = mutedColor) } }
    )
}