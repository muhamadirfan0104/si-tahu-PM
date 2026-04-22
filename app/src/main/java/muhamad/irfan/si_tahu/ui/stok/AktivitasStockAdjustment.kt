package muhamad.irfan.si_tahu.ui.stok

import android.content.Intent
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

        bindToolbar(
            binding.toolbar,
            "Stock Adjustment",
            "Sesuaikan stok fisik dengan stok sistem"
        )

        setupForm()
        loadProducts()
    }

    private fun setupForm() {
        binding.etDate.setText(Formatter.currentDateOnly())

        binding.etDate.setOnClickListener {
            PembantuPilihTanggalWaktu.showDatePicker(
                this,
                binding.etDate.text?.toString()
            ) { selectedDate ->
                binding.etDate.setText(selectedDate)
            }
        }

        binding.spType.adapter = AdapterSpinner.stringAdapter(
            this,
            listOf("Tambah stok", "Kurangi stok")
        )

        binding.btnSave.setOnClickListener {
            saveAdjustment()
        }
    }

    private fun loadProducts() {
        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatSemuaProduk() }
                .onSuccess { result ->
                    products = result

                    val labels = if (products.isEmpty()) {
                        listOf("Belum ada produk")
                    } else {
                        products.map { "${it.name} • stok ${it.stock} ${it.unit}" }
                    }

                    binding.spProduct.adapter =
                        AdapterSpinner.stringAdapter(this@AktivitasStockAdjustment, labels)

                    val preselectedId = intent.getStringExtra(EXTRA_PRODUCT_ID)
                    val preselectedIndex = products.indexOfFirst { it.id == preselectedId }
                    if (preselectedIndex >= 0) {
                        binding.spProduct.setSelection(preselectedIndex)
                    }
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

        val date = binding.etDate.text?.toString().orEmpty()
        val type = if (binding.spType.selectedItemPosition == 0) "add" else "subtract"
        val qty = binding.etQty.text?.toString()?.trim()?.toIntOrNull() ?: 0
        val note = binding.etNote.text?.toString()?.trim().orEmpty()

        if (date.isBlank()) {
            showMessage("Tanggal wajib diisi")
            return
        }

        if (qty <= 0) {
            showMessage("Jumlah adjustment harus lebih dari 0")
            return
        }

        if (note.isBlank()) {
            showMessage("Alasan adjustment wajib diisi")
            return
        }

        lifecycleScope.launch {
            binding.btnSave.isEnabled = false
            binding.btnSave.text = "Menyimpan..."

            runCatching {
                val id = RepositoriFirebaseUtama.simpanAdjustment(
                    dateOnly = date,
                    productId = product.id,
                    type = type,
                    qty = qty,
                    note = note,
                    userAuthId = currentUserId()
                )
                RepositoriFirebaseUtama.buildAdjustmentDetailText(id)
            }.onSuccess { detail ->
                setResult(RESULT_OK, Intent().putExtra(EXTRA_STOCK_UPDATED, true))
                showReceiptModal("Adjustment tersimpan", detail)
                binding.etQty.setText("")
                binding.etNote.setText("")
                loadProducts()
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan adjustment")
            }

            binding.btnSave.isEnabled = true
            binding.btnSave.text = "Simpan Adjustment"
        }
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "extra_product_id"
        const val EXTRA_STOCK_UPDATED = "extra_stock_updated"
    }
}