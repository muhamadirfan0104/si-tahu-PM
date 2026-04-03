package muhamad.irfan.si_tahu.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.data.ProfilPenggunaFirebase
import muhamad.irfan.si_tahu.databinding.ActivityLoginBinding
import muhamad.irfan.si_tahu.ui.base.AktivitasDasar
import muhamad.irfan.si_tahu.ui.main.AktivitasUtamaAdmin
import muhamad.irfan.si_tahu.ui.main.AktivitasUtamaKasir

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

        binding.btnLogin.setOnClickListener {
            doLogin()
        }

        auth.currentUser?.let { currentUser ->
            loadUserProfileAndOpenHome(currentUser.uid)
        }
    }

    private fun doLogin() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
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
                loadUserProfileAndOpenHome(uid)
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

    private fun loadUserProfileAndOpenHome(uid: String) {
        setLoading(true)

        firestore.collection("pengguna")
            .document(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                setLoading(false)

                if (!snapshot.exists()) {
                    auth.signOut()
                    showMessage("Data pengguna tidak ditemukan di Firestore.")
                    return@addOnSuccessListener
                }

                val profile = snapshot.toObject(ProfilPenggunaFirebase::class.java)
                if (profile == null) {
                    auth.signOut()
                    showMessage("Data pengguna tidak valid.")
                    return@addOnSuccessListener
                }

                if (!profile.aktif) {
                    auth.signOut()
                    showMessage("Akun ini tidak aktif.")
                    return@addOnSuccessListener
                }

                if (!profile.bolehMasuk) {
                    auth.signOut()
                    showMessage("Akun ini tidak diizinkan masuk.")
                    return@addOnSuccessListener
                }

                openHome(profile)
            }
            .addOnFailureListener { e ->
                setLoading(false)
                auth.signOut()
                Log.e("LoginFirebase", "Load profile error", e)
                showMessage("Gagal membaca data pengguna: ${e.message}")
            }
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