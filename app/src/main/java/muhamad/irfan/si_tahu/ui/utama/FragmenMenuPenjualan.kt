package muhamad.irfan.si_tahu.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.RepositoriLokal
import muhamad.irfan.si_tahu.databinding.FragmentSalesMenuBinding
import muhamad.irfan.si_tahu.ui.base.FragmenDasar
import muhamad.irfan.si_tahu.ui.common.AdapterBarisUmum
import muhamad.irfan.si_tahu.ui.sales.AktivitasRekapPasar
import muhamad.irfan.si_tahu.ui.sales.AktivitasRiwayatPenjualan
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class FragmenMenuPenjualan : FragmenDasar(R.layout.fragment_sales_menu) {
    private var _binding: FragmentSalesMenuBinding? = null
    private val binding get() = _binding!!
    private val adapter = AdapterBarisUmum(onItemClick = { row -> showReceiptModal("Struk ${row.id}", RepositoriLokal.buildReceiptText(row.id)) })

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSalesMenuBinding.bind(view)
        binding.rvSalesRecent.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSalesRecent.adapter = adapter
        binding.btnMarketRecap.setOnClickListener { startActivity(Intent(requireContext(), AktivitasRekapPasar::class.java)) }
        binding.btnSalesHistory.setOnClickListener { startActivity(Intent(requireContext(), AktivitasRiwayatPenjualan::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        val latest = RepositoriLokal.latestDateOnly()
        val todaySales = RepositoriLokal.allSales().filter { Formatter.toDateOnly(it.date) == latest }
        val cashierTotal = todaySales.filter { it.source == "KASIR" }.sumOf { it.total }
        val recapTotal = todaySales.filter { it.source != "KASIR" }.sumOf { it.total }
        val soldQty = todaySales.sumOf { sale -> sale.items.sumOf { it.qty } }
        binding.tvSalesTotal.text = Formatter.currency(cashierTotal + recapTotal)
        binding.tvCashierTotal.text = "Kasir: ${Formatter.currency(cashierTotal)}"
        binding.tvRecapTotal.text = "Rekap pasar: ${Formatter.currency(recapTotal)}"
        binding.tvSalesCount.text = "Transaksi: ${todaySales.size}"
        binding.tvSoldQty.text = "Produk terjual: ${soldQty} pcs"

        adapter.submitList(todaySales.take(4).map { sale ->
            ItemBaris(
                id = sale.id,
                title = sale.id,
                subtitle = sale.source + " • " + sale.items.joinToString(", ") {
                    (RepositoriLokal.getProduct(it.productId)?.name ?: "Produk") + " x" + it.qty
                },
                badge = sale.paymentMethod,
                amount = Formatter.currency(sale.total),
                tone = if (sale.source == "KASIR") WarnaBaris.GOLD else WarnaBaris.BLUE
            )
        })
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
