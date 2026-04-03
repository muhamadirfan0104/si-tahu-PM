package muhamad.irfan.si_tahu.ui.main

import android.os.Bundle
import android.view.View
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.databinding.FragmentCashierHistoryBinding
import muhamad.irfan.si_tahu.ui.base.FragmenDasar
import muhamad.irfan.si_tahu.ui.common.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class FragmenRiwayatKasir : FragmenDasar(R.layout.fragment_cashier_history) {
    private var _binding: FragmentCashierHistoryBinding? = null
    private val binding get() = _binding!!

    private val adapter = AdapterBarisUmum(onItemClick = {})

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentCashierHistoryBinding.bind(view)
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter
        binding.etSearch.addTextChangedListener { refresh() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val query = binding.etSearch.text?.toString().orEmpty().trim().lowercase()

        val items = if (query.isBlank()) {
            listOf(
                ItemBaris(
                    id = "info_history",
                    title = "Riwayat belum terhubung",
                    subtitle = "Data riwayat kasir akan tampil setelah koleksi penjualan dan rincian dibaca dari Firebase.",
                    badge = "Info",
                    amount = "",
                    tone = WarnaBaris.GOLD
                )
            )
        } else {
            emptyList()
        }

        adapter.submitList(items)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}