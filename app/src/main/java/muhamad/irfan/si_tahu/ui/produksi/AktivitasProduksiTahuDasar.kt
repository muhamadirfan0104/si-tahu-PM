package muhamad.irfan.si_tahu.ui.produksi

import android.os.Bundle
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.ActivityBasicProductionBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.InputAngka
import muhamad.irfan.si_tahu.util.PembantuPilihTanggalWaktu
import muhamad.irfan.si_tahu.utilitas.PembantuPilihProduk
import muhamad.irfan.si_tahu.utilitas.ProdukPilihanUi

class AktivitasProduksiTahuDasar : AktivitasDasar() {

    private lateinit var binding: ActivityBasicProductionBinding
    private var daftarProdukDasar: List<Produk> = emptyList()
    private var selectedProductIdAktif: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBasicProductionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindToolbar(
            binding.toolbar,
            "Produksi Tahu Dasar",
            "Input jumlah masak dan stok bertambah otomatis"
        )

        binding.etDate.setText(Formatter.currentDateOnly())
        binding.etTime.setText(Formatter.currentTimeOnly())

        binding.etDate.setOnClickListener {
            PembantuPilihTanggalWaktu.showDatePicker(
                this,
                binding.etDate.text?.toString()
            ) { selectedDate ->
                binding.etDate.setText(selectedDate)
            }
        }

        binding.etTime.setOnClickListener {
            val currentIso = Formatter.isoDate(
                binding.etDate.text?.toString().orEmpty(),
                binding.etTime.text?.toString().orEmpty() + ":00"
            )
            PembantuPilihTanggalWaktu.showTimePicker(this, currentIso) { selectedTime ->
                binding.etTime.setText(selectedTime)
            }
        }

        InputAngka.pasang(binding.etBatches, desimal = true)
        binding.etBatches.addTextChangedListener {
            updateEstimasi()
        }

        binding.cardProductPicker.setOnClickListener {
            showProductPicker()
        }

        binding.btnSave.setOnClickListener {
            simpanProduksi()
        }

