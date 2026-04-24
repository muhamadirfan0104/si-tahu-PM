package muhamad.irfan.si_tahu.ui.stok

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.ActivityStockAdjustmentBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.InputAngka
import muhamad.irfan.si_tahu.util.PembantuPilihTanggalWaktu
import muhamad.irfan.si_tahu.utilitas.PembantuPilihProduk
import muhamad.irfan.si_tahu.utilitas.ProdukPilihanUi

class AktivitasStockAdjustment : AktivitasDasar() {

    private lateinit var binding: ActivityStockAdjustmentBinding
    private var products: List<Produk> = emptyList()
    private var selectedProductId: String? = null
    private var modeKadaluarsa = false
    private var stokKadaluarsaTerpilih: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        binding = ActivityStockAdjustmentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        modeKadaluarsa = intent.getBooleanExtra(EXTRA_EXPIRED_MODE, false)

        bindToolbar(
            binding.toolbar,
            if (modeKadaluarsa) "Buang Stok Kadaluarsa" else "Stock Adjustment",
            if (modeKadaluarsa) "Tindak lanjuti stok yang sudah tidak layak jual" else "Kurangi stok fisik dari stok sistem"
        )

        setupForm()
        loadProducts()
    }

    private fun setupForm() {
        binding.etDate.setText(Formatter.currentDateOnly())
        InputAngka.pasang(binding.etQty)
        updateProductSelector()

        if (modeKadaluarsa) {
            binding.etQty.hint = "Jumlah stok kadaluarsa yang dibuang"
            binding.etNote.hint = "Catatan tindakan stok kadaluarsa"
            binding.etNote.setText("Dibuang karena kadaluarsa / tidak layak jual")
            binding.btnSave.text = "Buang Stok Kadaluarsa"
        }

        binding.etDate.setOnClickListener {
            PembantuPilihTanggalWaktu.showDatePicker(
                this,
                binding.etDate.text?.toString()
            ) { selectedDate ->
                binding.etDate.setText(selectedDate)
            }
        }

        binding.cardProductPicker.setOnClickListener {
            if (!modeKadaluarsa) showProductPicker()
        }

        binding.btnSave.setOnClickListener {
            saveAdjustment()
        }
    }

    private fun loadProducts() {
        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatSemuaProduk() }
                .onSuccess { result ->
                    products = result.sortedBy { it.name.lowercase() }

                    val preselectedId = intent.getStringExtra(EXTRA_PRODUCT_ID)
                    selectedProductId = when {
                        !preselectedId.isNullOrBlank() && products.any { it.id == preselectedId } -> preselectedId
                        products.any { it.id == selectedProductId } -> selectedProductId
                        else -> products.firstOrNull()?.id
                    }
                    muatStokKadaluarsaTerpilih()
                    updateProductSelector()
                }
                .onFailure {
                    products = emptyList()
                    selectedProductId = null
                    stokKadaluarsaTerpilih = 0L
                    updateProductSelector()
                    showMessage(it.message ?: "Gagal memuat produk")
                }
        }
    }

    private fun muatStokKadaluarsaTerpilih() {
        val productId = selectedProductId ?: return
        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatTotalStokKadaluarsa(productId) }
                .onSuccess { expiredQty ->
                    stokKadaluarsaTerpilih = expiredQty
                    if (modeKadaluarsa && expiredQty > 0L) {
                        binding.etQty.setText(Formatter.ribuan(expiredQty))
                    }
                    updateProductSelector()
                }
        }
    }

    private fun selectedProduct(): Produk? {
        return products.firstOrNull { it.id == selectedProductId }
    }

    private fun updateProductSelector() {
        val produk = selectedProduct()
        binding.tvProductPickerLabel.text = if (modeKadaluarsa) "Produk kadaluarsa" else "Produk dipilih"
        binding.tvSelectedProductName.text = when {
            products.isEmpty() -> "Belum ada produk"
            produk == null -> "Pilih produk adjustment"
            else -> produk.name
        }
        binding.tvSelectedProductMeta.text = when {
            products.isEmpty() -> "Tambahkan produk terlebih dahulu sebelum melakukan adjustment stok"
            produk == null -> "Cari dan pilih produk yang stoknya akan disesuaikan"
            modeKadaluarsa -> "Kadaluarsa ${Formatter.ribuan(stokKadaluarsaTerpilih)} ${produk.unit} • Total fisik ${Formatter.ribuan(produk.stock.toLong())} ${produk.unit}"
            else -> listOf(
                produk.category.ifBlank { "Produk" },
                if (produk.active) "Aktif" else "Nonaktif",
                "Stok fisik ${Formatter.ribuan(produk.stock.toLong())} ${produk.unit}",
                "Layak jual ${Formatter.ribuan((produk.safeStock + produk.nearExpiredStock).toLong())} ${produk.unit}"
            ).joinToString(" • ")
        }
        binding.tvProductLeading.text = produk?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "P"
        binding.cardProductPicker.isEnabled = products.isNotEmpty() && !modeKadaluarsa
        binding.cardProductPicker.alpha = if (products.isNotEmpty()) 1f else 0.7f
        binding.tvStockInfo.text = when {
            products.isEmpty() -> "Belum ada produk yang bisa di-adjust."
            produk == null -> "Pilih produk untuk melihat stok saat ini."
            modeKadaluarsa -> "Tindakan ini mengurangi stok kadaluarsa dari batch lama dan mencatatnya sebagai Adjustment Kadaluarsa. Stok jual tidak akan memasukkan stok kadaluarsa."
            else -> "Stok fisik: ${Formatter.ribuan(produk.stock.toLong())} ${produk.unit}\nLayak jual: ${Formatter.ribuan((produk.safeStock + produk.nearExpiredStock).toLong())} ${produk.unit}\nKadaluarsa/perlu tindakan: ${Formatter.ribuan(produk.expiredStock.toLong())} ${produk.unit}"
        }
    }

    private fun showProductPicker() {
        if (products.isEmpty()) return

        PembantuPilihProduk.show(
            activity = this,
            title = "Pilih Produk Adjustment",
            produk = products.map { produk ->
                ProdukPilihanUi(
                    id = produk.id,
                    namaProduk = produk.name,
                    jenisProduk = produk.category,
                    stokSaatIni = produk.stock.toLong(),
                    satuan = produk.unit,
                    aktifDijual = produk.active,
                    infoTambahan = "Layak jual ${Formatter.ribuan((produk.safeStock + produk.nearExpiredStock).toLong())} • Kadaluarsa ${Formatter.ribuan(produk.expiredStock.toLong())}"
                )
            },
            selectedId = selectedProductId,
            kategoriOptions = listOf("Semua", "Dasar", "Olahan")
        ) { selected ->
            selectedProductId = selected.id
            muatStokKadaluarsaTerpilih()
            updateProductSelector()
        }
    }

    private fun saveAdjustment() {
        val product = selectedProduct()
        if (product == null) {
            showMessage("Produk belum tersedia")
            return
        }

        val date = binding.etDate.text?.toString().orEmpty()
        val qty = InputAngka.ambilInt(binding.etQty)
        val note = binding.etNote.text?.toString()?.trim().orEmpty()

        if (date.isBlank()) {
            showMessage("Tanggal wajib diisi")
            return
        }

        if (qty <= 0) {
            showMessage(if (modeKadaluarsa) "Jumlah stok kadaluarsa harus lebih dari 0" else "Jumlah adjustment harus lebih dari 0")
            return
        }

        if (modeKadaluarsa) {
            if (qty > stokKadaluarsaTerpilih) {
                showMessage("Jumlah melebihi stok kadaluarsa yang tersedia")
                return
            }
        } else if (qty > product.stock) {
            showMessage("Jumlah adjustment melebihi stok saat ini")
            return
        }

        if (note.isBlank()) {
            showMessage(if (modeKadaluarsa) "Catatan tindakan wajib diisi" else "Alasan adjustment wajib diisi")
            return
        }

        lifecycleScope.launch {
            binding.btnSave.isEnabled = false
            binding.btnSave.text = "Menyimpan..."

            runCatching {
                val id = if (modeKadaluarsa) {
                    RepositoriFirebaseUtama.simpanAdjustmentKadaluarsa(
                        dateOnly = date,
                        productId = product.id,
                        qty = qty,
                        note = note,
                        userAuthId = currentUserId()
                    )
                } else {
                    RepositoriFirebaseUtama.simpanAdjustment(
                        dateOnly = date,
                        productId = product.id,
                        type = "subtract",
                        qty = qty,
                        note = note,
                        userAuthId = currentUserId()
                    )
                }
                RepositoriFirebaseUtama.buildAdjustmentDetailText(id)
            }.onSuccess { detail ->
                setResult(RESULT_OK, Intent().putExtra(EXTRA_STOCK_UPDATED, true))
                showReceiptModal(if (modeKadaluarsa) "Stok kadaluarsa ditindaklanjuti" else "Adjustment tersimpan", detail)
                binding.etQty.setText("")
                if (!modeKadaluarsa) binding.etNote.setText("")
                loadProducts()
            }.onFailure {
                showMessage(it.message ?: if (modeKadaluarsa) "Gagal membuang stok kadaluarsa" else "Gagal menyimpan adjustment")
            }

            binding.btnSave.isEnabled = true
            binding.btnSave.text = if (modeKadaluarsa) "Buang Stok Kadaluarsa" else "Simpan Adjustment"
        }
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "extra_product_id"
        const val EXTRA_STOCK_UPDATED = "extra_stock_updated"
        const val EXTRA_EXPIRED_MODE = "extra_expired_mode"
    }
}
