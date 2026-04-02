package muhamad.irfan.si_tahupm.ui.stock

import android.content.Intent
import android.os.Bundle
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.ActivityStockAdjustmentBinding
import muhamad.irfan.si_tahupm.ui.base.BaseActivity
import muhamad.irfan.si_tahupm.util.AppExtras
import muhamad.irfan.si_tahupm.util.Formatters
import muhamad.irfan.si_tahupm.util.SpinnerAdapters

class StockAdjustmentActivity : BaseActivity() {
    private lateinit var binding: ActivityStockAdjustmentBinding
    private val products by lazy { DemoRepository.allProducts() }
    private val types = listOf("add", "subtract")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockAdjustmentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Adjustment Stok", "Penyesuaian stok manual")

        binding.etDate.setText(Formatters.currentDateOnly())
        binding.spProduct.adapter = SpinnerAdapters.stringAdapter(this, products.map { it.name })
        binding.spType.adapter = SpinnerAdapters.stringAdapter(this, types)
        val initialProductId = intent.getStringExtra(AppExtras.EXTRA_PRODUCT_ID)
        val productIndex = products.indexOfFirst { it.id == initialProductId }.takeIf { it >= 0 } ?: 0
        binding.spProduct.setSelection(productIndex)

        binding.btnSave.setOnClickListener {
            val product = products.getOrNull(binding.spProduct.selectedItemPosition) ?: return@setOnClickListener
            val type = types.getOrNull(binding.spType.selectedItemPosition) ?: "add"
            runCatching {
                DemoRepository.adjustStock(
                    dateOnly = binding.etDate.text?.toString().orEmpty(),
                    productId = product.id,
                    type = type,
                    qty = binding.etQty.text?.toString()?.toIntOrNull() ?: 0,
                    note = binding.etNote.text?.toString().orEmpty(),
                    userId = currentUserId()
                )
            }.onSuccess {
                showMessage("Adjustment stok berhasil.")
                startActivity(Intent(this, StockDetailActivity::class.java).putExtra(AppExtras.EXTRA_PRODUCT_ID, product.id))
                finish()
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan adjustment")
            }
        }
    }
}
