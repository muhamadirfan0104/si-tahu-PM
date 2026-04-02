package muhamad.irfan.si_tahupm.ui.stock

import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahupm.R
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.ActivityStockDetailBinding
import muhamad.irfan.si_tahupm.ui.base.BaseActivity
import muhamad.irfan.si_tahupm.ui.common.GenericRowAdapter
import muhamad.irfan.si_tahupm.util.AppExtras
import muhamad.irfan.si_tahupm.util.RowItem
import muhamad.irfan.si_tahupm.util.RowTone

class StockDetailActivity : BaseActivity() {
    private lateinit var binding: ActivityStockDetailBinding
    private lateinit var movementAdapter: GenericRowAdapter
    private var productId: String = ""
    private var movementRows: List<RowItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Detail Stok", "Informasi lengkap stok")
        productId = intent.getStringExtra(AppExtras.EXTRA_PRODUCT_ID).orEmpty()
        movementAdapter = GenericRowAdapter(onItemClick = { row -> showDetailModal(row.title, row.subtitle + "\n\n" + row.badge) })
        binding.rvMovement.layoutManager = LinearLayoutManager(this)
        binding.rvMovement.adapter = movementAdapter
        binding.btnAdjustStock.setOnClickListener {
            startActivity(Intent(this, StockAdjustmentActivity::class.java).putExtra(AppExtras.EXTRA_PRODUCT_ID, productId))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val product = DemoRepository.getProduct(productId) ?: return
        binding.tvProductName.text = product.name
        binding.tvProductMeta.text = "${product.code} • ${product.category} • ${product.unit}"
        binding.tvStockNow.text = "Stok saat ini: ${product.stock} ${product.unit}"
        binding.tvMinStock.text = "Minimum: ${product.minStock} ${product.unit}"
        binding.tvStatus.text = DemoRepository.productStatus(product)
        val toneRes = when (DemoRepository.productStatus(product)) {
            "Aman" -> R.drawable.bg_tone_green
            "Menipis" -> R.drawable.bg_tone_gold
            else -> R.drawable.bg_tone_orange
        }
        binding.tvStatus.setBackgroundResource(toneRes)
        movementRows = DemoRepository.stockMovements(product.id).map {
            RowItem(
                id = it.id,
                title = it.title,
                subtitle = it.subtitle,
                badge = it.qtyText,
                amount = "",
                tone = when (it.tone) {
                    "green" -> RowTone.GREEN
                    "gold" -> RowTone.GOLD
                    "blue" -> RowTone.BLUE
                    else -> RowTone.ORANGE
                }
            )
        }
        movementAdapter.submitList(movementRows)
    }
}
