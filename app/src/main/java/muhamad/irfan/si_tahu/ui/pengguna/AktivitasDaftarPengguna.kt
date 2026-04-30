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
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
@OptIn(ExperimentalMaterial3Api::class)
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

    var showFilterSheet by remember { mutableStateOf(false) }

    // Bottom Sheet Aksi
    var selectedUserForAction by remember { mutableStateOf<DataBarisPengguna?>(null) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pengguna", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                        Text("Kelola admin dan kasir", style = MaterialTheme.typography.labelMedium, color = mutedColor)
                    }
                },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Rounded.ArrowBack, "Kembali", tint = textColor) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToForm(null) }, containerColor = primaryColor, shape = CircleShape) {
                Icon(Icons.Rounded.Add, "Tambah Pengguna", tint = Color.White)
            }
        },
        containerColor = bgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- PENCARIAN & FILTER ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari nama, email, telepon...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, "Cari", tint = mutedColor) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor, focusedContainerColor = surfaceColor, unfocusedContainerColor = surfaceColor),
                    modifier = Modifier.weight(1f)
                )

                Box {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (jumlahFilterAktif > 0) primaryColor else surfaceColor,
                        border = if (jumlahFilterAktif > 0) null else BorderStroke(1.dp, borderColor),
                        modifier = Modifier.size(56.dp).clickable { showFilterSheet = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.FilterList, "Filter", tint = if (jumlahFilterAktif > 0) Color.White else textColor)
                        }
                    }

                    if (jumlahFilterAktif > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(dangerColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(jumlahFilterAktif.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- DAFTAR PENGGUNA ---
            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = primaryColor) }
            } else if (filteredUsers.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Person, null, tint = borderColor, modifier = Modifier.size(64.dp))
                        Text(if (users.isEmpty()) "Belum ada pengguna." else "Tidak ada pengguna yang cocok.", color = mutedColor, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredUsers) { user ->
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
                            onActionClick = { selectedUserForAction = user }
                        )
                    }
                }
            }
        }

        // === BOTTOM SHEET FILTER ===
        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                containerColor = surfaceColor,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                windowInsets = WindowInsets.navigationBars
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Filter Pengguna", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = {
                            roleAktif = "Semua"
                            statusAktif = "Semua"
                        }) {
                            Text("Reset", color = primaryColor, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Filter Peran
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Peran Pengguna", color = textColor, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            roleOptions.forEach { option ->
                                FilterChip(
                                    selected = roleAktif == option,
                                    onClick = { roleAktif = option },
                                    label = { Text(option) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primaryColor.copy(alpha = 0.15f), selectedLabelColor = primaryColor)
                                )
                            }
                        }
                    }

                    // Filter Status
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Status Akun", color = textColor, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            statusOptions.forEach { option ->
                                FilterChip(
                                    selected = statusAktif == option,
                                    onClick = { statusAktif = option },
                                    label = { Text(option) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primaryColor.copy(alpha = 0.15f), selectedLabelColor = primaryColor)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { showFilterSheet = false },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        Text("Terapkan Filter", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // === BOTTOM SHEET MENU AKSI ===
        if (selectedUserForAction != null) {
            val user = selectedUserForAction!!

            ModalBottomSheet(
                onDismissRequest = { selectedUserForAction = null },
                containerColor = surfaceColor,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                windowInsets = WindowInsets.navigationBars
            ) {
                Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    Text("Opsi ${user.namaPengguna}", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

                    MenuItemAction(Icons.Rounded.Edit, "Edit Data", textColor) {
                        selectedUserForAction = null
                        onNavigateToForm(user.id)
                    }

                    val toggleText = if (user.aktif) "Nonaktifkan Pengguna" else "Aktifkan Pengguna"
                    val toggleColor = if (user.aktif) warningColor else successColor
                    MenuItemAction(Icons.Rounded.PowerSettingsNew, toggleText, toggleColor) {
                        selectedUserForAction = null
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

                    MenuItemAction(Icons.Rounded.DeleteForever, "Hapus Pengguna", dangerColor) {
                        selectedUserForAction = null
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
                }
            }
        }
    }
}

@Composable
private fun MenuItemAction(icon: ImageVector, title: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(title, color = color, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun UserCard(
    user: DataBarisPengguna,
    surfaceColor: Color, borderColor: Color, textColor: Color, mutedColor: Color,
    primaryColor: Color, successColor: Color, dangerColor: Color, warningColor: Color,
    onClick: () -> Unit, onActionClick: () -> Unit
) {
    val isAdmin = user.peranAsli == "ADMIN"
    val roleColor = if (isAdmin) primaryColor else warningColor
    val icon = if (isAdmin) Icons.Rounded.AdminPanelSettings else Icons.Rounded.Person
    val statusColor = if (user.aktif) successColor else dangerColor
    val statusText = if (user.aktif) "Akun Aktif" else "Akun Nonaktif"

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

                IconButton(onClick = onActionClick, modifier = Modifier.size(28.dp).offset(x = 8.dp, y = (-8).dp)) {
                    Icon(Icons.Rounded.MoreVert, "Opsi", tint = mutedColor)
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