        loadProdukDasar()
    }

    private fun loadProdukDasar() {
        lifecycleScope.launch {
            binding.btnSave.isEnabled = false
            binding.tvParameterInfo.text = "Memuat produk dasar..."
            binding.tvEstimation.text = "Estimasi hasil: -"
            binding.tvSelectedProductName.text = "Memuat produk..."
            binding.tvSelectedProductMeta.text = "Mohon tunggu sebentar"
            binding.tvProductLeading.text = "P"

            runCatching { RepositoriFirebaseUtama.muatProdukAktif() }
                .onSuccess { products ->
                    daftarProdukDasar = products
                        .filter { it.category.equals("DASAR", ignoreCase = true) }
                        .sortedBy { it.name.lowercase() }

                    selectedProductIdAktif = daftarProdukDasar.firstOrNull { it.id == selectedProductIdAktif }?.id
                        ?: daftarProdukDasar.firstOrNull()?.id

                    if (daftarProdukDasar.isEmpty()) {
                        updateProductSelector()
                        binding.tvParameterInfo.text = "Produk dasar tidak ditemukan."
                        binding.tvEstimation.text = "Estimasi hasil: -"
                        binding.btnSave.isEnabled = false
                        return@onSuccess
                    }

                    binding.btnSave.isEnabled = true
                    updateProductSelector()
                    updateInfoParameter()
                    updateEstimasi()
                }
                .onFailure {
                    daftarProdukDasar = emptyList()
                    selectedProductIdAktif = null
                    updateProductSelector()
                    binding.tvParameterInfo.text = "Gagal memuat produk dasar."
                    binding.tvEstimation.text = "Estimasi hasil: -"
                    binding.btnSave.isEnabled = false
                    showMessage(it.message ?: "Gagal memuat produk dasar")
                }
        }
    }

    private fun selectedProduk(): Produk? {
        return daftarProdukDasar.firstOrNull { it.id == selectedProductIdAktif }
    }

    private fun updateProductSelector() {
        val produk = selectedProduk()
        binding.tvProductPickerLabel.text = "Produk dipilih"
        binding.tvSelectedProductName.text = when {
            daftarProdukDasar.isEmpty() -> "Belum ada produk dasar"
            produk == null -> "Pilih produk dasar"
            else -> produk.name
        }
        binding.tvSelectedProductMeta.text = when {
            daftarProdukDasar.isEmpty() -> "Tambahkan produk dasar aktif terlebih dahulu"
            produk == null -> "Cari dan pilih produk dasar yang akan diproduksi"
            else -> listOf(
                produk.category.ifBlank { "DASAR" },
                if (produk.active) "Aktif" else "Nonaktif",
                "Stok ${Formatter.ribuan(produk.stock.toLong())} ${produk.unit}"
            ).joinToString(" • ")
        }
        binding.tvProductLeading.text = produk?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "P"
        binding.cardProductPicker.isEnabled = daftarProdukDasar.isNotEmpty()
        binding.cardProductPicker.alpha = if (daftarProdukDasar.isNotEmpty()) 1f else 0.7f
    }

    private fun showProductPicker() {
        if (daftarProdukDasar.isEmpty()) return

        PembantuPilihProduk.show(
            activity = this,
            title = "Pilih Produk Produksi",
            produk = daftarProdukDasar.map { produk ->
                ProdukPilihanUi(
                    id = produk.id,
                    namaProduk = produk.name,
                    jenisProduk = produk.category,
                    stokSaatIni = produk.stock.toLong(),
                    satuan = produk.unit,
                    aktifDijual = produk.active,
                    infoTambahan = "Siap diproduksi"
                )
            },
            selectedId = selectedProductIdAktif,
            kategoriOptions = listOf("Semua", "Dasar")
        ) { selected ->
            selectedProductIdAktif = selected.id
            updateProductSelector()
            updateInfoParameter()
            updateEstimasi()
        }
    }

    private fun updateInfoParameter() {
        val produk = selectedProduk()
        if (produk == null) {
            binding.tvParameterInfo.text = if (daftarProdukDasar.isEmpty()) {
                "Produk dasar belum tersedia."
            } else {
                "Produk dasar belum dipilih."
            }
            return
        }

        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatParameterAktif(produk.id) }
                .onSuccess { parameter ->
                    binding.tvParameterInfo.text = if (parameter == null) {
                        "Parameter aktif untuk ${produk.name} belum ada. Tambahkan dulu di menu Parameter Produksi."
                    } else {
                        "Parameter aktif ${produk.name}: ${Formatter.ribuan(parameter.resultPerBatch.toLong())} ${produk.unit} per 1x masak.\nCatatan: ${parameter.note.ifBlank { "-" }}"
                    }
                }
                .onFailure {
                    binding.tvParameterInfo.text = "Gagal memuat parameter produksi."
                }
        }
    }

    private fun updateEstimasi() {
        val produk = selectedProduk()
        if (produk == null) {
            binding.tvEstimation.text = "Estimasi hasil: -"
            return
        }

        val jumlahMasak = InputAngka.ambilDouble(binding.etBatches)

        if (jumlahMasak <= 0.0) {
            binding.tvEstimation.text = "Estimasi hasil: -"
            return
        }

        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatParameterAktif(produk.id) }
                .onSuccess { parameter ->
                    if (parameter == null) {
                        binding.tvEstimation.text = "Estimasi hasil: -"
                    } else {
                        val hasil = (parameter.resultPerBatch.toDouble() * jumlahMasak).roundToInt()
                        binding.tvEstimation.text = "Estimasi hasil: ${Formatter.ribuan(hasil.toLong())} ${produk.unit}"
                    }
                }
                .onFailure {
                    binding.tvEstimation.text = "Estimasi hasil: -"
                }
        }
    }

    private fun simpanProduksi() {
        val produk = selectedProduk()
        if (produk == null) {
            showMessage("Produk dasar belum dipilih.")
            return
        }

        val tanggal = binding.etDate.text?.toString().orEmpty()
        val waktu = binding.etTime.text?.toString().orEmpty()
        val jumlahMasak = InputAngka.ambilDouble(binding.etBatches).takeIf { it > 0.0 }
        val catatan = binding.etNote.text?.toString()?.trim().orEmpty()

        if (tanggal.isBlank()) {
            showMessage("Tanggal wajib diisi.")
            return
        }

        if (waktu.isBlank()) {
            showMessage("Waktu wajib diisi.")
            return
        }

        if (jumlahMasak == null || jumlahMasak <= 0.0) {
            showMessage("Jumlah masak harus lebih dari 0.")
            return
        }

        val dateTime = Formatter.isoDate(tanggal, "$waktu:00")

        lifecycleScope.launch {
            binding.btnSave.isEnabled = false
            binding.btnSave.text = "Menyimpan..."

            runCatching {
                RepositoriFirebaseUtama.simpanProduksiDasar(
                    dateTime = dateTime,
                    productId = produk.id,
                    batches = jumlahMasak,
                    note = catatan,
                    userAuthId = currentUserId()
                )
            }.onSuccess {
                binding.btnSave.isEnabled = true
                binding.btnSave.text = "Simpan Produksi"
                setResult(RESULT_OK)
                showMessage("Produksi dasar berhasil disimpan.")
                finish()
            }.onFailure {
                binding.btnSave.isEnabled = true
                binding.btnSave.text = "Simpan Produksi"
                showMessage(it.message ?: "Gagal menyimpan produksi dasar")
            }
        }
    }
}
