package muhamad.irfan.si_tahu.ui.produksi

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.ActivityBasicProductionBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.PembantuPilihTanggalWaktu

class AktivitasProduksiTahuDasar : AktivitasDasar() {

    private lateinit var binding: ActivityBasicProductionBinding
    private var daftarProdukDasar: List<Produk> = emptyList()

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

        binding.etBatches.addTextChangedListener {
            updateEstimasi()
        }

        binding.spProduct.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateInfoParameter()
                updateEstimasi()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
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

            runCatching { RepositoriFirebaseUtama.muatProdukAktif() }
                .onSuccess { products ->
                    daftarProdukDasar = products
                        .filter { it.category.equals("DASAR", ignoreCase = true) }
                        .sortedBy { it.name.lowercase() }

                    if (daftarProdukDasar.isEmpty()) {
                        binding.spProduct.adapter = AdapterSpinner.stringAdapter(
                            this@AktivitasProduksiTahuDasar,
                            listOf("Belum ada produk dasar aktif")
                        )
                        binding.tvParameterInfo.text = "Produk dasar tidak ditemukan."
                        binding.tvEstimation.text = "Estimasi hasil: -"
                        binding.btnSave.isEnabled = false
                        return@onSuccess
                    }

                    binding.spProduct.adapter = AdapterSpinner.stringAdapter(
                        this@AktivitasProduksiTahuDasar,
                        daftarProdukDasar.map { it.name }
                    )

                    binding.btnSave.isEnabled = true
                    updateInfoParameter()
                    updateEstimasi()
                }
                .onFailure {
                    binding.spProduct.adapter = AdapterSpinner.stringAdapter(
                        this@AktivitasProduksiTahuDasar,
                        listOf("Gagal memuat produk")
                    )
                    binding.tvParameterInfo.text = "Gagal memuat produk dasar."
                    binding.tvEstimation.text = "Estimasi hasil: -"
                    binding.btnSave.isEnabled = false
                    showMessage(it.message ?: "Gagal memuat produk dasar")
                }
        }
    }

    private fun selectedProduk(): Produk? {
        return daftarProdukDasar.getOrNull(binding.spProduct.selectedItemPosition)
    }

    private fun updateInfoParameter() {
        val produk = selectedProduk()
        if (produk == null) {
            binding.tvParameterInfo.text = "Produk dasar belum dipilih."
            return
        }

        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatParameterAktif(produk.id) }
                .onSuccess { parameter ->
                    binding.tvParameterInfo.text = if (parameter == null) {
                        "Parameter aktif untuk ${produk.name} belum ada. Tambahkan dulu di menu Parameter Produksi."
                    } else {
                        "Parameter aktif ${produk.name}: ${parameter.resultPerBatch} ${produk.unit} per 1x masak.\nCatatan: ${parameter.note.ifBlank { "-" }}"
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

        val jumlahMasak = binding.etBatches.text?.toString()
            ?.trim()
            ?.replace(",", ".")
            ?.toDoubleOrNull() ?: 0.0

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
                        binding.tvEstimation.text = "Estimasi hasil: $hasil ${produk.unit}"
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
        val jumlahMasak = binding.etBatches.text?.toString()?.trim()?.replace(",", ".")?.toDoubleOrNull()
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