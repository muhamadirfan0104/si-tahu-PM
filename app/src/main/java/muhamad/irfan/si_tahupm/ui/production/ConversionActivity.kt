package muhamad.irfan.si_tahupm.ui.production

import android.os.Bundle
import android.widget.ArrayAdapter
import muhamad.irfan.si_tahupm.R
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.ActivityConversionBinding
import muhamad.irfan.si_tahupm.ui.base.BaseActivity
import muhamad.irfan.si_tahupm.ui.main.AdminMainActivity
import muhamad.irfan.si_tahupm.util.DateTimePickerHelper
import muhamad.irfan.si_tahupm.util.Formatters

class ConversionActivity : BaseActivity() {
    private lateinit var binding: ActivityConversionBinding
    private val products by lazy { DemoRepository.allProducts() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Produksi Turunan", "Olah stok dasar menjadi produk turunan")

        binding.etDate.setText(DemoRepository.latestDateOnly())
        binding.etTime.setText(DemoRepository.latestTimeOnly())
        binding.etDate.setOnClickListener {
            DateTimePickerHelper.showDatePicker(this, binding.etDate.text?.toString()) { selected -> binding.etDate.setText(selected) }
        }
        binding.etTime.setOnClickListener {
            DateTimePickerHelper.showTimePicker(this, currentDateTime()) { selected -> binding.etTime.setText(selected) }
        }
        val names = products.map { it.name }
        binding.spFromProduct.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        binding.spToProduct.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        binding.spFromProduct.setSelection(products.indexOfFirst { it.id == "prd-putih" }.coerceAtLeast(0))
        binding.spToProduct.setSelection(products.indexOfFirst { it.id == "prd-goreng" }.coerceAtLeast(0))

        binding.btnSave.setOnClickListener {
            val from = products.getOrNull(binding.spFromProduct.selectedItemPosition) ?: return@setOnClickListener
            val to = products.getOrNull(binding.spToProduct.selectedItemPosition) ?: return@setOnClickListener
            runCatching {
                DemoRepository.saveConversion(
                    dateTime = currentDateTime(),
                    fromProductId = from.id,
                    toProductId = to.id,
                    inputQty = binding.etInputQty.text?.toString()?.toIntOrNull() ?: 0,
                    outputQty = binding.etOutputQty.text?.toString()?.toIntOrNull() ?: 0,
                    note = binding.etNote.text?.toString().orEmpty(),
                    userId = currentUserId()
                )
            }.onSuccess {
                showMessage("Konversi berhasil disimpan.")
                startActivity(AdminMainActivity.intent(this, R.id.nav_admin_production))
                finish()
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan konversi")
            }
        }
    }

    private fun currentDateTime(): String {
        return Formatters.isoDate(
            binding.etDate.text?.toString().orEmpty(),
            binding.etTime.text?.toString().orEmpty() + ":00"
        )
    }
}
