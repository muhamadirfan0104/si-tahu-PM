package muhamad.irfan.si_tahu.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.ProfilPenggunaFirebase
import muhamad.irfan.si_tahu.databinding.FragmentCashierMenuBinding
import muhamad.irfan.si_tahu.ui.base.FragmenDasar
import muhamad.irfan.si_tahu.ui.login.AktivitasMasuk
import muhamad.irfan.si_tahu.ui.settings.AktivitasPengaturanUsaha

class FragmenMenuKasir : FragmenDasar(R.layout.fragment_cashier_menu) {
    private var _binding: FragmentCashierMenuBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentCashierMenuBinding.bind(view)

        binding.btnBusinessSettings.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasPengaturanUsaha::class.java))
        }

        binding.btnSwitchAdmin.setOnClickListener {
            switchModeToAdmin(view)
        }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(requireContext(), AktivitasMasuk::class.java))
            requireActivity().finish()
        }
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    private fun loadProfile() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("pengguna")
            .document(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val currentBinding = _binding ?: return@addOnSuccessListener
                val profile = snapshot.toObject(ProfilPenggunaFirebase::class.java) ?: return@addOnSuccessListener

                currentBinding.tvUserName.text = profile.namaPengguna.ifBlank { "-" }
                currentBinding.tvUserRole.text = profile.modeAplikasi.ifBlank { "-" }

                val canSwitchAdmin = profile.peranAsli.trim().uppercase() == "ADMIN"
                currentBinding.btnSwitchAdmin.visibility =
                    if (canSwitchAdmin) View.VISIBLE else View.GONE
            }
            .addOnFailureListener {
                val currentBinding = _binding ?: return@addOnFailureListener
                currentBinding.tvUserName.text = "-"
                currentBinding.tvUserRole.text = "-"
                currentBinding.btnSwitchAdmin.visibility = View.GONE
            }
    }

    private fun switchModeToAdmin(anchor: View) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            showMessage(anchor, "Session login tidak ditemukan.")
            return
        }

        val currentBinding = _binding ?: return
        currentBinding.btnSwitchAdmin.isEnabled = false

        firestore.collection("pengguna")
            .document(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val profile = snapshot.toObject(ProfilPenggunaFirebase::class.java)
                val isAdminAsli = profile?.peranAsli?.trim()?.uppercase() == "ADMIN"

                if (!isAdminAsli) {
                    val b = _binding ?: return@addOnSuccessListener
                    b.btnSwitchAdmin.isEnabled = true
                    b.btnSwitchAdmin.visibility = View.GONE
                    showMessage(anchor, "Kasir tidak boleh ganti ke admin.")
                    return@addOnSuccessListener
                }

                firestore.collection("pengguna")
                    .document(uid)
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
            .addOnFailureListener { e ->
                val b = _binding ?: return@addOnFailureListener
                b.btnSwitchAdmin.isEnabled = true
                showMessage(anchor, "Gagal membaca profil pengguna: ${e.message}")
            }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}