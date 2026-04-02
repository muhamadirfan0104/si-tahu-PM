package muhamad.irfan.si_tahupm.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahupm.R
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.FragmentSalesMenuBinding
import muhamad.irfan.si_tahupm.ui.base.BaseFragment
import muhamad.irfan.si_tahupm.ui.common.GenericRowAdapter
import muhamad.irfan.si_tahupm.ui.sales.MarketRecapActivity
import muhamad.irfan.si_tahupm.ui.sales.SalesHistoryActivity
import muhamad.irfan.si_tahupm.util.Formatters
import muhamad.irfan.si_tahupm.util.RowItem
import muhamad.irfan.si_tahupm.util.RowTone

class SalesMenuFragment : BaseFragment(R.layout.fragment_sales_menu) {
    private var _binding: FragmentSalesMenuBinding? = null
    private val binding get() = _binding!!
    private val adapter = GenericRowAdapter(onItemClick = { row -> showReceiptModal("Struk ${row.id}", DemoRepository.buildReceiptText(row.id)) })

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSalesMenuBinding.bind(view)
        binding.rvSalesRecent.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSalesRecent.adapter = adapter
        binding.btnMarketRecap.setOnClickListener { startActivity(Intent(requireContext(), MarketRecapActivity::class.java)) }
        binding.btnSalesHistory.setOnClickListener { startActivity(Intent(requireContext(), SalesHistoryActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        val latest = DemoRepository.latestDateOnly()
        val todaySales = DemoRepository.allSales().filter { Formatters.toDateOnly(it.date) == latest }
        val cashierTotal = todaySales.filter { it.source == "KASIR" }.sumOf { it.total }
        val recapTotal = todaySales.filter { it.source != "KASIR" }.sumOf { it.total }
        val soldQty = todaySales.sumOf { sale -> sale.items.sumOf { it.qty } }
        binding.tvSalesTotal.text = Formatters.currency(cashierTotal + recapTotal)
        binding.tvCashierTotal.text = "Kasir: ${Formatters.currency(cashierTotal)}"
        binding.tvRecapTotal.text = "Rekap pasar: ${Formatters.currency(recapTotal)}"
        binding.tvSalesCount.text = "Transaksi: ${todaySales.size}"
        binding.tvSoldQty.text = "Produk terjual: ${soldQty} pcs"

        adapter.submitList(todaySales.take(4).map { sale ->
            RowItem(
                id = sale.id,
                title = sale.id,
                subtitle = sale.source + " • " + sale.items.joinToString(", ") {
                    (DemoRepository.getProduct(it.productId)?.name ?: "Produk") + " x" + it.qty
                },
                badge = sale.paymentMethod,
                amount = Formatters.currency(sale.total),
                tone = if (sale.source == "KASIR") RowTone.GOLD else RowTone.BLUE
            )
        })
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
