package muhamad.irfan.si_tahu.ui.utama

import android.content.Intent
import android.os.Bundle
import android.view.View
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentProductionMenuBinding.bind(view)

        binding.rvProductionRecent.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProductionRecent.adapter = recentAdapter

        binding.btnBasicProduction.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasProduksiTahuDasar::class.java))
        }

        binding.btnConversion.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasKonversiProduk::class.java))
        }

        binding.btnProductionHistory.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasRiwayatProduksi::class.java))
        }

        binding.btnParameterList.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasDaftarParameter::class.java))
        }

        renderSummary()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) renderSummary()
    }

    private fun renderSummary() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatRingkasanProduksi() }
                .onSuccess { summary ->
                    binding.tvProductionTotal.text = "${summary.totalProduksiDasarHariIni} pcs"
                    binding.tvProductionBatch.text = "Batch dasar tercatat: ${summary.totalBatchHariIni}"
                    binding.tvProductionDerived.text = "Konversi tercatat: ${summary.totalKonversiHariIni} transaksi"
                    binding.tvParameterActive.text = "Parameter aktif: ${summary.totalParameterAktif}"
                    binding.tvProductionHistoryCount.text = "Riwayat total: ${summary.totalRiwayat} catatan"

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
                }
                .onFailure {
                    recentAdapter.submitList(emptyList())
                    showMessage(binding.root, it.message ?: "Gagal memuat ringkasan produksi")
                }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}