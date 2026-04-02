package muhamad.irfan.si_tahupm.ui.main

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahupm.R
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.FragmentCashierDashboardBinding
import muhamad.irfan.si_tahupm.ui.base.BaseFragment
import muhamad.irfan.si_tahupm.ui.common.GenericRowAdapter
import muhamad.irfan.si_tahupm.util.Formatters
import muhamad.irfan.si_tahupm.util.RowItem
import muhamad.irfan.si_tahupm.util.RowTone

class CashierDashboardFragment : BaseFragment(R.layout.fragment_cashier_dashboard) {
    private var _binding: FragmentCashierDashboardBinding? = null
    private val binding get() = _binding!!

    private val topAdapter = GenericRowAdapter(onItemClick = {})
    private val recentAdapter = GenericRowAdapter(onItemClick = { row -> showDetailModal(row.title, DemoRepository.buildReceiptText(row.id)) })

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCashierDashboardBinding.bind(view)
        binding.rvTopProducts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTopProducts.adapter = topAdapter
        binding.rvRecentCashierSales.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentCashierSales.adapter = recentAdapter
        binding.btnNewSale.setOnClickListener { (requireActivity() as? CashierMainActivity)?.openTab(R.id.nav_cashier_sale) }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        val summary = DemoRepository.daySummary(DemoRepository.latestDateOnly())
        binding.tvTodaySales.text = Formatters.currency(summary.totalSales)
        binding.tvTodayCount.text = summary.transactionCount.toString()

        topAdapter.submitList(DemoRepository.topProducts(limit = 3, source = "KASIR").map {
            RowItem(
                id = it.productId,
                title = it.name,
                subtitle = "Total terjual hari ini",
                badge = "Terlaris",
                amount = "${it.qty} pcs",
                tone = RowTone.GOLD
            )
        })

        recentAdapter.submitList(DemoRepository.allSales().filter { it.source == "KASIR" }.take(3).map { sale ->
            RowItem(
                id = sale.id,
                title = sale.id,
                subtitle = sale.items.joinToString(", ") {
                    (DemoRepository.getProduct(it.productId)?.name ?: "Produk") + " x" + it.qty
                },
                badge = sale.paymentMethod,
                amount = Formatters.currency(sale.total),
                tone = RowTone.GOLD
            )
        })
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
