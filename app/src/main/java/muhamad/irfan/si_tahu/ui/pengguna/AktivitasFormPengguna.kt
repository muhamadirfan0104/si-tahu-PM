package muhamad.irfan.si_tahu.ui.pengguna

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.utama.SiTahuProTheme
import muhamad.irfan.si_tahu.util.EkstraAplikasi

class AktivitasFormPengguna : AktivitasDasar() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        val editingUserId = intent.getStringExtra(EkstraAplikasi.EXTRA_USER_ID)

        setContent {
            SiTahuProTheme {
                UserFormScreen(
                    editingUserId = editingUserId,
                    onNavigateBack = { finish() },
                    onShowMessage = { pesan -> showMessage(pesan) },
                    onSaveSuccess = {
                        setResult(RESULT_OK)
                        finish()
                    },
                    activityContext = this@AktivitasFormPengguna
                )
            }
        }
    }
}

// === KOMPONEN UTAMA UI ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserFormScreen(
    editingUserId: String?,
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    onSaveSuccess: () -> Unit,
    activityContext: AppCompatActivity
) {
    val firestore = FirebaseFirestore.getInstance()
    val isEditing = !editingUserId.isNullOrBlank()

    // State Form Pribadi
    var namaPengguna by remember { mutableStateOf("") }
    var nomorTelepon by remember { mutableStateOf("") }

    // State Peran (Dropdown)
    val roleOptions = listOf("ADMIN", "KASIR")
    var isRoleDropdownExpanded by remember { mutableStateOf(false) }
    var peranAsli by remember { mutableStateOf(roleOptions.last()) } // Default Kasir

    // State Kredensial Login
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // State Akses
    var aktif by remember { mutableStateOf(true) }

    // State Proses
    var isLoading by remember { mutableStateOf(isEditing) }
    var isSaving by remember { mutableStateOf(false) }
    var isSendingReset by remember { mutableStateOf(false) }

    // Tema Warna Pro
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF111827) else Color(0xFFF3F4F6)
    val surfaceColor = if (isDark) Color(0xFF1F2937) else Color(0xFFFFFFFF)
    val primaryColor = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
    val textColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF111827)
    val mutedColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)
    val warningColor = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)

    // Load Data Jika Mode Edit
    LaunchedEffect(editingUserId) {
        if (isEditing && editingUserId != null) {
            firestore.collection("Pengguna").document(editingUserId).get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        onShowMessage("Data pengguna tidak ditemukan.")
                        onNavigateBack()
                        return@addOnSuccessListener
                    }
                    namaPengguna = doc.getString("namaPengguna").orEmpty()
                    email = doc.getString("email").orEmpty()
                    nomorTelepon = doc.getString("nomorTelepon").orEmpty()
                    aktif = doc.getBoolean("aktif") ?: true
                    peranAsli = doc.getString("peranAsli").orEmpty().ifBlank { "KASIR" }
                    isLoading = false
                }
                .addOnFailureListener { e ->
                    onShowMessage("Gagal memuat pengguna: ${e.message}")
                    onNavigateBack()
                }
        }
    }

    // --- FUNGSI LOGIKA FIREBASE ---

    fun sendResetPassword() {
        if (email.isBlank()) {
            onShowMessage("Email pengguna tidak ditemukan.")
            return
        }
        isSendingReset = true
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnSuccessListener {
                isSendingReset = false
                onShowMessage("Link reset password berhasil dikirim ke $email")
            }
            .addOnFailureListener { e ->
                isSendingReset = false
                onShowMessage("Gagal mengirim link reset: ${e.message}")
            }
    }

    fun saveUser() {
        val namaBersih = namaPengguna.trim()
        val emailBersih = email.trim().lowercase()
        val telpBersih = nomorTelepon.trim()
        val passBersih = password.trim()

        // Validasi
        if (namaBersih.isBlank()) { onShowMessage("Nama wajib diisi."); return }
        if (emailBersih.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(emailBersih).matches()) {
            onShowMessage("Format email tidak valid."); return
        }
        if (telpBersih.isBlank()) { onShowMessage("Nomor telepon wajib diisi."); return }

        if (!isEditing) {
            if (passBersih.isBlank()) { onShowMessage("Password wajib diisi untuk pengguna baru."); return }
            if (passBersih.length < 6) { onShowMessage("Password minimal 6 karakter."); return }
        }

        isSaving = true

        // Cek Duplikasi Email
        firestore.collection("Pengguna").whereEqualTo("email", emailBersih).get()
            .addOnSuccessListener { snapshot ->
                val duplicateExists = snapshot.documents.any { it.id != editingUserId }
                if (duplicateExists) {
                    isSaving = false
                    onShowMessage("Email sudah dipakai oleh pengguna lain.")
                    return@addOnSuccessListener
                }

                if (!isEditing) {
                    // --- ALUR PEMBUATAN PENGGUNA BARU (SECONDARY AUTH) ---
                    val secondaryAppName = "user_creator_${System.currentTimeMillis()}"
                    val primaryApp = FirebaseApp.getInstance()
                    val secondaryApp = FirebaseApp.initializeApp(activityContext, primaryApp.options, secondaryAppName)

                    if (secondaryApp == null) {
                        isSaving = false; onShowMessage("Gagal menyiapkan sistem Auth pengguna."); return@addOnSuccessListener
                    }
                    val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)

                    secondaryAuth.createUserWithEmailAndPassword(emailBersih, passBersih)
                        .addOnSuccessListener { result ->
                            val firebaseUser = result.user
                            if (firebaseUser == null) {
                                try { secondaryAuth.signOut(); secondaryApp.delete() } catch (_: Exception) {}
                                isSaving = false; onShowMessage("Akun login gagal dibuat.")
                                return@addOnSuccessListener
                            }

                            // Generate ID dan Simpan ke Firestore
                            val prefix = if (peranAsli == "ADMIN") "usr_admin_" else "usr_kasir_"
                            firestore.collection("Pengguna").get().addOnSuccessListener { userSnap ->
                                val lastNum = userSnap.documents.mapNotNull { doc ->
                                    if (doc.id.startsWith(prefix)) doc.id.removePrefix(prefix).toIntOrNull() else null
                                }.maxOrNull() ?: 0

                                val newId = prefix + "%02d".format(lastNum + 1)
                                val now = Timestamp.now()
                                val data = hashMapOf<String, Any?>(
                                    "namaPengguna" to namaBersih, "email" to emailBersih, "nomorTelepon" to telpBersih,
                                    "peranAsli" to peranAsli, "modeAplikasi" to peranAsli,
                                    "aktif" to aktif, "bolehMasuk" to aktif,
                                    "dihapus" to false, "dihapusPada" to null,
                                    "authUid" to firebaseUser.uid, "dibuatPada" to now, "diperbaruiPada" to now
                                )

                                firestore.collection("Pengguna").document(newId).set(data, SetOptions.merge())
                                    .addOnSuccessListener {
                                        try { secondaryAuth.signOut(); secondaryApp.delete() } catch (_: Exception) {}
                                        onShowMessage("Pengguna baru berhasil ditambahkan.")
                                        onSaveSuccess()
                                    }
                                    .addOnFailureListener { e ->
                                        firebaseUser.delete().addOnCompleteListener {
                                            try { secondaryAuth.signOut(); secondaryApp.delete() } catch (_: Exception) {}
                                            isSaving = false; onShowMessage("Gagal menyimpan ke Firestore: ${e.message}")
                                        }
                                    }
                            }
                        }
                        .addOnFailureListener { e ->
                            try { secondaryAuth.signOut(); secondaryApp.delete() } catch (_: Exception) {}
                            isSaving = false
                            val msg = when (e) {
                                is FirebaseAuthWeakPasswordException -> "Password terlalu lemah (min. 6 karakter)."
                                is FirebaseAuthInvalidCredentialsException -> "Format email tidak valid."
                                is FirebaseAuthUserCollisionException -> "Email sudah terdaftar di sistem otentikasi."
                                else -> "Gagal membuat akun: ${e.message}"
                            }
                            onShowMessage(msg)
                        }

                } else {
                    // --- ALUR EDIT PENGGUNA ---
                    val now = Timestamp.now()
                    val data = hashMapOf<String, Any?>(
                        "namaPengguna" to namaBersih, "nomorTelepon" to telpBersih,
                        "peranAsli" to peranAsli, "modeAplikasi" to peranAsli,
                        "aktif" to aktif, "bolehMasuk" to aktif, "dihapus" to false, "dihapusPada" to null, "diperbaruiPada" to now
                    )

                    firestore.collection("Pengguna").document(editingUserId!!).set(data, SetOptions.merge())
                        .addOnSuccessListener {
                            onShowMessage("Data pengguna berhasil diperbarui.")
                            onSaveSuccess()
                        }
                        .addOnFailureListener { e ->
                            isSaving = false; onShowMessage("Gagal memperbarui data: ${e.message}")
                        }
                }
            }
            .addOnFailureListener { e ->
                isSaving = false
                onShowMessage("Gagal memeriksa email: ${e.message}")
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (isEditing) "Edit Pengguna" else "Tambah Pengguna", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                        Text(if (isEditing) "Perbarui data staf" else "Daftarkan staf baru", style = MaterialTheme.typography.labelMedium, color = mutedColor)
                    }
                },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Rounded.ArrowBack, "Kembali", tint = textColor) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        },
        bottomBar = {
            Surface(
                color = surfaceColor,
                shadowElevation = 24.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
                    Button(
                        onClick = { saveUser() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isSaving && !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Menyimpan...", fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Icon(Icons.Rounded.Save, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Simpan Pengguna", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
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
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {

                // === 1. KARTU INFORMASI PRIBADI ===
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Person, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            Text("Informasi Pribadi", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                        }

                        OutlinedTextField(
                            value = namaPengguna,
                            onValueChange = { namaPengguna = it },
                            label = { Text("Nama Lengkap") },
                            placeholder = { Text("Masukkan nama pengguna") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = nomorTelepon,
                            onValueChange = { nomorTelepon = it.filter { char -> char.isDigit() || char == '+' } },
                            label = { Text("Nomor Telepon / WhatsApp") },
                            placeholder = { Text("0812xxxxxx") },
                            leadingIcon = { Icon(Icons.Rounded.Phone, null, tint = mutedColor) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Dropdown Peran
                        ExposedDropdownMenuBox(
                            expanded = isRoleDropdownExpanded,
                            onExpandedChange = { isRoleDropdownExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = peranAsli,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Peran / Posisi") },
                                leadingIcon = { Icon(Icons.Rounded.AdminPanelSettings, null, tint = mutedColor) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isRoleDropdownExpanded) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor),
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = isRoleDropdownExpanded,
                                onDismissRequest = { isRoleDropdownExpanded = false },
                                modifier = Modifier.background(surfaceColor)
                            ) {
                                roleOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, color = textColor) },
                                        onClick = { peranAsli = option; isRoleDropdownExpanded = false }
                                    )
                                }
                            }
                        }
                    }
                }

                // === 2. KARTU KREDENSIAL LOGIN ===
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Security, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            Text("Kredensial Login", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Alamat Email") },
                            placeholder = { Text("nama@email.com") },
                            leadingIcon = { Icon(Icons.Rounded.Email, null, tint = mutedColor) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = if (isEditing) ImeAction.Done else ImeAction.Next),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isEditing, // Email tidak bisa diubah saat edit
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor,
                                disabledTextColor = textColor, disabledBorderColor = borderColor, disabledLabelColor = mutedColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (!isEditing) {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password Akun") },
                                placeholder = { Text("Minimal 6 karakter") },
                                leadingIcon = { Icon(Icons.Rounded.Lock, null, tint = mutedColor) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            // Jika Edit, Password disembunyikan dan muncul tombol Reset
                            Surface(shape = RoundedCornerShape(12.dp), color = warningColor.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Rounded.Info, null, tint = warningColor, modifier = Modifier.size(20.dp))
                                    Text("Password tidak dapat dilihat/diubah langsung untuk keamanan.", color = warningColor, style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            OutlinedButton(
                                onClick = { sendResetPassword() },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isSendingReset,
                                border = BorderStroke(1.dp, primaryColor)
                            ) {
                                if (isSendingReset) {
                                    CircularProgressIndicator(color = primaryColor, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Mengirim...", color = primaryColor, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Rounded.LockReset, null, tint = primaryColor)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Kirim Link Reset Password", color = primaryColor, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // === 3. KARTU PENGATURAN STATUS ===
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.AdminPanelSettings, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            Text("Pengaturan Akses", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                        }
                        HorizontalDivider(color = borderColor, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))

                        Row(
                            Modifier.fillMaxWidth().clickable { aktif = !aktif }.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Izinkan Masuk (Aktif)", fontWeight = FontWeight.Bold, color = textColor)
                                Text("Pengguna dapat login dan menggunakan sistem", color = mutedColor, style = MaterialTheme.typography.labelMedium)
                            }
                            Switch(checked = aktif, onCheckedChange = { aktif = it }, colors = SwitchDefaults.colors(checkedTrackColor = primaryColor))
                        }
                    }
                }

                Spacer(Modifier.height(100.dp)) // Ruang ekstra untuk Bottom Bar
            }
        }
    }
}