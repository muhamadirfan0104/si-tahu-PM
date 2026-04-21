package muhamad.irfan.si_tahu.ui.utama

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.FragmentCashierDashboardBinding
import muhamad.irfan.si_tahu.ui.dasar.FragmenDasar
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class FragmenDasborKasir : FragmenDasar(R.layout.fragment_cashier_dashboard) {

    private var _binding: FragmentCashierDashboardBinding? = null
    private val binding get() = _binding!!

    private val topAdapter = AdapterBarisUmum(onItemClick = {})
    private val recentAdapter = AdapterBarisUmum(onItemClick = ::openRecentSale)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentCashierDashboardBinding.bind(view)

        binding.rvTopProducts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTopProducts.adapter = topAdapter

        binding.rvRecentCashierSales.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentCashierSales.adapter = recentAdapter

        binding.btnNewSale.text = "Transaksi Baru"
        binding.btnNewSale.setOnClickListener {
            (activity as? AktivitasUtamaKasir)?.openTab(R.id.nav_cashier_sale)
        }

        renderDashboard()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) renderDashboard()
    }

    private fun renderDashboard() {
        val currentBinding = _binding ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatRingkasanKasir() }
                .onSuccess { summary ->
                    currentBinding.tvTodaySales.text = Formatter.currency(summary.totalHariIni)
                    currentBinding.tvTodayCount.text = summary.jumlahTransaksiHariIni.toString()

                    topAdapter.submitList(
                        if (summary.topProducts.isEmpty()) {
                            listOf(
                                ItemBaris(
                                    id = "empty-top",
                                    title = "Belum ada penjualan hari ini",
                                    subtitle = "Produk terlaris akan muncul setelah transaksi kasir masuk",
                                    badge = "Kosong",
                                    amount = Formatter.currency(0),
                                    tone = WarnaBaris.GOLD
                                )
                            )
                        } else {
                            summary.topProducts.map {
                                ItemBaris(
                                    id = it.id,
                                    title = it.title,
                                    subtitle = it.subtitle,
                                    badge = it.badge,
                                    amount = it.amount,
                                    tone = WarnaBaris.GREEN
                                )
                            }
                        }
                    )

                    recentAdapter.submitList(
                        if (summary.recentRows.isEmpty()) {
                            listOf(
                                ItemBaris(
                                    id = "empty-recent",
                                    title = "Belum ada transaksi kasir",
                                    subtitle = "Riwayat terakhir akan tampil di sini",
                                    badge = "Kosong",
                                    amount = Formatter.currency(0),
                                    tone = WarnaBaris.GOLD
                                )
                            )
                        } else {
                            summary.recentRows.map {
                                ItemBaris(
                                    id = it.id,
                                    title = it.title,
                                    subtitle = it.subtitle,
                                    badge = it.badge,
                                    amount = it.amount,
                                    tone = WarnaBaris.BLUE
                                )
                            }
                        }
                    )
                }
                .onFailure {
                    currentBinding.tvTodaySales.text = Formatter.currency(0)
                    currentBinding.tvTodayCount.text = "0"
                    topAdapter.submitList(emptyList())
                    recentAdapter.submitList(emptyList())
                    val activeBinding = _binding ?: return@onFailure
                    showMessage(activeBinding.root, it.message ?: "Gagal memuat dashboard kasir")
                }
        }
    }

    private fun openRecentSale(item: ItemBaris) {
        if (item.id == "empty-top" || item.id == "empty-recent") return

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.buildReceiptText(item.id) }
                .onSuccess { detail ->
                    showReceiptModal("Detail Penjualan", detail, "Bagikan")
                }
                .onFailure {
                    val activeBinding = _binding ?: return@onFailure
                    showMessage(activeBinding.root, it.message ?: "Gagal memuat detail transaksi")
                }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}