package muhamad.irfan.si_tahu.ui.utama

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.ProfilPenggunaFirebase
import muhamad.irfan.si_tahu.databinding.FragmentCashierMenuBinding
import muhamad.irfan.si_tahu.ui.dasar.FragmenDasar
import muhamad.irfan.si_tahu.ui.masuk.AktivitasMasuk

class FragmenMenuKasir : FragmenDasar(R.layout.fragment_cashier_menu) {

    private var _binding: FragmentCashierMenuBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var currentUserDoc: DocumentSnapshot? = null
    private var currentProfile: ProfilPenggunaFirebase? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentCashierMenuBinding.bind(view)

        setupInitialState()
        setupActions()
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    private fun setupInitialState() {
        binding.tvUserName.text = "-"
        binding.tvUserRole.text = "-"
        binding.btnBusinessSettings.visibility = View.GONE
        binding.btnSwitchAdmin.visibility = View.GONE
        binding.btnSwitchAdmin.isEnabled = true
    }

    private fun setupActions() {
        binding.btnSwitchAdmin.setOnClickListener { anchor ->
            switchModeToAdmin(anchor)
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
            val currentBinding = _binding ?: return
            currentBinding.tvUserName.text = "-"
            currentBinding.tvUserRole.text = "-"
            currentBinding.btnSwitchAdmin.visibility = View.GONE
            return
        }

        firestore.collection("pengguna")
            .whereEqualTo("authUid", uid)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val currentBinding = _binding ?: return@addOnSuccessListener
                val doc = snapshot.documents.firstOrNull()

                if (doc == null) {
                    currentUserDoc = null
                    currentProfile = null
                    currentBinding.tvUserName.text = "-"
                    currentBinding.tvUserRole.text = "-"
                    currentBinding.btnSwitchAdmin.visibility = View.GONE
                    showMessage(currentBinding.root, "Profil pengguna tidak ditemukan.")
                    return@addOnSuccessListener
                }

                val profile = doc.toObject(ProfilPenggunaFirebase::class.java)
                if (profile == null) {
                    currentUserDoc = null
                    currentProfile = null
                    currentBinding.tvUserName.text = "-"
                    currentBinding.tvUserRole.text = "-"
                    currentBinding.btnSwitchAdmin.visibility = View.GONE
                    showMessage(currentBinding.root, "Data profil pengguna tidak valid.")
                    return@addOnSuccessListener
                }

                currentUserDoc = doc
                currentProfile = profile

                bindProfile(profile)
            }
            .addOnFailureListener { e ->
                val currentBinding = _binding ?: return@addOnFailureListener
                currentUserDoc = null
                currentProfile = null
                currentBinding.tvUserName.text = "-"
                currentBinding.tvUserRole.text = "-"
                currentBinding.btnSwitchAdmin.visibility = View.GONE
                showMessage(currentBinding.root, "Gagal membaca profil pengguna: ${e.message}")
            }
    }

    private fun bindProfile(profile: ProfilPenggunaFirebase) {
        val currentBinding = _binding ?: return

        currentBinding.tvUserName.text = profile.namaPengguna.ifBlank { "-" }
        currentBinding.tvUserRole.text = profile.peranAsli.ifBlank { "-" }

        val isAdminAsli = profile.peranAsli.trim().uppercase() == "ADMIN"


        currentBinding.btnSwitchAdmin.visibility =
            if (isAdminAsli) View.VISIBLE else View.GONE
    }

    private fun switchModeToAdmin(anchor: View) {
        val currentBinding = _binding ?: return
        val profile = currentProfile
        val doc = currentUserDoc

        if (profile == null || doc == null) {
            showMessage(anchor, "Profil pengguna belum siap.")
            return
        }

        val isAdminAsli = profile.peranAsli.trim().uppercase() == "ADMIN"
        if (!isAdminAsli) {
            currentBinding.btnSwitchAdmin.visibility = View.GONE
            showMessage(anchor, "Kasir tidak boleh ganti ke admin.")
            return
        }

        currentBinding.btnSwitchAdmin.isEnabled = false

        doc.reference
            .update("modeAplikasi", "ADMIN")
            .addOnSuccessListener {
                startActivity(AktivitasUtamaAdmin.intent(requireContext()))
                requireActivity().finish()
            }
            .addOnFailureListener { e ->
                val b = _binding ?: return@addOnFailureListener
                b.btnSwitchAdmin.isEnabled = true
                showMessage(anchor, "Gagal ganti mode ke admin: ${e.message}")
            }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}