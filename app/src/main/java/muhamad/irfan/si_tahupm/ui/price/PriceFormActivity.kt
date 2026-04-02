package muhamad.irfan.si_tahupm.ui.price

import android.content.Intent
import android.os.Bundle
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.ActivityPriceFormBinding
import muhamad.irfan.si_tahupm.ui.base.BaseActivity
import muhamad.irfan.si_tahupm.util.AppExtras
import muhamad.irfan.si_tahupm.util.SpinnerAdapters

class PriceFormActivity : BaseActivity() {
    private lateinit var binding: ActivityPriceFormBinding
    private val products by lazy { DemoRepository.allProducts() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPriceFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Form Harga Kanal", "Tambah atau edit harga kanal")

        binding.spProduct.adapter = SpinnerAdapters.stringAdapter(this, products.map { it.name })
        val initialProductId = intent.getStringExtra(AppExtras.EXTRA_PRODUCT_ID)
        val initialProductIndex = products.indexOfFirst { it.id == initialProductId }.takeIf { it >= 0 } ?: 0
        binding.spProduct.setSelection(initialProductIndex)
        val editingProduct = products.getOrNull(initialProductIndex)
        val editing = editingProduct?.channels?.firstOrNull { it.id == intent.getStringExtra(AppExtras.EXTRA_PRICE_ID) }
        if (editing != null) {
            binding.etLabel.setText(editing.label)
            binding.etPrice.setText(editing.price.toString())
            binding.cbActive.isChecked = editing.active
            binding.cbDefault.isChecked = editing.defaultCashier
        } else {
            binding.cbActive.isChecked = true
        }

        binding.btnSave.setOnClickListener {
            val selectedProduct = products.getOrNull(binding.spProduct.selectedItemPosition) ?: return@setOnClickListener
            runCatching {
                DemoRepository.saveChannelPrice(
                    productId = selectedProduct.id,
                    existingId = editing?.id,
                    label = binding.etLabel.text?.toString().orEmpty(),
                    price = binding.etPrice.text?.toString()?.toLongOrNull() ?: 0L,
                    active = binding.cbActive.isChecked,
                    defaultCashier = binding.cbDefault.isChecked
                )
            }.onSuccess {
                showMessage("Harga kanal berhasil disimpan.")
                startActivity(Intent(this, PriceListActivity::class.java).putExtra(AppExtras.EXTRA_PRODUCT_ID, selectedProduct.id))
                finish()
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan harga kanal")
            }
        }
    }
}
