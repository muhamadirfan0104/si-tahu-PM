package muhamad.irfan.si_tahu.ui.utama

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.R
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
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    private fun setupInitialState() {
        binding.btnExpenses.visibility = View.GONE
        binding.btnReport.visibility = View.GONE
        binding.btnTransactions.visibility = View.GONE
        binding.btnSettings.visibility = View.VISIBLE
        binding.btnSwitchCashier.visibility = View.VISIBLE
        binding.btnSwitchCashier.isEnabled = true
    }

    private fun setupActions() {
        binding.btnProducts.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasDaftarProduk::class.java))
        }

        binding.btnPrices.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasDaftarHarga::class.java))
        }

        binding.btnParameters.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasDaftarParameter::class.java))
        }

        binding.btnUsers.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasDaftarPengguna::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasPengaturanUsaha::class.java))
        }

        binding.btnSwitchCashier.setOnClickListener { anchor ->
            switchModeToCashier(anchor)
        }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(requireContext(), AktivitasMasuk::class.java))
            requireActivity().finish()
        }
    }

    private fun loadProfile() {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            currentUserDoc = null
            currentProfile = null
            return
        }

        firestore.collection("pengguna")
            .whereEqualTo("authUid", uid)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val doc = snapshot.documents.firstOrNull()
                if (doc == null) {
                    currentUserDoc = null
                    currentProfile = null
                    showMessage(binding.root, "Profil admin tidak ditemukan.")
                    return@addOnSuccessListener
                }

                val profile = doc.toObject(ProfilPenggunaFirebase::class.java)
                if (profile == null) {
                    currentUserDoc = null
                    currentProfile = null
                    showMessage(binding.root, "Data profil admin tidak valid.")
                    return@addOnSuccessListener
                }

                currentUserDoc = doc
                currentProfile = profile
                applyVisibility(profile)
            }
            .addOnFailureListener { e ->
                currentUserDoc = null
                currentProfile = null
                showMessage(binding.root, "Gagal membaca profil admin: ${e.message}")
            }
    }

    private fun applyVisibility(profile: ProfilPenggunaFirebase) {
        val isAdminAsli = profile.peranAsli.trim().uppercase() == "ADMIN"

        binding.btnSettings.visibility = if (isAdminAsli) View.VISIBLE else View.GONE
        binding.btnUsers.visibility = if (isAdminAsli) View.VISIBLE else View.GONE
        binding.btnSwitchCashier.visibility = if (isAdminAsli) View.VISIBLE else View.GONE
    }

    private fun switchModeToCashier(anchor: View) {
        val doc = currentUserDoc
        val profile = currentProfile

        if (doc == null || profile == null) {
            showMessage(anchor, "Profil admin belum siap.")
            return
        }

        val isAdminAsli = profile.peranAsli.trim().uppercase() == "ADMIN"
        if (!isAdminAsli) {
            binding.btnSwitchCashier.visibility = View.GONE
            showMessage(anchor, "Hanya admin yang bisa pindah ke mode kasir.")
            return
        }

        binding.btnSwitchCashier.isEnabled = false

        doc.reference
            .update("modeAplikasi", "KASIR")
            .addOnSuccessListener {
                startActivity(AktivitasUtamaKasir.intent(requireContext()))
                requireActivity().finish()
            }
            .addOnFailureListener { e ->
                val currentBinding = _binding ?: return@addOnFailureListener
                currentBinding.btnSwitchCashier.isEnabled = true
                showMessage(anchor, "Gagal ganti mode ke kasir: ${e.message}")
            }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}