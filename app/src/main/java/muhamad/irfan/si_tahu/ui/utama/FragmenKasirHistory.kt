package muhamad.irfan.si_tahu.ui.utama

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.FragmentCashierHistoryBinding
import muhamad.irfan.si_tahu.ui.dasar.FragmenDasar
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class FragmenKasirHistory : FragmenDasar(R.layout.fragment_cashier_history) {

    private var _binding: FragmentCashierHistoryBinding? = null
    private val binding get() = _binding!!

    private val historyAdapter = AdapterBarisUmum(
        onItemClick = ::openDetail,
        onActionClick = ::showActionMenu
    )

    private var semuaRows: List<ItemBaris> = emptyList()
    private var filteredRows: List<ItemBaris> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentCashierHistoryBinding.bind(view)

        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = historyAdapter

        binding.etSearch.addTextChangedListener {
            refresh()
        }

        buildRows()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) buildRows()
    }

    private fun buildRows() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatRiwayatPenjualan() }
                .onSuccess { sales ->
                    semuaRows = sales
                        .filter { it.badge.equals("Rumahan", ignoreCase = true) }
                        .sortedByDescending { it.tanggalIso }
                        .map {
                            ItemBaris(
                                id = it.id,
                                title = it.title,
                                subtitle = it.subtitle,
                                amount = it.amount,
                                badge = it.badge,
                                tone = WarnaBaris.GREEN,
                                actionLabel = "⋮"
                            )
                        }

                    refresh()
                }
                .onFailure {
                    semuaRows = emptyList()
                    filteredRows = emptyList()
                    historyAdapter.submitList(emptyList())
                    showMessage(binding.root, it.message ?: "Gagal memuat riwayat penjualan")
                }
        }
    }

    private fun refresh() {
        val keyword = binding.etSearch.text?.toString().orEmpty().trim().lowercase()

        filteredRows = semuaRows.filter {
            keyword.isBlank() ||
                    it.title.lowercase().contains(keyword) ||
                    it.subtitle.lowercase().contains(keyword) ||
                    it.badge.lowercase().contains(keyword)
        }

        historyAdapter.submitList(filteredRows)
    }

    private fun openDetail(item: ItemBaris) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.buildReceiptText(item.id) }
                .onSuccess { detail ->
                    showReceiptModal("Detail Penjualan", detail)
                }
                .onFailure {
                    showMessage(binding.root, it.message ?: "Gagal memuat detail penjualan")
                }
        }
    }

    private fun showActionMenu(item: ItemBaris, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add("Lihat detail")
            menu.add("Bagikan")
            menu.add("Hapus")

            setOnMenuItemClickListener {
                when (it.title.toString()) {
                    "Lihat detail" -> openDetail(item)
                    "Bagikan" -> shareItem(item)
                    "Hapus" -> confirmDelete(item)
                }
                true
            }
        }.show()
    }

    private fun shareItem(item: ItemBaris) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.buildReceiptText(item.id) }
                .onSuccess { detail ->
                    sharePlainText("Penjualan ${item.title}", detail)
                }
                .onFailure {
                    showMessage(binding.root, it.message ?: "Gagal membagikan detail penjualan")
                }
        }
    }

    private fun confirmDelete(item: ItemBaris) {
        showConfirmationModal(
            "Hapus transaksi",
            "Transaksi ${item.title} akan dihapus dan stok produk dikembalikan."
        ) {
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching { RepositoriFirebaseUtama.hapusPenjualan(item.id) }
                    .onSuccess {
                        buildRows()
                        showMessage(binding.root, "Transaksi berhasil dihapus")
                    }
                    .onFailure {
                        showMessage(binding.root, it.message ?: "Gagal menghapus transaksi")
                    }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}