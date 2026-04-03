package muhamad.irfan.si_tahu.ui.stock

import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.RepositoriLokal
import muhamad.irfan.si_tahu.databinding.ActivityStockDetailBinding
import muhamad.irfan.si_tahu.ui.base.AktivitasDasar
import muhamad.irfan.si_tahu.ui.common.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class AktivitasDetailStok : AktivitasDasar() {
    private lateinit var binding: ActivityStockDetailBinding
    private lateinit var movementAdapter: AdapterBarisUmum
    private var productId: String = ""
    private var movementRows: List<ItemBaris> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Detail Stok", "Informasi lengkap stok")
        productId = intent.getStringExtra(EkstraAplikasi.EXTRA_PRODUCT_ID).orEmpty()
        movementAdapter = AdapterBarisUmum(onItemClick = { row -> showDetailModal(row.title, row.subtitle + "\n\n" + row.badge) })
        binding.rvMovement.layoutManager = LinearLayoutManager(this)
        binding.rvMovement.adapter = movementAdapter
        binding.btnAdjustStock.setOnClickListener {
            startActivity(Intent(this, AktivitasPenyesuaianStok::class.java).putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, productId))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val product = RepositoriLokal.getProduct(productId) ?: return
        binding.tvProductName.text = product.name
        binding.tvProductMeta.text = "${product.code} • ${product.category} • ${product.unit}"
        binding.tvStockNow.text = "Stok saat ini: ${product.stock} ${product.unit}"
        binding.tvMinStock.text = "Minimum: ${product.minStock} ${product.unit}"
        binding.tvStatus.text = RepositoriLokal.productStatus(product)
        val toneRes = when (RepositoriLokal.productStatus(product)) {
            "Aman" -> R.drawable.bg_tone_green
            "Menipis" -> R.drawable.bg_tone_gold
            else -> R.drawable.bg_tone_orange
        }
        binding.tvStatus.setBackgroundResource(toneRes)
        movementRows = RepositoriLokal.stockMovements(product.id).map {
            ItemBaris(
                id = it.id,
                title = it.title,
                subtitle = it.subtitle,
                badge = it.qtyText,
                amount = "",
                tone = when (it.tone) {
                    "green" -> WarnaBaris.GREEN
                    "gold" -> WarnaBaris.GOLD
                    "blue" -> WarnaBaris.BLUE
                    else -> WarnaBaris.ORANGE
                }
            )
        }
        movementAdapter.submitList(movementRows)
    }
}
