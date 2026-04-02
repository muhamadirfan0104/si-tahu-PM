package muhamad.irfan.si_tahupm.ui.main

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahupm.R
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.FragmentCashierSaleBinding
import muhamad.irfan.si_tahupm.ui.base.BaseFragment
import muhamad.irfan.si_tahupm.ui.common.CartAdapter
import muhamad.irfan.si_tahupm.ui.common.ProductAdapter
import muhamad.irfan.si_tahupm.util.Formatters

class CashierSaleFragment : BaseFragment(R.layout.fragment_cashier_sale) {
    private var _binding: FragmentCashierSaleBinding? = null
    private val binding get() = _binding!!

    private val productAdapter = ProductAdapter { product ->
        runCatching { DemoRepository.addToCart(product.id) }
            .onSuccess { refreshCart() }
            .onFailure { showMessage(requireView(), it.message ?: "Gagal menambah produk") }
    }

    private val cartAdapter = CartAdapter(
        onIncrease = { item ->
            runCatching { DemoRepository.changeCart(item.productId, 1) }
                .onSuccess { refreshCart() }
                .onFailure { showMessage(requireView(), it.message ?: "Gagal menambah jumlah") }
        },
        onDecrease = { item ->
            DemoRepository.changeCart(item.productId, -1)
            refreshCart()
        },
        onRemove = { item ->
            DemoRepository.removeFromCart(item.productId)
            refreshCart()
        }
    )

    private val categories = listOf("Semua", "DASAR", "OLAHAN")
    private val paymentMethods = listOf("Tunai", "QRIS", "Transfer")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCashierSaleBinding.bind(view)

        binding.rvProducts.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvProducts.adapter = productAdapter
        binding.rvCart.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCart.adapter = cartAdapter

        binding.spCategory.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)
        binding.spPayment.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, paymentMethods)
        binding.spPayment.setSelection(paymentMethods.indexOf(DemoRepository.checkoutMethod).coerceAtLeast(0))
        binding.etCashPaid.setText(DemoRepository.cashPaid.toString())
        binding.etSearch.addTextChangedListener { refreshProducts() }
        binding.spCategory.setOnItemSelectedListener(SimpleItemSelectedListener { refreshProducts() })
        binding.spPayment.setOnItemSelectedListener(SimpleItemSelectedListener { updatePaymentVisibility() })
        binding.btnClearCart.setOnClickListener {
            DemoRepository.cart.clear()
            refreshCart()
        }
        binding.btnCheckout.setOnClickListener {
            val payment = binding.spPayment.selectedItem?.toString().orEmpty()
            val cashPaid = binding.etCashPaid.text?.toString()?.toLongOrNull() ?: 0L
            runCatching { DemoRepository.checkoutCart(currentUserId(), payment, cashPaid) }
                .onSuccess { sale ->
                    refreshProducts()
                    refreshCart()
                    showDetailModal("Struk ${sale.id}", DemoRepository.buildReceiptText(sale.id))
                }
                .onFailure { showMessage(view, it.message ?: "Gagal menyimpan transaksi") }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshProducts()
        refreshCart()
    }

    private fun refreshProducts() {
        val query = binding.etSearch.text?.toString().orEmpty().trim().lowercase()
        val category = binding.spCategory.selectedItem?.toString().orEmpty()
        val items = DemoRepository.allProducts().filter {
            it.active && it.showInCashier &&
                it.name.lowercase().contains(query) &&
                (category == "Semua" || it.category == category)
        }
        productAdapter.submitList(items)
    }

    private fun refreshCart() {
        cartAdapter.submitList(DemoRepository.cart.toList())
        binding.tvCartSummary.text = DemoRepository.cartCount().toString() + " item • Total " + Formatters.currency(DemoRepository.cartTotal())
        binding.etCashPaid.setText(DemoRepository.cashPaid.toString())
        updatePaymentVisibility()
    }

    private fun updatePaymentVisibility() {
        val payment = binding.spPayment.selectedItem?.toString().orEmpty()
        binding.tilCashPaid.isVisible = payment == "Tunai"
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
