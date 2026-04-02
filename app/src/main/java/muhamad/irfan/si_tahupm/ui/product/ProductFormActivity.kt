package muhamad.irfan.si_tahupm.ui.product

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.ActivityProductFormBinding
import muhamad.irfan.si_tahupm.ui.base.BaseActivity
import muhamad.irfan.si_tahupm.util.AppExtras

class ProductFormActivity : BaseActivity() {
    private lateinit var binding: ActivityProductFormBinding
    private val categories = listOf("DASAR", "OLAHAN")
    private val tones = listOf("green", "gold", "orange", "blue", "soft")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Form Produk", "Tambah atau edit produk")

        binding.spCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        binding.spPhotoTone.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tones)

        val editing = DemoRepository.getProduct(intent.getStringExtra(AppExtras.EXTRA_PRODUCT_ID))
        if (editing != null) {
            binding.etCode.setText(editing.code)
            binding.etName.setText(editing.name)
            binding.spCategory.setSelection(categories.indexOf(editing.category).coerceAtLeast(0))
            binding.etUnit.setText(editing.unit)
            binding.etStock.setText(editing.stock.toString())
            binding.etMinStock.setText(editing.minStock.toString())
            binding.etDefaultPrice.setText((DemoRepository.defaultChannel(editing)?.price ?: 0L).toString())
            binding.spPhotoTone.setSelection(tones.indexOf(editing.photoTone).coerceAtLeast(0))
            binding.cbActive.isChecked = editing.active
            binding.cbShowCashier.isChecked = editing.showInCashier
        } else {
            binding.cbActive.isChecked = true
            binding.cbShowCashier.isChecked = true
        }

        binding.btnSave.setOnClickListener {
            runCatching {
                DemoRepository.saveProduct(
                    existingId = editing?.id,
                    code = binding.etCode.text?.toString().orEmpty(),
                    name = binding.etName.text?.toString().orEmpty(),
                    category = categories[binding.spCategory.selectedItemPosition],
                    unit = binding.etUnit.text?.toString().orEmpty(),
                    stock = binding.etStock.text?.toString()?.toIntOrNull() ?: 0,
                    minStock = binding.etMinStock.text?.toString()?.toIntOrNull() ?: 0,
                    defaultPrice = binding.etDefaultPrice.text?.toString()?.toLongOrNull() ?: 0L,
                    photoTone = tones[binding.spPhotoTone.selectedItemPosition],
                    active = binding.cbActive.isChecked,
                    showInCashier = binding.cbShowCashier.isChecked
                )
            }.onSuccess {
                showMessage("Produk berhasil disimpan.")
                startActivity(Intent(this, ProductListActivity::class.java))
                finish()
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan produk")
            }
        }
    }
}
