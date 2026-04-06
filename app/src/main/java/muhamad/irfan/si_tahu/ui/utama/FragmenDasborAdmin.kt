package muhamad.irfan.si_tahu.ui.utama

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.databinding.FragmentAdminDashboardBinding
import muhamad.irfan.si_tahu.ui.dasar.FragmenDasar
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum

class FragmenDasborAdmin : FragmenDasar(R.layout.fragment_admin_dashboard) {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val lowStockAdapter = AdapterBarisUmum(onItemClick = {})
    private val recentAdapter = AdapterBarisUmum(onItemClick = {})

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentAdminDashboardBinding.bind(view)

        setupRecyclerView()
        setupStaticView()
        loadBusinessSettings()
    }

    override fun onResume() {
        super.onResume()
        renderEmptyState()
        loadBusinessSettings()
    }

    private fun setupRecyclerView() {
        binding.rvLowStock.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLowStock.adapter = lowStockAdapter

        binding.rvRecentTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentTransactions.adapter = recentAdapter
    }

    private fun setupStaticView() {
        binding.btnGoProduction.visibility = View.GONE
        binding.btnGoSales.visibility = View.GONE
        binding.btnGoStock.visibility = View.GONE
        binding.btnNewExpense.visibility = View.GONE
    }

    private fun loadBusinessSettings() {
        firestore.collection("pengaturan")
            .document("usaha")
            .get()
            .addOnSuccessListener { doc ->
                val currentBinding = _binding ?: return@addOnSuccessListener

                val namaUsaha = doc.getString("namaUsaha").orEmpty().ifBlank { "Tahu Berkah" }
                currentBinding.tvBusinessName.text = namaUsaha
            }
            .addOnFailureListener {
                val currentBinding = _binding ?: return@addOnFailureListener
                currentBinding.tvBusinessName.text = "Tahu Berkah"
            }
    }

    private fun renderEmptyState() {
        val currentBinding = _binding ?: return

        currentBinding.tvSummaryDate.text = "Ringkasan belum terhubung"
        currentBinding.tvSummarySales.text = "-"
        currentBinding.tvSummaryProduction.text = "Produksi: -"
        currentBinding.tvSummaryExpenses.text = "Pengeluaran: -"
        currentBinding.tvSummaryTransactions.text = "Transaksi: -"
        currentBinding.tvSummaryProfit.text = "Laba sementara: -"

        lowStockAdapter.submitList(emptyList())
        recentAdapter.submitList(emptyList())
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}