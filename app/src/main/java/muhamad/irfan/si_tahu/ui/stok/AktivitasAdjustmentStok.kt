package muhamad.irfan.si_tahu.ui.stok

import android.os.Bundle
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.databinding.ActivityStockAdjustmentBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.Formatter

class AktivitasAdjustmentStok : AktivitasDasar() {

    private lateinit var binding: ActivityStockAdjustmentBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val tipeAdjustment = listOf("TAMBAH", "KURANG")
    private var daftarProduk: List<ProdukAdjustmentItem> = emptyList()
    private var preselectedProductId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockAdjustmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindToolbar(
            binding.toolbar,
            "Stock Adjustment",
            "Sesuaikan stok fisik dengan stok sistem"
        )

        binding.etDate.setText(Formatter.currentDateOnly())
        binding.spType.adapter = AdapterSpinner.stringAdapter(this, tipeAdjustment)

        preselectedProductId =
            intent.getStringExtra(AktivitasMonitoringStok.EXTRA_PRODUCT_ID)

        binding.btnSave.setOnClickListener {
            simpanAdjustment()
        }

        loadProduk()
    }

    private fun loadProduk() {
        firestore.collection("produk")
            .whereEqualTo("dihapus", false)
            .get()
            .addOnSuccessListener { snapshot ->
                daftarProduk = snapshot.documents.map { doc ->
                    ProdukAdjustmentItem(
                        id = doc.id,
                        namaProduk = doc.getString("namaProduk").orEmpty(),
                        satuan = doc.getString("satuan").orEmpty(),
                        stokSaatIni = doc.getLong("stokSaatIni") ?: 0L
                    )
                }.sortedBy { it.namaProduk.lowercase() }

                if (daftarProduk.isEmpty()) {
                    binding.spProduct.adapter = AdapterSpinner.stringAdapter(
                        this,
                        listOf("Belum ada produk")
                    )
                    showMessage("Data produk belum ada.")
                    return@addOnSuccessListener
                }

                binding.spProduct.adapter = AdapterSpinner.stringAdapter(
                    this,
                    daftarProduk.map { "${it.namaProduk} • stok ${it.stokSaatIni} ${it.satuan}" }
                )

                val selectedIndex = preselectedProductId?.let { id ->
                    daftarProduk.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: 0
                } ?: 0

                binding.spProduct.setSelection(selectedIndex)
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat produk: ${e.message}")
            }
    }

    private fun selectedProduk(): ProdukAdjustmentItem? {
        return daftarProduk.getOrNull(binding.spProduct.selectedItemPosition)
    }

    private fun simpanAdjustment() {
        val produk = selectedProduk()
        if (produk == null) {
            showMessage("Produk belum dipilih.")
            return
        }

        val tanggal = binding.etDate.text?.toString().orEmpty()
        val tipe = tipeAdjustment.getOrNull(binding.spType.selectedItemPosition).orEmpty()
        val qty = binding.etQty.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val catatan = binding.etNote.text?.toString()?.trim().orEmpty()

        if (tanggal.isBlank()) {
            showMessage("Tanggal wajib diisi.")
            return
        }

        if (qty <= 0L) {
            showMessage("Jumlah adjustment harus lebih dari 0.")
            return
        }

        if (catatan.isBlank()) {
            showMessage("Alasan adjustment wajib diisi.")
            return
        }

        binding.btnSave.isEnabled = false
        binding.btnSave.text = "Menyimpan..."

        val now = Timestamp.now()
        val produkRef = firestore.collection("produk").document(produk.id)
        val adjustmentRef = firestore.collection("stockAdjustment").document()

        firestore.runTransaction { transaction ->
            val produkSnapshot = transaction.get(produkRef)
            val stokSaatIni = produkSnapshot.getLong("stokSaatIni") ?: 0L

            val stokBaru = when (tipe) {
                "TAMBAH" -> stokSaatIni + qty
                else -> {
                    if (stokSaatIni < qty) {
                        throw IllegalStateException(
                            "Stok ${produk.namaProduk} tidak cukup untuk dikurangi. Tersedia $stokSaatIni ${produk.satuan}."
                        )
                    }
                    stokSaatIni - qty
                }
            }

            transaction.set(
                adjustmentRef,
                hashMapOf<String, Any>(
                    "idProduk" to produk.id,
                    "namaProduk" to produk.namaProduk,
                    "tipeAdjustment" to tipe,
                    "qty" to qty,
                    "satuan" to produk.satuan,
                    "stokSebelum" to stokSaatIni,
                    "stokSesudah" to stokBaru,
                    "tanggalAdjustment" to tanggal,
                    "catatan" to catatan,
                    "dibuatOleh" to currentUserId(),
                    "dibuatPada" to now,
                    "diperbaruiPada" to now
                )
            )

            transaction.update(
                produkRef,
                mapOf(
                    "stokSaatIni" to stokBaru,
                    "diperbaruiPada" to now
                )
            )

            val aktivitasRef = firestore.collection("aktivitasStok").document()
            transaction.set(
                aktivitasRef,
                mapOf(
                    "idProduk" to produk.id,
                    "namaProduk" to produk.namaProduk,
                    "jenisAktivitas" to if (tipe == "TAMBAH") {
                        "ADJUSTMENT_TAMBAH"
                    } else {
                        "ADJUSTMENT_KURANG"
                    },
                    "qty" to qty,
                    "satuan" to produk.satuan,
                    "referensiId" to adjustmentRef.id,
                    "catatan" to catatan,
                    "dibuatPada" to now
                )
            )

            null
        }.addOnSuccessListener {
            binding.btnSave.isEnabled = true
            binding.btnSave.text = "Simpan Adjustment"
            showMessage("Stock adjustment berhasil disimpan.")
            finish()
        }.addOnFailureListener { e ->
            binding.btnSave.isEnabled = true
            binding.btnSave.text = "Simpan Adjustment"
            showMessage("Gagal menyimpan adjustment: ${e.message}")
        }
    }
}

data class ProdukAdjustmentItem(
    val id: String,
    val namaProduk: String,
    val satuan: String,
    val stokSaatIni: Long
)