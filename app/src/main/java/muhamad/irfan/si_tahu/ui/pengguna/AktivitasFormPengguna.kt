package muhamad.irfan.si_tahu.ui.pengguna

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import muhamad.irfan.si_tahu.databinding.ActivityUserFormBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.EkstraAplikasi

class AktivitasFormPengguna : AktivitasDasar() {

    private lateinit var binding: ActivityUserFormBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var editingUserId: String? = null
    private val roleOptions = listOf("ADMIN", "KASIR")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Form Pengguna", "Tambah atau edit pengguna")

        editingUserId = intent.getStringExtra(EkstraAplikasi.EXTRA_USER_ID)

        binding.spRole.adapter = AdapterSpinner.stringAdapter(this, roleOptions)
        binding.cbActive.isChecked = true
        binding.btnResetPassword.visibility = View.GONE

        binding.btnSave.setOnClickListener {
            saveUser()
        }

        binding.btnResetPassword.setOnClickListener {
            sendResetPassword()
        }

        if (!editingUserId.isNullOrBlank()) {
            loadEditingUser(editingUserId!!)
        }
    }

    private fun loadEditingUser(userId: String) {
        firestore.collection("pengguna")
            .document(userId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    showMessage("Data pengguna tidak ditemukan.")
                    return@addOnSuccessListener
                }

                binding.etName.setText(doc.getString("namaPengguna").orEmpty())
                binding.etEmail.setText(doc.getString("email").orEmpty())
                binding.etPhone.setText(doc.getString("nomorTelepon").orEmpty())
                binding.cbActive.isChecked = doc.getBoolean("aktif") ?: true

                val role = doc.getString("peranAsli").orEmpty().ifBlank { "KASIR" }
                val roleIndex = roleOptions.indexOf(role).takeIf { it >= 0 } ?: 0
                binding.spRole.setSelection(roleIndex)

                binding.etEmail.isEnabled = false
                binding.etPassword.isEnabled = false
                binding.etPassword.setText("")
                binding.etPassword.hint = "Password tidak diubah dari form ini"
                binding.btnResetPassword.visibility = View.VISIBLE
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat pengguna: ${e.message}")
            }
    }

    private fun saveUser() {
        clearErrors()

        val namaPengguna = binding.etName.text?.toString()?.trim().orEmpty()
        val email = binding.etEmail.text?.toString()?.trim()?.lowercase().orEmpty()
        val nomorTelepon = binding.etPhone.text?.toString()?.trim().orEmpty()
        val passwordInput = binding.etPassword.text?.toString()?.trim().orEmpty()
        val peranAsli = binding.spRole.selectedItem?.toString().orEmpty().ifBlank { "KASIR" }
        val aktif = binding.cbActive.isChecked

        if (namaPengguna.isBlank()) {
            binding.etName.error = "Nama wajib diisi"
            binding.etName.requestFocus()
            return
        }

        if (email.isBlank()) {
            binding.etEmail.error = "Email wajib diisi"
            binding.etEmail.requestFocus()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Format email tidak valid"
            binding.etEmail.requestFocus()
            return
        }

        if (nomorTelepon.isBlank()) {
            binding.etPhone.error = "Nomor telepon wajib diisi"
            binding.etPhone.requestFocus()
            return
        }

        if (editingUserId.isNullOrBlank()) {
            if (passwordInput.isBlank()) {
                binding.etPassword.error = "Password wajib diisi"
                binding.etPassword.requestFocus()
                return
            }

            if (passwordInput.length < 6) {
                binding.etPassword.error = "Password minimal 6 karakter"
                binding.etPassword.requestFocus()
                return
            }
        }

        setFormLoading(true)

        firestore.collection("pengguna")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { snapshot ->
                val duplicateExists = snapshot.documents.any { it.id != editingUserId }

                if (duplicateExists) {
                    setFormLoading(false)
                    binding.etEmail.error = "Email sudah dipakai pengguna lain"
                    binding.etEmail.requestFocus()
                    return@addOnSuccessListener
                }

                if (editingUserId.isNullOrBlank()) {
                    createAuthUserThenPersist(
                        namaPengguna = namaPengguna,
                        email = email,
                        nomorTelepon = nomorTelepon,
                        password = passwordInput,
                        peranAsli = peranAsli,
                        aktif = aktif
                    )
                } else {
                    persistUser(
                        userId = editingUserId!!,
                        authUid = null,
                        namaPengguna = namaPengguna,
                        email = email,
                        nomorTelepon = nomorTelepon,
                        peranAsli = peranAsli,
                        aktif = aktif,
                        isNew = false
                    )
                }
            }
            .addOnFailureListener { e ->
                setFormLoading(false)
                showMessage("Gagal memeriksa email pengguna: ${e.message}")
            }
    }

    private fun createAuthUserThenPersist(
        namaPengguna: String,
        email: String,
        nomorTelepon: String,
        password: String,
        peranAsli: String,
        aktif: Boolean
    ) {
        val secondaryAppName = "user_creator_${System.currentTimeMillis()}"
        val primaryApp = FirebaseApp.getInstance()
        val secondaryApp = FirebaseApp.initializeApp(this, primaryApp.options, secondaryAppName)

        if (secondaryApp == null) {
            setFormLoading(false)
            showMessage("Gagal menyiapkan auth pengguna.")
            return
        }

        val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)

        secondaryAuth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val firebaseUser = result.user
                if (firebaseUser == null) {
                    cleanupSecondaryAuth(secondaryAuth, secondaryApp)
                    setFormLoading(false)
                    showMessage("Akun login gagal dibuat.")
                    return@addOnSuccessListener
                }

                generateNextUserId(
                    peranAsli = peranAsli,
                    onResult = { newId ->
                        persistUser(
                            userId = newId,
                            authUid = firebaseUser.uid,
                            namaPengguna = namaPengguna,
                            email = email,
                            nomorTelepon = nomorTelepon,
                            peranAsli = peranAsli,
                            aktif = aktif,
                            isNew = true,
                            onSuccess = {
                                cleanupSecondaryAuth(secondaryAuth, secondaryApp)
                            },
                            onFailure = { firestoreError ->
                                rollbackCreatedAuthUser(
                                    secondaryAuth = secondaryAuth,
                                    secondaryApp = secondaryApp,
                                    failureMessage = "Auth berhasil dibuat, tapi Firestore gagal: ${firestoreError.message}"
                                )
                            }
                        )
                    },
                    onError = { e ->
                        rollbackCreatedAuthUser(
                            secondaryAuth = secondaryAuth,
                            secondaryApp = secondaryApp,
                            failureMessage = "Gagal membuat ID pengguna: ${e.message}"
                        )
                    }
                )
            }
            .addOnFailureListener { e ->
                cleanupSecondaryAuth(secondaryAuth, secondaryApp)
                setFormLoading(false)
                showMessage(readableAuthError(e))
            }
    }

    private fun persistUser(
        userId: String,
        authUid: String?,
        namaPengguna: String,
        email: String,
        nomorTelepon: String,
        peranAsli: String,
        aktif: Boolean,
        isNew: Boolean,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        val now = Timestamp.now()
        val modeAplikasi = if (peranAsli == "ADMIN") "ADMIN" else "KASIR"

        val data = hashMapOf<String, Any?>(
            "namaPengguna" to namaPengguna,
            "email" to email,
            "nomorTelepon" to nomorTelepon,
            "peranAsli" to peranAsli,
            "modeAplikasi" to modeAplikasi,
            "aktif" to aktif,
            "bolehMasuk" to aktif,
            "diperbaruiPada" to now
        )

        if (authUid != null) {
            data["authUid"] = authUid
        }

        if (isNew) {
            data["dibuatPada"] = now
        }

        firestore.collection("pengguna")
            .document(userId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                onSuccess?.invoke()
                setFormLoading(false)
                showMessage("Pengguna berhasil disimpan.")
                startActivity(Intent(this, AktivitasDaftarPengguna::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                onFailure?.invoke(e)
                setFormLoading(false)
                if (onFailure == null) {
                    showMessage("Gagal menyimpan pengguna: ${e.message}")
                }
            }
    }

    private fun sendResetPassword() {
        val email = binding.etEmail.text?.toString()?.trim()?.lowercase().orEmpty()

        if (email.isBlank()) {
            binding.etEmail.error = "Email pengguna tidak ditemukan"
            binding.etEmail.requestFocus()
            return
        }

        binding.btnResetPassword.isEnabled = false

        FirebaseAuth.getInstance()
            .sendPasswordResetEmail(email)
            .addOnSuccessListener {
                binding.btnResetPassword.isEnabled = true
                showMessage("Link reset password berhasil dikirim ke $email")
            }
            .addOnFailureListener { e ->
                binding.btnResetPassword.isEnabled = true
                showMessage("Gagal mengirim reset password: ${e.message}")
            }
    }

    private fun rollbackCreatedAuthUser(
        secondaryAuth: FirebaseAuth,
        secondaryApp: FirebaseApp,
        failureMessage: String
    ) {
        val createdUser = secondaryAuth.currentUser
        if (createdUser == null) {
            cleanupSecondaryAuth(secondaryAuth, secondaryApp)
            setFormLoading(false)
            showMessage(failureMessage)
            return
        }

        createdUser.delete()
            .addOnCompleteListener {
                cleanupSecondaryAuth(secondaryAuth, secondaryApp)
                setFormLoading(false)
                showMessage(failureMessage)
            }
    }

    private fun cleanupSecondaryAuth(
        secondaryAuth: FirebaseAuth,
        secondaryApp: FirebaseApp
    ) {
        try {
            secondaryAuth.signOut()
        } catch (_: Exception) {
        }

        try {
            secondaryApp.delete()
        } catch (_: Exception) {
        }
    }

    private fun readableAuthError(error: Exception): String {
        return when (error) {
            is FirebaseAuthWeakPasswordException -> {
                "Password terlalu lemah. Minimal 6 karakter."
            }
            is FirebaseAuthInvalidCredentialsException -> {
                "Email tidak valid."
            }
            is FirebaseAuthUserCollisionException -> {
                "Email sudah terdaftar di Authentication."
            }
            else -> {
                "Gagal membuat akun login: ${error.message}"
            }
        }
    }

    private fun generateNextUserId(
        peranAsli: String,
        onResult: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val prefix = if (peranAsli == "ADMIN") "usr_admin_" else "usr_kasir_"

        firestore.collection("pengguna")
            .get()
            .addOnSuccessListener { snapshot ->
                val lastNumber = snapshot.documents.mapNotNull { doc ->
                    if (doc.id.startsWith(prefix)) {
                        doc.id.removePrefix(prefix).toIntOrNull()
                    } else {
                        null
                    }
                }.maxOrNull() ?: 0

                val nextNumber = lastNumber + 1
                onResult(prefix + "%02d".format(nextNumber))
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    private fun clearErrors() {
        binding.etName.error = null
        binding.etEmail.error = null
        binding.etPhone.error = null
        binding.etPassword.error = null
    }

    private fun setFormLoading(isLoading: Boolean) {
        binding.etName.isEnabled = !isLoading
        binding.etPhone.isEnabled = !isLoading
        binding.spRole.isEnabled = !isLoading
        binding.cbActive.isEnabled = !isLoading
        binding.btnSave.isEnabled = !isLoading

        if (editingUserId.isNullOrBlank()) {
            binding.etEmail.isEnabled = !isLoading
            binding.etPassword.isEnabled = !isLoading
        } else {
            binding.btnResetPassword.isEnabled = !isLoading
        }

        binding.btnSave.text = if (isLoading) "Menyimpan..." else "Simpan Pengguna"
    }
}