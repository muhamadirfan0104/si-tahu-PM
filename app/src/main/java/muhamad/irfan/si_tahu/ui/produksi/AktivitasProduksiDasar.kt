package muhamad.irfan.si_tahu.ui.produksi

import android.os.Bundle
import android.widget.Spinner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.ParameterProduksi
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.ActivityBasicProductionBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.PembantuPilihTanggalWaktu

class AktivitasProduksiDasar : AktivitasDasar() {

    private lateinit var binding: ActivityBasicProductionBinding
    private var products: List<Produk> = emptyList()
    private var activeParameter: ParameterProduksi? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        binding = ActivityBasicProductionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Produksi Tahu Dasar", "Hanya produk kategori dasar yang punya parameter")

        setupForm()
        loadProducts()
    }

    private fun setupForm() {
        binding.etDate.setText(Formatter.currentDateOnly())
        binding.etTime.setText(Formatter.currentTimeOnly())
        binding.btnSave.isEnabled = false

        binding.etDate.setOnClickListener {
            PembantuPilihTanggalWaktu.showDatePicker(this, binding.etDate.text?.toString()) {
                binding.etDate.setText(it)
            }
        }
        binding.etTime.setOnClickListener {
            PembantuPilihTanggalWaktu.showTimePicker(this, binding.etTime.text?.toString()) {
                binding.etTime.setText(it)
            }
        }
        binding.spProduct.setOnItemSelectedListener(SimpleItemSelectedListener { updateParameterInfo() })
        binding.btnSave.setOnClickListener { saveProduction() }
    }

    private fun loadProducts() {
        lifecycleScope.launch {
            runCatching {
                RepositoriFirebaseUtama.muatProdukAktif().filter { it.category.equals("DASAR", ignoreCase = true) }
            }
                .onSuccess {
                    products = it
                    bindProductSpinner(binding.spProduct)
                    updateParameterInfo()
                }
                .onFailure {
                    products = emptyList()
                    bindProductSpinner(binding.spProduct)
                    binding.tvParameterInfo.text = it.message ?: "Gagal memuat produk"
                    binding.btnSave.isEnabled = false
                }
        }
    }

    private fun bindProductSpinner(spinner: Spinner) {
        val labels = if (products.isEmpty()) listOf("Belum ada produk dasar") else products.map { "${it.code} • ${it.name}" }
        spinner.adapter = AdapterSpinner.stringAdapter(this, labels)
    }

    private fun selectedProduct(): Produk? = products.getOrNull(binding.spProduct.selectedItemPosition)

    private fun updateParameterInfo() {
        val product = selectedProduct()
        if (product == null) {
            activeParameter = null
            binding.btnSave.isEnabled = false
            binding.tvParameterInfo.text = "Belum ada produk kategori dasar. Tambahkan produk dasar dan parameter produksi terlebih dahulu."
            return
        }

        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatParameterAktif(product.id) }
                .onSuccess { parameter ->
                    activeParameter = parameter
                    if (parameter == null) {
                        binding.btnSave.isEnabled = false
                        binding.tvParameterInfo.text = "${product.name} belum punya parameter aktif. Produksi dasar belum bisa disimpan."
                    } else {
                        binding.btnSave.isEnabled = true
                        binding.tvParameterInfo.text = "Parameter aktif ${product.name}: ${parameter.resultPerBatch} ${product.unit} per batch"
                    }
                }
                .onFailure {
                    activeParameter = null
                    binding.btnSave.isEnabled = false
                    binding.tvParameterInfo.text = it.message ?: "Gagal memuat parameter"
                }
        }
    }

    private fun saveProduction() {
        val product = selectedProduct()
        if (product == null) {
            showMessage("Produk dasar belum tersedia")
            return
        }
        if (activeParameter == null) {
            showMessage("Parameter produksi belum ada. Tambahkan parameter dulu sebelum masak.")
            return
        }

        val date = binding.etDate.text?.toString().orEmpty()
        val time = binding.etTime.text?.toString().orEmpty().ifBlank { "07:00" }
        val batches = binding.etBatches.text?.toString()?.toIntOrNull() ?: 0
        val note = binding.etNote.text?.toString().orEmpty().trim()

        lifecycleScope.launch {
            binding.btnSave.isEnabled = false
            runCatching {
                val id = RepositoriFirebaseUtama.simpanProduksiDasar(
                    dateTime = Formatter.isoDate(date, "$time:00"),
                    productId = product.id,
                    batches = batches,
                    note = note,
                    userAuthId = currentUserId()
                )
                RepositoriFirebaseUtama.buildProductionDetailText(id)
            }.onSuccess { detail ->
                showReceiptModal("Produksi tersimpan", detail, "Bagikan")
                binding.etBatches.setText("")
                binding.etNote.setText("")
                updateParameterInfo()
                loadProducts()
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan produksi")
                binding.btnSave.isEnabled = activeParameter != null
            }
        }
    }
}

private class SimpleItemSelectedListener(
    private val onSelected: () -> Unit
) : android.widget.AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) = onSelected()
    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
}
