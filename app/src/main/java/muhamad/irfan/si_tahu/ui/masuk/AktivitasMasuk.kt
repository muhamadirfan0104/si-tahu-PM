package muhamad.irfan.si_tahu.ui.masuk

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Login
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.data.PenggunaFirestoreCompat
import muhamad.irfan.si_tahu.data.ProfilPenggunaFirebase
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.utama.AktivitasUtamaAdmin
import muhamad.irfan.si_tahu.ui.utama.AktivitasUtamaKasir
import muhamad.irfan.si_tahu.ui.utama.SiTahuProTheme

class AktivitasMasuk : AktivitasDasar() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    // State untuk dikirim ke Compose UI
    private var logoTextState by mutableStateOf("TAHU")
    private var businessNameState by mutableStateOf("Nama Usaha")
    private var isLoadingState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        applyDefaultBusinessIdentity()
        loadBusinessIdentity()

        setContent {
            SiTahuProTheme {
                LoginScreen(
                    logoText = logoTextState,
                    businessName = businessNameState,
                    isLoading = isLoadingState,
                    onLoginClick = { email, password -> doLogin(email, password) }
                )
            }
        }

        // Cek jika sudah login
        auth.currentUser?.let { currentUser ->
            loadUserProfileAndOpenHome(
                uid = currentUser.uid,
                email = currentUser.email
            )
        }
    }

    private fun applyDefaultBusinessIdentity() {
        logoTextState = "TAHU"
        businessNameState = "Nama Usaha"
    }

    private fun loadBusinessIdentity() {
        firestore.collection("Pengaturan")
            .document("umum")
            .get()
            .addOnSuccessListener { doc ->
                val logoText = doc.getString("teksLogo").orEmpty().ifBlank { doc.getString("logoText").orEmpty() }.trim()
                val namaUsaha = doc.getString("namaTampilanToko").orEmpty().ifBlank { doc.getString("namaUsaha").orEmpty() }.trim()

                logoTextState = if (logoText.isBlank()) "TAHU" else logoText.uppercase()
                businessNameState = if (namaUsaha.isBlank()) "Nama Usaha" else namaUsaha
            }
            .addOnFailureListener {
                applyDefaultBusinessIdentity()
            }
    }

    private fun doLogin(emailInput: String, passwordInput: String) {
        val email = emailInput.trim().lowercase()
        val password = passwordInput.trim()

        if (email.isBlank()) {
            showMessage("Email wajib diisi")
            return
        }

        if (password.isBlank()) {
            showMessage("Password wajib diisi")
            return
        }

        updateLoadingState(true) // PERBAIKAN: Menggunakan nama fungsi baru

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid.isNullOrBlank()) {
                    updateLoadingState(false) // PERBAIKAN: Menggunakan nama fungsi baru
                    showMessage("Login gagal: UID tidak ditemukan.")
                    return@addOnSuccessListener
                }

                loadUserProfileAndOpenHome(
                    uid = uid,
                    email = result.user?.email ?: email
                )
            }
            .addOnFailureListener { e ->
                updateLoadingState(false) // PERBAIKAN: Menggunakan nama fungsi baru

                val message = when (e) {
                    is FirebaseAuthInvalidUserException -> "Email belum terdaftar sebagai pengguna aplikasi."
                    is FirebaseAuthInvalidCredentialsException -> "Password salah atau format email tidak valid."
                    is FirebaseNetworkException -> "Koneksi ke server sedang bermasalah. Periksa internet lalu coba lagi."
                    else -> "Login gagal: ${e.message}"
                }

                Log.e("LoginFirebase", "Login error", e)
                showMessage(message)
            }
    }

    private fun loadUserProfileAndOpenHome(uid: String, email: String?) {
        updateLoadingState(true) // PERBAIKAN: Menggunakan nama fungsi baru

        PenggunaFirestoreCompat.findByAuthUid(
            firestore = firestore,
            authUid = uid,
            onFound = { doc ->
                PenggunaFirestoreCompat.migrateLegacyDocIfNeeded(
                    firestore = firestore,
                    doc = doc,
                    authUid = uid,
                    onComplete = { syncedDoc -> handleProfileDocument(syncedDoc) },
                    onError = { e ->
                        updateLoadingState(false) // PERBAIKAN: Menggunakan nama fungsi baru
                        auth.signOut()
                        Log.e("LoginFirebase", "Migrate legacy user error", e)
                        showMessage("Gagal sinkron data pengguna lama: ${e.message}")
                    }
                )
            },
            onNotFound = {
                fallbackFindProfileByEmail(
                    uid = uid,
                    email = email?.trim()?.lowercase().orEmpty()
                )
            },
            onError = { e ->
                updateLoadingState(false) // PERBAIKAN: Menggunakan nama fungsi baru
                auth.signOut()
                Log.e("LoginFirebase", "Load profile by authUid error", e)
                showMessage("Gagal membaca data pengguna: ${e.message}")
            }
        )
    }

    private fun fallbackFindProfileByEmail(uid: String, email: String) {
        if (email.isBlank()) {
            updateLoadingState(false) // PERBAIKAN: Menggunakan nama fungsi baru
            auth.signOut()
            showMessage("Data pengguna tidak ditemukan di Firestore.")
            return
        }

        PenggunaFirestoreCompat.findByEmail(
            firestore = firestore,
            email = email,
            onFound = { doc ->
                PenggunaFirestoreCompat.migrateLegacyDocIfNeeded(
                    firestore = firestore,
                    doc = doc,
                    authUid = uid,
                    onComplete = { syncedDoc ->
                        val currentAuthUid = syncedDoc.getString("authUid").orEmpty()
                        if (currentAuthUid == uid) {
                            handleProfileDocument(syncedDoc)
                            return@migrateLegacyDocIfNeeded
                        }

                        syncedDoc.reference.update(mapOf("authUid" to uid, "diperbaruiPada" to Timestamp.now()))
                            .addOnSuccessListener {
                                syncedDoc.reference.get()
                                    .addOnSuccessListener { refreshedDoc -> handleProfileDocument(refreshedDoc) }
                                    .addOnFailureListener { refreshError ->
                                        updateLoadingState(false) // PERBAIKAN: Menggunakan nama fungsi baru
                                        auth.signOut()
                                        Log.e("LoginFirebase", "Refresh profile error", refreshError)
                                        showMessage("Gagal memuat ulang data pengguna: ${refreshError.message}")
                                    }
                            }
                            .addOnFailureListener { e ->
                                updateLoadingState(false) // PERBAIKAN: Menggunakan nama fungsi baru
                                auth.signOut()
                                Log.e("LoginFirebase", "Sync authUid error", e)
                                showMessage("Gagal sinkron UID pengguna: ${e.message}")
                            }
                    },
                    onError = { e ->
                        updateLoadingState(false) // PERBAIKAN: Menggunakan nama fungsi baru
                        auth.signOut()
                        Log.e("LoginFirebase", "Migrate legacy user by email error", e)
                        showMessage("Gagal sinkron data pengguna lama: ${e.message}")
                    }
                )
            },
            onNotFound = {
                updateLoadingState(false) // PERBAIKAN: Menggunakan nama fungsi baru
                auth.signOut()
                showMessage("Data pengguna tidak ditemukan di Firestore.")
            },
            onError = { e ->
                updateLoadingState(false) // PERBAIKAN: Menggunakan nama fungsi baru
                auth.signOut()
                Log.e("LoginFirebase", "Load profile by email error", e)
                showMessage("Gagal membaca data pengguna: ${e.message}")
            }
        )
    }

    private fun handleProfileDocument(doc: DocumentSnapshot) {
        updateLoadingState(false) // PERBAIKAN: Menggunakan nama fungsi baru

        val profile = doc.toObject(ProfilPenggunaFirebase::class.java)
        if (profile == null) {
            auth.signOut()
            showMessage("Data pengguna tidak valid.")
            return
        }

        if (profile.dihapus) {
            auth.signOut()
            showMessage("Akun ini sudah dihapus dari sistem.")
            return
        }

        if (!profile.aktif) {
            auth.signOut()
            showMessage("Akun ini tidak aktif.")
            return
        }

        if (!profile.bolehMasuk) {
            auth.signOut()
            showMessage("Akun ini tidak diizinkan masuk.")
            return
        }

        openHome(profile)
    }

    private fun openHome(profile: ProfilPenggunaFirebase) {
        val mode = profile.peranAsli.trim().ifBlank { profile.modeAplikasi.trim() }.uppercase()

        val targetIntent = when (mode) {
            "ADMIN" -> Intent(this, AktivitasUtamaAdmin::class.java)
            "KASIR" -> Intent(this, AktivitasUtamaKasir::class.java)
            else -> {
                showMessage("Hak akses akun tidak dikenali: '$mode'")
                return
            }
        }

        startActivity(targetIntent)
        finish()
    }

    // PERBAIKAN: Nama fungsi diubah agar tidak terjadi bentrok dengan setter otomatis dari properti isLoadingState
    private fun updateLoadingState(isLoading: Boolean) {
        isLoadingState = isLoading
    }
}

