package muhamad.irfan.si_tahu.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.databinding.FragmentAdminDashboardBinding
import muhamad.irfan.si_tahu.ui.base.FragmenDasar
import muhamad.irfan.si_tahu.ui.common.AdapterBarisUmum
import muhamad.irfan.si_tahu.ui.expense.AktivitasFormPengeluaran

class FragmenDasborAdmin : FragmenDasar(R.layout.fragment_admin_dashboard) {
    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!

    private val lowStockAdapter = AdapterBarisUmum(onItemClick = {})
    private val recentAdapter = AdapterBarisUmum(onItemClick = {})

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentAdminDashboardBinding.bind(view)

        binding.rvLowStock.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLowStock.adapter = lowStockAdapter
        binding.rvRecentTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentTransactions.adapter = recentAdapter

        binding.btnGoProduction.setOnClickListener {
            (requireActivity() as? AktivitasUtamaAdmin)?.openTab(R.id.nav_admin_production)
        }
        binding.btnGoSales.setOnClickListener {
            (requireActivity() as? AktivitasUtamaAdmin)?.openTab(R.id.nav_admin_sales)
        }
        binding.btnGoStock.setOnClickListener {
            (requireActivity() as? AktivitasUtamaAdmin)?.openTab(R.id.nav_admin_stock)
        }
        binding.btnNewExpense.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasFormPengeluaran::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        renderEmptyState()
    }

    private fun renderEmptyState() {
        binding.tvSummaryDate.text = "Ringkasan belum terhubung"
        binding.tvSummarySales.text = "-"
        binding.tvSummaryProduction.text = "Produksi: -"
        binding.tvSummaryExpenses.text = "Pengeluaran: -"
        binding.tvSummaryTransactions.text = "Transaksi: -"
        binding.tvSummaryProfit.text = "Laba sementara: -"

        lowStockAdapter.submitList(emptyList())
        recentAdapter.submitList(emptyList())
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}