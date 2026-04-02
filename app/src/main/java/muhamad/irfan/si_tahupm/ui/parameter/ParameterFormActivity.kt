package muhamad.irfan.si_tahupm.ui.parameter

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.ActivityParameterFormBinding
import muhamad.irfan.si_tahupm.ui.base.BaseActivity
import muhamad.irfan.si_tahupm.util.AppExtras

class ParameterFormActivity : BaseActivity() {
    private lateinit var binding: ActivityParameterFormBinding
    private val products by lazy { DemoRepository.allProducts().filter { it.category == "DASAR" } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParameterFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Form Parameter", "Tambah atau edit parameter")

        binding.spProduct.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, products.map { it.name })
        val editing = DemoRepository.getParameter(intent.getStringExtra(AppExtras.EXTRA_PARAMETER_ID))
        if (editing != null) {
            val productIndex = products.indexOfFirst { it.id == editing.productId }.coerceAtLeast(0)
            binding.spProduct.setSelection(productIndex)
            binding.etResultPerBatch.setText(editing.resultPerBatch.toString())
            binding.etNote.setText(editing.note)
            binding.cbActive.isChecked = editing.active
        } else {
            binding.cbActive.isChecked = true
        }

        binding.btnSave.setOnClickListener {
            val product = products.getOrNull(binding.spProduct.selectedItemPosition) ?: return@setOnClickListener
            runCatching {
                DemoRepository.saveParameter(
                    existingId = editing?.id,
                    productId = product.id,
                    resultPerBatch = binding.etResultPerBatch.text?.toString()?.toIntOrNull() ?: 0,
                    note = binding.etNote.text?.toString().orEmpty(),
                    active = binding.cbActive.isChecked
                )
            }.onSuccess {
                showMessage("Parameter berhasil disimpan.")
                startActivity(Intent(this, ParameterListActivity::class.java))
                finish()
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan parameter")
            }
        }
    }
}
