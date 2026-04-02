package muhamad.irfan.si_tahupm.ui.production

import android.os.Bundle
import android.widget.ArrayAdapter
import muhamad.irfan.si_tahupm.R
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.ActivityBasicProductionBinding
import muhamad.irfan.si_tahupm.ui.base.BaseActivity
import muhamad.irfan.si_tahupm.ui.main.AdminMainActivity
import muhamad.irfan.si_tahupm.ui.main.SimpleItemSelectedListener
import muhamad.irfan.si_tahupm.util.DateTimePickerHelper
import muhamad.irfan.si_tahupm.util.Formatters

class BasicProductionActivity : BaseActivity() {
    private lateinit var binding: ActivityBasicProductionBinding
    private val products by lazy { DemoRepository.allProducts().filter { it.category == "DASAR" } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBasicProductionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Produksi Dasar", "Input produksi untuk produk dasar")

        binding.etDate.setText(DemoRepository.latestDateOnly())
        binding.etTime.setText(DemoRepository.latestTimeOnly())
        binding.etDate.setOnClickListener {
            DateTimePickerHelper.showDatePicker(this, binding.etDate.text?.toString()) { selected -> binding.etDate.setText(selected) }
        }
        binding.etTime.setOnClickListener {
            DateTimePickerHelper.showTimePicker(this, currentDateTime()) { selected -> binding.etTime.setText(selected) }
        }

        binding.spProduct.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, products.map { it.name })
        binding.spProduct.onItemSelectedListener = SimpleItemSelectedListener { refreshParameterInfo() }
        binding.btnSave.setOnClickListener {
            val product = selectedProduct() ?: return@setOnClickListener
            val batches = binding.etBatches.text?.toString()?.toIntOrNull() ?: 0
            runCatching {
                DemoRepository.saveProduction(
                    dateTime = currentDateTime(),
                    productId = product.id,
                    batches = batches,
                    note = binding.etNote.text?.toString().orEmpty(),
                    userId = currentUserId()
                )
            }.onSuccess {
                showMessage("Produksi berhasil disimpan.")
                startActivity(AdminMainActivity.intent(this, R.id.nav_admin_production))
                finish()
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan produksi")
            }
        }
        refreshParameterInfo()
    }

    private fun currentDateTime(): String {
        return Formatters.isoDate(
            binding.etDate.text?.toString().orEmpty(),
            binding.etTime.text?.toString().orEmpty() + ":00"
        )
    }

    private fun selectedProduct() = products.getOrNull(binding.spProduct.selectedItemPosition)

    private fun refreshParameterInfo() {
        val product = selectedProduct() ?: return
        val parameter = DemoRepository.activeParameter(product.id)
        binding.tvParameterInfo.text = if (parameter == null) {
            "Belum ada parameter aktif untuk ${product.name}. Sistem akan memakai default 100 pcs per masak."
        } else {
            "${product.name}: ${parameter.resultPerBatch} pcs per masak. ${parameter.note}"
        }
    }
}
