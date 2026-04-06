package muhamad.irfan.si_tahu.ui.masuk

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.data.ProfilPenggunaFirebase
import muhamad.irfan.si_tahu.databinding.ActivityLoginBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.utama.AktivitasUtamaAdmin
import muhamad.irfan.si_tahu.ui.utama.AktivitasUtamaKasir

class AktivitasMasuk : AktivitasDasar() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        applyDefaultBusinessIdentity()
        loadBusinessIdentity()

        binding.btnLogin.setOnClickListener {
            doLogin()
        }

        auth.currentUser?.let { currentUser ->
            loadUserProfileAndOpenHome(
                uid = currentUser.uid,
                email = currentUser.email
            )
        }
    }

    private fun applyDefaultBusinessIdentity() {
        binding.tvLogo.text = "TAHU"
        binding.tvBusinessName.text = "Nama Usaha"
        binding.tvTitle.text = "Masuk ke\nSi Tahu."
    }

    private fun loadBusinessIdentity() {
        firestore.collection("pengaturan")
            .document("usaha")
            .get()
            .addOnSuccessListener { doc ->
                val logoText = doc.getString("logoText").orEmpty().trim()
                val namaUsaha = doc.getString("namaUsaha").orEmpty().trim()

                binding.tvLogo.text = if (logoText.isBlank()) {
                    "TAHU"
                } else {
                    logoText.uppercase()
                }

                binding.tvBusinessName.text = if (namaUsaha.isBlank()) {
                    "Nama Usaha"
                } else {
                    namaUsaha
                }

                binding.tvTitle.text = "Masuk ke\nSi Tahu."
            }
            .addOnFailureListener {
                applyDefaultBusinessIdentity()
            }
    }

    private fun doLogin() {
        val email = binding.etEmail.text?.toString()?.trim()?.lowercase().orEmpty()
        val password = binding.etPassword.text?.toString()?.trim().orEmpty()

        if (email.isBlank()) {
            binding.etEmail.error = "Email wajib diisi"
            binding.etEmail.requestFocus()
            return
        }

        if (password.isBlank()) {
            binding.etPassword.error = "Password wajib diisi"
            binding.etPassword.requestFocus()
            return
        }

        setLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid.isNullOrBlank()) {
                    setLoading(false)
                    showMessage("Login gagal: UID tidak ditemukan.")
                    return@addOnSuccessListener
                }

                loadUserProfileAndOpenHome(
                    uid = uid,
                    email = result.user?.email ?: email
                )
            }
            .addOnFailureListener { e ->
                setLoading(false)

                val message = when (e) {
                    is FirebaseAuthInvalidUserException ->
                        "Email belum terdaftar di Firebase Auth."
                    is FirebaseAuthInvalidCredentialsException ->
                        "Password salah atau format email tidak valid."
                    is FirebaseNetworkException ->
                        "Koneksi internet ke Firebase bermasalah."
                    else ->
                        "Login gagal: ${e.message}"
                }

                Log.e("LoginFirebase", "Login error", e)
                showMessage(message)
            }
    }

    private fun loadUserProfileAndOpenHome(
        uid: String,
        email: String?
    ) {
        setLoading(true)

        firestore.collection("pengguna")
            .whereEqualTo("authUid", uid)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val doc = snapshot.documents.firstOrNull()
                if (doc != null) {
                    handleProfileDocument(doc)
                } else {
                    fallbackFindProfileByEmail(
                        uid = uid,
                        email = email?.trim()?.lowercase().orEmpty()
                    )
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                auth.signOut()
                Log.e("LoginFirebase", "Load profile by authUid error", e)
                showMessage("Gagal membaca data pengguna: ${e.message}")
            }
    }

    private fun fallbackFindProfileByEmail(
        uid: String,
        email: String
    ) {
        if (email.isBlank()) {
            setLoading(false)
            auth.signOut()
            showMessage("Data pengguna tidak ditemukan di Firestore.")
            return
        }

        firestore.collection("pengguna")
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val doc = snapshot.documents.firstOrNull()
                if (doc == null) {
                    setLoading(false)
                    auth.signOut()
                    showMessage("Data pengguna tidak ditemukan di Firestore.")
                    return@addOnSuccessListener
                }

                val currentAuthUid = doc.getString("authUid").orEmpty()
                if (currentAuthUid == uid) {
                    handleProfileDocument(doc)
                    return@addOnSuccessListener
                }

                doc.reference.update(
                    mapOf(
                        "authUid" to uid,
                        "diperbaruiPada" to Timestamp.now()
                    )
                )
                    .addOnSuccessListener {
                        handleProfileDocument(doc)
                    }
                    .addOnFailureListener { e ->
                        setLoading(false)
                        auth.signOut()
                        Log.e("LoginFirebase", "Sync authUid error", e)
                        showMessage("Gagal sinkron UID pengguna: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                auth.signOut()
                Log.e("LoginFirebase", "Load profile by email error", e)
                showMessage("Gagal membaca data pengguna: ${e.message}")
            }
    }

    private fun handleProfileDocument(doc: DocumentSnapshot) {
        setLoading(false)

        val profile = doc.toObject(ProfilPenggunaFirebase::class.java)
        if (profile == null) {
            auth.signOut()
            showMessage("Data pengguna tidak valid.")
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
        val mode = profile.modeAplikasi.trim().uppercase()

        val targetIntent = when (mode) {
            "ADMIN" -> Intent(this, AktivitasUtamaAdmin::class.java)
            "KASIR" -> Intent(this, AktivitasUtamaKasir::class.java)
            else -> {
                showMessage("modeAplikasi tidak dikenali: '$mode'")
                return
            }
        }

        startActivity(targetIntent)
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.btnLogin.isEnabled = !isLoading
        binding.etEmail.isEnabled = !isLoading
        binding.etPassword.isEnabled = !isLoading
        binding.btnLogin.text = if (isLoading) "Memproses..." else "Login"
    }
}