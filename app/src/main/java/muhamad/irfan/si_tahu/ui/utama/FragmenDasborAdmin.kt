package muhamad.irfan.si_tahu.ui.utama

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.FragmentAdminDashboardBinding
import muhamad.irfan.si_tahu.ui.dasar.FragmenDasar
import muhamad.irfan.si_tahu.ui.penjualan.AktivitasMenuPenjualan
import muhamad.irfan.si_tahu.ui.produksi.AktivitasMenuProduksi
import muhamad.irfan.si_tahu.ui.stok.AktivitasMonitoringStok
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class FragmenDasborAdmin : FragmenDasar(R.layout.fragment_admin_dashboard) {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!

    private val lowStockAdapter = AdapterBarisUmum(onItemClick = {})
    private val recentAdapter = AdapterBarisUmum(onItemClick = ::openRecentDetail)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentAdminDashboardBinding.bind(view)

        setupRecyclerView()
        setupActions()
        renderDashboard()
    }

    override fun onResume() {
        super.onResume()
        renderDashboard()
    }

    private fun setupRecyclerView() {
        binding.rvLowStock.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLowStock.adapter = lowStockAdapter

        binding.rvRecentTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentTransactions.adapter = recentAdapter
    }

    private fun setupActions() {
        binding.btnGoProduction.visibility = View.VISIBLE
        binding.btnGoSales.visibility = View.VISIBLE
        binding.btnGoStock.visibility = View.VISIBLE
        binding.btnNewExpense.visibility = View.GONE

        binding.btnGoProduction.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasMenuProduksi::class.java))
        }
        binding.btnGoSales.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasMenuPenjualan::class.java))
        }
        binding.btnGoStock.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasMonitoringStok::class.java))
        }
    }

    private fun renderDashboard() {
        val currentBinding = _binding ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatRingkasanDashboard() }
                .onSuccess { dashboard ->
                    currentBinding.tvBusinessName.text = dashboard.namaUsaha
                    currentBinding.tvSummaryDate.text = Formatter.readableDate(dashboard.tanggalRingkasan)
                    currentBinding.tvSummarySales.text = Formatter.currency(dashboard.totalPenjualan)
                    currentBinding.tvSummaryProduction.text = "Produksi: ${dashboard.totalProduksi} pcs"
                    currentBinding.tvSummaryExpenses.text = Formatter.currency(dashboard.totalPengeluaran)
                    currentBinding.tvSummaryTransactions.visibility = View.VISIBLE
                    currentBinding.tvSummaryTransactions.text = "Transaksi: ${dashboard.totalTransaksi}"
                    currentBinding.tvSummaryProfit.text = Formatter.currency(dashboard.totalLaba)

                    lowStockAdapter.submitList(dashboard.lowStock.map { product ->
                        val status = when {
                            product.stock <= 0 -> "Habis"
                            product.stock <= product.minStock -> "Menipis"
                            else -> "Aman"
                        }
                        ItemBaris(
                            id = product.id,
                            title = product.name,
                            subtitle = "${product.code} • ${product.category}",
                            badge = status,
                            amount = "Stok ${product.stock} • Min ${product.minStock}",
                            tone = when (status) {
                                "Aman" -> WarnaBaris.GREEN
                                "Menipis" -> WarnaBaris.GOLD
                                else -> WarnaBaris.ORANGE
                            }
                        )
                    })
                    recentAdapter.submitList(dashboard.recentItems.map {
                        ItemBaris(
                            id = it.id,
                            title = it.title,
                            subtitle = it.subtitle,
                            amount = it.amount,
                            badge = it.badge,
                            tone = when (it.badge) {
                                "Rumahan", "Produksi Dasar" -> WarnaBaris.GREEN
                                "Konversi", "PASAR", "RESELLER" -> WarnaBaris.BLUE
                                else -> WarnaBaris.GOLD
                            }
                        )
                    })
                }
                .onFailure {
                    lowStockAdapter.submitList(emptyList())
                    recentAdapter.submitList(emptyList())
                    showMessage(requireView(), it.message ?: "Gagal memuat dashboard")
                }
        }
    }

    private fun openRecentDetail(item: ItemBaris) {
        viewLifecycleOwner.lifecycleScope.launch {
            val detail = RepositoriFirebaseUtama.buildTransactionDetailText(item.id, item.title)
            showDetailModal("Detail Aktivitas", detail)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