// === KOMPONEN UTAMA UI COMPOSE ===
@Composable
private fun LoginScreen(
    logoText: String,
    businessName: String,
    isLoading: Boolean,
    onLoginClick: (String, String) -> Unit
) {
    // Tema Warna Pro
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF111827) else Color(0xFFF3F4F6)
    val surfaceColor = if (isDark) Color(0xFF1F2937) else Color(0xFFFFFFFF)
    val primaryColor = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
    val textColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF111827)
    val mutedColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)

    // Form State
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = bgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // --- HEADER IDENTITAS BISNIS ---
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(primaryColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = logoText.take(4), // Maksimal 4 huruf
                    color = primaryColor,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Masuk ke Si Tahu.",
                fontWeight = FontWeight.Black,
                color = textColor,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = businessName,
                color = mutedColor,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(40.dp))

            // --- KARTU FORM LOGIN ---
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Input Email
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Pengguna") },
                        leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null, tint = mutedColor) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        singleLine = true,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = borderColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Input Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = mutedColor) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        singleLine = true,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = borderColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    // Tombol Login
                    Button(
                        onClick = { onLoginClick(email, password) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Memproses...", fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Icon(Icons.Rounded.Login, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Masuk Sekarang", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            // Footer (Opsional, untuk estetika)
            Spacer(Modifier.height(32.dp))
            Text(
                text = "Aplikasi Kasir & Manajemen Usaha Tahu",
                color = mutedColor,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}