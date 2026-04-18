package muhamad.irfan.si_tahu.ui.utama

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.FragmentSalesMenuBinding
import muhamad.irfan.si_tahu.ui.dasar.FragmenDasar
import muhamad.irfan.si_tahu.ui.penjualan.AktivitasPenjualanRumahan
import muhamad.irfan.si_tahu.ui.penjualan.AktivitasRekapPasar
import muhamad.irfan.si_tahu.ui.penjualan.AktivitasRiwayatPenjualan
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class FragmenPenjualan : FragmenDasar(R.layout.fragment_sales_menu) {

    private var _binding: FragmentSalesMenuBinding? = null
    private val binding get() = _binding!!

    private val recentAdapter by lazy {
        AdapterBarisUmum(onItemClick = ::openDetailPenjualan)
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
        if (_binding != null) {
            renderSummary()
        }
    }

    private fun setupView() = with(binding) {
        rvSalesRecent.layoutManager = LinearLayoutManager(requireContext())
        rvSalesRecent.adapter = recentAdapter
    }

    private fun setupActions() = with(binding) {
        // Flow baru: page katalog -> cart action -> checkout page
        btnHomeSales.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasPenjualanRumahan::class.java))
        }

        btnMarketRecap.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasRekapPasar::class.java))
        }

        btnSalesHistory.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasRiwayatPenjualan::class.java))
        }
    }

    private fun renderSummary() {
        val currentBinding = _binding ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatRingkasanPenjualan() }
                .onSuccess { summary ->
                    currentBinding.tvSalesHeader.text = "Penjualan hari ini"
                    currentBinding.tvSalesTotal.text = Formatter.currency(summary.totalHariIni)
                    currentBinding.tvCashierTotal.text =
                        "Rumahan: ${Formatter.currency(summary.totalKasirHariIni)}"
                    currentBinding.tvRecapTotal.text =
                        "Pasar: ${Formatter.currency(summary.totalRekapHariIni)}"
                    currentBinding.tvSalesCount.text =
                        "Transaksi: ${summary.jumlahTransaksiHariIni}"
                    currentBinding.tvSoldQty.text =
                        "Qty terjual: ${summary.totalItemHariIni}"

                    val rows = summary.recentRows.map {
                        ItemBaris(
                            id = it.id,
                            title = it.title,
                            subtitle = it.subtitle,
                            badge = it.badge,
                            amount = it.amount,
                            tone = when (it.badge) {
                                "Rumahan" -> WarnaBaris.GREEN
                                "PASAR" -> WarnaBaris.BLUE
                                else -> WarnaBaris.GOLD
                            },
                            actionLabel = "⋮"
                        )
                    }

                    recentAdapter.submitList(
                        if (rows.isEmpty()) {
                            listOf(
                                ItemBaris(
                                    id = "empty-sales",
                                    title = "Belum ada transaksi penjualan",
                                    subtitle = "Penjualan Rumahan dan Rekap Pasar yang tersimpan akan tampil paling atas di sini",
                                    badge = "Kosong",
                                    amount = Formatter.currency(0),
                                    tone = WarnaBaris.GOLD
                                )
                            )
                        } else {
                            rows
                        }
                    )
                }
                .onFailure {
                    currentBinding.tvSalesHeader.text = "Penjualan hari ini"
                    currentBinding.tvSalesTotal.text = Formatter.currency(0)
                    currentBinding.tvCashierTotal.text = "Rumahan: ${Formatter.currency(0)}"
                    currentBinding.tvRecapTotal.text = "Pasar: ${Formatter.currency(0)}"
                    currentBinding.tvSalesCount.text = "Transaksi: 0"
                    currentBinding.tvSoldQty.text = "Qty terjual: 0"

                    recentAdapter.submitList(
                        listOf(
                            ItemBaris(
                                id = "error-sales",
                                title = "Ringkasan belum bisa dimuat",
                                subtitle = it.message ?: "Terjadi kendala saat mengambil data penjualan",
                                badge = "Error",
                                amount = "",
                                tone = WarnaBaris.RED
                            )
                        )
                    )

                    showMessage(requireView(), it.message ?: "Gagal memuat ringkasan penjualan")
                }
        }
    }

    private fun openDetailPenjualan(item: ItemBaris) {
        if (item.id == "empty-sales" || item.id == "error-sales") return

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.buildReceiptText(item.id) }
                .onSuccess { detail ->
                    showReceiptModal("Detail Penjualan", detail, "Bagikan")
                }
                .onFailure {
                    showMessage(requireView(), it.message ?: "Gagal memuat detail penjualan")
                }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}