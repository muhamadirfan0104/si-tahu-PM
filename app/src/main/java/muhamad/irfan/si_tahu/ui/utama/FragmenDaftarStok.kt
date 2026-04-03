package muhamad.irfan.si_tahu.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.databinding.FragmentStockListBinding
import muhamad.irfan.si_tahu.ui.base.FragmenDasar
import muhamad.irfan.si_tahu.ui.common.AdapterBarisUmum
import muhamad.irfan.si_tahu.ui.stock.AktivitasPenyesuaianStok
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris
import muhamad.irfan.si_tahu.util.AdapterSpinner

class FragmenDaftarStok : FragmenDasar(R.layout.fragment_stock_list) {
    private var _binding: FragmentStockListBinding? = null
    private val binding get() = _binding!!

    private val adapter = AdapterBarisUmum(onItemClick = {
        showMessage(binding.root, "Detail stok belum terhubung ke Firebase.")
    })

    private val filters = listOf("Semua", "Aman", "Menipis", "Habis")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentStockListBinding.bind(view)
        binding.rvStock.layoutManager = LinearLayoutManager(requireContext())
        binding.rvStock.adapter = adapter

        binding.spStatus.adapter = AdapterSpinner.stringAdapter(requireContext(), filters)
        binding.spStatus.setSelection(0)
        binding.etSearch.addTextChangedListener { refresh() }
        binding.spStatus.setOnItemSelectedListener(PendengarPilihItemSederhana { refresh() })

        binding.btnAdjustment.setOnClickListener {
            startActivity(Intent(requireContext(), AktivitasPenyesuaianStok::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val query = binding.etSearch.text?.toString().orEmpty().trim().lowercase()
        val statusFilter = binding.spStatus.selectedItem?.toString().orEmpty()

        val infoRow = ItemBaris(
            id = "info_stock",
            title = "Stok belum terhubung",
            subtitle = "Daftar stok akan tampil setelah master produk dibaca dari Firebase.",
            badge = if (statusFilter == "Semua") "Info" else statusFilter,
            amount = "",
            tone = WarnaBaris.GOLD
        )

        val items = if (query.isBlank()) listOf(infoRow) else emptyList()
        adapter.submitList(items)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}