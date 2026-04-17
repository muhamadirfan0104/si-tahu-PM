package muhamad.irfan.si_tahu.ui.utama

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.RepositoriLokal
import muhamad.irfan.si_tahu.databinding.FragmentSalesMenuBinding
import muhamad.irfan.si_tahu.ui.dasar.FragmenDasar
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class FragmenPenjualan : FragmenDasar(R.layout.fragment_sales_menu) {

    private var _binding: FragmentSalesMenuBinding? = null
    private val binding get() = _binding!!

    private val recentAdapter by lazy {
        AdapterBarisUmum(onItemClick = {})
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentSalesMenuBinding.bind(view)

        setupView()
        setupActions()
        renderSummary()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) renderSummary()
    }

    private fun setupView() = with(binding) {
        rvSalesRecent.layoutManager = LinearLayoutManager(requireContext())
        rvSalesRecent.adapter = recentAdapter
    }

    private fun setupActions() = with(binding) {
        btnHomeSales.setOnClickListener {
            showMessage(root, "Penjualan Rumahan kita pasang penuh di step berikutnya.")
        }

        btnMarketRecap.setOnClickListener {
            showMessage(root, "Rekap Penjualan Pasar kita pasang penuh di step berikutnya.")
        }

        btnSalesHistory.setOnClickListener {
            showMessage(root, "Riwayat penjualan detail kita lanjut setelah form penjualan jadi.")
        }
    }

    private fun renderSummary() = with(binding) {
        val sales = RepositoriLokal.db().sales

        val totalSales = sales.sumOf { it.total }
        val totalCount = sales.size
        val totalQty = sales.sumOf { sale -> sale.items.sumOf { it.qty } }

        tvSalesHeader.text = "Penjualan hari ini"
        tvSalesTotal.text = Formatter.currency(totalSales)
        tvCashierTotal.text = "Rumahan: ${Formatter.currency(totalSales)}"
        tvRecapTotal.text = "Pasar: ${Formatter.currency(0)}"
        tvSalesCount.text = "Transaksi: $totalCount"
        tvSoldQty.text = "Qty terjual: $totalQty"

        val items = sales.take(6).map { sale ->
            ItemBaris(
                id = sale.id,
                title = if (sale.source.equals("RUMAHAN", true)) "Penjualan Rumahan" else "Penjualan",
                subtitle = "${sale.items.size} item • ${sale.paymentMethod}",
                badge = sale.source,
                amount = Formatter.currency(sale.total),
                tone = WarnaBaris.BLUE
            )
        }

        recentAdapter.submitList(
            if (items.isEmpty()) {
                listOf(
                    ItemBaris(
                        id = "placeholder-sales",
                        title = "Belum ada transaksi",
                        subtitle = "Penjualan Rumahan dan Rekap Pasar akan kita pasang berikutnya",
                        badge = "Draft",
                        amount = Formatter.currency(0),
                        tone = WarnaBaris.GOLD
                    )
                )
            } else {
                items
            }
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}