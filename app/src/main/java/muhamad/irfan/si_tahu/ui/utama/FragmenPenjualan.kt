package muhamad.irfan.si_tahu.ui.utama

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.FragmentSalesMenuBinding
import muhamad.irfan.si_tahu.ui.dasar.FragmenDasar
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

    private var hasLoadedOnce = false
    private var isLoading = false
    private var lastLoadedAt = 0L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentSalesMenuBinding.bind(view)

        setupView()
        setupActions()
        renderSummary(forceInitialLoading = true)
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null && shouldRefreshData()) {
            renderSummary(forceInitialLoading = !hasLoadedOnce)
        }
    }

    private fun shouldRefreshData(): Boolean {
        if (!hasLoadedOnce) return true
        return SystemClock.elapsedRealtime() - lastLoadedAt > 30_000L
    }

    private fun setupView() = with(binding) {
        rvSalesRecent.layoutManager = LinearLayoutManager(requireContext())
        rvSalesRecent.adapter = recentAdapter
    }

    private fun setupActions() = with(binding) {
        btnHomeSales.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(
                AktivitasRiwayatPenjualan.intentRiwayatRumahanAdmin(safeContext)
            )
        }

        btnMarketRecap.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(Intent(safeContext, AktivitasRekapPasar::class.java))
        }

        btnSalesHistory.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(
                AktivitasRiwayatPenjualan.intentRiwayatSemuaAdmin(safeContext)
            )
        }

        btnSalesQuickHistory.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(
                AktivitasRiwayatPenjualan.intentRiwayatRumahanAdmin(safeContext)
            )
        }
    }

    private fun renderSummary(forceInitialLoading: Boolean) {
        val currentBinding = _binding ?: return
        if (isLoading) return

        isLoading = true
        setLoadingState(showLoading = forceInitialLoading && !hasLoadedOnce)

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatRingkasanPenjualan() }
                .onSuccess { summary ->
                    val safeBinding = _binding ?: return@onSuccess
                    hasLoadedOnce = true
                    lastLoadedAt = SystemClock.elapsedRealtime()

                    safeBinding.tvSalesTitle.text = "Penjualan hari ini"
                    safeBinding.tvSalesTotal.text = Formatter.currency(summary.totalHariIni)
                    safeBinding.tvCashierTotal.text =
                        "Rumahan tercatat: ${Formatter.currency(summary.totalKasirHariIni)}"
                    safeBinding.tvRecapTotal.text =
                        "Pasar tercatat: ${Formatter.currency(summary.totalRekapHariIni)}"
                    safeBinding.tvSalesCount.text =
                        "Transaksi: ${summary.jumlahTransaksiHariIni}"
                    safeBinding.tvSoldQty.text =
                        "Item terjual: ${summary.totalItemHariIni}"

                    val rows = summary.recentRows.map {
                        ItemBaris(
                            id = it.id,
                            title = it.title,
                            subtitle = it.subtitle,
                            badge = it.badge,
                            amount = it.amount,
                            tone = when (it.badge) {
                                "Rumahan" -> WarnaBaris.GREEN
                                "Pasar" -> WarnaBaris.BLUE
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

                    setLoadingState(showLoading = false)
                    isLoading = false
                }
                .onFailure {
                    val safeBinding = _binding ?: return@onFailure
                    if (!hasLoadedOnce) {
                        safeBinding.tvSalesTitle.text = "Penjualan hari ini"
                        safeBinding.tvSalesTotal.text = Formatter.currency(0)
                        safeBinding.tvCashierTotal.text = "Rumahan tercatat: ${Formatter.currency(0)}"
                        safeBinding.tvRecapTotal.text = "Pasar tercatat: ${Formatter.currency(0)}"
                        safeBinding.tvSalesCount.text = "Transaksi: 0"
                        safeBinding.tvSoldQty.text = "Item terjual: 0"
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
                    }

                    setLoadingState(showLoading = false)
                    isLoading = false
                    showMessage(safeBinding.root, it.message ?: "Gagal memuat ringkasan penjualan")
                }
        }
    }

    private fun setLoadingState(showLoading: Boolean) {
        val currentBinding = _binding ?: return
        currentBinding.progressLoadSales.isVisible = showLoading
        currentBinding.contentSales.isVisible = !showLoading
    }

    private fun openDetailPenjualan(item: ItemBaris) {
        if (item.id == "empty-sales" || item.id == "error-sales") return

        val currentBinding = _binding ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.buildReceiptText(item.id) }
                .onSuccess { detail ->
                    showReceiptModal("Detail Penjualan", detail, "Bagikan")
                }
                .onFailure {
                    showMessage(currentBinding.root, it.message ?: "Gagal memuat detail penjualan")
                }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
