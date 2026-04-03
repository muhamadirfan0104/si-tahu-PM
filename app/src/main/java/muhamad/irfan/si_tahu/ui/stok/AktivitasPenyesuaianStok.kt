package muhamad.irfan.si_tahu.ui.stock

import android.content.Intent
import android.os.Bundle
import muhamad.irfan.si_tahu.data.RepositoriLokal
import muhamad.irfan.si_tahu.databinding.ActivityStockAdjustmentBinding
import muhamad.irfan.si_tahu.ui.base.AktivitasDasar
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.AdapterSpinner

class AktivitasPenyesuaianStok : AktivitasDasar() {
    private lateinit var binding: ActivityStockAdjustmentBinding
    private val products by lazy { RepositoriLokal.allProducts() }
    private val types = listOf("add", "subtract")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockAdjustmentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Adjustment Stok", "Penyesuaian stok manual")

        binding.etDate.setText(Formatter.currentDateOnly())
        binding.spType.adapter = AdapterSpinner.stringAdapter(this, types)
        setupProduk()

        binding.btnSave.setOnClickListener {
            if (products.isEmpty()) {
                showMessage("Tambahkan produk terlebih dahulu.")
                return@setOnClickListener
            }

            val product = products.getOrNull(binding.spProduct.selectedItemPosition) ?: return@setOnClickListener
            val type = types.getOrNull(binding.spType.selectedItemPosition) ?: "add"
            runCatching {
                RepositoriLokal.adjustStock(
                    dateOnly = binding.etDate.text?.toString().orEmpty(),
                    productId = product.id,
                    type = type,
                    qty = binding.etQty.text?.toString()?.toIntOrNull() ?: 0,
                    note = binding.etNote.text?.toString().orEmpty(),
                    userId = currentUserId()
                )
            }.onSuccess {
                showMessage("Adjustment stok berhasil.")
                startActivity(
                    Intent(this, AktivitasDetailStok::class.java)
                        .putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, product.id)
                )
                finish()
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan adjustment")
            }
        }
    }

    private fun setupProduk() {
        if (products.isEmpty()) {
            binding.spProduct.adapter = AdapterSpinner.stringAdapter(this, listOf("Belum ada produk"))
            binding.btnSave.isEnabled = false
            binding.etNote.hint = "Tambahkan produk terlebih dahulu"
            return
        }

        binding.spProduct.adapter = AdapterSpinner.stringAdapter(this, products.map { it.name })
        val initialProductId = intent.getStringExtra(EkstraAplikasi.EXTRA_PRODUCT_ID)
        val productIndex = products.indexOfFirst { it.id == initialProductId }.takeIf { it >= 0 } ?: 0
        binding.spProduct.setSelection(productIndex)
        binding.btnSave.isEnabled = true
    }
}
