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
import muhamad.irfan.si_tahu.databinding.FragmentProductionMenuBinding
import muhamad.irfan.si_tahu.ui.dasar.FragmenDasar
import muhamad.irfan.si_tahu.ui.parameter.AktivitasDaftarParameter
import muhamad.irfan.si_tahu.ui.produksi.AktivitasKonversiProduk
import muhamad.irfan.si_tahu.ui.produksi.AktivitasProduksiTahuDasar
import muhamad.irfan.si_tahu.ui.produksi.AktivitasRiwayatProduksi
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class FragmenProduksi : FragmenDasar(R.layout.fragment_production_menu) {

    private var _binding: FragmentProductionMenuBinding? = null
    private val binding get() = _binding!!

    private val recentAdapter by lazy {
        AdapterBarisUmum(onItemClick = {})
    }

    private var hasLoadedOnce = false
    private var isLoading = false
    private var lastLoadedAt = 0L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentProductionMenuBinding.bind(view)

        binding.rvProductionRecent.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProductionRecent.adapter = recentAdapter

        binding.btnBasicProduction.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(Intent(safeContext, AktivitasProduksiTahuDasar::class.java))
        }

        binding.btnConversion.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(Intent(safeContext, AktivitasKonversiProduk::class.java))
        }

        binding.btnProductionHistory.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(Intent(safeContext, AktivitasRiwayatProduksi::class.java))
        }

        binding.btnParameterList.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            launchActivitySafely(Intent(safeContext, AktivitasDaftarParameter::class.java))
        }

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

    private fun renderSummary(forceInitialLoading: Boolean) {
        val currentBinding = _binding ?: return
        if (isLoading) return

        isLoading = true
        setLoadingState(showLoading = forceInitialLoading && !hasLoadedOnce)

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatRingkasanProduksi() }
                .onSuccess { summary ->
                    val safeBinding = _binding ?: return@onSuccess
                    hasLoadedOnce = true
                    lastLoadedAt = SystemClock.elapsedRealtime()

                    safeBinding.tvProductionTotal.text = "${summary.totalProduksiDasarHariIni} pcs"
                    safeBinding.tvProductionBatch.text = "Batch dasar tercatat: ${summary.totalBatchHariIni}"
                    safeBinding.tvProductionDerived.text = "Konversi tercatat: ${summary.totalKonversiHariIni} transaksi"
                    safeBinding.tvParameterActive.text = "Parameter aktif: ${summary.totalParameterAktif}"
                    safeBinding.tvProductionHistoryCount.text = "Riwayat total: ${summary.totalRiwayat} catatan"

                    recentAdapter.submitList(
                        summary.recentRows.map {
                            ItemBaris(
                                id = it.id,
                                title = it.title,
                                subtitle = it.subtitle,
                                badge = it.badge,
                                amount = it.amount,
                                tone = if (it.badge == "Konversi") WarnaBaris.BLUE else WarnaBaris.GREEN
                            )
                        }
                    )

                    setLoadingState(showLoading = false)
                    isLoading = false
                }
                .onFailure {
                    val safeBinding = _binding ?: return@onFailure
                    if (!hasLoadedOnce) {
                        recentAdapter.submitList(emptyList())
                    }
                    setLoadingState(showLoading = false)
                    isLoading = false
                    showMessage(safeBinding.root, it.message ?: "Gagal memuat ringkasan produksi")
                }
        }
    }

    private fun setLoadingState(showLoading: Boolean) {
        val currentBinding = _binding ?: return
        currentBinding.progressLoadProduction.isVisible = showLoading
        currentBinding.contentProduction.isVisible = !showLoading
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
