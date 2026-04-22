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

class AktivitasKonversiProduk : AktivitasDasar() {

    private lateinit var binding: ActivityConversionBinding

    private var daftarProdukDasar: List<Produk> = emptyList()
    private var daftarProdukOlahan: List<Produk> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        binding = ActivityConversionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindToolbar(
            binding.toolbar,
            "Produksi Produk Olahan",
            "Kurangi stok bahan, tambah stok hasil olahan"
        )

        setupForm()
        loadProduk()
    }

    private fun setupForm() {
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

            PembantuPilihTanggalWaktu.showTimePicker(
                this,
                currentIso
            ) { selectedTime ->
                binding.etTime.setText(selectedTime)
            }
        }

        binding.btnSave.setOnClickListener {
            simpanKonversi()
        }
    }

    private fun loadProduk() {
        binding.btnSave.isEnabled = false

        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatProdukAktif() }
                .onSuccess { semuaProduk ->
                    daftarProdukDasar = semuaProduk
                        .filter { it.category.equals("DASAR", ignoreCase = true) }
                        .sortedBy { it.name.lowercase() }

                    daftarProdukOlahan = semuaProduk
                        .filter { it.category.equals("OLAHAN", ignoreCase = true) }
                        .sortedBy { it.name.lowercase() }

                    renderSpinner()
                }
                .onFailure {
                    showMessage(it.message ?: "Gagal memuat data produk")
                }

            binding.btnSave.isEnabled =
                daftarProdukDasar.isNotEmpty() && daftarProdukOlahan.isNotEmpty()
        }
    }

    private fun renderSpinner() {
        if (daftarProdukDasar.isEmpty()) {
            binding.spFromProduct.adapter = AdapterSpinner.stringAdapter(
                this,
                listOf("Belum ada produk dasar")
            )
        } else {
            binding.spFromProduct.adapter = AdapterSpinner.stringAdapter(
                this,
                daftarProdukDasar.map { "${it.name} • stok ${it.stock} ${it.unit}" }
            )
        }

        if (daftarProdukOlahan.isEmpty()) {
            binding.spToProduct.adapter = AdapterSpinner.stringAdapter(
                this,
                listOf("Belum ada produk olahan")
            )
        } else {
            binding.spToProduct.adapter = AdapterSpinner.stringAdapter(
                this,
                daftarProdukOlahan.map { "${it.name} • stok ${it.stock} ${it.unit}" }
            )
        }

        if (daftarProdukDasar.isEmpty()) {
            showMessage("Produk dasar belum tersedia.")
        }

        if (daftarProdukOlahan.isEmpty()) {
            showMessage("Produk olahan belum tersedia.")
        }
    }

    private fun selectedProdukDasar(): Produk? {
        return daftarProdukDasar.getOrNull(binding.spFromProduct.selectedItemPosition)
    }

    private fun selectedProdukOlahan(): Produk? {
        return daftarProdukOlahan.getOrNull(binding.spToProduct.selectedItemPosition)
    }

    private fun simpanKonversi() {
        val produkAsal = selectedProdukDasar()
        val produkHasil = selectedProdukOlahan()

        if (produkAsal == null) {
            showMessage("Produk dasar belum dipilih.")
            return
        }

        if (produkHasil == null) {
            showMessage("Produk olahan belum dipilih.")
            return
        }

        if (produkAsal.id == produkHasil.id) {
            showMessage("Produk asal dan hasil tidak boleh sama.")
            return
        }

        val tanggal = binding.etDate.text?.toString().orEmpty()
        val waktu = binding.etTime.text?.toString().orEmpty()
        val qtyBahan = binding.etInputQty.text?.toString()?.trim()?.toIntOrNull() ?: 0
        val qtyHasil = binding.etOutputQty.text?.toString()?.trim()?.toIntOrNull() ?: 0
        val catatan = binding.etNote.text?.toString()?.trim().orEmpty()

        if (tanggal.isBlank()) {
            showMessage("Tanggal wajib diisi.")
            return
        }

        if (waktu.isBlank()) {
            showMessage("Waktu wajib diisi.")
            return
        }

        if (qtyBahan <= 0) {
            showMessage("Jumlah bahan harus lebih dari 0.")
            return
        }

        if (qtyHasil <= 0) {
            showMessage("Jumlah hasil harus lebih dari 0.")
            return
        }

        val dateTime = Formatter.isoDate(tanggal, "$waktu:00")

        lifecycleScope.launch {
            binding.btnSave.isEnabled = false
            binding.btnSave.text = "Menyimpan..."

            runCatching {
                RepositoriFirebaseUtama.simpanKonversi(
                    dateTime = dateTime,
                    fromProductId = produkAsal.id,
                    toProductId = produkHasil.id,
                    inputQty = qtyBahan,
                    outputQty = qtyHasil,
                    note = catatan,
                    userAuthId = currentUserId()
                )
            }.onSuccess {
                setResult(RESULT_OK)
                showMessage(
                    "Produk olahan berhasil disimpan. ${produkAsal.name} berkurang $qtyBahan ${produkAsal.unit}, ${produkHasil.name} bertambah $qtyHasil ${produkHasil.unit}."
                )
                finish()
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan produk olahan")
            }

            binding.btnSave.isEnabled = true
            binding.btnSave.text = "Simpan Produk Olahan"
        }
    }
}