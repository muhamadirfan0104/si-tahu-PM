package muhamad.irfan.si_tahupm.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahupm.R
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.FragmentAdminDashboardBinding
import muhamad.irfan.si_tahupm.ui.base.BaseFragment
import muhamad.irfan.si_tahupm.ui.common.GenericRowAdapter
import muhamad.irfan.si_tahupm.ui.expense.ExpenseFormActivity
import muhamad.irfan.si_tahupm.ui.stock.StockDetailActivity
import muhamad.irfan.si_tahupm.util.AppExtras
import muhamad.irfan.si_tahupm.util.Formatters
import muhamad.irfan.si_tahupm.util.RowItem
import muhamad.irfan.si_tahupm.util.RowTone

class AdminDashboardFragment : BaseFragment(R.layout.fragment_admin_dashboard) {
    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!

    private val lowStockAdapter = GenericRowAdapter(onItemClick = { row -> openStockDetail(row.id) })
    private val recentAdapter = GenericRowAdapter(onItemClick = ::openRecentDetail)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminDashboardBinding.bind(view)

        binding.rvLowStock.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLowStock.adapter = lowStockAdapter
        binding.rvRecentTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentTransactions.adapter = recentAdapter

        binding.btnGoProduction.setOnClickListener { (requireActivity() as? AdminMainActivity)?.openTab(R.id.nav_admin_production) }
        binding.btnGoSales.setOnClickListener { (requireActivity() as? AdminMainActivity)?.openTab(R.id.nav_admin_sales) }
        binding.btnGoStock.setOnClickListener { (requireActivity() as? AdminMainActivity)?.openTab(R.id.nav_admin_stock) }
        binding.btnNewExpense.setOnClickListener { startActivity(Intent(requireContext(), ExpenseFormActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        val dateOnly = DemoRepository.latestDateOnly()
        val summary = DemoRepository.daySummary(dateOnly)
        binding.tvSummaryDate.text = "Ringkasan ${Formatters.readableDate(dateOnly)}"
        binding.tvSummarySales.text = Formatters.currency(summary.totalSales)
        binding.tvSummaryProduction.text = "Produksi: ${summary.totalProduction} pcs"
        binding.tvSummaryExpenses.text = "Pengeluaran: ${Formatters.currency(summary.totalExpenses)}"
        binding.tvSummaryTransactions.text = "Transaksi: ${summary.transactionCount}"
        binding.tvSummaryProfit.text = "Laba sementara: ${Formatters.currency(summary.totalProfit)}"

        lowStockAdapter.submitList(DemoRepository.lowStockProducts().map {
            val tone = when (DemoRepository.productStatus(it)) {
                "Aman" -> RowTone.GREEN
                "Menipis" -> RowTone.GOLD
                else -> RowTone.ORANGE
            }
            RowItem(
                id = it.id,
                title = it.name,
                subtitle = "Stok ${it.stock} ${it.unit} • minimum ${it.minStock}",
                badge = DemoRepository.productStatus(it),
                amount = Formatters.currency(DemoRepository.defaultChannel(it)?.price ?: 0),
                tone = tone
            )
        })

        recentAdapter.submitList(DemoRepository.transactions().take(4).map {
            RowItem(
                id = it.id,
                title = it.id,
                subtitle = it.type + " • " + it.subtitle,
                badge = it.type,
                amount = it.valueText,
                tone = when (it.type) {
                    "Produksi" -> RowTone.GREEN
                    "Konversi" -> RowTone.BLUE
                    "Pengeluaran" -> RowTone.ORANGE
                    "Adjustment" -> RowTone.ORANGE
                    else -> RowTone.GOLD
                }
            )
        })
    }

    private fun openRecentDetail(row: RowItem) {
        if (row.badge == "Penjualan" || row.badge == "Rekap Pasar") {
            showReceiptModal("Struk ${row.id}", DemoRepository.buildReceiptText(row.id))
        } else {
            showDetailModal(row.title, DemoRepository.buildTransactionDetailText(row.id, row.badge))
        }
    }

    private fun openStockDetail(productId: String) {
        startActivity(Intent(requireContext(), StockDetailActivity::class.java).putExtra(AppExtras.EXTRA_PRODUCT_ID, productId))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
