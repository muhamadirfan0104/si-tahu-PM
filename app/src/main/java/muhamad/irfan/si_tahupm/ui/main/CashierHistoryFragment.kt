package muhamad.irfan.si_tahupm.ui.main

import android.os.Bundle
import android.view.View
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahupm.R
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.FragmentCashierHistoryBinding
import muhamad.irfan.si_tahupm.ui.base.BaseFragment
import muhamad.irfan.si_tahupm.ui.common.GenericRowAdapter
import muhamad.irfan.si_tahupm.util.Formatters
import muhamad.irfan.si_tahupm.util.RowItem
import muhamad.irfan.si_tahupm.util.RowTone

class CashierHistoryFragment : BaseFragment(R.layout.fragment_cashier_history) {
    private var _binding: FragmentCashierHistoryBinding? = null
    private val binding get() = _binding!!
    private val adapter = GenericRowAdapter(
        onItemClick = { row -> showReceiptModal("Struk ${row.id}", DemoRepository.buildReceiptText(row.id)) },
        onActionClick = { row -> confirmDelete(row) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
        val query = binding.etSearch.text?.toString().orEmpty().lowercase()
        val items = DemoRepository.allSales().filter { it.source == "KASIR" }.filter { sale ->
            buildString {
                append(sale.id)
                append(' ')
                append(sale.items.joinToString(" ") { DemoRepository.getProduct(it.productId)?.name ?: "" })
            }.lowercase().contains(query)
        }.map { sale ->
            RowItem(
                id = sale.id,
                title = sale.id,
                subtitle = sale.items.joinToString(", ") { (DemoRepository.getProduct(it.productId)?.name ?: "Produk") + " x" + it.qty },
                badge = sale.paymentMethod,
                amount = Formatters.currency(sale.total),
                actionLabel = "Hapus",
                tone = RowTone.GOLD
            )
        }
        adapter.submitList(items)
    }

    private fun confirmDelete(row: RowItem) {
        showConfirmationModal(
            title = "Hapus transaksi kasir?",
            message = "Transaksi ${row.id} akan dihapus dan stok produk akan dikembalikan. Lanjutkan?"
        ) {
            runCatching { DemoRepository.deleteSale(row.id) }
                .onSuccess {
                    showMessage(requireView(), "Transaksi berhasil dihapus.")
                    refresh()
                }
                .onFailure { showMessage(requireView(), it.message ?: "Gagal menghapus transaksi") }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
