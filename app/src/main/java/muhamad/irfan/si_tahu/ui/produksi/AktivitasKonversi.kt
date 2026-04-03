package muhamad.irfan.si_tahu.ui.production

import android.os.Bundle
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.RepositoriLokal
import muhamad.irfan.si_tahu.databinding.ActivityConversionBinding
import muhamad.irfan.si_tahu.ui.base.AktivitasDasar
import muhamad.irfan.si_tahu.ui.main.AktivitasUtamaAdmin
import muhamad.irfan.si_tahu.util.PembantuPilihTanggalWaktu
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.AdapterSpinner

class AktivitasKonversi : AktivitasDasar() {
    private lateinit var binding: ActivityConversionBinding
    private val products by lazy { RepositoriLokal.allProducts() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Produksi Turunan", "Olah stok dasar menjadi produk turunan")

        binding.etDate.setText(Formatter.currentDateOnly())
        binding.etTime.setText(Formatter.currentTimeOnly())
        binding.etDate.setOnClickListener {
            PembantuPilihTanggalWaktu.showDatePicker(this, binding.etDate.text?.toString()) { selected ->
                binding.etDate.setText(selected)
            }
        }
        binding.etTime.setOnClickListener {
            PembantuPilihTanggalWaktu.showTimePicker(this, currentDateTime()) { selected ->
                binding.etTime.setText(selected)
            }
        }

        setupProduk()

        binding.btnSave.setOnClickListener {
            if (products.size < 2) {
                showMessage("Tambahkan minimal dua produk terlebih dahulu.")
                return@setOnClickListener
            }

            val from = products.getOrNull(binding.spFromProduct.selectedItemPosition) ?: return@setOnClickListener
            val to = products.getOrNull(binding.spToProduct.selectedItemPosition) ?: return@setOnClickListener
            runCatching {
                RepositoriLokal.saveConversion(
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
                startActivity(AktivitasUtamaAdmin.intent(this, R.id.nav_admin_production))
                finish()
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan konversi")
            }
        }
    }

    private fun setupProduk() {
        if (products.size < 2) {
            val placeholder = listOf("Belum ada produk")
            binding.spFromProduct.adapter = AdapterSpinner.stringAdapter(this, placeholder)
            binding.spToProduct.adapter = AdapterSpinner.stringAdapter(this, placeholder)
            binding.btnSave.isEnabled = false
            binding.etInputQty.setText("")
            binding.etOutputQty.setText("")
            binding.etNote.hint = "Tambahkan minimal dua produk terlebih dahulu"
            return
        }

        val names = products.map { it.name }
        binding.spFromProduct.adapter = AdapterSpinner.stringAdapter(this, names)
        binding.spToProduct.adapter = AdapterSpinner.stringAdapter(this, names)
        binding.spFromProduct.setSelection(products.indexOfFirst { it.id == "prd-putih" }.takeIf { it >= 0 } ?: 0)
        binding.spToProduct.setSelection(products.indexOfFirst { it.id == "prd-goreng" }.takeIf { it >= 0 } ?: 0)
        binding.btnSave.isEnabled = true
    }

    private fun currentDateTime(): String {
        return Formatter.isoDate(
            binding.etDate.text?.toString().orEmpty(),
            binding.etTime.text?.toString().orEmpty() + ":00"
        )
    }
}
