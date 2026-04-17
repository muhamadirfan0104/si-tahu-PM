package muhamad.irfan.si_tahu.ui.produksi

import android.os.Bundle
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.databinding.ActivityConversionBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.PembantuPilihTanggalWaktu

class AktivitasKonversiProduk : AktivitasDasar() {

    private lateinit var binding: ActivityConversionBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var daftarProdukDasar: List<ProdukKonversiItem> = emptyList()
    private var daftarProdukOlahan: List<ProdukKonversiItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindToolbar(
            binding.toolbar,
            "Produksi Produk Olahan",
            "Kurangi stok bahan, tambah stok hasil olahan"
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

        loadProduk()
    }

    private fun loadProduk() {
        binding.btnSave.isEnabled = false

        firestore.collection("produk")
            .whereEqualTo("dihapus", false)
            .whereEqualTo("aktifDijual", true)
            .get()
            .addOnSuccessListener { snapshot ->
                val semuaProduk = snapshot.documents.map { doc ->
                    ProdukKonversiItem(
                        id = doc.id,
                        namaProduk = doc.getString("namaProduk").orEmpty(),
                        jenisProduk = doc.getString("jenisProduk").orEmpty(),
                        satuan = doc.getString("satuan").orEmpty(),
                        stokSaatIni = doc.getLong("stokSaatIni") ?: 0L,
                        stokMinimum = doc.getLong("stokMinimum") ?: 0L
                    )
                }

                daftarProdukDasar = semuaProduk
                    .filter { it.jenisProduk.equals("DASAR", ignoreCase = true) }
                    .sortedBy { it.namaProduk.lowercase() }

                daftarProdukOlahan = semuaProduk
                    .filter { it.jenisProduk.equals("OLAHAN", ignoreCase = true) }
                    .sortedBy { it.namaProduk.lowercase() }

                if (daftarProdukDasar.isEmpty()) {
                    binding.spFromProduct.adapter = AdapterSpinner.stringAdapter(
                        this,
                        listOf("Belum ada produk dasar")
                    )
                    binding.spToProduct.adapter = AdapterSpinner.stringAdapter(
                        this,
                        listOf("Belum ada produk olahan")
                    )
                    showMessage("Produk dasar belum tersedia.")
                    return@addOnSuccessListener
                }

                if (daftarProdukOlahan.isEmpty()) {
                    binding.spFromProduct.adapter = AdapterSpinner.stringAdapter(
                        this,
                        daftarProdukDasar.map { "${it.namaProduk} • stok ${it.stokSaatIni} ${it.satuan}" }
                    )
                    binding.spToProduct.adapter = AdapterSpinner.stringAdapter(
                        this,
                        listOf("Belum ada produk olahan")
                    )
                    showMessage("Produk olahan belum tersedia.")
                    return@addOnSuccessListener
                }

                binding.spFromProduct.adapter = AdapterSpinner.stringAdapter(
                    this,
                    daftarProdukDasar.map { "${it.namaProduk} • stok ${it.stokSaatIni} ${it.satuan}" }
                )

                binding.spToProduct.adapter = AdapterSpinner.stringAdapter(
                    this,
                    daftarProdukOlahan.map { "${it.namaProduk} • stok ${it.stokSaatIni} ${it.satuan}" }
                )

                binding.btnSave.isEnabled = true
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat data produk: ${e.message}")
            }
    }

    private fun selectedProdukDasar(): ProdukKonversiItem? {
        return daftarProdukDasar.getOrNull(binding.spFromProduct.selectedItemPosition)
    }

