package muhamad.irfan.si_tahu.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.databinding.FragmentProductionMenuBinding
import muhamad.irfan.si_tahu.ui.base.FragmenDasar
import muhamad.irfan.si_tahu.ui.common.AdapterBarisUmum
import muhamad.irfan.si_tahu.ui.parameter.AktivitasDaftarParameter
import muhamad.irfan.si_tahu.ui.production.AktivitasProduksiDasar
import muhamad.irfan.si_tahu.ui.production.AktivitasKonversi
import muhamad.irfan.si_tahu.ui.production.AktivitasRiwayatProduksi
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class FragmenMenuProduksi : FragmenDasar(R.layout.fragment_production_menu) {
    private var _binding: FragmentProductionMenuBinding? = null
    private val binding get() = _binding!!

    private val adapter = AdapterBarisUmum(onItemClick = {
        showMessage(binding.root, "Riwayat produksi belum terhubung ke Firebase.")
    })

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentProductionMenuBinding.bind(view)
        binding.rvProductionRecent.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProductionRecent.adapter = adapter

        binding.btnBasicProduction.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasProduksiDasar::class.java))
        }
        binding.btnConversion.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasKonversi::class.java))
        }
        binding.btnProductionHistory.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasRiwayatProduksi::class.java))
        }
        binding.btnParameterList.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasDaftarParameter::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        renderEmptyState()
    }

    private fun renderEmptyState() {
        binding.tvProductionTotal.text = "- pcs"
        binding.tvProductionBatch.text = "Batch dasar: -"
        binding.tvProductionDerived.text = "Produk turunan: -"
        binding.tvParameterActive.text = "Parameter aktif: -"
        binding.tvProductionHistoryCount.text = "Riwayat total: -"

        adapter.submitList(
            listOf(
                ItemBaris(
                    id = "info_production",
                    title = "Produksi belum terhubung",
                    subtitle = "Ringkasan produksi akan tampil setelah parameter produksi dan catatan produksi dibaca dari Firebase.",
                    badge = "Info",
                    amount = "",
                    tone = WarnaBaris.GREEN
                )
            )
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}