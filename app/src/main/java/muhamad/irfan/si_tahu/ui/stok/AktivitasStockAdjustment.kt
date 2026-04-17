package muhamad.irfan.si_tahu.ui.stok

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.ActivityStockAdjustmentBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.PembantuPilihTanggalWaktu

class AktivitasStockAdjustment : AktivitasDasar() {

    private lateinit var binding: ActivityStockAdjustmentBinding
    private var products: List<Produk> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        binding = ActivityStockAdjustmentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Stock Adjustment", "Tambah atau kurangi stok")

        setupForm()
        loadProducts()
    }

    private fun setupForm() {
        binding.etDate.setText(Formatter.currentDateOnly())
        binding.etDate.setOnClickListener {
            PembantuPilihTanggalWaktu.showDatePicker(this, binding.etDate.text?.toString()) { binding.etDate.setText(it) }
        }
        binding.spType.adapter = AdapterSpinner.stringAdapter(this, listOf("Tambah stok", "Kurangi stok"))
        binding.btnSave.setOnClickListener { saveAdjustment() }
    }

    private fun loadProducts() {
        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatSemuaProduk() }
                .onSuccess {
                    products = it
                    val labels = if (products.isEmpty()) listOf("Belum ada produk") else products.map { produk -> "${produk.code} • ${produk.name}" }
                    binding.spProduct.adapter = AdapterSpinner.stringAdapter(this@AktivitasStockAdjustment, labels)
                    val preselectedId = intent.getStringExtra(EXTRA_PRODUCT_ID)
                    val preselectedIndex = products.indexOfFirst { product -> product.id == preselectedId }
                    if (preselectedIndex >= 0) binding.spProduct.setSelection(preselectedIndex)
                }
                .onFailure {
                    showMessage(it.message ?: "Gagal memuat produk")
                }
        }
    }

    private fun saveAdjustment() {
        val product = products.getOrNull(binding.spProduct.selectedItemPosition)
        if (product == null) {
            showMessage("Produk belum tersedia")
            return
        }
        val type = if (binding.spType.selectedItemPosition == 0) "add" else "subtract"
        val qty = binding.etQty.text?.toString()?.toIntOrNull() ?: 0
        val note = binding.etNote.text?.toString().orEmpty().trim()
        val date = binding.etDate.text?.toString().orEmpty()

        lifecycleScope.launch {
            binding.btnSave.isEnabled = false
            runCatching {
                val id = RepositoriFirebaseUtama.simpanAdjustment(date, product.id, type, qty, note, currentUserId())
                RepositoriFirebaseUtama.buildAdjustmentDetailText(id)
            }.onSuccess { detail ->
                showReceiptModal("Adjustment tersimpan", detail, "Bagikan")
                binding.etQty.setText("")
                binding.etNote.setText("")
                loadProducts()
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan adjustment")
            }
            binding.btnSave.isEnabled = true
        }
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "extra_product_id"
    }
}
