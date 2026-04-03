package muhamad.irfan.si_tahu.ui.production

import android.content.Intent
import android.os.Bundle
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.databinding.ActivityBasicProductionBinding
import muhamad.irfan.si_tahu.ui.base.AktivitasDasar
import muhamad.irfan.si_tahu.ui.main.AktivitasUtamaAdmin
import muhamad.irfan.si_tahu.ui.main.PendengarPilihItemSederhana
import muhamad.irfan.si_tahu.util.PembantuPilihTanggalWaktu
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.AdapterSpinner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AktivitasProduksiDasar : AktivitasDasar() {
    private lateinit var binding: ActivityBasicProductionBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var products: List<OpsiProdukDasar> = emptyList()
    private var activeParameter: ParameterProduksi? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBasicProductionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Produksi Dasar", "Input produksi untuk produk dasar")

        binding.etDate.setText(Formatter.currentDateOnly())
        binding.etTime.setText(Formatter.currentTimeOnly())

        binding.etDate.setOnClickListener {
            PembantuPilihTanggalWaktu.showDatePicker(this, binding.etDate.text?.toString()) { selected ->
                binding.etDate.setText(selected)
            }
        }

        binding.etTime.setOnClickListener {
            PembantuPilihTanggalWaktu.showTimePicker(this, currentDateTimeText()) { selected ->
                binding.etTime.setText(selected)
            }
        }

        binding.spProduct.onItemSelectedListener = PendengarPilihItemSederhana {
            refreshParameterInfo()
        }

        binding.btnSave.setOnClickListener {
            saveProduction()
        }

        loadBasicProducts()
    }

    private fun loadBasicProducts() {
        firestore.collection("produk")
            .whereEqualTo("jenisProduk", "DASAR")
            .get()
            .addOnSuccessListener { snapshot ->
                products = snapshot.documents.map { doc ->
                    OpsiProdukDasar(
                        id = doc.id,
                        namaProduk = doc.getString("namaProduk").orEmpty(),
                        satuan = doc.getString("satuan").orEmpty()
                    )
                }.sortedBy { it.namaProduk }

                if (products.isEmpty()) {
                    binding.spProduct.adapter =
                        AdapterSpinner.stringAdapter(this, listOf("Belum ada produk dasar"))
                    binding.tvParameterInfo.text = "Belum ada produk dasar di Firebase."
                    return@addOnSuccessListener
                }

                binding.spProduct.adapter =
                    AdapterSpinner.stringAdapter(this, products.map { it.namaProduk })

                refreshParameterInfo()
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat produk dasar: ${e.message}")
            }
    }

    private fun refreshParameterInfo() {
        val product = selectedProduct()
        if (product == null) {
            activeParameter = null
            binding.tvParameterInfo.text = "Produk dasar belum tersedia."
            return
        }

        firestore.collection("parameterProduksi")
            .whereEqualTo("idProduk", product.id)
            .whereEqualTo("aktif", true)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val doc = snapshot.documents.firstOrNull()
                activeParameter = if (doc != null) {
                    ParameterProduksi(
                        id = doc.id,
                        hasilPerProduksi = doc.getLong("hasilPerProduksi") ?: 0L,
                        catatan = doc.getString("catatan").orEmpty()
                    )
                } else {
                    null
                }

                binding.tvParameterInfo.text = if (activeParameter == null) {
                    "Belum ada parameter aktif untuk ${product.namaProduk}."
                } else {
                    "${product.namaProduk}: ${activeParameter!!.hasilPerProduksi} ${product.satuan} per produksi. ${activeParameter!!.catatan}"
                }
            }
            .addOnFailureListener { e ->
                activeParameter = null
                binding.tvParameterInfo.text = "Gagal memuat parameter: ${e.message}"
            }
    }

    private fun saveProduction() {
        val product = selectedProduct()
        if (product == null) {
            showMessage("Produk dasar belum tersedia.")
            return
        }

        val parameter = activeParameter
        if (parameter == null) {
            showMessage("Belum ada parameter aktif untuk produk ini.")
            return
        }

        val batches = binding.etBatches.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        if (batches <= 0L) {
            binding.etBatches.error = "Jumlah produksi harus lebih dari 0"
            binding.etBatches.requestFocus()
            return
        }

        val tanggalProduksi = parseDateTime(
            binding.etDate.text?.toString().orEmpty(),
            binding.etTime.text?.toString().orEmpty()
        )
        if (tanggalProduksi == null) {
            showMessage("Format tanggal/jam tidak valid.")
            return
        }

        val jumlahHasil = parameter.hasilPerProduksi * batches
        val note = binding.etNote.text?.toString()?.trim().orEmpty()
        val uid = currentUserId()

        firestore.collection("pengguna")
            .document(uid)
            .get()
            .addOnSuccessListener { userDoc ->
                val namaUser = userDoc.getString("namaPengguna").orEmpty()
                val logRef = firestore.collection("catatanProduksi").document()
                val productRef = firestore.collection("produk").document(product.id)

                firestore.runTransaction { trx ->
                    val productSnap = trx.get(productRef)
                    val stokSaatIni = productSnap.getLong("stokSaatIni") ?: 0L

                    trx.update(productRef, "stokSaatIni", stokSaatIni + jumlahHasil)

                    trx.set(
                        logRef,
                        hashMapOf(
                            "jenisProduksi" to "DASAR",
                            "tanggalProduksi" to Timestamp(tanggalProduksi),
                            "kunciTanggal" to binding.etDate.text?.toString().orEmpty(),
                            "idParameterProduksi" to parameter.id,
                            "idProdukAsal" to "",
                            "namaProdukAsal" to "",
                            "jumlahBahan" to 0L,
                            "satuanBahan" to "",
                            "idProdukHasil" to product.id,
                            "namaProdukHasil" to product.namaProduk,
                            "jumlahHasil" to jumlahHasil,
                            "satuanHasil" to product.satuan,
                            "catatan" to note,
                            "dibuatOlehId" to uid,
                            "dibuatOlehNama" to namaUser,
                            "dibuatPada" to Timestamp.now()
                        )
                    )
                }.addOnSuccessListener {
                    showMessage("Produksi berhasil disimpan.")
                    startActivity(AktivitasUtamaAdmin.intent(this, R.id.nav_admin_production))
                    finish()
                }.addOnFailureListener { e ->
                    showMessage("Gagal menyimpan produksi: ${e.message}")
                }
            }
            .addOnFailureListener { e ->
                showMessage("Gagal membaca pengguna: ${e.message}")
            }
    }

    private fun selectedProduct(): OpsiProdukDasar? {
        return products.getOrNull(binding.spProduct.selectedItemPosition)
    }

    private fun currentDateTimeText(): String {
        return binding.etDate.text?.toString().orEmpty() + " " +
                binding.etTime.text?.toString().orEmpty()
    }

    private fun parseDateTime(date: String, time: String): Date? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            sdf.isLenient = false
            sdf.parse("$date $time")
        } catch (_: Exception) {
            null
        }
    }
}

private data class OpsiProdukDasar(
    val id: String,
    val namaProduk: String,
    val satuan: String
)

private data class ParameterProduksi(
    val id: String,
    val hasilPerProduksi: Long,
    val catatan: String
)