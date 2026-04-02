package muhamad.irfan.si_tahupm.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahupm.R
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.FragmentProductionMenuBinding
import muhamad.irfan.si_tahupm.ui.base.BaseFragment
import muhamad.irfan.si_tahupm.ui.common.GenericRowAdapter
import muhamad.irfan.si_tahupm.ui.parameter.ParameterListActivity
import muhamad.irfan.si_tahupm.ui.production.BasicProductionActivity
import muhamad.irfan.si_tahupm.ui.production.ConversionActivity
import muhamad.irfan.si_tahupm.ui.production.ProductionHistoryActivity
import muhamad.irfan.si_tahupm.util.Formatters
import muhamad.irfan.si_tahupm.util.RowItem
import muhamad.irfan.si_tahupm.util.RowTone

class ProductionMenuFragment : BaseFragment(R.layout.fragment_production_menu) {
    private var _binding: FragmentProductionMenuBinding? = null
    private val binding get() = _binding!!
    private val adapter = GenericRowAdapter(onItemClick = ::openLatestDetail)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentProductionMenuBinding.bind(view)
        binding.rvProductionRecent.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProductionRecent.adapter = adapter
        binding.btnBasicProduction.setOnClickListener { startActivity(Intent(requireContext(), BasicProductionActivity::class.java)) }
        binding.btnConversion.setOnClickListener { startActivity(Intent(requireContext(), ConversionActivity::class.java)) }
        binding.btnProductionHistory.setOnClickListener { startActivity(Intent(requireContext(), ProductionHistoryActivity::class.java)) }
        binding.btnParameterList.setOnClickListener { startActivity(Intent(requireContext(), ParameterListActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        val latest = DemoRepository.latestDateOnly()
        val producedToday = DemoRepository.allProductionLogs().filter { Formatters.toDateOnly(it.date) == latest }
        val conversionsToday = DemoRepository.allConversions().filter { Formatters.toDateOnly(it.date) == latest }
        binding.tvProductionTotal.text = producedToday.sumOf { it.result }.toString() + " pcs"
        binding.tvProductionBatch.text = "Batch dasar: ${producedToday.size}"
        binding.tvProductionDerived.text = "Produk turunan: ${conversionsToday.sumOf { it.outputQty }} pcs"
        binding.tvParameterActive.text = "Parameter aktif: ${DemoRepository.allParameters().count { it.active }}"
        binding.tvProductionHistoryCount.text = "Riwayat total: ${DemoRepository.allProductionLogs().size + DemoRepository.allConversions().size}"

        val items = mutableListOf<Pair<java.util.Date, RowItem>>()
        producedToday.forEach {
            items += Formatters.parseDate(it.date) to RowItem(
                id = it.id,
                title = it.id,
                subtitle = "${DemoRepository.getProduct(it.productId)?.name ?: "Produk"} • ${it.result} pcs",
                badge = "Produksi",
                amount = "${it.batches} masak",
                tone = RowTone.GREEN
            )
        }
        conversionsToday.forEach {
            items += Formatters.parseDate(it.date) to RowItem(
                id = it.id,
                title = it.id,
                subtitle = "${DemoRepository.getProduct(it.fromProductId)?.name ?: "Bahan"} -> ${DemoRepository.getProduct(it.toProductId)?.name ?: "Hasil"}",
                badge = "Konversi",
                amount = "${it.outputQty} pcs",
                tone = RowTone.BLUE
            )
        }
        adapter.submitList(items.sortedByDescending { it.first }.map { it.second }.take(4))
    }

    private fun openLatestDetail(item: RowItem) {
        when (item.badge) {
            "Konversi" -> showDetailModal(item.title, DemoRepository.buildConversionDetailText(item.id))
            else -> showDetailModal(item.title, DemoRepository.buildProductionDetailText(item.id))
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
