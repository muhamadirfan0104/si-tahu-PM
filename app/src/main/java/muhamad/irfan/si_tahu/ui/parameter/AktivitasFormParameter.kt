package muhamad.irfan.si_tahu.ui.parameter

import android.content.Intent
import android.os.Bundle
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import muhamad.irfan.si_tahu.databinding.ActivityParameterFormBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.EkstraAplikasi

class AktivitasFormParameter : AktivitasDasar() {

    private lateinit var binding: ActivityParameterFormBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var products: List<OpsiProdukDasar> = emptyList()
    private var editingParameterId: String? = null
    private var requestedProductId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParameterFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Form Parameter", "Tambah atau edit parameter produksi")

        editingParameterId = intent.getStringExtra(EkstraAplikasi.EXTRA_PARAMETER_ID)
        requestedProductId = intent.getStringExtra(EkstraAplikasi.EXTRA_PRODUCT_ID)

        binding.cbActive.isChecked = true

        if (!editingParameterId.isNullOrBlank()) {
            lockProductFieldForEdit()
        }

        binding.btnSave.setOnClickListener {
            saveParameter()
        }

        loadBasicProducts()
    }

    private fun lockProductFieldForEdit() {
        binding.spProduct.isEnabled = false
        binding.spProduct.isClickable = false
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
                }.sortedBy { it.namaProduk.lowercase() }

                if (products.isEmpty()) {
                    binding.spProduct.adapter =
                        AdapterSpinner.stringAdapter(this, listOf("Belum ada produk dasar"))
                    showMessage("Belum ada produk dasar. Tambahkan produk DASAR dulu.")
                    return@addOnSuccessListener
                }

                binding.spProduct.adapter =
                    AdapterSpinner.stringAdapter(this, products.map { it.namaProduk })

                val initialIndex = requestedProductId?.let { targetId ->
                    products.indexOfFirst { it.id == targetId }.takeIf { it >= 0 } ?: 0
                } ?: 0
                binding.spProduct.setSelection(initialIndex)

                if (!editingParameterId.isNullOrBlank()) {
                    loadEditingParameter(editingParameterId!!)
                }
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat produk dasar: ${e.message}")
            }
    }

    private fun loadEditingParameter(parameterId: String) {
        firestore.collection("parameterProduksi")
            .document(parameterId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    showMessage("Data parameter tidak ditemukan.")
                    return@addOnSuccessListener
                }

                if (doc.getBoolean("dihapus") == true) {
                    showMessage("Parameter ini sudah dihapus.")
                    finish()
                    return@addOnSuccessListener
                }

                val idProduk = doc.getString("idProduk").orEmpty()
                val productIndex = products.indexOfFirst { it.id == idProduk }
                    .takeIf { it >= 0 } ?: 0
                binding.spProduct.setSelection(productIndex)

                binding.etResultPerBatch.setText(
                    (doc.getLong("hasilPerProduksi") ?: 0L).toString()
                )
                binding.etNote.setText(doc.getString("catatan").orEmpty())
                binding.cbActive.isChecked = doc.getBoolean("aktif") ?: true
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat parameter: ${e.message}")
            }
    }

    private fun saveParameter() {
        val product = products.getOrNull(binding.spProduct.selectedItemPosition)
        if (product == null) {
            showMessage("Produk dasar belum tersedia.")
            return
        }

        val hasilPerProduksi = binding.etResultPerBatch.text
            ?.toString()
            ?.trim()
            ?.toLongOrNull() ?: 0L
        val catatan = binding.etNote.text?.toString()?.trim().orEmpty()
        val aktif = binding.cbActive.isChecked

        if (hasilPerProduksi <= 0L) {
            binding.etResultPerBatch.error = "Hasil produksi harus lebih dari 0"
            binding.etResultPerBatch.requestFocus()
            return
        }

        if (editingParameterId.isNullOrBlank()) {
            generateNextParameterId(
                onResult = { newId ->
                    persistParameter(
                        parameterId = newId,
                        product = product,
                        hasilPerProduksi = hasilPerProduksi,
                        catatan = catatan,
                        aktif = aktif,
                        isNew = true
                    )
                },
                onError = { e ->
                    showMessage("Gagal membuat ID parameter: ${e.message}")
                }
            )
        } else {
            persistParameter(
                parameterId = editingParameterId!!,
                product = product,
                hasilPerProduksi = hasilPerProduksi,
                catatan = catatan,
                aktif = aktif,
                isNew = false
            )
        }
    }

    private fun persistParameter(
        parameterId: String,
        product: OpsiProdukDasar,
        hasilPerProduksi: Long,
        catatan: String,
        aktif: Boolean,
        isNew: Boolean
    ) {
        val now = Timestamp.now()

        val data = hashMapOf<String, Any?>(
            "idProduk" to product.id,
            "namaProduk" to product.namaProduk,
            "hasilPerProduksi" to hasilPerProduksi,
            "satuanHasil" to product.satuan,
            "aktif" to aktif,
            "catatan" to catatan,
            "dihapus" to false,
            "dihapusPada" to null,
            "diperbaruiPada" to now
        )

        if (isNew) {
            data["dibuatPada"] = now
        }

        firestore.collection("parameterProduksi")
            .document(parameterId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                if (aktif) {
                    deactivateOtherParameters(
                        productId = product.id,
                        currentParameterId = parameterId
                    )
                } else {
                    goBackToList(product.id)
                }
            }
            .addOnFailureListener { e ->
                showMessage("Gagal menyimpan parameter: ${e.message}")
            }
    }

    private fun deactivateOtherParameters(
        productId: String,
        currentParameterId: String
    ) {
        firestore.collection("parameterProduksi")
            .whereEqualTo("idProduk", productId)
            .whereEqualTo("dihapus", false)
            .whereEqualTo("aktif", true)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = firestore.batch()
                val now = Timestamp.now()

                snapshot.documents.forEach { doc ->
                    if (doc.id != currentParameterId) {
                        batch.update(
                            doc.reference,
                            mapOf(
                                "aktif" to false,
                                "diperbaruiPada" to now
                            )
                        )
                    }
                }

                batch.commit()
                    .addOnSuccessListener {
                        goBackToList(productId)
                    }
                    .addOnFailureListener { e ->
                        showMessage("Parameter tersimpan, tapi sinkron aktif gagal: ${e.message}")
                        goBackToList(productId)
                    }
            }
            .addOnFailureListener { e ->
                showMessage("Parameter tersimpan, tapi validasi aktif gagal: ${e.message}")
                goBackToList(productId)
            }
    }

    private fun generateNextParameterId(
        onResult: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection("parameterProduksi")
            .get()
            .addOnSuccessListener { snapshot ->
                val lastNumber = snapshot.documents.mapNotNull { doc ->
                    if (doc.id.startsWith("ppm_")) {
                        doc.id.removePrefix("ppm_").toIntOrNull()
                    } else {
                        null
                    }
                }.maxOrNull() ?: 0

                val nextNumber = lastNumber + 1
                onResult("ppm_%03d".format(nextNumber))
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    private fun goBackToList(productId: String) {
        showMessage("Parameter produksi berhasil disimpan.")
        setResult(RESULT_OK, Intent().putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, productId))
        finish()
    }
}

private data class OpsiProdukDasar(
    val id: String,
    val namaProduk: String,
    val satuan: String
)