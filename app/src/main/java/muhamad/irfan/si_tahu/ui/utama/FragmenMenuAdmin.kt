package muhamad.irfan.si_tahu.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.databinding.FragmentAdminMenuBinding
import muhamad.irfan.si_tahu.ui.base.FragmenDasar
import muhamad.irfan.si_tahu.ui.expense.AktivitasDaftarPengeluaran
import muhamad.irfan.si_tahu.ui.history.AktivitasRiwayatTransaksi
import muhamad.irfan.si_tahu.ui.login.AktivitasMasuk
import muhamad.irfan.si_tahu.ui.parameter.AktivitasDaftarParameter
import muhamad.irfan.si_tahu.ui.price.AktivitasDaftarHarga
import muhamad.irfan.si_tahu.ui.product.AktivitasDaftarProduk
import muhamad.irfan.si_tahu.ui.report.AktivitasLaporan
import muhamad.irfan.si_tahu.ui.settings.AktivitasPengaturanUsaha
import muhamad.irfan.si_tahu.ui.user.AktivitasDaftarPengguna

class FragmenMenuAdmin : FragmenDasar(R.layout.fragment_admin_menu) {
    private var _binding: FragmentAdminMenuBinding? = null
    private val binding get() = _binding!!

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentAdminMenuBinding.bind(view)

        binding.btnProducts.setOnClickListener { startActivity(Intent(requireContext(), AktivitasDaftarProduk::class.java)) }
        binding.btnPrices.setOnClickListener { startActivity(Intent(requireContext(), AktivitasDaftarHarga::class.java)) }
        binding.btnParameters.setOnClickListener { startActivity(Intent(requireContext(), AktivitasDaftarParameter::class.java)) }
        binding.btnExpenses.setOnClickListener { startActivity(Intent(requireContext(), AktivitasDaftarPengeluaran::class.java)) }
        binding.btnReport.setOnClickListener { startActivity(Intent(requireContext(), AktivitasLaporan::class.java)) }
        binding.btnTransactions.setOnClickListener { startActivity(Intent(requireContext(), AktivitasRiwayatTransaksi::class.java)) }
        binding.btnSettings.setOnClickListener { startActivity(Intent(requireContext(), AktivitasPengaturanUsaha::class.java)) }
        binding.btnUsers.setOnClickListener { startActivity(Intent(requireContext(), AktivitasDaftarPengguna::class.java)) }

        binding.btnSwitchCashier.setOnClickListener {
            val uid = auth.currentUser?.uid
            if (uid.isNullOrBlank()) {
                showMessage(view, "Session login tidak ditemukan.")
                return@setOnClickListener
            }

            firestore.collection("pengguna")
                .document(uid)
                .update("modeAplikasi", "KASIR")
                .addOnSuccessListener {
                    startActivity(AktivitasUtamaKasir.intent(requireContext()))
                    requireActivity().finish()
                }
                .addOnFailureListener { e ->
                    showMessage(view, "Gagal ganti mode ke kasir: ${e.message}")
                }
        }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(requireContext(), AktivitasMasuk::class.java))
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}