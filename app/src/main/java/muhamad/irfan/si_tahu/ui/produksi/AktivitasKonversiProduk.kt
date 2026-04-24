package muhamad.irfan.si_tahu.ui.produksi

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.databinding.ActivityConversionBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.InputAngka
import muhamad.irfan.si_tahu.util.PembantuPilihTanggalWaktu
import muhamad.irfan.si_tahu.utilitas.PembantuPilihProduk
import muhamad.irfan.si_tahu.utilitas.ProdukPilihanUi

class AktivitasKonversiProduk : AktivitasDasar() {

    private lateinit var binding: ActivityConversionBinding

    private var daftarProdukDasar: List<Produk> = emptyList()
    private var daftarProdukOlahan: List<Produk> = emptyList()
    private var selectedProdukDasarId: String? = null
    private var selectedProdukOlahanId: String? = null

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

        binding.cardFromProductPicker.setOnClickListener {
            showPickerProdukDasar()
        }

        binding.cardToProductPicker.setOnClickListener {
            showPickerProdukOlahan()
        }

        InputAngka.pasang(binding.etInputQty)
        InputAngka.pasang(binding.etOutputQty)

        binding.btnSave.setOnClickListener {
            simpanKonversi()
        }
    }

    private fun loadProduk() {
        binding.btnSave.isEnabled = false
        binding.tvConversionInfo.text = "Memuat produk bahan dan hasil olahan..."
        updateProdukAsalSelector()
        updateProdukHasilSelector()

        lifecycleScope.launch {
            runCatching { RepositoriFirebaseUtama.muatProdukAktif() }
                .onSuccess { semuaProduk ->
                    daftarProdukDasar = semuaProduk
                        .filter { it.category.equals("DASAR", ignoreCase = true) }
                        .sortedBy { it.name.lowercase() }

                    daftarProdukOlahan = semuaProduk
                        .filter { it.category.equals("OLAHAN", ignoreCase = true) }
                        .sortedBy { it.name.lowercase() }

                    selectedProdukDasarId = daftarProdukDasar.firstOrNull { it.id == selectedProdukDasarId }?.id
                        ?: daftarProdukDasar.firstOrNull()?.id
                    selectedProdukOlahanId = daftarProdukOlahan.firstOrNull { it.id == selectedProdukOlahanId }?.id
                        ?: daftarProdukOlahan.firstOrNull()?.id

                    updateProdukAsalSelector()
                    updateProdukHasilSelector()
                    updateRingkasanKonversi()
                }
                .onFailure {
                    daftarProdukDasar = emptyList()
                    daftarProdukOlahan = emptyList()
                    selectedProdukDasarId = null
                    selectedProdukOlahanId = null
                    updateProdukAsalSelector()
                    updateProdukHasilSelector()
                    updateRingkasanKonversi()
                    showMessage(it.message ?: "Gagal memuat data produk")
                }

            binding.btnSave.isEnabled =
                daftarProdukDasar.isNotEmpty() && daftarProdukOlahan.isNotEmpty()
        }
    }

    private fun selectedProdukDasar(): Produk? {
        return daftarProdukDasar.firstOrNull { it.id == selectedProdukDasarId }
    }

    private fun selectedProdukOlahan(): Produk? {
        return daftarProdukOlahan.firstOrNull { it.id == selectedProdukOlahanId }
    }

    private fun updateProdukAsalSelector() {
        val produk = selectedProdukDasar()
        binding.tvFromProductPickerLabel.text = "Bahan dipilih"
        binding.tvSelectedFromProductName.text = when {
            daftarProdukDasar.isEmpty() -> "Belum ada produk dasar"
            produk == null -> "Pilih produk bahan"
            else -> produk.name
        }
        binding.tvSelectedFromProductMeta.text = when {
            daftarProdukDasar.isEmpty() -> "Tambahkan produk dasar aktif terlebih dahulu"
            produk == null -> "Cari dan pilih produk dasar yang akan dipakai sebagai bahan"
            else -> formatMetaProduk(produk)
        }
        binding.tvFromProductLeading.text = produk?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "B"
        binding.cardFromProductPicker.isEnabled = daftarProdukDasar.isNotEmpty()
        binding.cardFromProductPicker.alpha = if (daftarProdukDasar.isNotEmpty()) 1f else 0.7f
    }

    private fun updateProdukHasilSelector() {
        val produk = selectedProdukOlahan()
        binding.tvToProductPickerLabel.text = "Hasil dipilih"
        binding.tvSelectedToProductName.text = when {
            daftarProdukOlahan.isEmpty() -> "Belum ada produk olahan"
            produk == null -> "Pilih produk hasil"
            else -> produk.name
        }
        binding.tvSelectedToProductMeta.text = when {
            daftarProdukOlahan.isEmpty() -> "Tambahkan produk olahan aktif terlebih dahulu"
            produk == null -> "Cari dan pilih produk olahan yang menjadi hasil produksi"
            else -> formatMetaProduk(produk)
        }
        binding.tvToProductLeading.text = produk?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "H"
        binding.cardToProductPicker.isEnabled = daftarProdukOlahan.isNotEmpty()
        binding.cardToProductPicker.alpha = if (daftarProdukOlahan.isNotEmpty()) 1f else 0.7f
    }

    private fun updateRingkasanKonversi() {
        val produkAsal = selectedProdukDasar()
        val produkHasil = selectedProdukOlahan()

        binding.tvConversionInfo.text = when {
            daftarProdukDasar.isEmpty() && daftarProdukOlahan.isEmpty() -> {
                "Produk dasar dan produk olahan belum tersedia. Tambahkan data produk terlebih dahulu."
            }
            daftarProdukDasar.isEmpty() -> {
                "Produk dasar belum tersedia. Tambahkan produk kategori DASAR untuk dijadikan bahan produksi."
            }
            daftarProdukOlahan.isEmpty() -> {
                "Produk olahan belum tersedia. Tambahkan produk kategori OLAHAN untuk dijadikan hasil produksi."
            }
            produkAsal == null || produkHasil == null -> {
                "Pilih produk bahan dan produk hasil agar proses produksi olahan lebih jelas dan konsisten."
            }
            else -> {
                "Bahan: ${produkAsal.name} • stok ${Formatter.ribuan(produkAsal.stock.toLong())} ${produkAsal.unit}\nHasil: ${produkHasil.name} • stok ${Formatter.ribuan(produkHasil.stock.toLong())} ${produkHasil.unit}"
            }
        }
    }

    private fun showPickerProdukDasar() {
        if (daftarProdukDasar.isEmpty()) return

        PembantuPilihProduk.show(
            activity = this,
            title = "Pilih Produk Bahan",
            produk = daftarProdukDasar.map { produk ->
                ProdukPilihanUi(
                    id = produk.id,
                    namaProduk = produk.name,
                    jenisProduk = produk.category,
                    stokSaatIni = produk.stock.toLong(),
                    satuan = produk.unit,
                    aktifDijual = produk.active,
                    infoTambahan = "Dipakai sebagai bahan"
                )
            },
            selectedId = selectedProdukDasarId,
            kategoriOptions = listOf("Semua", "Dasar")
        ) { selected ->
            selectedProdukDasarId = selected.id
            updateProdukAsalSelector()
            updateRingkasanKonversi()
        }
    }

    private fun showPickerProdukOlahan() {
        if (daftarProdukOlahan.isEmpty()) return

        PembantuPilihProduk.show(
            activity = this,
            title = "Pilih Produk Hasil",
            produk = daftarProdukOlahan.map { produk ->
                ProdukPilihanUi(
                    id = produk.id,
                    namaProduk = produk.name,
                    jenisProduk = produk.category,
                    stokSaatIni = produk.stock.toLong(),
                    satuan = produk.unit,
                    aktifDijual = produk.active,
                    infoTambahan = "Menjadi hasil produksi"
                )
            },
            selectedId = selectedProdukOlahanId,
            kategoriOptions = listOf("Semua", "Olahan")
        ) { selected ->
            selectedProdukOlahanId = selected.id
            updateProdukHasilSelector()
            updateRingkasanKonversi()
        }
    }

    private fun formatMetaProduk(produk: Produk): String {
        return listOf(
            produk.category.ifBlank { "Produk" },
            if (produk.active) "Aktif" else "Nonaktif",
            "Stok ${Formatter.ribuan(produk.stock.toLong())} ${produk.unit}"
        ).joinToString(" • ")
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
        val qtyBahan = InputAngka.ambilInt(binding.etInputQty)
        val qtyHasil = InputAngka.ambilInt(binding.etOutputQty)
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
                    "Produk olahan berhasil disimpan. ${produkAsal.name} berkurang ${Formatter.ribuan(qtyBahan.toLong())} ${produkAsal.unit}, ${produkHasil.name} bertambah ${Formatter.ribuan(qtyHasil.toLong())} ${produkHasil.unit}."
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
