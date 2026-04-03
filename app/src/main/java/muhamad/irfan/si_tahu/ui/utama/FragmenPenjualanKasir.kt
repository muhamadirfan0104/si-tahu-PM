package muhamad.irfan.si_tahu.ui.main

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.databinding.FragmentCashierSaleBinding
import muhamad.irfan.si_tahu.ui.base.FragmenDasar
import muhamad.irfan.si_tahu.ui.common.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris
import muhamad.irfan.si_tahu.util.AdapterSpinner

class FragmenPenjualanKasir : FragmenDasar(R.layout.fragment_cashier_sale) {
    private var _binding: FragmentCashierSaleBinding? = null
    private val binding get() = _binding!!

    private val productAdapter = AdapterBarisUmum(onItemClick = {
        showMessage(binding.root, "Produk kasir belum terhubung ke Firebase.")
    })

    private val cartAdapter = AdapterBarisUmum(onItemClick = {})

    private val categories = listOf("Semua", "DASAR", "OLAHAN")
    private val paymentMethods = listOf("TUNAI", "QRIS", "TRANSFER")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireLoginOrRedirect()) return

        _binding = FragmentCashierSaleBinding.bind(view)

        binding.rvProducts.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvProducts.adapter = productAdapter
        binding.rvCart.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCart.adapter = cartAdapter

        binding.spCategory.adapter = AdapterSpinner.stringAdapter(requireContext(), categories)
        binding.spPayment.adapter = AdapterSpinner.stringAdapter(requireContext(), paymentMethods)
        binding.spPayment.setSelection(0)

        binding.etSearch.addTextChangedListener { renderProductsPlaceholder() }
        binding.spCategory.setOnItemSelectedListener(PendengarPilihItemSederhana { renderProductsPlaceholder() })
        binding.spPayment.setOnItemSelectedListener(PendengarPilihItemSederhana { updatePaymentVisibility() })

        binding.btnClearCart.setOnClickListener {
            showMessage(view, "Keranjang belum aktif. Modul penjualan belum terhubung ke Firebase.")
        }

        binding.btnCheckout.setOnClickListener {
            showMessage(view, "Checkout belum aktif. Modul penjualan belum terhubung ke Firebase.")
        }
    }

    override fun onResume() {
        super.onResume()
        renderProductsPlaceholder()
        renderCartPlaceholder()
    }

    private fun renderProductsPlaceholder() {
        val query = binding.etSearch.text?.toString().orEmpty().trim().lowercase()
        val category = binding.spCategory.selectedItem?.toString().orEmpty()

        val info = ItemBaris(
            id = "info_produk",
            title = "Produk belum terhubung",
            subtitle = "Master produk kasir akan muncul setelah data produk dan harga kanal diambil dari Firebase.",
            badge = if (category == "Semua") "Info" else category,
            amount = "",
            tone = WarnaBaris.GOLD
        )

        val items = if (query.isBlank()) listOf(info) else emptyList()
        productAdapter.submitList(items)
    }

    private fun renderCartPlaceholder() {
        cartAdapter.submitList(
            listOf(
                ItemBaris(
                    id = "info_cart",
                    title = "Keranjang kosong",
                    subtitle = "Keranjang belum memiliki item.",
                    badge = "Info",
                    amount = "",
                    tone = WarnaBaris.BLUE
                )
            )
        )
        binding.tvCartSummary.text = "0 item • Total -"
        binding.etCashPaid.setText("")
        updatePaymentVisibility()
    }

    private fun updatePaymentVisibility() {
        val payment = binding.spPayment.selectedItem?.toString().orEmpty()
        binding.tilCashPaid.isVisible = payment == "TUNAI"
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}