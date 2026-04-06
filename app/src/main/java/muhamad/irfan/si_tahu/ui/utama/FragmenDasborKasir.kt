// FragmenDasborKasir.kt
package muhamad.irfan.si_tahu.ui.utama

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.databinding.FragmentCashierDashboardBinding
import muhamad.irfan.si_tahu.ui.dasar.FragmenDasar
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum

class FragmenDasborKasir : FragmenDasar(R.layout.fragment_cashier_dashboard) {
    private var _binding: FragmentCashierDashboardBinding? = null
    private val binding get() = _binding!!

    private val topAdapter = AdapterBarisUmum(onItemClick = {})
    private val recentAdapter = AdapterBarisUmum(onItemClick = {})

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentCashierDashboardBinding.bind(view)

        binding.rvTopProducts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTopProducts.adapter = topAdapter
        binding.rvRecentCashierSales.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentCashierSales.adapter = recentAdapter

        binding.btnNewSale.text = "Buka Menu"
        binding.btnNewSale.setOnClickListener {
            (requireActivity() as? AktivitasUtamaKasir)?.openTab(R.id.nav_cashier_menu)
        }
    }

    override fun onResume() {
        super.onResume()
        renderEmptyState()
    }

    private fun renderEmptyState() {
        binding.tvTodaySales.text = "-"
        binding.tvTodayCount.text = "-"

        topAdapter.submitList(emptyList())
        recentAdapter.submitList(emptyList())
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}