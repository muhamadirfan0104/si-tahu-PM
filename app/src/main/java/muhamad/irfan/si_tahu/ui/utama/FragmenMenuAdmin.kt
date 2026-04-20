package muhamad.irfan.si_tahu.ui.utama

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.PenggunaFirestoreCompat
import muhamad.irfan.si_tahu.data.ProfilPenggunaFirebase
import muhamad.irfan.si_tahu.databinding.FragmentAdminMenuBinding
import muhamad.irfan.si_tahu.ui.dasar.FragmenDasar
import muhamad.irfan.si_tahu.ui.harga.AktivitasDaftarHarga
import muhamad.irfan.si_tahu.ui.masuk.AktivitasMasuk
import muhamad.irfan.si_tahu.ui.parameter.AktivitasDaftarParameter
import muhamad.irfan.si_tahu.ui.pengaturan.AktivitasPengaturanUsaha
import muhamad.irfan.si_tahu.ui.pengguna.AktivitasDaftarPengguna
import muhamad.irfan.si_tahu.ui.produk.AktivitasDaftarProduk

class FragmenMenuAdmin : FragmenDasar(R.layout.fragment_admin_menu) {

    private var _binding: FragmentAdminMenuBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var currentUserDoc: DocumentSnapshot? = null
    private var currentProfile: ProfilPenggunaFirebase? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentAdminMenuBinding.bind(view)

        setupInitialState()
        setupActions()
        loadProfile()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) loadProfile()
    }

    private fun setupInitialState() {
        val currentBinding = _binding ?: return
        currentBinding.btnExpenses.visibility = View.GONE
        currentBinding.btnReport.visibility = View.GONE
        currentBinding.btnTransactions.visibility = View.GONE
        currentBinding.btnSettings.visibility = View.VISIBLE
        currentBinding.btnUsers.visibility = View.VISIBLE
        currentBinding.btnSwitchCashier.visibility = View.VISIBLE
        currentBinding.btnSwitchCashier.isEnabled = true
    }

    private fun setupActions() {
        val currentBinding = _binding ?: return

        currentBinding.btnProducts.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(Intent(safeContext, AktivitasDaftarProduk::class.java))
        }

        currentBinding.btnPrices.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(Intent(safeContext, AktivitasDaftarHarga::class.java))
        }

        currentBinding.btnParameters.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(Intent(safeContext, AktivitasDaftarParameter::class.java))
        }

        currentBinding.btnUsers.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(Intent(safeContext, AktivitasDaftarPengguna::class.java))
        }

        currentBinding.btnSettings.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(Intent(safeContext, AktivitasPengaturanUsaha::class.java))
        }

        currentBinding.btnSwitchCashier.setOnClickListener { anchor ->
            switchModeToCashier(anchor)
        }

        currentBinding.btnLogout.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            auth.signOut()
            launchActivitySafely(Intent(safeContext, AktivitasMasuk::class.java), finishCurrent = true)
        }
    }

    private fun loadProfile() {
        val currentBinding = _binding ?: return
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            currentUserDoc = null
            currentProfile = null
            setupInitialState()
            return
        }

        runCatching {
            PenggunaFirestoreCompat.findByAuthUid(
                firestore = firestore,
                authUid = uid,
                onFound = { doc ->
                    PenggunaFirestoreCompat.migrateLegacyDocIfNeeded(
                        firestore = firestore,
                        doc = doc,
                        authUid = uid,
                        onComplete = { syncedDoc ->
                            val activeBinding = _binding ?: return@migrateLegacyDocIfNeeded
                            val profile = syncedDoc.toObject(ProfilPenggunaFirebase::class.java)
                                ?: ProfilPenggunaFirebase()

                            currentUserDoc = syncedDoc
                            currentProfile = profile
                            applyVisibility(profile)
                            activeBinding.btnSwitchCashier.isEnabled = true
                        },
                        onError = {
                            currentUserDoc = null
                            currentProfile = null
                            setupInitialState()
                            showMessage(currentBinding.root, "Gagal membaca profil admin")
                        }
                    )
                },
                onNotFound = {
                    currentUserDoc = null
                    currentProfile = null
                    setupInitialState()
                },
                onError = {
                    currentUserDoc = null
                    currentProfile = null
                    setupInitialState()
                }
            )
        }.onFailure {
            currentUserDoc = null
            currentProfile = null
            setupInitialState()
        }
    }

    private fun applyVisibility(profile: ProfilPenggunaFirebase) {
        val currentBinding = _binding ?: return
        val isAdminAsli = profile.peranAsli.trim().uppercase() == "ADMIN"

        currentBinding.btnSettings.visibility = if (isAdminAsli) View.VISIBLE else View.GONE
        currentBinding.btnUsers.visibility = if (isAdminAsli) View.VISIBLE else View.GONE
        currentBinding.btnSwitchCashier.visibility = if (isAdminAsli) View.VISIBLE else View.GONE
        currentBinding.btnSwitchCashier.isEnabled = true
    }

    private fun switchModeToCashier(anchor: View) {
        val doc = currentUserDoc
        val profile = currentProfile
        val currentBinding = _binding ?: return

        if (doc == null || profile == null) {
            showMessage(anchor, "Profil admin belum siap.")
            return
        }

        val isAdminAsli = profile.peranAsli.trim().uppercase() == "ADMIN"
        if (!isAdminAsli) {
            currentBinding.btnSwitchCashier.visibility = View.GONE
            showMessage(anchor, "Hanya admin yang bisa pindah ke mode kasir.")
            return
        }

        currentBinding.btnSwitchCashier.isEnabled = false

        doc.reference
            .update("modeAplikasi", "KASIR")
            .addOnSuccessListener {
                val safeContext = context ?: return@addOnSuccessListener
                launchActivitySafely(AktivitasUtamaKasir.intent(safeContext), finishCurrent = true)
            }
            .addOnFailureListener { e ->
                val activeBinding = _binding ?: return@addOnFailureListener
                activeBinding.btnSwitchCashier.isEnabled = true
                showMessage(anchor, "Gagal ganti mode ke kasir: ${e.message}")
            }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
