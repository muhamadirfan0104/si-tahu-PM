package muhamad.irfan.si_tahupm.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahupm.R
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.FragmentStockListBinding
import muhamad.irfan.si_tahupm.ui.base.BaseFragment
import muhamad.irfan.si_tahupm.ui.common.GenericRowAdapter
import muhamad.irfan.si_tahupm.ui.stock.StockAdjustmentActivity
import muhamad.irfan.si_tahupm.ui.stock.StockDetailActivity
import muhamad.irfan.si_tahupm.util.AppExtras
import muhamad.irfan.si_tahupm.util.RowItem
import muhamad.irfan.si_tahupm.util.RowTone

class StockListFragment : BaseFragment(R.layout.fragment_stock_list) {
    private var _binding: FragmentStockListBinding? = null
    private val binding get() = _binding!!
    private val adapter = GenericRowAdapter(onItemClick = { row -> openDetail(row.id) })
    private val filters = listOf("Semua", "Aman", "Menipis", "Habis")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentStockListBinding.bind(view)
        binding.rvStock.layoutManager = LinearLayoutManager(requireContext())
        binding.rvStock.adapter = adapter
        binding.spStatus.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, filters)
        binding.spStatus.setSelection(0)
        binding.etSearch.addTextChangedListener { refresh() }
        binding.spStatus.setOnItemSelectedListener(SimpleItemSelectedListener { refresh() })
        binding.btnAdjustment.setOnClickListener { startActivity(Intent(requireContext(), StockAdjustmentActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val query = binding.etSearch.text?.toString().orEmpty().trim().lowercase()
        val statusFilter = binding.spStatus.selectedItem?.toString().orEmpty()
        val items = DemoRepository.allProducts().filter {
            val status = DemoRepository.productStatus(it)
            it.name.lowercase().contains(query) && (statusFilter == "Semua" || status == statusFilter)
        }.map {
            val tone = when (DemoRepository.productStatus(it)) {
                "Aman" -> RowTone.GREEN
                "Menipis" -> RowTone.GOLD
                else -> RowTone.ORANGE
            }
            RowItem(it.id, it.name, "Stok ${it.stock} ${it.unit} • minimum ${it.minStock}", DemoRepository.productStatus(it), "", tone = tone)
        }
        adapter.submitList(items)
    }

    private fun openDetail(productId: String) {
        startActivity(Intent(requireContext(), StockDetailActivity::class.java).putExtra(AppExtras.EXTRA_PRODUCT_ID, productId))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