    private fun selectedProdukOlahan(): ProdukKonversiItem? {
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
        val qtyBahan = binding.etInputQty.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val qtyHasil = binding.etOutputQty.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val catatan = binding.etNote.text?.toString()?.trim().orEmpty()

        if (tanggal.isBlank()) {
            showMessage("Tanggal wajib diisi.")
            return
        }

        if (waktu.isBlank()) {
            showMessage("Waktu wajib diisi.")
            return
        }

        if (qtyBahan <= 0L) {
            showMessage("Jumlah bahan harus lebih dari 0.")
            return
        }

        if (qtyHasil <= 0L) {
            showMessage("Jumlah hasil harus lebih dari 0.")
            return
        }

        binding.btnSave.isEnabled = false
        binding.btnSave.text = "Menyimpan..."

        val now = Timestamp.now()
        val konversiRef = firestore.collection("konversiProduk").document()
        val produkAsalRef = firestore.collection("produk").document(produkAsal.id)
        val produkHasilRef = firestore.collection("produk").document(produkHasil.id)

        firestore.runTransaction { transaction ->
            val asalSnapshot = transaction.get(produkAsalRef)
            val hasilSnapshot = transaction.get(produkHasilRef)

            val stokAsalSaatIni = asalSnapshot.getLong("stokSaatIni") ?: 0L
            val stokHasilSaatIni = hasilSnapshot.getLong("stokSaatIni") ?: 0L

            if (stokAsalSaatIni < qtyBahan) {
                throw IllegalStateException(
                    "Stok ${produkAsal.namaProduk} tidak cukup. Tersedia $stokAsalSaatIni ${produkAsal.satuan}."
                )
            }

            val stokAsalBaru = stokAsalSaatIni - qtyBahan
            val stokHasilBaru = stokHasilSaatIni + qtyHasil

            transaction.set(
                konversiRef,
                hashMapOf<String, Any>(
                    "idProdukAsal" to produkAsal.id,
                    "namaProdukAsal" to produkAsal.namaProduk,
                    "qtyBahan" to qtyBahan,
                    "satuanBahan" to produkAsal.satuan,
                    "idProdukHasil" to produkHasil.id,
                    "namaProdukHasil" to produkHasil.namaProduk,
                    "qtyHasil" to qtyHasil,
                    "satuanHasil" to produkHasil.satuan,
                    "tanggalKonversi" to tanggal,
                    "waktuKonversi" to waktu,
                    "tanggalJamKonversiIso" to Formatter.isoDate(tanggal, "$waktu:00"),
                    "catatan" to catatan,
                    "dibuatOleh" to currentUserId(),
                    "dibuatPada" to now,
                    "diperbaruiPada" to now
                )
            )

            transaction.update(
                produkAsalRef,
                mapOf(
                    "stokSaatIni" to stokAsalBaru,
                    "diperbaruiPada" to now
                )
            )

            transaction.update(
                produkHasilRef,
                mapOf(
                    "stokSaatIni" to stokHasilBaru,
                    "diperbaruiPada" to now
                )
            )

            val aktivitasKeluarRef = firestore.collection("aktivitasStok").document()
            transaction.set(
                aktivitasKeluarRef,
                mapOf(
                    "idProduk" to produkAsal.id,
                    "namaProduk" to produkAsal.namaProduk,
                    "jenisAktivitas" to "KONVERSI_KELUAR",
                    "qty" to qtyBahan,
                    "satuan" to produkAsal.satuan,
                    "referensiId" to konversiRef.id,
                    "catatan" to if (catatan.isBlank()) {
                        "Bahan untuk ${produkHasil.namaProduk}"
                    } else {
                        catatan
                    },
                    "dibuatPada" to now
                )
            )

            val aktivitasMasukRef = firestore.collection("aktivitasStok").document()
            transaction.set(
                aktivitasMasukRef,
                mapOf(
                    "idProduk" to produkHasil.id,
                    "namaProduk" to produkHasil.namaProduk,
                    "jenisAktivitas" to "KONVERSI_MASUK",
                    "qty" to qtyHasil,
                    "satuan" to produkHasil.satuan,
                    "referensiId" to konversiRef.id,
                    "catatan" to if (catatan.isBlank()) {
                        "Hasil olahan dari ${produkAsal.namaProduk}"
                    } else {
                        catatan
                    },
                    "dibuatPada" to now
                )
            )

            null
        }.addOnSuccessListener {
            binding.btnSave.isEnabled = true
            binding.btnSave.text = "Simpan Konversi"
            showMessage(
                "Konversi berhasil. ${produkAsal.namaProduk} berkurang $qtyBahan ${produkAsal.satuan}, ${produkHasil.namaProduk} bertambah $qtyHasil ${produkHasil.satuan}."
            )
            finish()
        }.addOnFailureListener { e ->
            binding.btnSave.isEnabled = true
            binding.btnSave.text = "Simpan Konversi"
            showMessage("Gagal menyimpan konversi: ${e.message}")
        }
    }
}

data class ProdukKonversiItem(
    val id: String,
    val namaProduk: String,
    val jenisProduk: String,
    val satuan: String,
    val stokSaatIni: Long,
    val stokMinimum: Long
)