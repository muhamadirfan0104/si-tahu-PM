package muhamad.irfan.si_tahu.ui.stok

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.ActivityStockDetailBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.umum.AdapterBarisUmum
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class AktivitasDetailStok : AktivitasDasar() {

    private lateinit var binding: ActivityStockDetailBinding
    private val movementAdapter = AdapterBarisUmum(onItemClick = ::openMovementDetail)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        binding = ActivityStockDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Detail Stok", "Pergerakan produk")

        binding.rvMovement.layoutManager = LinearLayoutManager(this)
        binding.rvMovement.adapter = movementAdapter
        binding.btnAdjustStock.setOnClickListener {
            startActivity(Intent(this, AktivitasStockAdjustment::class.java).putExtra(EXTRA_PRODUCT_ID, intent.getStringExtra(EXTRA_PRODUCT_ID)))
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun productStatus(stock: Int, minStock: Int): String = when {
        stock <= 0 -> "Habis"
        stock <= minStock -> "Menipis"
        else -> "Aman"
    }

    private fun render() {
        val productId = intent.getStringExtra(EXTRA_PRODUCT_ID).orEmpty()
        if (productId.isBlank()) {
            showMessage("Produk tidak ditemukan")
            finish()
            return
        }

        lifecycleScope.launch {
            runCatching {
                val product = RepositoriFirebaseUtama.muatProdukById(productId)
                    ?: throw IllegalStateException("Produk tidak ditemukan")
                val movements = RepositoriFirebaseUtama.muatPergerakanStok(productId)
                product to movements
            }.onSuccess { (product, movements) ->
                binding.tvProductName.text = product.name
                binding.tvProductMeta.text = "${product.code} • ${product.category} • ${product.unit}"
                binding.tvStockNow.text = "Stok saat ini: ${product.stock} ${product.unit}"
                binding.tvMinStock.text = "Stok minimum: ${product.minStock} ${product.unit}"
                val status = productStatus(product.stock, product.minStock)
                binding.tvStatus.text = status
                binding.tvStatus.setBackgroundResource(
                    when (status) {
                        "Aman" -> R.drawable.bg_tone_green
                        "Menipis" -> R.drawable.bg_tone_gold
                        else -> R.drawable.bg_tone_orange
                    }
                )

                movementAdapter.submitList(movements.map {
                    ItemBaris(
                        id = it.id,
                        title = it.title,
                        subtitle = it.subtitle,
                        amount = it.qtyText,
                        badge = it.tanggalIso.substringBefore('T'),
                        tone = when (it.tone) {
                            "green" -> WarnaBaris.GREEN
                            "orange" -> WarnaBaris.ORANGE
                            "blue" -> WarnaBaris.BLUE
                            else -> WarnaBaris.GOLD
                        }
                    )
                })
            }.onFailure {
                showMessage(it.message ?: "Gagal memuat detail stok")
                finish()
            }
        }
    }

    private fun openMovementDetail(item: ItemBaris) {
        lifecycleScope.launch {
            val productId = intent.getStringExtra(EXTRA_PRODUCT_ID).orEmpty()
            val cleanId = item.id.removeSuffix("-out").removeSuffix("-in").removeSuffix(productId)
            val detail = when {
                item.title.contains("Produksi", true) || item.title.contains("Konversi", true) -> RepositoriFirebaseUtama.buildProductionDetailText(cleanId)
                item.title.contains("Adjustment", true) -> RepositoriFirebaseUtama.buildAdjustmentDetailText(cleanId)
                item.title.contains("Penjualan", true) -> RepositoriFirebaseUtama.buildReceiptText(cleanId)
                else -> "Detail pergerakan belum tersedia"
            }
            showDetailModal("Pergerakan Stok", detail)
        }
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "extra_product_id"
    }
}
