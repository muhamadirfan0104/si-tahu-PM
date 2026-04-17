package muhamad.irfan.si_tahu.ui.produksi

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.ActivityConversionBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.PembantuPilihTanggalWaktu

class AktivitasKonversi : AktivitasDasar() {

    private lateinit var binding: ActivityConversionBinding
    private var basicProducts: List<Produk> = emptyList()
    private var processedProducts: List<Produk> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        binding = ActivityConversionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Produksi Produk Olahan", "Dari produk dasar ke produk olahan")

        setupForm()
        loadProducts()
    }

    private fun setupForm() {
        binding.etDate.setText(Formatter.currentDateOnly())
        binding.etTime.setText(Formatter.currentTimeOnly())

        binding.etDate.setOnClickListener {
            PembantuPilihTanggalWaktu.showDatePicker(this, binding.etDate.text?.toString()) { binding.etDate.setText(it) }
        }
        binding.etTime.setOnClickListener {
            PembantuPilihTanggalWaktu.showTimePicker(this, binding.etTime.text?.toString()) { binding.etTime.setText(it) }
        }
        binding.btnSave.setOnClickListener { saveConversion() }
    }

    private fun loadProducts() {
        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatProdukAktif() }
                .onSuccess { allProducts ->
                    basicProducts = allProducts.filter { it.category.equals("DASAR", ignoreCase = true) }
                    processedProducts = allProducts.filter { it.category.equals("OLAHAN", ignoreCase = true) }

                    val fromLabels = if (basicProducts.isEmpty()) listOf("Belum ada produk dasar") else basicProducts.map { produk -> "${produk.code} • ${produk.name}" }
                    val toLabels = if (processedProducts.isEmpty()) listOf("Belum ada produk olahan") else processedProducts.map { produk -> "${produk.code} • ${produk.name}" }

                    binding.spFromProduct.adapter = AdapterSpinner.stringAdapter(this@AktivitasKonversi, fromLabels)
                    binding.spToProduct.adapter = AdapterSpinner.stringAdapter(this@AktivitasKonversi, toLabels)
                    binding.btnSave.isEnabled = basicProducts.isNotEmpty() && processedProducts.isNotEmpty()
                }
                .onFailure {
                    basicProducts = emptyList()
                    processedProducts = emptyList()
                    binding.btnSave.isEnabled = false
                    showMessage(it.message ?: "Gagal memuat produk")
                }
        }
    }

    private fun saveConversion() {
        val from = basicProducts.getOrNull(binding.spFromProduct.selectedItemPosition)
        val to = processedProducts.getOrNull(binding.spToProduct.selectedItemPosition)
        if (from == null || to == null) {
            showMessage("Produk dasar atau produk olahan belum tersedia")
            return
        }

        val date = binding.etDate.text?.toString().orEmpty()
        val time = binding.etTime.text?.toString().orEmpty().ifBlank { "08:00" }
        val inputQty = binding.etInputQty.text?.toString()?.toIntOrNull() ?: 0
        val outputQty = binding.etOutputQty.text?.toString()?.toIntOrNull() ?: 0
        val note = binding.etNote.text?.toString().orEmpty().trim()

        lifecycleScope.launch {
            binding.btnSave.isEnabled = false
            runCatching {
                val id = RepositoriFirebaseUtama.simpanKonversi(
                    dateTime = Formatter.isoDate(date, "$time:00"),
                    fromProductId = from.id,
                    toProductId = to.id,
                    inputQty = inputQty,
                    outputQty = outputQty,
                    note = note,
                    userAuthId = currentUserId()
                )
                RepositoriFirebaseUtama.buildProductionDetailText(id)
            }.onSuccess { detail ->
                showReceiptModal("Konversi tersimpan", detail, "Bagikan")
                binding.etInputQty.setText("")
                binding.etOutputQty.setText("")
                binding.etNote.setText("")
                loadProducts()
            }.onFailure {
                binding.btnSave.isEnabled = basicProducts.isNotEmpty() && processedProducts.isNotEmpty()
                showMessage(it.message ?: "Gagal menyimpan konversi")
            }
        }
    }
}
